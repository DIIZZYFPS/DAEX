package com.daex.android.services

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DownloadProgress(
    val bytesWritten: Long,
    val contentLength: Long,
    val percent: Int
)

data class SpecSupport(
    val totalRAM: Long,
    val freeSpace: Long,
    val hasEnoughRAM: Boolean,
    val hasEnoughStorage: Boolean,
    val supported: Boolean
)

data class StorageInfo(
    val freeSpace: Long,
    val totalRAM: Long,
    val modelSize: Long,
    val isDownloaded: Boolean,
    val isHardwareCapable: Boolean
)

private class DownloadCancelledException : Exception("Download cancelled")
private class DownloadCorruptException(message: String) : Exception(message)

class ModelManager(private val context: Context) {
    private val deviceService = DeviceService(context)
    private val client = OkHttpClient()
    @Volatile private var activeGenerativeDownloadId: String? = null
    @Volatile private var activeKokoroDownloadId: String? = null

    fun getModelPath(model: Model): String {
        return File(context.filesDir, "${model.id}.${model.extension}").absolutePath
    }

    fun checkSpecSupport(model: Model): SpecSupport {
        val specs = deviceService.getDeviceSpecs()
        val hasEnoughRAM = specs.totalRAM >= model.requiredRAM
        val hasEnoughStorage = specs.freeStorage > model.size

        return SpecSupport(
            totalRAM = specs.totalRAM,
            freeSpace = specs.freeStorage,
            hasEnoughRAM = hasEnoughRAM,
            hasEnoughStorage = hasEnoughStorage,
            supported = hasEnoughRAM // Storage is transient, but RAM is a hard limit
        )
    }

    private fun partFileFor(file: File): File = File(file.parentFile, "${file.name}.part")
    private fun sizeMetaFileFor(file: File): File = File(file.parentFile, "${file.name}.size")

    /**
     * A file is valid only if its length exactly matches the size we recorded when we
     * finished downloading it (learned from the server, not [ModelBank]'s rounded estimates).
     * Files that predate this tracking (no sidecar yet) get a one-time heuristic pass so
     * existing installs aren't forced to re-download; the sidecar is then backfilled.
     */
    private fun isFileValid(file: File, fallbackSize: Long): Boolean {
        if (!file.exists()) return false
        val sizeMetaFile = sizeMetaFileFor(file)
        val recordedSize = if (sizeMetaFile.exists()) {
            sizeMetaFile.readText().trim().toLongOrNull()
        } else null

        if (recordedSize != null) {
            return file.length() == recordedSize
        }

        val looksComplete = file.length() >= (fallbackSize * 0.9).toLong()
        if (looksComplete) {
            sizeMetaFile.writeText(file.length().toString())
        }
        return looksComplete
    }

    private fun parseContentRangeTotal(header: String?): Long? {
        // Expected format: "bytes start-end/total"
        return header?.substringAfterLast('/')?.toLongOrNull()
    }

    /**
     * Downloads [url] into [partFile], resuming via HTTP Range if a partial [partFile]
     * already exists, then atomically renames it into [finalFile] once its length matches
     * the size we actually observed from the server. Only an explicit cancel or a
     * post-download size mismatch should cause the caller to delete [partFile] - any other
     * exception (network drop, etc.) leaves it in place so the next call resumes.
     */
    private suspend fun downloadWithResume(
        url: String,
        partFile: File,
        finalFile: File,
        fallbackSize: Long,
        isActive: () -> Boolean,
        onChunk: (bytesWritten: Long, expectedTotal: Long) -> Unit
    ) {
        var resumeOffset = if (partFile.exists()) partFile.length() else 0L
        val requestBuilder = Request.Builder().url(url)
        if (resumeOffset > 0) {
            requestBuilder.header("Range", "bytes=$resumeOffset-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download from $url: ${response.code}")

            val body = response.body ?: throw Exception("Empty response body from $url")
            var isResumed = response.code == 206

            if (resumeOffset > 0 && !isResumed) {
                // Server ignored the Range header and is sending the full file - restart it.
                partFile.delete()
                resumeOffset = 0L
                isResumed = false
            }

            val expectedTotal = if (isResumed) {
                parseContentRangeTotal(response.header("Content-Range"))
                    ?: body.contentLength().takeIf { it > 0 }?.let { resumeOffset + it }
                    ?: fallbackSize
            } else {
                body.contentLength().takeIf { it > 0 } ?: fallbackSize
            }

            body.byteStream().use { input ->
                FileOutputStream(partFile, isResumed).use { output ->
                    val buffer = ByteArray(8192)
                    var written = resumeOffset
                    while (isActive()) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        written += bytesRead
                        onChunk(written, expectedTotal)
                    }
                    if (!isActive()) throw DownloadCancelledException()
                }
            }

            if (partFile.length() != expectedTotal) {
                throw DownloadCorruptException(
                    "Downloaded file size mismatch for $url: expected $expectedTotal, got ${partFile.length()}"
                )
            }

            sizeMetaFileFor(finalFile).writeText(expectedTotal.toString())
            Files.move(
                partFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }
    }

    suspend fun isModelDownloaded(model: Model): Boolean = withContext(Dispatchers.IO) {
        isFileValid(File(getModelPath(model)), model.size)
    }

    suspend fun downloadModel(
        model: Model,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val destPath = getModelPath(model)
        val file = File(destPath)
        val partFile = partFileFor(file)

        val spec = checkSpecSupport(model)
        if (!spec.supported) {
            throw Exception("Device does not meet the required ${formatBytes(model.requiredRAM)} of RAM for this model.")
        }

        if (file.exists()) {
            if (isFileValid(file, model.size)) {
                return@withContext destPath
            }
            file.delete()
        }

        val downloadId = java.util.UUID.randomUUID().toString()
        synchronized(this) {
            if (activeGenerativeDownloadId != null) {
                throw IllegalStateException("downloadModel is already active.")
            }
            activeGenerativeDownloadId = downloadId
        }

        try {
            downloadWithResume(
                url = model.downloadUrl,
                partFile = partFile,
                finalFile = file,
                fallbackSize = model.size,
                isActive = { activeGenerativeDownloadId == downloadId }
            ) { written, total ->
                val percent = if (total > 0) ((written.toDouble() / total) * 100).toInt() else 0
                onProgress?.invoke(DownloadProgress(written, total, percent))
            }
        } catch (e: DownloadCancelledException) {
            partFile.delete()
            throw e
        } catch (e: DownloadCorruptException) {
            partFile.delete()
            throw e
        } finally {
            synchronized(this) {
                if (activeGenerativeDownloadId == downloadId) {
                    activeGenerativeDownloadId = null
                }
            }
        }

        destPath
     }

     fun cancelDownload() {
         synchronized(this) {
             activeGenerativeDownloadId = null
         }
     }

    suspend fun deleteModel(model: Model) = withContext(Dispatchers.IO) {
        val file = File(getModelPath(model))
        val partFile = partFileFor(file)
        val sizeMetaFile = sizeMetaFileFor(file)
        if (file.exists()) file.delete()
        if (partFile.exists()) partFile.delete()
        if (sizeMetaFile.exists()) sizeMetaFile.delete()
    }

    suspend fun getStorageInfo(model: Model): StorageInfo {
        val spec = checkSpecSupport(model)
        val isDownloaded = isModelDownloaded(model)

        var modelSize = 0L
        if (isDownloaded) {
            val file = File(getModelPath(model))
            modelSize = file.length()
        }

        return StorageInfo(
            freeSpace = spec.freeSpace,
            totalRAM = spec.totalRAM,
            modelSize = modelSize,
            isDownloaded = isDownloaded,
            isHardwareCapable = spec.supported
        )
    }

    fun getKokoroDir(): File {
        return File(context.filesDir, "kokoro-en-v0_19")
    }

    suspend fun isKokoroDownloaded(): Boolean = withContext(Dispatchers.IO) {
        val dir = getKokoroDir()
        isFileValid(File(dir, "model.onnx"), ModelBank.kokoroModel.size) &&
                isFileValid(File(dir, "voices.bin"), ModelBank.kokoroVoices.size) &&
                isFileValid(File(dir, "tokens.txt"), ModelBank.kokoroTokens.size)
    }

    private fun deleteStrayKokoroParts(targets: List<Triple<File, String, Long>>) {
        for ((file, _, _) in targets) {
            val partFile = partFileFor(file)
            if (partFile.exists()) partFile.delete()
        }
    }

    suspend fun downloadKokoro(
        onProgress: ((Int) -> Unit)? = null
    ): Unit = withContext(Dispatchers.IO) {
        val dir = getKokoroDir()
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val targets = listOf(
            Triple(File(dir, "model.onnx"), ModelBank.kokoroModel.downloadUrl, ModelBank.kokoroModel.size),
            Triple(File(dir, "voices.bin"), ModelBank.kokoroVoices.downloadUrl, ModelBank.kokoroVoices.size),
            Triple(File(dir, "tokens.txt"), ModelBank.kokoroTokens.downloadUrl, ModelBank.kokoroTokens.size)
        )

        val totalSize = targets.sumOf { it.third }
        var completedBytes = targets.filter { isFileValid(it.first, it.third) }.sumOf { it.third }

        val downloadId = java.util.UUID.randomUUID().toString()
        synchronized(this) {
            if (activeKokoroDownloadId != null) {
                throw IllegalStateException("downloadKokoro is already active.")
            }
            activeKokoroDownloadId = downloadId
        }
        try {
            for ((file, url, expectedSize) in targets) {
                if (isFileValid(file, expectedSize)) {
                    continue
                }
                if (file.exists()) {
                    file.delete()
                }

                downloadWithResume(
                    url = url,
                    partFile = partFileFor(file),
                    finalFile = file,
                    fallbackSize = expectedSize,
                    isActive = { activeKokoroDownloadId == downloadId }
                ) { written, _ ->
                    val percent = if (totalSize > 0) {
                        (((completedBytes + written).toDouble() / totalSize) * 100).toInt()
                    } else 0
                    onProgress?.invoke(percent.coerceIn(0, 99))
                }
                completedBytes += expectedSize
            }
            onProgress?.invoke(100)
        } catch (e: DownloadCancelledException) {
            deleteStrayKokoroParts(targets)
            throw e
        } catch (e: DownloadCorruptException) {
            deleteStrayKokoroParts(targets)
            throw e
        } finally {
            synchronized(this) {
                if (activeKokoroDownloadId == downloadId) {
                    activeKokoroDownloadId = null
                }
            }
        }
    }

    fun cancelKokoroDownload() {
        synchronized(this) {
            activeKokoroDownloadId = null
        }
    }

    suspend fun deleteKokoro() = withContext(Dispatchers.IO) {
        val dir = getKokoroDir()
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes == 0L) return "0 B"
        val k = 1024.0
        val sizes = arrayOf("B", "KB", "MB", "GB")
        val i = kotlin.math.floor(kotlin.math.log(bytes.toDouble(), k)).toInt()
        return String.format("%.1f %s", bytes / k.pow(i.toDouble()), sizes[i])
    }
}

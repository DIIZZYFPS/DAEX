package com.daex.android.services

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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

    suspend fun isModelDownloaded(model: Model): Boolean = withContext(Dispatchers.IO) {
        val file = File(getModelPath(model))
        if (!file.exists()) return@withContext false
        
        // Ensure the file is at least 90% of expected size to be considered "valid"
        file.length() >= model.size * 0.9
    }

    suspend fun downloadModel(
        model: Model,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val destPath = getModelPath(model)
        val file = File(destPath)

        val spec = checkSpecSupport(model)
        if (!spec.supported) {
            throw Exception("Device does not meet the required ${formatBytes(model.requiredRAM)} of RAM for this model.")
        }

        if (file.exists()) {
            if (file.length() < model.size * 0.9) {
                file.delete()
            } else {
                return@withContext destPath
            }
        }

        val downloadId = java.util.UUID.randomUUID().toString()
        synchronized(this) {
            if (activeGenerativeDownloadId != null) {
                throw IllegalStateException("downloadModel is already active.")
            }
            activeGenerativeDownloadId = downloadId
        }

        try {
            val request = Request.Builder().url(model.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Failed to download model: ${response.code}")

                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: model.size
                
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalDownloaded = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1 && activeGenerativeDownloadId == downloadId) {
                            output.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead
                            
                            val percent = if (totalBytes > 0) ((totalDownloaded.toDouble() / totalBytes) * 100).toInt() else 0
                            onProgress?.invoke(DownloadProgress(totalDownloaded, totalBytes, percent))
                        }
                        
                        if (activeGenerativeDownloadId != downloadId) {
                            throw Exception("Download cancelled")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            synchronized(this) {
                if (activeGenerativeDownloadId == downloadId) {
                    if (file.exists()) file.delete()
                }
            }
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
        if (file.exists()) {
            file.delete()
        }
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
        val modelFile = File(dir, "model.onnx")
        val voicesFile = File(dir, "voices.bin")
        val tokensFile = File(dir, "tokens.txt")
        
        modelFile.exists() && modelFile.length() >= ModelBank.kokoroModel.size * 0.9 &&
                voicesFile.exists() && voicesFile.length() >= ModelBank.kokoroVoices.size * 0.9 &&
                tokensFile.exists() && tokensFile.length() >= ModelBank.kokoroTokens.size * 0.9
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
        var totalBytesDownloaded = 0L

        val downloadId = java.util.UUID.randomUUID().toString()
        synchronized(this) {
            if (activeKokoroDownloadId != null) {
                throw IllegalStateException("downloadKokoro is already active.")
            }
            activeKokoroDownloadId = downloadId
        }
        try {
            for ((file, url, expectedSize) in targets) {
                if (file.exists() && file.length() >= expectedSize * 0.9) {
                    totalBytesDownloaded += file.length()
                    continue
                }

                if (file.exists()) {
                    file.delete()
                }

                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Failed to download file from $url: ${response.code}")

                    val body = response.body ?: throw Exception("Empty response body from $url")
                    val bodyLength = body.contentLength().takeIf { it > 0 } ?: expectedSize
                    
                    body.byteStream().use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            
                            while (input.read(buffer).also { bytesRead = it } != -1 && activeKokoroDownloadId == downloadId) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesDownloaded += bytesRead
                                
                                val percent = if (totalSize > 0) ((totalBytesDownloaded.toDouble() / totalSize) * 100).toInt() else 0
                                onProgress?.invoke(percent.coerceAtMost(99))
                            }
                            
                            if (activeKokoroDownloadId != downloadId) {
                                throw Exception("Download cancelled")
                            }
                        }
                    }
                }
            }
            onProgress?.invoke(100)
        } catch (e: Exception) {
            synchronized(this) {
                if (activeKokoroDownloadId == downloadId) {
                    for ((file, _, expectedSize) in targets) {
                        if (file.exists() && file.length() < expectedSize * 0.9) {
                            file.delete()
                        }
                    }
                }
            }
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

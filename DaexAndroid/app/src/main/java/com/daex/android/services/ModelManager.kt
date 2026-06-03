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
    private var isDownloading = false

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
        hfToken: String = "",
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

        isDownloading = true
        try {
            val requestBuilder = Request.Builder().url(model.downloadUrl)
            if (hfToken.isNotBlank() && (model.downloadUrl.contains("huggingface.co") || model.downloadUrl.contains("huggingface"))) {
                requestBuilder.header("Authorization", "Bearer $hfToken")
            }
            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = if (response.code == 401 || response.code == 403) {
                        "Failed to download model: ${response.code} (Hugging Face gated model access requires a valid token in settings)"
                    } else {
                        "Failed to download model: ${response.code}"
                    }
                    throw Exception(msg)
                }

                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: model.size
                
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalDownloaded = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1 && isDownloading) {
                            output.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead
                            
                            val percent = if (totalBytes > 0) ((totalDownloaded.toDouble() / totalBytes) * 100).toInt() else 0
                            onProgress?.invoke(DownloadProgress(totalDownloaded, totalBytes, percent))
                        }
                        
                        if (!isDownloading) {
                            file.delete()
                            throw Exception("Download cancelled")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (file.exists()) file.delete()
            throw e
        } finally {
            isDownloading = false
        }
        
        destPath
    }

    fun cancelDownload() {
        isDownloading = false
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

    fun formatBytes(bytes: Long): String {
        if (bytes == 0L) return "0 B"
        val k = 1024.0
        val sizes = arrayOf("B", "KB", "MB", "GB")
        val i = kotlin.math.floor(kotlin.math.log(bytes.toDouble(), k)).toInt()
        return String.format("%.1f %s", bytes / k.pow(i.toDouble()), sizes[i])
    }
}

package com.daex.android.services

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

class DaexEmbedder(
    private val context: Context,
    private val modelManager: ModelManager
) {
    private var textEmbedder: TextEmbedder? = null

    suspend fun initEmbeddingContext() {
        withContext(Dispatchers.IO) {
            synchronized(this@DaexEmbedder) {
                if (textEmbedder != null) return@synchronized

                Log.d("DaexEmbedder", "Initializing MediaPipe TextEmbedder...")
                try {
                    val model = ModelBank.embeddingModel
                    val modelPath = modelManager.getModelPath(model)
                    val modelFile = File(modelPath)

                    if (!modelFile.exists()) {
                        Log.w("DaexEmbedder", "Embedding model file not found at $modelPath")
                        return@synchronized
                    }

                    // Map the model file into memory
                    val byteBuffer = FileInputStream(modelFile).channel.use { channel ->
                        channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                    }

                    val baseOptions = BaseOptions.builder()
                        .setModelAssetBuffer(byteBuffer)
                        .setDelegate(com.google.mediapipe.tasks.core.Delegate.CPU)
                        .build()

                    val options = TextEmbedderOptions.builder()
                        .setBaseOptions(baseOptions)
                        .build()

                    textEmbedder = TextEmbedder.createFromOptions(context, options)
                    Log.d("DaexEmbedder", "MediaPipe TextEmbedder initialized successfully with CPU delegate.")
                    Log.d("DaexEmbedder", "MediaPipe TextEmbedder initialized successfully.")
                } catch (e: Exception) {
                    Log.e("DaexEmbedder", "Failed to initialize MediaPipe TextEmbedder", e)
                }
            }
        }
    }

    suspend fun generateEmbedding(text: String, isQuery: Boolean = false): FloatArray {
        return withContext(Dispatchers.IO) {
            initEmbeddingContext()

            synchronized(this@DaexEmbedder) {
                val embedder = textEmbedder
                if (embedder != null) {
                    try {
                        val result = embedder.embed(text)
                        val embeddings = result.embeddingResult().embeddings()
                        if (embeddings.isNotEmpty()) {
                            val floatArray = embeddings[0].floatEmbedding()
                            if (floatArray != null) {
                                var sum = 0.0f
                                for (v in floatArray) {
                                    sum += v * v
                                }
                                val norm = kotlin.math.sqrt(sum)
                                Log.d("DaexEmbedder", "Embedding text='${text.take(30).replace("\n", " ")}' -> raw norm=$norm, size=${floatArray.size}, first 5=[${floatArray.take(5).joinToString(", ")}]")
                                if (norm > 1e-9f) {
                                    for (i in floatArray.indices) {
                                        floatArray[i] /= norm
                                    }
                                }
                                return@withContext floatArray
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DaexEmbedder", "Embedding inference failed", e)
                    }
                }
                Log.w("DaexEmbedder", "Falling back to 512-dimensional zero-vector")
                FloatArray(512) { 0.0f }
            }
        }
    }
}

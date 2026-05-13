package com.daex.android.services

import android.content.Context
import android.util.Log
import com.daex.llama.DaexLlamaEngine
import com.daex.llama.internal.DaexLlamaEngineImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Text embedding service using DaexLlamaEngine.
 * 
 * Replaces the old LlamaAndroid-based embedder. Uses a separate
 * embedding model context (e.g., nomic-embed-text) for RAG.
 */
class DaexEmbedder(
    private val context: Context,
    private val modelManager: ModelManager,
) {
    companion object {
        private const val TAG = "DaexEmbedder"
        private const val DEFAULT_N_CTX = 2048
        private const val DEFAULT_N_BATCH = 512
    }

    private var engine: DaexLlamaEngine? = null
    private var embedCtxId: Int = -1

    suspend fun initEmbeddingContext() {
        withContext(Dispatchers.IO) {
            val eng = engine ?: run {
                val created = DaexLlamaEngineImpl.create(context.applicationContext)
                engine = created
                created
            }

            val embedModel = ModelBank.embeddingModel
            if (!modelManager.isModelDownloaded(embedModel)) {
                throw Exception("Embedding model is still downloading in the background.")
            }

            val modelPath = modelManager.getModelPath(embedModel)
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                throw IllegalArgumentException("Embedding model not found: $modelPath")
            }

            // Create a dedicated context for embeddings
            embedCtxId = eng.createContext()
            eng.setConfig(embedCtxId, n_ctx = DEFAULT_N_CTX, n_batch = DEFAULT_N_BATCH)

            // Load embedding model
            val loaded = eng.loadEmbeddingModel(embedCtxId, modelFile.absolutePath)
            if (!loaded) {
                throw IllegalStateException("Failed to load embedding model: $modelPath")
            }

            Log.d(TAG, "Embedding engine initialized (ctx=$embedCtxId)")
        }
    }

    suspend fun generateEmbedding(text: String, isQuery: Boolean = false): FloatArray {
        val ctxId = embedCtxId.takeIf { it != -1 }
            ?: throw Exception("Embedding engine not initialized. Call initEmbeddingContext() first.")

        // Nomic v1.5 requires specific prefixes for best accuracy
        val prefix = if (isQuery) "search_query: " else "search_document: "
        val embeddingPrompt = prefix + text

        return withContext(Dispatchers.IO) {
            val eng = engine ?: throw Exception("Engine not available")
            val vector = eng.getEmbedding(ctxId, embeddingPrompt)
            Log.d(TAG, "Generated embedding: size=${vector.size}, first5=[${vector.take(5).joinToString()}]")
            vector
        }
    }

    fun destroy() {
        if (embedCtxId != -1) {
            engine?.destroyContext(embedCtxId)
            embedCtxId = -1
        }
    }
}

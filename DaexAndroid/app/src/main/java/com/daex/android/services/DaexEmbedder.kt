package com.daex.android.services

// Android & Files
import android.content.Context
import android.net.Uri
import java.io.File

// Kotlin Standard
import android.util.Log

// Coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Llama.cpp Engine
import org.nehuatl.llamacpp.LlamaAndroid

// DAEX Services
import com.daex.android.services.ModelManager
import com.daex.android.services.ModelBank

class DaexEmbedder(
    private val context: Context,
    private val modelManager: ModelManager
) {
    private val llamaAndroid = LlamaAndroid(context.contentResolver)
    private var embedContextId: Int? = null

    suspend fun initEmbeddingContext() {
        withContext(Dispatchers.IO) {
            val embedModel = ModelBank.embeddingModel
            
            if (!modelManager.isModelDownloaded(embedModel)) {
                throw Exception("Embedding model is still downloading in the background.")
            }

            val modelPath = modelManager.getModelPath(embedModel)
            val modelUri = Uri.fromFile(File(modelPath))
            val modelPfd = context.contentResolver.openFileDescriptor(modelUri, "r")
                ?: throw IllegalArgumentException("Cannot open model file")
                
            val modelFd = modelPfd.detachFd()

            val config = mutableMapOf<String, Any>(
                "model" to modelUri.toString(),
                "model_fd" to modelFd,
                "embedding" to true,
                "n_ctx" to 8192,
                "n_batch" to 512,
                "n_threads" to 4,
                "use_mmap" to true
            )

            val result = llamaAndroid.startEngine(config) {_ ->}

            if (result == null) {
                throw Exception("Failed to start embedding engine")
            }

            val id = result["contextId"] ?: throw Exception("No contextID in result")
            embedContextId = (id as Number).toInt()
            Log.d("DaexEmbedder", "Embedding engine started with ID: $embedContextId")
        }
    }

    suspend fun generateEmbedding(text: String, isQuery: Boolean = false): FloatArray {
        val embedId = embedContextId ?: throw Exception("Embedding engine not initialized")
        
        // 1. Prepare Prompt (Nomic v1.5 requires specific prefixes for best accuracy)
        val prefix = if (isQuery) "search_query: " else "search_document: "
        val embeddingPrompt = prefix + text

        return withContext(Dispatchers.IO) {
            try {
                // BYPASS: The library's LlamaAndroid.embedding() calls context.getEmbedding(), 
                // which incorrectly tries to convert an ArrayList to a Map via .toMutableMap(),
                // triggering a java.lang.ArrayStoreException in HashMap.putMapEntries.
                // We bypass this by calling the native embedding method directly via reflection.

                // 1. Access LlamaAndroid.contexts (ConcurrentHashMap<Int, LlamaContext>)
                val contextsField = llamaAndroid.javaClass.getDeclaredField("contexts")
                contextsField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val contexts = contextsField.get(llamaAndroid) as Map<Int, Any>
                
                // 2. Get the LlamaContext instance for our ID
                val llamaContext = contexts[embedId] ?: throw Exception("LlamaContext not found for ID $embedId")
                
                // 3. Get the native 'context' pointer (Long) from LlamaContext
                val nativeCtxField = llamaContext.javaClass.getDeclaredField("context")
                nativeCtxField.isAccessible = true
                val nativeCtx = nativeCtxField.get(llamaContext) as Long
                
                // 4. Find the native 'embedding' method (Long, String)
                // Note: The patched AAR returns Object (ArrayList) instead of Map.
                val embeddingMethod = llamaContext.javaClass.getDeclaredMethods().find { 
                    it.name == "embedding" && it.parameterTypes.size == 2 && 
                    (it.parameterTypes[0] == Long::class.javaPrimitiveType || it.parameterTypes[0] == Long::class.javaObjectType)
                } ?: throw Exception("Native embedding method not found")
                
                embeddingMethod.isAccessible = true
                Log.d("DaexEmbedder", "Invoking native embedding for text: ${text.take(50)}...")
                val result = embeddingMethod.invoke(llamaContext, nativeCtx, embeddingPrompt)
                
                // 5. Parse the result based on what the JNI actually returned
                val vector = when (result) {
                    is FloatArray -> result
                    is DoubleArray -> result.map { it.toFloat() }.toFloatArray()
                    is List<*> -> result.map { (it as Number).toFloat() }.toFloatArray()
                    else -> throw Exception("Unknown embedding format: ${result?.javaClass?.name}")
                }

                Log.d("DaexEmbedder", "Generated embedding vector: size=${vector.size}, first5=[${vector.take(5).joinToString()}]")
                vector
            } catch (e: Exception) {
                Log.e("DaexEmbedder", "Embedding generation failed via reflection bypass", e)
                throw e
            }
        }
    }
}

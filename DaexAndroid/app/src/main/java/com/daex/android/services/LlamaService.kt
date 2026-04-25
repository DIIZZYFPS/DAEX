package com.daex.android.services

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.nehuatl.llamacpp.LlamaAndroid
import org.nehuatl.llamacpp.LlamaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Random
import kotlin.math.absoluteValue

data class Message(
    val id: String,
    val role: String, // "user" or "model"
    val content: String
)

interface LlamaService {
    suspend fun initContext(modelPath: String, useGPU: Boolean = false)
    suspend fun releaseContext()
    suspend fun generateResponse(
        messages: List<Message>,
        onToken: (String) -> Unit
    ): GenerationResult
    fun isLoaded(): Boolean
}

data class GenerationResult(
    val text: String,
    val tokensPerSecond: Double
)

class LlamaServiceImpl(private val context: Context) : LlamaService {
    private val llamaAndroid = LlamaAndroid(context.contentResolver)
    private val tokenFlow = MutableSharedFlow<String>(extraBufferCapacity = 128)
    private var currentContextId: Int? = null
    private var isLoaded = false
    private var isGenerating = false
    
    // Gemma 4 chat template tokens
    private val BOS = "<bos>"
    private val TURN_START = "<|turn>"
    private val TURN_END = "<turn|>"

    private val SYSTEM_PROMPT = "You are Icarus, running inside the Daedalus Execution Engine (DAEX). You are a high-performance AI assistant running directly on device hardware. You respond with precision and speed."

    private fun formatPrompt(messages: List<Message>): String {
        val sb = StringBuilder()
        sb.append("$BOS${TURN_START}system\n$SYSTEM_PROMPT$TURN_END\n")
        
        for (msg in messages) {
            sb.append("${TURN_START}${msg.role}\n${msg.content}$TURN_END\n")
        }
        
        // Open the model turn for generation
        sb.append("${TURN_START}model\n")
        return sb.toString()
    }

    override suspend fun initContext(modelPath: String, useGPU: Boolean) {
        withContext(Dispatchers.IO) {
            var modelFd = -1
            try {
                currentContextId?.let { 
                    try { llamaAndroid.releaseContext(it) } catch (e: Exception) {}
                    currentContextId = null
                }

                val modelUri = Uri.fromFile(File(modelPath))
                val modelPfd = context.contentResolver.openFileDescriptor(modelUri, "r")
                    ?: throw IllegalArgumentException("Cannot open model file: $modelPath")
                
                modelFd = modelPfd.detachFd()
                
                val config = mutableMapOf<String, Any>(
                    "model" to modelUri.toString(),
                    "model_fd" to modelFd,
                    "use_mmap" to true,
                    "use_mlock" to false,
                    "n_ctx" to 2048,
                    "embedding" to false,
                    "n_batch" to 512,
                    "n_threads" to 4,
                    "n_gpu_layers" to (if (useGPU) 99 else 0),
                    "vocab_only" to false,
                    "lora" to "",
                    "lora_scaled" to 1.0,
                    "rope_freq_base" to 0.0,
                    "rope_freq_scale" to 0.0
                )

                val result = llamaAndroid.startEngine(config) { token ->
                    tokenFlow.tryEmit(token)
                }

                if (result == null) {
                    // If startEngine failed, it might not have closed the FD.
                    // But usually, JNI side takes ownership if we detach.
                    // However, if it didn't even reach JNI...
                    throw Exception("Failed to start llama engine")
                }
                
                val id = result["contextId"] ?: throw Exception("No contextId in result")
                currentContextId = (id as Number).toInt()
                isLoaded = true
                Log.d("LlamaService", "Model loaded with context ID: $currentContextId")
            } catch (e: Exception) {
                isLoaded = false
                Log.e("LlamaService", "Failed to init context", e)
                throw e
            }
        }
    }

    override suspend fun releaseContext() {
        withContext(Dispatchers.IO) {
            currentContextId?.let {
                llamaAndroid.releaseContext(it)
                currentContextId = null
            }
            isLoaded = false
        }
    }

    override suspend fun generateResponse(
        messages: List<Message>,
        onToken: (String) -> Unit
    ): GenerationResult {
        val contextId = currentContextId ?: throw Exception("Model not loaded.")
        
        val prompt = formatPrompt(messages)
        val startTime = System.currentTimeMillis()
        var tokenCount = 0
        val fullText = StringBuilder()
        
        isGenerating = true
        
        try {
            val params = mutableMapOf<String, Any>(
                "prompt" to prompt,
                "emit_partial_completion" to true,
                "temperature" to 0.7,
                "n_predict" to 1024
            )

            // Collection job with its own local state to avoid race condition
            val collectionJob = CoroutineScope(Dispatchers.Default).launch {
                tokenFlow
                    .collect { token ->
                        // In some implementations, an empty token or a specific end token is sent
                        tokenCount++
                        fullText.append(token)
                        onToken(token)
                    }
            }

            withContext(Dispatchers.IO) {
                // Launch completion - this blocks until the generation is finished (on the JNI side)
                llamaAndroid.launchCompletion(contextId, params)
            }
            
            // Give a tiny buffer for any remaining tokens in the shared flow to be collected
            delay(100) 
            collectionJob.cancel()

        } catch (e: Exception) {
            Log.e("LlamaService", "Generation failed", e)
            throw e
        } finally {
            isGenerating = false
        }
        
        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        val tps = if (elapsedSeconds > 0) tokenCount / elapsedSeconds else 0.0
        
        return GenerationResult(
            text = fullText.toString(),
            tokensPerSecond = Math.round(tps * 10.0) / 10.0
        )
    }

    override fun isLoaded(): Boolean = isLoaded
    
    fun cancelGeneration() {
        isGenerating = false
    }
}

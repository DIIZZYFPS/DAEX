package com.daex.android.services

import android.content.Context
import android.util.Log
import com.daex.llama.DaexLlamaEngine
import com.daex.llama.internal.DaexLlamaEngineImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Message(
    val id: String,
    val role: String, // "user" or "model"
    val content: String,
    val tokensPerSecond: Double = 0.0,
    val thoughtContent: String? = null
)

interface LlamaService {
    suspend fun initContext(modelPath: String, useGPU: Boolean = false)
    suspend fun releaseContext()
    suspend fun generateResponse(
        messages: List<Message>,
        systemContext: String = "",
        isReasoningEnabled: Boolean = true,
        onToken: (String) -> Unit
    ): GenerationResult
    suspend fun generateSilent(prompt: String, maxTokens: Int = 512): String
    fun isLoaded(): Boolean
}

data class GenerationResult(
    val text: String,
    val tokensPerSecond: Double
)

/**
 * LlamaService implementation backed by DaexLlamaEngine (llama.cpp JNI).
 *
 * Replaces the legacy LlamaAndroid AAR with a modern, single-threaded
 * coroutine-based inference engine that:
 *   - Uses GGUF-native chat templates (no manual token formatting)
 *   - Exposes generation as Flow<String> (no race-condition buffer)
 *   - Supports dynamic backend detection (KleidiAI, HTP/NPU)
 */
class LlamaServiceImpl(
    private val context: Context,
) : LlamaService {

    companion object {
        private const val TAG = "LlamaService"
        private const val DEFAULT_SYSTEM_PROMPT =
            "You are Icarus, running inside the Daedalus Execution Engine (DAEX). " +
            "You are a high-performance AI assistant running directly on device hardware. " +
            "You respond with precision and speed."
        private const val DEFAULT_N_CTX = 8192
        private const val DEFAULT_N_BATCH = 512
        private const val DEFAULT_TEMP = 0.7f
        private const val DEFAULT_N_PREDICT = 1024
    }

    // Engine (singleton, initialized lazily)
    private var engine: DaexLlamaEngine? = null
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Context ID managed by the engine
    private var nativeCtxId: Int = -1
    private var isLoaded = false

    // --------------------------------------------------------------------------
    // Lifecycle
    // --------------------------------------------------------------------------

    private suspend fun ensureEngine(): DaexLlamaEngine {
        val e = engine ?: run {
            val created = DaexLlamaEngineImpl.create(context.applicationContext)
            engine = created
            created
        }
        return e
    }

    override suspend fun initContext(modelPath: String, useGPU: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val eng = ensureEngine()

                // Create context
                nativeCtxId = eng.createContext()

                // Set config
                eng.setConfig(
                    ctxId = nativeCtxId,
                    n_ctx = DEFAULT_N_CTX,
                    n_batch = DEFAULT_N_BATCH,
                    temp = DEFAULT_TEMP,
                    n_predict = DEFAULT_N_PREDICT,
                )

                // Load model
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    throw IllegalArgumentException("Model file not found: $modelPath")
                }

                val loaded = eng.loadModel(nativeCtxId, modelFile.absolutePath)
                if (!loaded) {
                    throw IllegalStateException("Failed to load model: $modelPath")
                }

                // Prepare context
                val prepared = eng.prepareContext(nativeCtxId)
                if (!prepared) {
                    throw IllegalStateException("Failed to prepare context")
                }

                isLoaded = true
                Log.d(TAG, "Model loaded successfully (ctx=$nativeCtxId)")

            } catch (e: Exception) {
                isLoaded = false
                Log.e(TAG, "Failed to init context", e)
                throw e
            }
        }
    }

    override suspend fun releaseContext() {
        withContext(Dispatchers.IO) {
            if (nativeCtxId != -1) {
                engine?.unloadModel(nativeCtxId)
                engine?.destroyContext(nativeCtxId)
                nativeCtxId = -1
            }
            isLoaded = false
            Log.d(TAG, "Context released")
        }
    }

    // --------------------------------------------------------------------------
    // Prompt formatting — uses GGUF-native chat templates via the engine
    // --------------------------------------------------------------------------

    private fun buildSystemPrompt(systemContext: String): String {
        return buildString {
            append(DEFAULT_SYSTEM_PROMPT)
            if (systemContext.isNotBlank()) {
                append("\n\n### CONTEXT_START ###\n")
                append(systemContext)
                append("\n### CONTEXT_END ###\n\n")
                append("The above block contains relevant excerpts from your memory or uploaded documents. " +
                       "Prioritize this information to answer the user's request accurately. " +
                       "If the context is not relevant, rely on your internal knowledge.")
            }
        }
    }

    // --------------------------------------------------------------------------
    // Generation
    // --------------------------------------------------------------------------

    override suspend fun generateResponse(
        messages: List<Message>,
        systemContext: String,
        isReasoningEnabled: Boolean,
        onToken: (String) -> Unit
    ): GenerationResult {
        val ctxId = nativeCtxId.takeIf { isLoaded }
            ?: throw IllegalStateException("Model not loaded. Call initContext() first.")

        val systemPrompt = buildSystemPrompt(systemContext)
        val startTime = System.currentTimeMillis()
        var tokenCount = 0
        val fullText = StringBuilder()

        try {
            // Process system prompt (one-shot, serialized)
            val sysOk = engine?.processSystemPrompt(ctxId, systemPrompt)
            if (sysOk != true) {
                throw IllegalStateException("Failed to process system prompt")
            }

            // Build user message from conversation history
            val userContent = buildString {
                messages.forEach { msg ->
                    if (msg.role == "user") {
                        append(msg.content)
                        append("\n\n")
                    }
                }
            }.trim()

            // Process user prompt — returns Flow<String>
            val userOk = engine?.processUserPrompt(ctxId, userContent)
            if (userOk != true) {
                throw IllegalStateException("Failed to process user prompt")
            }

            // Collect the flow (already serialized by the engine)
            engine?.processUserPrompt(ctxId, userContent)?.collect { token ->
                if (token.isNotEmpty()) {
                    tokenCount++
                    fullText.append(token)
                    onToken(token)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            throw e
        }

        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        val tps = if (elapsedSeconds > 0) tokenCount / elapsedSeconds else 0.0

        return GenerationResult(
            text = fullText.toString(),
            tokensPerSecond = Math.round(tps * 10.0) / 10.0
        )
    }

    override suspend fun generateSilent(prompt: String, maxTokens: Int): String {
        val ctxId = nativeCtxId.takeIf { isLoaded }
            ?: throw IllegalStateException("Model not loaded. Call initContext() first.")

        val fullText = StringBuilder()

        try {
            // Process system prompt
            val sysOk = engine?.processSystemPrompt(ctxId, DEFAULT_SYSTEM_PROMPT)
            if (sysOk != true) {
                throw IllegalStateException("Failed to process system prompt")
            }

            // Process user prompt
            val userOk = engine?.processUserPrompt(ctxId, prompt)
            if (userOk != true) {
                throw IllegalStateException("Failed to process user prompt")
            }

            // Collect tokens silently
            engine?.processUserPrompt(ctxId, prompt)?.collect { token ->
                if (token.isNotEmpty()) {
                    fullText.append(token)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Silent generation failed", e)
            throw e
        }

        return fullText.toString()
    }

    override fun isLoaded(): Boolean = isLoaded

    fun cancelGeneration() {
        if (nativeCtxId != -1) {
            engine?.cancelGeneration(nativeCtxId)
        }
    }

    fun getEngineInfo(): String {
        return try {
            "Backends: ${engine?.getActiveBackends()}\n" +
            "System: ${engine?.getSystemInfo()}"
        } catch (e: Exception) {
            "Engine not initialized"
        }
    }

    fun resetConversation() {
        if (nativeCtxId != -1) {
            engine?.resetConversation(nativeCtxId)
        }
    }
}

package com.daex.android.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Channel
import com.google.ai.edge.litertlm.Message as LiteRtMessage
import com.google.ai.edge.litertlm.Contents as LiteRtContents
import com.google.ai.edge.litertlm.Role as LiteRtRole

enum class BackendType {
    CPU,
    GPU,
    NPU
}

data class Message(
    val id: String,
    val role: String, // "user" or "model"
    val content: String,
    val tokensPerSecond: Double = 0.0,
    val thoughtContent: String? = null
)

interface LlamaService {
    suspend fun initContext(modelPath: String, backendType: BackendType): BackendType
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

class LlamaServiceImpl(private val context: Context) : LlamaService {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var isLoaded = false

    override suspend fun initContext(modelPath: String, backendType: BackendType): BackendType {
        return withContext(Dispatchers.IO) {
            try {
                releaseContext()
                Log.d("LlamaService", "Initializing LiteRT-LM Engine with model: $modelPath (backend=$backendType)")
                
                // Enable Speculative Decoding / MTP drafters for high-performance inference
                try {
                    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
                    com.google.ai.edge.litertlm.ExperimentalFlags.enableSpeculativeDecoding = true
                } catch (e: Throwable) {
                    Log.w("LlamaService", "Failed to set speculative decoding flag", e)
                }

                val backend = when (backendType) {
                    BackendType.NPU -> Backend.NPU(context.applicationInfo.nativeLibraryDir)
                    BackendType.GPU -> Backend.GPU()
                    BackendType.CPU -> Backend.CPU(numOfThreads = 4) // Optimized thread count for big cores
                }

                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    cacheDir = context.cacheDir.absolutePath
                )

                var newEngine: Engine? = null
                try {
                    newEngine = Engine(config)
                    newEngine.initialize()
                } catch (specEx: Exception) {
                    // If speculative decoding initialization failed, retry without it
                    Log.w("LlamaService", "Failed to initialize with speculative decoding, retrying with it disabled", specEx)
                    try {
                        @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
                        com.google.ai.edge.litertlm.ExperimentalFlags.enableSpeculativeDecoding = false
                    } catch (e: Throwable) {}
                    
                    newEngine?.close()
                    newEngine = Engine(config)
                    newEngine.initialize()
                }

                engine = newEngine
                isLoaded = true
                Log.d("LlamaService", "LiteRT-LM Engine initialized successfully with $backendType")
                backendType
            } catch (e: Exception) {
                // Cascading Fallback: NPU -> GPU -> CPU
                when (backendType) {
                    BackendType.NPU -> {
                        Log.w("LlamaService", "NPU initialization failed (missing TF_LITE_AUX), attempting fallback to GPU", e)
                        initContext(modelPath, BackendType.GPU)
                    }
                    BackendType.GPU -> {
                        Log.w("LlamaService", "GPU initialization failed, attempting fallback to CPU", e)
                        initContext(modelPath, BackendType.CPU)
                    }
                    BackendType.CPU -> {
                        isLoaded = false
                        Log.e("LlamaService", "Failed to initialize LiteRT-LM Engine on CPU", e)
                        throw e
                    }
                }
            }
        }
    }

    override suspend fun releaseContext() {
        withContext(Dispatchers.IO) {
            try {
                conversation?.close()
            } catch (e: Exception) {
                Log.e("LlamaService", "Error closing conversation", e)
            } finally {
                conversation = null
            }

            try {
                engine?.close()
            } catch (e: Exception) {
                Log.e("LlamaService", "Error closing engine", e)
            } finally {
                engine = null
            }
            
            isLoaded = false
            Log.d("LlamaService", "Engine and conversation released")
        }
    }

    override suspend fun generateResponse(
        messages: List<Message>,
        systemContext: String,
        isReasoningEnabled: Boolean,
        onToken: (String) -> Unit
    ): GenerationResult {
        val activeEngine = engine ?: throw Exception("Model not loaded.")
        
        val systemInstructionText = buildString {
            append("You are Icarus, running inside the Daedalus Execution Engine (DAEX). You are a high-performance AI assistant running directly on device hardware. You respond with precision and speed.\n\n")
            if (systemContext.isNotBlank()) {
                append("<global_memory>\n")
                append(systemContext)
                append("\n</global_memory>\n\n")
                append("The above is your persistent memory. Use it to personalize your responses. Do NOT attempt to update it yourself.\n")
            }
        }

        // Separate user's latest prompt from the conversation history
        val history = messages.dropLast(1)
        val activePrompt = messages.lastOrNull()?.content ?: ""

        val initialLiteRtMessages = history.map { msg ->
            when (msg.role) {
                "user" -> LiteRtMessage.user(msg.content)
                "model" -> LiteRtMessage.model(LiteRtContents.of(msg.content))
                else -> LiteRtMessage.user(msg.content)
            }
        }

        val channels = if (isReasoningEnabled) {
            listOf(Channel(channelName = "thinking", start = "<|think|>", end = "\n"))
        } else {
            emptyList()
        }

        val conversationConfig = ConversationConfig(
            systemInstruction = LiteRtContents.of(systemInstructionText),
            initialMessages = initialLiteRtMessages,
            channels = channels
        )

        // Close the previous conversation to start fresh with new history
        try {
            conversation?.close()
        } catch (e: Exception) {}

        val activeConversation = activeEngine.createConversation(conversationConfig)
        conversation = activeConversation

        val startTime = System.currentTimeMillis()
        var tokenCount = 0
        val fullText = StringBuilder()

        var hasStartedThinking = false
        var hasEndedThinking = false

        val extraContext = mapOf("enable_thinking" to isReasoningEnabled)

        withContext(Dispatchers.IO) {
            activeConversation.sendMessageAsync(activePrompt, extraContext).collect { reply ->
                val thinkingChunk = reply.channels["thinking"]
                val chunk = reply.contents.toString()

                if (!thinkingChunk.isNullOrEmpty()) {
                    if (!hasStartedThinking) {
                        onToken("<|think|>")
                        hasStartedThinking = true
                    }
                    onToken(thinkingChunk)
                }

                if (chunk.isNotEmpty()) {
                    if (hasStartedThinking && !hasEndedThinking) {
                        onToken("</think|>")
                        hasEndedThinking = true
                    }
                    tokenCount++
                    fullText.append(chunk)
                    onToken(chunk)
                }
            }
        }

        if (hasStartedThinking && !hasEndedThinking) {
            onToken("</think|>")
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
        try {
            conversation?.cancelProcess()
        } catch (e: Exception) {
            Log.e("LlamaService", "Failed to cancel process", e)
        }
    }

    override suspend fun generateSilent(prompt: String, maxTokens: Int): String {
        val activeEngine = engine ?: throw Exception("Model not loaded.")
        
        // Release the active conversation first to avoid "A session already exists" error
        try {
            conversation?.close()
        } catch (e: Exception) {}
        conversation = null

        val convConfig = ConversationConfig(
            systemInstruction = LiteRtContents.of(""),
            initialMessages = emptyList()
        )
        val tempConv = activeEngine.createConversation(convConfig)
        val responseText = StringBuilder()
        
        try {
            withContext(Dispatchers.IO) {
                tempConv.sendMessageAsync(prompt).collect { reply ->
                    responseText.append(reply.contents.toString())
                }
            }
        } finally {
            try {
                tempConv.close()
            } catch (e: Exception) {}
        }
        return responseText.toString()
    }
}

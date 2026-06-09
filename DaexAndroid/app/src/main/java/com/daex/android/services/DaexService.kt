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
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
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
    val thoughtContent: String? = null,
    val toolStatus: String? = null,
    val isPinned: Boolean = false,
    val isCompacted: Boolean = false
)

interface DaexService {
    suspend fun initContext(modelPath: String, backendType: BackendType, isSpeculativeDecodingEnabled: Boolean = true): BackendType
    suspend fun releaseContext()
    suspend fun generateResponse(
        messages: List<Message>,
        systemContext: String = "",
        isReasoningEnabled: Boolean = true,
        temperature: Float = 0.7f,
        topK: Int = 40,
        topP: Float = 0.9f,
        customSystemPrompt: String = "",
        isToolCallingEnabled: Boolean = false,
        onRequestPermission: (suspend (String, String) -> Boolean)? = null,
        onStatusUpdate: ((String?) -> Unit)? = null,
        onToken: (String) -> Unit
    ): GenerationResult
    suspend fun generateSilent(prompt: String, maxTokens: Int = 512): String
    fun isLoaded(): Boolean
}

data class GenerationResult(
    val text: String,
    val tokensPerSecond: Double
)

class DaexServiceImpl(private val context: Context) : DaexService {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var isLoaded = false

    companion object {
        private const val TAG = "DaexService"
        
    }

    override suspend fun initContext(modelPath: String, backendType: BackendType, isSpeculativeDecodingEnabled: Boolean): BackendType {
        return withContext(Dispatchers.IO) {
            try {
                releaseContext()
                Log.d("DaexService", "Initializing LiteRT-LM Engine with model: $modelPath (backend=$backendType, speculative=$isSpeculativeDecodingEnabled)")
                
                // Set native log severity to FATAL to suppress noisy NPU dispatch warning logs
                try {
                    com.google.ai.edge.litertlm.Engine.setNativeMinLogSeverity(com.google.ai.edge.litertlm.LogSeverity.FATAL)
                } catch (e: Throwable) {
                    Log.w("DaexService", "Failed to set native log severity", e)
                }

                // Enable Speculative Decoding / MTP drafters for high-performance inference
                try {
                    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
                    com.google.ai.edge.litertlm.ExperimentalFlags.enableSpeculativeDecoding = isSpeculativeDecodingEnabled
                } catch (e: Throwable) {
                    Log.w("DaexService", "Failed to set speculative decoding flag", e)
                }

                val backend = when (backendType) {
                    BackendType.NPU -> {
                        val libDir = context.applicationInfo.nativeLibraryDir
                        
                        // Check if a dispatch library is present in either nativeLibraryDir or filesDir
                        val hasDispatchLibInNative = java.io.File(libDir).listFiles()?.any {
                            it.name.startsWith("libLiteRtDispatch") && it.name.endsWith(".so")
                        } == true
                        val hasDispatchLibInFiles = context.filesDir.listFiles()?.any {
                            it.name.startsWith("libLiteRtDispatch") && it.name.endsWith(".so")
                        } == true
                        
                        if (!hasDispatchLibInNative && !hasDispatchLibInFiles) {
                            throw Exception("NPU Backend requested, but no LiteRT Dispatch library (libLiteRtDispatch_*.so) was found in $libDir or ${context.filesDir.absolutePath}.")
                        }
                        
                        val targetLibDir = if (hasDispatchLibInFiles) {
                            context.filesDir.absolutePath
                        } else {
                            libDir
                        }
                        Backend.NPU(targetLibDir)
                    }
                    BackendType.GPU -> Backend.GPU()
                    BackendType.CPU -> Backend.CPU() // Let LiteRT manage thread count optimally
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
                    Log.w("DaexService", "Failed to initialize with speculative decoding, retrying with it disabled", specEx)
                    try {
                        @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
                        com.google.ai.edge.litertlm.ExperimentalFlags.enableSpeculativeDecoding = false
                    } catch (e: Throwable) {}
                    
                    try {
                        newEngine?.close()
                    } catch (e: Throwable) {}
                    newEngine = Engine(config)
                    newEngine.initialize()
                }

                engine = newEngine
                isLoaded = true
                Log.d("DaexService", "LiteRT-LM Engine initialized successfully with $backendType")
                backendType
            } catch (e: Exception) {
                // Cascading Fallback: NPU -> GPU -> CPU
                when (backendType) {
                    BackendType.NPU -> {
                        Log.w("DaexService", "NPU initialization failed (missing TF_LITE_AUX), attempting fallback to GPU", e)
                        initContext(modelPath, BackendType.GPU)
                    }
                    BackendType.GPU -> {
                        Log.w("DaexService", "GPU initialization failed, attempting fallback to CPU", e)
                        initContext(modelPath, BackendType.CPU)
                    }
                    BackendType.CPU -> {
                        isLoaded = false
                        Log.e("DaexService", "Failed to initialize LiteRT-LM Engine on CPU", e)
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
                Log.e("DaexService", "Error closing conversation", e)
            } finally {
                conversation = null
            }

            try {
                engine?.close()
            } catch (e: Exception) {
                Log.e("DaexService", "Error closing engine", e)
            } finally {
                engine = null
            }
            
            isLoaded = false
            Log.d("DaexService", "Engine and conversation released")
        }
    }

    override suspend fun generateResponse(
        messages: List<Message>,
        systemContext: String,
        isReasoningEnabled: Boolean,
        temperature: Float,
        topK: Int,
        topP: Float,
        customSystemPrompt: String,
        isToolCallingEnabled: Boolean,
        onRequestPermission: (suspend (String, String) -> Boolean)?,
        onStatusUpdate: ((String?) -> Unit)?,
        onToken: (String) -> Unit
    ): GenerationResult {
        Log.i(TAG, "generateResponse: isToolCallingEnabled=$isToolCallingEnabled, temperature=$temperature, topK=$topK, topP=$topP, customPromptLength=${customSystemPrompt.length}")
        val activeEngine = engine ?: throw Exception("Model not loaded.")
        
        val systemInstructionText = buildString {
            if (customSystemPrompt.isNotBlank()) {
                append(customSystemPrompt)
                append("\n\n")
            } else {
                append("You are Icarus, running inside the Daedalus Execution Engine (DAEX). You are a high-performance AI assistant running directly on device hardware. You respond with precision and speed.\n")
                append("Do not self-reference as an AI, assistant, or mention 'Icarus' or 'DAEX' in your responses. Avoid meta-commentary about running on-device or your technical setup unless directly asked. Respond naturally and directly to the user.\n\n")
            }
            if (systemContext.isNotBlank()) {
                append("<global_memory>\n")
                append(systemContext)
                append("\n</global_memory>\n\n")
                append("The above is your persistent memory. Use it to personalize your responses. Do NOT mention your memory or refer to it, and do NOT attempt to update it yourself.\n")
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

        val samplerConfig = SamplerConfig(
            topK = topK,
            topP = topP.toDouble(),
            temperature = temperature.toDouble(),
            seed = 0
        )

        val tools = if (isToolCallingEnabled) {
            listOf(tool(DeviceTools(context, onRequestPermission, onStatusUpdate)))
        } else {
            emptyList()
        }

        val conversationConfig = ConversationConfig(
            systemInstruction = LiteRtContents.of(systemInstructionText),
            initialMessages = initialLiteRtMessages,
            channels = channels,
            samplerConfig = samplerConfig,
            automaticToolCalling = isToolCallingEnabled,
            tools = tools
        )

        // Close the previous conversation to start fresh with new history
        try {
            conversation?.close()
        } catch (e: Exception) {}

        val activeConversation = activeEngine.createConversation(conversationConfig)
        conversation = activeConversation

        var decodeStartTime = 0L
        var tokenCount = 0
        val fullText = StringBuilder()

        var hasStartedThinking = false
        var hasEndedThinking = false

        val extraContext = mapOf("enable_thinking" to isReasoningEnabled)

        withContext(Dispatchers.IO) {
            activeConversation.sendMessageAsync(activePrompt, extraContext).collect { reply ->
                val thinkingChunk = reply.channels["thinking"]
                val chunk = reply.contents.toString()

                val hasThinking = !thinkingChunk.isNullOrEmpty()
                val hasNormal = chunk.isNotEmpty()

                if (hasThinking || hasNormal) {
                    if (decodeStartTime == 0L) {
                        decodeStartTime = System.currentTimeMillis()
                    }
                }

                if (hasThinking) {
                    if (!hasStartedThinking) {
                        onToken("<|think|>")
                        hasStartedThinking = true
                    }
                    tokenCount++
                    onToken(thinkingChunk!!)
                }

                if (hasNormal) {
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

        val elapsedSeconds = if (decodeStartTime > 0L) {
            (System.currentTimeMillis() - decodeStartTime) / 1000.0
        } else {
            0.0
        }
        val tps = if (elapsedSeconds > 0.0) tokenCount / elapsedSeconds else 0.0

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
            Log.e("DaexService", "Failed to cancel process", e)
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

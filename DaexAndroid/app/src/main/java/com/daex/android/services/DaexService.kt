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
    val isCompacted: Boolean = false,
    val audioPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
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
        maxTokens: Int = 1024,
        isLiveVoiceActive: Boolean = false,
        conversationId: String? = null,
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

    /**
     * Everything about [conversation] that's baked into it at creation time via
     * ConversationConfig (system instruction, sampler config, channels, tools) and can't be
     * changed on a live Conversation - if any of it differs from the next call's request, the
     * conversation must be recreated rather than reused.
     */
    private data class ConversationSignature(
        val conversationId: String,
        val isLiveVoiceActive: Boolean,
        val isReasoningEnabled: Boolean,
        val isToolCallingEnabled: Boolean,
        val temperature: Float,
        val topK: Int,
        val topP: Float
    )

    private var conversationSignature: ConversationSignature? = null

    companion object {
        private const val TAG = "DaexService"

    }

    override suspend fun initContext(modelPath: String, backendType: BackendType, isSpeculativeDecodingEnabled: Boolean): BackendType {
        return withContext(Dispatchers.IO) {
            try {
                releaseContext()
                Log.d("DaexService", "Initializing LiteRT-LM Engine with model: $modelPath (backend=$backendType, speculative=$isSpeculativeDecodingEnabled)")
                
                // Set native log severity to FATAL to suppress noisy NPU dispatch warning logs
                // try {
                //     com.google.ai.edge.litertlm.Engine.setNativeMinLogSeverity(com.google.ai.edge.litertlm.LogSeverity.FATAL)
                // } catch (e: Throwable) {
                //     Log.w("DaexService", "Failed to set native log severity", e)
                // }

                // Enable Speculative Decoding / MTP drafters for high-performance inference
                try {
                    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
                    com.google.ai.edge.litertlm.ExperimentalFlags.enableSpeculativeDecoding = isSpeculativeDecodingEnabled
                } catch (e: Throwable) {
                    Log.w("DaexService", "Failed to set speculative decoding flag", e)
                }

                val backend = when (backendType) {
                    BackendType.NPU -> {
                        val targetLibDir = prepareDispatchLibrary()
                        Log.i(TAG, "Using dispatch library dir for NPU backend: $targetLibDir")
                        Backend.NPU(targetLibDir)
                    }
                    BackendType.GPU -> Backend.GPU()
                    BackendType.CPU -> Backend.CPU() // Let LiteRT manage thread count optimally
                }

                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = backend,
                    audioBackend = Backend.CPU(), // Audio preprocessor constraint requires CPU
                    maxNumTokens = 4096, // Safe default KV cache size that accommodates system prompt, RAG context, history and response
                    cacheDir = context.cacheDir.absolutePath
                )

                var newEngine: Engine? = null
                try {
                    newEngine = Engine(config)
                    newEngine.initialize()
                    try {
                        @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
                        com.google.ai.edge.litertlm.ExperimentalFlags.enableSpeculativeDecoding = false
                    } catch (e: Throwable) {}
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
                        initContext(modelPath, BackendType.GPU, isSpeculativeDecodingEnabled)
                    }
                    BackendType.GPU -> {
                        Log.w("DaexService", "GPU initialization failed, attempting fallback to CPU", e)
                        initContext(modelPath, BackendType.CPU, isSpeculativeDecodingEnabled)
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
                conversationSignature = null
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

    /** Thrown when a reused conversation fails before any token arrived - see [generateResponse]. */
    private class ReuseFailedException(cause: Throwable) : Exception(cause)

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
        maxTokens: Int,
        isLiveVoiceActive: Boolean,
        conversationId: String?,
        onToken: (String) -> Unit
    ): GenerationResult {
        return try {
            generateResponseAttempt(
                messages, systemContext, isReasoningEnabled, temperature, topK, topP,
                customSystemPrompt, isToolCallingEnabled, onRequestPermission, onStatusUpdate,
                maxTokens, isLiveVoiceActive, conversationId, allowReuse = true, onToken
            )
        } catch (e: ReuseFailedException) {
            // The reused conversation didn't work (e.g. left unusable by a prior cancelProcess()
            // call - not something confirmable from the library's compiled API alone). No tokens
            // reached the caller yet, so retrying from scratch is safe and invisible to the user.
            Log.w(TAG, "Reused conversation failed before producing any tokens, retrying with a fresh one", e.cause)
            generateResponseAttempt(
                messages, systemContext, isReasoningEnabled, temperature, topK, topP,
                customSystemPrompt, isToolCallingEnabled, onRequestPermission, onStatusUpdate,
                maxTokens, isLiveVoiceActive, conversationId, allowReuse = false, onToken
            )
        }
    }

    private suspend fun generateResponseAttempt(
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
        maxTokens: Int,
        isLiveVoiceActive: Boolean,
        conversationId: String?,
        allowReuse: Boolean,
        onToken: (String) -> Unit
    ): GenerationResult {
        Log.i(TAG, "generateResponse: isToolCallingEnabled=$isToolCallingEnabled, temperature=$temperature, topK=$topK, topP=$topP, customPromptLength=${customSystemPrompt.length}")
        val activeEngine = engine ?: throw Exception("Model not loaded.")

        val activePrompt = messages.lastOrNull()?.content ?: ""

        val requestedSignature = conversationId?.let {
            ConversationSignature(
                conversationId = it,
                isLiveVoiceActive = isLiveVoiceActive,
                isReasoningEnabled = isReasoningEnabled,
                isToolCallingEnabled = isToolCallingEnabled,
                temperature = temperature,
                topK = topK,
                topP = topP
            )
        }

        // Only live-voice turns are eligible for reuse today - text chat carries more state
        // (RAG context, per-message relative timestamps) that can legitimately change turn to
        // turn and would need more invalidation logic than this covers.
        val usedReuse = allowReuse &&
            isLiveVoiceActive &&
            requestedSignature != null &&
            requestedSignature == conversationSignature &&
            conversation?.isAlive == true

        val activeConversation: Conversation = if (usedReuse) {
            Log.d(TAG, "Reusing existing conversation (id=$conversationId) - skipping full history re-prefill")
            conversation!!
        } else {
            val systemInstructionText = buildString {
                if (customSystemPrompt.isNotBlank()) {
                    append(customSystemPrompt)
                    append("\n\n")
                } else {
                    append("You are Icarus, running inside the Daedalus Execution Engine (DAEX). You are a high-performance AI assistant running directly on device hardware. You respond with precision and speed.\n")
                    append("Do not self-reference as an AI, assistant, or mention 'Icarus' or 'DAEX' in your responses. Avoid meta-commentary about running on-device or your technical setup unless directly asked. Respond naturally and directly to the user.\n")
                    if (!isLiveVoiceActive) {
                        append("In the chat history, turns are prefixed with dynamic relative timestamps indicating elapsed time (e.g. '[5m ago]', '[1h ago]'). Do NOT include any timestamp prefixes in your new response.\n\n")
                    } else {
                        append("\n")
                    }
                }

                try {
                    val formatter = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy, h:mm a", java.util.Locale.getDefault())
                    val currentDateTimeStr = formatter.format(java.util.Date())
                    append("Current Reference Time: $currentDateTimeStr\n\n")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to format current date time for system prompt", e)
                }

                if (systemContext.isNotBlank()) {
                    append("<global_memory>\n")
                    append(systemContext)
                    append("\n</global_memory>\n\n")
                    append("The above is your persistent memory. Use it to personalize your responses. Do NOT mention your memory or refer to it, and do NOT attempt to update it yourself.\n")
                }

                if (maxTokens <= 256) {
                    append("\nIMPORTANT CONSTRAINT: The user has requested an extremely brief response. Keep both your internal thinking/reasoning and final response very short, concise, and direct (under 50 words).\n")
                } else if (maxTokens <= 512) {
                    append("\nIMPORTANT CONSTRAINT: The user has requested a concise response. Keep your reasoning and final response relatively brief.\n")
                }
            }

            // Separate user's latest prompt from the conversation history
            val history = messages.dropLast(1)
            val now = System.currentTimeMillis()
            val initialLiteRtMessages = history.map { msg ->
                val contentWithTime = if (isLiveVoiceActive) {
                    msg.content
                } else {
                    val diffSec = (now - msg.timestamp) / 1000
                    val relativeTime = when {
                        diffSec < 0 -> "just now"
                        diffSec < 60 -> "just now"
                        diffSec < 3600 -> "${diffSec / 60}m ago"
                        diffSec < 86400 -> "${diffSec / 3600}h ago"
                        else -> "${diffSec / 86400}d ago"
                    }
                    "[$relativeTime] ${msg.content}"
                }

                when (msg.role) {
                    "user" -> {
                        if (msg.audioPath != null) {
                            val file = java.io.File(msg.audioPath)
                            if (file.exists() && file.length() > 44) {
                                val audioContent = com.google.ai.edge.litertlm.Content.AudioFile(msg.audioPath)
                                val contents = if (msg.content.isNotBlank()) {
                                    val textContent = com.google.ai.edge.litertlm.Content.Text(msg.content)
                                    LiteRtContents.of(audioContent, textContent)
                                } else {
                                    LiteRtContents.of(audioContent)
                                }
                                LiteRtMessage.user(contents)
                            } else {
                                val fallbackText = if (msg.content.isNotBlank()) msg.content else "[Voice Audio Unavailable]"
                                LiteRtMessage.user(fallbackText)
                            }
                        } else {
                            LiteRtMessage.user(contentWithTime)
                        }
                    }
                    "model" -> LiteRtMessage.model(LiteRtContents.of(contentWithTime))
                    else -> LiteRtMessage.user(contentWithTime)
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

            val created = activeEngine.createConversation(conversationConfig)
            conversation = created
            conversationSignature = requestedSignature
            created
        }

        var decodeStartTime = 0L
        var responseTokenCount = 0
        var isLimitReached = false
        val fullText = StringBuilder()
        val allGeneratedText = StringBuilder()

        var hasStartedThinking = false
        var hasEndedThinking = false

        val extraContext = mapOf("enable_thinking" to isReasoningEnabled)

        try {
            withContext(Dispatchers.IO) {
                val lastMsg = messages.lastOrNull()
                val flow = if (lastMsg != null && lastMsg.audioPath != null) {
                    val audioContent = com.google.ai.edge.litertlm.Content.AudioFile(lastMsg.audioPath)
                    val contents = if (lastMsg.content.isNotBlank()) {
                        val textContent = com.google.ai.edge.litertlm.Content.Text(lastMsg.content)
                        LiteRtContents.of(audioContent, textContent)
                    } else {
                        LiteRtContents.of(audioContent)
                    }
                    activeConversation.sendMessageAsync(contents, extraContext)
                } else {
                    activeConversation.sendMessageAsync(activePrompt, extraContext)
                }

                flow.collect { reply ->
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
                            allGeneratedText.append("<|think|>")
                        }
                        onToken(thinkingChunk!!)
                        allGeneratedText.append(thinkingChunk)
                    }

                    if (hasNormal) {
                        if (hasStartedThinking && !hasEndedThinking) {
                            onToken("</think|>")
                            hasEndedThinking = true
                            allGeneratedText.append("</think|>")
                        }
                        responseTokenCount++
                        fullText.append(chunk)
                        allGeneratedText.append(chunk)
                        onToken(chunk)

                        if (responseTokenCount >= maxTokens) {
                            isLimitReached = true
                            cancelGeneration()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            val isCancellation = e is java.util.concurrent.CancellationException ||
                                 e is kotlinx.coroutines.CancellationException ||
                                 e.message?.contains("cancel", ignoreCase = true) == true
            when {
                isLimitReached -> {
                    // Our own maxTokens auto-stop: package up what was generated as a normal
                    // successful result rather than an error.
                    Log.d(TAG, "Generation stopped: max token limit reached.")
                }
                isCancellation -> {
                    // A genuine external cancel (user tapped stop, or a live-voice barge-in) -
                    // this used to be swallowed the same as the maxTokens case above, which
                    // made every user-initiated stop look like a normal completion: the
                    // "[Generation stopped by user]" marker never appeared, and barge-in never
                    // actually silenced the assistant. Propagate it so the caller's existing
                    // cancellation handling actually runs.
                    throw e
                }
                usedReuse && decodeStartTime == 0L -> {
                    conversation = null
                    conversationSignature = null
                    throw ReuseFailedException(e)
                }
                else -> throw e
            }
        }

        if (hasStartedThinking && !hasEndedThinking) {
            onToken("</think|>")
            allGeneratedText.append("</think|>")
        }

        val elapsedSeconds = if (decodeStartTime > 0L) {
            (System.currentTimeMillis() - decodeStartTime) / 1000.0
        } else {
            0.0
        }
        val estimatedTokens = estimateTokenCount(allGeneratedText.toString())
        val tps = if (elapsedSeconds > 0.0) estimatedTokens / elapsedSeconds else 0.0

        return GenerationResult(
            text = fullText.toString(),
            tokensPerSecond = Math.round(tps * 10.0) / 10.0
        )
    }

    private fun estimateTokenCount(text: String): Double {
        val wordCount = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val wordEstimator = wordCount * 1.3
        val charEstimator = text.length / 4.0
        return maxOf(wordEstimator, charEstimator)
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

    private fun getBestDispatchLibraryName(): String {
        val socModel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.os.Build.SOC_MODEL.lowercase()
        } else {
            ""
        }
        val hardware = android.os.Build.HARDWARE.lowercase()
        val board = android.os.Build.BOARD.lowercase()

        Log.d(TAG, "Detecting SoC - SOC_MODEL: $socModel, HARDWARE: $hardware, BOARD: $board")

        return when {
            socModel.contains("tensor") || socModel.contains("google") ||
            hardware.contains("gs") || hardware.contains("g5") || hardware.contains("tensor") ||
            board.contains("gs") || board.contains("rango") || board.contains("comet") || board.contains("caiman") || board.contains("tokay") || board.contains("komodo") -> {
                "libLiteRtDispatch_GoogleTensor.so"
            }
            socModel.contains("mediatek") || socModel.contains("dimensity") || socModel.startsWith("mt") ||
            hardware.contains("mt") || hardware.contains("mediatek") ||
            board.contains("mt") -> {
                "libLiteRtDispatch_MediaTek.so"
            }
            else -> {
                "libLiteRtDispatch_Qualcomm.so"
            }
        }
    }

    private fun prepareDispatchLibrary(): String {
        val libName = getBestDispatchLibraryName()
        Log.i(TAG, "Selected dispatch library for this device: $libName")
        
        val dispatchDir = java.io.File(context.filesDir, "dispatch")
        if (!dispatchDir.exists()) {
            dispatchDir.mkdirs()
        }
        
        // Clean up all files in the dispatch directory to ensure only one library is present
        dispatchDir.listFiles()?.forEach { file ->
            try {
                file.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete old dispatch file: ${file.name}", e)
            }
        }
        
        val sourcePath = "${context.applicationInfo.nativeLibraryDir}/$libName"
        val targetFile = java.io.File(dispatchDir, libName)
        
        try {
            Log.i(TAG, "Creating symlink from ${targetFile.absolutePath} pointing to $sourcePath")
            android.system.Os.symlink(sourcePath, targetFile.absolutePath)
            Log.i(TAG, "Successfully created symlink for NPU dispatch library.")
        } catch (symEx: Exception) {
            Log.w(TAG, "Failed to create symlink, falling back to copying file", symEx)
            try {
                java.io.File(sourcePath).inputStream().use { input ->
                    java.io.FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Successfully copied NPU dispatch library as fallback.")
            } catch (copyEx: Exception) {
                Log.e(TAG, "Failed to copy NPU dispatch library", copyEx)
                // Return nativeLibraryDir directly as a last resort
                return context.applicationInfo.nativeLibraryDir
            }
        }
        
        return dispatchDir.absolutePath
    }
}

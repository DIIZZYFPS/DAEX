package com.daex.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daex.android.data.Conversation
import com.daex.android.data.DaexMemory
import com.daex.android.data.DaexPreferences
import com.daex.android.data.DaexRag
import com.daex.android.data.DaexSkillManager
import com.daex.android.data.DownloadPhase
import com.daex.android.data.GenerativeDownloadStatus
import com.daex.android.data.ModelDownloadState
import com.daex.android.data.ModelManager
import com.daex.android.domain.ModelBank
import com.daex.android.domain.PermissionRequest
import com.daex.android.framework.DaexCoreMemory
import com.daex.android.framework.DaexService
import com.daex.android.framework.DaexServiceImpl
import com.daex.android.framework.Message
import com.daex.android.framework.ModelDownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ~30fps: fast enough that throttled streaming text looks continuous, far less often than per-token.
private const val STREAM_UI_THROTTLE_MS = 33L

/**
 * Owns messages, conversations, generation, RAG/document context, and memory curation. Depends
 * on [SettingsViewModel] for inference parameters + model lifecycle and on [TtsViewModel] for
 * live-voice sentence speaking - both are constructed before this class, so those are plain
 * constructor references. It does NOT own live-voice session state (that's
 * AudioSessionViewModel, constructed after this class) - [isLiveVoiceActive], [currentVoiceState],
 * [setVoiceState] and [abortLiveVoiceSession] are hooks AudioSessionViewModel wires post-
 * construction so this class can participate in the live-voice loop without a reference back to it.
 */
class ChatViewModel(
    private val daexService: DaexService,
    private val modelManager: ModelManager? = null,
    private val daexMemory: DaexMemory? = null,
    private val daexCoreMemory: DaexCoreMemory? = null,
    private val preferences: DaexPreferences? = null,
    private val daexRag: DaexRag? = null,
    private val daexSkillManager: DaexSkillManager? = null,
    private val context: android.content.Context? = null,
    private val settingsViewModel: SettingsViewModel,
    private val ttsViewModel: TtsViewModel
) : ViewModel() {

    var isLiveVoiceActive: () -> Boolean = { false }
    var currentVoiceState: () -> VoiceState = { VoiceState.IDLE }
    var setVoiceState: ((VoiceState) -> Unit)? = null
    var abortLiveVoiceSession: (() -> Unit)? = null

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isReflecting = MutableStateFlow(false)
    val isReflecting: StateFlow<Boolean> = _isReflecting.asStateFlow()

    private val _isVectorizing = MutableStateFlow(false)
    val isVectorizing: StateFlow<Boolean> = _isVectorizing.asStateFlow()

    private val _uploadedFiles = MutableStateFlow<List<String>>(emptyList())
    val uploadedFiles: StateFlow<List<String>> = _uploadedFiles.asStateFlow()

    private val _embeddingDownloadProgress = MutableStateFlow<Int?>(null)
    val embeddingDownloadProgress: StateFlow<Int?> = _embeddingDownloadProgress.asStateFlow()

    private val _pinnedMessages = MutableStateFlow<List<Message>>(emptyList())
    val pinnedMessages: StateFlow<List<Message>> = _pinnedMessages.asStateFlow()

    private val _attachedFiles = MutableStateFlow<List<String>>(emptyList())
    val attachedFiles: StateFlow<List<String>> = _attachedFiles.asStateFlow()

    private val _tokenSpeed = MutableStateFlow(0.0)
    val tokenSpeed: StateFlow<Double> = _tokenSpeed.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _activePermission = MutableStateFlow<PermissionRequest?>(null)
    val activePermission: StateFlow<PermissionRequest?> = _activePermission.asStateFlow()

    private val _coreMemoryText = MutableStateFlow("")
    val coreMemoryText: StateFlow<String> = _coreMemoryText.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    // Sidebar cross-conversation search - null means "no search active, show the full list";
    // an empty list means "searched, nothing matched". Purely user-initiated (see searchConversations).
    private val _conversationSearchResults = MutableStateFlow<List<Conversation>?>(null)
    val conversationSearchResults: StateFlow<List<Conversation>?> = _conversationSearchResults.asStateFlow()

    private val _suggestedPrompts = MutableStateFlow<List<String>>(
        listOf(
            "Explain quantum entanglement simply",
            "Write a haiku about midnight code",
            "Plan a 3-day trip to Lisbon"
        )
    )
    val suggestedPrompts: StateFlow<List<String>> = _suggestedPrompts.asStateFlow()

    private var searchJob: Job? = null
    private var generationJob: Job? = null
    private var curationJob: Job? = null

    // A chunk that finished while the model was still generating; submitted as the
    // next turn when the current generation completes, instead of being dropped.
    @Volatile private var pendingAudioPath: String? = null

    /**
     * Extracts <think>/<|think|>/<|channel> blocks from streamed text, returning
     * (visibleText, thoughtText). Re-scans from the start of [rawText] every call by design -
     * this is what lets a tag split across two token deliveries (e.g. "<th" then "ink>") still
     * be recognized correctly. Kept as a single full-rescan (not an incremental parser) since
     * StreamingUpdater below throttles how often it's called instead of rewriting the algorithm.
     */
    private fun parseThinkTags(rawText: String): Pair<String, String?> {
        val thinkTags = listOf(
            Pair("<|think|>", "</think|>"),
            Pair("<think>", "</think>"),
            Pair("<|channel>", "<channel|>")
        )

        val extractedThoughts = mutableListOf<String>()
        val modifiedText = StringBuilder()

        var i = 0
        while (i < rawText.length) {
            var foundTag = false
            for (tagPair in thinkTags) {
                if (rawText.startsWith(tagPair.first, i)) {
                    val startIdx = i + tagPair.first.length
                    val endIdx = rawText.indexOf(tagPair.second, startIdx)
                    if (endIdx != -1) {
                        val content = rawText.substring(startIdx, endIdx).trim()
                        if (content.isNotEmpty()) {
                            extractedThoughts.add(content)
                        }
                        i = endIdx + tagPair.second.length
                        foundTag = true
                        break
                    } else {
                        val content = rawText.substring(startIdx).trim()
                        if (content.isNotEmpty()) {
                            extractedThoughts.add(content)
                        }
                        i = rawText.length
                        foundTag = true
                        break
                    }
                }
            }
            if (!foundTag) {
                modifiedText.append(rawText[i])
                i++
            }
        }

        val thought = if (extractedThoughts.isNotEmpty()) extractedThoughts.joinToString("\n\n") else null
        return Pair(modifiedText.toString(), thought)
    }

    /**
     * Accumulates streamed tokens and re-publishes the parsed message content to [_messages] at
     * most every [STREAM_UI_THROTTLE_MS] - the tag-rescan + full message-list copy this triggers
     * is O(n) / O(m) respectively, so doing it on every token is O(n^2) / O(tokens * messages)
     * over a full generation. One instance per generation call; [finalFlush] guarantees the
     * saved message reflects the complete text regardless of when the last throttled tick fell.
     */
    private inner class StreamingUpdater(private val modelMsgId: String) {
        private val rawText = StringBuilder()
        private var lastUpdateMs = 0L
        var lastActual: String = ""
            private set

        /** Returns true if this call actually re-parsed and published (i.e. wasn't throttled). */
        fun onToken(token: String): Boolean {
            rawText.append(token)
            val now = System.currentTimeMillis()
            if (now - lastUpdateMs < STREAM_UI_THROTTLE_MS) return false
            lastUpdateMs = now
            publish()
            return true
        }

        fun finalFlush() {
            publish()
        }

        private fun publish() {
            val (actual, thought) = parseThinkTags(rawText.toString())
            lastActual = actual.trimStart()
            val updated = _messages.value.toMutableList()
            val idx = updated.indexOfFirst { it.id == modelMsgId }
            if (idx != -1) {
                updated[idx] = updated[idx].copy(content = lastActual, thoughtContent = thought)
                _messages.value = updated
            }
        }
    }

    init {
        // Lets SettingsViewModel keep blocking model/backend changes while a generation,
        // memory curation, or file vectorization is in flight, without it needing a reference
        // back to this class.
        settingsViewModel.isChatBusy = { _isGenerating.value || _isReflecting.value || _isVectorizing.value }

        // Once SettingsViewModel reaches READY for the first time, decide whether to generate
        // personalized suggestions or seed the cold-start pool - this class owns conversation
        // history, SettingsViewModel doesn't.
        settingsViewModel.onModelReadyForFirstTime = {
            val currentSuggestions = _suggestedPrompts.value
            val defaultList = listOf(
                "Explain quantum entanglement simply",
                "Write a haiku about midnight code",
                "Plan a 3-day trip to Lisbon"
            )
            if (currentSuggestions == defaultList) {
                if (_conversations.value.isNotEmpty()) {
                    generateSuggestedPrompts()
                } else {
                    // No history anywhere yet - a blind generateSuggestedPrompts() call here
                    // just produces mediocre generic output; seed from the fixed cold-start
                    // pool instead (see seedColdStartPromptsIfNeeded).
                    seedColdStartPromptsIfNeeded()
                }
            }
        }

        viewModelScope.launch {
            preferences?.suggestedPromptsFlow?.collectLatest { list ->
                _suggestedPrompts.value = list
            }
        }

        // Silent Background Initialization of the Embedding Model. Runs on its own independent
        // download lane (ModelDownloadState.embedding / ModelManager.downloadEmbeddingModel),
        // separate from the chat-model generative lane, so it downloads concurrently with a
        // mandatory first-run chat model download instead of waiting for that slot to free up -
        // that wait would otherwise defer it past the exact moment cold-start grounding (see
        // ingestAboutDocIfNeeded) needs it: the user's very first interaction.
        viewModelScope.launch {
            if (modelManager != null && daexRag != null && context != null) {
                val embedModel = ModelBank.embeddingModel
                val isDownloaded = modelManager.isModelDownloaded(embedModel)

                if (isDownloaded) {
                    try {
                        daexRag.initRag()
                        ingestAboutDocIfNeeded(daexRag)
                        seedColdStartPromptsIfNeeded()
                    } catch (e: Exception) {
                        // Handle potential load failures
                    }
                } else if (ModelDownloadState.embedding.value?.phase != DownloadPhase.DOWNLOADING) {
                    val requestId = ModelDownloadService.startEmbeddingDownload(context)
                    val status = awaitEmbeddingDownload(requestId) { percent ->
                        _embeddingDownloadProgress.value = percent
                    }
                    _embeddingDownloadProgress.value = null
                    if (status.phase == DownloadPhase.COMPLETED) {
                        settingsViewModel.refreshDownloadedModels()
                        try {
                            daexRag.initRag()
                            ingestAboutDocIfNeeded(daexRag)
                            // Ingestion just completed - if the user is still looking at a
                            // brand-new, empty conversation, refresh the seeded suggestions so
                            // grounding is available by the time they act on one.
                            seedColdStartPromptsIfNeeded()
                        } catch (e: Exception) {
                            // Handle potential load failures
                        }
                    }
                }

                // One-time backfill: embed/index messages saved before cross-conversation search
                // shipped, so old conversations become searchable too, not just new ones.
                if (daexMemory != null && preferences?.messageBackfillDoneFlow?.first() == false) {
                    try {
                        daexMemory.backfillMissingEmbeddings()
                        preferences.setMessageBackfillDone()
                    } catch (e: Exception) {
                        android.util.Log.e("ChatViewModel", "Message embedding backfill failed", e)
                    }
                }
            }
        }

        refreshConversations()
    }

    /** Mirrors [SettingsViewModel.awaitGenerativeDownload] for the embedding-model download lane. */
    private suspend fun awaitEmbeddingDownload(requestId: String, onProgress: (Int) -> Unit): GenerativeDownloadStatus = coroutineScope {
        val mirrorJob = launch {
            ModelDownloadState.embedding.collect { status ->
                if (status?.requestId == requestId && status.phase == DownloadPhase.DOWNLOADING) {
                    onProgress(status.percent)
                }
            }
        }
        val terminal = ModelDownloadState.embedding
            .first { it?.requestId == requestId && it.phase != DownloadPhase.DOWNLOADING }!!
        mirrorJob.cancel()
        terminal
    }

    fun refreshConversations() {
        viewModelScope.launch {
            _conversations.value = daexMemory?.getAllConversationsList() ?: emptyList()
        }
    }

    /**
     * Debounced Sidebar search across every conversation's messages (hybrid vector+BM25 -
     * see DaexMemory.searchMessages). Blank query clears back to the normal full list.
     */
    fun searchConversations(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _conversationSearchResults.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            val hits = daexMemory?.searchMessages(query) ?: emptyList()
            val byId = _conversations.value.associateBy { it.id }
            _conversationSearchResults.value = hits
                .mapNotNull { byId[it.conversationId] }
                .distinct()
        }
    }

    fun selectConversation(id: String) {
        _currentConversationId.value = id
        // Optionally load the model associated with the conversation
        viewModelScope.launch {
            val conv = _conversations.value.find { it.id == id }
            if (conv != null) {
                _attachedFiles.value = conv.attachedFileNames
                val model = ModelBank.generativeModels.find { it.id == conv.modelId }
                if (model != null && settingsViewModel.currentModel.value?.id != model.id) {
                    settingsViewModel.loadModel(model)
                }
            } else {
                _attachedFiles.value = emptyList()
            }
            _messages.value = daexMemory?.getMessagesForConversationList(id) ?: emptyList()
        }
    }

    suspend fun requestPermission(toolName: String, description: String): Boolean {
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        _activePermission.value = PermissionRequest(toolName, description, deferred)
        return try {
            deferred.await()
        } finally {
            _activePermission.value = null
        }
    }

    fun submitPrompt(prompt: String) {
        if (prompt.isBlank() || _isGenerating.value) return
        if (settingsViewModel.modelStatus.value != ModelStatus.READY || !daexService.isLoaded()) {
            _errorMessage.value = "Model is not loaded yet."
            return
        }

        viewModelScope.launch {
            // Wait for any in-flight background memory curation to fully stop before starting a
            // new generation - both drive the same DaexServiceImpl engine/conversation, and a
            // bare cancel() (without join) only requests cancellation, it doesn't guarantee
            // curation has actually stopped touching the engine by the time generation starts.
            curationJob?.cancelAndJoin()

            var convId = _currentConversationId.value
            if (convId == null) {
                val modelId = settingsViewModel.currentModel.value?.id ?: ModelBank.generativeModels.first().id
                convId = daexMemory?.createConversation(modelId, prompt.take(20) + "...")
                _currentConversationId.value = convId
                if (convId != null && _attachedFiles.value.isNotEmpty()) {
                    daexMemory?.updateAttachedFiles(convId, _attachedFiles.value)
                }
                refreshConversations()
            }

            if (convId == null) return@launch

            val userMsgId: String
            val modelMsgId: String
            val userMsg: Message
            val modelMsg: Message

            val currentMsgs = _messages.value
            val lastUserIdx = currentMsgs.indexOfLast { it.role == "user" }
            val lastModelIdx = currentMsgs.indexOfLast { it.role == "model" }
            val lastMsgIsStopped = lastModelIdx != -1 &&
                    currentMsgs[lastModelIdx].content.contains("[Generation stopped by user]")

            if (lastMsgIsStopped && lastUserIdx != -1 && lastModelIdx > lastUserIdx) {
                // Edit last turn in-place in DB and memory
                val oldUserMsg = currentMsgs[lastUserIdx]
                val oldModelMsg = currentMsgs[lastModelIdx]

                userMsgId = oldUserMsg.id
                modelMsgId = oldModelMsg.id

                userMsg = oldUserMsg.copy(content = prompt, timestamp = System.currentTimeMillis())
                modelMsg = oldModelMsg.copy(content = "", thoughtContent = null, tokensPerSecond = 0.0, timestamp = System.currentTimeMillis() + 1)

                val updated = currentMsgs.toMutableList()
                updated[lastUserIdx] = userMsg
                updated[lastModelIdx] = modelMsg
                _messages.value = updated

                daexMemory?.saveMessageWithEmbedding(convId, userMsg)
                daexMemory?.saveMessage(convId, modelMsg)
            } else {
                // Create new message turn
                userMsgId = System.currentTimeMillis().toString()
                modelMsgId = (System.currentTimeMillis() + 1).toString()

                userMsg = Message(id = userMsgId, role = "user", content = prompt)
                modelMsg = Message(id = modelMsgId, role = "model", content = "")

                _messages.value = _messages.value + listOf(userMsg, modelMsg)

                daexMemory?.saveMessageWithEmbedding(convId, userMsg)
                daexMemory?.saveMessage(convId, modelMsg)
            }

            _isGenerating.value = true
            _tokenSpeed.value = 0.0
            settingsViewModel.triggerHapticFeedback(type = HapticType.START_RESPONSE)

            generationJob = viewModelScope.launch {
                try {
                    // Filter out the placeholder model message and system logs from history sent to model
                    val fullHistory = (daexMemory?.getRecentHistory(convId) ?: emptyList())
                        .filter { it.id != modelMsgId && it.role != "system" }

                    // TOKEN-BASED COMPACTION & PRESSURE TRACKING
                    var activeHistory = fullHistory.filter { !it.isCompacted }
                    val maxContextLimit = settingsViewModel.currentModel.value?.maxContextTokens ?: 8192

                    var currentTokens = activeHistory.sumOf { estimateMessageTokens(it) }

                    if (currentTokens > maxContextLimit / 2) {
                        android.util.Log.i("ChatViewModel", "Context pressure warning: $currentTokens tokens. Triggering compaction check...")
                        // 1. Cheap local pruning pass
                        val pruned = pruneToolOutputs(activeHistory)
                        val prunedTokens = pruned.sumOf { estimateMessageTokens(it) }

                        if (prunedTokens <= maxContextLimit / 2) {
                            android.util.Log.i("ChatViewModel", "Local pruning reduced tokens to $prunedTokens. Saving pruned logs...")
                            for (i in activeHistory.indices) {
                                if (activeHistory[i].content != pruned[i].content) {
                                    daexMemory?.saveMessage(convId, pruned[i])
                                }
                            }
                            activeHistory = pruned
                        } else {
                            // 2. Perform deep on-device compaction
                            _isReflecting.value = true
                            try {
                                performCompaction(convId, fullHistory, maxContextLimit)
                                // Reload active history (ignoring compacted turns, including new summary)
                                activeHistory = (daexMemory?.getRecentHistory(convId) ?: emptyList())
                                    .filter { it.id != modelMsgId && !it.isCompacted }
                            } catch (e: Exception) {
                                android.util.Log.e("ChatViewModel", "Context compaction failed", e)
                            } finally {
                                _isReflecting.value = false
                            }
                        }
                    }

                    val inferenceHistory = activeHistory.toMutableList()

                    val streamingUpdater = StreamingUpdater(modelMsgId)
                    val coreMemoryContent = daexCoreMemory?.getMemoryContent() ?: ""

                    // --- FILE RAG CONTEXT INJECTION ---
                    var systemContext = coreMemoryContent
                    if (daexRag != null && daexRag.hasDocuments() && _attachedFiles.value.isNotEmpty()) {
                        try {
                            val relevantChunks = daexRag.queryDocuments(
                                query = prompt,
                                activeFileNames = _attachedFiles.value
                            )
                            if (relevantChunks.isNotEmpty()) {
                                val contextBlock = relevantChunks.joinToString("\n---\n")
                                systemContext += "\n\n<uploaded_documents>\n$contextBlock\n</uploaded_documents>\n"
                                systemContext += "Use the above document excerpts to help answer the user's query. If the excerpts are not relevant, ignore them.\n"
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ChatViewModel", "RAG query failed, continuing without context", e)
                        }
                    }

                    // --- COLD-START ABOUT-DAEX GROUNDING ---
                    // First-ever turn, matching one of the bundled cold-start starter questions
                    // (see seedColdStartPromptsIfNeeded): pull RAG context from the bundled
                    // about-DAEX system document explicitly, since it's hidden from the
                    // user-facing document list and so never appears in _attachedFiles / the
                    // gate above.
                    if (daexRag != null && fullHistory.size <= 1 && prompt.trim() in DaexPreferences.COLD_START_QUESTIONS) {
                        try {
                            val aboutChunks = daexRag.queryDocuments(
                                query = prompt,
                                activeFileNames = listOf(DaexPreferences.ABOUT_DAEX_FILENAME)
                            )
                            if (aboutChunks.isNotEmpty()) {
                                val contextBlock = aboutChunks.joinToString("\n---\n")
                                systemContext += "\n\n<about_daex>\n$contextBlock\n</about_daex>\n"
                                systemContext += "Use the above excerpts to answer the user's question about your own capabilities accurately.\n"
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ChatViewModel", "About-DAEX RAG query failed, continuing without it", e)
                        }
                    }

                    // --- MODULAR SKILLS INFO INJECTION ---
                    if (daexSkillManager != null) {
                        systemContext += "\n\nYou have domain-specific \"skills\" (additional instructions/parameters) available. If you need a special skill or want to see what is available, call the listSkills() tool. If you find a matching skill, call the loadSkill(skillName) tool to retrieve its instructions.\n"
                    }
                    val result = daexService.generateResponse(
                        messages = inferenceHistory,
                        systemContext = systemContext,
                        isReasoningEnabled = settingsViewModel.isReasoningEnabled.value,
                        temperature = settingsViewModel.inferenceTemperature.value,
                        topK = settingsViewModel.inferenceTopK.value,
                        topP = settingsViewModel.inferenceTopP.value,
                        customSystemPrompt = settingsViewModel.customSystemPrompt.value,
                        isToolCallingEnabled = settingsViewModel.isToolCallingEnabled.value,
                        disabledToolIds = settingsViewModel.disabledToolIds.value,
                        onRequestPermission = { toolName, description ->
                            requestPermission(toolName, description)
                        },
                        onStatusUpdate = { status ->
                            val updated = _messages.value.toMutableList()
                            val idx = updated.indexOfFirst { it.id == modelMsgId }
                            if (idx != -1) {
                                updated[idx] = updated[idx].copy(toolStatus = status)
                                _messages.value = updated
                            }
                            if (status != null) {
                                val convId = _currentConversationId.value
                                if (convId != null) {
                                    val logMsgId = "log_" + System.currentTimeMillis()
                                    val logMsg = Message(
                                        id = logMsgId,
                                        role = "system",
                                        content = "[SYSTEM_LOG]: ${status.uppercase()}"
                                    )
                                    _messages.value = _messages.value + logMsg
                                    viewModelScope.launch {
                                        daexMemory?.saveMessage(convId, logMsg)
                                    }
                                }
                            } else {
                                val convId = _currentConversationId.value
                                if (convId != null) {
                                    val messagesCopy = _messages.value.toMutableList()
                                    val logIdx = messagesCopy.indexOfLast {
                                        it.role == "system" && it.content.startsWith("[SYSTEM_LOG]:") && it.content.endsWith("...")
                                    }
                                    if (logIdx != -1) {
                                        val oldMsg = messagesCopy[logIdx]
                                        val cleanContent = oldMsg.content.removeSuffix("...")
                                        val updatedMsg = oldMsg.copy(content = cleanContent)
                                        messagesCopy[logIdx] = updatedMsg
                                        _messages.value = messagesCopy
                                        viewModelScope.launch {
                                            daexMemory?.saveMessage(convId, updatedMsg)
                                        }
                                    }
                                }
                            }
                        },
                        maxTokens = settingsViewModel.maxTokens.value
                    ) { token ->
                        if (!isActive) return@generateResponse
                        streamingUpdater.onToken(token)
                    }
                    streamingUpdater.finalFlush()
                    _tokenSpeed.value = result.tokensPerSecond
                    settingsViewModel.triggerHapticFeedback(type = HapticType.SUCCESS_COMPLETION)

                    // Save final result to DB
                    val updatedList = _messages.value
                    val finalMsg = updatedList.find { it.id == modelMsgId }
                    if (finalMsg != null) {
                        val finalModelMsg = finalMsg.copy(tokensPerSecond = result.tokensPerSecond)
                        daexMemory?.saveMessageWithEmbedding(convId, finalModelMsg)
                    }

                    // --- DEBUNCED GLOBAL MEMORY CURATION TRIGGER ---
                    curationJob?.cancel()
                    curationJob = viewModelScope.launch {
                        delay(90000) // 90 seconds inactivity
                        if (daexCoreMemory != null) {
                            _isReflecting.value = true
                            val logMsgId = "log_" + System.currentTimeMillis()
                            var logMsg = Message(
                                id = logMsgId,
                                role = "system",
                                content = "[SYSTEM_LOG]: CURATING GLOBAL MEMORY..."
                            )
                            _messages.value = _messages.value + logMsg
                            daexMemory?.saveMessage(convId, logMsg)

                            try {
                                val recentMsgs = daexMemory?.getRecentHistory(convId, limit = 20) ?: emptyList()
                                val curationResult = daexCoreMemory.compactMemory(recentMsgs, daexService)
                                logMsg = logMsg.copy(content = if (curationResult.learnedBullets.isNotEmpty()) {
                                    "[SYSTEM_LOG]: MEMORY UPDATED — learned: " + curationResult.learnedBullets.joinToString("; ")
                                } else {
                                    "[SYSTEM_LOG]: GLOBAL MEMORY CURATED"
                                })
                                generateSuggestedPrompts()
                            } catch (e: Exception) {
                                android.util.Log.e("ChatViewModel", "Memory curation failed", e)
                                logMsg = logMsg.copy(content = "[SYSTEM_LOG]: GLOBAL MEMORY CURATION FAILED")
                            } finally {
                                val updatedMsgs = _messages.value.toMutableList()
                                val logIdx = updatedMsgs.indexOfFirst { it.id == logMsgId }
                                if (logIdx != -1) {
                                    updatedMsgs[logIdx] = logMsg
                                    _messages.value = updatedMsgs
                                }
                                daexMemory?.saveMessage(convId, logMsg)
                                _isReflecting.value = false
                            }
                        }
                    }

                } catch (e: Exception) {
                    val isCancellation = e is kotlinx.coroutines.CancellationException ||
                                         e is java.util.concurrent.CancellationException ||
                                         e.message?.contains("cancel", ignoreCase = true) == true

                    val updated = _messages.value.toMutableList()
                    val idx = updated.indexOfFirst { it.id == modelMsgId }
                    if (idx != -1) {
                        val messageToAppend = if (isCancellation) {
                            "\n\n[Generation stopped by user]"
                        } else {
                            "\n[Error: ${e.message ?: "Generation failed"}]"
                        }
                        val errorContent = updated[idx].content + messageToAppend
                        updated[idx] = updated[idx].copy(content = errorContent)
                        _messages.value = updated
                        daexMemory?.saveMessage(convId, updated[idx])
                    }
                } finally {
                    _isGenerating.value = false
                }
            }
        }
    }

    fun submitAudioPrompt(audioPath: String) {
        if (_isGenerating.value) {
            android.util.Log.i("ChatViewModel", "submitAudioPrompt: generation in progress — queueing chunk for next turn")
            pendingAudioPath = audioPath
            if (isLiveVoiceActive() && currentVoiceState() == VoiceState.PROCESSING) {
                setVoiceState?.invoke(VoiceState.LISTENING)
            }
            return
        }
        if (settingsViewModel.modelStatus.value != ModelStatus.READY || !daexService.isLoaded()) {
            _errorMessage.value = "Model is not loaded yet."
            // Reset voice states on failure
            abortLiveVoiceSession?.invoke()
            return
        }

        viewModelScope.launch {
            // See submitPrompt() for why this needs to be a joined cancellation, not fire-and-forget.
            curationJob?.cancelAndJoin()

            var convId = _currentConversationId.value
            if (convId == null) {
                val modelId = settingsViewModel.currentModel.value?.id ?: ModelBank.generativeModels.first().id
                convId = daexMemory?.createConversation(modelId, "Audio Session")
                _currentConversationId.value = convId
                if (convId != null && _attachedFiles.value.isNotEmpty()) {
                    daexMemory?.updateAttachedFiles(convId, _attachedFiles.value)
                }
                refreshConversations()
            }

            if (convId == null) {
                abortLiveVoiceSession?.invoke()
                return@launch
            }

            val userMsgId = System.currentTimeMillis().toString()
            val modelMsgId = (System.currentTimeMillis() + 1).toString()

            val userMsg = Message(id = userMsgId, role = "user", content = "[Live Audio]", audioPath = audioPath)
            val modelMsg = Message(id = modelMsgId, role = "model", content = "")

            _messages.value = _messages.value + listOf(userMsg, modelMsg)

            daexMemory?.saveMessageWithEmbedding(convId, userMsg)
            daexMemory?.saveMessage(convId, modelMsg)

            _isGenerating.value = true
            _tokenSpeed.value = 0.0
            settingsViewModel.triggerHapticFeedback(type = HapticType.START_RESPONSE)

            generationJob = viewModelScope.launch {
                var lastSpokenIndex = 0
                try {
                    val fullHistory = (daexMemory?.getRecentHistory(convId) ?: emptyList())
                        .filter { it.id != modelMsgId && it.role != "system" }

                    var activeHistory = fullHistory.filter { !it.isCompacted }
                    val maxContextLimit = settingsViewModel.currentModel.value?.maxContextTokens ?: 8192

                    val inferenceHistory = activeHistory.toMutableList()
                    val streamingUpdater = StreamingUpdater(modelMsgId)
                    val coreMemoryContent = daexCoreMemory?.getMemoryContent() ?: ""

                    var systemContext = coreMemoryContent
                    if (daexRag != null && daexRag.hasDocuments() && _attachedFiles.value.isNotEmpty()) {
                        try {
                            val relevantChunks = daexRag.queryDocuments(
                                query = "Audio Input",
                                activeFileNames = _attachedFiles.value
                            )
                            if (relevantChunks.isNotEmpty()) {
                                val contextBlock = relevantChunks.joinToString("\n---\n")
                                systemContext += "\n\n<uploaded_documents>\n$contextBlock\n</uploaded_documents>\n"
                                systemContext += "Use the above document excerpts to help answer the user's query. If the excerpts are not relevant, ignore them.\n"
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ChatViewModel", "RAG query failed", e)
                        }
                    }

                    if (daexSkillManager != null) {
                        systemContext += "\n\nYou have domain-specific \"skills\" (additional instructions/parameters) available. If you need a special skill or want to see what is available, call the listSkills() tool. If you find a matching skill, call the loadSkill(skillName) tool to retrieve its instructions.\n"
                    }

                    if (isLiveVoiceActive()) {
                        systemContext += "\n\nIMPORTANT: You are in a live voice session. Keep your response conversational, friendly, and direct. Keep your response to around 1 to 3 sentences (under 60 words). Do not use lists, bullet points, or structured formatting."
                    }

                    val result = daexService.generateResponse(
                        messages = inferenceHistory,
                        systemContext = systemContext,
                        isReasoningEnabled = if (isLiveVoiceActive()) false else settingsViewModel.isReasoningEnabled.value,
                        temperature = settingsViewModel.inferenceTemperature.value,
                        topK = settingsViewModel.inferenceTopK.value,
                        topP = settingsViewModel.inferenceTopP.value,
                        customSystemPrompt = settingsViewModel.customSystemPrompt.value,
                        isToolCallingEnabled = settingsViewModel.isToolCallingEnabled.value,
                        disabledToolIds = settingsViewModel.disabledToolIds.value,
                        onRequestPermission = { toolName, description ->
                            requestPermission(toolName, description)
                        },
                        onStatusUpdate = { status ->
                            val updated = _messages.value.toMutableList()
                            val idx = updated.indexOfFirst { it.id == modelMsgId }
                            if (idx != -1) {
                                updated[idx] = updated[idx].copy(toolStatus = status)
                                _messages.value = updated
                            }
                        },
                        maxTokens = settingsViewModel.maxTokens.value,
                        isLiveVoiceActive = isLiveVoiceActive(),
                        conversationId = convId
                    ) { token ->
                        if (!isActive) return@generateResponse
                        val didUpdate = streamingUpdater.onToken(token)
                        if (didUpdate && isLiveVoiceActive() && ttsViewModel.isTtsEnabled.value) {
                            val currentText = streamingUpdater.lastActual
                            if (currentText.length > lastSpokenIndex) {
                                val searchSubstring = currentText.substring(lastSpokenIndex)
                                // Split only on punctuation followed by whitespace so
                                // decimals ("3.14") and abbreviations ("Dr. Smith")
                                // don't become TTS fragments. The end-of-stream
                                // remainder is flushed after generation completes.
                                var splitIndex = -1
                                for (i in searchSubstring.indices) {
                                    val c = searchSubstring[i]
                                    if (c == '\n') {
                                        splitIndex = i
                                        break
                                    }
                                    if ((c == '.' || c == '?' || c == '!') &&
                                        searchSubstring.getOrNull(i + 1)?.isWhitespace() == true
                                    ) {
                                        splitIndex = i
                                        break
                                    }
                                }
                                if (splitIndex != -1) {
                                    val sentence = searchSubstring.substring(0, splitIndex + 1).trim()
                                    if (sentence.isNotEmpty()) {
                                        ttsViewModel.speakLive(sentence)
                                    }
                                    lastSpokenIndex += splitIndex + 1
                                }
                            }
                        }
                    }

                    streamingUpdater.finalFlush()

                    if (isLiveVoiceActive() && ttsViewModel.isTtsEnabled.value) {
                        val currentText = streamingUpdater.lastActual
                        if (currentText.length > lastSpokenIndex) {
                            val remaining = currentText.substring(lastSpokenIndex).trim()
                            if (remaining.isNotEmpty()) {
                                ttsViewModel.speakLive(remaining)
                            }
                        }
                    }

                    _tokenSpeed.value = result.tokensPerSecond
                    settingsViewModel.triggerHapticFeedback(type = HapticType.SUCCESS_COMPLETION)

                    val updatedList = _messages.value
                    val finalMsg = updatedList.find { it.id == modelMsgId }
                    if (finalMsg != null) {
                        val finalModelMsg = finalMsg.copy(tokensPerSecond = result.tokensPerSecond)
                        daexMemory?.saveMessageWithEmbedding(convId, finalModelMsg)
                    }
                } catch (e: Exception) {
                    val isCancellation = e is kotlinx.coroutines.CancellationException ||
                                         e is java.util.concurrent.CancellationException ||
                                         e.message?.contains("cancel", ignoreCase = true) == true

                    val updated = _messages.value.toMutableList()
                    val idx = updated.indexOfFirst { it.id == modelMsgId }
                    if (idx != -1) {
                        val messageToAppend = if (isCancellation) {
                            "\n\n[Generation stopped by user]"
                        } else {
                            "\n[Error: ${e.message ?: "Generation failed"}]"
                        }
                        val errorContent = updated[idx].content + messageToAppend
                        updated[idx] = updated[idx].copy(content = errorContent)
                        _messages.value = updated
                        daexMemory?.saveMessage(convId, updated[idx])
                    }
                } finally {
                    _isGenerating.value = false
                    if (!isLiveVoiceActive()) {
                        setVoiceState?.invoke(VoiceState.IDLE)
                    } else {
                        setVoiceState?.invoke(VoiceState.LISTENING)
                    }
                    refreshConversations()

                    // A chunk finished while this generation was running — submit it
                    // now as the next turn so the user's speech isn't dropped.
                    val queued = pendingAudioPath
                    pendingAudioPath = null
                    if (queued != null && isLiveVoiceActive()) {
                        val queuedFile = java.io.File(queued)
                        if (queuedFile.exists() && queuedFile.length() > 44) {
                            android.util.Log.i("ChatViewModel", "Submitting queued audio chunk from during-generation speech")
                            submitAudioPrompt(queued)
                        }
                    }
                }
            }
        }
    }

    /** Drops any chunk queued during an in-flight generation - used when a live-voice session
     * is torn down before that generation finishes. */
    fun clearPendingAudio() {
        pendingAudioPath = null
    }

    fun cancelGeneration() {
        val job = generationJob
        job?.cancel()
        (daexService as? DaexServiceImpl)?.cancelGeneration()
        ttsViewModel.stopPlayback()
        // Do NOT set _isGenerating.value = false here when a job exists: cancel()
        // only requests cancellation, it doesn't wait for the coroutine to unwind.
        // submitPrompt/submitAudioPrompt gate new generations on _isGenerating, so
        // clearing it early lets a new generation start into the same underlying
        // conversation before the cancelled job's own finally block (which also
        // resubmits any queued audio chunk) has actually finished. Only that job's
        // finally clears the flag now — it always runs exactly once, cancelled or not.
        if (job == null) {
            _isGenerating.value = false
        }
    }

    fun clearMessages() {
        _currentConversationId.value = null
        _messages.value = emptyList()
        _tokenSpeed.value = 0.0
        _attachedFiles.value = emptyList()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            daexMemory?.deleteAllConversations()
            clearMessages()
            refreshConversations()
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            daexMemory?.deleteConversation(id)
            if (_currentConversationId.value == id) {
                clearMessages()
            }
            refreshConversations()
        }
    }

    fun loadCoreMemory() {
        viewModelScope.launch {
            val content = daexCoreMemory?.getMemoryContent() ?: ""
            _coreMemoryText.value = content
        }
    }

    fun saveCoreMemory(content: String) {
        viewModelScope.launch {
            daexCoreMemory?.overwriteMemory(content)
            _coreMemoryText.value = content
        }
    }

    fun uploadFile(uri: android.net.Uri, fileName: String) {
        viewModelScope.launch {
            _isVectorizing.value = true
            try {
                val textContent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val ctx = context ?: return@withContext ""
                    val mimeType = ctx.contentResolver.getType(uri) ?: ""
                    if (mimeType == "application/pdf") {
                        val inputStream = ctx.contentResolver.openInputStream(uri)
                        inputStream?.use { stream ->
                            val reader = com.itextpdf.kernel.pdf.PdfReader(stream)
                            val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(reader)
                            try {
                                val sb = java.lang.StringBuilder()
                                for (i in 1..pdfDoc.numberOfPages) {
                                    val page = pdfDoc.getPage(i)
                                    val text = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(page)
                                    sb.appendLine(text)
                                }
                                sb.toString()
                            } finally {
                                pdfDoc.close()
                            }
                        } ?: ""
                    } else {
                        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                }

                if (textContent.isNotBlank()) {
                    daexRag?.ingestFile(fileName, textContent)
                    refreshUploadedFiles()

                    // Auto-attach to the current session
                    val currentAttached = _attachedFiles.value.toMutableList()
                    if (!currentAttached.contains(fileName)) {
                        currentAttached.add(fileName)
                        _attachedFiles.value = currentAttached
                        val convId = _currentConversationId.value
                        if (convId != null) {
                            daexMemory?.updateAttachedFiles(convId, currentAttached)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "File upload failed", e)
                _errorMessage.value = "Failed to process file: ${e.message}"
            } finally {
                _isVectorizing.value = false
            }
        }
    }

    fun deleteUploadedFile(fileName: String) {
        viewModelScope.launch {
            try {
                daexRag?.deleteFileByName(fileName)
                refreshUploadedFiles()

                // Remove from active session attachments
                val currentAttached = _attachedFiles.value.toMutableList()
                if (currentAttached.remove(fileName)) {
                    _attachedFiles.value = currentAttached
                    val convId = _currentConversationId.value
                    if (convId != null) {
                        daexMemory?.updateAttachedFiles(convId, currentAttached)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "File deletion failed", e)
                _errorMessage.value = "Delete may be incomplete for \"$fileName\" - please try again."
            }
        }
    }

    fun toggleAttachedFile(fileName: String) {
        viewModelScope.launch {
            val currentAttached = _attachedFiles.value.toMutableList()
            if (currentAttached.contains(fileName)) {
                currentAttached.remove(fileName)
            } else {
                currentAttached.add(fileName)
            }
            _attachedFiles.value = currentAttached
            val convId = _currentConversationId.value
            if (convId != null) {
                daexMemory?.updateAttachedFiles(convId, currentAttached)
            }
        }
    }

    fun refreshUploadedFiles() {
        viewModelScope.launch {
            _uploadedFiles.value = daexRag?.getUploadedFiles() ?: emptyList()
        }
    }

    fun refreshPinnedMessages() {
        viewModelScope.launch {
            _pinnedMessages.value = daexMemory?.getPinnedMessages() ?: emptyList()
        }
    }

    /** Pins or unpins a message into the saved-prompt library (see SavedPromptLibraryModal). */
    fun togglePin(message: Message) {
        val newPinned = !message.isPinned
        viewModelScope.launch {
            daexMemory?.setPinned(message.id, newPinned)
            val updated = _messages.value.toMutableList()
            val idx = updated.indexOfFirst { it.id == message.id }
            if (idx != -1) {
                updated[idx] = updated[idx].copy(isPinned = newPinned)
                _messages.value = updated
            }
            refreshPinnedMessages()
        }
    }

    /**
     * Ingests the bundled about-DAEX reference doc (assets/about_daex.md) through the real RAG
     * pipeline, tagged as a system document, exactly once. This grounds cold-start suggested
     * prompts (see [seedColdStartPromptsIfNeeded]) in real retrieval instead of a blind guess.
     */
    private suspend fun ingestAboutDocIfNeeded(rag: DaexRag) {
        val ctx = context ?: return
        val currentVersion = preferences?.aboutDocVersionFlow?.first() ?: 0
        if (currentVersion >= DaexPreferences.ABOUT_DAEX_CONTENT_VERSION) return
        try {
            // Unconditional and safe even if nothing exists yet - clears any chunks ingested
            // under an older content version (or, on devices that ingested before this
            // versioning existed, under the flag it replaced) so stale and fresh content don't
            // both show up in retrieval side by side.
            rag.deleteFileByName(DaexPreferences.ABOUT_DAEX_FILENAME)
            val content = ctx.assets.open(DaexPreferences.ABOUT_DAEX_FILENAME).bufferedReader().use { it.readText() }
            rag.ingestFile(DaexPreferences.ABOUT_DAEX_FILENAME, content, isSystem = true)
            preferences?.setAboutDocVersion(DaexPreferences.ABOUT_DAEX_CONTENT_VERSION)
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Failed to ingest bundled about-DAEX document", e)
        }
    }

    /**
     * On a true cold start (no conversations exist anywhere yet), seeds the suggested-prompts
     * pool from a fixed set of questions about DAEX's own capabilities rather than the generic
     * fallback strings or a blind, context-less generateSuggestedPrompts() call.
     */
    private fun seedColdStartPromptsIfNeeded() {
        if (_conversations.value.isNotEmpty()) return
        val picks = DaexPreferences.COLD_START_QUESTIONS.shuffled().take(3)
        viewModelScope.launch {
            preferences?.setSuggestedPrompts(picks)
        }
    }

    fun estimateTokens(content: String): Int {
        return content.length / 4
    }

    fun estimateMessageTokens(msg: Message): Int {
        return (msg.content.length + (msg.thoughtContent?.length ?: 0)) / 4
    }

    fun pruneToolOutputs(messages: List<Message>): List<Message> {
        return messages.map { msg ->
            if (msg.role == "model" && msg.content.length > 800) {
                val lines = msg.content.lines()
                if (lines.size > 20) {
                    val summaryText = "[Verbose tool output pruned: ${lines.size} lines, first line: ${lines.firstOrNull()}]"
                    msg.copy(content = summaryText)
                } else {
                    msg
                }
            } else {
                msg
            }
        }
    }

    suspend fun performCompaction(convId: String, messages: List<Message>, maxContextLimit: Int) {
        val uncompactedMessages = messages.filter { !it.isCompacted }
        if (uncompactedMessages.size < 15) return

        val headMessages = uncompactedMessages.take(2)
        val tailMessages = uncompactedMessages.takeLast(10)
        val middleMessages = uncompactedMessages.subList(2, uncompactedMessages.size - 10)
        if (middleMessages.isEmpty()) return

        val logMsgId = "log_" + System.currentTimeMillis()
        var logMsg = Message(
            id = logMsgId,
            role = "system",
            content = "[SYSTEM_LOG]: COMPACTING HISTORICAL CONTEXT..."
        )
        _messages.value = _messages.value + logMsg
        daexMemory?.saveMessage(convId, logMsg)

        android.util.Log.d("ChatCompaction", "Compacting ${middleMessages.size} middle messages...")

        val middleTokens = middleMessages.sumOf { estimateMessageTokens(it) }
        val targetTokens = (middleTokens * 0.20).toInt().coerceIn(100, 512)
        android.util.Log.d("ChatCompaction", "Middle region contains $middleTokens tokens. Summary budget: $targetTokens tokens.")

        val middleText = middleMessages.joinToString("\n") { "${it.role}: ${it.content}" }
        val compactorPrompt = """
            You are an assistant summarizing a conversational history.
            Provide a concise summary of the following conversation history. Describe the user's requirements, the actions taken, and the results obtained so far.
            You must format the summary strictly using the following key-value template, starting exactly with '[CONTEXT COMPACTION]:':

            [CONTEXT COMPACTION]:
            - ACTIVE GOAL: <short objective>
            - STATE: <key progress / current status>
            - NEXT: <next steps>

            CONVERSATION HISTORY:
            $middleText

            SUMMARY:
        """.trimIndent()

        var summary = ""
        var isSuccess = false
        try {
            summary = daexService.generateSilent(compactorPrompt, maxTokens = targetTokens).trim()
            isSuccess = summary.isNotBlank() && summary.startsWith("[CONTEXT COMPACTION]:")
        } catch (e: Exception) {
            android.util.Log.e("ChatCompaction", "On-device compaction failed, using fallback", e)
        }

        if (!isSuccess) {
            val topics = middleMessages.filter { it.role == "user" }.take(3).joinToString(", ") { it.content.take(30) + "..." }
            summary = "[CONTEXT COMPACTION]:\n- ACTIVE GOAL: General conversation\n- STATE: A conversation segment of ${middleMessages.size} turns took place. Topics covered: $topics\n- NEXT: Continue conversation"
        }

        val newTokens = estimateTokens(summary)
        val savedTokens = (middleTokens - newTokens).coerceAtLeast(0)
        val logContent = if (isSuccess) {
            "[SYSTEM_LOG]: CONTEXT COMPACTED (Saved $savedTokens tokens)"
        } else {
            "[SYSTEM_LOG]: CONTEXT COMPACTED WITH FALLBACK (Saved $savedTokens tokens)"
        }
        logMsg = logMsg.copy(content = logContent)
        val updatedMsgs = _messages.value.toMutableList()
        val logIdx = updatedMsgs.indexOfFirst { it.id == logMsgId }
        if (logIdx != -1) {
            updatedMsgs[logIdx] = logMsg
            _messages.value = updatedMsgs
        }
        daexMemory?.saveMessage(convId, logMsg)

        val summaryMsgId = "summary_" + System.currentTimeMillis()
        val summaryMsg = Message(
            id = summaryMsgId,
            role = "model",
            content = summary,
            isPinned = false,
            isCompacted = false
        )
        android.util.Log.d("ChatCompaction", "Generated summary: $summary")
        daexMemory?.saveMessage(convId, summaryMsg)

        for (msg in middleMessages) {
            val updatedMsg = msg.copy(isCompacted = true)
            daexMemory?.saveMessage(convId, updatedMsg)
        }
        android.util.Log.d("ChatCompaction", "Compaction complete. Persisted summary.")
    }

    fun generateSuggestedPrompts() {
        if (daexMemory == null || preferences == null) return
        viewModelScope.launch {
            if (!daexService.isLoaded() || _isGenerating.value) return@launch

            try {
                android.util.Log.d("ChatSuggestions", "Generating personalized suggestions...")

                val convId = _currentConversationId.value ?: _conversations.value.firstOrNull()?.id
                val recentHistory = if (convId != null) {
                    daexMemory.getRecentHistory(convId, limit = 15)
                        .filter { (it.role == "user" || it.role == "model") && !it.content.startsWith("[CONTEXT COMPACTION]:") }
                } else {
                    emptyList()
                }

                val historyBlock = if (recentHistory.isNotEmpty()) {
                    recentHistory.joinToString("\n") { "${it.role}: ${it.content}" }
                } else {
                    "(No recent conversation history)"
                }

                val systemPrompt = """
                    You are a helpful assistant. Based on the following recent conversation history, suggest 3 short, personalized starter prompts (under 10 words each) the user is likely to ask next to continue or start a related topic.
                    The suggestions must be actual chat messages, questions, or commands that a user would type, NOT task categories (e.g., say "Write a Python script for..." instead of "Python coding").

                    RECENT CONVERSATION HISTORY:
                    $historyBlock

                    Output exactly three suggestions in the following format and nothing else. Do not add any preamble, conversational text, or explanation.

                    PROMPT 1: <suggested prompt 1>
                    PROMPT 2: <suggested prompt 2>
                    PROMPT 3: <suggested prompt 3>
                """.trimIndent()

                val result = daexService.generateSilent(systemPrompt, maxTokens = 256).trim()
                android.util.Log.d("ChatSuggestions", "Generated suggestions output: $result")

                val parsed = result.lines()
                    .filter { it.contains("PROMPT ") && it.contains(":") }
                    .map { it.substringAfter(":").trim().removeSurrounding("\"").removeSurrounding("'") }
                    .filter { it.isNotBlank() }
                    .take(3)

                if (parsed.size == 3) {
                    preferences.setSuggestedPrompts(parsed)
                    android.util.Log.d("ChatSuggestions", "Successfully saved 3 dynamic suggestions: $parsed")
                } else {
                    android.util.Log.w("ChatSuggestions", "Failed to parse 3 suggestions. Parsed: $parsed")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatSuggestions", "Failed to generate suggestions", e)
            }
        }
    }
}

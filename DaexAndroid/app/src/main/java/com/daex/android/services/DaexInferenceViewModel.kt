package com.daex.android.services

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import android.os.VibrationEffect

// ~30fps: fast enough that throttled streaming text looks continuous, far less often than per-token.
private const val STREAM_UI_THROTTLE_MS = 33L

enum class ModelStatus {
    NOT_DOWNLOADED, DOWNLOADING, LOADING, READY, ERROR
}

enum class VoiceState {
    IDLE, LISTENING, PROCESSING, SPEAKING
}

enum class HapticType {
    CLICK,
    TICK,
    DOUBLE_CLICK,
    HEAVY_CLICK,
    START_RESPONSE,
    SUCCESS_COMPLETION
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DaexInferenceViewModel(
    private val daexService: DaexService,
    private val modelManager: ModelManager? = null,
    private val deviceService: DeviceService? = null,
    private val daexMemory: DaexMemory? = null,
    private val daexCoreMemory: DaexCoreMemory? = null,
    private val preferences: DaexPreferences? = null,
    private val daexRag: DaexRag? = null,
    private val daexSkillManager: DaexSkillManager? = null,
    private val context: android.content.Context? = null
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _modelStatus = MutableStateFlow(ModelStatus.NOT_DOWNLOADED)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    val downloadingModelId: StateFlow<String?> = _downloadingModelId.asStateFlow()

    private val _downloadedModelIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModelIds: StateFlow<Set<String>> = _downloadedModelIds.asStateFlow()

    private val _embeddingDownloadProgress = MutableStateFlow<Int?>(null)
    val embeddingDownloadProgress: StateFlow<Int?> = _embeddingDownloadProgress.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isReflecting = MutableStateFlow(false)
    val isReflecting: StateFlow<Boolean> = _isReflecting.asStateFlow()

    private val _isVectorizing = MutableStateFlow(false)
    val isVectorizing: StateFlow<Boolean> = _isVectorizing.asStateFlow()

    private val _uploadedFiles = MutableStateFlow<List<String>>(emptyList())
    val uploadedFiles: StateFlow<List<String>> = _uploadedFiles.asStateFlow()

    private val _pinnedMessages = MutableStateFlow<List<Message>>(emptyList())
    val pinnedMessages: StateFlow<List<Message>> = _pinnedMessages.asStateFlow()

    private val _attachedFiles = MutableStateFlow<List<String>>(emptyList())
    val attachedFiles: StateFlow<List<String>> = _attachedFiles.asStateFlow()

    private val _tokenSpeed = MutableStateFlow(0.0)
    val tokenSpeed: StateFlow<Double> = _tokenSpeed.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _useGPU = MutableStateFlow(false)
    val useGPU: StateFlow<Boolean> = _useGPU.asStateFlow()

    private val _selectedBackend = MutableStateFlow(BackendType.CPU)
    val selectedBackend: StateFlow<BackendType> = _selectedBackend.asStateFlow()

    private val _hardwareState = MutableStateFlow("CPU")
    val hardwareState: StateFlow<String> = _hardwareState.asStateFlow()

    private val _currentModel = MutableStateFlow<Model?>(null)
    val currentModel: StateFlow<Model?> = _currentModel.asStateFlow()

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

    private var searchJob: Job? = null

    // Theme Settings
    private val _primaryColor = MutableStateFlow(Color(0xFF00FFFF)) // Default Cyan
    val primaryColor: StateFlow<Color> = _primaryColor.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isReasoningEnabled = MutableStateFlow(true)
    val isReasoningEnabled: StateFlow<Boolean> = _isReasoningEnabled.asStateFlow()

    // Developer Settings StateFlows
    private val _isSpeculativeDecodingEnabled = MutableStateFlow(false)
    val isSpeculativeDecodingEnabled: StateFlow<Boolean> = _isSpeculativeDecodingEnabled.asStateFlow()

    private val _inferenceTemperature = MutableStateFlow(0.7f)
    val inferenceTemperature: StateFlow<Float> = _inferenceTemperature.asStateFlow()

    private val _inferenceTopK = MutableStateFlow(40)
    val inferenceTopK: StateFlow<Int> = _inferenceTopK.asStateFlow()

    private val _inferenceTopP = MutableStateFlow(0.9f)
    val inferenceTopP: StateFlow<Float> = _inferenceTopP.asStateFlow()

    private val _customSystemPrompt = MutableStateFlow("")
    val customSystemPrompt: StateFlow<String> = _customSystemPrompt.asStateFlow()

    private val _isToolCallingEnabled = MutableStateFlow(false)
    val isToolCallingEnabled: StateFlow<Boolean> = _isToolCallingEnabled.asStateFlow()

    private val _disabledToolIds = MutableStateFlow<Set<String>>(
        ToolRegistry.ALL.filter { !it.defaultEnabled }.map { it.id }.toSet()
    )
    val disabledToolIds: StateFlow<Set<String>> = _disabledToolIds.asStateFlow()

    private val _maxTokens = MutableStateFlow(1024)
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    private val _isHapticEnabled = MutableStateFlow(true)
    val isHapticEnabled: StateFlow<Boolean> = _isHapticEnabled.asStateFlow()

    private val _isAuraEnabled = MutableStateFlow(true)
    val isAuraEnabled: StateFlow<Boolean> = _isAuraEnabled.asStateFlow()

    private val _suggestedPrompts = MutableStateFlow<List<String>>(
        listOf(
            "Explain quantum entanglement simply",
            "Write a haiku about midnight code",
            "Plan a 3-day trip to Lisbon"
        )
    )
    val suggestedPrompts: StateFlow<List<String>> = _suggestedPrompts.asStateFlow()

    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    private val _ttsVoiceId = MutableStateFlow(1) // Default to af_bella (1)
    val ttsVoiceId: StateFlow<Int> = _ttsVoiceId.asStateFlow()

    private val _isTtsDownloaded = MutableStateFlow(false)
    val isTtsDownloaded: StateFlow<Boolean> = _isTtsDownloaded.asStateFlow()

    private val _isTtsDownloading = MutableStateFlow(false)
    val isTtsDownloading: StateFlow<Boolean> = _isTtsDownloading.asStateFlow()

    private val _ttsDownloadProgress = MutableStateFlow(0)
    val ttsDownloadProgress: StateFlow<Int> = _ttsDownloadProgress.asStateFlow()

    // Id of the message currently being read aloud via the per-message read-aloud button
    // (separate from live-voice TTS, which never sets this).
    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private var kokoroTtsService: KokoroTtsService? = null

    // Voice Mode State Flows
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _isLiveVoiceActive = MutableStateFlow(false)
    val isLiveVoiceActive: StateFlow<Boolean> = _isLiveVoiceActive.asStateFlow()

    private val _voiceAmplitude = MutableStateFlow(0f)
    val voiceAmplitude: StateFlow<Float> = _voiceAmplitude.asStateFlow()

    private var speechManager: SpeechManager? = null
    private var audioRecorder: AudioRecorder? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null

    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss of audio focus: stop the live session
                stopLiveVoiceSession()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Temporary loss or ducking request: stop/pause TTS playback
                kokoroTtsService?.stopPlayback()
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                // Regained focus
            }
        }
    }

    val deviceSpecs: DeviceSpecs? = deviceService?.getDeviceSpecs()

    private var generationJob: Job? = null
    private var curationJob: Job? = null

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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val ctx = context
                if (ctx != null) {
                    ctx.cacheDir.listFiles()?.forEach { file ->
                        if (file.name.startsWith("live_audio_") && file.name.endsWith(".wav")) {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("DaexInference", "Failed to clean up old audio cache files", e)
            }
        }

        viewModelScope.launch {
            preferences?.primaryColorFlow?.collectLatest { colorInt ->
                _primaryColor.value = Color(colorInt)
            }
        }

        viewModelScope.launch {
            preferences?.isDarkModeFlow?.collectLatest { isDark ->
                _isDarkMode.value = isDark
            }
        }

        viewModelScope.launch {
            preferences?.isReasoningEnabledFlow?.collectLatest { enabled ->
                _isReasoningEnabled.value = enabled
            }
        }

        viewModelScope.launch {
            preferences?.isSpeculativeDecodingFlow?.collectLatest { enabled ->
                _isSpeculativeDecodingEnabled.value = enabled
            }
        }

        viewModelScope.launch {
            preferences?.inferenceTemperatureFlow?.collectLatest { temp ->
                _inferenceTemperature.value = temp
            }
        }

        viewModelScope.launch {
            preferences?.inferenceTopKFlow?.collectLatest { topK ->
                _inferenceTopK.value = topK
            }
        }

        viewModelScope.launch {
            preferences?.inferenceTopPFlow?.collectLatest { topP ->
                _inferenceTopP.value = topP
            }
        }

        viewModelScope.launch {
            preferences?.customSystemPromptFlow?.collectLatest { prompt ->
                _customSystemPrompt.value = prompt
            }
        }

        viewModelScope.launch {
            preferences?.isToolCallingEnabledFlow?.collectLatest { enabled ->
                _isToolCallingEnabled.value = enabled
            }
        }

        viewModelScope.launch {
            preferences?.disabledToolIdsFlow?.collectLatest { ids ->
                _disabledToolIds.value = ids
            }
        }

        viewModelScope.launch {
            preferences?.maxTokensFlow?.collectLatest { maxTokens ->
                _maxTokens.value = maxTokens
            }
        }

        viewModelScope.launch {
            preferences?.isHapticEnabledFlow?.collectLatest { enabled ->
                _isHapticEnabled.value = enabled
            }
        }

        viewModelScope.launch {
            preferences?.isAuraEnabledFlow?.collectLatest { enabled ->
                _isAuraEnabled.value = enabled
            }
        }

        viewModelScope.launch {
            preferences?.suggestedPromptsFlow?.collectLatest { list ->
                _suggestedPrompts.value = list
            }
        }

        viewModelScope.launch {
            preferences?.isTtsEnabledFlow?.collectLatest { enabled ->
                _isTtsEnabled.value = enabled
            }
        }

        viewModelScope.launch {
            preferences?.ttsVoiceIdFlow?.collectLatest { voiceId ->
                _ttsVoiceId.value = voiceId
            }
        }

        val ctx = context
        if (ctx != null) {
            kokoroTtsService = KokoroTtsService(ctx)
            kokoroTtsService?.onSpeakingStateChanged = { speaking ->
                if (!speaking) {
                    _speakingMessageId.value = null
                }
                if (_isLiveVoiceActive.value) {
                    if (speaking) {
                        speakingRevertJob?.cancel()
                        setVoiceStateInternal(VoiceState.SPEAKING)
                    } else {
                        // Debounced revert — cancel any existing pending revert first.
                        // isSpeaking now flips false only after the AudioTrack has
                        // physically drained, so the cooldown just needs to cover
                        // device output latency and the room's acoustic tail.
                        speakingRevertJob?.cancel()
                        ttsCooldownUntilMs = System.currentTimeMillis() + 500L
                        speakingRevertJob = viewModelScope.launch {
                            kotlinx.coroutines.delay(400L)
                            // Gate on the actual isSpeaking flag — not voiceState —
                            // so a between-sentence false trigger doesn't revert early.
                            if (kokoroTtsService?.isSpeaking != true && _isLiveVoiceActive.value) {
                                setVoiceStateInternal(VoiceState.LISTENING)
                            }
                        }
                    }
                }
            }

        }

        refreshConversations()

        // Autoload last used model if already downloaded
        viewModelScope.launch {
            val modelId = preferences?.lastUsedModelIdFlow?.firstOrNull()
            val backendStr = preferences?.lastUsedBackendFlow?.firstOrNull() ?: "CPU"
            android.util.Log.d("DaexAutoload", "Autoload check: modelId=$modelId, backend=$backendStr")
            if (modelId != null) {
                val model = ModelBank.generativeModels.find { it.id == modelId }
                val isDownloaded = model?.let { modelManager?.isModelDownloaded(it) } ?: false
                android.util.Log.d("DaexAutoload", "Model found: ${model?.name}, isDownloaded=$isDownloaded")
                if (model != null && isDownloaded) {
                    val savedBackend = try {
                        BackendType.valueOf(backendStr)
                    } catch (e: Exception) {
                        BackendType.CPU
                    }
                    _selectedBackend.value = savedBackend
                    _useGPU.value = (savedBackend == BackendType.GPU)
                    android.util.Log.d("DaexAutoload", "Triggering autoload for ${model.name} on $savedBackend")
                    loadModel(model)
                }
            }
        }

        // Reconnect to a generative-model download ModelDownloadService is already running -
        // e.g. started by a previous instance of this ViewModel before the Activity was
        // recreated, or from a Gallery/Landing download the user began in an earlier session.
        // Without this, a fresh ViewModel has no way to know a download is in flight and just
        // shows nothing until it finishes on its own.
        run {
            val existing = ModelDownloadState.generative.value
            if (existing != null && existing.phase == DownloadPhase.DOWNLOADING) {
                _downloadingModelId.value = existing.modelId
                _modelStatus.value = ModelStatus.DOWNLOADING
                _downloadProgress.value = existing.percent
                viewModelScope.launch {
                    val status = awaitGenerativeDownload(existing.requestId) { percent ->
                        _downloadProgress.value = percent
                    }
                    _downloadingModelId.value = null
                    if (status.phase == DownloadPhase.COMPLETED) {
                        _modelStatus.value = ModelStatus.NOT_DOWNLOADED
                        refreshDownloadedModels()
                    } else {
                        _modelStatus.value = ModelStatus.ERROR
                        _errorMessage.value = status.error ?: "Download failed"
                    }
                }
            }
        }

        // Reconnect to an in-progress Kokoro download the same way - see comment above.
        run {
            val existing = ModelDownloadState.kokoro.value
            if (existing != null && existing.phase == DownloadPhase.DOWNLOADING) {
                _isTtsDownloading.value = true
                _ttsDownloadProgress.value = existing.percent
                viewModelScope.launch {
                    val status = awaitKokoroDownload(existing.requestId) { percent -> _ttsDownloadProgress.value = percent }
                    _isTtsDownloading.value = false
                    if (status.phase == DownloadPhase.COMPLETED) {
                        _isTtsDownloaded.value = true
                        _ttsDownloadProgress.value = 100
                        refreshDownloadedModels()
                    } else {
                        android.util.Log.e("DaexInferenceViewModel", "Reconnected TTS download failed: ${status.error}")
                    }
                }
            }
        }

        // Reconnect to an in-progress embedding-model download the same way - see comment above.
        run {
            val existing = ModelDownloadState.embedding.value
            if (existing != null && existing.phase == DownloadPhase.DOWNLOADING) {
                _embeddingDownloadProgress.value = existing.percent
                viewModelScope.launch {
                    val status = awaitEmbeddingDownload(existing.requestId) { percent ->
                        _embeddingDownloadProgress.value = percent
                    }
                    _embeddingDownloadProgress.value = null
                    if (status.phase == DownloadPhase.COMPLETED && daexRag != null) {
                        refreshDownloadedModels()
                        try {
                            daexRag.initRag()
                            ingestAboutDocIfNeeded(daexRag)
                            seedColdStartPromptsIfNeeded()
                        } catch (e: Exception) {
                            // Handle potential load failures
                        }
                    }
                }
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
                        refreshDownloadedModels()
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
                        android.util.Log.e("DaexInference", "Message embedding backfill failed", e)
                    }
                }
            }
        }

        // Silent Background Initialization of the Kokoro TTS Model
        viewModelScope.launch {
            if (modelManager != null && context != null) {
                val isDownloaded = modelManager.isKokoroDownloaded()
                if (!isDownloaded && ModelDownloadState.kokoro.value?.phase != DownloadPhase.DOWNLOADING) {
                    _isTtsDownloading.value = true
                    _ttsDownloadProgress.value = 0
                    val requestId = ModelDownloadService.startKokoroDownload(context)
                    val status = awaitKokoroDownload(requestId) { percent -> _ttsDownloadProgress.value = percent }
                    _isTtsDownloading.value = false
                    if (status.phase == DownloadPhase.COMPLETED) {
                        _isTtsDownloaded.value = true
                        _ttsDownloadProgress.value = 100
                        refreshDownloadedModels()
                    } else {
                        android.util.Log.e("DaexInferenceViewModel", "Background TTS download failed: ${status.error}")
                    }
                }
            }
        }
        refreshDownloadedModels()
    }

    /**
     * Suspends until [ModelDownloadService] reports a terminal status for [requestId], mirroring
     * progress via [onProgress] in the meantime. Filtering on the request id (not just modelId)
     * matters: a terminal status from a *previous* download of the same model can already be
     * sitting in the StateFlow when this starts collecting, before the service has processed the
     * new request. The download itself runs in the service's own scope, so cancelling the caller
     * (e.g. this ViewModel being cleared) only stops listening - it does not cancel the download.
     */
    private suspend fun awaitGenerativeDownload(requestId: String, onProgress: (Int) -> Unit): GenerativeDownloadStatus = coroutineScope {
        val mirrorJob = launch {
            ModelDownloadState.generative.collect { status ->
                if (status?.requestId == requestId && status.phase == DownloadPhase.DOWNLOADING) {
                    onProgress(status.percent)
                }
            }
        }
        val terminal = ModelDownloadState.generative
            .first { it?.requestId == requestId && it.phase != DownloadPhase.DOWNLOADING }!!
        mirrorJob.cancel()
        terminal
    }

    private suspend fun awaitKokoroDownload(requestId: String, onProgress: (Int) -> Unit): KokoroDownloadStatus = coroutineScope {
        val mirrorJob = launch {
            ModelDownloadState.kokoro.collect { status ->
                if (status?.requestId == requestId && status.phase == DownloadPhase.DOWNLOADING) {
                    onProgress(status.percent)
                }
            }
        }
        val terminal = ModelDownloadState.kokoro.first { it?.requestId == requestId && it.phase != DownloadPhase.DOWNLOADING }!!
        mirrorJob.cancel()
        terminal
    }

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

    fun refreshDownloadedModels() {
        viewModelScope.launch {
            if (modelManager == null) return@launch
            val downloaded = ModelBank.generativeModels
                .filter { modelManager.isModelDownloaded(it) }
                .map { it.id }
                .toSet()
            _downloadedModelIds.value = downloaded
            _isTtsDownloaded.value = modelManager.isKokoroDownloaded()
        }
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

    fun setThemeColor(color: Color) {
        _primaryColor.value = color
        viewModelScope.launch {
            preferences?.setPrimaryColor(color.toArgb())
        }
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        viewModelScope.launch {
            preferences?.setDarkMode(enabled)
        }
    }

    fun toggleReasoning() {
        val newValue = !_isReasoningEnabled.value
        _isReasoningEnabled.value = newValue
        viewModelScope.launch {
            preferences?.setReasoningEnabled(newValue)
        }
    }

    private fun setVoiceStateInternal(state: VoiceState) {
        if (_isLiveVoiceActive.value && state == VoiceState.LISTENING && kokoroTtsService?.isSpeaking == true) {
            _voiceState.value = VoiceState.SPEAKING
            android.util.Log.w("DaexInference", "Blocked transition to LISTENING because TTS is speaking.")
        } else {
            _voiceState.value = state
        }
    }

    fun setVoiceState(state: VoiceState) {
        setVoiceStateInternal(state)
        if (state == VoiceState.IDLE) {
            _isLiveVoiceActive.value = false
        }
    }

    fun setVoiceAmplitude(amplitude: Float) {
        _voiceAmplitude.value = amplitude
    }

    fun startLiveVoiceSession(onTextResult: (String) -> Unit) {
        if (_isTtsEnabled.value && !_isTtsDownloaded.value) {
            android.util.Log.w("DaexInference", "Cannot start live voice session: TTS is enabled but not downloaded.")
            context?.let { ctx ->
                android.widget.Toast.makeText(ctx, "TTS voice engine is not downloaded yet. Please download it in Settings.", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }

        _isLiveVoiceActive.value = true
        setVoiceStateInternal(VoiceState.LISTENING)

        kokoroTtsService?.playWakeChime()

        // Request Transient Audio Focus to duck background music and identify stream intent
        context?.let { ctx ->
            try {
                val audioManager = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                // GAIN_TRANSIENT (not MAY_DUCK): ducked background music would keep
                // playing into the mic and false-trigger the VAD, so ask other apps to pause.
                val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioFocusRequest = focusRequest
                val result = audioManager.requestAudioFocus(focusRequest)
                android.util.Log.i("DaexInference", "Requested Audio Focus: result=$result")
            } catch (e: Exception) {
                android.util.Log.e("DaexInference", "Failed to request Audio Focus", e)
            }
        }

        // Note: We do NOT set AudioManager.mode to MODE_IN_COMMUNICATION or force speakerphone because
        // on some devices (especially Samsung), VoIP call routing triggers aggressive system-level half-duplex
        // echo suppression that completely silences/mutes the microphone input during active speaker playback.
        // Keeping it at MODE_NORMAL keeps the mic open; echo is handled by the TTS gate in AudioRecorder
        // (isSpeaking + cooldown), the RMS-scaled ducking floor, and hardware AEC where available.

        if (_isTtsEnabled.value) {
            kokoroTtsService?.initTts()
        }

        // Delay starting the recording segment by 300ms to allow the awake hum sound effect to finish playing.
        // This prevents the microphone from capturing the hum and false-triggering a speech-start interruption.
        viewModelScope.launch {
            kotlinx.coroutines.delay(300L)
            if (_isLiveVoiceActive.value) {
                startNewRecordingSegment()
            }
        }
    }

    private val liveAudioFiles = mutableListOf<java.io.File>()
    // Post-TTS cooldown consumed by the recorder's TTS gate: for 500ms after the
    // speaker actually goes silent, the VAD stays in COOLDOWN (stricter detection)
    // so the acoustic tail can't false-trigger a speech start.
    @Volatile private var ttsCooldownUntilMs = 0L
    // Debounce job for the SPEAKING → LISTENING state revert
    private var speakingRevertJob: kotlinx.coroutines.Job? = null


    private suspend fun startNewRecordingSegment() {
        val ctx = context ?: return
        val audioFile = java.io.File(ctx.cacheDir, "live_audio_${System.currentTimeMillis()}.wav")
        liveAudioFiles.add(audioFile)

        // Stop any active recorder
        audioRecorder?.stop()

        val recorder = AudioRecorder(audioFile)
        audioRecorder = recorder

        recorder.start(
            scope = viewModelScope,
            // Calibrated 2026-07-11: ambient floor measures ~0.001 on-device and
            // conversational-distance speech was landing under the old 0.03 —
            // users had to hold the phone to their face to be heard.
            speechThreshold = 0.015f,
            silenceThreshold = 0.008f,
            silenceDurationMs = 1500L,
            ttsGateState = {
                val now = System.currentTimeMillis()
                when {
                    kokoroTtsService?.isSpeaking == true -> TtsGateState.SPEAKING
                    // The wake/close chimes play via SoundPool, invisible to
                    // isSpeaking — gate them like TTS.
                    now < (kokoroTtsService?.chimeActiveUntilMs ?: 0L) -> TtsGateState.SPEAKING
                    now < ttsCooldownUntilMs -> TtsGateState.COOLDOWN
                    else -> TtsGateState.CLEAR
                }
            },
            onSpeechStarted = {
                handleUserSpeechStarted()
            },
            onSilenceDetected = {
                handleUserSilenceDetected(audioFile)
            }
        ) { amplitude ->
            setVoiceAmplitude(amplitude)
        }
    }

    private fun handleUserSpeechStarted() {
        // Deliberately does NOT cancel generation: a speech-start is only ~160ms of
        // signal and false-triggers on coughs/taps. Cancelling here made the model
        // cut itself off mid-response (2026-07-10 field testing). Chunks that finish
        // while generation is running are queued in submitAudioPrompt instead.
        android.util.Log.d("DaexInference", "VAD: Speech started")
    }

    private fun handleUserSilenceDetected(audioFile: java.io.File) {
        viewModelScope.launch {
            // Finalize current chunk by calling stop() on its recorder
            audioRecorder?.stop()

            android.util.Log.i("DaexInference", "VAD: Silence detected. Finalizing chunk and submitting.")
            setVoiceStateInternal(VoiceState.PROCESSING)

            // Immediately start next segment to keep recording loop uninterrupted
            if (_isLiveVoiceActive.value) {
                startNewRecordingSegment()
            }

            if (audioFile.exists() && audioFile.length() > 44) {
                submitAudioPrompt(audioFile.absolutePath)
            } else {
                if (_isLiveVoiceActive.value && _voiceState.value == VoiceState.PROCESSING) {
                    setVoiceStateInternal(VoiceState.LISTENING)
                }
            }
        }
    }

    fun stopLiveVoiceSession() {
        _isLiveVoiceActive.value = false
        setVoiceStateInternal(VoiceState.IDLE)
        audioRecorder?.stopAsync()
        audioRecorder = null
        cancelGeneration()
        pendingAudioPath = null
        liveAudioFiles.clear()
        kokoroTtsService?.stopPlayback()
        kokoroTtsService?.releaseTts()

        kokoroTtsService?.playCloseChime()

        // Abandon Audio Focus
        context?.let { ctx ->
            try {
                val audioManager = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                audioFocusRequest?.let { focusRequest ->
                    val result = audioManager.abandonAudioFocusRequest(focusRequest)
                    android.util.Log.i("DaexInference", "Abandoned Audio Focus: result=$result")
                }
            } catch (e: Exception) {
                android.util.Log.e("DaexInference", "Failed to abandon Audio Focus", e)
            } finally {
                audioFocusRequest = null
            }
        }
    }

    fun toggleVoiceInput(onTextResult: (String) -> Unit) {
        val ctx = context ?: return
        if (speechManager == null) {
            speechManager = SpeechManager(
                context = ctx,
                onAmplitudeChanged = { setVoiceAmplitude(it) },
                onResult = { result ->
                    onTextResult(result)
                },
                onStateChanged = { state ->
                    setVoiceState(state)
                }
            )
        }

        if (_voiceState.value == VoiceState.LISTENING) {
            speechManager?.stopListening()
        } else {
            speechManager?.startListening()
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        _isTtsEnabled.value = enabled
        viewModelScope.launch {
            preferences?.setTtsEnabled(enabled)
        }
    }

    fun setTtsVoiceId(voiceId: Int) {
        _ttsVoiceId.value = voiceId
        viewModelScope.launch {
            preferences?.setTtsVoiceId(voiceId)
        }
    }

    /** Reads a single past message aloud, independent of live-voice sessions. Tapping the same
     * message again stops playback; tapping a different one interrupts and switches to it. */
    fun speakMessage(message: Message) {
        if (_isLiveVoiceActive.value) return

        if (!_isTtsDownloaded.value) {
            downloadTtsModel()
            return
        }

        if (_speakingMessageId.value == message.id) {
            kokoroTtsService?.stopPlayback()
            _speakingMessageId.value = null
            return
        }

        kokoroTtsService?.stopPlayback()
        _speakingMessageId.value = message.id
        kokoroTtsService?.speak(message.content, _ttsVoiceId.value)
    }

    fun setSpeculativeDecodingEnabled(enabled: Boolean) {
        if (_isGenerating.value || _isReflecting.value || _isVectorizing.value) {
            _errorMessage.value = "Cannot change settings while the engine is busy."
            return
        }
        _isSpeculativeDecodingEnabled.value = enabled
        viewModelScope.launch {
            preferences?.setSpeculativeDecodingEnabled(enabled)
        }

        val targetModel = _currentModel.value
        if (targetModel != null && daexService.isLoaded()) {
            _modelStatus.value = ModelStatus.LOADING
            viewModelScope.launch {
                try {
                    daexService.releaseContext()
                    val modelPath = modelManager?.getModelPath(targetModel) ?: ""
                    val actualBackend = daexService.initContext(modelPath, _selectedBackend.value, enabled)
                    _selectedBackend.value = actualBackend
                    _hardwareState.value = actualBackend.name
                    _modelStatus.value = ModelStatus.READY
                } catch (e: Exception) {
                    _modelStatus.value = ModelStatus.ERROR
                    _errorMessage.value = e.message ?: "Failed to reload model with speculative decoding"
                }
            }
        }
    }

    fun setInferenceTemperature(temp: Float) {
        _inferenceTemperature.value = temp
        viewModelScope.launch {
            preferences?.setInferenceTemperature(temp)
        }
    }

    fun setInferenceTopK(topK: Int) {
        _inferenceTopK.value = topK
        viewModelScope.launch {
            preferences?.setInferenceTopK(topK)
        }
    }

    fun setInferenceTopP(topP: Float) {
        _inferenceTopP.value = topP
        viewModelScope.launch {
            preferences?.setInferenceTopP(topP)
        }
    }

    fun setCustomSystemPrompt(prompt: String) {
        _customSystemPrompt.value = prompt
        viewModelScope.launch {
            preferences?.setCustomSystemPrompt(prompt)
        }
    }

    fun setToolCallingEnabled(enabled: Boolean) {
        _isToolCallingEnabled.value = enabled
        viewModelScope.launch {
            preferences?.setToolCallingEnabled(enabled)
        }
    }

    fun setToolEnabled(toolId: String, enabled: Boolean) {
        val updated = if (enabled) _disabledToolIds.value - toolId else _disabledToolIds.value + toolId
        _disabledToolIds.value = updated
        viewModelScope.launch {
            preferences?.setDisabledToolIds(updated)
        }
    }

    fun setMaxTokens(maxTokens: Int) {
        _maxTokens.value = maxTokens
        viewModelScope.launch {
            preferences?.setMaxTokens(maxTokens)
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        _isHapticEnabled.value = enabled
        viewModelScope.launch {
            preferences?.setHapticEnabled(enabled)
        }
    }

    fun setAuraEnabled(enabled: Boolean) {
        _isAuraEnabled.value = enabled
        viewModelScope.launch {
            preferences?.setAuraEnabled(enabled)
        }
    }

    fun triggerHapticFeedback(context: android.content.Context? = null, force: Boolean = false, type: HapticType = HapticType.CLICK) {
        if (_isHapticEnabled.value || force) {
            val targetContext = context ?: this.context ?: return
            try {
                val vibrator = targetContext.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val effect = when (type) {
                            HapticType.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                            HapticType.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                            HapticType.DOUBLE_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                            HapticType.HEAVY_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                            HapticType.START_RESPONSE -> {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    try {
                                        VibrationEffect.startComposition()
                                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f, 50)
                                            .compose()
                                    } catch (e: Exception) {
                                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                                    }
                                } else {
                                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                                }
                            }
                            HapticType.SUCCESS_COMPLETION -> {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    try {
                                        VibrationEffect.startComposition()
                                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.5f, 100)
                                            .compose()
                                    } catch (e: Exception) {
                                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                                    }
                                } else {
                                    VibrationEffect.createWaveform(longArrayOf(0, 30, 80, 40), intArrayOf(0, 200, 0, 100), -1)
                                }
                            }
                        }
                        vibrator.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        when (type) {
                            HapticType.CLICK -> vibrator.vibrate(40)
                            HapticType.TICK -> vibrator.vibrate(10)
                            HapticType.DOUBLE_CLICK -> vibrator.vibrate(longArrayOf(0, 30, 60, 30), -1)
                            HapticType.HEAVY_CLICK -> vibrator.vibrate(80)
                            HapticType.START_RESPONSE -> vibrator.vibrate(longArrayOf(0, 30, 50, 30), -1)
                            HapticType.SUCCESS_COMPLETION -> vibrator.vibrate(longArrayOf(0, 35, 80, 50), -1)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DaexInferenceViewModel", "Failed to trigger haptic feedback", e)
            }
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
                if (model != null && _currentModel.value?.id != model.id) {
                    loadModel(model)
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
    
    

    fun checkModelStatus(model: Model) {
        viewModelScope.launch {
            if (modelManager == null) return@launch
            try {
                val isDownloaded = modelManager.isModelDownloaded(model)
                if (isDownloaded) {
                    if (daexService.isLoaded()) {
                        _modelStatus.value = ModelStatus.READY
                    } else {
                        _modelStatus.value = ModelStatus.NOT_DOWNLOADED
                    }
                } else {
                    _modelStatus.value = ModelStatus.NOT_DOWNLOADED
                }
            } catch (e: Exception) {
                _modelStatus.value = ModelStatus.NOT_DOWNLOADED
            }
        }
    }

    fun downloadModel(model: Model) {
        if (_downloadingModelId.value != null || modelManager == null || context == null) return

        _downloadingModelId.value = model.id
        _modelStatus.value = ModelStatus.DOWNLOADING
        _downloadProgress.value = 0
        _errorMessage.value = null

        val requestId = ModelDownloadService.startModelDownload(context, model.id)

        viewModelScope.launch {
            val status = awaitGenerativeDownload(requestId) { percent -> _downloadProgress.value = percent }
            _downloadingModelId.value = null
            if (status.phase == DownloadPhase.COMPLETED) {
                _modelStatus.value = ModelStatus.NOT_DOWNLOADED
                _downloadProgress.value = 100
                refreshDownloadedModels()
            } else {
                _modelStatus.value = ModelStatus.ERROR
                _errorMessage.value = status.error ?: "Download failed"
            }
        }
    }

    fun cancelDownload() {
        context?.let { ModelDownloadService.cancelModelDownload(it) }
        _downloadingModelId.value = null
        _modelStatus.value = ModelStatus.NOT_DOWNLOADED
        _downloadProgress.value = 0
    }

    fun downloadTtsModel() {
        if (_isTtsDownloading.value || modelManager == null || context == null) return

        _isTtsDownloading.value = true
        _ttsDownloadProgress.value = 0
        _errorMessage.value = null

        val requestId = ModelDownloadService.startKokoroDownload(context)

        viewModelScope.launch {
            val status = awaitKokoroDownload(requestId) { percent -> _ttsDownloadProgress.value = percent }
            _isTtsDownloading.value = false
            if (status.phase == DownloadPhase.COMPLETED) {
                _isTtsDownloaded.value = true
                _ttsDownloadProgress.value = 100
                refreshDownloadedModels()
            } else {
                _errorMessage.value = status.error ?: "TTS Download failed"
                android.util.Log.e("DaexInferenceViewModel", "TTS model download failed: ${status.error}")
            }
        }
    }

    fun deleteTtsModel() {
        viewModelScope.launch {
            if (modelManager == null) return@launch
            try {
                // releaseTts() only releases the native OfflineTts object, not the whole
                // service's coroutine scope/pipelines - release() would permanently kill TTS
                // for the rest of this singleton's lifetime, since nothing ever recreates it.
                kokoroTtsService?.releaseTts()
                modelManager.deleteKokoro()
                _isTtsDownloaded.value = false
                refreshDownloadedModels()
            } catch (e: Exception) {
                android.util.Log.e("DaexInferenceViewModel", "Failed to delete TTS model", e)
            }
        }
    }

    fun loadModel(model: Model) {
        if (_isGenerating.value || _isReflecting.value || _isVectorizing.value) {
            _errorMessage.value = "Cannot change models while the engine is busy."
            return
        }
        _currentModel.value = model
        viewModelScope.launch {
            if (modelManager == null) return@launch
            
            val isDownloaded = modelManager.isModelDownloaded(model)
            if (!isDownloaded) {
                if (context == null) {
                    _modelStatus.value = ModelStatus.ERROR
                    _errorMessage.value = "Download failed"
                    return@launch
                }
                _downloadingModelId.value = model.id
                _modelStatus.value = ModelStatus.DOWNLOADING
                _downloadProgress.value = 0
                _errorMessage.value = null

                val requestId = ModelDownloadService.startModelDownload(context, model.id)
                val status = awaitGenerativeDownload(requestId) { percent -> _downloadProgress.value = percent }
                _downloadingModelId.value = null

                if (status.phase != DownloadPhase.COMPLETED) {
                    _modelStatus.value = ModelStatus.ERROR
                    _errorMessage.value = status.error ?: "Download failed"
                    return@launch
                }
                refreshDownloadedModels()
            }

            _modelStatus.value = ModelStatus.LOADING
            _errorMessage.value = null

            try {
                val modelPath = modelManager.getModelPath(model)
                val targetBackend = if (model.supportedBackends.contains(_selectedBackend.value)) {
                    _selectedBackend.value
                } else {
                    model.supportedBackends.firstOrNull() ?: BackendType.CPU
                }
                _selectedBackend.value = targetBackend
                val actualBackend = daexService.initContext(modelPath, targetBackend, _isSpeculativeDecodingEnabled.value)
                _selectedBackend.value = actualBackend
                _hardwareState.value = actualBackend.name
                
                // Warm up the engine silently with 1 token to pre-allocate activation memory
                try {
                    android.util.Log.d("DaexAutoload", "Warming up model...")
                    daexService.generateSilent("warmup", maxTokens = 1)
                    android.util.Log.d("DaexAutoload", "Warmup complete.")
                } catch (warmupEx: Exception) {
                    android.util.Log.w("DaexAutoload", "Warmup failed silently, continuing", warmupEx)
                }

                _modelStatus.value = ModelStatus.READY
                android.util.Log.d("DaexAutoload", "Model loaded successfully. Saving configuration to preferences: id=${model.id}, backend=${actualBackend.name}")
                preferences?.setLastUsedModel(model.id, actualBackend.name)

                // If suggestions are still the default, try generating personalized ones once model is ready
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
            } catch (e: Exception) {
                _modelStatus.value = ModelStatus.ERROR
                _errorMessage.value = e.message ?: "Failed to load model"
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            daexService.releaseContext()
            _modelStatus.value = ModelStatus.NOT_DOWNLOADED
            _tokenSpeed.value = 0.0
        }
    }

    fun deleteModel(model: Model) {
        if (_isGenerating.value || _isReflecting.value || _isVectorizing.value) {
            _errorMessage.value = "Cannot delete models while the engine is busy."
            return
        }
        viewModelScope.launch {
            if (modelManager == null) return@launch
            try {
                if (_currentModel.value?.id == model.id) {
                    daexService.releaseContext()
                    _currentModel.value = null
                    _tokenSpeed.value = 0.0
                    _modelStatus.value = ModelStatus.NOT_DOWNLOADED
                }
                modelManager.deleteModel(model)
                refreshDownloadedModels()
            } catch (e: Exception) {
                android.util.Log.e("DaexInferenceViewModel", "Failed to delete model: ${model.name}", e)
            }
        }
    }

    fun toggleGPU(model: Model? = null) {
        if (_isGenerating.value || _isReflecting.value || _isVectorizing.value) {
            _errorMessage.value = "Cannot change backend while the engine is busy."
            return
        }
        val nextBackend = if (_selectedBackend.value == BackendType.CPU) BackendType.GPU else BackendType.CPU
        setBackend(nextBackend, model)
    }

    fun setBackend(backend: BackendType, model: Model? = null) {
        if (_isGenerating.value || _isReflecting.value || _isVectorizing.value) {
            _errorMessage.value = "Cannot change backend while the engine is busy."
            return
        }
        val targetModel = model ?: _currentModel.value
        if (targetModel != null && !targetModel.supportedBackends.contains(backend)) {
            _errorMessage.value = "${targetModel.name} does not support ${backend.name} execution."
            return
        }

        _selectedBackend.value = backend
        _useGPU.value = (backend == BackendType.GPU)
        if (targetModel == null) {
            _hardwareState.value = backend.name
            return
        }

        if (daexService.isLoaded()) {
            _modelStatus.value = ModelStatus.LOADING
            viewModelScope.launch {
                try {
                    daexService.releaseContext()
                    val modelPath = modelManager?.getModelPath(targetModel) ?: ""
                    val actualBackend = daexService.initContext(modelPath, backend, _isSpeculativeDecodingEnabled.value)
                    _selectedBackend.value = actualBackend
                    _hardwareState.value = actualBackend.name
                    _modelStatus.value = ModelStatus.READY
                    preferences?.setLastUsedModel(targetModel.id, actualBackend.name)
                } catch (e: Exception) {
                    _modelStatus.value = ModelStatus.ERROR
                    _errorMessage.value = e.message ?: "Failed to reload model"
                }
            }
        } else {
            _hardwareState.value = backend.name
            targetModel?.let {
                viewModelScope.launch {
                    preferences?.setLastUsedModel(it.id, backend.name)
                }
            }
        }
    }

    fun submitPrompt(prompt: String) {
        if (prompt.isBlank() || _isGenerating.value) return
        if (_modelStatus.value != ModelStatus.READY || !daexService.isLoaded()) {
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
                val modelId = _currentModel.value?.id ?: ModelBank.generativeModels.first().id
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
            triggerHapticFeedback(type = HapticType.START_RESPONSE)

            generationJob = viewModelScope.launch {
                try {
                    // Filter out the placeholder model message and system logs from history sent to model
                    val fullHistory = (daexMemory?.getRecentHistory(convId) ?: emptyList())
                        .filter { it.id != modelMsgId && it.role != "system" }
                    
                    // TOKEN-BASED COMPACTION & PRESSURE TRACKING
                    var activeHistory = fullHistory.filter { !it.isCompacted }
                    val maxContextLimit = _currentModel.value?.maxContextTokens ?: 8192
                    
                    var currentTokens = activeHistory.sumOf { estimateMessageTokens(it) }
                    
                    if (currentTokens > maxContextLimit / 2) {
                        android.util.Log.i("DaexInference", "Context pressure warning: $currentTokens tokens. Triggering compaction check...")
                        // 1. Cheap local pruning pass
                        val pruned = pruneToolOutputs(activeHistory)
                        val prunedTokens = pruned.sumOf { estimateMessageTokens(it) }
                        
                        if (prunedTokens <= maxContextLimit / 2) {
                            android.util.Log.i("DaexInference", "Local pruning reduced tokens to $prunedTokens. Saving pruned logs...")
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
                                android.util.Log.e("DaexInference", "Context compaction failed", e)
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
                            android.util.Log.e("DaexInference", "RAG query failed, continuing without context", e)
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
                            android.util.Log.e("DaexInference", "About-DAEX RAG query failed, continuing without it", e)
                        }
                    }

                    // --- MODULAR SKILLS INFO INJECTION ---
                    if (daexSkillManager != null) {
                        systemContext += "\n\nYou have domain-specific \"skills\" (additional instructions/parameters) available. If you need a special skill or want to see what is available, call the listSkills() tool. If you find a matching skill, call the loadSkill(skillName) tool to retrieve its instructions.\n"
                    }
                    val result = daexService.generateResponse(
                        messages = inferenceHistory,
                        systemContext = systemContext,
                        isReasoningEnabled = _isReasoningEnabled.value,
                        temperature = _inferenceTemperature.value,
                        topK = _inferenceTopK.value,
                        topP = _inferenceTopP.value,
                        customSystemPrompt = _customSystemPrompt.value,
                        isToolCallingEnabled = _isToolCallingEnabled.value,
                        disabledToolIds = _disabledToolIds.value,
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
                        maxTokens = _maxTokens.value
                    ) { token ->
                        if (!isActive) return@generateResponse
                        streamingUpdater.onToken(token)
                    }
                    streamingUpdater.finalFlush()
                    _tokenSpeed.value = result.tokensPerSecond
                    triggerHapticFeedback(type = HapticType.SUCCESS_COMPLETION)

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
                                android.util.Log.e("DaexInference", "Memory curation failed", e)
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

    // A chunk that finished while the model was still generating; submitted as the
    // next turn when the current generation completes, instead of being dropped.
    @Volatile private var pendingAudioPath: String? = null

    fun submitAudioPrompt(audioPath: String) {
        if (_isGenerating.value) {
            android.util.Log.i("DaexInference", "submitAudioPrompt: generation in progress — queueing chunk for next turn")
            pendingAudioPath = audioPath
            if (_isLiveVoiceActive.value && _voiceState.value == VoiceState.PROCESSING) {
                setVoiceStateInternal(VoiceState.LISTENING)
            }
            return
        }
        if (_modelStatus.value != ModelStatus.READY || !daexService.isLoaded()) {
            _errorMessage.value = "Model is not loaded yet."
            // Reset voice states on failure
            _isLiveVoiceActive.value = false
            setVoiceStateInternal(VoiceState.IDLE)
            return
        }

        viewModelScope.launch {
            // See submitPrompt() for why this needs to be a joined cancellation, not fire-and-forget.
            curationJob?.cancelAndJoin()

            var convId = _currentConversationId.value
            if (convId == null) {
                val modelId = _currentModel.value?.id ?: ModelBank.generativeModels.first().id
                convId = daexMemory?.createConversation(modelId, "Audio Session")
                _currentConversationId.value = convId
                if (convId != null && _attachedFiles.value.isNotEmpty()) {
                    daexMemory?.updateAttachedFiles(convId, _attachedFiles.value)
                }
                refreshConversations()
            }

            if (convId == null) {
                _isLiveVoiceActive.value = false
                setVoiceStateInternal(VoiceState.IDLE)
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
            triggerHapticFeedback(type = HapticType.START_RESPONSE)

            generationJob = viewModelScope.launch {
                var lastSpokenIndex = 0
                try {
                    val fullHistory = (daexMemory?.getRecentHistory(convId) ?: emptyList())
                        .filter { it.id != modelMsgId && it.role != "system" }
                    
                    var activeHistory = fullHistory.filter { !it.isCompacted }
                    val maxContextLimit = _currentModel.value?.maxContextTokens ?: 8192
                    
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
                            android.util.Log.e("DaexInference", "RAG query failed", e)
                        }
                    }

                    if (daexSkillManager != null) {
                        systemContext += "\n\nYou have domain-specific \"skills\" (additional instructions/parameters) available. If you need a special skill or want to see what is available, call the listSkills() tool. If you find a matching skill, call the loadSkill(skillName) tool to retrieve its instructions.\n"
                    }

                    if (_isLiveVoiceActive.value) {
                        systemContext += "\n\nIMPORTANT: You are in a live voice session. Keep your response conversational, friendly, and direct. Keep your response to around 1 to 3 sentences (under 60 words). Do not use lists, bullet points, or structured formatting."
                    }

                    val result = daexService.generateResponse(
                        messages = inferenceHistory,
                        systemContext = systemContext,
                        isReasoningEnabled = if (_isLiveVoiceActive.value) false else _isReasoningEnabled.value,
                        temperature = _inferenceTemperature.value,
                        topK = _inferenceTopK.value,
                        topP = _inferenceTopP.value,
                        customSystemPrompt = _customSystemPrompt.value,
                        isToolCallingEnabled = _isToolCallingEnabled.value,
                        disabledToolIds = _disabledToolIds.value,
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
                        maxTokens = _maxTokens.value,
                        isLiveVoiceActive = _isLiveVoiceActive.value,
                        conversationId = convId
                    ) { token ->
                        if (!isActive) return@generateResponse
                        val didUpdate = streamingUpdater.onToken(token)
                        if (didUpdate && _isLiveVoiceActive.value && _isTtsEnabled.value) {
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
                                        kokoroTtsService?.speak(sentence, _ttsVoiceId.value)
                                    }
                                    lastSpokenIndex += splitIndex + 1
                                }
                            }
                        }
                    }

                    streamingUpdater.finalFlush()

                    if (_isLiveVoiceActive.value && _isTtsEnabled.value) {
                        val currentText = streamingUpdater.lastActual
                        if (currentText.length > lastSpokenIndex) {
                            val remaining = currentText.substring(lastSpokenIndex).trim()
                            if (remaining.isNotEmpty()) {
                                kokoroTtsService?.speak(remaining, _ttsVoiceId.value)
                            }
                        }
                    }

                    _tokenSpeed.value = result.tokensPerSecond
                    triggerHapticFeedback(type = HapticType.SUCCESS_COMPLETION)
                    
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
                    if (!_isLiveVoiceActive.value) {
                        setVoiceStateInternal(VoiceState.IDLE)
                    } else {
                        setVoiceStateInternal(VoiceState.LISTENING)
                    }
                    refreshConversations()

                    // A chunk finished while this generation was running — submit it
                    // now as the next turn so the user's speech isn't dropped.
                    val queued = pendingAudioPath
                    pendingAudioPath = null
                    if (queued != null && _isLiveVoiceActive.value) {
                        val queuedFile = java.io.File(queued)
                        if (queuedFile.exists() && queuedFile.length() > 44) {
                            android.util.Log.i("DaexInference", "Submitting queued audio chunk from during-generation speech")
                            submitAudioPrompt(queued)
                        }
                    }
                }
            }
        }
    }

    fun cancelGeneration() {
        val job = generationJob
        job?.cancel()
        (daexService as? DaexServiceImpl)?.cancelGeneration()
        kokoroTtsService?.stopPlayback()
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

    fun disconnect() {
        unloadModel()
        clearMessages()
        _errorMessage.value = null
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
                android.util.Log.e("DaexInference", "File upload failed", e)
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
                android.util.Log.e("DaexInference", "File deletion failed", e)
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
            android.util.Log.e("DaexInference", "Failed to ingest bundled about-DAEX document", e)
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

        android.util.Log.d("DaexCompaction", "Compacting ${middleMessages.size} middle messages...")

        val middleTokens = middleMessages.sumOf { estimateMessageTokens(it) }
        val targetTokens = (middleTokens * 0.20).toInt().coerceIn(100, 512)
        android.util.Log.d("DaexCompaction", "Middle region contains $middleTokens tokens. Summary budget: $targetTokens tokens.")

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
            android.util.Log.e("DaexCompaction", "On-device compaction failed, using fallback", e)
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
        android.util.Log.d("DaexCompaction", "Generated summary: $summary")
        daexMemory?.saveMessage(convId, summaryMsg)

        for (msg in middleMessages) {
            val updatedMsg = msg.copy(isCompacted = true)
            daexMemory?.saveMessage(convId, updatedMsg)
        }
        android.util.Log.d("DaexCompaction", "Compaction complete. Persisted summary.")
    }

    fun generateSuggestedPrompts() {
        if (daexMemory == null || preferences == null) return
        viewModelScope.launch {
            if (!daexService.isLoaded() || _isGenerating.value) return@launch
            
            try {
                android.util.Log.d("DaexSuggestions", "Generating personalized suggestions...")
                
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
                android.util.Log.d("DaexSuggestions", "Generated suggestions output: $result")
                
                val parsed = result.lines()
                    .filter { it.contains("PROMPT ") && it.contains(":") }
                    .map { it.substringAfter(":").trim().removeSurrounding("\"").removeSurrounding("'") }
                    .filter { it.isNotBlank() }
                    .take(3)
                
                if (parsed.size == 3) {
                    preferences.setSuggestedPrompts(parsed)
                    android.util.Log.d("DaexSuggestions", "Successfully saved 3 dynamic suggestions: $parsed")
                } else {
                    android.util.Log.w("DaexSuggestions", "Failed to parse 3 suggestions. Parsed: $parsed")
                }
            } catch (e: Exception) {
                android.util.Log.e("DaexSuggestions", "Failed to generate suggestions", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager?.destroy()
        kokoroTtsService?.release()
    }
}

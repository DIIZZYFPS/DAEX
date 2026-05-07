package com.daex.android.services

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take

enum class ModelStatus {
    NOT_DOWNLOADED, DOWNLOADING, LOADING, READY, ERROR
}

class DaexInferenceViewModel(
    private val llamaService: LlamaService,
    private val modelManager: ModelManager? = null,
    private val deviceService: DeviceService? = null,
    private val daexMemory: DaexMemory? = null,
    private val daexCoreMemory: DaexCoreMemory? = null,
    private val preferences: DaexPreferences? = null,
    private val daexRag: DaexRag? = null
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _modelStatus = MutableStateFlow(ModelStatus.NOT_DOWNLOADED)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _embeddingDownloadProgress = MutableStateFlow<Int?>(null)
    val embeddingDownloadProgress: StateFlow<Int?> = _embeddingDownloadProgress.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isReflecting = MutableStateFlow(false)
    val isReflecting: StateFlow<Boolean> = _isReflecting.asStateFlow()

    private val _isVectorizing = MutableStateFlow(false)
    val isVectorizing: StateFlow<Boolean> = _isVectorizing.asStateFlow()

    private val _attachedDocumentIds = MutableStateFlow<List<String>>(emptyList())
    val attachedDocumentIds: StateFlow<List<String>> = _attachedDocumentIds.asStateFlow()

    private val _vaultDocuments = MutableStateFlow<List<VaultDocument>>(emptyList())
    val vaultDocuments: StateFlow<List<VaultDocument>> = _vaultDocuments.asStateFlow()

    private val _tokenSpeed = MutableStateFlow(0.0)
    val tokenSpeed: StateFlow<Double> = _tokenSpeed.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _useGPU = MutableStateFlow(false)
    val useGPU: StateFlow<Boolean> = _useGPU.asStateFlow()

    private val _hardwareState = MutableStateFlow("CPU")
    val hardwareState: StateFlow<String> = _hardwareState.asStateFlow()

    private val _currentModel = MutableStateFlow<Model?>(null)
    val currentModel: StateFlow<Model?> = _currentModel.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _coreMemoryText = MutableStateFlow("")
    val coreMemoryText: StateFlow<String> = _coreMemoryText.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    // Theme Settings
    private val _primaryColor = MutableStateFlow(Color(0xFF00FFFF)) // Default Cyan
    val primaryColor: StateFlow<Color> = _primaryColor.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isReasoningEnabled = MutableStateFlow(true)
    val isReasoningEnabled: StateFlow<Boolean> = _isReasoningEnabled.asStateFlow()

    private var generationJob: Job? = null
    private var exchangesSinceCompaction = 0
    private val COMPACTION_INTERVAL = 5

    init {
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
            if (preferences != null) {
                combine(
                    preferences.lastConversationIdFlow,
                    _conversations
                ) { lastId, convs -> lastId to convs }
                    .filter { it.first != null && it.second.isNotEmpty() }
                    .take(1)
                    .collect { (lastId, convs) ->
                        if (_currentConversationId.value == null) {
                            _currentConversationId.value = lastId
                            val conv = convs.find { it.id == lastId }
                            if (conv != null) {
                                val model = ModelBank.generativeModels.find { it.id == conv.modelId }
                                if (model != null && _currentModel.value == null) {
                                    loadModel(model)
                                }
                            }
                        }
                    }
            }
        }

        viewModelScope.launch {
            daexMemory?.getAllConversations()?.collect {
                _conversations.value = it
            }
        }

        viewModelScope.launch {
            _currentConversationId.flatMapLatest { id ->
                if (id != null) {
                    daexMemory?.getMessagesForConversation(id) ?: flowOf(emptyList())
                } else {
                    flowOf(emptyList())
                }
            }.collectLatest {
                _messages.value = it
            }
        }

        // Load attachments when conversation changes
        viewModelScope.launch {
            _currentConversationId.collectLatest { id ->
                if (id != null) {
                    val attachments = daexMemory?.getConversationAttachments(id) ?: emptyList()
                    _attachedDocumentIds.value = attachments
                } else {
                    _attachedDocumentIds.value = emptyList()
                }
            }
        }

        // Silent Background Initialization of the Embedding Model
        viewModelScope.launch {
            if (modelManager != null && daexRag != null) {
                val embedModel = ModelBank.embeddingModel
                val isDownloaded = modelManager.isModelDownloaded(embedModel)
                if (!isDownloaded) {
                    try {
                        modelManager.downloadModel(embedModel) { progress ->
                            _embeddingDownloadProgress.value = progress.percent
                        }
                        daexRag.initRag() // Initialize after download finishes
                        _embeddingDownloadProgress.value = null
                    } catch (e: Exception) {
                        _embeddingDownloadProgress.value = null
                        // Silently fail or log for background downloads
                    }
                } else {
                    try {
                        daexRag.initRag() // Initialize immediately if already downloaded
                    } catch (e: Exception) {
                        // Handle potential load failures
                    }
                }
            }
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

    fun selectConversation(id: String) {
        _currentConversationId.value = id
        viewModelScope.launch {
            preferences?.setLastConversationId(id)
        }
        // Optionally load the model associated with the conversation
        viewModelScope.launch {
            val conv = _conversations.value.find { it.id == id }
            if (conv != null) {
                val model = ModelBank.generativeModels.find { it.id == conv.modelId }
                if (model != null && _currentModel.value?.id != model.id) {
                    loadModel(model)
                }
            }
        }
    }
    
    // ... rest of class unchanged

    fun checkModelStatus(model: Model) {
        viewModelScope.launch {
            if (modelManager == null) return@launch
            try {
                val isDownloaded = modelManager.isModelDownloaded(model)
                if (isDownloaded) {
                    if (llamaService.isLoaded()) {
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
        if (_modelStatus.value == ModelStatus.DOWNLOADING || modelManager == null) return

        _modelStatus.value = ModelStatus.DOWNLOADING
        _downloadProgress.value = 0
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                modelManager.downloadModel(model) { progress ->
                    _downloadProgress.value = progress.percent
                }
                _modelStatus.value = ModelStatus.NOT_DOWNLOADED
                _downloadProgress.value = 100
            } catch (e: Exception) {
                _modelStatus.value = ModelStatus.ERROR
                _errorMessage.value = e.message ?: "Download failed"
            }
        }
    }

    fun cancelDownload() {
        modelManager?.cancelDownload()
        _modelStatus.value = ModelStatus.NOT_DOWNLOADED
        _downloadProgress.value = 0
    }

    fun loadModel(model: Model) {
        _currentModel.value = model
        viewModelScope.launch {
            if (modelManager == null) return@launch
            
            val isDownloaded = modelManager.isModelDownloaded(model)
            if (!isDownloaded) {
                _modelStatus.value = ModelStatus.DOWNLOADING
                _downloadProgress.value = 0
                _errorMessage.value = null

                try {
                    modelManager.downloadModel(model) { progress ->
                        _downloadProgress.value = progress.percent
                    }
                } catch (e: Exception) {
                    _modelStatus.value = ModelStatus.ERROR
                    _errorMessage.value = e.message ?: "Download failed"
                    return@launch
                }
            }

            _modelStatus.value = ModelStatus.LOADING
            _errorMessage.value = null

            try {
                val modelPath = modelManager.getModelPath(model)
                llamaService.initContext(modelPath, _useGPU.value)
                _modelStatus.value = ModelStatus.READY
                
                val specs = deviceService?.getDeviceSpecs()
                if (specs != null) {
                    _hardwareState.value = if (_useGPU.value && specs.hasVulkan) "Vulkan Ready (${specs.totalRAM / 1024 / 1024 / 1024}GB RAM)"
                    else "CPU Only (${specs.totalRAM / 1024 / 1024 / 1024}GB RAM)"
                } else {
                    _hardwareState.value = if (_useGPU.value) "GPU" else "CPU"
                }
            } catch (e: Exception) {
                _modelStatus.value = ModelStatus.ERROR
                _errorMessage.value = e.message ?: "Failed to load model"
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            llamaService.releaseContext()
            _modelStatus.value = ModelStatus.NOT_DOWNLOADED
            _tokenSpeed.value = 0.0
        }
    }

    fun toggleGPU(model: Model? = null) {
        val targetModel = model ?: _currentModel.value ?: return
        _useGPU.value = !_useGPU.value

        if (llamaService.isLoaded()) {
            _modelStatus.value = ModelStatus.LOADING
            viewModelScope.launch {
                try {
                    llamaService.releaseContext()
                    val modelPath = modelManager?.getModelPath(targetModel) ?: ""
                    llamaService.initContext(modelPath, _useGPU.value)
                    _modelStatus.value = ModelStatus.READY
                    _hardwareState.value = if (_useGPU.value) "GPU" else "CPU"
                } catch (e: Exception) {
                    _modelStatus.value = ModelStatus.ERROR
                    _errorMessage.value = e.message ?: "Failed to reload model"
                }
            }
        }
    }

    fun submitPrompt(prompt: String) {
        if (prompt.isBlank() || _isGenerating.value) return
        if (_modelStatus.value != ModelStatus.READY || !llamaService.isLoaded()) {
            _errorMessage.value = "Model is not loaded yet."
            return
        }

        viewModelScope.launch {
            try {
                var convId = _currentConversationId.value
                if (convId == null) {
                    val modelId = _currentModel.value?.id ?: ModelBank.generativeModels.first().id
                    convId = daexMemory?.createConversation(modelId, prompt.take(20) + "...")
                    _currentConversationId.value = convId
                    preferences?.setLastConversationId(convId)
                }

                if (convId == null) {
                    _errorMessage.value = "Failed to create conversation."
                    return@launch
                }

                val userMsgId = System.currentTimeMillis().toString()
                val modelMsgId = (System.currentTimeMillis() + 1).toString()

                // Create messages: userMsg is CLEAN for UI and DB.
                val userMsg = Message(id = userMsgId, role = "user", content = prompt)
                val modelMsg = Message(id = modelMsgId, role = "model", content = "")

                _messages.value = _messages.value + listOf(userMsg, modelMsg)

                daexMemory?.saveMessage(convId, userMsg)
                daexMemory?.saveMessage(convId, modelMsg)
                
                _isGenerating.value = true
                _tokenSpeed.value = 0.0

                generationJob = viewModelScope.launch {
                    try {
                        // Filter out the placeholder model message from history sent to model
                        val fullHistory = (daexMemory?.getRecentHistory(convId) ?: emptyList())
                            .filter { it.id != modelMsgId }
                        
                        // SLIDING WINDOW LOGIC
                        val MAX_CHARS = 8192
                        var currentCharCount = 0
                        val windowedHistory = mutableListOf<Message>()
                        
                        for (msg in fullHistory.reversed()) {
                            val msgLength = msg.content.length + (msg.thoughtContent?.length ?: 0)
                            if (currentCharCount + msgLength > MAX_CHARS && windowedHistory.isNotEmpty()) {
                                break 
                            }
                            currentCharCount += msgLength
                            windowedHistory.add(0, msg)
                        }

                        val inferenceHistory = windowedHistory.toMutableList()
                        
                        var rawText = ""
                        val coreMemoryContent = daexCoreMemory?.getMemoryContent() ?: ""

                        // --- FILE RAG CONTEXT INJECTION (targeted to attached docs) ---
                        var systemContext = coreMemoryContent
                        val attachedIds = _attachedDocumentIds.value
                        if (daexRag != null && attachedIds.isNotEmpty()) {
                            try {
                                val relevantChunks = daexRag.queryDocuments(prompt, attachedIds)
                                if (relevantChunks.isNotEmpty()) {
                                    val contextBlock = relevantChunks.joinToString("\n---\n")
                                    systemContext += "\n\n<uploaded_documents>\n$contextBlock\n</uploaded_documents>\n"
                                    systemContext += "Use the above document excerpts to help answer the user's query. If the excerpts are not relevant, ignore them.\n"
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("DaexInference", "RAG query failed, continuing without context", e)
                            }
                        }
                        val result = llamaService.generateResponse(inferenceHistory, systemContext, _isReasoningEnabled.value) { token ->
                            if (!isActive) return@generateResponse
                            rawText += token
                            
                            var thought: String? = null
                            var actual = rawText
                            
                            // Parse think/channel tags only — no reflection parsing needed
                            val thinkTags = listOf(
                                Pair("<|channel>", "<channel|>"),
                                Pair("<|think|>", "</think|>"),
                                Pair("<think>", "</think>")
                            )
                            
                            for (tagPair in thinkTags) {
                                val startIdx = rawText.indexOf(tagPair.first)
                                if (startIdx != -1) {
                                    val endIdx = rawText.indexOf(tagPair.second, startIdx + tagPair.first.length)
                                    if (endIdx != -1) {
                                        thought = rawText.substring(startIdx + tagPair.first.length, endIdx).trim()
                                        actual = rawText.substring(0, startIdx) + rawText.substring(endIdx + tagPair.second.length)
                                    } else {
                                        thought = rawText.substring(startIdx + tagPair.first.length).trim()
                                        actual = rawText.substring(0, startIdx)
                                    }
                                    break
                                }
                            }
                            
                            val updated = _messages.value.toMutableList()
                            val idx = updated.indexOfFirst { it.id == modelMsgId }
                            if (idx != -1) {
                                updated[idx] = updated[idx].copy(content = actual.trimStart(), thoughtContent = thought)
                                _messages.value = updated
                            }
                        }
                        _tokenSpeed.value = result.tokensPerSecond
                        
                        // Save final result to DB
                        val updatedList = _messages.value
                        val finalMsg = updatedList.find { it.id == modelMsgId }
                        if (finalMsg != null) {
                            val finalModelMsg = finalMsg.copy(tokensPerSecond = result.tokensPerSecond)
                            daexMemory?.saveMessage(convId, finalModelMsg)
                        }

                        // --- MEMORY COMPACTION TRIGGER ---
                        exchangesSinceCompaction++
                        if (exchangesSinceCompaction >= COMPACTION_INTERVAL && daexCoreMemory != null) {
                            _isReflecting.value = true
                            try {
                                val recentMsgs = daexMemory?.getRecentHistory(convId, limit = 20) ?: emptyList()
                                daexCoreMemory.compactMemory(recentMsgs, llamaService)
                                exchangesSinceCompaction = 0
                            } catch (e: Exception) {
                                android.util.Log.e("DaexInference", "Memory compaction failed", e)
                            } finally {
                                _isReflecting.value = false
                            }
                        }

                    } catch (e: Exception) {
                        android.util.Log.e("DaexInference", "Generation job failed", e)
                        val updated = _messages.value.toMutableList()
                        val idx = updated.indexOfFirst { it.id == modelMsgId }
                        if (idx != -1) {
                            val errorContent = updated[idx].content + "\n[Error: ${e.message ?: "Generation failed"}]"
                            updated[idx] = updated[idx].copy(content = errorContent)
                            _messages.value = updated
                            daexMemory?.saveMessage(convId, updated[idx])
                        }
                    } finally {
                        _isGenerating.value = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DaexInference", "Submit prompt outer failure", e)
                _errorMessage.value = "Failed to start generation: ${e.message}"
                _isGenerating.value = false
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        (llamaService as? LlamaServiceImpl)?.cancelGeneration()
        _isGenerating.value = false
    }

    fun clearMessages() {
        _currentConversationId.value = null
        _messages.value = emptyList()
        _tokenSpeed.value = 0.0
        viewModelScope.launch {
            preferences?.setLastConversationId(null)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            daexMemory?.deleteConversation(id)
            if (_currentConversationId.value == id) {
                clearMessages()
            }
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

    fun uploadFile(fileName: String, content: String) {
        viewModelScope.launch {
            _isVectorizing.value = true
            try {
                val documentId = daexRag?.ingestFile(fileName, content)
                if (documentId != null) {
                    // Auto-attach to current conversation
                    val convId = _currentConversationId.value
                    if (convId != null) {
                        val current = _attachedDocumentIds.value.toMutableList()
                        current.add(documentId)
                        _attachedDocumentIds.value = current
                        daexMemory?.updateConversationAttachments(convId, current)
                    }
                }
                refreshVault()
            } catch (e: Exception) {
                android.util.Log.e("DaexInference", "File upload failed", e)
                _errorMessage.value = "Failed to process file: ${e.message}"
            } finally {
                _isVectorizing.value = false
            }
        }
    }

    fun refreshVault() {
        viewModelScope.launch {
            _vaultDocuments.value = daexRag?.getVaultDocuments() ?: emptyList()
        }
    }

    fun loadConversationAttachments(conversationId: String) {
        viewModelScope.launch {
            _attachedDocumentIds.value = daexMemory?.getConversationAttachments(conversationId) ?: emptyList()
        }
    }

    fun toggleDocumentAttachment(documentId: String) {
        viewModelScope.launch {
            val convId = _currentConversationId.value ?: return@launch
            val current = _attachedDocumentIds.value.toMutableList()
            if (documentId in current) {
                current.remove(documentId)
            } else {
                current.add(documentId)
            }
            _attachedDocumentIds.value = current
            daexMemory?.updateConversationAttachments(convId, current)
        }
    }

    fun detachDocument(documentId: String) {
        viewModelScope.launch {
            val convId = _currentConversationId.value ?: return@launch
            val current = _attachedDocumentIds.value.toMutableList()
            current.remove(documentId)
            _attachedDocumentIds.value = current
            daexMemory?.updateConversationAttachments(convId, current)
        }
    }

    fun deleteVaultDocument(documentId: String) {
        viewModelScope.launch {
            daexRag?.deleteDocument(documentId)
            // Also detach from current conversation if attached
            detachDocument(documentId)
            refreshVault()
        }
    }

    fun renameVaultDocument(documentId: String, newName: String) {
        viewModelScope.launch {
            daexRag?.renameDocument(documentId, newName)
            refreshVault()
        }
    }
}

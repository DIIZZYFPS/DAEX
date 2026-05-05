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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

enum class ModelStatus {
    NOT_DOWNLOADED, DOWNLOADING, LOADING, READY, ERROR
}

class DaexInferenceViewModel(
    private val llamaService: LlamaService,
    private val modelManager: ModelManager? = null,
    private val deviceService: DeviceService? = null,
    private val daexMemory: DaexMemory? = null,
    private val preferences: DaexPreferences? = null
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _modelStatus = MutableStateFlow(ModelStatus.NOT_DOWNLOADED)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

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

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    // Theme Settings
    private val _primaryColor = MutableStateFlow(Color(0xFF00FFFF)) // Default Cyan
    val primaryColor: StateFlow<Color> = _primaryColor.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private var generationJob: Job? = null

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

    fun selectConversation(id: String) {
        _currentConversationId.value = id
        // Optionally load the model associated with the conversation
        viewModelScope.launch {
            val conv = _conversations.value.find { it.id == id }
            if (conv != null) {
                val model = ModelBank.models.find { it.id == conv.modelId }
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
            var convId = _currentConversationId.value
            if (convId == null) {
                val modelId = _currentModel.value?.id ?: ModelBank.models.first().id
                convId = daexMemory?.createConversation(modelId, prompt.take(20) + "...")
                _currentConversationId.value = convId
            }

            if (convId == null) return@launch

            val userMsgId = System.currentTimeMillis().toString()
            val modelMsgId = (System.currentTimeMillis() + 1).toString()

            val userMsg = Message(id = userMsgId, role = "user", content = prompt)
            val modelMsg = Message(id = modelMsgId, role = "model", content = "")

            daexMemory?.saveMessage(convId, userMsg)
            daexMemory?.saveMessage(convId, modelMsg)
            
            _isGenerating.value = true
            _tokenSpeed.value = 0.0

            generationJob = viewModelScope.launch {
                try {
                    val history = daexMemory?.getRecentHistory(convId) ?: emptyList()
                    
                    var rawText = ""
                    val result = llamaService.generateResponse(history) { token ->
                        if (!isActive) return@generateResponse
                        rawText += token
                        
                        var thought: String? = null
                        var actual = rawText
                        
                        val tags = listOf(
                            Pair("<|channel>", "<channel|>"),
                            Pair("<|think|>", "</think|>"),
                            Pair("<think>", "</think>")
                        )
                        
                        for (tagPair in tags) {
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
                    
                    // Save final result to DB. The rawText contains tags, but we'll save the parsed version.
                    // If you want to keep tags in DB, use rawText. But saving parsed is better for history context.
                    val updatedList = _messages.value
                    val finalMsg = updatedList.find { it.id == modelMsgId }
                    if (finalMsg != null) {
                        val finalModelMsg = finalMsg.copy(tokensPerSecond = result.tokensPerSecond)
                        daexMemory?.saveMessage(convId, finalModelMsg)
                    }

                } catch (e: Exception) {
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
    }

    fun disconnect() {
        unloadModel()
        clearMessages()
        _errorMessage.value = null
    }
}

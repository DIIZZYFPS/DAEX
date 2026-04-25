package com.daex.android.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ModelStatus {
    NOT_DOWNLOADED, DOWNLOADING, LOADING, READY, ERROR
}

class DaexInferenceViewModel(
    private val llamaService: LlamaService,
    private val modelManager: ModelManager? = null,
    private val deviceService: DeviceService? = null
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

    private var generationJob: Job? = null

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

        val userMsgId = System.currentTimeMillis().toString()
        val modelMsgId = (System.currentTimeMillis() + 1).toString()

        val userMsg = Message(id = userMsgId, role = "user", content = prompt)
        val modelMsg = Message(id = modelMsgId, role = "model", content = "")

        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(userMsg)
        currentMessages.add(modelMsg)
        
        _messages.value = currentMessages
        _isGenerating.value = true
        _tokenSpeed.value = 0.0

        generationJob = viewModelScope.launch {
            try {
                // Pass exact conversation history including the new user message
                val history = _messages.value.filter { it.role == "user" || it.role == "model" && it.content.isNotEmpty() }
                
                val result = llamaService.generateResponse(history) { token ->
                    if (!isActive) return@generateResponse
                    val updated = _messages.value.toMutableList()
                    val idx = updated.indexOfFirst { it.id == modelMsgId }
                    if (idx != -1) {
                        updated[idx] = updated[idx].copy(content = updated[idx].content + token)
                        _messages.value = updated
                    }
                }
                _tokenSpeed.value = result.tokensPerSecond
            } catch (e: Exception) {
                val updated = _messages.value.toMutableList()
                val idx = updated.indexOfFirst { it.id == modelMsgId }
                if (idx != -1) {
                    updated[idx] = updated[idx].copy(content = updated[idx].content + "\n[Error: ${e.message ?: "Generation failed"}]")
                    _messages.value = updated
                }
            } finally {
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
        _messages.value = emptyList()
        _tokenSpeed.value = 0.0
    }

    fun disconnect() {
        unloadModel()
        clearMessages()
        _errorMessage.value = null
    }
}

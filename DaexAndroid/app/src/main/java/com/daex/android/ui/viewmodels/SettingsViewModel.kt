package com.daex.android.ui.viewmodels

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daex.android.data.DaexPreferences
import com.daex.android.data.DownloadPhase
import com.daex.android.data.GenerativeDownloadStatus
import com.daex.android.data.ModelDownloadState
import com.daex.android.data.ModelManager
import com.daex.android.domain.Model
import com.daex.android.framework.BackendType
import com.daex.android.framework.DaexService
import com.daex.android.framework.DeviceService
import com.daex.android.framework.DeviceSpecs
import com.daex.android.framework.ModelDownloadService
import com.daex.android.framework.ToolRegistry
import android.os.VibrationEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

enum class ModelStatus {
    NOT_DOWNLOADED, DOWNLOADING, LOADING, READY, ERROR
}

enum class HapticType {
    CLICK,
    TICK,
    DOUBLE_CLICK,
    HEAVY_CLICK,
    START_RESPONSE,
    SUCCESS_COMPLETION
}

/**
 * Owns generative-model download/load/backend lifecycle, theme, developer/inference settings,
 * tool toggles, and haptics/aura. The dependency root of the ViewModel split - it has no
 * reference to any of the other four ViewModels. [onModelReadyForFirstTime] is how a
 * chat-history-dependent action (seeding suggested prompts) gets triggered from [loadModel]
 * without this class needing to know ChatViewModel exists; ChatViewModel registers the callback
 * on itself in its own init since it already holds a reference to this class.
 */
class SettingsViewModel(
    private val daexService: DaexService,
    private val modelManager: ModelManager? = null,
    private val deviceService: DeviceService? = null,
    private val preferences: DaexPreferences? = null,
    private val context: android.content.Context? = null
) : ViewModel() {

    var onModelReadyForFirstTime: (() -> Unit)? = null

    // Wired by ChatViewModel in its own init - lets model/backend changes here stay blocked
    // while the shared engine is actively generating/reflecting/vectorizing, without this class
    // needing a reference back to ChatViewModel.
    var isChatBusy: () -> Boolean = { false }

    private val _modelStatus = MutableStateFlow(ModelStatus.NOT_DOWNLOADED)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    val downloadingModelId: StateFlow<String?> = _downloadingModelId.asStateFlow()

    private val _downloadedModelIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModelIds: StateFlow<Set<String>> = _downloadedModelIds.asStateFlow()

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

    val deviceSpecs: DeviceSpecs? = deviceService?.getDeviceSpecs()

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

        // Autoload last used model if already downloaded
        viewModelScope.launch {
            val modelId = preferences?.lastUsedModelIdFlow?.firstOrNull()
            val backendStr = preferences?.lastUsedBackendFlow?.firstOrNull() ?: "CPU"
            android.util.Log.d("DaexAutoload", "Autoload check: modelId=$modelId, backend=$backendStr")
            if (modelId != null) {
                val model = com.daex.android.domain.ModelBank.generativeModels.find { it.id == modelId }
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

    fun refreshDownloadedModels() {
        viewModelScope.launch {
            if (modelManager == null) return@launch
            val downloaded = com.daex.android.domain.ModelBank.generativeModels
                .filter { modelManager.isModelDownloaded(it) }
                .map { it.id }
                .toSet()
            _downloadedModelIds.value = downloaded
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

    fun setSpeculativeDecodingEnabled(enabled: Boolean) {
        if (isBusy()) {
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
                android.util.Log.e("SettingsViewModel", "Failed to trigger haptic feedback", e)
            }
        }
    }

    private fun isBusy() = _modelStatus.value == ModelStatus.LOADING || _modelStatus.value == ModelStatus.DOWNLOADING || isChatBusy()

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

    fun loadModel(model: Model) {
        if (isBusy()) {
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

                // First time this VM has reached READY - let ChatViewModel decide whether to
                // generate personalized suggestions or seed the cold-start pool. This class has
                // no reference to chat history, so it defers entirely to the callback.
                onModelReadyForFirstTime?.invoke()
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
        }
    }

    fun deleteModel(model: Model) {
        if (isBusy()) {
            _errorMessage.value = "Cannot delete models while the engine is busy."
            return
        }
        viewModelScope.launch {
            if (modelManager == null) return@launch
            try {
                if (_currentModel.value?.id == model.id) {
                    daexService.releaseContext()
                    _currentModel.value = null
                    _modelStatus.value = ModelStatus.NOT_DOWNLOADED
                }
                modelManager.deleteModel(model)
                refreshDownloadedModels()
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to delete model: ${model.name}", e)
            }
        }
    }

    fun toggleGPU(model: Model? = null) {
        if (isBusy()) {
            _errorMessage.value = "Cannot change backend while the engine is busy."
            return
        }
        val nextBackend = if (_selectedBackend.value == BackendType.CPU) BackendType.GPU else BackendType.CPU
        setBackend(nextBackend, model)
    }

    fun setBackend(backend: BackendType, model: Model? = null) {
        if (isBusy()) {
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
            targetModel.let {
                viewModelScope.launch {
                    preferences?.setLastUsedModel(it.id, backend.name)
                }
            }
        }
    }
}

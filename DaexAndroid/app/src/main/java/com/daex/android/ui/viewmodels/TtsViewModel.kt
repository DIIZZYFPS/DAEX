package com.daex.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daex.android.data.DaexPreferences
import com.daex.android.data.DownloadPhase
import com.daex.android.data.KokoroDownloadStatus
import com.daex.android.data.ModelDownloadState
import com.daex.android.data.ModelManager
import com.daex.android.framework.KokoroTtsService
import com.daex.android.framework.Message
import com.daex.android.framework.ModelDownloadService
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the Kokoro TTS engine and its download/enablement state, independent of every other
 * ViewModel in the split. [onLiveSpeakingChanged] lets AudioSessionViewModel (which holds a
 * reference to this class) react to speaking-state transitions for the live-voice loop without
 * this class needing to know AudioSessionViewModel exists.
 */
class TtsViewModel(
    private val modelManager: ModelManager? = null,
    private val preferences: DaexPreferences? = null,
    private val context: android.content.Context? = null
) : ViewModel() {

    var onLiveSpeakingChanged: ((Boolean) -> Unit)? = null

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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Id of the message currently being read aloud via the per-message read-aloud button
    // (separate from live-voice TTS, which never sets this).
    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private var kokoroTtsService: KokoroTtsService? = null

    val isSpeaking: Boolean get() = kokoroTtsService?.isSpeaking == true
    val chimeActiveUntilMs: Long get() = kokoroTtsService?.chimeActiveUntilMs ?: 0L

    init {
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
                onLiveSpeakingChanged?.invoke(speaking)
            }
        }

        refreshTtsDownloadedState()

        // Reconnect to an in-progress Kokoro download the same way generative-model reconnect
        // works in SettingsViewModel - see that class's init for the full rationale.
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
                        refreshTtsDownloadedState()
                    } else {
                        android.util.Log.e("TtsViewModel", "Reconnected TTS download failed: ${status.error}")
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
                        refreshTtsDownloadedState()
                    } else {
                        android.util.Log.e("TtsViewModel", "Background TTS download failed: ${status.error}")
                    }
                }
            }
        }
    }

    private fun refreshTtsDownloadedState() {
        viewModelScope.launch {
            _isTtsDownloaded.value = modelManager?.isKokoroDownloaded() ?: false
        }
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
    fun speakMessage(message: Message, isLiveVoiceActive: Boolean) {
        if (isLiveVoiceActive) return

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

    /** Speaks a sentence fragment during a live-voice generation stream. */
    fun speakLive(text: String) {
        kokoroTtsService?.speak(text, _ttsVoiceId.value)
    }

    fun stopPlayback() {
        kokoroTtsService?.stopPlayback()
    }

    fun playWakeChime() {
        kokoroTtsService?.playWakeChime()
    }

    fun playCloseChime() {
        kokoroTtsService?.playCloseChime()
    }

    fun initTts() {
        kokoroTtsService?.initTts()
    }

    /** Releases the native TTS engine (not the service's coroutine scope) - used when a live-voice
     * session ends, distinct from [onCleared] which tears the whole service down permanently. */
    fun releaseTts() {
        kokoroTtsService?.releaseTts()
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
                refreshTtsDownloadedState()
            } else {
                _errorMessage.value = status.error ?: "TTS Download failed"
                android.util.Log.e("TtsViewModel", "TTS model download failed: ${status.error}")
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
                refreshTtsDownloadedState()
            } catch (e: Exception) {
                android.util.Log.e("TtsViewModel", "Failed to delete TTS model", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        kokoroTtsService?.release()
    }
}

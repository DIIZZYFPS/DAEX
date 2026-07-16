package com.daex.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daex.android.framework.AudioRecorder
import com.daex.android.framework.SpeechManager
import com.daex.android.framework.TtsGateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VoiceState {
    IDLE, LISTENING, PROCESSING, SPEAKING
}

/**
 * Owns the live-voice session lifecycle: mic recording/VAD, audio focus, and the debounced
 * SPEAKING/LISTENING transitions driven by TTS playback. Depends on [ChatViewModel] (to submit
 * recorded chunks and cancel generation) and [TtsViewModel] (chimes, TTS gate state), both
 * constructed before this class. Since neither of those classes holds a reference back to this
 * one, this class registers callbacks on them in its own init to receive the notifications it
 * needs (speaking-state changes, generation-finished) - see each class's own callback docs.
 */
class AudioSessionViewModel(
    private val context: android.content.Context? = null,
    private val chatViewModel: ChatViewModel,
    private val ttsViewModel: TtsViewModel
) : ViewModel() {

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
                ttsViewModel.stopPlayback()
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                // Regained focus
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
                android.util.Log.w("AudioSessionViewModel", "Failed to clean up old audio cache files", e)
            }
        }

        ttsViewModel.onLiveSpeakingChanged = { speaking ->
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
                        if (!ttsViewModel.isSpeaking && _isLiveVoiceActive.value) {
                            setVoiceStateInternal(VoiceState.LISTENING)
                        }
                    }
                }
            }
        }

        chatViewModel.isLiveVoiceActive = { _isLiveVoiceActive.value }
        chatViewModel.currentVoiceState = { _voiceState.value }
        chatViewModel.setVoiceState = { state -> setVoiceStateInternal(state) }
        chatViewModel.abortLiveVoiceSession = {
            _isLiveVoiceActive.value = false
            setVoiceStateInternal(VoiceState.IDLE)
        }
    }

    private fun setVoiceStateInternal(state: VoiceState) {
        if (_isLiveVoiceActive.value && state == VoiceState.LISTENING && ttsViewModel.isSpeaking) {
            _voiceState.value = VoiceState.SPEAKING
            android.util.Log.w("AudioSessionViewModel", "Blocked transition to LISTENING because TTS is speaking.")
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
        if (ttsViewModel.isTtsEnabled.value && !ttsViewModel.isTtsDownloaded.value) {
            android.util.Log.w("AudioSessionViewModel", "Cannot start live voice session: TTS is enabled but not downloaded.")
            context?.let { ctx ->
                android.widget.Toast.makeText(ctx, "TTS voice engine is not downloaded yet. Please download it in Settings.", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }

        _isLiveVoiceActive.value = true
        setVoiceStateInternal(VoiceState.LISTENING)

        ttsViewModel.playWakeChime()

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
                android.util.Log.i("AudioSessionViewModel", "Requested Audio Focus: result=$result")
            } catch (e: Exception) {
                android.util.Log.e("AudioSessionViewModel", "Failed to request Audio Focus", e)
            }
        }

        // Note: We do NOT set AudioManager.mode to MODE_IN_COMMUNICATION or force speakerphone because
        // on some devices (especially Samsung), VoIP call routing triggers aggressive system-level half-duplex
        // echo suppression that completely silences/mutes the microphone input during active speaker playback.
        // Keeping it at MODE_NORMAL keeps the mic open; echo is handled by the TTS gate in AudioRecorder
        // (isSpeaking + cooldown), the RMS-scaled ducking floor, and hardware AEC where available.

        if (ttsViewModel.isTtsEnabled.value) {
            ttsViewModel.initTts()
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
                    ttsViewModel.isSpeaking -> TtsGateState.SPEAKING
                    // The wake/close chimes play via SoundPool, invisible to
                    // isSpeaking — gate them like TTS.
                    now < ttsViewModel.chimeActiveUntilMs -> TtsGateState.SPEAKING
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
        android.util.Log.d("AudioSessionViewModel", "VAD: Speech started")
    }

    private fun handleUserSilenceDetected(audioFile: java.io.File) {
        viewModelScope.launch {
            // Finalize current chunk by calling stop() on its recorder
            audioRecorder?.stop()

            android.util.Log.i("AudioSessionViewModel", "VAD: Silence detected. Finalizing chunk and submitting.")
            setVoiceStateInternal(VoiceState.PROCESSING)

            // Immediately start next segment to keep recording loop uninterrupted
            if (_isLiveVoiceActive.value) {
                startNewRecordingSegment()
            }

            if (audioFile.exists() && audioFile.length() > 44) {
                chatViewModel.submitAudioPrompt(audioFile.absolutePath)
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
        chatViewModel.cancelGeneration()
        chatViewModel.clearPendingAudio()
        liveAudioFiles.clear()
        ttsViewModel.stopPlayback()
        ttsViewModel.releaseTts()

        ttsViewModel.playCloseChime()

        // Abandon Audio Focus
        context?.let { ctx ->
            try {
                val audioManager = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                audioFocusRequest?.let { focusRequest ->
                    val result = audioManager.abandonAudioFocusRequest(focusRequest)
                    android.util.Log.i("AudioSessionViewModel", "Abandoned Audio Focus: result=$result")
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioSessionViewModel", "Failed to abandon Audio Focus", e)
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

    override fun onCleared() {
        super.onCleared()
        speechManager?.destroy()
    }
}

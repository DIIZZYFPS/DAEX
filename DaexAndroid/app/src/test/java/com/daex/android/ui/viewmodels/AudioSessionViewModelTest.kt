package com.daex.android.ui.viewmodels

import com.daex.android.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AudioSessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * TtsViewModel is mocked (rather than a real instance) specifically so [TtsViewModel.isSpeaking]
     * can be controlled directly - it's a computed property over a real, private KokoroTtsService
     * that only exists when a real Context is supplied, which a JVM unit test can't provide safely.
     */
    private fun ttsViewModelMock(isSpeaking: Boolean): TtsViewModel {
        val tts = mockk<TtsViewModel>(relaxed = true)
        every { tts.isTtsEnabled } returns MutableStateFlow(true)
        every { tts.isTtsDownloaded } returns MutableStateFlow(true)
        every { tts.isSpeaking } returns isSpeaking
        return tts
    }

    private fun chatViewModelMock(): ChatViewModel = mockk(relaxed = true)

    @Test
    fun `starting a live voice session while TTS is not speaking enters LISTENING`() {
        val viewModel = AudioSessionViewModel(
            context = null,
            chatViewModel = chatViewModelMock(),
            ttsViewModel = ttsViewModelMock(isSpeaking = false)
        )

        viewModel.startLiveVoiceSession {}

        assertTrue(viewModel.isLiveVoiceActive.value)
        assertEquals(VoiceState.LISTENING, viewModel.voiceState.value)
    }

    @Test
    fun `starting a live voice session while TTS is already speaking enters SPEAKING instead`() {
        val viewModel = AudioSessionViewModel(
            context = null,
            chatViewModel = chatViewModelMock(),
            ttsViewModel = ttsViewModelMock(isSpeaking = true)
        )

        viewModel.startLiveVoiceSession {}

        assertTrue(viewModel.isLiveVoiceActive.value)
        assertEquals(VoiceState.SPEAKING, viewModel.voiceState.value)
    }

    @Test
    fun `setVoiceState blocks the transition to LISTENING while TTS is speaking mid-session`() {
        // This is the exact self-interruption guard the Phase 1 audio work depends on: once a
        // live-voice session is active, nothing may force the state back to LISTENING while the
        // model's own voice is still playing, or the mic would pick up the model hearing itself.
        val tts = ttsViewModelMock(isSpeaking = false)
        val viewModel = AudioSessionViewModel(
            context = null,
            chatViewModel = chatViewModelMock(),
            ttsViewModel = tts
        )
        viewModel.startLiveVoiceSession {}
        assertEquals(VoiceState.LISTENING, viewModel.voiceState.value)

        every { tts.isSpeaking } returns true
        viewModel.setVoiceState(VoiceState.LISTENING)

        assertEquals(VoiceState.SPEAKING, viewModel.voiceState.value)
    }

    @Test
    fun `setVoiceState allows LISTENING once TTS stops speaking`() {
        val tts = ttsViewModelMock(isSpeaking = true)
        val viewModel = AudioSessionViewModel(
            context = null,
            chatViewModel = chatViewModelMock(),
            ttsViewModel = tts
        )
        viewModel.startLiveVoiceSession {}
        assertEquals(VoiceState.SPEAKING, viewModel.voiceState.value)

        every { tts.isSpeaking } returns false
        viewModel.setVoiceState(VoiceState.LISTENING)

        assertEquals(VoiceState.LISTENING, viewModel.voiceState.value)
    }

    @Test
    fun `setVoiceState IDLE always ends the live-voice session`() {
        val viewModel = AudioSessionViewModel(
            context = null,
            chatViewModel = chatViewModelMock(),
            ttsViewModel = ttsViewModelMock(isSpeaking = true)
        )
        viewModel.startLiveVoiceSession {}

        viewModel.setVoiceState(VoiceState.IDLE)

        assertEquals(VoiceState.IDLE, viewModel.voiceState.value)
        assertFalse(viewModel.isLiveVoiceActive.value)
    }
}

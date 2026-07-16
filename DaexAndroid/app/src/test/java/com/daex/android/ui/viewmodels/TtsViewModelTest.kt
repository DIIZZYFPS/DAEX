package com.daex.android.ui.viewmodels

import com.daex.android.MainDispatcherRule
import com.daex.android.data.DaexPreferences
import com.daex.android.framework.Message
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class TtsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `setTtsEnabled updates state and persists`() {
        val prefs = mockk<DaexPreferences>(relaxed = true)
        val viewModel = TtsViewModel(preferences = prefs)

        viewModel.setTtsEnabled(false)

        assertEquals(false, viewModel.isTtsEnabled.value)
        coVerify(exactly = 1) { prefs.setTtsEnabled(false) }
    }

    @Test
    fun `setTtsVoiceId updates state and persists`() {
        val prefs = mockk<DaexPreferences>(relaxed = true)
        val viewModel = TtsViewModel(preferences = prefs)

        viewModel.setTtsVoiceId(7)

        assertEquals(7, viewModel.ttsVoiceId.value)
        coVerify(exactly = 1) { prefs.setTtsVoiceId(7) }
    }

    @Test
    fun `speakMessage is a no-op while a live-voice session is active`() {
        val viewModel = TtsViewModel()
        val message = Message(id = "msg-1", role = "model", content = "hello")

        viewModel.speakMessage(message, isLiveVoiceActive = true)

        assertNull(viewModel.speakingMessageId.value)
    }

    @Test
    fun `speakMessage does not start playback when the voice model isn't downloaded yet`() {
        // isTtsDownloaded defaults to false with no ModelManager wired up.
        val viewModel = TtsViewModel()
        val message = Message(id = "msg-1", role = "model", content = "hello")

        viewModel.speakMessage(message, isLiveVoiceActive = false)

        assertEquals(false, viewModel.isTtsDownloaded.value)
        assertNull(viewModel.speakingMessageId.value)
    }
}

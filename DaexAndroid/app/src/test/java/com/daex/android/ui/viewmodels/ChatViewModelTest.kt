package com.daex.android.ui.viewmodels

import com.daex.android.MainDispatcherRule
import com.daex.android.data.DaexMemory
import com.daex.android.framework.DaexService
import com.daex.android.framework.Message
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        daexMemory: DaexMemory? = null,
        daexService: DaexService = mockk(relaxed = true)
    ): ChatViewModel {
        val settingsViewModel = SettingsViewModel(daexService = daexService)
        val ttsViewModel = TtsViewModel()
        return ChatViewModel(
            daexService = daexService,
            daexMemory = daexMemory,
            settingsViewModel = settingsViewModel,
            ttsViewModel = ttsViewModel
        )
    }

    private fun message(
        id: String = "msg-1",
        role: String = "model",
        content: String = "hello",
        isPinned: Boolean = false,
        thoughtContent: String? = null
    ) = Message(id = id, role = role, content = content, isPinned = isPinned, thoughtContent = thoughtContent)

    // --- pruneToolOutputs ---

    @Test
    fun `pruneToolOutputs summarizes long multi-line model output`() {
        val viewModel = viewModel()
        val longContent = (1..30).joinToString("\n") { "line $it of a very verbose tool dump" }
        val msg = message(role = "model", content = longContent)

        val pruned = viewModel.pruneToolOutputs(listOf(msg))

        assertTrue(pruned[0].content.startsWith("[Verbose tool output pruned:"))
        assertTrue(pruned[0].content.contains("30 lines"))
    }

    @Test
    fun `pruneToolOutputs leaves long single-line model output untouched`() {
        val viewModel = viewModel()
        val longSingleLine = "x".repeat(900)
        val msg = message(role = "model", content = longSingleLine)

        val pruned = viewModel.pruneToolOutputs(listOf(msg))

        assertEquals(longSingleLine, pruned[0].content)
    }

    @Test
    fun `pruneToolOutputs leaves short model messages and user messages untouched`() {
        val viewModel = viewModel()
        val shortModel = message(role = "model", content = "short reply")
        val longUser = message(id = "msg-2", role = "user", content = "a".repeat(2000))

        val pruned = viewModel.pruneToolOutputs(listOf(shortModel, longUser))

        assertEquals("short reply", pruned[0].content)
        assertEquals("a".repeat(2000), pruned[1].content)
    }

    // --- estimateTokens / estimateMessageTokens ---

    @Test
    fun `estimateTokens divides character count by 4`() {
        val viewModel = viewModel()
        assertEquals(10, viewModel.estimateTokens("x".repeat(40)))
    }

    @Test
    fun `estimateMessageTokens includes thought content`() {
        val viewModel = viewModel()
        val msg = message(content = "x".repeat(20), thoughtContent = "y".repeat(20))
        assertEquals(10, viewModel.estimateMessageTokens(msg))
    }

    // --- submitPrompt guards ---

    @Test
    fun `submitPrompt no-ops on a blank prompt`() {
        val viewModel = viewModel()

        viewModel.submitPrompt("   ")

        assertTrue(viewModel.messages.value.isEmpty())
        assertFalse(viewModel.isGenerating.value)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun `submitPrompt sets an error when the model is not ready`() {
        // Default SettingsViewModel.modelStatus is NOT_DOWNLOADED, so this guard fires without
        // any extra setup.
        val viewModel = viewModel()

        viewModel.submitPrompt("hello there")

        assertEquals("Model is not loaded yet.", viewModel.errorMessage.value)
        assertTrue(viewModel.messages.value.isEmpty())
        assertFalse(viewModel.isGenerating.value)
    }

    // --- togglePin ---

    @Test
    fun `togglePin flips the pin state and persists it`() {
        val daexMemory = mockk<DaexMemory>(relaxed = true)
        val pinnedMsg = message(id = "msg-1", isPinned = true)
        coEvery { daexMemory.getPinnedMessages() } returns listOf(pinnedMsg)
        val viewModel = viewModel(daexMemory = daexMemory)
        val unpinnedMsg = message(id = "msg-1", isPinned = false)

        viewModel.togglePin(unpinnedMsg)

        coVerify(exactly = 1) { daexMemory.setPinned("msg-1", true) }
        assertEquals(listOf(pinnedMsg), viewModel.pinnedMessages.value)
    }

    // --- clearMessages / clearError ---

    @Test
    fun `clearMessages resets conversation state`() {
        val viewModel = viewModel()

        viewModel.clearMessages()

        assertNull(viewModel.currentConversationId.value)
        assertTrue(viewModel.messages.value.isEmpty())
        assertEquals(0.0, viewModel.tokenSpeed.value, 0.0)
        assertTrue(viewModel.attachedFiles.value.isEmpty())
    }

    @Test
    fun `clearError clears the error message`() {
        val viewModel = viewModel()
        viewModel.submitPrompt("hello there") // triggers "Model is not loaded yet."
        assertEquals("Model is not loaded yet.", viewModel.errorMessage.value)

        viewModel.clearError()

        assertNull(viewModel.errorMessage.value)
    }
}

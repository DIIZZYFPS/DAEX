package com.daex.android

import android.content.Context
import com.daex.android.data.DaexMemory
import com.daex.android.data.DaexPreferences
import com.daex.android.data.MyObjectBox
import com.daex.android.domain.Model
import com.daex.android.framework.BackendType
import com.daex.android.framework.DaexService
import com.daex.android.framework.GenerationResult
import com.daex.android.framework.Message
import com.daex.android.ui.viewmodels.AudioSessionViewModel
import com.daex.android.ui.viewmodels.ChatViewModel
import com.daex.android.ui.viewmodels.ModelStatus
import com.daex.android.ui.viewmodels.OnboardingViewModel
import com.daex.android.ui.viewmodels.SettingsViewModel
import com.daex.android.ui.viewmodels.TtsViewModel
import com.daex.android.ui.viewmodels.VoiceState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A deterministic stand-in for the real LiteRT engine: instant "loading", and
 * `generateResponse`/`generateSilent` return canned, controllable text instead of running actual
 * on-device inference. This is what makes it possible to exercise the full chat pipeline
 * (compaction thresholds aside) without a device.
 */
private class FakeDaexService : DaexService {
    var nextResponse: String = "Hello! How can I help?"
    private var loaded = false

    override suspend fun initContext(
        modelPath: String,
        backendType: BackendType,
        isSpeculativeDecodingEnabled: Boolean
    ): BackendType {
        loaded = true
        return backendType
    }

    override suspend fun releaseContext() {
        loaded = false
    }

    override suspend fun generateResponse(
        messages: List<Message>,
        systemContext: String,
        isReasoningEnabled: Boolean,
        temperature: Float,
        topK: Int,
        topP: Float,
        customSystemPrompt: String,
        isToolCallingEnabled: Boolean,
        disabledToolIds: Set<String>,
        onRequestPermission: (suspend (String, String) -> Boolean)?,
        onStatusUpdate: ((String?) -> Unit)?,
        maxTokens: Int,
        isLiveVoiceActive: Boolean,
        conversationId: String?,
        onToken: (String) -> Unit
    ): GenerationResult {
        nextResponse.split(" ").forEach { onToken("$it ") }
        return GenerationResult(text = nextResponse, tokensPerSecond = 42.0)
    }

    override suspend fun generateSilent(prompt: String, maxTokens: Int): String = nextResponse

    override fun isLoaded(): Boolean = loaded
}

/**
 * Wires all 5 real ViewModels together exactly as [MainActivity] does - a real ObjectBox
 * [BoxStore] and real FTS5 index underneath (same pattern as DaexMemoryIntegrationTest), a
 * [FakeDaexService] standing in for the on-device LLM engine (the one thing that genuinely can't
 * run off-device), and everything else genuinely real. This is the closest equivalent to an
 * on-device end-to-end test achievable in this environment - it exists because a full instrumented
 * UI walkthrough isn't runnable here (see the androidTest scaffold's own caveat).
 */
class EndToEndScenarioTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var boxStore: BoxStore
    private lateinit var daexMemory: DaexMemory
    private lateinit var fakeDaexService: FakeDaexService
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var ttsViewModel: TtsViewModel
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var audioSessionViewModel: AudioSessionViewModel
    private lateinit var onboardingViewModel: OnboardingViewModel

    private val testModel = Model(
        id = "e2e-model",
        name = "E2E Test Model",
        size = 1000L,
        description = "d",
        requiredRAM = 1L,
        downloadUrl = "https://example.com/model.litertlm",
        extension = "litertlm",
        provider = "test",
        familyId = "fam",
        familyName = "Fam",
        sizeName = "S",
        variantName = "base"
    )

    @Before
    fun setup() {
        boxStore = MyObjectBox.builder().directory(tempFolder.newFolder("objectbox")).build()
        val dbContext = mockk<Context>()
        every { dbContext.getDatabasePath(any()) } answers { File(tempFolder.root, "databases/${firstArg<String>()}") }
        daexMemory = DaexMemory(boxStore = boxStore, embedder = null, context = dbContext)

        val onboardingPrefs = mockk<DaexPreferences>(relaxed = true)
        every { onboardingPrefs.hasCompletedLandingFlow } returns flowOf(false)

        fakeDaexService = FakeDaexService()
        val modelManager = mockk<com.daex.android.data.ModelManager>(relaxed = true)
        coEvery { modelManager.isModelDownloaded(testModel) } returns true
        every { modelManager.getModelPath(testModel) } returns "/fake/path/e2e-model.litertlm"

        settingsViewModel = SettingsViewModel(daexService = fakeDaexService, modelManager = modelManager)
        ttsViewModel = TtsViewModel()
        chatViewModel = ChatViewModel(
            daexService = fakeDaexService,
            daexMemory = daexMemory,
            settingsViewModel = settingsViewModel,
            ttsViewModel = ttsViewModel
        )
        audioSessionViewModel = AudioSessionViewModel(
            context = null,
            chatViewModel = chatViewModel,
            ttsViewModel = ttsViewModel
        )
        onboardingViewModel = OnboardingViewModel(onboardingPrefs)
    }

    @After
    fun tearDown() {
        boxStore.close()
    }

    /**
     * [ChatViewModel.submitPrompt]/[ChatViewModel.togglePin] are fire-and-forget
     * (`viewModelScope.launch { ... }`, not suspend functions), and their bodies touch the real
     * [DaexMemory], which internally hops to a genuine `Dispatchers.IO` thread rather than the
     * [MainDispatcherRule]'s test dispatcher. That real thread hop is exactly what a plain
     * `runBlocking` test doesn't automatically wait for, so this polls a real (short) delay
     * loop until [condition] holds instead of asserting immediately after the call returns.
     */
    private suspend fun awaitUntil(timeoutMs: Long = 2000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }
        assertTrue("Condition was not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun `full user journey - onboarding, model load, chat, pin, search, and a live-voice session`() = runBlocking {
        // 1. Onboarding starts incomplete and can be completed.
        assertEquals(false, onboardingViewModel.hasCompletedOnboarding.value)
        onboardingViewModel.completeOnboarding()
        assertEquals(true, onboardingViewModel.hasCompletedOnboarding.value)

        // 2. Loading a model reaches READY using the fake engine.
        settingsViewModel.loadModel(testModel)
        awaitUntil { settingsViewModel.modelStatus.value == ModelStatus.READY }
        assertEquals(testModel, settingsViewModel.currentModel.value)

        // 3. Sending a prompt produces a real round trip through ChatViewModel + DaexMemory.
        // Waiting on `!isGenerating` alone is ambiguous: it's also true before generation ever
        // starts (submitPrompt's own placeholder-insert + saveMessage calls suspend on real
        // Dispatchers.IO before `_isGenerating` flips true), so this instead polls for the
        // concrete outcome the test actually cares about - a non-blank reply landing in the list.
        fakeDaexService.nextResponse = "The mitochondria is the powerhouse of the cell"
        chatViewModel.submitPrompt("Tell me a fun biology fact")
        awaitUntil {
            chatViewModel.messages.value.size == 2 && chatViewModel.messages.value[1].content.isNotBlank()
        }

        val userMsg = chatViewModel.messages.value[0]
        val modelMsg = chatViewModel.messages.value[1]
        assertEquals("Tell me a fun biology fact", userMsg.content)
        assertEquals("The mitochondria is the powerhouse of the cell ", modelMsg.content)
        assertTrue(chatViewModel.tokenSpeed.value > 0)

        // 4. Pinning the reply surfaces it in the saved-prompt library, backed by real ObjectBox.
        chatViewModel.togglePin(modelMsg)
        awaitUntil { chatViewModel.pinnedMessages.value.any { it.id == modelMsg.id } }

        // 5. Cross-conversation search finds it via the real FTS5 index. Calling DaexMemory
        // directly (not ChatViewModel.searchConversations) since that wrapper debounces via
        // viewModelScope + delay(250), which needs virtual-time advancement to resolve
        // deterministically in a plain runBlocking test - the debounce itself isn't what this
        // test is verifying.
        val searchResults = daexMemory.searchMessages("mitochondria powerhouse")
        assertEquals(1, searchResults.size)
        assertEquals(modelMsg.id, searchResults[0].message.id)
        assertEquals(chatViewModel.currentConversationId.value, searchResults[0].conversationId)

        // 6. Starting a live-voice session flips AudioSessionViewModel's state and is visible
        // to ChatViewModel through the callback wiring AudioSessionViewModel registered in its
        // own init - proof the cross-ViewModel plumbing from the last phase actually works.
        // TtsViewModel defaults to enabled but, with no real ModelManager behind it in this test,
        // never reports the Kokoro voice as downloaded - startLiveVoiceSession's own guard
        // refuses to start a session in that combination (real production behavior, not a test
        // artifact), so this disables TTS first exactly as a real user without it downloaded
        // would from Settings.
        ttsViewModel.setTtsEnabled(false)
        audioSessionViewModel.startLiveVoiceSession {}
        assertEquals(VoiceState.LISTENING, audioSessionViewModel.voiceState.value)
        assertTrue(audioSessionViewModel.isLiveVoiceActive.value)
        assertTrue(chatViewModel.isLiveVoiceActive())
    }
}

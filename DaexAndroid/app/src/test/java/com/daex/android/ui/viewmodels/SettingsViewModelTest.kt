package com.daex.android.ui.viewmodels

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.daex.android.MainDispatcherRule
import com.daex.android.data.DaexPreferences
import com.daex.android.domain.Model
import com.daex.android.framework.BackendType
import com.daex.android.framework.DaexService
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun testModel(
        id: String = "test-model",
        supportedBackends: List<BackendType> = listOf(BackendType.CPU, BackendType.GPU)
    ) = Model(
        id = id,
        name = "Test Model",
        size = 1024L,
        description = "A model for tests",
        requiredRAM = 512L,
        downloadUrl = "https://example.com/model.bin",
        extension = "bin",
        supportedBackends = supportedBackends,
        provider = "test",
        familyId = "test-family",
        familyName = "Test Family",
        sizeName = "S",
        variantName = "base"
    )

    private fun viewModel(preferences: DaexPreferences? = mockk(relaxed = true)): Pair<SettingsViewModel, DaexService> {
        val daexService = mockk<DaexService>(relaxed = true)
        val viewModel = SettingsViewModel(daexService = daexService, preferences = preferences)
        return viewModel to daexService
    }

    @Test
    fun `setBackend is blocked while chat is busy`() {
        val (viewModel, _) = viewModel()
        viewModel.isChatBusy = { true }

        viewModel.setBackend(BackendType.GPU)

        assertEquals(BackendType.CPU, viewModel.selectedBackend.value)
        assertEquals("Cannot change backend while the engine is busy.", viewModel.errorMessage.value)
    }

    @Test
    fun `toggleGPU is blocked while chat is busy`() {
        val (viewModel, _) = viewModel()
        viewModel.isChatBusy = { true }

        viewModel.toggleGPU()

        assertEquals(BackendType.CPU, viewModel.selectedBackend.value)
        assertEquals("Cannot change backend while the engine is busy.", viewModel.errorMessage.value)
    }

    @Test
    fun `loadModel is blocked while chat is busy`() {
        val (viewModel, _) = viewModel()
        viewModel.isChatBusy = { true }

        viewModel.loadModel(testModel())

        assertNull(viewModel.currentModel.value)
        assertEquals("Cannot change models while the engine is busy.", viewModel.errorMessage.value)
    }

    @Test
    fun `deleteModel is blocked while chat is busy`() {
        val (viewModel, _) = viewModel()
        viewModel.isChatBusy = { true }

        viewModel.deleteModel(testModel())

        assertEquals("Cannot delete models while the engine is busy.", viewModel.errorMessage.value)
    }

    @Test
    fun `setSpeculativeDecodingEnabled is blocked while chat is busy`() {
        val (viewModel, _) = viewModel()
        viewModel.isChatBusy = { true }

        viewModel.setSpeculativeDecodingEnabled(true)

        assertEquals(false, viewModel.isSpeculativeDecodingEnabled.value)
        assertEquals("Cannot change settings while the engine is busy.", viewModel.errorMessage.value)
    }

    @Test
    fun `setBackend rejects a backend the target model does not support`() {
        val (viewModel, _) = viewModel()
        val cpuOnlyModel = testModel(supportedBackends = listOf(BackendType.CPU))

        viewModel.setBackend(BackendType.GPU, cpuOnlyModel)

        assertEquals(BackendType.CPU, viewModel.selectedBackend.value)
        assertEquals("Test Model does not support GPU execution.", viewModel.errorMessage.value)
    }

    @Test
    fun `setThemeColor updates state and persists`() {
        val prefs = mockk<DaexPreferences>(relaxed = true)
        val (viewModel, _) = viewModel(prefs)
        val color = Color(0xFF112233)

        viewModel.setThemeColor(color)

        assertEquals(color, viewModel.primaryColor.value)
        coVerify(exactly = 1) { prefs.setPrimaryColor(color.toArgb()) }
    }

    @Test
    fun `setDarkMode updates state and persists`() {
        val prefs = mockk<DaexPreferences>(relaxed = true)
        val (viewModel, _) = viewModel(prefs)

        viewModel.setDarkMode(false)

        assertEquals(false, viewModel.isDarkMode.value)
        coVerify(exactly = 1) { prefs.setDarkMode(false) }
    }
}

package com.daex.android.ui.viewmodels

import com.daex.android.MainDispatcherRule
import com.daex.android.data.DaexPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun preferencesMock(hasCompleted: Boolean = false): DaexPreferences {
        val prefs = mockk<DaexPreferences>(relaxed = true)
        every { prefs.hasCompletedLandingFlow } returns flowOf(hasCompleted)
        return prefs
    }

    @Test
    fun `initial state reflects preferences flow`() {
        val viewModel = OnboardingViewModel(preferencesMock(hasCompleted = true))
        assertEquals(true, viewModel.hasCompletedOnboarding.value)
    }

    @Test
    fun `initial state is false when preferences reports incomplete`() {
        val viewModel = OnboardingViewModel(preferencesMock(hasCompleted = false))
        assertEquals(false, viewModel.hasCompletedOnboarding.value)
    }

    @Test
    fun `null preferences defaults to already completed`() {
        val viewModel = OnboardingViewModel(preferences = null)
        assertEquals(true, viewModel.hasCompletedOnboarding.value)
    }

    @Test
    fun `completeOnboarding persists the flag and updates state`() {
        val prefs = preferencesMock(hasCompleted = false)
        coEvery { prefs.completeLandingPage() } returns Unit
        val viewModel = OnboardingViewModel(prefs)

        viewModel.completeOnboarding()

        coVerify(exactly = 1) { prefs.completeLandingPage() }
        assertEquals(true, viewModel.hasCompletedOnboarding.value)
    }

    @Test
    fun `startReplay and endReplay toggle replay mode`() {
        val viewModel = OnboardingViewModel(preferencesMock())

        assertFalse(viewModel.isReplayMode.value)

        viewModel.startReplay()
        assertTrue(viewModel.isReplayMode.value)

        viewModel.endReplay()
        assertFalse(viewModel.isReplayMode.value)
    }
}

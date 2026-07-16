package com.daex.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daex.android.data.DaexPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the landing/onboarding flow's completion state and replay mode. Fully independent -
 * takes only [DaexPreferences], no reference to any other ViewModel.
 */
class OnboardingViewModel(
    private val preferences: DaexPreferences? = null
) : ViewModel() {

    private val _hasCompletedOnboarding = MutableStateFlow<Boolean?>(null)
    val hasCompletedOnboarding: StateFlow<Boolean?> = _hasCompletedOnboarding.asStateFlow()

    private val _isReplayMode = MutableStateFlow(false)
    val isReplayMode: StateFlow<Boolean> = _isReplayMode.asStateFlow()

    init {
        viewModelScope.launch {
            _hasCompletedOnboarding.value = preferences?.hasCompletedLandingFlow?.first() ?: true
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences?.completeLandingPage()
            _hasCompletedOnboarding.value = true
        }
    }

    fun startReplay() {
        _isReplayMode.value = true
    }

    fun endReplay() {
        _isReplayMode.value = false
    }
}

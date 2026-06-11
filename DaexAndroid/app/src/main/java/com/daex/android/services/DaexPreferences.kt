package com.daex.android.services

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DaexPreferences(private val context: Context) {

    companion object {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val PRIMARY_COLOR = intPreferencesKey("primary_color")
        val HAS_COMPLETED_LANDING = booleanPreferencesKey("has_completed_landing")
        val IS_REASONING_ENABLED = booleanPreferencesKey("is_reasoning_enabled")
        val LAST_USED_MODEL_ID = stringPreferencesKey("last_used_model_id")
        val LAST_USED_BACKEND = stringPreferencesKey("last_used_backend")

        // Developer Settings Keys
        val IS_SPECULATIVE_DECODING_ENABLED = booleanPreferencesKey("is_speculative_decoding_enabled")
        val INFERENCE_TEMPERATURE = floatPreferencesKey("inference_temperature")
        val INFERENCE_TOP_K = intPreferencesKey("inference_top_k")
        val INFERENCE_TOP_P = floatPreferencesKey("inference_top_p")
        val CUSTOM_SYSTEM_PROMPT = stringPreferencesKey("custom_system_prompt")
        val IS_TOOL_CALLING_ENABLED = booleanPreferencesKey("is_tool_calling_enabled")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val IS_HAPTIC_ENABLED = booleanPreferencesKey("is_haptic_enabled")
        val IS_AURA_ENABLED = booleanPreferencesKey("is_aura_enabled")
        val SUGGESTED_PROMPTS = stringPreferencesKey("suggested_prompts")
    }

    val lastUsedModelIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_USED_MODEL_ID]
    }

    val lastUsedBackendFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_USED_BACKEND]
    }

    val isReasoningEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_REASONING_ENABLED] ?: false
    }

    val isDarkModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_DARK_MODE] ?: true
    }

    val primaryColorFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PRIMARY_COLOR] ?: Color(0xFF00FFFF).toArgb()
    }

    val hasCompletedLandingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_COMPLETED_LANDING] ?: false
    }

    // Developer Settings Flows
    val isSpeculativeDecodingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_SPECULATIVE_DECODING_ENABLED] ?: false
    }

    val inferenceTemperatureFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[INFERENCE_TEMPERATURE] ?: 0.7f
    }

    val inferenceTopKFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[INFERENCE_TOP_K] ?: 40
    }

    val inferenceTopPFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[INFERENCE_TOP_P] ?: 0.9f
    }

    val customSystemPromptFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_SYSTEM_PROMPT] ?: ""
    }

    val isToolCallingEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_TOOL_CALLING_ENABLED] ?: false
    }

    val maxTokensFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MAX_TOKENS] ?: 1024
    }

    val isHapticEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_HAPTIC_ENABLED] ?: true
    }

    val isAuraEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_AURA_ENABLED] ?: true
    }

    val suggestedPromptsFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val saved = preferences[SUGGESTED_PROMPTS]
        if (!saved.isNullOrBlank()) {
            saved.split("\n").filter { it.isNotBlank() }
        } else {
            listOf(
                "Explain quantum entanglement simply",
                "Write a haiku about midnight code",
                "Plan a 3-day trip to Lisbon"
            )
        }
    }

    suspend fun setDarkMode(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DARK_MODE] = isDark
        }
    }

    suspend fun setPrimaryColor(color: Int) {
        context.dataStore.edit { preferences ->
            preferences[PRIMARY_COLOR] = color
        }
    }

    suspend fun completeLandingPage() {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_LANDING] = true
        }
    }

    suspend fun setReasoningEnabled(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_REASONING_ENABLED] = isEnabled
        }
    }

    suspend fun setLastUsedModel(modelId: String, backend: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_USED_MODEL_ID] = modelId
            preferences[LAST_USED_BACKEND] = backend
        }
    }

    // Developer Settings Setters
    suspend fun setSpeculativeDecodingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_SPECULATIVE_DECODING_ENABLED] = enabled
        }
    }

    suspend fun setInferenceTemperature(temp: Float) {
        context.dataStore.edit { preferences ->
            preferences[INFERENCE_TEMPERATURE] = temp
        }
    }

    suspend fun setInferenceTopK(topK: Int) {
        context.dataStore.edit { preferences ->
            preferences[INFERENCE_TOP_K] = topK
        }
    }

    suspend fun setInferenceTopP(topP: Float) {
        context.dataStore.edit { preferences ->
            preferences[INFERENCE_TOP_P] = topP
        }
    }

    suspend fun setCustomSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_SYSTEM_PROMPT] = prompt
        }
    }

    suspend fun setToolCallingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_TOOL_CALLING_ENABLED] = enabled
        }
    }

    suspend fun setMaxTokens(maxTokens: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_TOKENS] = maxTokens
        }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun setAuraEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_AURA_ENABLED] = enabled
        }
    }

    suspend fun setSuggestedPrompts(prompts: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[SUGGESTED_PROMPTS] = prompts.joinToString("\n")
        }
    }
}

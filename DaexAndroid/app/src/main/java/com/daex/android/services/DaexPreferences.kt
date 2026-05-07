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
        val LAST_CONVERSATION_ID = stringPreferencesKey("last_conversation_id")
    }

    val lastConversationIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_CONVERSATION_ID]
    }

    val isReasoningEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_REASONING_ENABLED] ?: true
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

    suspend fun setLastConversationId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id == null) {
                preferences.remove(LAST_CONVERSATION_ID)
            } else {
                preferences[LAST_CONVERSATION_ID] = id
            }
        }
    }
}

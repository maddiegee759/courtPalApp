package com.example.courtpalapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.courtPalDataStore by preferencesDataStore(
    name = "courtpal_preferences"
)

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.courtPalDataStore

    private fun scoringKey(userId: Long) =
        stringPreferencesKey("user_${userId}_scoring_mode")

    private fun formatKey(userId: Long) =
        stringPreferencesKey("user_${userId}_match_format")

    private fun rallyKey(userId: Long) =
        booleanPreferencesKey("user_${userId}_rally_tracking")

    private fun calendarKey(userId: Long) =
        stringPreferencesKey("user_${userId}_calendar_sync")

    private fun themeKey(userId: Long) =
        stringPreferencesKey("user_${userId}_theme")

    fun observePreferences(userId: Long): Flow<UserPreferences> =
        dataStore.data.map { preferences ->
            UserPreferences(
                scoringMode = preferences[scoringKey(userId)] ?: "LIVE",
                matchFormat = preferences[formatKey(userId)] ?: "3",
                rallyTracking = preferences[rallyKey(userId)] ?: false,
                calendarSync = preferences[calendarKey(userId)] ?: "ASK",
                theme = preferences[themeKey(userId)] ?: "SYSTEM"
            )
        }

    suspend fun updateScoringMode(userId: Long, value: String) {
        require(value in setOf("LIVE", "MANUAL"))
        dataStore.edit { it[scoringKey(userId)] = value }
    }

    suspend fun updateMatchFormat(userId: Long, value: String) {
        require(value in setOf("1", "3", "5"))
        dataStore.edit { it[formatKey(userId)] = value }
    }

    suspend fun updateRallyTracking(userId: Long, value: Boolean) {
        dataStore.edit { it[rallyKey(userId)] = value }
    }

    suspend fun updateCalendarSync(userId: Long, value: String) {
        require(value in setOf("ASK", "AUTO", "OFF"))
        dataStore.edit { it[calendarKey(userId)] = value }
    }

    suspend fun updateTheme(userId: Long, value: String) {
        require(value in setOf("SYSTEM", "LIGHT", "DARK"))
        dataStore.edit { it[themeKey(userId)] = value }
    }
}

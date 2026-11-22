package com.example.courtpalapp.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SessionManager(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences("courtpal_session", Context.MODE_PRIVATE)

    companion object {
        private val _currentUserId = MutableStateFlow<Long?>(null)
        private var initialized = false
    }

    init {
        synchronized(SessionManager::class.java) {
            if (!initialized) {
                _currentUserId.value = preferences
                    .getLong("current_user_id", -1L)
                    .takeIf { it != -1L }

                initialized = true
            }
        }
    }

    val currentUserId: StateFlow<Long?> = _currentUserId

    fun login(userId: Long) {
        preferences.edit()
            .putLong("current_user_id", userId)
            .apply()

        _currentUserId.value = userId
    }

    fun logout() {
        preferences.edit()
            .remove("current_user_id")
            .apply()

        _currentUserId.value = null
    }
}
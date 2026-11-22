
package com.example.courtpalapp

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.courtpalapp.data.local.SessionManager
import com.example.courtpalapp.data.local.UserPreferencesRepository
import com.example.courtpalapp.navigation.AppNavHost
import com.example.courtpalapp.ui.theme.CourtPalAppTheme

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CourtPalRoot()
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun CourtPalRoot() {
    val context = LocalContext.current

    val sessionManager = remember(context) {
        SessionManager(context)
    }

    val preferencesRepository = remember(context) {
        UserPreferencesRepository(context)
    }

    val currentUserId by sessionManager.currentUserId.collectAsState()

    val preferences = key(currentUserId) {
        val userId = currentUserId

        if (userId != null) {
            val themeFlow = remember(userId, preferencesRepository) {
                preferencesRepository.observePreferences(userId)
            }

            themeFlow.collectAsState(initial = null).value
        } else {
            null
        }
    }

    val systemDarkTheme = isSystemInDarkTheme()

    val darkTheme = when (preferences?.theme) {
        "LIGHT" -> false
        "DARK" -> true
        else -> systemDarkTheme
    }

    CourtPalAppTheme(darkTheme = darkTheme) {
        key(currentUserId) {
            AppNavHost()
        }
    }
}

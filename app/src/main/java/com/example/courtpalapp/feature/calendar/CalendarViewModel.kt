package com.example.courtpalapp.feature.calendar

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.courtpalapp.api.calendar.CalendarAuthManager
import com.example.courtpalapp.navigation.AppNavHost
import com.example.courtpalapp.ui.theme.CourtPalAppTheme

@Composable
fun CalendarAuthTest() {
    val activity = LocalActivity.current as ComponentActivity
    val authManager = androidx.compose.runtime.remember(activity) {
        CalendarAuthManager(activity)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                try {
                    val authResult = authManager.getResultFromIntent(data)
                    val message = if (authResult.accessToken != null) {
                        "Google Calendar connected!"
                    } else {
                        "No access token returned"
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        activity,
                        "Authorisation failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Toast.makeText(
                activity,
                "Google Calendar permission not granted",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Button(
        onClick = {
            authManager.requestAuthorization(
                onSuccess = { result ->
                    if (result.hasResolution()) {
                        val pendingIntent = result.pendingIntent
                        if (pendingIntent != null) {
                            launcher.launch(
                                IntentSenderRequest.Builder(
                                    pendingIntent.intentSender
                                ).build()
                            )
                        }
                    } else {
                        val message = if (result.accessToken != null) {
                            "Google Calendar connected!"
                        } else {
                            "No access token returned"
                        }
                        Toast.makeText(
                            activity, message, Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onError = { error ->
                    Toast.makeText(
                        activity,
                        "Error: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    ) {
        Text("Connect Google Calendar")
    }
}
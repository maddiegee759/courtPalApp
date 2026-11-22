package com.example.courtpalapp.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.data.local.UserPreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentUserId: Long,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    var showLogoutConfirmation by remember {
        mutableStateOf(false)
    }

    val context = LocalContext.current

    val preferencesRepository = remember(context) {
        UserPreferencesRepository(context)
    }

    val scope = rememberCoroutineScope()

    val preferencesFlow = remember(preferencesRepository, currentUserId) {
        preferencesRepository.observePreferences(currentUserId)
    }

    val preferences by preferencesFlow.collectAsState(initial = null)

    val scoringMode = preferences?.scoringMode ?: "LIVE"
    val matchFormat = preferences?.matchFormat ?: "3"
    val rallyTracking = preferences?.rallyTracking ?: false
    val theme = preferences?.theme ?: "SYSTEM"

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack) {
                Text("← Back to Profile")
            }

            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            SettingsSectionTitle("Account")

            OutlinedButton(
                onClick = { showLogoutConfirmation = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log out")
            }

            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete account")
            }

            Text(
                text = "Account deletion will be available once account-specific data is implemented.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            SettingsSectionTitle("Match Preferences")

            Text(
                "Default scoring mode",
                fontWeight = FontWeight.SemiBold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = scoringMode == "LIVE",
                    onClick = {
                        scope.launch {
                            preferencesRepository.updateScoringMode(
                                currentUserId,
                                "LIVE"
                            )
                        }
                    },
                    enabled = preferences != null,
                    label = { Text("Live") }
                )

                FilterChip(
                    selected = scoringMode == "MANUAL",
                    onClick = {
                        scope.launch {
                            preferencesRepository.updateScoringMode(
                                currentUserId,
                                "MANUAL"
                            )
                        }
                    },
                    enabled = preferences != null,
                    label = { Text("Manual") }
                )
            }

            Text(
                "Default match format",
                fontWeight = FontWeight.SemiBold
            )

            Column {
                listOf(
                    "1" to "1 set",
                    "3" to "Best of 3 sets",
                    "5" to "Best of 5 sets"
                ).forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = matchFormat == value,
                            onClick = {
                                scope.launch {
                                    preferencesRepository.updateMatchFormat(
                                        currentUserId,
                                        value
                                    )
                                }
                            },
                            enabled = preferences != null
                        )

                        Text(label)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Rally tracking",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Enable by default for live matches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = rallyTracking,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferencesRepository.updateRallyTracking(
                                currentUserId,
                                enabled
                            )
                        }
                    },
                    enabled = preferences != null
                )
            }

            HorizontalDivider()

            SettingsSectionTitle("App")

            Text(
                "Theme",
                fontWeight = FontWeight.SemiBold
            )

            Column {
                listOf(
                    "SYSTEM" to "System default",
                    "LIGHT" to "Light",
                    "DARK" to "Dark"
                ).forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == value,
                            onClick = {
                                scope.launch {
                                    preferencesRepository.updateTheme(
                                        currentUserId,
                                        value
                                    )
                                }
                            },
                            enabled = preferences != null
                        )
                        Text(label)
                    }
                }
            }
        }
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showLogoutConfirmation = false
            },
            title = { Text("Log out?") },
            text = {
                Text("You can log back into this local account at any time.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmation = false
                        onLogout()
                    }
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmation = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

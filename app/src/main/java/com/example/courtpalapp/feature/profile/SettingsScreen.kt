package com.example.courtpalapp.feature.profile

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.data.local.AccountDeletionRepository
import com.example.courtpalapp.data.local.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
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

    val accountDeletionRepository = remember(context) {
        AccountDeletionRepository(context)
    }

    var showDeleteConfirmation by remember(currentUserId) {
        mutableStateOf(false)
    }

    var deletePassword by remember(currentUserId) {
        mutableStateOf("")
    }

    var deleteConfirmationText by remember(currentUserId) {
        mutableStateOf("")
    }

    var isDeletingAccount by remember(currentUserId) {
        mutableStateOf(false)
    }

    var deleteAccountError by remember(currentUserId) {
        mutableStateOf<String?>(null)
    }

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
                onClick = {
                    deletePassword = ""
                    deleteConfirmationText = ""
                    deleteAccountError = null
                    showDeleteConfirmation = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete account")
            }

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
    if (showDeleteConfirmation) {

        AlertDialog(
            onDismissRequest = {
                if (!isDeletingAccount) {
                    showDeleteConfirmation = false
                    deletePassword = ""
                    deleteConfirmationText = ""
                    deleteAccountError = null
                }
            },

            title = {
                Text("Permanently delete account?")
            },

            text = {
                Column(
                    modifier = Modifier.verticalScroll(
                        rememberScrollState()
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Text(
                        "This permanently deletes your account, " +
                                "training history, friends, calendar events, " +
                                "tracked matches and their photos."
                    )

                    Text(
                        "Matches you tracked will also disappear from " +
                                "your opponents' histories. Matches tracked " +
                                "by other people will remain, but your " +
                                "identity will be replaced with " +
                                "\"Deleted account\".",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = {
                            deletePassword = it
                            deleteAccountError = null
                        },
                        label = {
                            Text("Your password")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isDeletingAccount,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = deleteConfirmationText,
                        onValueChange = {
                            deleteConfirmationText = it
                        },
                        label = {
                            Text("Type DELETE to confirm")
                        },
                        singleLine = true,
                        enabled = !isDeletingAccount,
                        modifier = Modifier.fillMaxWidth()
                    )

                    deleteAccountError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        val passwordToVerify = deletePassword

                        isDeletingAccount = true
                        deleteAccountError = null

                        scope.launch {
                            try {
                                accountDeletionRepository.deleteAccount(
                                    userId = currentUserId,
                                    password = passwordToVerify
                                )

                                // The repository logs out after successful
                                // deletion. AppNavHost then shows LoginScreen.

                            } catch (cancelled: CancellationException) {
                                throw cancelled

                            } catch (error: Exception) {
                                deleteAccountError =
                                    "Could not delete the account. " +
                                            "Check your password and try again."

                            } finally {
                                deletePassword = ""
                                isDeletingAccount = false
                            }
                        }
                    },

                    enabled = !isDeletingAccount &&
                            deletePassword.isNotBlank() &&
                            deleteConfirmationText == "DELETE"
                ) {
                    Text(
                        if (isDeletingAccount) {
                            "Deleting..."
                        } else {
                            "Delete forever"
                        }
                    )
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        deletePassword = ""
                        deleteConfirmationText = ""
                        deleteAccountError = null
                    },
                    enabled = !isDeletingAccount
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

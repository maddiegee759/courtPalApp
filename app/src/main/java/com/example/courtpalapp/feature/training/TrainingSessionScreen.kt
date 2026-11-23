package com.example.courtpalapp.feature.training

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.common.formatDate
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.SessionManager
import com.example.courtpalapp.data.local.TrainingSessionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.example.courtpalapp.common.formatDate

private val trainingChoices = listOf(
    "DRILLS" to "Drills",
    "CARDIO_TENNIS" to "Cardio tennis",
    "SERVING" to "Serving practice",
    "FITNESS" to "Fitness / conditioning",
    "OTHER" to "Other"
)

private fun trainingLabel(type: String): String {
    return trainingChoices.firstOrNull { it.first == type }
        ?.second ?: type
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TrainingSessionScreen(
    userId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val database = remember(context) {
        AppDatabase.getDatabase(context)
    }

    val dao = remember(database) {
        database.trainingSessionDao()
    }

    val sessionManager = remember(context) {
        SessionManager(context)
    }

    val signedInUserId by sessionManager.currentUserId.collectAsState()

    val sessions by remember(dao, userId) {
        dao.observeSessionsForUser(userId)
    }.collectAsState(initial = emptyList())

    val totalMinutes by remember(dao, userId) {
        dao.observeTotalTrainingMinutes(userId)
    }.collectAsState(initial = 0)

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var activityType by rememberSaveable(userId) {
        mutableStateOf("DRILLS")
    }

    var duration by rememberSaveable(userId) {
        mutableStateOf("")
    }

    var selectedDate by rememberSaveable(userId) {
        mutableStateOf(LocalDate.now().toString())
    }

    var notes by rememberSaveable(userId) {
        mutableStateOf("")
    }

    var saving by remember(userId) {
        mutableStateOf(false)
    }

    var errorMessage by remember(userId) {
        mutableStateOf<String?>(null)
    }

    var sessionToDelete by remember(userId) {
        mutableStateOf<TrainingSessionEntity?>(null)
    }

    val formattedDate = remember(selectedDate) {
        runCatching {
            formatDate(selectedDate)
        }.getOrDefault(selectedDate)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar)
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {
                TextButton(onClick = onBack) {
                    Text("← Back to Home")
                }

                Text(
                    text = "Training Log",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Record your tennis training sessions.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Total training: " +
                            "${totalMinutes / 60}h " +
                            "${totalMinutes % 60}m",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "${sessions.size} sessions recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Text(
                            text = "Record Training",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Activity type",
                            style = MaterialTheme.typography.titleMedium
                        )

                        trainingChoices.forEach { (code, label) ->

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = activityType == code,
                                        role = Role.RadioButton,
                                        onClick = {
                                            activityType = code
                                            errorMessage = null
                                        }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                RadioButton(
                                    selected = activityType == code,
                                    onClick = null
                                )

                                Spacer(Modifier.width(8.dp))

                                Text(label)
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "Date",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedButton(
                            onClick = {
                                val date = runCatching {
                                    LocalDate.parse(selectedDate)
                                }.getOrDefault(LocalDate.now())

                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        selectedDate = LocalDate.of(
                                            year,
                                            month + 1,
                                            day
                                        ).toString()

                                        errorMessage = null
                                    },
                                    date.year,
                                    date.monthValue - 1,
                                    date.dayOfMonth
                                ).apply {
                                    datePicker.maxDate =
                                        System.currentTimeMillis()
                                }.show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(formattedDate)
                        }

                        OutlinedTextField(
                            value = duration,
                            onValueChange = {
                                duration = it
                                    .filter(Char::isDigit)
                                    .take(4)

                                errorMessage = null
                            },
                            label = {
                                Text("Duration (minutes)")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = {
                                notes = it.take(500)
                            },
                            label = {
                                Text("Notes (optional)")
                            },
                            placeholder = {
                                Text(
                                    "e.g. Practised backhand returns"
                                )
                            },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )

                        errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = save@{

                                if (signedInUserId != userId) {
                                    errorMessage =
                                        "Please log in to record training."
                                    return@save
                                }

                                val minutes = duration.toIntOrNull()

                                if (
                                    minutes == null ||
                                    minutes !in 1..1440
                                ) {
                                    errorMessage =
                                        "Enter a duration between 1 and 1440 minutes."
                                    return@save
                                }

                                val date = runCatching {
                                    LocalDate.parse(selectedDate)
                                }.getOrNull()

                                if (
                                    date == null ||
                                    date.isAfter(LocalDate.now())
                                ) {
                                    errorMessage =
                                        "Choose a valid date that is not in the future."
                                    return@save
                                }

                                val entry = TrainingSessionEntity(
                                    ownerUserId = userId,
                                    activityType = activityType,
                                    durationMinutes = minutes,
                                    notes = notes.trim(),
                                    occurredAt = date.atStartOfDay(
                                        ZoneId.systemDefault()
                                    ).toInstant().toEpochMilli()
                                )

                                saving = true
                                errorMessage = null

                                scope.launch {
                                    try {

                                        if (
                                            sessionManager.currentUserId.first()
                                            != userId
                                        ) {
                                            return@launch
                                        }

                                        dao.insertSession(entry)

                                        if (
                                            sessionManager.currentUserId.first()
                                            == userId
                                        ) {
                                            duration = ""
                                            notes = ""
                                            saving = false

                                            snackbar.showSnackbar(
                                                "Training session recorded"
                                            )
                                        }

                                    } catch (
                                        cancelled: CancellationException
                                    ) {
                                        throw cancelled
                                    } catch (e: Exception) {
                                        if (
                                            sessionManager.currentUserId.first()
                                            == userId
                                        ) {
                                            errorMessage =
                                                "Could not save training."
                                        }
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving &&
                                    signedInUserId == userId,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (saving) {
                                    "Saving..."
                                } else {
                                    "Save Training"
                                }
                            )
                        }
                    }
                }
            }

            item {
                HorizontalDivider()

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Training History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (sessions.isEmpty()) {
                item {
                    Text(
                        text = "No training sessions recorded yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(
                items = sessions,
                key = { it.id }
            ) { session ->

                TrainingHistoryCard(
                    session = session,
                    onDelete = {
                        sessionToDelete = session
                    }
                )
            }
        }
    }

    sessionToDelete?.let { session ->

        AlertDialog(
            onDismissRequest = {
                sessionToDelete = null
            },
            title = {
                Text("Delete training session?")
            },
            text = {
                Text(
                    "This permanently removes the session " +
                            "from your Training Log."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToDelete = null

                        scope.launch {
                            try {
                                if (
                                    sessionManager.currentUserId.first()
                                    != userId
                                ) {
                                    return@launch
                                }

                                val deleted = dao.deleteSessionForUser(
                                    session.id,
                                    userId
                                )

                                if (deleted != 1) {
                                    snackbar.showSnackbar(
                                        "Training session not found"
                                    )
                                }

                            } catch (
                                cancelled: CancellationException
                            ) {
                                throw cancelled
                            } catch (e: Exception) {
                                snackbar.showSnackbar(
                                    "Could not delete training session"
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        sessionToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun TrainingHistoryCard(
    session: TrainingSessionEntity,
    onDelete: () -> Unit
) {
    val date = remember(session.occurredAt) {
        val isoDate = Instant.ofEpochMilli(session.occurredAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()

        formatDate(isoDate)
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Text(
                text = trainingLabel(session.activityType),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text("$date · ${session.durationMinutes} minutes")

            if (session.notes.isNotBlank()) {
                Text(session.notes)
            }

            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = "Delete entry",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
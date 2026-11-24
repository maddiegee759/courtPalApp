package com.example.courtpalapp.feature.matches

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.SessionManager
import com.example.courtpalapp.domain.match.MatchSettings
import com.example.courtpalapp.feature.matches.components.ScoreBoard

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun LiveMatchScreen(
    settings: MatchSettings,
    onMatchFinished: (Long) -> Unit = {},
    onMatchQuit: () -> Unit = {},
    onMatchDeleted: () -> Unit = {},
    matchViewModel: LiveMatchViewModel = viewModel()
) {
    matchViewModel.initialise(settings)
    val context = LocalContext.current

    val database = remember(context) { AppDatabase.getDatabase(context) }
    val sessionManager = remember(context) { SessionManager(context) }
    val currentUserId by sessionManager.currentUserId.collectAsState()

    val score = matchViewModel.score ?: return
    val history = matchViewModel.history
    val rallyShots = matchViewModel.rallyShots
    var showQuitDialog by remember { mutableStateOf(false) }
    val latestOnMatchFinished by rememberUpdatedState(onMatchFinished)

    LaunchedEffect(score.isFinished, currentUserId) {
        if (score.isFinished) {
            // We no longer need the finish confirmation dialog
            matchViewModel.dismissFinishDialog()

            currentUserId?.let { userId ->
                matchViewModel.saveCompletedMatch(
                    database = database,
                    ownerUserId = userId,
                    onSaved = { savedMatchId ->
                        latestOnMatchFinished(savedMatchId)
                    }
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        Text(
            text = "Live Match",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        ScoreBoard(
            score = score,
            settings = settings
        )

        if (!score.isFinished) {

            Text(
                text = "Award point",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Button(
                    onClick = { matchViewModel.awardPoint(1) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                ) {
                    Text(
                        text = "+ ${settings.player1Name}"
                    )
                }

                Button(
                    onClick = { matchViewModel.awardPoint(2) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                ) {
                    Text(
                        text = "+ ${settings.player2Name}"
                    )
                }
            }
        }

        if (settings.trackRallies && !score.isFinished) {
            HorizontalDivider()

            Text(
                text = "Rally shots: $rallyShots",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                OutlinedButton(
                    onClick = { matchViewModel.removeRallyShot() }
                ) {
                    Text("−")
                }

                Button(
                    onClick = { matchViewModel.addRallyShot() }
                ) {
                    Text("+ Shot")
                }

                TextButton(
                    onClick = { matchViewModel.resetRallyShots() }
                ) {
                    Text("Reset")
                }
            }
        }

        OutlinedButton(
            onClick = {
                matchViewModel.undoPoint()
            },
            enabled = history.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Undo last point")
        }

        if (score.isFinished) {

            Text(
                text = "Match complete!",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "Winner: " +
                        if (score.winner == 1) {
                            settings.player1Name
                        } else {
                            settings.player2Name
                        }
            )

            if (matchViewModel.isSaving) {

                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Saving your match...")

            } else if (matchViewModel.saveError != null) {

                Text(
                    text = matchViewModel.saveError!!,
                    color = MaterialTheme.colorScheme.error
                )

                Button(
                    onClick = {
                        currentUserId?.let { userId ->
                            matchViewModel.saveCompletedMatch(
                                database = database,
                                ownerUserId = userId,
                                onSaved = { savedMatchId ->
                                    latestOnMatchFinished(savedMatchId)
                                }
                            )
                        }
                    },
                    enabled = currentUserId != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry Save")
                }

            } else {

                Text("Preparing Match History...")

            }
        }

        HorizontalDivider()

        Text(
            text = "Match statistics",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "Points won: " +
                    "${settings.player1Name} " +
                    "${score.player1TotalPoints} · " +
                    "${settings.player2Name} " +
                    "${score.player2TotalPoints}"
        )

        if (settings.trackRallies && score.rallies.isNotEmpty()) {
            Text(
                "Longest rally: " +
                        "${score.rallies.maxOrNull() ?: 0} shots"
            )

            Text(
                "Average rally: " +
                        "%.1f".format(score.rallies.average()) +
                        " shots"
            )
        }

        if (!score.isFinished) {
            HorizontalDivider()

            OutlinedButton(
                onClick = { showQuitDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Quit Match")
            }
        }
    }


    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = { Text("Quit Match?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This match is unfinished. Would you like to " +
                                "save the partial result to Match History " +
                                "or permanently delete it?"
                    )

                    matchViewModel.saveError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        currentUserId?.let { userId ->
                            matchViewModel.saveUnfinishedMatch(
                                database = database,
                                ownerUserId = userId,
                                onSaved = {
                                    showQuitDialog = false
                                    onMatchQuit()
                                }
                            )
                        }
                    },
                    enabled = !matchViewModel.isSaving && currentUserId != null
                ) {
                    Text(
                        if (matchViewModel.isSaving) "Saving..."
                        else "Save to History"
                    )
                }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = {
                            currentUserId?.let { userId ->
                                matchViewModel.deleteUnfinishedMatch(
                                    ownerUserId = userId,
                                    onDeleted = {
                                        showQuitDialog = false
                                        onMatchDeleted()
                                    }
                                )
                            }
                        },
                        enabled = !matchViewModel.isSaving &&
                                currentUserId != null
                    ) {
                        Text(
                            "Delete Match",
                            color = MaterialTheme.colorScheme.error
                        )
                    }


                    TextButton(
                        onClick = { showQuitDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

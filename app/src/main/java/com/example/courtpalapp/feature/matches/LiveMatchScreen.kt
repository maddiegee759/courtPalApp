package com.example.courtpalapp.feature.matches

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.domain.match.*
import com.example.courtpalapp.feature.matches.components.ScoreBoard
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.example.courtpalapp.data.local.AppDatabase

@Composable
fun LiveMatchScreen(
    settings: MatchSettings,
    onMatchFinished: (TennisScore) -> Unit = {},
    matchViewModel: LiveMatchViewModel = viewModel()
) {
    matchViewModel.initialise(settings)
    val context = LocalContext.current

    val matchDao = remember(context) {
        AppDatabase.getDatabase(context).matchDao()
    }

    val score = matchViewModel.score ?: return
    val history = matchViewModel.history
    val rallyShots = matchViewModel.rallyShots
    val showFinishDialog = matchViewModel.showFinishDialog

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
                        if (score.winner == 1)
                            settings.player1Name
                        else
                            settings.player2Name
            )

            Button(
                onClick = {
                    matchViewModel.saveCompletedMatch(matchDao) {
                        onMatchFinished(score)
                    }
                },
                enabled = !matchViewModel.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (matchViewModel.isSaving) {
                        "Saving..."
                    } else {
                        "Save Match"
                    }
                )
            }

            matchViewModel.saveError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error
                )
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
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = {
                matchViewModel.dismissFinishDialog()
            },
            title = {
                Text("Match complete!")
            },
            text = {
                Text(
                    "Winner: " +
                            if (score.winner == 1)
                                settings.player1Name
                            else
                                settings.player2Name
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        matchViewModel.dismissFinishDialog()
                        matchViewModel.saveCompletedMatch(matchDao) {
                            onMatchFinished(score)
                        }
                    }
                ) {
                    Text("Match History")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        matchViewModel.dismissFinishDialog()
                    }
                ) {
                    Text("Stay here")
                }
            }
        )
    }
}

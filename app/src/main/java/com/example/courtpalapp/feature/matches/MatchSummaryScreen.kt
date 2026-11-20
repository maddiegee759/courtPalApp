package com.example.courtpalapp.feature.matches

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.MatchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@Composable
fun MatchSummaryScreen(
    matchId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val matchDao = remember(context) {
        AppDatabase.getDatabase(context).matchDao()
    }

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    var isDeleting by remember {
        mutableStateOf(false)
    }

    var deleteError by remember {
        mutableStateOf<String?>(null)
    }

    val coroutineScope = rememberCoroutineScope()

    var match by remember(matchId) {
        mutableStateOf<MatchEntity?>(null)
    }

    var isLoading by remember(matchId) {
        mutableStateOf(true)
    }

    LaunchedEffect(matchId) {
        isLoading = true
        match = withContext(Dispatchers.IO) {
            matchDao.getMatchById(matchId)
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        TextButton(onClick = onBack) {
            Text("← Back to matches")
        }

        Text(
            text = "Match Summary",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else if (match == null) {
            Text("Match not found.")
        } else {
            val savedMatch = match!!

            val date = remember(savedMatch.playedAt) {
                SimpleDateFormat(
                    "dd MMM yyyy",
                    Locale.getDefault()
                ).format(Date(savedMatch.playedAt))
            }

            val winnerName = when (savedMatch.winner) {
                1 -> savedMatch.player1Name
                2 -> savedMatch.player2Name
                else -> "Unknown"
            }

            Text(
                text = date,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "${savedMatch.player1Name} vs ${savedMatch.player2Name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Winner: $winnerName",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider()

            Text(
                text = "Set Scores",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val setScores = remember(savedMatch.completedSetsJson) {
                parseSetScores(savedMatch.completedSetsJson)
            }

            if (setScores.isEmpty()) {
                Text("Individual set scores unavailable.")
            } else {
                setScores.forEachIndexed { index, set ->
                    Text(
                        text = "Set ${index + 1}: $set",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "Match Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Points won by ${savedMatch.player1Name}: " +
                        savedMatch.player1TotalPoints
            )

            Text(
                "Points won by ${savedMatch.player2Name}: " +
                        savedMatch.player2TotalPoints
            )

            if (savedMatch.trackRallies) {
                Text(
                    "Longest rally: ${savedMatch.longestRally} shots"
                )

                Text(
                    "Average rally: " +
                            "%.1f".format(savedMatch.averageRally) +
                            " shots"
                )
            }

            HorizontalDivider()

            Text(
                text = "Match Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text("Mode: ${savedMatch.matchMode}")
            Text("Best of ${savedMatch.bestOfSets} sets")
            Text("${savedMatch.gamesPerSet} games per set")
            Text(
                "Scoring: " +
                        savedMatch.scoringMode.replace("_", " ")
            )
            Text(
                "Tie-breaks: " +
                        if (savedMatch.tieBreakEnabled) "Enabled" else "Disabled"
            )

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            deleteError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedButton(
                onClick = {
                    showDeleteDialog = true
                },
                enabled = !isDeleting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete Match")
            }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDeleteDialog = false
                }
            },
            title = {
                Text("Delete Match?")
            },
            text = {
                Text(
                    "Are you sure you want to delete this match? " +
                            "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        isDeleting = true
                        deleteError = null

                        coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    matchDao.deleteMatchById(matchId)
                                }

                                showDeleteDialog = false
                                onBack()

                            } catch (e: Exception) {
                                deleteError =
                                    e.message ?: "Unable to delete match."
                            } finally {
                                isDeleting = false
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (isDeleting)
                            "Deleting..."
                        else
                            "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun parseSetScores(json: String): List<String> {
    return try {
        val sets = JSONArray(json)

        (0 until sets.length()).map { index ->
            val set = sets.getJSONObject(index)

            val p1 = set.getInt("player1Games")
            val p2 = set.getInt("player2Games")

            val tb1 = if (
                set.has("player1TieBreakPoints") &&
                !set.isNull("player1TieBreakPoints")
            ) {
                set.getInt("player1TieBreakPoints")
            } else null

            val tb2 = if (
                set.has("player2TieBreakPoints") &&
                !set.isNull("player2TieBreakPoints")
            ) {
                set.getInt("player2TieBreakPoints")
            } else null

            if (tb1 != null && tb2 != null) {
                "$p1–$p2 (Tie-break $tb1–$tb2)"
            } else {
                "$p1–$p2"
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

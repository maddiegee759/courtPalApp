package com.example.courtpalapp.feature.matches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.MatchEntity
import com.example.courtpalapp.data.local.SessionManager
import com.example.courtpalapp.feature.matches.components.displayMatchForViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MatchSummaryScreen(
    matchId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val sessionManager = remember(context) {
        SessionManager(context)
    }

    val currentUserId by sessionManager.currentUserId.collectAsState()

    val userDao = remember(context) {
        AppDatabase.getDatabase(context).userDao()
    }

    val users by remember(userDao) {
        userDao.getAllUsers()
    }.collectAsState(initial = emptyList())

    val usernamesById = remember(users) {
        users.associate { it.id to it.username }
    }

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

    var match by remember(matchId, currentUserId) {
        mutableStateOf<MatchEntity?>(null)
    }

    var isLoading by remember(matchId, currentUserId) {
        mutableStateOf(true)
    }

    LaunchedEffect(matchId, currentUserId) {
        isLoading = true

        match = currentUserId?.let { userId ->
            withContext(Dispatchers.IO) {
                matchDao.getMatchByIdVisibleToUser(
                    matchId = matchId,
                    userId = userId
                )
            }
        }

        isLoading = false
    }

    val viewerId = currentUserId

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
        } else if (match == null || viewerId == null) {
            Text("Match not found.")
        } else {
            val savedMatch = match!!

            val canEdit = savedMatch.ownerUserId != null &&
                    savedMatch.ownerUserId == currentUserId

            val display = remember(savedMatch, viewerId, usernamesById) {
                displayMatchForViewer(
                    match = savedMatch,
                    viewerUserId = viewerId,
                    usernamesById = usernamesById
                )
            }

            Text(
                text = display.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (savedMatch.isCompleted) {
                    "Winner: ${display.winner}"
                } else {
                    "Match unfinished"
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider()

            Text(
                text = "Set Scores",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val setScores = remember(display.sets) {
                if (
                    display.sets == "Not recorded" ||
                    display.sets == "Unavailable"
                ) {
                    emptyList()
                } else {
                    display.sets.split("  |  ")
                }
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

            if (savedMatch.matchMode != "MANUAL") {
                Text("Points won: ${display.pointsWon}")
            }

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

            if (canEdit) {
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
            } else {
                Text(
                    text = "This match was recorded by your friend. " +
                            "Only the tracker can delete it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (
        showDeleteDialog &&
        currentUserId != null &&
        match?.ownerUserId == currentUserId
    ) {
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
                                val userId = requireNotNull(currentUserId) {
                                    "Please log in to delete this match"
                                }

                                val deleted = withContext(Dispatchers.IO) {
                                    matchDao.deleteMatchByIdForUser(
                                        matchId = matchId,
                                        userId = userId
                                    )
                                }

                                check(deleted == 1) {
                                    "Match not found or you do not have permission to delete it"
                                }

                                // Remove the locally stored match photo as well.
                                withContext(Dispatchers.IO) {
                                    match?.photoUri?.let { photoPath ->
                                        deleteOwnedPhoto(context.filesDir, photoPath)
                                    }
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
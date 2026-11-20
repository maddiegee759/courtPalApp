
package com.example.courtpalapp.feature.matches

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.MatchEntity
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MatchHistoryScreen(
    onMatchClick: (Long) -> Unit = {}
) {

    val context = LocalContext.current

    val matchDao = remember(context) {
        AppDatabase.getDatabase(context).matchDao()
    }

    val matches by remember(matchDao) {
        matchDao.getAllMatches()
    }.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Match History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (matches.isEmpty()) {
            Text(
                text = "No completed matches yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = matches,
                    key = { it.id }
                ) { match ->
                    MatchHistoryCard(
                        match = match,
                        onClick = { onMatchClick(match.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchHistoryCard(
    match: MatchEntity,
    onClick: () -> Unit
) {

    val date = remember(match.playedAt) {
        SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        ).format(Date(match.playedAt))
    }

    val winnerName = when (match.winner) {
        1 -> match.player1Name
        2 -> match.player2Name
        else -> "Unknown"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Text(
                text = date,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "${match.player1Name} vs ${match.player2Name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Winner: $winnerName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider()

            Text(
                text = "Sets: ${formatCompletedSets(match.completedSetsJson)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Points won: " +
                        "${match.player1TotalPoints} – " +
                        "${match.player2TotalPoints}",
                style = MaterialTheme.typography.bodySmall
            )

            if (match.trackRallies) {
                Text(
                    text = "Longest rally: ${match.longestRally} shots",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Average rally: " +
                            "%.1f".format(match.averageRally) +
                            " shots",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatCompletedSets(json: String): String {
    return try {
        val sets = JSONArray(json)

        if (sets.length() == 0) {
            return "Not recorded"
        }

        (0 until sets.length()).joinToString("  |  ") { index ->
            val set = sets.getJSONObject(index)

            val p1 = set.getInt("player1Games")
            val p2 = set.getInt("player2Games")

            val tieBreak1 = if (
                set.has("player1TieBreakPoints") &&
                !set.isNull("player1TieBreakPoints")
            ) {
                set.getInt("player1TieBreakPoints")
            } else null

            val tieBreak2 = if (
                set.has("player2TieBreakPoints") &&
                !set.isNull("player2TieBreakPoints")
            ) {
                set.getInt("player2TieBreakPoints")
            } else null

            if (tieBreak1 != null && tieBreak2 != null) {
                "$p1–$p2 (TB $tieBreak1–$tieBreak2)"
            } else {
                "$p1–$p2"
            }
        }
    } catch (e: Exception) {
        "Unavailable"
    }
}


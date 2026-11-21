package com.example.courtpalapp.feature.matches

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.MatchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "Match History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (matches.isEmpty()) {
            Text(
                text = "No matches saved yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items = matches, key = { it.id }) { match ->
                    MatchHistoryCard(match = match, onClick = { onMatchClick(match.id) })
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
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(match.playedAt))
    }
    val winnerName = when {
        !match.isCompleted -> null
        match.winner == 1 -> match.player1Name
        match.winner == 2 -> match.player2Name
        else -> "Unknown"
    }

    // The details screen stores an absolute path to a private photo file.
    // Decode a downsampled image so scrolling the match list stays responsive.
    val photoBitmap by produceState<Bitmap?>(initialValue = null, key1 = match.photoUri) {
        value = null
        value = withContext(Dispatchers.IO) {
            match.photoUri?.let { path ->
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    var sampleSize = 1
                    while (bounds.outWidth / sampleSize > 1000 ||
                        bounds.outHeight / sampleSize > 1000) {
                        sampleSize *= 2
                    }
                    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    })
                } catch (_: Exception) {
                    null
                }
            }
        }
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
            Text(
                text = "${match.player1Name} vs ${match.player2Name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (match.isCompleted) {
                    "Winner: $winnerName"
                } else {
                    "Match unfinished"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (match.isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            // Show the match photo directly in the history card when available.
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap!!.asImageBitmap(),
                    contentDescription = "Photo from ${match.player1Name} vs ${match.player2Name}",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop
                )
            }

            HorizontalDivider()

            Text(
                text = "Sets: ${formatMatchSets(match)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Points won: ${match.player1TotalPoints} – ${match.player2TotalPoints}",
                style = MaterialTheme.typography.bodySmall
            )
            if (match.trackRallies) {
                Text(
                    text = "Longest rally: ${match.longestRally} shots",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Average rally: ${"%.1f".format(match.averageRally)} shots",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatCompletedSets(json: String): String {
    return try {
        val sets = JSONArray(json)
        if (sets.length() == 0) return "Not recorded"
        (0 until sets.length()).joinToString("  |  ") { index ->
            val set = sets.getJSONObject(index)
            val p1 = set.getInt("player1Games")
            val p2 = set.getInt("player2Games")
            val tieBreak1 = if (set.has("player1TieBreakPoints") &&
                !set.isNull("player1TieBreakPoints")) {
                set.getInt("player1TieBreakPoints")
            } else null
            val tieBreak2 = if (set.has("player2TieBreakPoints") &&
                !set.isNull("player2TieBreakPoints")) {
                set.getInt("player2TieBreakPoints")
            } else null
            if (tieBreak1 != null && tieBreak2 != null) {
                "$p1–$p2 (TB $tieBreak1–$tieBreak2)"
            } else "$p1–$p2"
        }
    } catch (_: Exception) {
        "Unavailable"
    }
}

private fun formatMatchSets(match: MatchEntity): String {
    val completed = formatCompletedSets(match.completedSetsJson)

    if (match.isCompleted) {
        return completed
    }

    val currentSet = "${match.player1Games}–${match.player2Games}"

    return if (completed == "Not recorded") {
        currentSet
    } else {
        "$completed  |  $currentSet"
    }
}


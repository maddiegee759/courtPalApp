package com.example.courtpalapp.feature.matches

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.MatchEntity
import com.example.courtpalapp.data.local.SessionManager
import com.example.courtpalapp.feature.matches.components.displayMatchForViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val sessionManager = remember(context) { SessionManager(context) }
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

    // A fresh Flow and keyed composition prevent one account's matches from
    // briefly appearing when someone switches accounts on the same device.
    val matchesFlow = remember(matchDao, currentUserId) {
        currentUserId?.let {
            matchDao.getMatchesVisibleToUser(it)
        }
    }
    val matches = if (matchesFlow != null) {
        key(currentUserId) {
            matchesFlow.collectAsState(initial = emptyList()).value
        }
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "Match History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        when {
            currentUserId == null -> Text(
                text = "Log in to view your match history.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            matches.isEmpty() -> Text(
                text = "No matches saved yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items = matches, key = { it.id }) { match ->
                    MatchHistoryCard(
                        match = match,
                        viewerUserId = currentUserId!!,
                        usernamesById = usernamesById,
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
    viewerUserId: Long,
    usernamesById: Map<Long, String>,
    onClick: () -> Unit
) {
    val date = remember(match.playedAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(match.playedAt))
    }
    val display = remember(match, viewerUserId, usernamesById) {
        displayMatchForViewer(
            match = match,
            viewerUserId = viewerUserId,
            usernamesById = usernamesById
        )
    }

    // Downsample photos to avoid decoding full-resolution files while scrolling.
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
                text = display.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (match.isCompleted) {
                    "Winner: ${display.winner}"
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

            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap!!.asImageBitmap(),
                    contentDescription = "Photo from ${display.title}",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop
                )
            }

            HorizontalDivider()

            Text(
                text = "Sets: ${display.sets}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (match.matchMode != "MANUAL") {
                Text(
                    text = "Points won: ${display.pointsWon}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
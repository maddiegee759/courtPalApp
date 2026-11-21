package com.example.courtpalapp.feature.matches

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.MatchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun MatchDetailScreen(
    matchId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember(context) { AppDatabase.getDatabase(context).matchDao() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var match by remember(matchId) { mutableStateOf<MatchEntity?>(null) }
    var loading by remember(matchId) { mutableStateOf(true) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(matchId) {
        match = withContext(Dispatchers.IO) { dao.getMatchById(matchId) }
        loading = false
    }
    LaunchedEffect(match?.photoUri) {
        val path = match?.photoUri
        photoBitmap = withContext(Dispatchers.IO) {
            path?.let { BitmapFactory.decodeFile(it) }
        }
    }

    fun storePhoto(source: suspend () -> String?) {
        scope.launch {
            try {
                val path = withContext(Dispatchers.IO) { source() } ?: return@launch
                val previous = match?.photoUri
                dao.updateMatchPhoto(matchId, path)
                match = dao.getMatchById(matchId)
                if (previous != null && previous != path) {
                    withContext(Dispatchers.IO) { deleteOwnedPhoto(context.filesDir, previous) }
                }
                snackbar.showSnackbar("Match photo saved")
            } catch (e: Exception) {
                snackbar.showSnackbar("Could not save photo")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) storePhoto { copyPhotoToPrivateStorage(context, uri) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) storePhoto { saveBitmapToPrivateStorage(context, bitmap) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Back to Match History") }
            Text("Match Details", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)

            val current = match
            if (loading) {
                CircularProgressIndicator()
            } else if (current == null) {
                Text("Match not found")
            } else {
                val date = remember(current.playedAt) {
                    SimpleDateFormat("EEE d MMM yyyy", Locale.UK)
                        .format(Date(current.playedAt))
                }
                Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${current.player1Name} vs ${current.player2Name}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val winner = when (current.winner) {
                    1 -> current.player1Name
                    2 -> current.player2Name
                    else -> "Unknown"
                }
                Text("Winner: $winner", color = MaterialTheme.colorScheme.primary)
                HorizontalDivider()
                Text("Sets: ${formatMatchSets(current.completedSetsJson)}")
                Text("Points won: ${current.player1TotalPoints} – ${current.player2TotalPoints}")
                if (current.trackRallies) {
                    Text("Longest rally: ${current.longestRally} shots")
                    Text("Average rally: ${"%.1f".format(Locale.UK, current.averageRally)} shots")
                }
                HorizontalDivider()
                Text("Match Photo", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                if (photoBitmap != null) {
                    Image(bitmap = photoBitmap!!.asImageBitmap(),
                        contentDescription = "Match photo",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        contentScale = ContentScale.Fit)
                } else {
                    Text("No photo added", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f)) { Text("Take photo") }
                    OutlinedButton(onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)) { Text("Choose photo") }
                }
                if (current.photoUri != null) {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                dao.updateMatchPhoto(matchId, null)
                                match = dao.getMatchById(matchId)
                                withContext(Dispatchers.IO) {
                                    deleteOwnedPhoto(context.filesDir, current.photoUri)
                                }
                            } catch (e: Exception) {
                                snackbar.showSnackbar("Could not remove photo")
                            }
                        }
                    }) { Text("Remove photo") }
                }
                Spacer(Modifier.height(28.dp))
                OutlinedButton(onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Delete match", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete match?") },
            text = { Text("This permanently deletes the match and its photo.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    scope.launch {
                        try {
                            val path = match?.photoUri
                            dao.deleteMatchById(matchId)
                            withContext(Dispatchers.IO) {
                                if (path != null) deleteOwnedPhoto(context.filesDir, path)
                            }
                            onBack()
                        } catch (e: Exception) {
                            snackbar.showSnackbar("Could not delete match")
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatMatchSets(json: String): String = try {
    val sets = JSONArray(json)
    if (sets.length() == 0) "Not recorded" else (0 until sets.length()).joinToString("  |  ") { i ->
        val set = sets.getJSONObject(i)
        val score = "${set.getInt("player1Games")}–${set.getInt("player2Games")}"
        if (set.has("player1TieBreakPoints") && !set.isNull("player1TieBreakPoints") &&
            set.has("player2TieBreakPoints") && !set.isNull("player2TieBreakPoints")) {
            "$score (TB ${set.getInt("player1TieBreakPoints")}–${set.getInt("player2TieBreakPoints")})"
        } else score
    }
} catch (e: Exception) { "Unavailable" }

private fun photoFolder(context: android.content.Context): File =
    File(context.filesDir, "match_photos").apply { mkdirs() }

internal fun copyPhotoToPrivateStorage(context: android.content.Context, uri: Uri): String {
    val file = File(photoFolder(context), "${UUID.randomUUID()}.jpg")
    try {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: error("Unable to read image")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        return file.absolutePath
    } catch (e: Exception) {
        file.delete()
        throw e
    }
}

internal fun saveBitmapToPrivateStorage(context: android.content.Context, bitmap: Bitmap): String {
    val file = File(photoFolder(context), "${UUID.randomUUID()}.jpg")
    try {
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        return file.absolutePath
    } catch (e: Exception) {
        file.delete()
        throw e
    }
}

internal fun deleteOwnedPhoto(filesDir: File, path: String) {
    val folder = File(filesDir, "match_photos").canonicalFile
    val file = File(path).canonicalFile
    if (file.parentFile == folder) file.delete()
}

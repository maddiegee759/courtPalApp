package com.example.courtpalapp.feature.matches

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.MatchEntity
import com.example.courtpalapp.data.local.SessionManager
import com.example.courtpalapp.feature.matches.components.displayMatchForViewer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val sessionManager = remember(context) { SessionManager(context) }
    val currentUserId by sessionManager.currentUserId.collectAsState()
    val userDao = remember(context) { AppDatabase.getDatabase(context).userDao() }
    val users by remember(userDao) { userDao.getAllUsers() }.collectAsState(initial = emptyList())
    val usernamesById = remember(users) { users.associate { it.id to it.username } }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var match by remember(matchId, currentUserId) { mutableStateOf<MatchEntity?>(null) }
    var loading by remember(matchId, currentUserId) { mutableStateOf(currentUserId != null) }
    var showDeleteConfirmation by remember(matchId, currentUserId) { mutableStateOf(false) }
    var photoBitmap by remember(matchId, currentUserId) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(matchId, currentUserId) {
        match = currentUserId?.let { userId ->
            withContext(Dispatchers.IO) {
                dao.getMatchByIdVisibleToUser(matchId, userId)
            }
        }
        loading = false
    }
    LaunchedEffect(matchId, currentUserId, match?.photoUri) {
        val path = match?.photoUri
        photoBitmap = withContext(Dispatchers.IO) {
            path?.let { BitmapFactory.decodeFile(it) }
        }
    }

    fun storePhoto(source: suspend () -> String?) {
        val userId = currentUserId ?: return
        if (match?.ownerUserId != userId) return

        scope.launch {
            var newPhotoPath: String? = null
            var photoAttached = false
            try {
                val path = withContext(Dispatchers.IO) { source() } ?: return@launch
                newPhotoPath = path

                if (sessionManager.currentUserId.first() != userId) {
                    snackbar.showSnackbar("Please log in to edit this match")
                    return@launch
                }

                val existing = dao.getMatchByIdForUser(matchId, userId)
                if (existing == null || dao.updateMatchPhotoForUser(matchId, userId, path) != 1) {
                    snackbar.showSnackbar("Match not found")
                    return@launch
                }
                photoAttached = true
                match = dao.getMatchByIdForUser(matchId, userId)
                val previous = existing.photoUri
                if (previous != null && previous != path) {
                    withContext(Dispatchers.IO) { deleteOwnedPhoto(context.filesDir, previous) }
                }
                snackbar.showSnackbar("Match photo saved")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                snackbar.showSnackbar("Could not save photo")
            } finally {
                val orphanPath = newPhotoPath
                if (!photoAttached && orphanPath != null) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        deleteOwnedPhoto(context.filesDir, orphanPath)
                    }
                }
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
            val viewerId = currentUserId

            if (loading) {
                CircularProgressIndicator()
            } else if (current == null || viewerId == null) {
                Text("Match not found")
            } else {

                val display = remember(current, viewerId, usernamesById) {
                    displayMatchForViewer(
                        match = current,
                        viewerUserId = viewerId,
                        usernamesById = usernamesById
                    )
                }
                val canEdit = current.ownerUserId != null &&
                        current.ownerUserId == currentUserId

                val date = remember(current.playedAt) {
                    SimpleDateFormat("EEE d MMM yyyy", Locale.UK)
                        .format(Date(current.playedAt))
                }
                Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = display.title,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = if (current.isCompleted) {
                        "Winner: ${display.winner}"
                    } else {
                        "Match unfinished"
                    },
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
                Text("Sets: ${display.sets}")
                if (current.matchMode != "MANUAL") {
                    Text("Points won: ${display.pointsWon}")
                }
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

                if (canEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { cameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Take photo") }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Choose photo") }
                    }
                    if (current.photoUri != null) {
                        TextButton(onClick = {
                            currentUserId?.let { userId ->
                                scope.launch {
                                    try {
                                        if (sessionManager.currentUserId.first() != userId) return@launch
                                        val latest = dao.getMatchByIdForUser(matchId, userId)
                                        val previousPhoto = latest?.photoUri ?: return@launch
                                        if (dao.updateMatchPhotoForUser(
                                                matchId,
                                                userId,
                                                null
                                            ) == 1
                                        ) {
                                            match = dao.getMatchByIdForUser(matchId, userId)
                                            withContext(Dispatchers.IO) {
                                                deleteOwnedPhoto(context.filesDir, previousPhoto)
                                            }
                                        } else {
                                            snackbar.showSnackbar("Match not found")
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar("Could not remove photo")
                                    }
                                }
                            }
                        }) { Text("Remove photo") }
                    }
                    Spacer(Modifier.height(28.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete match", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "This match was recorded by your friend. " +
                                "Only the tracker can edit photos or delete the match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (
        showDeleteConfirmation &&
        currentUserId != null &&
        match?.ownerUserId == currentUserId
    ) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete match?") },
            text = { Text("This permanently deletes the match and its photo.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    currentUserId?.let { userId ->
                        scope.launch {
                            try {
                                if (sessionManager.currentUserId.first() != userId) {
                                    snackbar.showSnackbar("Please log in to delete this match")
                                    return@launch
                                }

                                val existing = dao.getMatchByIdForUser(matchId, userId)
                                if (existing == null) {
                                    snackbar.showSnackbar("Match not found")
                                    return@launch
                                }

                                if (dao.deleteMatchByIdForUser(matchId, userId) == 1) {
                                    withContext(Dispatchers.IO) {
                                        existing.photoUri?.let { path ->
                                            deleteOwnedPhoto(context.filesDir, path)
                                        }
                                    }
                                    onBack()
                                } else {
                                    snackbar.showSnackbar("Match not found")
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                snackbar.showSnackbar("Could not delete match")
                            }
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
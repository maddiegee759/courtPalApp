package com.example.courtpalapp.feature.matches

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.SessionManager
import com.example.courtpalapp.data.local.toMatchEntity
import com.example.courtpalapp.feature.profile.recordEarnedBadges
import com.example.courtpalapp.domain.match.MatchSettings
import com.example.courtpalapp.domain.match.SetScore
import com.example.courtpalapp.domain.match.TennisScoring
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ManualMatchScreen(
    settings: MatchSettings,
    calendarEventId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AppDatabase.getDatabase(context) }
    val sessionManager = remember(context) { SessionManager(context) }
    val currentUserId by sessionManager.currentUserId.collectAsState()
    val scope = rememberCoroutineScope()
    var games by rememberSaveable {
        mutableStateOf(ArrayList(List(settings.bestOfSets * 2) { "" }))
    }
    var galleryPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            galleryPhoto = uri
            cameraPhoto = null
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            cameraPhoto = bitmap
            galleryPhoto = null
        }
    }

    BackHandler(enabled = saving) {}
    Column(
        modifier = Modifier.fillMaxSize().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack, enabled = !saving) { Text("Back to match setup") }
        Text("Enter result", style = MaterialTheme.typography.headlineMedium)
        Text("${settings.player1Name} vs ${settings.player2Name}")
        Text("Enter games won in each played set. Leave unused sets blank.")

        repeat(settings.bestOfSets) { set ->
            Text("Set ${set + 1}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(settings.player1Name, settings.player2Name).forEachIndexed { player, name ->
                    val index = set * 2 + player
                    OutlinedTextField(
                        value = games[index],
                        onValueChange = { value ->
                            if (value.length <= 9 && value.all { it in '0'..'9' }) {
                                games = ArrayList(games).apply { this[index] = value }
                                error = null
                            }
                        },
                        label = { Text(name, maxLines = 1) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Text("Match photo (optional)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { camera.launch(null) }, enabled = !saving) {
                Text("Take photo")
            }
            OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !saving) {
                Text("Choose photo")
            }
        }
        if (galleryPhoto != null || cameraPhoto != null) {
            Text("Photo attached")
            TextButton(onClick = {
                galleryPhoto = null
                cameraPhoto = null
            }, enabled = !saving) { Text("Remove photo") }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
            onClick = save@{
                if (saving) return@save
                val result = try {
                    parseManualResult(settings, games)
                } catch (e: IllegalArgumentException) {
                    error = e.message
                    return@save
                }
                val ownerUserId = currentUserId ?: run {
                    error = "Please log in before saving a match."
                    return@save
                }
                val selectedUri = galleryPhoto
                val selectedBitmap = cameraPhoto
                saving = true
                error = null
                scope.launch {
                    val savedMatchId = try {
                        withContext(
                            NonCancellable + Dispatchers.IO
                        ) {
                            val photoPath = when {
                                selectedUri != null -> copyPhotoToPrivateStorage(context, selectedUri)
                                selectedBitmap != null -> saveBitmapToPrivateStorage(context, selectedBitmap)
                                else -> null
                            }
                            val insertedMatchId = try {
                                val completedScore = TennisScoring.newMatch(settings).copy(
                                    completedSets = result.sets,
                                    player1Sets = result.player1Sets,
                                    player2Sets = result.player2Sets,
                                    winner = result.winner
                                )
                                val entity = completedScore.toMatchEntity(settings).copy(
                                    trackRallies = false,
                                    photoUri = photoPath,
                                    ownerUserId = ownerUserId
                                )
                                database.withTransaction {
                                    if (calendarEventId != null) {
                                        val dao = database.calendarEventDao()
                                        val event = dao.getEventById(calendarEventId)
                                        check(event != null && event.status == "CONFIRMED" && !event.hasStarted) {
                                            "This scheduled match is no longer available."
                                        }
                                        dao.markMatchStarted(calendarEventId)
                                    }
                                    database.matchDao().insertMatch(entity)
                                }
                            } catch (e: Exception) {
                                photoPath?.let { deleteOwnedPhoto(context.filesDir, it) }
                                throw e
                            }
                            // Badge recording is separate: its failure must never
                            // roll back a saved result or remove its attached photo.
                            try {
                                recordEarnedBadges(database, ownerUserId)
                            } catch (badgeError: Exception) {
                                Log.w("CourtPalBadges", "Could not record match badges", badgeError)
                            }
                            insertedMatchId
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save the result."
                        return@launch
                    } finally {
                        saving = false
                    }
                    if (sessionManager.currentUserId.first() == ownerUserId) {
                        onSaved(savedMatchId)
                    }
                }
            }
        ) { Text(if (saving) "Saving…" else "Save result") }
    }
}

private data class ManualResult(
    val sets: List<SetScore>,
    val player1Sets: Int,
    val player2Sets: Int,
    val winner: Int
)

private fun parseManualResult(settings: MatchSettings, games: List<String>): ManualResult {
    val sets = mutableListOf<SetScore>()
    val setWins = IntArray(2)
    val setsToWin = settings.bestOfSets / 2 + 1
    val target = settings.gamesPerSet
    var blankSet = false

    games.chunked(2).forEachIndexed { index, (first, second) ->
        if (first.isEmpty() && second.isEmpty()) {
            blankSet = true
            return@forEachIndexed
        }
        val setNumber = index + 1
        require(!blankSet) { "Enter scores in set order." }
        require(setWins.none { it == setsToWin }) { "Remove sets entered after the match was won." }
        val firstGames = requireNotNull(first.toIntOrNull()) { "Enter both scores for set $setNumber." }
        val secondGames = requireNotNull(second.toIntOrNull()) { "Enter both scores for set $setNumber." }
        val high = maxOf(firstGames, secondGames)
        val low = minOf(firstGames, secondGames)
        val valid = when {
            high == target -> low <= target - 2
            high == target + 1 -> low == target - 1 || (settings.tieBreakEnabled && low == target)
            !settings.tieBreakEnabled && high > target + 1 -> high - low == 2
            else -> false
        }
        require(valid) { "Enter a completed score for set $setNumber." }

        setWins[if (firstGames > secondGames) 0 else 1]++
        sets.add(SetScore(player1Games = firstGames, player2Games = secondGames))
    }

    val winner = setWins.indexOf(setsToWin)
    require(winner >= 0) { "Enter enough sets for one player to win the match." }
    return ManualResult(sets, setWins[0], setWins[1], winner + 1)
}

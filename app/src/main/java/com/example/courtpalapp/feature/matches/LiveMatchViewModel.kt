package com.example.courtpalapp.feature.matches

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.courtpalapp.domain.match.MatchSettings
import com.example.courtpalapp.domain.match.TennisScore
import com.example.courtpalapp.domain.match.TennisScoring
import androidx.lifecycle.viewModelScope
import com.example.courtpalapp.data.local.toMatchEntity
import com.example.courtpalapp.feature.profile.recordEarnedBadges
import kotlinx.coroutines.launch
import com.example.courtpalapp.data.local.ActiveMatchDao
import com.example.courtpalapp.data.local.ActiveMatchEntity
import com.example.courtpalapp.data.local.ActiveMatchJson
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.room.withTransaction
import com.example.courtpalapp.data.local.AppDatabase

class LiveMatchViewModel : ViewModel() {

    var score by mutableStateOf<TennisScore?>(null)
        private set

    var history by mutableStateOf(emptyList<TennisScore>())
        private set

    var rallyShots by mutableIntStateOf(0)
        private set

    var showFinishDialog by mutableStateOf(false)
        private set

    var settings by mutableStateOf<MatchSettings?>(null)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var savedMatchId by mutableStateOf<Long?>(null)
        private set

    var saveError by mutableStateOf<String?>(null)
        private set

    private var activeMatchDao: ActiveMatchDao? = null
    private var saveJob: Job? = null

    var activeMatchId by mutableStateOf<Long?>(null)
        private set

    private var activeMatchOwnerUserId: Long? = null

    var persistenceError by mutableStateOf<String?>(null)
        private set

    fun clearPersistenceError() {
        persistenceError = null
    }

    fun initialise(newSettings: MatchSettings) {
        if (score != null) return

        settings = newSettings
        score = TennisScoring.newMatch(newSettings)
    }

    private var lastSessionUserId: Long? = null

    private fun ownsCurrentActiveMatch(userId: Long): Boolean {
        return lastSessionUserId == userId &&
                activeMatchOwnerUserId == userId
    }

    fun resetForAccountChange(userId: Long?) {
        if (lastSessionUserId == userId) return

        lastSessionUserId = userId

        // Clear only the match held in memory.
        // Do not delete the unfinished match from Room.
        settings = null
        score = null
        history = emptyList()
        rallyShots = 0
        showFinishDialog = false

        activeMatchId = null
        activeMatchOwnerUserId = null

        isSaving = false
        savedMatchId = null
        saveError = null
        persistenceError = null
    }

    fun awardPoint(player: Int) {
        val current = score ?: return
        val currentSettings = settings ?: return

        if (current.isFinished) return

        history = history + current

        score = TennisScoring.awardPoint(
            score = current,
            settings = currentSettings,
            player = player,
            rallyShots = if (currentSettings.trackRallies) {
                rallyShots
            } else {
                null
            }
        )

        rallyShots = 0

        if (score?.isFinished == true) {
            showFinishDialog = true
        }

        persistCurrentMatch()
    }

    fun startNewMatch(newSettings: MatchSettings) {
        settings = newSettings
        score = TennisScoring.newMatch(newSettings)
        history = emptyList()
        rallyShots = 0
        showFinishDialog = false
        isSaving = false
        savedMatchId = null
        saveError = null
        activeMatchId = null
        activeMatchOwnerUserId = null
    }

    fun undoPoint() {
        if (history.isEmpty()) return

        score = history.last()
        history = history.dropLast(1)
        showFinishDialog = false

        persistCurrentMatch()
    }

    fun addRallyShot() {
        rallyShots++
        persistCurrentMatch()
    }

    fun removeRallyShot() {
        if (rallyShots > 0) rallyShots--
        persistCurrentMatch()
    }

    fun resetRallyShots() {
        rallyShots = 0
        persistCurrentMatch()
    }

    fun dismissFinishDialog() {
        showFinishDialog = false
    }


    fun saveCompletedMatch(
        database: AppDatabase,
        ownerUserId: Long,
        onSaved: (Long) -> Unit
    ) {
        val currentScore = score ?: return
        val currentSettings = settings ?: return

        if (!currentScore.isFinished || isSaving) return

        savedMatchId?.let { savedId ->
            if (lastSessionUserId == ownerUserId) {
                onSaved(savedId)
            }
            return
        }

        val currentActiveMatchId = activeMatchId ?: return

        if (!ownsCurrentActiveMatch(ownerUserId)) {
            saveError = "This match belongs to another account"
            return
        }

        isSaving = true
        saveError = null

        viewModelScope.launch {
            try {
                // Finish any pending score updates.
                saveJob?.join()

                check(ownsCurrentActiveMatch(ownerUserId)) {
                    "Account changed while saving"
                }

                // Insert into history and remove from active matches
                // as one transaction.
                val savedId = database.withTransaction {
                    val existing = database.activeMatchDao()
                        .getMatchByIdForUser(
                            currentActiveMatchId,
                            ownerUserId
                        )

                    check(existing != null) {
                        "Match not found for this account"
                    }

                    val entity = currentScore
                        .toMatchEntity(currentSettings)
                        .copy(ownerUserId = ownerUserId)

                    val id = database.matchDao()
                        .insertMatch(entity)

                    val deleted = database.activeMatchDao()
                        .deleteMatchForUser(
                            currentActiveMatchId,
                            ownerUserId
                        )

                    check(deleted == 1) {
                        "Unable to remove active match"
                    }

                    id
                }

                // Do not update another account's screen.
                if (lastSessionUserId != ownerUserId) {
                    return@launch
                }

                savedMatchId = savedId
                activeMatchId = null
                activeMatchOwnerUserId = null

                // Badge failure must not duplicate the saved match.
                try {
                    recordEarnedBadges(database, ownerUserId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Log.w(
                        "CourtPalBadges",
                        "Could not record match badges",
                        e
                    )
                }

                if (lastSessionUserId == ownerUserId) {
                    onSaved(savedId)
                }

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (lastSessionUserId == ownerUserId) {
                    saveError = e.message ?: "Unable to save match"
                }
            } finally {
                if (lastSessionUserId == ownerUserId) {
                    isSaving = false
                }
            }
        }
    }

    fun saveUnfinishedMatch(
        database: AppDatabase,
        ownerUserId: Long,
        onSaved: (Long) -> Unit
    ) {
        val currentScore = score ?: return
        val currentSettings = settings ?: return
        val currentActiveMatchId = activeMatchId ?: return

        if (currentScore.isFinished || isSaving) return

        if (!ownsCurrentActiveMatch(ownerUserId)) {
            saveError = "This match belongs to another account"
            return
        }

        isSaving = true
        saveError = null

        viewModelScope.launch {
            try {
                saveJob?.join()

                check(ownsCurrentActiveMatch(ownerUserId)) {
                    "Account changed while saving"
                }

                val savedId = database.withTransaction {
                    val existing = database.activeMatchDao()
                        .getMatchByIdForUser(
                            currentActiveMatchId,
                            ownerUserId
                        )

                    check(existing != null) {
                        "Match not found for this account"
                    }

                    val entity = currentScore.toMatchEntity(
                        settings = currentSettings,
                        allowUnfinished = true
                    ).copy(ownerUserId = ownerUserId)

                    val id = database.matchDao()
                        .insertMatch(entity)

                    val deleted = database.activeMatchDao()
                        .deleteMatchForUser(
                            currentActiveMatchId,
                            ownerUserId
                        )

                    check(deleted == 1) {
                        "Unable to remove unfinished match"
                    }

                    id
                }

                if (lastSessionUserId != ownerUserId) {
                    return@launch
                }

                savedMatchId = savedId
                activeMatchId = null
                activeMatchOwnerUserId = null

                settings = null
                score = null
                history = emptyList()
                rallyShots = 0
                showFinishDialog = false
                saveError = null

                onSaved(savedId)

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (lastSessionUserId == ownerUserId) {
                    saveError =
                        e.message ?: "Unable to save unfinished match"
                }
            } finally {
                if (lastSessionUserId == ownerUserId) {
                    isSaving = false
                }
            }
        }
    }

    fun deleteUnfinishedMatch(
        ownerUserId: Long,
        onDeleted: () -> Unit
    ) {
        val id = activeMatchId ?: return
        val dao = activeMatchDao ?: return
        val currentScore = score ?: return

        if (currentScore.isFinished || isSaving) return

        if (!ownsCurrentActiveMatch(ownerUserId)) {
            saveError = "This match belongs to another account"
            return
        }

        isSaving = true
        saveError = null

        viewModelScope.launch {
            try {
                saveJob?.join()

                check(ownsCurrentActiveMatch(ownerUserId)) {
                    "Account changed while deleting"
                }

                val deleted = dao.deleteMatchForUser(
                    matchId = id,
                    userId = ownerUserId
                )

                check(deleted == 1) {
                    "Match not found for this account"
                }

                if (lastSessionUserId != ownerUserId) {
                    return@launch
                }

                // Clear the deleted match from memory.
                activeMatchId = null
                activeMatchOwnerUserId = null
                settings = null
                score = null
                history = emptyList()
                rallyShots = 0
                showFinishDialog = false
                savedMatchId = null
                saveError = null

                onDeleted()

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (lastSessionUserId == ownerUserId) {
                    saveError =
                        e.message ?: "Unable to delete match"
                }
            } finally {
                if (lastSessionUserId == ownerUserId) {
                    isSaving = false
                }
            }
        }
    }

    fun setActiveMatchDao(dao: ActiveMatchDao) {
        activeMatchDao = dao
    }


    fun startPersistedMatch(
        newSettings: MatchSettings,
        ownerUserId: Long,
        calendarEventId: Long? = null,
        database: AppDatabase? = null,
        onCreated: (Long) -> Unit
    ) {
        val dao = activeMatchDao ?: run {
            persistenceError = "Active match database is unavailable"
            return
        }

        viewModelScope.launch {
            try {
                persistenceError = null

                val initialScore = TennisScoring.newMatch(newSettings)

                val entity = ActiveMatchEntity(
                    ownerUserId = ownerUserId,
                    calendarEventId = calendarEventId,
                    player1Name = newSettings.player1Name,
                    player2Name = newSettings.player2Name,
                    settingsJson = ActiveMatchJson.settingsToJson(newSettings),
                    scoreJson = ActiveMatchJson.scoreToJson(initialScore),
                    historyJson = ActiveMatchJson.historyToJson(emptyList()),
                    rallyShots = 0
                )

                val id = if (calendarEventId != null) {
                    val db = requireNotNull(database) {
                        "Database required for scheduled matches"
                    }

                    db.withTransaction {
                        val event = db.calendarEventDao()
                            .getEventById(calendarEventId)

                        check(event != null &&
                                event.status == "CONFIRMED" &&
                                !event.hasStarted
                        ) {
                            "This scheduled match is no longer available"
                        }

                        check(dao.getMatchByCalendarEvent(calendarEventId) == null) {
                            "This scheduled match has already been started"
                        }

                        val newId = dao.insertMatch(entity)

                        db.calendarEventDao()
                            .markMatchStarted(calendarEventId)

                        newId
                    }
                } else {
                    dao.insertMatch(entity)
                }

                startNewMatch(newSettings)
                activeMatchId = id
                activeMatchOwnerUserId = ownerUserId
                onCreated(id)

            } catch (e: Exception) {
                persistenceError = e.message ?: "Unable to start match"
            }
        }
    }

    fun loadActiveMatch(
        matchId: Long,
        ownerUserId: Long,
        onLoaded: () -> Unit
    ) {
        val dao = activeMatchDao ?: run {
            persistenceError = "Active match database is unavailable"
            return
        }

        viewModelScope.launch {
            try {
                persistenceError = null

                val entity = dao.getMatchByIdForUser(matchId, ownerUserId)
                    ?: throw IllegalStateException(
                        "Match not found for this account"
                    )
                val restoredSettings =
                    ActiveMatchJson.settingsFromJson(entity.settingsJson)

                val restoredScore =
                    ActiveMatchJson.scoreFromJson(entity.scoreJson)

                val restoredHistory =
                    ActiveMatchJson.historyFromJson(entity.historyJson)

                settings = restoredSettings
                score = restoredScore
                history = restoredHistory
                rallyShots = entity.rallyShots
                activeMatchId = entity.id
                activeMatchOwnerUserId = ownerUserId

                showFinishDialog = false
                savedMatchId = null
                saveError = null

                onLoaded()
            } catch (e: Exception) {
                persistenceError = e.message ?: "Unable to load match"
            }
        }
    }

    private fun persistCurrentMatch() {
        val ownerId = activeMatchOwnerUserId ?: return
        val dao = activeMatchDao ?: return
        val id = activeMatchId ?: return
        val currentSettings = settings ?: return
        val currentScore = score ?: return

        val snapshot = ActiveMatchEntity(
            id = id,
            ownerUserId = ownerId,
            player1Name = currentSettings.player1Name,
            player2Name = currentSettings.player2Name,
            settingsJson = ActiveMatchJson.settingsToJson(currentSettings),
            scoreJson = ActiveMatchJson.scoreToJson(currentScore),
            historyJson = ActiveMatchJson.historyToJson(history.toList()),
            rallyShots = rallyShots,
            lastUpdated = System.currentTimeMillis()
        )

        val previousJob = saveJob

        saveJob = viewModelScope.launch {
            try {
                previousJob?.join()

                val rowsUpdated = dao.updateProgressForUser(
                    matchId = id,
                    ownerUserId = ownerId,
                    scoreJson = snapshot.scoreJson,
                    historyJson = snapshot.historyJson,
                    rallyShots = snapshot.rallyShots,
                    lastUpdated = snapshot.lastUpdated
                )

                if (lastSessionUserId == ownerId) {
                    persistenceError = if (rowsUpdated == 1) {
                        null
                    } else {
                        "Unable to save match progress for this account"
                    }
                }

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (lastSessionUserId == ownerId) {
                    persistenceError =
                        e.message ?: "Unable to save match progress"
                }
            }
        }
    }
}

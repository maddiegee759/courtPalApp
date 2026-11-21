package com.example.courtpalapp.feature.matches

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.courtpalapp.domain.match.MatchSettings
import com.example.courtpalapp.domain.match.TennisScore
import com.example.courtpalapp.domain.match.TennisScoring
import androidx.lifecycle.viewModelScope
import com.example.courtpalapp.data.local.MatchDao
import com.example.courtpalapp.data.local.toMatchEntity
import kotlinx.coroutines.launch
import com.example.courtpalapp.data.local.ActiveMatchDao
import com.example.courtpalapp.data.local.ActiveMatchEntity
import com.example.courtpalapp.data.local.ActiveMatchJson
import kotlinx.coroutines.Job
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
        matchDao: MatchDao,
        onSaved: (Long) -> Unit
    ) {
        val currentScore = score ?: return
        val currentSettings = settings ?: return

        if (!currentScore.isFinished || isSaving) return

        savedMatchId?.let {
            onSaved(it)
            return
        }

        isSaving = true
        saveError = null

        val completedActiveMatchId = activeMatchId

        viewModelScope.launch {
            try {
                // Wait for pending score updates.
                saveJob?.join()

                // Save the completed match to Match History.
                val entity = currentScore.toMatchEntity(currentSettings)
                val savedId = matchDao.insertMatch(entity)

                savedMatchId = savedId

                // Remove only this completed match from active_matches.
                if (completedActiveMatchId != null) {
                    activeMatchDao?.deleteMatch(completedActiveMatchId)
                }

                // Navigate to Match History.
                onSaved(savedId)

            } catch (e: Exception) {
                saveError = e.message ?: "Unable to save match"
            } finally {
                isSaving = false
            }
        }
    }

    fun saveUnfinishedMatch(
        database: AppDatabase,
        onSaved: (Long) -> Unit
    ) {
        val currentScore = score ?: return
        val currentSettings = settings ?: return
        val currentActiveMatchId = activeMatchId ?: return

        if (currentScore.isFinished || isSaving) return

        isSaving = true
        saveError = null

        viewModelScope.launch {
            try {
                // Wait for any pending score updates.
                saveJob?.join()

                val savedId = database.withTransaction {
                    // Save the partial match to Match History.
                    val entity = currentScore.toMatchEntity(
                        settings = currentSettings,
                        allowUnfinished = true
                    )

                    val id = database.matchDao().insertMatch(entity)

                    // Remove the unfinished match from Continue Matches.
                    database.activeMatchDao()
                        .deleteMatch(currentActiveMatchId)

                    id
                }

                savedMatchId = savedId
                activeMatchId = null

                // Clear the saved match from memory.
                settings = null
                score = null
                history = emptyList()
                rallyShots = 0
                showFinishDialog = false
                saveError = null

                onSaved(savedId)

            } catch (e: Exception) {
                saveError = e.message ?: "Unable to save unfinished match"
            } finally {
                isSaving = false
            }
        }
    }

    fun deleteUnfinishedMatch(
        onDeleted: () -> Unit
    ) {
        val id = activeMatchId ?: return
        val dao = activeMatchDao ?: return
        val currentScore = score ?: return

        if (currentScore.isFinished || isSaving) return

        isSaving = true
        saveError = null

        viewModelScope.launch {
            try {
                // Finish any pending score updates before deletion
                saveJob?.join()

                dao.deleteMatch(id)

                // Clear the deleted match from memory
                activeMatchId = null
                settings = null
                score = null
                history = emptyList()
                rallyShots = 0
                showFinishDialog = false
                savedMatchId = null
                saveError = null

                onDeleted()

            } catch (e: Exception) {
                saveError = e.message ?: "Unable to delete match"
            } finally {
                isSaving = false
            }
        }
    }

    fun setActiveMatchDao(dao: ActiveMatchDao) {
        activeMatchDao = dao
    }


    fun startPersistedMatch(
        newSettings: MatchSettings,
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
                onCreated(id)

            } catch (e: Exception) {
                persistenceError = e.message ?: "Unable to start match"
            }
        }
    }

    fun loadActiveMatch(
        matchId: Long,
        onLoaded: () -> Unit
    ) {
        val dao = activeMatchDao ?: run {
            persistenceError = "Active match database is unavailable"
            return
        }

        viewModelScope.launch {
            try {
                persistenceError = null

                val entity = dao.getMatchById(matchId)
                    ?: throw IllegalStateException("Match not found")

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
        val dao = activeMatchDao ?: return
        val id = activeMatchId ?: return
        val currentSettings = settings ?: return
        val currentScore = score ?: return

        val snapshot = ActiveMatchEntity(
            id = id,
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

                val existing = dao.getMatchById(id) ?: return@launch

                dao.updateMatch(
                    snapshot.copy(
                        calendarEventId = existing.calendarEventId
                    )
                )

                persistenceError = null
            } catch (e: Exception) {
                persistenceError =
                    e.message ?: "Unable to save match progress"
            }
        }
    }
}

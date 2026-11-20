package com.example.courtpalapp.feature.matches

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.courtpalapp.domain.match.MatchSettings
import com.example.courtpalapp.domain.match.TennisScore
import com.example.courtpalapp.domain.match.TennisScoring

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
    }

    fun startNewMatch(newSettings: MatchSettings) {
        settings = newSettings
        score = TennisScoring.newMatch(newSettings)
        history = emptyList()
        rallyShots = 0
        showFinishDialog = false
    }

    fun undoPoint() {
        if (history.isEmpty()) return

        score = history.last()
        history = history.dropLast(1)
        showFinishDialog = false
    }

    fun addRallyShot() {
        rallyShots++
    }

    fun removeRallyShot() {
        if (rallyShots > 0) rallyShots--
    }

    fun resetRallyShots() {
        rallyShots = 0
    }

    fun dismissFinishDialog() {
        showFinishDialog = false
    }
}

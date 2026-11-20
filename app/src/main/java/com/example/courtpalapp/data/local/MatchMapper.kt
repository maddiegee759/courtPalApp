package com.example.courtpalapp.data.local

import com.example.courtpalapp.domain.match.MatchSettings
import com.example.courtpalapp.domain.match.TennisScore
import org.json.JSONArray
import org.json.JSONObject

fun TennisScore.toMatchEntity(
    settings: MatchSettings
): MatchEntity {

    require(isFinished) {
        "Only completed matches can be saved"
    }

    val setsJson = JSONArray()

    completedSets.forEach { set ->
        val json = JSONObject().apply {
            put("player1Games", set.player1Games)
            put("player2Games", set.player2Games)

            set.player1TieBreakPoints?.let {
                put("player1TieBreakPoints", it)
            }

            set.player2TieBreakPoints?.let {
                put("player2TieBreakPoints", it)
            }
        }

        setsJson.put(json)
    }

    return MatchEntity(
        player1Name = settings.player1Name,
        player2Name = settings.player2Name,
        player2Id = settings.player2Id,

        winner = requireNotNull(winner),

        player1Sets = player1Sets,
        player2Sets = player2Sets,

        player1Games = player1Games,
        player2Games = player2Games,

        player1TotalPoints = player1TotalPoints,
        player2TotalPoints = player2TotalPoints,

        longestRally = rallies.maxOrNull() ?: 0,
        averageRally = if (rallies.isNotEmpty()) {
            rallies.average()
        } else {
            0.0
        },

        bestOfSets = settings.bestOfSets,
        gamesPerSet = settings.gamesPerSet,

        completedSetsJson = setsJson.toString(),

        scoringMode = settings.scoringMode.name,
        tieBreakEnabled = settings.tieBreakEnabled,
        tieBreakPoints = settings.tieBreakPoints,
        trackRallies = settings.trackRallies,
        matchMode = settings.matchMode.name
    )
}

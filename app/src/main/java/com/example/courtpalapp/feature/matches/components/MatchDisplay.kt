package com.example.courtpalapp.feature.matches.components

import com.example.courtpalapp.common.formatUsername
import com.example.courtpalapp.data.local.MatchEntity
import org.json.JSONArray

data class MatchDisplay(
    val title: String,
    val winner: String?,
    val sets: String,
    val pointsWon: String
)

fun displayMatchForViewer(
    match: MatchEntity,
    viewerUserId: Long,
    usernamesById: Map<Long, String>
): MatchDisplay {

    // Player 1 is always the tracker in the saved match.
    // Player 2 might be a linked CourtPal friend.
    val viewerIsPlayer2 =
        match.ownerUserId != viewerUserId &&
                match.player2Id?.toLongOrNull() == viewerUserId

    val opponentName = if (viewerIsPlayer2) {

        // The friend is viewing: show the tracker's username.
        match.ownerUserId?.let { usernamesById[it] }
            ?: match.player1Name.takeUnless {
                it.equals("You", ignoreCase = true)
            }
            ?: "Opponent"

    } else {

        // The tracker is viewing: show the opponent's username.
        match.player2Id?.toLongOrNull()?.let { usernamesById[it] }
            ?: match.player2Name
    }

    val opponentLabel = if (match.player2Id != null) {
        formatUsername(opponentName)
    } else {
        opponentName
    }

    val winnerName = when {
        !match.isCompleted -> null

        match.winner == 1 ->
            if (viewerIsPlayer2) opponentLabel else "You"

        match.winner == 2 ->
            if (viewerIsPlayer2) "You" else opponentLabel

        else -> "Unknown"
    }

    return MatchDisplay(
        title = "You vs $opponentLabel",
        winner = winnerName,
        sets = formatSetsForViewer(match, viewerIsPlayer2),
        pointsWon = formatScore(
            match.player1TotalPoints,
            match.player2TotalPoints,
            viewerIsPlayer2
        )
    )
}

private fun formatScore(
    player1: Int,
    player2: Int,
    reverse: Boolean
): String {
    return if (reverse) {
        "$player2–$player1"
    } else {
        "$player1–$player2"
    }
}

private fun formatSetsForViewer(
    match: MatchEntity,
    reverse: Boolean
): String {
    val completed = try {
        val sets = JSONArray(match.completedSetsJson)

        if (sets.length() == 0) {
            "Not recorded"
        } else {
            (0 until sets.length()).joinToString("  |  ") { index ->
                val set = sets.getJSONObject(index)

                val games = formatScore(
                    set.getInt("player1Games"),
                    set.getInt("player2Games"),
                    reverse
                )

                val hasTieBreak =
                    set.has("player1TieBreakPoints") &&
                            !set.isNull("player1TieBreakPoints") &&
                            set.has("player2TieBreakPoints") &&
                            !set.isNull("player2TieBreakPoints")

                if (hasTieBreak) {
                    val tieBreak = formatScore(
                        set.getInt("player1TieBreakPoints"),
                        set.getInt("player2TieBreakPoints"),
                        reverse
                    )

                    "$games (TB $tieBreak)"
                } else {
                    games
                }
            }
        }
    } catch (_: Exception) {
        "Unavailable"
    }

    if (match.isCompleted) {
        return completed
    }

    val currentSet = formatScore(
        match.player1Games,
        match.player2Games,
        reverse
    )

    return if (completed == "Not recorded") {
        currentSet
    } else {
        "$completed  |  $currentSet"
    }
}
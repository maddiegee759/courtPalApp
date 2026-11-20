package com.example.courtpalapp.domain.match

import kotlin.math.abs

object TennisScoring {

    fun newMatch(settings: MatchSettings): TennisScore {
        return TennisScore(
            firstServer = settings.firstServer,
            currentServer = settings.firstServer
        )
    }

    fun pointLabel(
        ownPoints: Int,
        opponentPoints: Int,
        isTieBreak: Boolean,
        scoringMode: ScoringMode
    ): String {
        if (isTieBreak) return ownPoints.toString()

        val labels = listOf("0", "15", "30", "40")

        if (scoringMode == ScoringMode.NO_ADVANTAGE) {
            return labels[ownPoints.coerceAtMost(3)]
        }

        if (ownPoints >= 3 && opponentPoints >= 3) {
            return if (ownPoints > opponentPoints) "AD" else "40"
        }

        return labels[ownPoints.coerceAtMost(3)]
    }

    fun awardPoint(
        score: TennisScore,
        settings: MatchSettings,
        player: Int,
        rallyShots: Int? = null
    ): TennisScore {

        require(player == 1 || player == 2) {
            "Player must be 1 or 2"
        }

        if (score.isFinished) return score

        val p1 = score.player1Points +
                if (player == 1) 1 else 0

        val p2 = score.player2Points +
                if (player == 2) 1 else 0

        val totalPoints1 = score.player1TotalPoints +
                if (player == 1) 1 else 0

        val totalPoints2 = score.player2TotalPoints +
                if (player == 2) 1 else 0

        val updatedRallies =
            if (settings.trackRallies && rallyShots != null) {
                score.rallies + rallyShots
            } else {
                score.rallies
            }

        val pointTarget = when {
            score.isTieBreak -> settings.tieBreakPoints
            settings.scoringMode == ScoringMode.NO_ADVANTAGE -> 4
            else -> 4
        }

        val pointsNeededToWin = when {
            score.isTieBreak -> 2
            settings.scoringMode == ScoringMode.NO_ADVANTAGE -> 1
            else -> 2
        }

        val gameWon =
            (p1 >= pointTarget || p2 >= pointTarget) &&
                    abs(p1 - p2) >= pointsNeededToWin

        if (!gameWon) {
            val tieBreakPoints =
                score.tieBreakPointsPlayed +
                        if (score.isTieBreak) 1 else 0

            val nextServer =
                if (score.isTieBreak) {
                    tieBreakServer(
                        firstServer = score.tieBreakFirstServer ?: score.currentServer,
                        pointsPlayed = tieBreakPoints
                    )
                } else {
                    score.currentServer
                }

            return score.copy(
                player1Points = p1,
                player2Points = p2,
                player1TotalPoints = totalPoints1,
                player2TotalPoints = totalPoints2,
                rallies = updatedRallies,
                tieBreakPointsPlayed = tieBreakPoints,
                currentServer = nextServer
            )
        }

        val games1 = score.player1Games +
                if (player == 1) 1 else 0

        val games2 = score.player2Games +
                if (player == 2) 1 else 0

        val setWon = if (score.isTieBreak) {
            true
        } else {
            (games1 >= settings.gamesPerSet ||
                    games2 >= settings.gamesPerSet) &&
                    abs(games1 - games2) >= 2
        }

        val completedGames = score.completedGames + 1

        if (!setWon) {
            val nextTieBreak =
                settings.tieBreakEnabled &&
                        games1 == settings.gamesPerSet &&
                        games2 == settings.gamesPerSet

            return score.copy(
                player1Points = 0,
                player2Points = 0,
                player1Games = games1,
                player2Games = games2,
                player1TotalPoints = totalPoints1,
                player2TotalPoints = totalPoints2,
                completedGames = completedGames,
                isTieBreak = nextTieBreak,
                tieBreakPointsPlayed = 0,
                rallies = updatedRallies,
                currentServer =
                    if (score.currentServer == 1) 2 else 1,
                tieBreakFirstServer =
                    if (nextTieBreak) {
                        if (score.currentServer == 1) 2 else 1
                    } else {
                        null
                    },
            )
        }

        val sets1 = score.player1Sets +
                if (games1 > games2) 1 else 0

        val sets2 = score.player2Sets +
                if (games2 > games1) 1 else 0

        val winner = when {
            sets1 >= settings.setsToWin -> 1
            sets2 >= settings.setsToWin -> 2
            else -> null
        }

        val completedSet = SetScore(
            player1Games = games1,
            player2Games = games2,
            player1TieBreakPoints =
                if (score.isTieBreak) p1 else null,
            player2TieBreakPoints =
                if (score.isTieBreak) p2 else null
        )

        val nextServer =
            if (score.currentServer == 1) 2 else 1

        return score.copy(
            player1Points = 0,
            player2Points = 0,
            player1Games = 0,
            player2Games = 0,
            completedSets = score.completedSets + completedSet,
            player1Sets = sets1,
            player2Sets = sets2,
            player1TotalPoints = totalPoints1,
            player2TotalPoints = totalPoints2,
            completedGames = completedGames,
            isTieBreak = false,
            tieBreakPointsPlayed = 0,
            tieBreakFirstServer = null,
            rallies = updatedRallies,
            currentServer = nextServer,
            winner = winner
        )
    }

    private fun tieBreakServer(
        firstServer: Int,
        pointsPlayed: Int
    ): Int {
        val alternate =
            ((pointsPlayed + 1) / 2) % 2 == 1

        return if (alternate) {
            if (firstServer == 1) 2 else 1
        } else {
            firstServer
        }
    }
}

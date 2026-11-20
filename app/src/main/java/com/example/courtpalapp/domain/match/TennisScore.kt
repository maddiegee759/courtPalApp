package com.example.courtpalapp.domain.match

data class SetScore(
    val player1Games: Int,
    val player2Games: Int,
    val player1TieBreakPoints: Int? = null,
    val player2TieBreakPoints: Int? = null
)

data class TennisScore(
    val player1Points: Int = 0,
    val player2Points: Int = 0,

    val player1Games: Int = 0,
    val player2Games: Int = 0,

    val completedSets: List<SetScore> = emptyList(),

    val player1Sets: Int = 0,
    val player2Sets: Int = 0,

    val isTieBreak: Boolean = false,
    val firstServer: Int = 1,
    val currentServer: Int = 1,
    val tieBreakFirstServer: Int? = null,

    val completedGames: Int = 0,
    val tieBreakPointsPlayed: Int = 0,

    val player1TotalPoints: Int = 0,
    val player2TotalPoints: Int = 0,

    val rallies: List<Int> = emptyList(),

    val winner: Int? = null
) {
    val isFinished: Boolean
        get() = winner != null
}

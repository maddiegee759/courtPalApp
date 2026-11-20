package com.example.courtpalapp.domain.match

enum class MatchMode {
    LIVE,
    MANUAL
}

enum class ScoringMode {
    ADVANTAGE,
    NO_ADVANTAGE
}

data class MatchSettings(

    // Players
    val player1Id: String? = null,
    val player1Name: String = "You",

    val player2Id: String? = null,
    val player2Name: String = "Guest",

    // Match format
    val bestOfSets: Int = 3,
    val gamesPerSet: Int = 6,

    // Scoring rules
    val scoringMode: ScoringMode = ScoringMode.ADVANTAGE,
    val tieBreakEnabled: Boolean = true,
    val tieBreakPoints: Int = 7,

    // Tracking options
    val trackRallies: Boolean = false,
    val matchMode: MatchMode = MatchMode.LIVE,

    // Serving
    val coinFlipEnabled: Boolean = false,
    val firstServer: Int = 1

) {

    init {
        require(bestOfSets in listOf(1, 3, 5)) {
            "Best of sets must be 1, 3 or 5"
        }

        require(gamesPerSet in listOf(4, 6, 8)) {
            "Games per set must be 4, 6 or 8"
        }

        require(tieBreakPoints >= 1) {
            "Tie-break points must be positive"
        }

        require(firstServer == 1 || firstServer == 2) {
            "First server must be Player 1 or Player 2"
        }
    }

    val setsToWin: Int
        get() = (bestOfSets / 2) + 1
}

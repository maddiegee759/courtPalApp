package com.example.courtpalapp.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Matches : Screen("matches")
    data object Friends : Screen("friends")
    data object Calendar : Screen("calendar")
    data object Profile : Screen("profile")
    data object Stats : Screen("stats")
    data object AddMatch : Screen("add_match")
    data object AddBooking : Screen("add_booking")

    object MatchSetup : Screen("match_setup")
    object LiveMatch : Screen("live_match")

    data object MatchSummary : Screen("match_summary/{matchId}") {
        fun createRoute(matchId: Long): String =
            "match_summary/$matchId"
    }
}

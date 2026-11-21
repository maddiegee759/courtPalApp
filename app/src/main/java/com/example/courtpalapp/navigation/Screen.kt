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

    data object MatchSetup : Screen("match_setup")

    data object ScheduledMatchSetup : Screen("match_setup/{eventId}") {
        fun createRoute(eventId: Long) = "match_setup/$eventId"
    }

    data object LiveMatch : Screen("live_match")

    data object ResumeMatch : Screen("live_match/{matchId}") {
        fun createRoute(matchId: Long): String =
            "live_match/$matchId"
    }

    data object MatchSummary : Screen("match_summary/{matchId}") {
        fun createRoute(matchId: Long): String =
            "match_summary/$matchId"
    }
}

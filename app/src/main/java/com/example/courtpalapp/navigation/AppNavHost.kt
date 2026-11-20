package com.example.courtpalapp.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.courtpalapp.common.components.BottomNavBar
import com.example.courtpalapp.feature.home.HomeScreen
import com.example.courtpalapp.feature.matches.MatchHistoryScreen
import com.example.courtpalapp.feature.friends.FriendsScreen
import com.example.courtpalapp.feature.calendar.CalendarScreen
import com.example.courtpalapp.feature.profile.ProfileScreen
import androidx.compose.runtime.*
import com.example.courtpalapp.domain.match.MatchSettings
import com.example.courtpalapp.feature.matches.MatchSetupScreen
import com.example.courtpalapp.feature.matches.LiveMatchScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.courtpalapp.feature.matches.LiveMatchViewModel
import com.example.courtpalapp.feature.matches.MatchSummaryScreen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val matchViewModel: LiveMatchViewModel = viewModel()

    Scaffold(
        bottomBar = {
            BottomNavBar(navController)
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onStartMatchClick = {
                        navController.navigate(Screen.MatchSetup.route)
                    }
                )
            }

            composable(Screen.Matches.route) {
                MatchHistoryScreen(
                    onMatchClick = { matchId ->
                        navController.navigate(
                            Screen.MatchSummary.createRoute(matchId)
                        )
                    }
                )
            }

            composable(
                route = Screen.MatchSummary.route
            ) { backStackEntry ->

                val matchId = backStackEntry.arguments
                    ?.getString("matchId")
                    ?.toLongOrNull()

                if (matchId != null) {
                    MatchSummaryScreen(
                        matchId = matchId,
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                } else {
                    Text("Invalid match ID")
                }
            }

            composable(Screen.Friends.route) {
                FriendsScreen(
                    onNavigateToCalendarEvent = { eventId ->
                        navController.navigate(
                            "${Screen.Calendar.route}/$eventId"
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Calendar.route) {
                CalendarScreen()
            }

            composable(
                route = "${Screen.Calendar.route}/{eventId}",
                arguments = listOf(
                    navArgument("eventId") {
                        type = NavType.LongType
                    }
                )
            ) { backStackEntry ->

                val eventId = backStackEntry.arguments
                    ?.getLong("eventId")

                CalendarScreen(
                    selectedEventId = eventId
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen()
            }

            composable(Screen.MatchSetup.route) {
                MatchSetupScreen(
                    onStartMatch = { settings ->
                        matchViewModel.startNewMatch(settings)
                        navController.navigate(Screen.LiveMatch.route)
                    },
                    onAddFriend = {
                        navController.navigate(Screen.Friends.route)
                    }
                )
            }

            composable(Screen.LiveMatch.route) {
                matchViewModel.settings?.let { settings ->
                    LiveMatchScreen(
                        settings = settings,
                        matchViewModel = matchViewModel,
                        onMatchFinished = { _ ->
                            navController.navigate(Screen.Matches.route) {
                                popUpTo(Screen.LiveMatch.route) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    }
}

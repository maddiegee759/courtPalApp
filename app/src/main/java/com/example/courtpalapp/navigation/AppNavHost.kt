
package com.example.courtpalapp.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

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
                HomeScreen()
            }

            composable(Screen.Matches.route) {
                MatchHistoryScreen()
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
        }
    }
}

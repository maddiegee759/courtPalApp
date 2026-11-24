package com.example.courtpalapp.navigation

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.courtpalapp.common.components.BottomNavBar
import com.example.courtpalapp.data.local.ActiveMatchJson
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.SessionManager
import com.example.courtpalapp.data.local.UserPreferencesRepository
import com.example.courtpalapp.domain.match.MatchMode
import com.example.courtpalapp.domain.match.MatchSettings
import com.example.courtpalapp.feature.auth.LoginScreen
import com.example.courtpalapp.feature.calendar.CalendarScreen
import com.example.courtpalapp.feature.friends.FriendsScreen
import com.example.courtpalapp.feature.home.HomeScreen
import com.example.courtpalapp.feature.home.getUpcomingMatches
import com.example.courtpalapp.feature.matches.LiveMatchScreen
import com.example.courtpalapp.feature.matches.LiveMatchViewModel
import com.example.courtpalapp.feature.matches.ManualMatchScreen
import com.example.courtpalapp.feature.matches.MatchDetailScreen
import com.example.courtpalapp.feature.matches.MatchHistoryScreen
import com.example.courtpalapp.feature.matches.MatchSetupScreen
import com.example.courtpalapp.feature.matches.MatchSummaryScreen
import com.example.courtpalapp.feature.profile.EditProfileScreen
import com.example.courtpalapp.feature.profile.ProfileScreen
import com.example.courtpalapp.feature.profile.SettingsScreen
import com.example.courtpalapp.feature.training.TrainingSessionScreen

private const val MANUAL_RESULT_ROUTE = "manual-result/{settings}?eventId={eventId}"

private fun manualResultRoute(settings: MatchSettings, eventId: Long? = null): String {
    val encodedSettings = Uri.encode(ActiveMatchJson.settingsToJson(settings))
    val eventQuery = eventId?.let { "?eventId=$it" }.orEmpty()
    return "manual-result/$encodedSettings$eventQuery"
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    val context = LocalContext.current

    val database = remember(context) {
        AppDatabase.getDatabase(context)
    }

    val sessionManager = remember(context) {
        SessionManager(context)
    }

    val currentUserId by sessionManager.currentUserId.collectAsState()

    val userDao = remember(database) {
        database.userDao()
    }

    var sessionChecked by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserId) {
        sessionChecked = false

        if (currentUserId != null) {
            val user = userDao.getUserById(currentUserId!!)

            if (user == null) {
                sessionManager.logout()
            }
        }

        sessionChecked = true
    }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            android.util.Log.d("CourtPalNav", "route=${entry.destination.route}")
        }
    }

    val matchViewModel: LiveMatchViewModel = viewModel()

    LaunchedEffect(currentUserId) {
        matchViewModel.resetForAccountChange(currentUserId)
    }

    val activeMatchDao = remember(context) {
        AppDatabase.getDatabase(context).activeMatchDao()
    }

    val calendarEventDao = remember(context) {
        AppDatabase.getDatabase(context).calendarEventDao()
    }

    LaunchedEffect(activeMatchDao) {
        matchViewModel.setActiveMatchDao(activeMatchDao)
    }

    if (!sessionChecked) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (currentUserId == null) {
        LoginScreen(
            userDao = userDao,
            onLogin = { userId ->
                sessionManager.login(userId)
            }
        )
        return
    }

    val linkedFriends by remember(database, currentUserId) {
        database.friendDao()
            .observeFriendDisplaysForUser(currentUserId!!)
    }.collectAsState(initial = emptyList())

    val matchFriendOptions = remember(linkedFriends) {
        linkedFriends.map { friend ->
            friend.friendUserId.toString() to friend.username
        }
    }

    val preferencesRepository = remember(context) {
        UserPreferencesRepository(context)
    }

    val matchPreferencesFlow = remember(preferencesRepository, currentUserId) {
        preferencesRepository.observePreferences(currentUserId!!)
    }

    val matchPreferences by matchPreferencesFlow.collectAsState(
        initial = null
    )

    Scaffold(
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {

            composable(Screen.Home.route) {
                val activeMatchesFlow = remember(
                    activeMatchDao,
                    currentUserId
                ) {
                    activeMatchDao.getActiveMatchesForUser(currentUserId!!)
                }

                val activeMatches by activeMatchesFlow.collectAsState(
                    initial = emptyList()
                )

                val calendarEventsFlow = remember(
                    calendarEventDao,
                    currentUserId
                ) {
                    calendarEventDao.getEventsForUser(currentUserId!!)
                }

                val calendarEvents by calendarEventsFlow.collectAsState(
                    initial = emptyList()
                )
                val upcomingMatches = getUpcomingMatches(calendarEvents)

                HomeScreen(
                    onStartMatchClick = {
                        navController.navigate(Screen.MatchSetup.route)
                    },
                    activeMatches = activeMatches,
                    onContinueMatchClick = { matchId ->
                        navController.navigate(
                            Screen.ResumeMatch.createRoute(matchId)
                        )
                    },
                    upcomingMatches = upcomingMatches,
                    onStartScheduledMatchClick = { eventId ->
                        navController.navigate(
                            Screen.ScheduledMatchSetup.createRoute(eventId)
                        )
                    },
                    onRecordTrainingClick = {
                        navController.navigate(Screen.TrainingSession.route)
                    }
                )
            }

            composable(Screen.TrainingSession.route) {
                TrainingSessionScreen(
                    userId = currentUserId!!,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Matches.route) {
                MatchHistoryScreen(
                    onMatchClick = { matchId ->
                        if (navController.currentBackStackEntry?.destination?.route == Screen.Matches.route) {
                            navController.navigate("match-detail/$matchId")
                        }
                    }
                )
            }

            composable(
                route = "match-detail/{matchId}",
                arguments = listOf(
                    navArgument("matchId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getLong("matchId")
                if (matchId != null) {
                    MatchDetailScreen(
                        matchId = matchId,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    Text("Invalid match ID")
                }
            }

            composable(route = Screen.MatchSummary.route) { backStackEntry ->
                val matchId = backStackEntry.arguments
                    ?.getString("matchId")
                    ?.toLongOrNull()

                if (matchId != null) {
                    MatchSummaryScreen(
                        matchId = matchId,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    Text("Invalid match ID")
                }
            }

            composable(Screen.Friends.route) {
                FriendsScreen(
                    onNavigateToCalendarEvent = { eventId ->
                        navController.navigate("${Screen.Calendar.route}/$eventId") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToCalendarDate = { date ->
                        navController.navigate("${Screen.Calendar.route}/date/$date") {
                            launchSingleTop = true
                        }
                    },
                    onAcceptedMatch = { eventId ->
                        navController.navigate("${Screen.Calendar.route}/accepted/$eventId") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "${Screen.Calendar.route}/accepted/{eventId}",
                arguments = listOf(
                    navArgument("eventId") {
                        type = NavType.LongType
                    }
                )
            ) { backStackEntry ->

                CalendarScreen(
                    selectedEventId = backStackEntry.arguments?.getLong("eventId"),
                    autoSyncAccepted = true
                )
            }

            composable(Screen.Calendar.route) {
                CalendarScreen()
            }

            composable(
                route = "${Screen.Calendar.route}/{eventId}",
                arguments = listOf(
                    navArgument("eventId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getLong("eventId")
                CalendarScreen(selectedEventId = eventId)
            }

            composable(
                route = "${Screen.Calendar.route}/date/{date}",
                arguments = listOf(
                    navArgument("date") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->

                CalendarScreen(
                    requestedDate = backStackEntry.arguments?.getString("date")
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    currentUserId = currentUserId!!,
                    onEditProfile = {
                        navController.navigate("edit_profile")
                    },
                    onSettingsClick = {
                        navController.navigate("settings")
                    },
                    onLogout = {
                        sessionManager.logout()
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    currentUserId = currentUserId!!,
                    onBack = {
                        navController.popBackStack()
                    },
                    onLogout = {
                        sessionManager.logout()
                    }
                )
            }

            composable("edit_profile") {
                EditProfileScreen(
                    currentUserId = currentUserId!!,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.MatchSetup.route) {
                matchPreferences?.let { preferences ->
                    MatchSetupScreen(
                        defaultPreferences = preferences,
                        friends = matchFriendOptions,
                        onStartMatch = onStartMatch@{ settings ->
                            if (settings.matchMode == MatchMode.MANUAL) {
                                navController.navigate(manualResultRoute(settings)) {
                                    launchSingleTop = true
                                }
                                return@onStartMatch
                            }
                            matchViewModel.startPersistedMatch(
                                newSettings = settings,
                                ownerUserId = currentUserId!!,
                                onCreated = {
                                    navController.navigate(Screen.LiveMatch.route)
                                }
                            )
                        },
                        onAddFriend = {
                            navController.navigate(Screen.Friends.route)
                        }
                    )
                }
            }


            composable(
                route = Screen.ScheduledMatchSetup.route,
                arguments = listOf(
                    navArgument("eventId") {
                        type = NavType.LongType
                    },
                )

            ) { backStackEntry ->

                val eventId = backStackEntry.arguments
                    ?.getLong("eventId")
                    ?: return@composable

                LaunchedEffect(eventId) {
                    matchViewModel.clearPersistenceError()
                }

                val calendarEventsFlow = remember(
                    calendarEventDao,
                    currentUserId
                ) {
                    calendarEventDao.getEventsForUser(currentUserId!!)
                }

                val calendarEvents by calendarEventsFlow.collectAsState(
                    initial = emptyList()
                )

                val event = calendarEvents.firstOrNull { it.id == eventId }

                if (event != null) {

                    if (
                        event.status == "CONFIRMED" &&
                        !event.hasStarted
                    ) {
                        val persistenceError = matchViewModel.persistenceError
                        matchPreferences?.let { preferences ->
                            MatchSetupScreen(
                                initialPlayer2Name = event.friendName
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Guest",
                                initialPlayer2Id = event.friendId,
                                defaultPreferences = preferences,
                                friends = matchFriendOptions,

                                onStartMatch = onStartMatch@{ settings ->
                                    if (settings.matchMode == MatchMode.MANUAL) {
                                        navController.navigate(
                                            manualResultRoute(
                                                settings,
                                                eventId
                                            )
                                        ) {
                                            launchSingleTop = true
                                        }
                                        return@onStartMatch
                                    }
                                    matchViewModel.startPersistedMatch(
                                        newSettings = settings,
                                        ownerUserId = currentUserId!!,
                                        calendarEventId = eventId,
                                        database = AppDatabase.getDatabase(context),
                                        onCreated = { matchId ->
                                            navController.navigate(
                                                Screen.ResumeMatch.createRoute(matchId)
                                            ) {
                                                popUpTo(Screen.ScheduledMatchSetup.route) {
                                                    inclusive = true
                                                }
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                },
                                onAddFriend = {
                                    navController.navigate(Screen.Friends.route)
                                },
                                errorMessage = matchViewModel.persistenceError
                            )
                        }
                    } else {
                        Text("This scheduled match is no longer available to start.")
                    }

                } else {
                    CircularProgressIndicator()
                }
            }

            composable(
                route = MANUAL_RESULT_ROUTE,
                arguments = listOf(
                    navArgument("settings") { type = NavType.StringType },
                    navArgument("eventId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { entry ->
                val settings = remember(entry) {
                    entry.arguments?.getString("settings")?.let { json ->
                        runCatching { ActiveMatchJson.settingsFromJson(json) }.getOrNull()
                    }
                }
                if (settings == null) {
                    Text("Could not load match settings.")
                } else {
                    ManualMatchScreen(
                        settings = settings,
                        calendarEventId = entry.arguments?.getLong("eventId")?.takeIf { it != -1L },
                        onBack = { navController.popBackStack() },
                        onSaved = { savedMatchId ->
                            navController.navigate(Screen.Matches.route) {
                                popUpTo(Screen.Home.route) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }

                            navController.navigate("match-detail/$savedMatchId") {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }

            composable(Screen.LiveMatch.route) {
                matchViewModel.settings?.let { settings ->
                    LiveMatchScreen(
                        settings = settings,
                        matchViewModel = matchViewModel,
                        onMatchFinished = { savedMatchId ->
                            navController.navigate(Screen.Matches.route) {
                                popUpTo(Screen.LiveMatch.route) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }

                            navController.navigate("match-detail/$savedMatchId") {
                                launchSingleTop = true
                            }
                        },
                        onMatchQuit = {
                            navController.navigate(Screen.Matches.route) {
                                popUpTo(Screen.Home.route)
                                launchSingleTop = true
                            }
                        },
                        onMatchDeleted = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }


            composable(
                route = Screen.ResumeMatch.route,
                arguments = listOf(
                    navArgument("matchId") {
                        type = NavType.LongType
                    }
                )
            ) { backStackEntry ->

                val matchId = backStackEntry.arguments
                    ?.getLong("matchId")
                    ?: return@composable

                LaunchedEffect(matchId, currentUserId) {
                    currentUserId?.let { userId ->
                        matchViewModel.loadActiveMatch(
                            matchId = matchId,
                            ownerUserId = userId,
                            onLoaded = {}
                        )
                    }
                }

                val settings = matchViewModel.settings

                if (
                    matchViewModel.activeMatchId == matchId &&
                    settings != null
                ) {
                    LiveMatchScreen(
                        settings = settings,
                        matchViewModel = matchViewModel,
                        onMatchFinished = { savedMatchId ->
                            navController.navigate(Screen.Matches.route) {
                                popUpTo(Screen.ResumeMatch.route) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }

                            navController.navigate("match-detail/$savedMatchId") {
                                launchSingleTop = true
                            }
                        },
                        onMatchQuit = {
                            navController.navigate(Screen.Matches.route) {
                                popUpTo(Screen.Home.route)
                                launchSingleTop = true
                            }
                        },
                        onMatchDeleted = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

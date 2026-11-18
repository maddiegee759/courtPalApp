package com.example.courtpalapp.common.components

import android.net.http.SslCertificate.saveState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.courtpalapp.navigation.Screen

@Composable
fun BottomNavBar(navController: NavHostController) {

    val items = listOf(
        Triple(Screen.Home, "Home", Icons.Default.Home),
        Triple(Screen.Matches, "Matches", Icons.Default.SportsTennis),
        Triple(Screen.Friends, "Friends", Icons.Default.People),
        Triple(Screen.Calendar, "Calendar", Icons.Default.CalendarMonth),
        Triple(Screen.Profile, "Profile", Icons.Default.Person)
    )

    val currentRoute =
        navController.currentBackStackEntryAsState()
            .value?.destination?.route

    NavigationBar {
        items.forEach { (screen, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(Screen.Home.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label
                    )
                },
                label = { Text(label) }
            )
        }
    }
}

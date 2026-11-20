
package com.example.courtpalapp.common.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.courtpalapp.navigation.Screen
import com.example.courtpalapp.ui.theme.NavShadow

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

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = 0.dp,
        modifier = Modifier.shadow(
            elevation = 8.dp,
            shape = RectangleShape,
            ambientColor = NavShadow,
            spotColor = NavShadow
        )
    ) {
        items.forEach { (screen, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == screen.route || (screen == Screen.Calendar && currentRoute?.startsWith("${Screen.Calendar.route}/") == true),
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(Screen.Home.route) {
                            inclusive = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label
                    )
                },
                label = {
                    Text(label)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color.White.copy(alpha = 0.75f),
                    unselectedTextColor = Color.White.copy(alpha = 0.75f),
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

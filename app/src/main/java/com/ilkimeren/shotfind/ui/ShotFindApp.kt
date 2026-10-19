package com.ilkimeren.shotfind.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ilkimeren.shotfind.ui.inbox.InboxScreen
import com.ilkimeren.shotfind.ui.search.SearchScreen
import com.ilkimeren.shotfind.ui.settings.SettingsScreen

/** The tabs shown in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    INBOX("inbox", "Gelen kutusu", Icons.Outlined.Inbox),
    SEARCH("search", "Ara", Icons.Outlined.Search),
    SETTINGS("settings", "Ayarlar", Icons.Outlined.Settings)
}

@Composable
fun ShotFindApp() {
    val navController = rememberNavController()
    // Observe the current screen so the bottom bar highlights the right tab
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Don't pile up screens when switching tabs
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.INBOX.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopLevelDestination.INBOX.route) { InboxScreen() }
            composable(TopLevelDestination.SEARCH.route) { SearchScreen() }
            composable(TopLevelDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
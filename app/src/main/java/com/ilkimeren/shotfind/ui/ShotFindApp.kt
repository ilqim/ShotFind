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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ilkimeren.shotfind.ui.detail.DetailScreen
import com.ilkimeren.shotfind.ui.detail.DetailViewModel
import androidx.navigation.navDeepLink
import androidx.compose.foundation.layout.consumeWindowInsets
import com.ilkimeren.shotfind.ui.trash.TrashScreen
import com.ilkimeren.shotfind.ui.sort.SortScreen
import androidx.compose.material.icons.outlined.Folder
import com.ilkimeren.shotfind.ui.tags.AlbumScreen
import com.ilkimeren.shotfind.ui.tags.AlbumViewModel
import com.ilkimeren.shotfind.ui.tags.TagsScreen

/** The tabs shown in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    INBOX("inbox", "Gelen kutusu", Icons.Outlined.Inbox),
    TAGS("tags", "Etiketler", Icons.Outlined.Folder),
    SEARCH("search", "Ara", Icons.Outlined.Search),
    SETTINGS("settings", "Ayarlar", Icons.Outlined.Settings)
}

private const val DETAIL_ROUTE = "detail/{${DetailViewModel.MEDIA_ID_ARG}}"
private const val TRASH_ROUTE = "trash"
private const val SORT_ROUTE = "sort"
private const val ALBUM_ROUTE = "album/{${AlbumViewModel.ALBUM_ID_ARG}}"

private fun detailRoute(mediaId: Long) = "detail/$mediaId"
private fun albumRoute(albumId: Long) = "album/$albumId"

@Composable
fun ShotFindApp() {
    val navController = rememberNavController()
    // Observe the current screen so the bottom bar highlights the right tab
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Hide the bottom bar on screens that aren't tabs (e.g. detail)
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.INBOX.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(TopLevelDestination.INBOX.route) {
                InboxScreen(
                    onScreenshotClick = { navController.navigate(detailRoute(it)) },
                    onStartSorting = { navController.navigate(SORT_ROUTE) }
                )
            }
            composable(SORT_ROUTE) {
                SortScreen(onBack = { navController.popBackStack() })
            }
            composable(TopLevelDestination.TAGS.route) {
                TagsScreen(
                    onOpenAlbum = { navController.navigate(albumRoute(it)) },
                    onOpenKept = { navController.navigate(albumRoute(AlbumViewModel.KEPT_ALBUM_ID)) },
                    onOpenTrash = { navController.navigate(TRASH_ROUTE) }
                )
            }
            composable(
                route = ALBUM_ROUTE,
                arguments = listOf(navArgument(AlbumViewModel.ALBUM_ID_ARG) { type = NavType.LongType })
            ) {
                AlbumScreen(
                    onBack = { navController.popBackStack() },
                    onScreenshotClick = { navController.navigate(detailRoute(it)) }
                )
            }
            composable(TopLevelDestination.SEARCH.route) {
                SearchScreen(onScreenshotClick = { navController.navigate(detailRoute(it)) })
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen()
            }
            composable(TRASH_ROUTE) {
                TrashScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = DETAIL_ROUTE,
                arguments = listOf(navArgument(DetailViewModel.MEDIA_ID_ARG) { type = NavType.LongType }),
                deepLinks = listOf(navDeepLink { uriPattern = "shotfind://detail/{${DetailViewModel.MEDIA_ID_ARG}}" }),
            ) {
                DetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
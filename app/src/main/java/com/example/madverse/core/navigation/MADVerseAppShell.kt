package com.example.madverse.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.madverse.core.designsystem.components.MADVerseBottomBar
import com.example.madverse.core.designsystem.components.MADVerseTopBar
import com.example.madverse.core.designsystem.theme.ObsidianBackground

@Composable
fun MADVerseAppShell() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Check if the current route is one of the primary bottom bar tabs
    val isBottomBarVisible = NavItem.entries.any { it.route == currentRoute }

    val topBarTitle = when {
        currentRoute == Screen.Discover.route -> "MADVerse Discover"
        currentRoute == Screen.Search.route -> "Search Movies"
        currentRoute == Screen.Quiz.route -> "Movie Taste Quiz"
        currentRoute == Screen.Library.route -> "My Library"
        currentRoute?.startsWith("movie_details") == true -> "Movie Overview"
        else -> "MADVerse"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBackground,
        topBar = {
            MADVerseTopBar(
                title = topBarTitle,
                canNavigateBack = !isBottomBarVisible,
                onNavigateBack = { navController.popBackStack() }
            )
        },
        bottomBar = {
            if (isBottomBarVisible) {
                MADVerseBottomBar(
                    currentRoute = currentRoute,
                    onNavigateToRoute = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Discover.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        MADVerseNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

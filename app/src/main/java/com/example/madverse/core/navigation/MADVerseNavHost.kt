package com.example.madverse.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.madverse.feature.details.presentation.MovieDetailsScreen
import com.example.madverse.feature.discover.presentation.DiscoverScreen
import com.example.madverse.feature.library.presentation.FavoritesScreen
import com.example.madverse.feature.library.presentation.LibraryScreen
import com.example.madverse.feature.library.presentation.WatchlistScreen
import com.example.madverse.feature.player.presentation.YouTubePlayerScreen
import com.example.madverse.feature.quiz.presentation.QuizResultScreen
import com.example.madverse.feature.quiz.presentation.QuizScreen
import com.example.madverse.feature.search.presentation.SearchScreen

@Composable
fun MADVerseNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Discover.route,
        modifier = modifier
    ) {
        composable(route = Screen.Discover.route) {
            DiscoverScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetails.createRoute(movieId))
                }
            )
        }

        composable(route = Screen.Search.route) {
            SearchScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetails.createRoute(movieId))
                }
            )
        }

        composable(route = Screen.Quiz.route) {
            QuizScreen(
                onQuizCompleted = {
                    navController.navigate(Screen.QuizResult.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(route = Screen.QuizResult.route) {
            QuizResultScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetails.createRoute(movieId))
                },
                onRetakeQuiz = {
                    navController.navigate(Screen.Quiz.route) {
                        popUpTo(Screen.Quiz.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onExploreMore = {
                    navController.navigate(Screen.Discover.route) {
                        popUpTo(Screen.Discover.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(route = Screen.Library.route) {
            LibraryScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetails.createRoute(movieId))
                }
            )
        }

        composable(route = Screen.Favorites.route) {
            FavoritesScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetails.createRoute(movieId))
                }
            )
        }

        composable(route = Screen.Watchlist.route) {
            WatchlistScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetails.createRoute(movieId))
                }
            )
        }

        composable(
            route = Screen.MovieDetails.route,
            arguments = listOf(
                navArgument(Screen.ARG_MOVIE_ID) {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getInt(Screen.ARG_MOVIE_ID) ?: 0
            MovieDetailsScreen(
                movieId = movieId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.YouTubePlayer.route,
            arguments = listOf(
                navArgument(Screen.ARG_VIDEO_ID) {
                    type = NavType.StringType
                },
                navArgument(Screen.ARG_MOVIE_TITLE) {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString(Screen.ARG_VIDEO_ID).orEmpty()
            val movieTitle = backStackEntry.arguments?.getString(Screen.ARG_MOVIE_TITLE).orEmpty()
            YouTubePlayerScreen(
                videoId = videoId,
                movieTitle = movieTitle,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

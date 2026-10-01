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
import com.example.madverse.feature.library.presentation.LibraryScreen
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
                    navController.navigate(Screen.Discover.route)
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
    }
}

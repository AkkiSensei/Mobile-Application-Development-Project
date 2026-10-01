package com.example.madverse.core.navigation

/**
 * Type-safe navigation routes and argument keys for MADVerse.
 */
sealed class Screen(val route: String) {
    // Primary bottom bar destinations
    data object Discover : Screen("discover")
    data object Search : Screen("search")
    data object Quiz : Screen("quiz")
    data object Library : Screen("library")

    // Sub-screens and detail destinations
    data object MovieDetails : Screen("movie_details/{$ARG_MOVIE_ID}") {
        fun createRoute(movieId: Int): String = "movie_details/$movieId"
    }

    data object QuizResult : Screen("quiz_result")
    data object SeeAll : Screen("see_all/{$ARG_CATEGORY_TYPE}") {
        fun createRoute(categoryType: String): String = "see_all/$categoryType"
    }

    companion object {
        const val ARG_MOVIE_ID = "movieId"
        const val ARG_CATEGORY_TYPE = "categoryType"
    }
}

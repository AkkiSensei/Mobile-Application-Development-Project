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

    // Personal collection sub-destinations
    data object Favorites : Screen("favorites")
    data object Watchlist : Screen("watchlist")

    // Sub-screens and detail destinations
    data object MovieDetails : Screen("movie_details/{$ARG_MOVIE_ID}") {
        fun createRoute(movieId: Int): String = "movie_details/$movieId"
    }

    data object QuizResult : Screen("quiz_result")
    data object Recommendations : Screen("recommendations")
    data object Genres : Screen("genres")
    data object Comparison : Screen("comparison?movieId={movieId}") {
        fun createRoute(movieId: Int = 0): String = "comparison?movieId=$movieId"
    }
    data object SeeAll : Screen("see_all/{$ARG_CATEGORY_TYPE}") {
        fun createRoute(categoryType: String): String = "see_all/$categoryType"
    }

    data object YouTubePlayer : Screen("youtube_player/{$ARG_VIDEO_ID}?$ARG_MOVIE_TITLE={$ARG_MOVIE_TITLE}") {
        fun createRoute(videoId: String, movieTitle: String = ""): String {
            val sanitizedId = videoId.trim()
            val encodedTitle = if (movieTitle.isBlank()) "" else java.net.URLEncoder.encode(movieTitle, "UTF-8")
            return "youtube_player/$sanitizedId?$ARG_MOVIE_TITLE=$encodedTitle"
        }
    }

    companion object {
        const val ARG_MOVIE_ID = "movieId"
        const val ARG_CATEGORY_TYPE = "categoryType"
        const val ARG_VIDEO_ID = "videoId"
        const val ARG_MOVIE_TITLE = "movieTitle"
    }
}

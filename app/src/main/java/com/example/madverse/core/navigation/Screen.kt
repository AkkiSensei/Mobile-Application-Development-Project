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
    data object FreeMovies : Screen("free_movies")
    data object FreeMoviePlayer : Screen("free_movie/{$ARG_CATALOGUE_ID}") {
        fun createRoute(catalogueId: String): String {
            require(catalogueId.matches(Regex("[a-z0-9-]+")))
            return "free_movie/$catalogueId"
        }
    }
    data object Comparison : Screen("comparison?movieId={movieId}") {
        fun createRoute(movieId: Int = 0): String = "comparison?movieId=$movieId"
    }

    data object YouTubePlayer : Screen("youtube_player/{$ARG_VIDEO_ID}?$ARG_MOVIE_TITLE={$ARG_MOVIE_TITLE}&$ARG_VIDEO_TYPE={$ARG_VIDEO_TYPE}") {
        fun createRoute(videoId: String, movieTitle: String = "", category: com.example.madverse.core.model.VideoCategory = com.example.madverse.core.model.VideoCategory.OTHER): String {
            val sanitizedId = videoId.trim()
            require(com.example.madverse.core.domain.streaming.YouTubeUrlSanitizer.isValidVideoId(sanitizedId))
            val encodedTitle = java.net.URLEncoder.encode(movieTitle, "UTF-8").replace("+", "%20")
            return "youtube_player/$sanitizedId?$ARG_MOVIE_TITLE=$encodedTitle&$ARG_VIDEO_TYPE=${category.name}"
        }
    }

    companion object {
        const val ARG_MOVIE_ID = "movieId"
        const val ARG_VIDEO_ID = "videoId"
        const val ARG_MOVIE_TITLE = "movieTitle"
        const val ARG_VIDEO_TYPE = "videoType"
        const val ARG_CATALOGUE_ID = "catalogueId"
    }
}

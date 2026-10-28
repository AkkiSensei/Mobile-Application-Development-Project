package com.example.madverse

import com.example.madverse.core.model.VideoCategory
import com.example.madverse.core.navigation.NavItem
import com.example.madverse.core.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Explicit verification of the MADVerse Navigation Route Matrix,
 * verifying type-safety, argument keys, parameter encoding, and route contracts.
 */
class NavigationRouteContractTest {

    @Test
    fun primaryDestinations_routesMatchExpectedPatterns() {
        assertEquals("discover", Screen.Discover.route)
        assertEquals("search", Screen.Search.route)
        assertEquals("quiz", Screen.Quiz.route)
        assertEquals("library", Screen.Library.route)
    }

    @Test
    fun navItems_matchPrimaryRoutesAndHaveUniqueProperties() {
        val navItems = NavItem.entries
        assertEquals(4, navItems.size)

        val routes = navItems.map { it.route }.toSet()
        assertEquals(4, routes.size)
        assertTrue(routes.contains(Screen.Discover.route))
        assertTrue(routes.contains(Screen.Search.route))
        assertTrue(routes.contains(Screen.Quiz.route))
        assertTrue(routes.contains(Screen.Library.route))

        val titles = navItems.map { it.title }.toSet()
        assertEquals(4, titles.size)

        navItems.forEach { item ->
            assertNotNull(item.selectedIcon)
            assertNotNull(item.unselectedIcon)
        }
    }

    @Test
    fun subDestinations_routesMatchExpectedPatterns() {
        assertEquals("favorites", Screen.Favorites.route)
        assertEquals("watchlist", Screen.Watchlist.route)
        assertEquals("auth", Screen.Auth.route)
        assertEquals("profile", Screen.Profile.route)
        assertEquals("quiz_result", Screen.QuizResult.route)
        assertEquals("recommendations", Screen.Recommendations.route)
        assertEquals("genres", Screen.Genres.route)
        assertEquals("free_movies", Screen.FreeMovies.route)
    }

    @Test
    fun movieDetails_createRoute_generatesValidRoute() {
        assertEquals("movie_details/{movieId}", Screen.MovieDetails.route)
        assertEquals("movie_details/157336", Screen.MovieDetails.createRoute(157336))
        assertEquals("movie_details/0", Screen.MovieDetails.createRoute(0))
        assertEquals("movie_details/-1", Screen.MovieDetails.createRoute(-1))
    }

    @Test
    fun freeMoviePlayer_createRoute_acceptsValidSlugs() {
        assertEquals("free_movie/{catalogueId}", Screen.FreeMoviePlayer.route)
        assertEquals("free_movie/star-wreck", Screen.FreeMoviePlayer.createRoute("star-wreck"))
        assertEquals("free_movie/big-buck-bunny", Screen.FreeMoviePlayer.createRoute("big-buck-bunny"))
        assertEquals("free_movie/sintel", Screen.FreeMoviePlayer.createRoute("sintel"))
        assertEquals("free_movie/tears-of-steel", Screen.FreeMoviePlayer.createRoute("tears-of-steel"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun freeMoviePlayer_createRoute_rejectsInvalidCatalogueIdWithSpaces() {
        Screen.FreeMoviePlayer.createRoute("star wreck")
    }

    @Test(expected = IllegalArgumentException::class)
    fun freeMoviePlayer_createRoute_rejectsInvalidCatalogueIdWithUppercase() {
        Screen.FreeMoviePlayer.createRoute("Star-Wreck")
    }

    @Test(expected = IllegalArgumentException::class)
    fun freeMoviePlayer_createRoute_rejectsInvalidCatalogueIdWithSpecialChars() {
        Screen.FreeMoviePlayer.createRoute("star_wreck!")
    }

    @Test(expected = IllegalArgumentException::class)
    fun freeMoviePlayer_createRoute_rejectsRawYouTubeVideoIdWithUppercase() {
        // Enforces that raw YouTube IDs like "RHAPXlVq5lk" cannot bypass the slug contract
        Screen.FreeMoviePlayer.createRoute("RHAPXlVq5lk")
    }

    @Test(expected = IllegalArgumentException::class)
    fun freeMoviePlayer_createRoute_rejectsRawYouTubeVideoIdWithUnderscores() {
        // Enforces that raw YouTube IDs with underscores cannot be injected directly
        Screen.FreeMoviePlayer.createRoute("aqz_KE_bpKQ")
    }

    @Test
    fun comparison_createRoute_generatesValidRouteWithDefaults() {
        assertEquals("comparison?movieId={movieId}", Screen.Comparison.route)
        assertEquals("comparison?movieId=0", Screen.Comparison.createRoute())
        assertEquals("comparison?movieId=0", Screen.Comparison.createRoute(0))
        assertEquals("comparison?movieId=550", Screen.Comparison.createRoute(550))
    }

    @Test
    fun youTubePlayer_createRoute_encodesTitleAndPropagatesCategory() {
        val expectedPattern = "youtube_player/{videoId}?movieTitle={movieTitle}&videoType={videoType}"
        assertEquals(expectedPattern, Screen.YouTubePlayer.route)

        val route = Screen.YouTubePlayer.createRoute(
            videoId = "dQw4w9WgXcQ",
            movieTitle = "Spider-Man: Across the Spider-Verse",
            category = VideoCategory.TRAILER
        )
        assertEquals(
            "youtube_player/dQw4w9WgXcQ?movieTitle=Spider-Man%3A%20Across%20the%20Spider-Verse&videoType=TRAILER",
            route
        )
    }

    @Test
    fun youTubePlayer_createRoute_defaultsCategoryToOtherAndTitleToEmpty() {
        val route = Screen.YouTubePlayer.createRoute(videoId = "RHAPXlVq5lk")
        assertEquals("youtube_player/RHAPXlVq5lk?movieTitle=&videoType=OTHER", route)
    }

    @Test(expected = IllegalArgumentException::class)
    fun youTubePlayer_createRoute_rejectsInvalidVideoId() {
        Screen.YouTubePlayer.createRoute("not_a_valid_youtube_id_too_long_or_with_bad_chars!!!")
    }

    @Test(expected = IllegalArgumentException::class)
    fun youTubePlayer_createRoute_rejectsBlankVideoId() {
        Screen.YouTubePlayer.createRoute("   ")
    }

    @Test
    fun argumentConstants_areCorrectAndConsistent() {
        assertEquals("movieId", Screen.ARG_MOVIE_ID)
        assertEquals("videoId", Screen.ARG_VIDEO_ID)
        assertEquals("movieTitle", Screen.ARG_MOVIE_TITLE)
        assertEquals("videoType", Screen.ARG_VIDEO_TYPE)
        assertEquals("catalogueId", Screen.ARG_CATALOGUE_ID)
    }
}

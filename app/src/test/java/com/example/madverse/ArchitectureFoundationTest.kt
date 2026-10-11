package com.example.madverse

import com.example.madverse.core.common.network.ApiConstants
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.database.entity.UserPreferenceEntity
import com.example.madverse.core.domain.recommendation.DeterministicRecommendationEngine
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.UserPreferenceProfile
import com.example.madverse.core.model.VideoTrailer
import com.example.madverse.core.navigation.Screen
import com.example.madverse.core.network.model.MovieDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying architectural core components, routing, contracts, and model mappings.
 */
class ArchitectureFoundationTest {

    @Test
    fun resource_successState_returnsDataAndSuccessFlag() {
        val testData = "Inception"
        val resource: Resource<String> = Resource.Success(testData)

        assertTrue(resource.isSuccess)
        assertEquals(testData, resource.getOrNull())
    }

    @Test
    fun resource_errorState_returnsErrorMessage() {
        val errorMessage = "Network timeout"
        val resource: Resource<String> = Resource.Error(errorMessage)

        assertTrue(resource.isError)
        assertNull(resource.getOrNull())
        assertEquals(errorMessage, (resource as Resource.Error).message)
    }

    @Test
    fun resource_loadingState_returnsLoadingFlag() {
        val resource: Resource<String> = Resource.Loading

        assertTrue(resource.isLoading)
        assertNull(resource.getOrNull())
    }

    @Test
    fun screenRoutes_movieDetails_generatesExpectedParameterizedRoute() {
        val movieId = 550
        val generatedRoute = Screen.MovieDetails.createRoute(movieId)

        assertEquals("movie_details/550", generatedRoute)
    }

    @Test
    fun apiConstants_tmdbBaseUrl_isSecureHttps() {
        assertTrue(ApiConstants.TMDB_BASE_URL.startsWith("https://"))
    }

    @Test
    fun movieDto_toDomain_mapsFieldsAndBuildsCdnUrls() {
        val dto = MovieDto(
            id = 101,
            title = "Interstellar",
            overview = "A team of explorers travel through a wormhole in space.",
            posterPath = "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
            backdropPath = "/rAiYTggFHikMoVo7ExUcw1P8lhf.jpg",
            releaseDate = "2014-11-05",
            voteAverage = 8.6,
            voteCount = 35000,
            genreIds = listOf(12, 18, 878),
            popularity = 120.5
        )

        val domain = dto.toDomain()

        assertEquals(101, domain.id)
        assertEquals("Interstellar", domain.title)
        assertEquals("2014", domain.releaseYear)
        assertEquals("8.6", domain.formattedRating)
        assertTrue(domain.posterUrl?.startsWith(ApiConstants.TMDB_IMAGE_BASE_W500) == true)
        assertTrue(domain.backdropUrl?.startsWith(ApiConstants.TMDB_BACKDROP_BASE_W780) == true)
    }

    @Test
    fun userPreferenceProfile_entityRoundTrip_preservesPreferences() {
        val originalProfile = UserPreferenceProfile(
            selectedGenreIds = setOf(28, 878, 53),
            mood = MovieMood.MIND_BENDING,
            era = MovieEra.MODERN,
            runtimePreference = RuntimePreference.STANDARD,
            preferredLanguage = "en",
            minRatingThreshold = 7.5
        )

        val entity = UserPreferenceEntity.fromDomain(originalProfile)
        val reconstructedProfile = entity.toDomain()

        assertEquals(originalProfile.selectedGenreIds, reconstructedProfile.selectedGenreIds)
        assertEquals(originalProfile.mood, reconstructedProfile.mood)
        assertEquals(originalProfile.era, reconstructedProfile.era)
        assertEquals(originalProfile.runtimePreference, reconstructedProfile.runtimePreference)
        assertEquals(originalProfile.preferredLanguage, reconstructedProfile.preferredLanguage)
        assertEquals(originalProfile.minRatingThreshold, reconstructedProfile.minRatingThreshold, 0.01)
    }

    @Test
    fun videoTrailer_embeddableYouTubeTrailer_validatesProperly() {
        val youtubeTrailer = VideoTrailer(
            id = "vid_1",
            key = "dQw4w9WgXcQ",
            name = "Official Trailer",
            site = "YouTube",
            type = "Trailer",
            isOfficial = true
        )

        assertTrue(youtubeTrailer.isEmbeddableYouTubeTrailer)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", youtubeTrailer.youtubeWatchUrl)

        val vimeoClip = VideoTrailer(
            id = "vid_2",
            key = "12345",
            name = "Featurette",
            site = "Vimeo",
            type = "Behind the Scenes",
            isOfficial = false
        )

        assertFalse(vimeoClip.isEmbeddableYouTubeTrailer)
    }

    @Test
    fun uiState_statesVerifyCorrectly() {
        val loadingState: UiState<String> = UiState.Loading
        val successState: UiState<String> = UiState.Success("Data loaded")
        val emptyState: UiState<String> = UiState.Empty("No items found")
        val errorState: UiState<String> = UiState.Error("Failed to fetch", isOffline = true)

        assertTrue(loadingState is UiState.Loading)
        assertEquals("Data loaded", (successState as UiState.Success).data)
        assertEquals("No items found", (emptyState as UiState.Empty).message)
        assertTrue((errorState as UiState.Error).isOffline)
    }
}

package com.example.madverse

import androidx.lifecycle.SavedStateHandle
import com.example.madverse.core.model.VideoCategory
import com.example.madverse.core.model.VideoTrailer
import com.example.madverse.core.navigation.Screen
import com.example.madverse.feature.player.presentation.YouTubePlayerViewModel
import com.example.madverse.feature.player.util.YouTubeUrlSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests covering YouTube player URL sanitization, video ID validation,
 * navigation route argument formatting, and ViewModel state transitions.
 */
class YouTubePlayerViewModelTest {

    // =========================================================================
    // 1. YouTubeUrlSanitizer & Regex Validation
    // =========================================================================

    @Test
    fun isValidVideoId_acceptsStandard11CharacterIds() {
        assertTrue(YouTubeUrlSanitizer.isValidVideoId("dQw4w9WgXcQ"))
        assertTrue(YouTubeUrlSanitizer.isValidVideoId("M7lc1UVf-VE"))
        assertTrue(YouTubeUrlSanitizer.isValidVideoId("1234567890a"))
        assertTrue(YouTubeUrlSanitizer.isValidVideoId("AbCdEfGhIjK"))
        assertTrue(YouTubeUrlSanitizer.isValidVideoId("__________-"))
    }

    @Test
    fun isValidVideoId_rejectsMalformedOrInvalidIds() {
        assertFalse(YouTubeUrlSanitizer.isValidVideoId(null))
        assertFalse(YouTubeUrlSanitizer.isValidVideoId(""))
        assertFalse(YouTubeUrlSanitizer.isValidVideoId("   "))
        assertFalse(YouTubeUrlSanitizer.isValidVideoId("shortId")) // 7 chars
        assertFalse(YouTubeUrlSanitizer.isValidVideoId("123456789012")) // 12 chars
        assertFalse(YouTubeUrlSanitizer.isValidVideoId("dQw4w9WgXc!")) // invalid symbol
        assertFalse(YouTubeUrlSanitizer.isValidVideoId("<script>"))
        assertFalse(YouTubeUrlSanitizer.isValidVideoId("../test/path"))
    }

    @Test
    fun extractVideoId_extractsFromVariousYouTubeUrlFormats() {
        // Direct ID
        assertEquals("dQw4w9WgXcQ", YouTubeUrlSanitizer.extractVideoId("dQw4w9WgXcQ"))

        // Standard watch URL
        assertEquals("dQw4w9WgXcQ", YouTubeUrlSanitizer.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlSanitizer.extractVideoId("http://youtube.com/watch?v=dQw4w9WgXcQ&feature=share"))

        // youtu.be short URL
        assertEquals("dQw4w9WgXcQ", YouTubeUrlSanitizer.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))

        // Embed URL
        assertEquals("dQw4w9WgXcQ", YouTubeUrlSanitizer.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlSanitizer.extractVideoId("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))

        // Invalid or untrusted URLs
        assertNull(YouTubeUrlSanitizer.extractVideoId("https://evil.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeUrlSanitizer.extractVideoId("invalid_non_url"))
    }

    @Test
    fun buildEmbedUrl_producesSecureHttpsUrlForValidId() {
        val embedUrl = YouTubeUrlSanitizer.buildEmbedUrl("dQw4w9WgXcQ")
        assertNotNull(embedUrl)
        assertTrue(embedUrl!!.startsWith("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))
        assertTrue(embedUrl.contains("enablejsapi=1"))
    }

    @Test
    fun buildEmbedUrl_returnsNullForInvalidId() {
        assertNull(YouTubeUrlSanitizer.buildEmbedUrl("invalid"))
        assertNull(YouTubeUrlSanitizer.buildEmbedUrl(null))
        assertNull(YouTubeUrlSanitizer.buildEmbedUrl("!@#$"))
    }

    @Test
    fun isPermittedUrl_verifiesOriginSecurity() {
        assertTrue(YouTubeUrlSanitizer.isPermittedUrl("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertTrue(YouTubeUrlSanitizer.isPermittedUrl("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))
        assertTrue(YouTubeUrlSanitizer.isPermittedUrl("https://youtube.com/watch?v=dQw4w9WgXcQ"))

        // Reject non-https, non-youtube, file or javascript schemes
        assertFalse(YouTubeUrlSanitizer.isPermittedUrl("http://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertFalse(YouTubeUrlSanitizer.isPermittedUrl("https://attacker.com/embed/dQw4w9WgXcQ"))
        assertFalse(YouTubeUrlSanitizer.isPermittedUrl("javascript:alert(1)"))
        assertFalse(YouTubeUrlSanitizer.isPermittedUrl("file:///android_asset/exploit.html"))
        assertFalse(YouTubeUrlSanitizer.isPermittedUrl(null))
        assertFalse(YouTubeUrlSanitizer.isPermittedUrl(""))
    }

    // =========================================================================
    // 2. Navigation Route & Argument Formatting
    // =========================================================================

    @Test
    fun screenYouTubePlayer_createRoute_formatsArgumentsAndEncodesTitle() {
        val route = Screen.YouTubePlayer.createRoute("dQw4w9WgXcQ", "Fight Club")
        assertEquals("youtube_player/dQw4w9WgXcQ?movieTitle=Fight+Club", route)

        val routeWithSpecialChars = Screen.YouTubePlayer.createRoute("M7lc1UVf-VE", "Spider-Man: & More")
        assertEquals("youtube_player/M7lc1UVf-VE?movieTitle=Spider-Man%3A+%26+More", routeWithSpecialChars)

        val routeWithoutTitle = Screen.YouTubePlayer.createRoute("dQw4w9WgXcQ", "")
        assertEquals("youtube_player/dQw4w9WgXcQ?movieTitle=", routeWithoutTitle)
    }

    // =========================================================================
    // 3. YouTubePlayerViewModel State Flow
    // =========================================================================

    @Test
    fun viewModel_initializedWithValidVideoId_emitsSuccessState() {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Screen.ARG_VIDEO_ID to "dQw4w9WgXcQ",
                Screen.ARG_MOVIE_TITLE to "Inception"
            )
        )

        val viewModel = YouTubePlayerViewModel(savedStateHandle)
        val state = viewModel.uiState.value

        assertEquals("dQw4w9WgXcQ", state.videoId)
        assertEquals("Inception", state.movieTitle)
        assertTrue(state.isValid)
        assertFalse(state.isLoading)
        assertFalse(state.isRestricted)
        assertNull(state.errorMessage)
        assertNotNull(state.embedUrl)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", state.webWatchUrl)
    }

    @Test
    fun viewModel_initializedWithUrlEncodedTitle_decodesCorrectly() {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Screen.ARG_VIDEO_ID to "dQw4w9WgXcQ",
                Screen.ARG_MOVIE_TITLE to "Interstellar%3A+The+Movie"
            )
        )

        val viewModel = YouTubePlayerViewModel(savedStateHandle)
        assertEquals("Interstellar: The Movie", viewModel.uiState.value.movieTitle)
    }

    @Test
    fun viewModel_initializedWithInvalidVideoId_emitsErrorState() {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Screen.ARG_VIDEO_ID to "invalid_id",
                Screen.ARG_MOVIE_TITLE to "Unknown"
            )
        )

        val viewModel = YouTubePlayerViewModel(savedStateHandle)
        val state = viewModel.uiState.value

        assertFalse(state.isValid)
        assertFalse(state.isLoading)
        assertNull(state.embedUrl)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Invalid YouTube video ID"))
    }

    @Test
    fun viewModel_initializedWithEmptyVideoId_emitsNoVideoError() {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Screen.ARG_VIDEO_ID to "",
                Screen.ARG_MOVIE_TITLE to ""
            )
        )

        val viewModel = YouTubePlayerViewModel(savedStateHandle)
        val state = viewModel.uiState.value

        assertFalse(state.isValid)
        assertFalse(state.isLoading)
        assertEquals("No video ID provided.", state.errorMessage)
    }

    @Test
    fun viewModel_updateControlsAndFallbackTransitions() {
        val savedStateHandle = SavedStateHandle(
            mapOf(Screen.ARG_VIDEO_ID to "dQw4w9WgXcQ")
        )
        val viewModel = YouTubePlayerViewModel(savedStateHandle)

        // Set playing
        viewModel.setPlaying(true)
        assertTrue(viewModel.uiState.value.isPlaying)

        // Toggle full screen
        assertFalse(viewModel.uiState.value.isFullScreen)
        viewModel.toggleFullScreen()
        assertTrue(viewModel.uiState.value.isFullScreen)

        // Mark restricted
        viewModel.markRestricted(true)
        assertTrue(viewModel.uiState.value.isRestricted)

        // Set error
        viewModel.setPlayerError("Network timed out")
        assertEquals("Network timed out", viewModel.uiState.value.errorMessage)

        // Set new video
        viewModel.setVideo("M7lc1UVf-VE", "The Matrix")
        assertEquals("M7lc1UVf-VE", viewModel.uiState.value.videoId)
        assertEquals("The Matrix", viewModel.uiState.value.movieTitle)
        assertTrue(viewModel.uiState.value.isValid)
    }

    // =========================================================================
    // 4. Full-Length Movie vs Trailer Handling
    // =========================================================================

    @Test
    fun videoTrailer_categorization_differentiatesTrailersClipsAndFullMovies() {
        val trailer = VideoTrailer(
            id = "1",
            key = "dQw4w9WgXcQ",
            name = "Official Trailer",
            site = "YouTube",
            type = "Trailer",
            isOfficial = true
        )
        assertEquals(VideoCategory.TRAILER, trailer.category)
        assertTrue(trailer.isTrailerOrTeaser)
        assertFalse(trailer.isFullLengthMovie)
        assertTrue(trailer.isPlayableInApp)

        val teaser = VideoTrailer(
            id = "2",
            key = "dQw4w9WgXcQ",
            name = "Teaser",
            site = "YouTube",
            type = "Teaser",
            isOfficial = true
        )
        assertEquals(VideoCategory.TEASER, teaser.category)
        assertTrue(teaser.isTrailerOrTeaser)

        val clip = VideoTrailer(
            id = "3",
            key = "dQw4w9WgXcQ",
            name = "Opening Scene",
            site = "YouTube",
            type = "Clip",
            isOfficial = true
        )
        assertEquals(VideoCategory.CLIP, clip.category)
        assertFalse(clip.isTrailerOrTeaser)
        assertFalse(clip.isFullLengthMovie)

        // Commercial Full Movie (unauthorized for free full-length in-app playback)
        val commercialFullMovie = VideoTrailer(
            id = "4",
            key = "dQw4w9WgXcQ",
            name = "Inception Full Movie",
            site = "YouTube",
            type = "Full Movie",
            isOfficial = false,
            isAuthorizedFullLength = false
        )
        assertEquals(VideoCategory.FULL_MOVIE, commercialFullMovie.category)
        assertTrue(commercialFullMovie.isFullLengthMovie)
        assertFalse(commercialFullMovie.isPlayableInApp) // Gracefully restricted to trailers

        // Authorized Public Domain Full Movie
        val publicDomainFilm = VideoTrailer(
            id = "5",
            key = "dQw4w9WgXcQ",
            name = "Night of the Living Dead (1968)",
            site = "YouTube",
            type = "Feature Film",
            isOfficial = true,
            isAuthorizedFullLength = true
        )
        assertEquals(VideoCategory.FULL_MOVIE, publicDomainFilm.category)
        assertTrue(publicDomainFilm.isFullLengthMovie)
        assertTrue(publicDomainFilm.isPlayableInApp) // Authorized
    }

    @Test
    fun viewModel_fullMovieTitle_marksRestrictedUnlessAuthorized() {
        // Commercial full movie without free license
        val commercialHandle = SavedStateHandle(
            mapOf(
                Screen.ARG_VIDEO_ID to "dQw4w9WgXcQ",
                Screen.ARG_MOVIE_TITLE to "Avengers: Endgame (Full Movie)"
            )
        )
        val commercialVm = YouTubePlayerViewModel(commercialHandle)
        assertTrue(commercialVm.uiState.value.isFullLengthMovie)
        assertFalse(commercialVm.uiState.value.isAuthorizedFullLength)
        assertTrue(commercialVm.uiState.value.isRestricted)

        // Authorized Public Domain movie
        val publicDomainHandle = SavedStateHandle(
            mapOf(
                Screen.ARG_VIDEO_ID to "dQw4w9WgXcQ",
                Screen.ARG_MOVIE_TITLE to "Nosferatu (1922) Public Domain Full Movie"
            )
        )
        val pdVm = YouTubePlayerViewModel(publicDomainHandle)
        assertTrue(pdVm.uiState.value.isFullLengthMovie)
        assertTrue(pdVm.uiState.value.isAuthorizedFullLength)
        assertFalse(pdVm.uiState.value.isRestricted)
    }
}

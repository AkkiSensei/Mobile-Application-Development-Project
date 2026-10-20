package com.example.madverse

import androidx.lifecycle.SavedStateHandle
import com.example.madverse.core.data.repository.FreeMovieRepository
import com.example.madverse.core.model.AuthorizationBasis
import com.example.madverse.core.model.FilmFormat
import com.example.madverse.core.model.FreeMovie
import com.example.madverse.core.model.VideoCategory
import com.example.madverse.core.model.VideoTrailer
import com.example.madverse.core.navigation.Screen
import com.example.madverse.feature.player.presentation.*
import com.example.madverse.core.domain.streaming.YouTubeUrlSanitizer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import org.junit.Assert.*
import org.junit.Test

class YouTubePlayerViewModelTest {
    private val id = "M7lc1UVf-VE" // YouTube's official IFrame API example; never a rights fixture.
    private fun player(videoId: String = id, title: String = "Trailer", type: String = "TRAILER") =
        YouTubePlayerViewModel(SavedStateHandle(mapOf(Screen.ARG_VIDEO_ID to videoId,
            Screen.ARG_MOVIE_TITLE to title, Screen.ARG_VIDEO_TYPE to type)), FreeMovieRepository())

    @Test fun validIdsAreExactlyElevenSafeCharacters() {
        listOf(id, "dQw4w9WgXcQ", "__________-").forEach { assertTrue(YouTubeUrlSanitizer.isValidVideoId(it)) }
        listOf(null, "", "short", "123456789012", "<script>", " $id ", "../test/path").forEach {
            assertFalse(YouTubeUrlSanitizer.isValidVideoId(it))
        }
    }
    @Test fun supportedUrlsResolveEntireId() {
        listOf(id, "https://www.youtube.com/watch?v=$id", "http://youtube.com/watch?v=$id&feature=share",
            "https://youtu.be/$id", "https://m.youtube.com/watch?v=$id", "https://www.youtube.com/embed/$id",
            "https://www.youtube-nocookie.com/embed/$id", "https://www.youtube.com/shorts/$id").forEach {
            assertEquals(it, id, YouTubeUrlSanitizer.extractVideoId(it))
        }
    }
    @Test fun hostileAndAmbiguousUrlsAreRejected() {
        listOf("https://evil.com/youtube.com/watch?v=$id", "https://youtube.com.evil.com/watch?v=$id",
            "https://youtube.com@evil.com/watch?v=$id", "https://user@youtube.com/watch?v=$id",
            "https://youtube.com:444/watch?v=$id", "https://youtu.be/${id}extra", "https://youtu.be/$id/extra",
            "https://youtube.com/watch?notv=$id", "https://youtube.com/watch?v=$id&v=dQw4w9WgXcQ",
            "javascript:https://youtube.com/watch?v=$id", "https://youtube.com/watch?v=$id#fragment",
            "file:///youtube.com/watch?v=$id", "https://youtube.com/embed/$id/extra").forEach {
            assertNull(it, YouTubeUrlSanitizer.extractVideoId(it))
        }
    }
    @Test fun fallbackAndEmbedUrlsNeverContainInvalidInputOrAutoplay() {
        assertEquals("", YouTubeUrlSanitizer.buildWebWatchUrl("bad&admin=true"))
        assertNull(YouTubeUrlSanitizer.buildEmbedUrl(null))
        assertTrue(YouTubeUrlSanitizer.buildEmbedUrl(id)!!.contains("autoplay=0"))
        assertEquals("https://www.youtube.com/watch?v=$id", YouTubeUrlSanitizer.buildWebWatchUrl(id))
        assertFalse(YouTubeUrlSanitizer.isPermittedUrl("http://youtube.com/watch?v=$id"))
        assertTrue(YouTubeUrlSanitizer.isPermittedUrl("https://youtube.com/watch?v=$id"))
    }
    @Test fun routeEncodesSpecialCharactersOnceAndIncludesCategory() {
        val title = "A + B & 100% / #?"
        val route = Screen.YouTubePlayer.createRoute(id, title, VideoCategory.TEASER)
        assertTrue(route.contains("movieTitle=A%20%2B%20B%20%26%20100%25%20%2F%20%23%3F"))
        assertTrue(route.endsWith("videoType=TEASER"))
        assertEquals(title, player(title = title).uiState.value.movieTitle)
    }
    @Test(expected = IllegalArgumentException::class) fun routeRejectsInvalidVideoId() {
        Screen.YouTubePlayer.createRoute("../bad?x=1")
    }
    @Test fun validVideoStartsInitializingAndOnlyCallbackMakesItReady() {
        val vm = player()
        assertTrue(vm.uiState.value.isLoading); assertFalse(vm.uiState.value.isReady)
        assertTrue(vm.uiState.value.canEmbed)
        vm.onReady(vm.uiState.value.attempt)
        assertTrue(vm.uiState.value.isReady); assertFalse(vm.uiState.value.isLoading)
    }
    @Test fun invalidIdHasNoPlayerOrExternalLink() {
        val state = player("invalid").uiState.value
        assertFalse(state.canEmbed); assertFalse(state.isLoading)
        assertEquals("", state.webWatchUrl); assertNotNull(state.errorMessage)
    }
    @Test fun titleNeverGrantsFullFilmRights() {
        val state = player(title = "Public Domain Free Full Movie", type = "FULL_MOVIE").uiState.value
        assertTrue(state.isRestricted); assertFalse(state.isAuthorizedFullLength); assertFalse(state.canEmbed)
        assertTrue(state.webWatchUrl.isNotBlank())
    }
    @Test fun unknownTypeDoesNotAssumeTrailerEligibility() {
        assertFalse(player(type = "OTHER").uiState.value.canEmbed)
        assertFalse(player(type = "malformed").uiState.value.canEmbed)
    }
    @Test fun catalogueRouteResolvesTrustedFilmNotInjectedVideoOrTitle() {
        val repo = FreeMovieRepository()
        val film = repo.getMovies().first()
        val vm = YouTubePlayerViewModel(SavedStateHandle(mapOf(Screen.ARG_CATALOGUE_ID to film.catalogueId,
            Screen.ARG_VIDEO_ID to id, Screen.ARG_MOVIE_TITLE to "Fake title")), repo)
        assertEquals(film.videoId, vm.uiState.value.videoId)
        assertEquals(film.title, vm.uiState.value.movieTitle)
        assertTrue(vm.uiState.value.isAuthorizedFullLength)
        assertTrue(vm.uiState.value.canEmbed)
    }
    @Test fun unknownCatalogueEntryHasNoFallbackToArbitraryVideo() {
        val vm = YouTubePlayerViewModel(SavedStateHandle(mapOf(Screen.ARG_CATALOGUE_ID to "missing", Screen.ARG_VIDEO_ID to id)), FreeMovieRepository())
        assertFalse(vm.uiState.value.canEmbed); assertEquals("", vm.uiState.value.webWatchUrl)
    }
    @Test fun timeoutAndRetryCreateFreshAttemptAndIgnoreOldCallbacks() {
        val vm = player(); val old = vm.uiState.value.attempt
        vm.initializationTimedOut(old)
        assertFalse(vm.uiState.value.canEmbed); assertTrue(vm.uiState.value.retryable)
        vm.retry(); val fresh = vm.uiState.value.attempt
        assertTrue(fresh > old); assertTrue(vm.uiState.value.isLoading)
        vm.onReady(old); vm.fail(old, PlayerFailure.HTML5)
        assertTrue(vm.uiState.value.isLoading); assertNull(vm.uiState.value.errorMessage)
        vm.onReady(fresh); vm.initializationTimedOut(fresh)
        assertTrue(vm.uiState.value.isReady)
    }
    @Test fun changingVideoClearsOldPlaybackState() {
        val vm = player(); val old = vm.uiState.value.attempt
        vm.onReady(old); vm.setPlaying(old, true)
        vm.setVideo("dQw4w9WgXcQ", "Another trailer", VideoCategory.TRAILER)
        vm.setPlaying(old, true)
        assertFalse(vm.uiState.value.isPlaying); assertFalse(vm.uiState.value.isReady)
    }
    @Test fun errorClassesRemainDistinctAndEmbeddingFailureIsNotCopyrightClaim() {
        assertEquals(PlayerFailure.CLIENT_IDENTITY, PlayerFailure.from(PlayerConstants.PlayerError.REQUEST_MISSING_HTTP_REFERER))
        assertEquals(PlayerFailure.EMBEDDING_DISABLED, PlayerFailure.from(PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER))
        assertEquals(PlayerFailure.UNAVAILABLE, PlayerFailure.from(PlayerConstants.PlayerError.VIDEO_NOT_FOUND))
        assertEquals(PlayerFailure.HTML5, PlayerFailure.from(PlayerConstants.PlayerError.HTML_5_PLAYER))
        assertEquals(PlayerFailure.INVALID_VIDEO, PlayerFailure.from(PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST))
        assertEquals(PlayerFailure.UNKNOWN, PlayerFailure.from(PlayerConstants.PlayerError.UNKNOWN))
        val vm = player(); vm.fail(vm.uiState.value.attempt, PlayerFailure.EMBEDDING_DISABLED)
        assertFalse(vm.uiState.value.retryable); assertFalse(vm.uiState.value.canEmbed)
        assertFalse(vm.uiState.value.errorMessage!!.contains("copyright", true))
    }
    @Test fun fullFilmBooleanInTmdbModelCannotGrantPlayback() {
        val film = VideoTrailer("1", id, "Free Public Domain Film", "YouTube", "Full Movie", true, true)
        assertFalse(film.isPlayableInApp)
        val trailer = film.copy(type = "Trailer", name = "Trailer")
        assertTrue(trailer.isPlayableInApp); assertTrue(trailer.isEmbeddableYouTubeTrailer)
        assertFalse(trailer.copy(site = "Vimeo").isPlayableInApp)
        assertEquals("", trailer.copy(site = "Vimeo").youtubeWatchUrl)
        assertFalse(trailer.copy(key = "bad").isPlayableInApp)
    }
    @Test fun failedExternalLaunchIsVisibleWithoutChangingReadiness() {
        val vm = player(); vm.externalFailed()
        assertNotNull(vm.uiState.value.externalError)
        assertTrue(vm.uiState.value.isLoading)
    }

    private fun cataloguePlayer(film: FreeMovie) = YouTubePlayerViewModel(
        SavedStateHandle(mapOf(Screen.ARG_CATALOGUE_ID to film.catalogueId)),
        FreeMovieRepository(listOf(film)))

    @Test fun authorizedFeatureRetainsFullLengthClassification() {
        val film = FreeMovieRepository().find("star-wreck")!!
        val state = cataloguePlayer(film).uiState.value
        assertEquals(FilmFormat.FEATURE_FILM, film.format)
        assertTrue(state.isFullLengthMovie)
        assertTrue(state.isAuthorizedFullLength)
        assertTrue(state.canEmbed)
        assertEquals("RHAPXlVq5lk", state.videoId)
    }

    private fun assertPlayableShort(catalogueId: String, videoId: String) {
        val film = FreeMovieRepository().find(catalogueId)!!
        val state = cataloguePlayer(film).uiState.value
        assertEquals(FilmFormat.SHORT_FILM, film.format)
        assertFalse(state.isFullLengthMovie)
        assertFalse(state.isAuthorizedFullLength)
        assertFalse(state.isRestricted)
        assertTrue(state.canEmbed)
        assertTrue(state.isLoading)
        assertFalse(state.isPlaying)
        assertEquals(videoId, state.videoId)
        assertEquals(FilmFormat.SHORT_FILM.label, state.contentLabel)
    }
    @Test fun bigBuckBunnyRemainsPlayableAsAShort() = assertPlayableShort("big-buck-bunny", "aqz-KE-bpKQ")
    @Test fun sintelRemainsPlayableAsAShort() = assertPlayableShort("sintel", "eRsGyueVLvQ")
    @Test fun tearsOfSteelRemainsPlayableAsAShort() = assertPlayableShort("tears-of-steel", "R6MlUcmOul8")

    @Test fun unknownAuthorizationBlocksFeaturesAndShortsDespiteFreeMovieTitle() {
        FilmFormat.entries.forEach { format ->
            val film = FreeMovieRepository().getMovies().first().copy(
                title = "Free Movie", format = format, authorization = AuthorizationBasis.UNKNOWN)
            val vm = cataloguePlayer(film)
            assertFalse(vm.uiState.value.isAuthorizedFullLength)
            assertFalse(vm.uiState.value.canEmbed)
            assertTrue(vm.uiState.value.isRestricted)
            assertFalse(vm.uiState.value.isLoading)
            assertNotNull(vm.uiState.value.errorMessage)
            vm.onReady(0); vm.setPlaying(0, true); vm.retry()
            assertFalse(vm.uiState.value.isReady)
            assertFalse(vm.uiState.value.isPlaying)
            assertFalse(vm.uiState.value.canEmbed)
        }
    }

    @Test fun nonYouTubeCatalogueFilmNeverReachesEmbeddedPlayer() {
        val film = FreeMovieRepository().getMovies().first().copy(site = "Vimeo")
        val state = cataloguePlayer(film).uiState.value
        assertFalse(state.canEmbed)
        assertFalse(state.isValid)
        assertFalse(state.isLoading)
        assertEquals("", state.videoId)
        assertEquals("", state.webWatchUrl)
    }

    @Test fun everyCatalogueRoutePreservesTheExactTrustedVideoId() {
        val repo = FreeMovieRepository()
        repo.getMovies().forEach { film ->
            val route = Screen.FreeMoviePlayer.createRoute(film.catalogueId)
            val vm = YouTubePlayerViewModel(SavedStateHandle(mapOf(
                Screen.ARG_CATALOGUE_ID to route.substringAfter("free_movie/"),
                Screen.ARG_VIDEO_ID to id, Screen.ARG_MOVIE_TITLE to "Free Movie",
                "isAuthorizedFullLength" to true, "isFullLengthMovie" to true)), repo)
            assertEquals(film.videoId, vm.uiState.value.videoId)
            assertEquals(film.title, vm.uiState.value.movieTitle)
            assertEquals("https://www.youtube.com/watch?v=${film.videoId}", vm.uiState.value.webWatchUrl)
            assertEquals(film.format == FilmFormat.FEATURE_FILM, vm.uiState.value.isAuthorizedFullLength)
        }
    }

    @Test fun injectedAuthorizationFlagsCannotAuthorizeAnUnknownCatalogueId() {
        val state = YouTubePlayerViewModel(SavedStateHandle(mapOf(
            Screen.ARG_CATALOGUE_ID to "unknown", Screen.ARG_VIDEO_ID to id,
            Screen.ARG_MOVIE_TITLE to "Free Movie", "isAuthorizedFullLength" to true,
            "isFullLengthMovie" to true)), FreeMovieRepository()).uiState.value
        assertFalse(state.isAuthorizedFullLength)
        assertFalse(state.canEmbed)
        assertFalse(state.isLoading)
        assertEquals("", state.videoId)
    }

    @Test fun promotionalRoutePreservesExactValidatedId() {
        val route = Screen.YouTubePlayer.createRoute(id, "Trailer", VideoCategory.TRAILER)
        val routedId = route.substringAfter("youtube_player/").substringBefore('?')
        assertEquals(id, routedId)
        assertEquals(id, player(routedId).uiState.value.videoId)
    }
}

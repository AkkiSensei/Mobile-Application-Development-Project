package com.example.madverse

import com.example.madverse.core.common.ui.UiState
import com.example.madverse.core.data.repository.FreeMovieRepository
import com.example.madverse.core.model.FilmFormat
import com.example.madverse.core.navigation.Screen
import com.example.madverse.feature.freemovies.presentation.FreeMoviesViewModel
import com.example.madverse.core.domain.streaming.YouTubeUrlSanitizer
import org.junit.Assert.*
import org.junit.Test

class FreeMovieRepositoryTest {
    private val repository = FreeMovieRepository()
    @Test fun catalogueContainsVerifiedSourcesAndHonestFormats() {
        assertEquals(4, repository.getMovies().size)
        assertEquals(1, repository.getMovies().count { it.format == FilmFormat.FEATURE_FILM })
        repository.getMovies().forEach {
            assertTrue(it.sourceUrl.startsWith("https://"))
            assertTrue(it.regionNotes.contains("device"))
            assertTrue(YouTubeUrlSanitizer.isValidVideoId(it.videoId))
            assertTrue(YouTubeUrlSanitizer.isPermittedUrl(YouTubeUrlSanitizer.buildWebWatchUrl(it.videoId)))
        }
    }
    @Test fun unknownCatalogueItemCannotResolveToArbitraryVideo() {
        assertNull(repository.find("M7lc1UVf-VE"))
        assertNull(repository.find("Free Public Domain Full Movie"))
    }
    @Test fun catalogueRoutePassesOnlyStableId() {
        val film = repository.getMovies().first()
        assertEquals("free_movie/${film.catalogueId}", Screen.FreeMoviePlayer.createRoute(film.catalogueId))
        assertEquals(film, repository.find(film.catalogueId))
        assertTrue(FreeMoviesViewModel(repository).state.value is UiState.Success)
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateCatalogueIdsAreRejected() {
        val movie = repository.getMovies().first()
        FreeMovieRepository.validateCatalogue(listOf(movie, movie.copy(videoId = "M7lc1UVf-VE")))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateVideosAreRejected() {
        val movie = repository.getMovies().first()
        FreeMovieRepository.validateCatalogue(listOf(movie, movie.copy(catalogueId = "duplicate")))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidVideoIdIsRejected() {
        FreeMovieRepository.validateCatalogue(listOf(repository.getMovies().first().copy(videoId = "invalid")))
    }
    @Test(expected = IllegalArgumentException::class) fun missingRightsEvidenceIsRejected() {
        FreeMovieRepository.validateCatalogue(listOf(repository.getMovies().first().copy(rightsUrl = "")))
    }

    @Test fun catalogueFormatsMatchTheFourReviewedFilms() {
        assertEquals(mapOf("star-wreck" to FilmFormat.FEATURE_FILM,
            "big-buck-bunny" to FilmFormat.SHORT_FILM, "sintel" to FilmFormat.SHORT_FILM,
            "tears-of-steel" to FilmFormat.SHORT_FILM),
            repository.getMovies().associate { it.catalogueId to it.format })
        assertTrue(repository.getMovies().all { it.isAuthorized && it.isEligibleForYouTube })
    }
    @Test(expected = IllegalArgumentException::class) fun invalidEntryCannotEnterRepository() {
        FreeMovieRepository(listOf(repository.getMovies().first().copy(videoId = "bad")))
    }
    @Test fun callerCannotMutateTheTrustedCatalogue() {
        val input = repository.getMovies().toMutableList()
        val isolated = FreeMovieRepository(input)
        input.clear()
        assertEquals(4, isolated.getMovies().size)
        (isolated.getMovies() as MutableList).clear()
        assertEquals(4, isolated.getMovies().size)
    }
}

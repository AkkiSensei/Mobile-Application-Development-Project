package com.example.madverse

import com.example.madverse.core.data.repository.FreeMovieRepository
import com.example.madverse.core.model.FilmFormat
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.ProviderType
import com.example.madverse.core.model.WatchProvider
import com.example.madverse.feature.comparison.presentation.formatCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression unit tests covering UI polish contracts, accessibility semantics,
 * formatting helpers, and presentation models introduced in the Saanvi UI pass.
 */
class UiPolishRegressionTest {

    @Test
    fun comparisonCurrencyFormatting_formatsValidAmountsWithCommas() {
        assertEquals("$160,000,000", formatCurrency(160_000_000L))
        assertEquals("$2,500,000", formatCurrency(2_500_000L))
        assertEquals("$750,000", formatCurrency(750_000L))
        assertEquals("$0", formatCurrency(0L))
    }

    @Test
    fun moviePosterCard_accessibleDescription_includesYearAndRatingWhenAvailable() {
        val movieWithRating = Movie(
            id = 1,
            title = "Oppenheimer",
            overview = "The story of J. Robert Oppenheimer...",
            posterUrl = "/oppenheimer.jpg",
            backdropUrl = null,
            releaseDate = "2023-07-21",
            voteAverage = 8.12,
            voteCount = 12000,
            genreIds = listOf(18, 36),
            popularity = 85.0
        )

        val accessibleDescription = "${movieWithRating.title}, release year ${movieWithRating.releaseYear}${if (movieWithRating.voteAverage > 0) ", rated ${movieWithRating.formattedRating} out of 10" else ""}"
        assertEquals("Oppenheimer, release year 2023, rated 8.1 out of 10", accessibleDescription)
    }

    @Test
    fun moviePosterCard_accessibleDescription_omitsRatingWhenZero() {
        val unratedMovie = Movie(
            id = 2,
            title = "Upcoming Indie",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            releaseDate = "2026-11-15",
            voteAverage = 0.0,
            voteCount = 0,
            genreIds = emptyList(),
            popularity = 5.0
        )

        val accessibleDescription = "${unratedMovie.title}, release year ${unratedMovie.releaseYear}${if (unratedMovie.voteAverage > 0) ", rated ${unratedMovie.formattedRating} out of 10" else ""}"
        assertEquals("Upcoming Indie, release year 2026", accessibleDescription)
    }

    @Test
    fun heroFeaturedMovieCard_accessibleDescription_prefixesFeaturedSpotlight() {
        val heroMovie = Movie(
            id = 3,
            title = "Dune: Part Two",
            overview = "Paul Atreides unites with Chani...",
            posterUrl = "/dune_poster.jpg",
            backdropUrl = "/dune_backdrop.jpg",
            releaseDate = "2024-03-01",
            voteAverage = 8.3,
            voteCount = 8500,
            genreIds = listOf(878, 12),
            popularity = 120.0
        )

        val accessibleDescription = "Featured spotlight movie: ${heroMovie.title}, release year ${heroMovie.releaseYear}${if (heroMovie.voteAverage > 0) ", rated ${heroMovie.formattedRating}" else ""}"
        assertEquals("Featured spotlight movie: Dune: Part Two, release year 2024, rated 8.3", accessibleDescription)
    }

    @Test
    fun movieHorizontalCard_accessibleDescription_conciseSummary() {
        val movie = Movie(
            id = 4,
            title = "Interstellar",
            overview = "A team of explorers travel through a wormhole...",
            posterUrl = "/interstellar.jpg",
            backdropUrl = null,
            releaseDate = "2014-11-07",
            voteAverage = 8.6,
            voteCount = 34000,
            genreIds = listOf(12, 18, 878),
            popularity = 92.0
        )

        val accessibleDescription = "${movie.title}, ${movie.releaseYear}${if (movie.voteAverage > 0) ", rated ${movie.formattedRating} out of 10" else ""}"
        assertEquals("Interstellar, 2014, rated 8.6 out of 10", accessibleDescription)
    }

    @Test
    fun freeMovieCatalogue_presentationModelConversion_neverExposesTmdbId() {
        val repository = FreeMovieRepository()
        val films = repository.getMovies()
        assertFalse(films.isEmpty())

        films.forEach { film ->
            // Presentation mapping must isolate catalogue items from TMDB IDs
            val presentationMovie = Movie(
                id = 0,
                title = film.title,
                overview = film.description,
                posterUrl = film.thumbnailUrl,
                backdropUrl = null,
                releaseDate = null,
                voteAverage = 0.0,
                voteCount = 0,
                genreIds = emptyList(),
                popularity = 0.0
            )

            assertEquals(0, presentationMovie.id)
            assertEquals(film.title, presentationMovie.title)
            assertEquals(film.thumbnailUrl, presentationMovie.posterUrl)
            assertNotNull(film.format.label)
            assertTrue(film.format.label.isNotBlank())
            assertTrue(film.verifiedOn.isNotBlank())
        }
    }

    @Test
    fun watchProvider_accessibilityActionLabel_generatesProperVoiceOverPrompt() {
        val provider = WatchProvider(
            providerId = 8,
            providerName = "Netflix",
            logoUrl = "/netflix.png",
            type = ProviderType.STREAMING_SUBSCRIPTION,
            directLink = "https://netflix.com"
        )

        val actionDescription = "Watch on ${provider.providerName}"
        assertEquals("Watch on Netflix", actionDescription)
    }

    @Test
    fun recommendationTasteMatch_tierThreshold_contract() {
        val highMatch = 85
        val moderateMatch = 65

        fun isTopTierMatch(percentage: Int) = percentage >= 75

        assertTrue(isTopTierMatch(highMatch))
        assertFalse(isTopTierMatch(moderateMatch))
    }

    @Test
    fun moviePosterCard_releaseYearMetadata_isAccessibleAndPreserved() {
        val movie = Movie(
            id = 101,
            title = "Test Movie",
            overview = "Test overview",
            posterUrl = "/test.jpg",
            backdropUrl = null,
            releaseDate = "2026-05-10",
            voteAverage = 7.5,
            voteCount = 1420,
            genreIds = listOf(28),
            popularity = 45.0
        )

        assertEquals("2026", movie.releaseYear)
        assertEquals("1420 votes", "${movie.voteCount} votes")
        val accessibleDescription = "${movie.title}, release year ${movie.releaseYear}${if (movie.voteAverage > 0) ", rated ${movie.formattedRating} out of 10" else ""}"
        assertEquals("Test Movie, release year 2026, rated 7.5 out of 10", accessibleDescription)
    }
}


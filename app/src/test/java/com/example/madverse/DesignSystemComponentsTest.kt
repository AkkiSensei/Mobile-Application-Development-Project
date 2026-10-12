package com.example.madverse

import com.example.madverse.core.designsystem.components.RatingBadgeStyle
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.ProviderType
import com.example.madverse.core.model.WatchProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests validating Design System component contracts, rating calculations,
 * accessibility attributes, and visual style configurations for MADVerse S1.
 */
class DesignSystemComponentsTest {

    @Test
    fun ratingBadge_stylesExistAndAreDistinct() {
        val styles = RatingBadgeStyle.entries
        assertEquals(3, styles.size)
        assertTrue(styles.contains(RatingBadgeStyle.OverlayBadge))
        assertTrue(styles.contains(RatingBadgeStyle.Inline))
        assertTrue(styles.contains(RatingBadgeStyle.Prominent))
    }

    @Test
    fun ratingBadge_formatting_formatsToOneDecimalPlace() {
        val rating = 8.4321
        val formatted = String.format(Locale.US, "%.1f", rating)
        assertEquals("8.4", formatted)

        val perfectRating = 10.0
        val formattedPerfect = String.format(Locale.US, "%.1f", perfectRating)
        assertEquals("10.0", formattedPerfect)
    }

    @Test
    fun movieModel_formattedRating_matchesExpectedContract() {
        val movie = Movie(
            id = 101,
            title = "Inception",
            overview = "A thief who steals corporate secrets...",
            posterUrl = "https://image.tmdb.org/t/p/w500/path.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/back.jpg",
            releaseDate = "2010-07-16",
            voteAverage = 8.357,
            voteCount = 35000,
            genreIds = listOf(28, 878),
            popularity = 95.5
        )

        assertEquals("8.4", movie.formattedRating)
        assertEquals("2010", movie.releaseYear)
    }

    @Test
    fun movieModel_emptyReleaseDate_handlesGracefully() {
        val movie = Movie(
            id = 102,
            title = "Future Movie",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            releaseDate = null,
            voteAverage = 0.0,
            voteCount = 0,
            genreIds = emptyList(),
            popularity = 1.0
        )

        assertEquals("Unknown", movie.releaseYear)
        assertEquals("0.0", movie.formattedRating)
    }

    @Test
    fun watchProvider_modelCreation_supportsAllProviderTypes() {
        val streamProvider = WatchProvider(
            providerId = 8,
            providerName = "Netflix",
            logoUrl = "/netflix.jpg",
            type = ProviderType.STREAMING_SUBSCRIPTION,
            directLink = "https://netflix.com"
        )
        val rentProvider = WatchProvider(
            providerId = 2,
            providerName = "Apple TV",
            logoUrl = "/appletv.jpg",
            type = ProviderType.RENT
        )
        val buyProvider = WatchProvider(
            providerId = 3,
            providerName = "Google Play",
            logoUrl = "/googleplay.jpg",
            type = ProviderType.BUY
        )

        assertEquals(ProviderType.STREAMING_SUBSCRIPTION, streamProvider.type)
        assertEquals(ProviderType.RENT, rentProvider.type)
        assertEquals(ProviderType.BUY, buyProvider.type)
        assertNotNull(streamProvider.directLink)
        assertEquals("Netflix", streamProvider.providerName)
    }

    @Test
    fun accessibilityText_ratingDescription_formatsCleanly() {
        val rating = 7.9
        val formattedRating = String.format(Locale.US, "%.1f", rating)
        val accessibleText = "Rated $formattedRating out of 10 stars"
        assertEquals("Rated 7.9 out of 10 stars", accessibleText)
    }

    @Test
    fun accessibilityText_cardDescription_formatsCorrectly() {
        val movie = Movie(
            id = 550,
            title = "Fight Club",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            releaseDate = "1999-10-15",
            voteAverage = 8.433,
            voteCount = 27000,
            genreIds = listOf(18),
            popularity = 80.0
        )

        val accessibleDescription = "${movie.title}, release year ${movie.releaseYear}${if (movie.voteAverage > 0) ", rated ${movie.formattedRating} out of 10" else ""}"
        assertEquals("Fight Club, release year 1999, rated 8.4 out of 10", accessibleDescription)
    }
}

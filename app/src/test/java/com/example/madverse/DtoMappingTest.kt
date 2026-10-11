package com.example.madverse

import com.example.madverse.core.common.network.ApiConstants
import com.example.madverse.core.model.Genre
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieDetails
import com.example.madverse.core.model.MovieWatchProviders
import com.example.madverse.core.model.ProviderType
import com.example.madverse.core.model.WatchProvider
import com.example.madverse.core.network.model.CastDto
import com.example.madverse.core.network.model.CountryWatchProvidersDto
import com.example.madverse.core.network.model.GenreDto
import com.example.madverse.core.network.model.MovieDetailsDto
import com.example.madverse.core.network.model.MovieDto
import com.example.madverse.core.network.model.ProviderItemDto
import com.example.madverse.core.network.model.VideoDto
import com.example.madverse.core.network.model.WatchProvidersResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for all DTO-to-domain mapping functions.
 * These verify that [MovieRepositoryImpl] consumers receive correctly
 * mapped domain models regardless of what partial/null data TMDB returns.
 */
class DtoMappingTest {

    // -----------------------------------------------------------------------
    // MovieDto → Movie
    // -----------------------------------------------------------------------

    @Test
    fun movieDto_withAllFields_mapsCorrectly() {
        val dto = MovieDto(
            id = 550,
            title = "Fight Club",
            overview = "An insomniac office worker forms an underground fight club.",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            releaseDate = "1999-10-15",
            voteAverage = 8.8,
            voteCount = 26000,
            genreIds = listOf(18, 53),
            popularity = 200.0
        )

        val domain = dto.toDomain()

        assertEquals(550, domain.id)
        assertEquals("Fight Club", domain.title)
        assertEquals("1999", domain.releaseYear)
        assertEquals("8.8", domain.formattedRating)
        assertEquals("${ApiConstants.TMDB_IMAGE_BASE_W500}/poster.jpg", domain.posterUrl)
        assertEquals("${ApiConstants.TMDB_BACKDROP_BASE_W780}/backdrop.jpg", domain.backdropUrl)
        assertEquals(8.8, domain.voteAverage, 0.001)
        assertEquals(26000, domain.voteCount)
        assertEquals(listOf(18, 53), domain.genreIds)
        assertEquals(200.0, domain.popularity, 0.001)
    }

    @Test
    fun movieDto_withNullFields_returnsDefaults() {
        val dto = MovieDto(
            id = 999,
            title = null,
            overview = null,
            posterPath = null,
            backdropPath = null,
            releaseDate = null,
            voteAverage = null,
            voteCount = null,
            genreIds = null,
            popularity = null
        )

        val domain = dto.toDomain()

        assertEquals(999, domain.id)
        assertEquals("Untitled", domain.title)
        assertEquals("", domain.overview)
        assertNull(domain.posterUrl)
        assertNull(domain.backdropUrl)
        assertNull(domain.releaseDate)
        assertEquals("Unknown", domain.releaseYear)
        assertEquals(0.0, domain.voteAverage, 0.001)
        assertEquals(0, domain.voteCount)
        assertTrue(domain.genreIds.isEmpty())
        assertEquals(0.0, domain.popularity, 0.001)
    }

    // -----------------------------------------------------------------------
    // MovieDetailsDto → MovieDetails
    // -----------------------------------------------------------------------

    @Test
    fun movieDetailsDto_toDomain_mapsAllFields() {
        val dto = MovieDetailsDto(
            id = 101,
            title = "Inception",
            tagline = "Your mind is the scene of the crime.",
            overview = "A thief who steals corporate secrets through the use of dream-sharing technology.",
            posterPath = "/inception_poster.jpg",
            backdropPath = "/inception_backdrop.jpg",
            releaseDate = "2010-07-16",
            runtime = 148,
            voteAverage = 8.8,
            voteCount = 34000,
            genres = listOf(GenreDto(28, "Action"), GenreDto(878, "Science Fiction")),
            status = "Released",
            budget = 160_000_000L,
            revenue = 836_836_967L,
            originalLanguage = "en"
        )

        val domain = dto.toDomain(isFavorite = true, isInWatchlist = false)

        assertEquals(101, domain.id)
        assertEquals("Inception", domain.title)
        assertEquals("Your mind is the scene of the crime.", domain.tagline)
        assertEquals("2010", domain.releaseYear)
        assertEquals(148, domain.runtimeMinutes)
        assertEquals("2h 28m", domain.formattedRuntime)
        assertEquals(2, domain.genres.size)
        assertEquals("Action", domain.genres[0].name)
        assertEquals("Released", domain.status)
        assertEquals(160_000_000L, domain.budget)
        assertTrue(domain.isFavorite)
        assertFalse(domain.isInWatchlist)
    }

    @Test
    fun movieDetailsDto_blankTagline_returnsNull() {
        val dto = MovieDetailsDto(
            id = 1, title = "Test", tagline = "   ", overview = "Overview",
            posterPath = null, backdropPath = null, releaseDate = null,
            runtime = null, voteAverage = 7.0, voteCount = 100,
            genres = emptyList(), status = "Released",
            budget = 0L, revenue = 0L, originalLanguage = "en"
        )

        assertNull(dto.toDomain().tagline)
    }

    @Test
    fun movieDetailsDto_runtime_formatsCorrectly() {
        fun runtimeString(mins: Int): String {
            val dto = MovieDetailsDto(
                id = 1, title = "T", tagline = null, overview = "",
                posterPath = null, backdropPath = null, releaseDate = null,
                runtime = mins, voteAverage = 7.0, voteCount = 100,
                genres = emptyList(), status = "Released",
                budget = 0L, revenue = 0L, originalLanguage = "en"
            )
            return dto.toDomain().formattedRuntime
        }

        assertEquals("1h 30m", runtimeString(90))
        assertEquals("2h 0m", runtimeString(120))
        assertEquals("45m", runtimeString(45))
    }

    // -----------------------------------------------------------------------
    // GenreDto → Genre
    // -----------------------------------------------------------------------

    @Test
    fun genreDto_toDomain_mapsIdAndName() {
        val genreDto = GenreDto(id = 28, name = "Action")
        val genre: Genre = genreDto.toDomain()

        assertEquals(28, genre.id)
        assertEquals("Action", genre.name)
    }

    @Test
    fun genreDto_nullName_returnsUnknown() {
        val genreDto = GenreDto(id = 999, name = null)
        assertEquals("Unknown", genreDto.toDomain().name)
    }

    // -----------------------------------------------------------------------
    // VideoDto → VideoTrailer
    // -----------------------------------------------------------------------

    @Test
    fun videoDto_youtubeTrailer_mapsToEmbeddableDomain() {
        val dto = VideoDto(
            id = "abc123",
            key = "YtKEY_123",
            name = "Official Trailer",
            site = "YouTube",
            type = "Trailer",
            official = true
        )

        val domain = dto.toDomain()

        assertNotNull(domain)
        assertEquals("YtKEY_123", domain!!.key)
        assertTrue(domain.isEmbeddableYouTubeTrailer)
        assertEquals("https://www.youtube.com/watch?v=YtKEY_123", domain.youtubeWatchUrl)
    }

    @Test
    fun videoDto_nullKey_returnsNull() {
        val dto = VideoDto(id = "x", key = null, name = "BTS", site = "YouTube", type = "Featurette", official = false)
        assertNull(dto.toDomain())
    }

    // -----------------------------------------------------------------------
    // WatchProvidersResponseDto → MovieWatchProviders
    // -----------------------------------------------------------------------

    @Test
    fun watchProvidersDto_usRegion_mapsProvidersCorrectly() {
        val netflixProvider = ProviderItemDto(
            providerId = 8,
            providerName = "Netflix",
            logoPath = "/netflix_logo.png",
            displayPriority = 1
        )
        val primeProvider = ProviderItemDto(
            providerId = 119,
            providerName = "Amazon Prime Video",
            logoPath = "/prime_logo.png",
            displayPriority = 2
        )

        val responseDto = WatchProvidersResponseDto(
            id = 550,
            results = mapOf(
                "US" to CountryWatchProvidersDto(
                    link = "https://www.themoviedb.org/movie/550/watch",
                    flatrate = listOf(netflixProvider, primeProvider),
                    rent = emptyList(),
                    buy = emptyList()
                )
            )
        )

        val domain: MovieWatchProviders = responseDto.toDomain(movieId = 550, countryCode = "US")

        assertEquals(550, domain.movieId)
        assertEquals(2, domain.flatrate.size)
        assertTrue(domain.hasAnyProviders)
        assertEquals("Netflix", domain.flatrate[0].providerName)
        assertEquals(ProviderType.STREAMING_SUBSCRIPTION, domain.flatrate[0].type)
        assertTrue(domain.flatrate[0].logoUrl?.contains("netflix_logo") == true)
    }

    @Test
    fun watchProvidersDto_missingCountry_fallsBackToUs() {
        val provider = ProviderItemDto(8, "Netflix", "/logo.png", 1)
        val responseDto = WatchProvidersResponseDto(
            id = 1,
            results = mapOf(
                "US" to CountryWatchProvidersDto(null, listOf(provider), null, null)
            )
        )

        // Requesting IN (India) with no IN data → should fall back to US
        val domain = responseDto.toDomain(movieId = 1, countryCode = "IN")

        assertTrue(domain.hasAnyProviders)
        assertEquals("Netflix", domain.flatrate[0].providerName)
    }

    @Test
    fun watchProvidersDto_noResults_returnsEmptyProviders() {
        val responseDto = WatchProvidersResponseDto(id = 1, results = emptyMap())
        val domain = responseDto.toDomain(movieId = 1)

        assertFalse(domain.hasAnyProviders)
        assertTrue(domain.flatrate.isEmpty())
        assertTrue(domain.rent.isEmpty())
        assertTrue(domain.buy.isEmpty())
    }

    @Test
    fun watchProvidersDto_defaultsToIndiaRegion() {
        val hotstarProvider = ProviderItemDto(122, "Disney+ Hotstar", "/hotstar.png", 1)
        val responseDto = WatchProvidersResponseDto(
            id = 100,
            results = mapOf(
                "IN" to CountryWatchProvidersDto("https://themoviedb.org/movie/100/watch?locale=IN", listOf(hotstarProvider), null, null)
            )
        )

        val domain = responseDto.toDomain(movieId = 100)

        assertTrue(domain.hasAnyProviders)
        assertEquals("Disney+ Hotstar", domain.flatrate[0].providerName)
    }

    // -----------------------------------------------------------------------
    // CastDto → CastMember
    // -----------------------------------------------------------------------

    @Test
    fun castDto_toDomain_mapsFieldsAndBuildsProfileUrl() {
        val castDto = CastDto(
            id = 500,
            name = "Tom Hanks",
            character = "Forrest Gump",
            profilePath = "/tomhanks.jpg",
            order = 0
        )

        val domain = castDto.toDomain()

        assertEquals(500, domain.id)
        assertEquals("Tom Hanks", domain.name)
        assertEquals("Forrest Gump", domain.character)
        assertEquals(0, domain.order)
        assertTrue(domain.profileUrl?.startsWith(ApiConstants.TMDB_IMAGE_BASE_W500) == true)
    }

    @Test
    fun castDto_nullFields_useDefaults() {
        val castDto = CastDto(id = 1, name = null, character = null, profilePath = null, order = null)
        val domain = castDto.toDomain()

        assertEquals("Unknown", domain.name)
        assertEquals("", domain.character)
        assertNull(domain.profileUrl)
        assertEquals(999, domain.order) // fallback for unknown order
    }
}

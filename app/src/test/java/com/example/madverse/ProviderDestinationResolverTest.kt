package com.example.madverse

import com.example.madverse.core.domain.streaming.*
import com.example.madverse.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ProviderDestinationResolverTest {
    private val provider = WatchProvider(8, "Netflix", null, ProviderType.STREAMING_SUBSCRIPTION)
    private val fallback = "https://www.themoviedb.org/movie/550/watch?locale=IN"
    // Test contracts exercise precedence/encoding, not verification of live routes.
    private val search = VerifiedProviderSearchRoute(setOf(8), setOf("netflix"), "https://www.netflix.com/search", "q")
    private val direct = VerifiedContentLink(550, 8, "IN", provider.type, "https://www.netflix.com/title/12345")
    private fun resolve(resolver: ProviderDestinationResolver = ProviderDestinationResolver(),
        selected: WatchProvider = provider, title: String? = "Fight Club", url: String? = fallback,
        movieId: Int = 550, region: String = "IN") = resolver.resolve(selected, movieId, title, 1999, region, url)

    @Test fun verifiedDirectContentTakesPrecedence() {
        val result = resolve(ProviderDestinationResolver(listOf(direct), listOf(search)))
        assertEquals(ProviderDestinationType.DIRECT_CONTENT, result.type)
        assertEquals(direct.url, result.url)
    }
    @Test fun searchPrecedesFallbackAndEncodesUnicodePunctuation() {
        val result = resolve(ProviderDestinationResolver(emptyList(), listOf(search)), title = "दिल & C++ / #1?")
        assertEquals(ProviderDestinationType.PROVIDER_SEARCH, result.type)
        assertEquals("https://www.netflix.com/search?q=%E0%A4%A6%E0%A4%BF%E0%A4%B2%20%26%20C%2B%2B%20%2F%20%231%3F", result.url)
        assertEquals("दिल & C++ / #1?", result.movieTitle)
        assertEquals(1999, result.releaseYear)
        assertEquals(550, result.movieId)
    }
    @Test fun productionUsesRegionalFallbackWithoutInventingRoutes() {
        assertEquals(ProviderDestinationType.REGIONAL_WATCH_FALLBACK, resolve().type)
        assertEquals(fallback, resolve().url)
    }
    @Test fun arbitraryDirectLinkIsNotProofOfMovieAssociation() {
        assertEquals(fallback, resolve(selected = provider.copy(directLink = direct.url)).url)
    }
    @Test fun contentBindingMustMatchMovieRegionProviderAndCategory() {
        val invalid = listOf(direct.copy(movieId = 1), direct.copy(region = "US"),
            direct.copy(providerId = 119), direct.copy(category = ProviderType.BUY))
        invalid.forEach { assertEquals(fallback, resolve(ProviderDestinationResolver(listOf(it), emptyList())).url) }
    }
    @Test fun invalidContentUrlsCannotOverrideFallback() {
        listOf("javascript:alert(1)", "https://www.netflix.com.evil.test/title/12345",
            "https://user@www.netflix.com/title/12345", "https://www.netflix.com/",
            "https://www.netflix.com/title/12345?redirect=https://evil.test",
            "https://www.netflix.com:8443/title/12345").forEach { url ->
            assertEquals(fallback, resolve(ProviderDestinationResolver(listOf(direct.copy(url = url)), emptyList())).url)
        }
    }
    @Test fun stableProviderIdWinsOverConflictingName() {
        assertEquals(ProviderDestinationType.PROVIDER_SEARCH,
            resolve(ProviderDestinationResolver(emptyList(), listOf(search)), selected = provider.copy(providerName = "Prime Video")).type)
    }
    @Test fun exactNormalizedNameFallbackDoesNotUseSubstrings() {
        val resolver = ProviderDestinationResolver(emptyList(), listOf(search))
        assertEquals(ProviderDestinationType.PROVIDER_SEARCH, resolve(resolver, provider.copy(providerId = 0, providerName = " NETFLIX ")).type)
        assertEquals(ProviderDestinationType.REGIONAL_WATCH_FALLBACK, resolve(resolver, provider.copy(providerId = 999, providerName = "Netflix imitation")).type)
    }
    @Test fun blankTitlesSkipSearch() {
        val resolver = ProviderDestinationResolver(emptyList(), listOf(search))
        listOf(null, "", "  ").forEach { assertEquals(fallback, resolve(resolver, title = it).url) }
    }
    @Test fun allProviderCategoriesRemainAttached() {
        ProviderType.entries.forEach { type ->
            val result = resolve(selected = provider.copy(type = type))
            assertEquals(type, result.category); assertEquals(8, result.providerId)
        }
    }
    @Test fun unknownProviderCanUseWatchPageButNotHomepage() {
        assertEquals(fallback, resolve(selected = provider.copy(providerId = 777, providerName = "Unknown")).url)
        assertEquals(ProviderDestinationType.UNAVAILABLE, resolve(url = "https://www.netflix.com/").type)
    }
    @Test fun missingOrInvalidWatchPageIsUnavailable() {
        listOf(null, "", "file:///movie/550/watch", "content://movie/550/watch", "javascript:alert(1)",
            "http://www.themoviedb.org/movie/550/watch", "https://evil.test/movie/550/watch",
            "https://www.themoviedb.org.evil.test/movie/550/watch", "https://user@www.themoviedb.org/movie/550/watch",
            "https://www.themoviedb.org/movie/551/watch", "https://www.themoviedb.org/movie/550/watch#US",
            "https://www.themoviedb.org/movie/550/watch?locale=US",
            "https://www.themoviedb.org/movie/550/../551/watch", "https://www.themoviedb.org/movie/%35%35%30/watch",
            "https://www.themoviedb.org/movie/550/watch?redirect=https://evil.test", "https://[malformed",
            "https://www.justwatch.com/in/movie/unverified-slug").forEach { url ->
            assertEquals(url, ProviderDestinationType.UNAVAILABLE, resolve(url = url).type)
        }
    }
    @Test fun regionIsNormalizedAndAddedOnlyToMatchingMoviePage() {
        val result = resolve(region = " us ", url = "https://www.themoviedb.org/movie/550-fight-club/watch")
        assertEquals("US", result.region)
        assertEquals("https://www.themoviedb.org/movie/550-fight-club/watch?locale=US", result.url)
        assertEquals(ProviderDestinationType.UNAVAILABLE, resolve(region = "../IN").type)
        assertEquals(ProviderDestinationType.UNAVAILABLE, resolve(movieId = -1).type)
    }
    @Test fun unverifiedSearchHostIsRejected() {
        val resolver = ProviderDestinationResolver(emptyList(), listOf(search.copy(endpoint = "https://evil.test/search")))
        assertEquals(fallback, resolve(resolver).url)
    }
}

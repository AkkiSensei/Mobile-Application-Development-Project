package com.example.madverse.core.domain.streaming

import com.example.madverse.core.model.ProviderType
import com.example.madverse.core.model.WatchProvider
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

enum class ProviderDestinationType { DIRECT_CONTENT, PROVIDER_SEARCH, REGIONAL_WATCH_FALLBACK, UNAVAILABLE }

data class ProviderDestination(
    val type: ProviderDestinationType, val url: String?, val movieId: Int,
    val movieTitle: String?, val releaseYear: Int?, val region: String,
    val providerId: Int?, val category: ProviderType?
)

// Populate only from reviewed, trusted evidence. TMDB provider metadata does not
// establish a provider content ID or a provider search contract.
internal data class VerifiedContentLink(val movieId: Int, val providerId: Int, val region: String,
    val category: ProviderType, val url: String)
internal data class VerifiedProviderSearchRoute(val providerIds: Set<Int>, val exactNames: Set<String>,
    val endpoint: String, val queryParameter: String)

class ProviderDestinationResolver internal constructor(
    private val contentLinks: List<VerifiedContentLink>,
    private val searchRoutes: List<VerifiedProviderSearchRoute>
) {
    // No exact content links or public search routes were verified in this pass.
    constructor() : this(emptyList(), emptyList())

    fun resolve(provider: WatchProvider?, movieId: Int, title: String?, releaseYear: Int?,
        region: String, watchPageUrl: String?): ProviderDestination {
        val country = region.trim().uppercase(Locale.ROOT)
        fun result(type: ProviderDestinationType, url: String? = null) = ProviderDestination(
            type, url, movieId, title, releaseYear, country, provider?.providerId, provider?.type)
        if (movieId <= 0 || !country.matches(Regex("[A-Z]{2}"))) return result(ProviderDestinationType.UNAVAILABLE)
        if (provider != null) {
            val hosts = officialHosts(provider)
            contentLinks.firstOrNull { it.movieId == movieId && it.providerId == provider.providerId &&
                it.region == country && it.category == provider.type && safeHttps(it.url)?.let { uri ->
                    uri.host.lowercase(Locale.ROOT) in hosts && isContentPath(uri)
                } == true }?.let { return result(ProviderDestinationType.DIRECT_CONTENT, it.url) }

            val route = searchRoutes.firstOrNull { provider.providerId in it.providerIds }
                ?: searchRoutes.firstOrNull { provider.providerName.trim().lowercase(Locale.ROOT) in it.exactNames }
            if (!title.isNullOrBlank() && route != null &&
                safeHttps(route.endpoint)?.let { it.host.lowercase(Locale.ROOT) in hosts && it.rawQuery == null && it.fragment == null } == true &&
                route.queryParameter.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*"))) {
                val encoded = URLEncoder.encode(title.trim(), "UTF-8").replace("+", "%20")
                return result(ProviderDestinationType.PROVIDER_SEARCH, "${route.endpoint}?${route.queryParameter}=$encoded")
            }
        }
        val fallback = regionalWatchPage(watchPageUrl, movieId, country)
        return if (fallback != null) result(ProviderDestinationType.REGIONAL_WATCH_FALLBACK, fallback)
            else result(ProviderDestinationType.UNAVAILABLE)
    }

    private fun officialHosts(provider: WatchProvider): Set<String> {
        fun hosts(id: Int) = when (id) {
            8 -> setOf("www.netflix.com", "netflix.com")
            9, 119 -> setOf("www.primevideo.com", "primevideo.com")
            else -> emptySet()
        }
        val byId = hosts(provider.providerId)
        if (byId.isNotEmpty()) return byId
        return when (provider.providerName.trim().lowercase(Locale.ROOT)) {
            "netflix" -> hosts(8)
            "amazon prime video", "prime video" -> hosts(119)
            else -> emptySet()
        }
    }

    private fun isContentPath(uri: URI): Boolean = when (uri.host.lowercase(Locale.ROOT)) {
        "netflix.com", "www.netflix.com" -> uri.path.matches(Regex("/(title|watch)/[0-9]+/?"))
        "primevideo.com", "www.primevideo.com" -> uri.path.matches(Regex("/detail/[A-Za-z0-9]+/?"))
        else -> false
    } && uri.rawQuery == null && uri.fragment == null

    companion object {
        internal fun safeHttps(value: String?): URI? {
            if (value.isNullOrBlank() || value.any { it.isWhitespace() || it == '\\' || it.code < 32 }) return null
            val uri = try { URI(value) } catch (_: Exception) { return null }
            return uri.takeIf { it.scheme.equals("https", true) && !it.host.isNullOrBlank() &&
                it.rawUserInfo == null && it.port == -1 && it.rawPath == it.path && it.normalize() == it }
        }

        fun regionalWatchPage(value: String?, movieId: Int, region: String): String? {
            if (movieId <= 0 || !region.matches(Regex("[A-Z]{2}"))) return null
            val uri = safeHttps(value) ?: return null
            // Arbitrary JustWatch slugs cannot be associated with a TMDB movie ID
            // without an independently verified mapping. Use TMDB's supplied URL.
            if (uri.host.lowercase(Locale.ROOT) !in setOf("www.themoviedb.org", "themoviedb.org") ||
                !uri.path.matches(Regex("/movie/$movieId(?:-[A-Za-z0-9-]+)?/watch/?"))) return null
            if (uri.rawQuery != null && uri.rawQuery != "locale=$region") return null
            if (uri.fragment != null && uri.fragment != region) return null
            return "https://${uri.host}${uri.path}?locale=$region"
        }
    }
}

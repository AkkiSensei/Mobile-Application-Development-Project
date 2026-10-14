package com.example.madverse.core.domain.streaming

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Parses entire URLs, never trusted-looking substrings inside an untrusted URL. */
object YouTubeUrlSanitizer {
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")
    private val watchHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
    private val embedHosts = watchHosts + setOf("youtube-nocookie.com", "www.youtube-nocookie.com")

    fun isValidVideoId(id: String?): Boolean = id != null && idPattern.matches(id)

    fun extractVideoId(input: String?): String? {
        val value = input?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (isValidVideoId(value)) return value
        return runCatching {
            val uri = URI(value)
            if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http") ||
                uri.userInfo != null || uri.port != -1 || uri.rawFragment != null) return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val path = uri.rawPath
            val candidate = when {
                host in setOf("youtu.be", "www.youtu.be") -> path.removePrefix("/")
                host in watchHosts && path == "/watch" -> {
                    val ids = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
                        .filter { it[0] == "v" }.map { URLDecoder.decode(it.getOrElse(1) { "" }, "UTF-8") }
                    ids.singleOrNull()
                }
                host in embedHosts && path.startsWith("/embed/") -> path.removePrefix("/embed/")
                host in watchHosts && path.startsWith("/shorts/") -> path.removePrefix("/shorts/")
                else -> null
            }
            candidate?.takeIf(::isValidVideoId)
        }.getOrNull()
    }

    fun buildEmbedUrl(videoId: String?): String? = extractVideoId(videoId)?.let {
        "https://www.youtube-nocookie.com/embed/$it?enablejsapi=1&autoplay=0&rel=0"
    }

    /** Invalid input has no external destination and is never interpolated into a URL. */
    fun buildWebWatchUrl(videoId: String?): String = extractVideoId(videoId)?.let {
        "https://www.youtube.com/watch?v=$it"
    }.orEmpty()

    fun isPermittedUrl(url: String?): Boolean = url?.startsWith("https://", ignoreCase = true) == true && extractVideoId(url) != null
}

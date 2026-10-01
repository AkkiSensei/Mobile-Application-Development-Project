package com.example.madverse.core.domain.streaming

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.madverse.core.model.WatchProvider

/**
 * Contract and helper functions for resolving official streaming destinations.
 * Strict security rule: MADVerse NEVER scrapes DRM or hosts unauthorized video playback.
 * All streaming actions launch official destination apps or web URLs via Android Intents.
 */
object StreamingContract {

    /**
     * Resolves the safest external intent to direct the user to the official provider.
     */
    fun createProviderIntent(provider: WatchProvider, movieTitle: String, fallbackUrl: String?): Intent {
        val directUrl = provider.directLink
        val targetUrl = when {
            !directUrl.isNullOrBlank() -> directUrl
            !fallbackUrl.isNullOrBlank() -> fallbackUrl
            else -> "https://www.themoviedb.org/search?query=" + Uri.encode(movieTitle)
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
    }
}

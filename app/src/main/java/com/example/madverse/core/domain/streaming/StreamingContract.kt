package com.example.madverse.core.domain.streaming

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Launch HTTPS App Links normally, allowing Android to choose an app or browser. */
object StreamingContract {
    fun open(context: Context, destination: ProviderDestination) = dispatch(destination,
        launch = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        notify = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() })

    internal fun dispatch(destination: ProviderDestination, launch: (String) -> Unit, notify: (String) -> Unit) {
        val url = destination.url
        if (destination.type == ProviderDestinationType.UNAVAILABLE || ProviderDestinationResolver.safeHttps(url) == null) {
            notify("No verified watch destination is available for this movie and region.")
            return
        }
        try {
            launch(requireNotNull(url))
            if (destination.type == ProviderDestinationType.REGIONAL_WATCH_FALLBACK) {
                notify("Opening regional watch options on TMDB / JustWatch (${destination.region}).")
            }
        } catch (_: Exception) {
            // Includes ActivityNotFoundException and SecurityException; report failed launches.
            notify("No app could open these watch options. Please try again with a browser available.")
        }
    }
}

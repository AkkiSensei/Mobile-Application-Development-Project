package com.example.madverse

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import com.example.madverse.core.data.repository.SessionManager
import javax.inject.Inject

/**
 * Main application class for MADVerse.
 * Serves as the application-level dependency container via Hilt.
 */
@HiltAndroidApp
class MADVerseApp : Application(), ImageLoaderFactory {
    // Restore sync on process startup, even if the user only opens Discover.
    @Inject lateinit var sessionManager: SessionManager

    // Coil calls this lazily for its process singleton. Do not use the TMDB API
    // OkHttp client here: image requests must never inherit API credentials.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizeBytes(minOf(32L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8).toInt())
                .build()
        }
        .diskCache {
            DiskCache.Builder().directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(128L * 1024 * 1024).build()
        }
        .build()
}

package com.example.madverse.core.network.interceptor

import com.example.madverse.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that automatically injects the TMDB API key query parameter into every request.
 * Reads the key safely from BuildConfig (populated from local.properties or environment).
 */
@Singleton
class AuthInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        val apiKey = BuildConfig.TMDB_API_KEY

        val urlWithKey = if (apiKey.isNotBlank()) {
            originalUrl.newBuilder()
                .addQueryParameter("api_key", apiKey)
                .build()
        } else {
            originalUrl
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .url(urlWithKey)
            .build()

        return chain.proceed(authenticatedRequest)
    }
}

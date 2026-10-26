package com.example.madverse

import com.example.madverse.core.data.repository.impl.MovieRepositoryImpl
import com.example.madverse.core.network.api.TmdbApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Exercises the actual Retrofit suspend adapter without a server, credentials or new dependencies. */
@OptIn(ExperimentalCoroutinesApi::class)
class RetrofitCancellationTest {
    @Test fun cancellingRepositoryCollectionCancelsUnderlyingHttpCall() = runTest {
        lateinit var httpCall: PendingCall
        val api = Retrofit.Builder()
            .baseUrl("https://example.invalid/")
            .addConverterFactory(GsonConverterFactory.create())
            .callFactory { request -> PendingCall(request).also { httpCall = it } }
            .build().create(TmdbApi::class.java)
        val repository = MovieRepositoryImpl(api)
        val collection = launch { repository.searchMovies("example").toList() }
        runCurrent()
        assertTrue(httpCall.isExecuted())
        assertFalse(httpCall.isCanceled())
        collection.cancel(); runCurrent()
        assertTrue(collection.isCancelled)
        assertTrue(httpCall.isCanceled())
    }

    private class PendingCall(private val original: Request) : Call {
        private var started = false
        private var cancelled = false
        override fun request(): Request = original
        override fun execute(): Response = error("The suspend adapter must enqueue")
        override fun enqueue(responseCallback: Callback) { started = true }
        override fun cancel() { cancelled = true }
        override fun isExecuted(): Boolean = started
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = PendingCall(original)
    }
}

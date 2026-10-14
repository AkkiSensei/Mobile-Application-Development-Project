package com.example.madverse

import com.example.madverse.core.network.NetworkModule
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkLoggingTest {
    @Test fun authenticatedRequestUrlsAreNeverLogged() {
        assertEquals(HttpLoggingInterceptor.Level.NONE, NetworkModule.provideLoggingInterceptor().level)
    }
}

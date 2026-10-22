package com.example.madverse

import com.example.madverse.core.domain.streaming.*
import org.junit.Assert.*
import org.junit.Test

class StreamingContractTest {
    private fun destination(url: String? = "https://www.themoviedb.org/movie/550/watch?locale=IN") =
        ProviderDestinationResolver().resolve(null, 550, "Fight Club", 1999, "IN", url)

    @Test fun fallbackLaunchUsesResolvedUrlAndExplainsRegionalOptions() {
        val launched = mutableListOf<String>(); val messages = mutableListOf<String>()
        StreamingContract.dispatch(destination(), { launched += it }, { messages += it })
        assertEquals(listOf(destination().url), launched)
        assertTrue(messages.single().contains("regional watch options"))
        assertTrue(messages.single().contains("IN"))
    }
    @Test fun unavailableDoesNotLaunchAndReportsWhy() {
        val messages = mutableListOf<String>()
        StreamingContract.dispatch(destination(null), { fail("Must not launch") }, { messages += it })
        assertTrue(messages.single().contains("No verified"))
    }
    @Test fun launchFailureIsVisibleWithoutSuccessMessage() {
        val messages = mutableListOf<String>()
        StreamingContract.dispatch(destination(), { throw SecurityException("Blocked") }, { messages += it })
        assertTrue(messages.single().contains("No app could open"))
    }
}

package com.example.madverse

import com.example.madverse.core.common.ui.RepositoryActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RepositoryActionsTest {
    @Test fun duplicateTapsAreCoalescedButDifferentCollectionsAreIndependent() = runTest {
        val actions = RepositoryActions(this)
        val completed = mutableListOf<String>()
        actions.run("favorite:1") { delay(100); completed += "favorite" }
        actions.run("favorite:1") { fail("Duplicate write") }
        actions.run("watchlist:1") { completed += "watchlist" }
        advanceUntilIdle()
        assertEquals(listOf("watchlist", "favorite"), completed)
    }
    @Test fun failedActionCanBeDismissedAndRetried() = runTest {
        val actions = RepositoryActions(this)
        actions.run("favorite:1") { error("Disk full") }; advanceUntilIdle()
        assertNotNull(actions.error.value)
        actions.dismissError(); assertNull(actions.error.value)
        var saved = false
        actions.run("favorite:1") { saved = true }; advanceUntilIdle()
        assertTrue(saved)
    }
    @Test fun cancellationIsNotAnErrorAndReleasesPendingKey() = runTest {
        val actions = RepositoryActions(this)
        actions.run("favorite:1") { throw CancellationException("Cancelled") }; advanceUntilIdle()
        assertNull(actions.error.value)
        var saved = false
        actions.run("favorite:1") { saved = true }; advanceUntilIdle()
        assertTrue(saved)
    }
}

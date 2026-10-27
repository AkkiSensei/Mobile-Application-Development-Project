package com.example.madverse

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.madverse.core.data.account.DeletionStage
import com.example.madverse.core.data.account.SharedPreferencesAccountDeletionJournal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DeletionJournalPersistenceTest {
    // Instrumentation runs as the target UID. Use its writable storage with an isolated
    // test preference name, never the real account journal.
    private val context get() = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("prompt5_test_$name", mode)
    }

    @Test fun ambiguousDeletionSurvivesJournalRecreationAndRemainsPending() = runBlocking {
        val uid = "journal-test-${UUID.randomUUID()}"
        val first = SharedPreferencesAccountDeletionJournal(context)
        try {
            first.recordStage(uid, DeletionStage.AUTH_DELETE_REQUESTED, "Simulated timeout")
            val recreated = SharedPreferencesAccountDeletionJournal(context)
            val entry = recreated.getJournalEntry(uid)!!
            assertEquals(DeletionStage.AUTH_DELETE_REQUESTED, entry.stage)
            assertEquals("Simulated timeout", entry.lastError)
            assertTrue(entry.timestamp > 0)
            assertTrue(recreated.isDeletionPending(uid))
            assertTrue(recreated.pendingEntries().any { it.userId == uid })
            recreated.recordStage(uid, DeletionStage.COMPLETED)
            assertFalse(first.isDeletionPending(uid))
            assertFalse(first.pendingEntries().any { it.userId == uid })
        } finally {
            first.clearJournal(uid)
        }
    }

    @Test fun unknownStageRemainsPendingWithoutAffectingAnotherAccount() = runBlocking {
        val uid = "journal-test-${UUID.randomUUID()}"
        val other = "journal-test-${UUID.randomUUID()}"
        val journal = SharedPreferencesAccountDeletionJournal(context)
        try {
            journal.recordStage(other, DeletionStage.REMOTE_AUTH_CONFIRMED)
            val prefs = context.getSharedPreferences("madverse_account_deletion_journal", Context.MODE_PRIVATE)
            assertTrue(prefs.edit().putString("stage_$uid", "UNKNOWN_FUTURE_STAGE").commit())
            val recreated = SharedPreferencesAccountDeletionJournal(context)
            assertEquals(DeletionStage.STARTED, recreated.getJournalEntry(uid)!!.stage)
            assertTrue(recreated.isDeletionPending(uid))
            recreated.clearJournal(uid)
            assertNull(recreated.getJournalEntry(uid))
            assertEquals(DeletionStage.REMOTE_AUTH_CONFIRMED, recreated.getJournalEntry(other)!!.stage)
            assertTrue(recreated.isDeletionPending(other))
        } finally {
            journal.clearJournal(uid)
            journal.clearJournal(other)
        }
    }
}

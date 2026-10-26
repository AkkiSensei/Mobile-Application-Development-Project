package com.example.madverse

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.madverse.core.data.repository.*
import com.example.madverse.core.data.repository.impl.*
import com.example.madverse.core.database.MADVerseDatabase
import com.example.madverse.core.database.RoomTransactionRunner
import com.example.madverse.core.database.dao.PendingSyncDao
import com.example.madverse.core.database.entity.PendingSyncEntity
import com.example.madverse.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Runs actual Room transactions; compiling this test alone does not establish SQLite rollback. */
class RoomQueueAtomicityTest {
    @Test fun queueFailureRollsBackAllSixRepositoryMutations() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, MADVerseDatabase::class.java).build()
        try {
            val session = object : SessionManager {
                override val currentUserId = "atomic-test"
                override val currentUserIdFlow = MutableStateFlow(currentUserId)
                override val authState = MutableStateFlow<AuthState>(AuthState.Guest)
                override val isGuest = false
                override suspend fun hasGuestData() = false
            }
            var failQueue = true
            val queue = object : PendingSyncDao by db.pendingSyncDao() {
                override suspend fun insertAction(action: PendingSyncEntity): Long {
                    val id = db.pendingSyncDao().insertAction(action)
                    if (failQueue) throw IOException("Injected queue failure after insert")
                    return id
                }
            }
            val transactions = RoomTransactionRunner(db)
            val cloud = NoOpCloudSyncService()
            val favorites = FavoritesRepositoryImpl(db.favoriteDao(), session, cloud, queue, transactions)
            val watchlist = WatchlistRepositoryImpl(db.watchlistDao(), session, cloud, queue, transactions)
            val quiz = QuizRepositoryImpl(db.userPreferenceDao(), session, cloud, queue, transactions)
            val movie = Movie(1, "Test", "", null, null, null, 7.0, 0, emptyList(), 0.0)
            val profile = UserPreferenceProfile(selectedGenreIds = setOf(28))
            suspend fun fails(block: suspend () -> Unit) {
                try { block(); fail("Expected injected queue failure") } catch (_: IOException) { }
            }
            fails { favorites.addToFavorites(movie) }
            fails { watchlist.addToWatchlist(movie) }
            fails { quiz.saveUserPreferenceProfile(profile) }
            assertNull(db.favoriteDao().getFavoriteById(1, session.currentUserId))
            assertNull(db.watchlistDao().getWatchlistMovieById(1, session.currentUserId))
            assertNull(db.userPreferenceDao().getUserPreferenceProfileOnce(session.currentUserId))
            assertTrue(queue.getPendingActionsForUser(session.currentUserId).isEmpty())

            failQueue = false
            favorites.addToFavorites(movie); watchlist.addToWatchlist(movie); quiz.saveUserPreferenceProfile(profile)
            val before = queue.getPendingActionsForUser(session.currentUserId)
            failQueue = true
            fails { favorites.removeFromFavorites(1) }
            fails { watchlist.removeFromWatchlist(1) }
            fails { quiz.clearUserPreferenceProfile() }
            assertNotNull(db.favoriteDao().getFavoriteById(1, session.currentUserId))
            assertNotNull(db.watchlistDao().getWatchlistMovieById(1, session.currentUserId))
            assertEquals(profile, db.userPreferenceDao().getUserPreferenceProfileOnce(session.currentUserId)!!.toDomain())
            assertEquals(before, queue.getPendingActionsForUser(session.currentUserId))
        } finally {
            db.close()
        }
    }
}

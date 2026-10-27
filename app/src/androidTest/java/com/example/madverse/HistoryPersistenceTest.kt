package com.example.madverse

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.madverse.core.database.*
import com.example.madverse.core.database.dao.RecentlyViewedDao
import com.example.madverse.core.database.entity.RecentlyViewedEntity
import com.example.madverse.core.data.repository.impl.RecentlyViewedRepositoryImpl
import com.example.madverse.core.model.Movie
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HistoryPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun movie(id: Int) = Movie(id, "Movie $id", "", null, null, null, 7.5, 0, emptyList(), 0.0)

    @Test fun migrationPreservesLibrariesPreferencesAndPendingIntent() = runBlocking {
        val name = "prompt4-migration-${UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Historical v1 fixture, then the actual 1->2 migration, producing a real v2 database.
                    for (table in listOf("favorite_movies", "watchlist_movies")) {
                        db.execSQL("CREATE TABLE $table (movie_id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, poster_path TEXT, backdrop_path TEXT, release_date TEXT, vote_average REAL NOT NULL, overview TEXT, saved_timestamp INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO $table VALUES (42, 'Preserved', NULL, NULL, NULL, 7.5, 'Saved overview', 123)")
                    }
                    db.execSQL("CREATE TABLE user_preference_profile (id INTEGER NOT NULL PRIMARY KEY, genre_ids_csv TEXT NOT NULL, mood TEXT NOT NULL, era TEXT NOT NULL, runtime_preference TEXT NOT NULL, preferred_language TEXT, min_rating_threshold REAL NOT NULL, updated_timestamp INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO user_preference_profile VALUES (1, '28', 'ANY', 'ANY', 'ANY', 'en', 8.0, 456)")
                    MADVerseDatabase.MIGRATION_1_2.migrate(db)
                    db.execSQL("INSERT INTO pending_sync_actions VALUES (77, 'guest', 'DELETE', 'FAVORITE', '42', NULL, 789)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture upgrade not expected")
            }).build())
        try {
            helper.writableDatabase
            helper.close()
            val db = Room.databaseBuilder(context, MADVerseDatabase::class.java, name).addMigrations(*MADVerseDatabase.ALL_MIGRATIONS).build()
            try {
                assertEquals("Preserved", db.favoriteDao().getFavoriteById(42, "guest")!!.title)
                assertEquals("Saved overview", db.watchlistDao().getWatchlistMovieById(42, "guest")!!.overview)
                assertEquals(8.0, db.userPreferenceDao().getUserPreferenceProfileOnce("guest")!!.minRatingThreshold, 0.0)
                assertEquals(77L, db.pendingSyncDao().getNextPendingActionForUser("guest")!!.id)
                val history = RecentlyViewedRepositoryImpl(db.recentlyViewedDao(), RoomTransactionRunner(db))
                assertTrue(history.getRecentlyViewedOnce().isEmpty())
                history.recordMovieView(movie(42)); history.clearHistory()
                assertTrue(history.getRecentlyViewedOnce().isEmpty())
                assertTrue(db.favoriteDao().isFavoriteOnce(42, "guest"))
                assertTrue(db.watchlistDao().isMovieInWatchlistOnce(42, "guest"))
                assertEquals(77L, db.pendingSyncDao().getNextPendingActionForUser("guest")!!.id)
            } finally { db.close() }
        } finally { helper.close(); context.deleteDatabase(name) }
    }

    @Test fun historySurvivesReopenAndRollbackAndKeepsNewestTwenty() = runBlocking {
        val name = "prompt4-history-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, MADVerseDatabase::class.java, name).build()
        var db = open()
        try {
            val history = RecentlyViewedRepositoryImpl(db.recentlyViewedDao(), RoomTransactionRunner(db))
            db.recentlyViewedDao().insertOrUpdate(RecentlyViewedEntity.fromDomain(movie(1), System.currentTimeMillis() + 1000000))
            for (id in 2..25) history.recordMovieView(movie(id))
            history.recordMovieView(movie(10))
            assertEquals(20, history.getRecentlyViewedOnce().size)
            assertEquals(10, history.getRecentlyViewed().first().first().id)
            assertFalse(history.getRecentlyViewedOnce().any { it.id == 1 })
            val failing = object : RecentlyViewedDao by db.recentlyViewedDao() {
                override suspend fun trimToMaxLimit(keepCount: Int) { error("Injected trim failure") }
            }
            assertTrue(runCatching { RecentlyViewedRepositoryImpl(failing, RoomTransactionRunner(db)).recordMovieView(movie(99)) }.isFailure)
            assertFalse(history.getRecentlyViewedOnce().any { it.id == 99 })
            db.close(); db = open()
            assertEquals(10, db.recentlyViewedDao().getRecentlyViewedOnce().first().movieId)
            assertEquals(20, db.recentlyViewedDao().getCountOnce())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

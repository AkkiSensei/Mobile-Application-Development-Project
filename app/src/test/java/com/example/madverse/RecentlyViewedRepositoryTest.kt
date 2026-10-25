package com.example.madverse

import com.example.madverse.core.data.repository.RecentlyViewedRepository
import com.example.madverse.core.data.repository.impl.RecentlyViewedRepositoryImpl
import com.example.madverse.core.database.DirectTransactionRunner
import com.example.madverse.core.database.dao.RecentlyViewedDao
import com.example.madverse.core.database.entity.RecentlyViewedEntity
import com.example.madverse.core.model.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecentlyViewedRepositoryTest {

    private lateinit var fakeDao: FakeRecentlyViewedDao
    private lateinit var repository: RecentlyViewedRepository

    @Before
    fun setUp() {
        fakeDao = FakeRecentlyViewedDao()
        repository = RecentlyViewedRepositoryImpl(
            recentlyViewedDao = fakeDao,
            transactionRunner = DirectTransactionRunner()
        )
    }

    private fun createMovie(id: Int, title: String = "Movie $id"): Movie {
        return Movie(
            id = id,
            title = title,
            overview = "Overview $id",
            posterUrl = "/poster_$id.jpg",
            backdropUrl = "/backdrop_$id.jpg",
            releaseDate = "2024-01-01",
            voteAverage = 7.5,
            voteCount = 100,
            genreIds = listOf(28),
            popularity = 50.0
        )
    }

    @Test
    fun recordMovieView_insertsMovieIntoRecentlyViewed() = runTest {
        val movie = createMovie(1, "Inception")
        repository.recordMovieView(movie)

        val list = repository.getRecentlyViewed().first()
        assertEquals(1, list.size)
        assertEquals(1, list[0].id)
        assertEquals("Inception", list[0].title)
    }

    @Test
    fun recordMovieView_deduplicatesByMovieId_andBumpsToFront() = runTest {
        val movie1 = createMovie(1, "Movie 1")
        val movie2 = createMovie(2, "Movie 2")

        repository.recordMovieView(movie1)
        repository.recordMovieView(movie2)

        // View movie1 again
        repository.recordMovieView(movie1)

        val list = repository.getRecentlyViewed().first()
        assertEquals("List should be deduplicated to 2 movies", 2, list.size)
        assertEquals("Most recently viewed should be movie 1", 1, list[0].id)
        assertEquals("Second should be movie 2", 2, list[1].id)
    }

    @Test
    fun recordMovieView_boundsHistoryTo20Items() = runTest {
        for (i in 1..25) {
            repository.recordMovieView(createMovie(i, "Movie $i"))
        }

        val list = repository.getRecentlyViewed(limit = 50).first()
        assertEquals("History must be bounded to maximum 20 items", 20, list.size)
        // The most recently added (25 down to 6) should be present; 1..5 should be trimmed
        assertEquals(25, list.first().id)
        assertEquals(6, list.last().id)
        assertTrue("Movie 1 should have been trimmed out", list.none { it.id == 1 })
    }

    @Test
    fun clearHistory_removesAllEntries() = runTest {
        repository.recordMovieView(createMovie(1))
        repository.recordMovieView(createMovie(2))

        repository.clearHistory()

        val list = repository.getRecentlyViewed().first()
        assertTrue("History should be empty after clear", list.isEmpty())
    }

    private class FakeRecentlyViewedDao : RecentlyViewedDao {
        private val storage = mutableMapOf<Int, RecentlyViewedEntity>()
        private val flow = MutableStateFlow<List<RecentlyViewedEntity>>(emptyList())
        private var timeCounter = 1000L

        private fun updateFlow() {
            flow.value = storage.values.sortedByDescending { it.viewedTimestamp }
        }

        override fun getRecentlyViewed(limit: Int): Flow<List<RecentlyViewedEntity>> {
            return flow.map { list -> list.take(limit) }
        }

        override suspend fun getRecentlyViewedOnce(limit: Int): List<RecentlyViewedEntity> {
            return storage.values.sortedByDescending { it.viewedTimestamp }.take(limit)
        }

        override suspend fun insertOrUpdate(entity: RecentlyViewedEntity) {
            timeCounter += 10L
            storage[entity.movieId] = entity.copy(viewedTimestamp = timeCounter)
            updateFlow()
        }

        override suspend fun trimToMaxLimit(keepCount: Int) {
            val sorted = storage.values.sortedByDescending { it.viewedTimestamp }
            if (sorted.size > keepCount) {
                val toRemove = sorted.drop(keepCount).map { it.movieId }
                toRemove.forEach { storage.remove(it) }
                updateFlow()
            }
        }

        override suspend fun deleteByMovieId(movieId: Int) {
            storage.remove(movieId)
            updateFlow()
        }

        override suspend fun clearAll() {
            storage.clear()
            updateFlow()
        }

        override fun getCount(): Flow<Int> = flow.map { it.size }

        override suspend fun getCountOnce(): Int = storage.size
    }
}

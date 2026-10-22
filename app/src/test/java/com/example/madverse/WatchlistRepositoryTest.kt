package com.example.madverse

import com.example.madverse.core.data.repository.impl.WatchlistRepositoryImpl
import com.example.madverse.core.database.dao.WatchlistDao
import com.example.madverse.core.database.entity.WatchlistEntity
import com.example.madverse.core.model.Movie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [WatchlistRepositoryImpl] and [WatchlistEntity].
 * Verifies:
 * - Reactive flow observation of saved watchlist movies
 * - Add, remove, and query CRUD operations
 * - Duplicate prevention (OnConflict REPLACE by TMDB movie ID)
 * - Membership checking (both reactive Flow and one-time query)
 * - Metadata retention for offline viewing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistRepositoryTest {

    private lateinit var testDao: TestWatchlistDao
    private lateinit var repository: WatchlistRepositoryImpl

    @Before
    fun setUp() {
        testDao = TestWatchlistDao()
        repository = WatchlistRepositoryImpl(testDao)
    }

    private fun createMovie(
        id: Int = 27205,
        title: String = "Inception",
        overview: String = "A thief who steals corporate secrets through the dream-sharing technology.",
        posterUrl: String? = "https://image.tmdb.org/t/p/w500/poster27205.jpg",
        backdropUrl: String? = "https://image.tmdb.org/t/p/w780/backdrop27205.jpg",
        releaseDate: String? = "2010-07-16",
        voteAverage: Double = 8.8
    ): Movie {
        return Movie(
            id = id,
            title = title,
            overview = overview,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            releaseDate = releaseDate,
            voteAverage = voteAverage,
            voteCount = 34000,
            genreIds = listOf(28, 878, 12),
            popularity = 95.0
        )
    }

    @Test
    fun getWatchlistMovies_emptyByDefault_emitsEmptyList() = runTest {
        val watchlist = repository.getWatchlistMovies().first()
        assertTrue(watchlist.isEmpty())
    }

    @Test
    fun addToWatchlist_savesMovie_emitsInWatchlistWithMetadata() = runTest {
        val movie = createMovie(id = 27205, title = "Inception")
        repository.addToWatchlist(movie)

        val watchlist = repository.getWatchlistMovies().first()
        assertEquals(1, watchlist.size)

        val saved = watchlist.first()
        assertEquals(27205, saved.id)
        assertEquals("Inception", saved.title)
        assertEquals("A thief who steals corporate secrets through the dream-sharing technology.", saved.overview)
        assertEquals("https://image.tmdb.org/t/p/w500/poster27205.jpg", saved.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w780/backdrop27205.jpg", saved.backdropUrl)
        assertEquals("2010-07-16", saved.releaseDate)
        assertEquals(8.8, saved.voteAverage, 0.001)
        assertEquals("2010", saved.releaseYear)
        assertEquals("8.8", saved.formattedRating)
    }

    @Test
    fun addToWatchlist_duplicateMovie_updatesRecordWithoutDuplication() = runTest {
        val movieV1 = createMovie(id = 27205, title = "Inception", voteAverage = 8.7)
        val movieV2 = createMovie(id = 27205, title = "Inception (IMAX)", voteAverage = 8.9)

        repository.addToWatchlist(movieV1)
        repository.addToWatchlist(movieV2)

        val watchlist = repository.getWatchlistMovies().first()
        assertEquals(1, watchlist.size)
        assertEquals("Inception (IMAX)", watchlist.first().title)
        assertEquals(8.9, watchlist.first().voteAverage, 0.001)
    }

    @Test
    fun removeFromWatchlist_removesMovieFromWatchlist() = runTest {
        val movie1 = createMovie(id = 27205, title = "Inception")
        val movie2 = createMovie(id = 157336, title = "Interstellar")

        repository.addToWatchlist(movie1)
        repository.addToWatchlist(movie2)

        var watchlist = repository.getWatchlistMovies().first()
        assertEquals(2, watchlist.size)

        repository.removeFromWatchlist(27205)

        watchlist = repository.getWatchlistMovies().first()
        assertEquals(1, watchlist.size)
        assertEquals(157336, watchlist.first().id)
    }

    @Test
    fun removeFromWatchlist_nonExistentMovie_doesNotCrashOrAlterList() = runTest {
        val movie = createMovie(id = 27205)
        repository.addToWatchlist(movie)

        repository.removeFromWatchlist(999999)

        val watchlist = repository.getWatchlistMovies().first()
        assertEquals(1, watchlist.size)
    }

    @Test
    fun isMovieInWatchlist_reactivelyReflectsMembership() = runTest {
        val movie = createMovie(id = 27205)

        assertFalse(repository.isMovieInWatchlist(27205).first())
        assertFalse(repository.isMovieInWatchlistOnce(27205))

        repository.addToWatchlist(movie)

        assertTrue(repository.isMovieInWatchlist(27205).first())
        assertTrue(repository.isMovieInWatchlistOnce(27205))

        repository.removeFromWatchlist(27205)

        assertFalse(repository.isMovieInWatchlist(27205).first())
        assertFalse(repository.isMovieInWatchlistOnce(27205))
    }

    @Test
    fun getWatchlistMovieById_returnsCorrectMovieOrNull() = runTest {
        val movie = createMovie(id = 27205, title = "Inception")

        assertNull(repository.getWatchlistMovieById(27205))

        repository.addToWatchlist(movie)

        val found = repository.getWatchlistMovieById(27205)
        assertNotNull(found)
        assertEquals(27205, found?.id)
        assertEquals("Inception", found?.title)
    }

    @Test
    fun watchlistEntity_fromDomainAndToDomain_preservesAllMetadata() {
        val movie = createMovie(
            id = 299536,
            title = "Avengers: Infinity War",
            overview = "An unprecedented cinematic journey ten years in the making.",
            posterUrl = "https://image.tmdb.org/t/p/w500/infinity_war.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/infinity_war_backdrop.jpg",
            releaseDate = "2018-04-27",
            voteAverage = 8.3
        )

        val entity = WatchlistEntity.fromDomain(movie, savedTimestamp = 9876543210L)
        assertEquals(299536, entity.movieId)
        assertEquals("Avengers: Infinity War", entity.title)
        assertEquals("An unprecedented cinematic journey ten years in the making.", entity.overview)
        assertEquals("https://image.tmdb.org/t/p/w500/infinity_war.jpg", entity.posterPath)
        assertEquals("https://image.tmdb.org/t/p/w780/infinity_war_backdrop.jpg", entity.backdropPath)
        assertEquals("2018-04-27", entity.releaseDate)
        assertEquals(8.3, entity.voteAverage, 0.001)
        assertEquals(9876543210L, entity.savedTimestamp)

        val restoredMovie = entity.toDomain()
        assertEquals(movie.id, restoredMovie.id)
        assertEquals(movie.title, restoredMovie.title)
        assertEquals(movie.overview, restoredMovie.overview)
        assertEquals(movie.posterUrl, restoredMovie.posterUrl)
        assertEquals(movie.backdropUrl, restoredMovie.backdropUrl)
        assertEquals(movie.releaseDate, restoredMovie.releaseDate)
        assertEquals(movie.voteAverage, restoredMovie.voteAverage, 0.001)
    }

    @Test
    fun getWatchlistMovies_orderedBySavedTimestampDescending() = runTest {
        val movie1 = createMovie(id = 10, title = "First Watchlist")
        val movie2 = createMovie(id = 20, title = "Second Watchlist")

        testDao.insertMovie(WatchlistEntity.fromDomain(movie1, savedTimestamp = 1000L))
        testDao.insertMovie(WatchlistEntity.fromDomain(movie2, savedTimestamp = 3000L))

        val watchlist = repository.getWatchlistMovies().first()
        assertEquals(2, watchlist.size)
        assertEquals("Second Watchlist", watchlist[0].title)
        assertEquals("First Watchlist", watchlist[1].title)
    }

    private class TestWatchlistDao : WatchlistDao {
        private val records = mutableMapOf<Pair<Int, String>, WatchlistEntity>()
        private val flow = MutableStateFlow<List<WatchlistEntity>>(emptyList())

        private fun emit() {
            flow.value = records.values.sortedByDescending { it.savedTimestamp }
        }

        override fun getAllWatchlistMovies(userId: String): Flow<List<WatchlistEntity>> =
            flow.map { list -> list.filter { it.userId == userId } }

        override suspend fun getWatchlistMovieById(movieId: Int, userId: String): WatchlistEntity? =
            records[movieId to userId]

        override fun isMovieInWatchlist(movieId: Int, userId: String): Flow<Boolean> =
            flow.map { list -> list.any { it.movieId == movieId && it.userId == userId } }

        override suspend fun isMovieInWatchlistOnce(movieId: Int, userId: String): Boolean =
            records.containsKey(movieId to userId)

        override suspend fun insertMovie(movie: WatchlistEntity) {
            records[movie.movieId to movie.userId] = movie
            emit()
        }

        override suspend fun deleteMovie(movie: WatchlistEntity) {
            records.remove(movie.movieId to movie.userId)
            emit()
        }

        override suspend fun deleteMovieById(movieId: Int, userId: String) {
            records.remove(movieId to userId)
            emit()
        }

        override suspend fun deleteAllWatchlistForUser(userId: String) {
            records.entries.removeAll { it.key.second == userId }
            emit()
        }

        override fun getWatchlistCount(userId: String): Flow<Int> =
            flow.map { list -> list.count { it.userId == userId } }

        override suspend fun getWatchlistCountOnce(userId: String): Int =
            records.values.count { it.userId == userId }

        override suspend fun getAllWatchlistOnce(userId: String): List<WatchlistEntity> =
            records.values.filter { it.userId == userId }.sortedByDescending { it.savedTimestamp }
    }
}

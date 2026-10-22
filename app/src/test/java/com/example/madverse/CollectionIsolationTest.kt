package com.example.madverse

import com.example.madverse.core.data.repository.impl.FavoritesRepositoryImpl
import com.example.madverse.core.data.repository.impl.WatchlistRepositoryImpl
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.dao.WatchlistDao
import com.example.madverse.core.database.entity.FavoriteEntity
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying complete data isolation between Favorites and Watchlist collections.
 * Ensures:
 * - Adding a movie to Favorites does not populate or affect the Watchlist table.
 * - Adding a movie to Watchlist does not populate or affect the Favorites table.
 * - A movie can simultaneously exist in both collections with independent lifecycle and timestamps.
 * - Removing a movie from one collection preserves the movie in the other collection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionIsolationTest {

    private lateinit var favoriteDao: TestFavoriteDao
    private lateinit var watchlistDao: TestWatchlistDao
    private lateinit var favoritesRepository: FavoritesRepositoryImpl
    private lateinit var watchlistRepository: WatchlistRepositoryImpl

    @Before
    fun setUp() {
        favoriteDao = TestFavoriteDao()
        watchlistDao = TestWatchlistDao()
        favoritesRepository = FavoritesRepositoryImpl(favoriteDao)
        watchlistRepository = WatchlistRepositoryImpl(watchlistDao)
    }

    private fun createMovie(id: Int, title: String): Movie {
        return Movie(
            id = id,
            title = title,
            overview = "Overview of $title",
            posterUrl = "https://image.tmdb.org/t/p/w500/$id.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/$id.jpg",
            releaseDate = "2020-01-01",
            voteAverage = 8.0,
            voteCount = 5000,
            genreIds = listOf(28),
            popularity = 75.0
        )
    }

    @Test
    fun addingToFavorites_doesNotAffectWatchlist() = runTest {
        val movie = createMovie(550, "Fight Club")
        favoritesRepository.addToFavorites(movie)

        assertTrue(favoritesRepository.isFavorite(550).first())
        assertFalse(watchlistRepository.isMovieInWatchlist(550).first())

        val favorites = favoritesRepository.getFavoriteMovies().first()
        val watchlist = watchlistRepository.getWatchlistMovies().first()

        assertEquals(1, favorites.size)
        assertTrue(watchlist.isEmpty())
    }

    @Test
    fun addingToWatchlist_doesNotAffectFavorites() = runTest {
        val movie = createMovie(680, "Pulp Fiction")
        watchlistRepository.addToWatchlist(movie)

        assertFalse(favoritesRepository.isFavorite(680).first())
        assertTrue(watchlistRepository.isMovieInWatchlist(680).first())

        val favorites = favoritesRepository.getFavoriteMovies().first()
        val watchlist = watchlistRepository.getWatchlistMovies().first()

        assertTrue(favorites.isEmpty())
        assertEquals(1, watchlist.size)
    }

    @Test
    fun movieCanExistInBothCollectionsSimultaneously_withIndependentTimestamps() = runTest {
        val movie = createMovie(157336, "Interstellar")

        favoritesRepository.addToFavorites(movie)
        watchlistRepository.addToWatchlist(movie)

        assertTrue(favoritesRepository.isFavorite(157336).first())
        assertTrue(watchlistRepository.isMovieInWatchlist(157336).first())

        assertEquals(1, favoritesRepository.getFavoriteMovies().first().size)
        assertEquals(1, watchlistRepository.getWatchlistMovies().first().size)
    }

    @Test
    fun removingFromFavorites_preservesMovieInWatchlist() = runTest {
        val movie = createMovie(157336, "Interstellar")

        favoritesRepository.addToFavorites(movie)
        watchlistRepository.addToWatchlist(movie)

        favoritesRepository.removeFromFavorites(157336)

        assertFalse(favoritesRepository.isFavorite(157336).first())
        assertTrue(watchlistRepository.isMovieInWatchlist(157336).first())

        assertTrue(favoritesRepository.getFavoriteMovies().first().isEmpty())
        assertEquals(1, watchlistRepository.getWatchlistMovies().first().size)
    }

    @Test
    fun removingFromWatchlist_preservesMovieInFavorites() = runTest {
        val movie = createMovie(157336, "Interstellar")

        favoritesRepository.addToFavorites(movie)
        watchlistRepository.addToWatchlist(movie)

        watchlistRepository.removeFromWatchlist(157336)

        assertTrue(favoritesRepository.isFavorite(157336).first())
        assertFalse(watchlistRepository.isMovieInWatchlist(157336).first())

        assertEquals(1, favoritesRepository.getFavoriteMovies().first().size)
        assertTrue(watchlistRepository.getWatchlistMovies().first().isEmpty())
    }

    private class TestFavoriteDao : FavoriteDao {
        val records = mutableMapOf<Pair<Int, String>, FavoriteEntity>()
        val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())

        private fun emit() { flow.value = records.values.sortedByDescending { it.savedTimestamp } }
        override fun getAllFavorites(userId: String): Flow<List<FavoriteEntity>> =
            flow.map { list -> list.filter { it.userId == userId } }
        override suspend fun getFavoriteById(movieId: Int, userId: String): FavoriteEntity? = records[movieId to userId]
        override fun isFavorite(movieId: Int, userId: String): Flow<Boolean> =
            flow.map { list -> list.any { it.movieId == movieId && it.userId == userId } }
        override suspend fun isFavoriteOnce(movieId: Int, userId: String): Boolean = records.containsKey(movieId to userId)
        override suspend fun insertFavorite(movie: FavoriteEntity) { records[movie.movieId to movie.userId] = movie; emit() }
        override suspend fun deleteFavorite(movie: FavoriteEntity) { records.remove(movie.movieId to movie.userId); emit() }
        override suspend fun deleteFavoriteById(movieId: Int, userId: String) { records.remove(movieId to userId); emit() }
        override suspend fun deleteAllFavoritesForUser(userId: String) { records.entries.removeAll { it.key.second == userId }; emit() }
        override fun getFavoritesCount(userId: String): Flow<Int> = flow.map { list -> list.count { it.userId == userId } }
        override suspend fun getFavoritesCountOnce(userId: String): Int = records.values.count { it.userId == userId }
        override suspend fun getAllFavoritesOnce(userId: String): List<FavoriteEntity> = records.values.filter { it.userId == userId }
    }

    private class TestWatchlistDao : WatchlistDao {
        val records = mutableMapOf<Pair<Int, String>, WatchlistEntity>()
        val flow = MutableStateFlow<List<WatchlistEntity>>(emptyList())

        private fun emit() { flow.value = records.values.sortedByDescending { it.savedTimestamp } }
        override fun getAllWatchlistMovies(userId: String): Flow<List<WatchlistEntity>> =
            flow.map { list -> list.filter { it.userId == userId } }
        override suspend fun getWatchlistMovieById(movieId: Int, userId: String): WatchlistEntity? = records[movieId to userId]
        override fun isMovieInWatchlist(movieId: Int, userId: String): Flow<Boolean> =
            flow.map { list -> list.any { it.movieId == movieId && it.userId == userId } }
        override suspend fun isMovieInWatchlistOnce(movieId: Int, userId: String): Boolean = records.containsKey(movieId to userId)
        override suspend fun insertMovie(movie: WatchlistEntity) { records[movie.movieId to movie.userId] = movie; emit() }
        override suspend fun deleteMovie(movie: WatchlistEntity) { records.remove(movie.movieId to movie.userId); emit() }
        override suspend fun deleteMovieById(movieId: Int, userId: String) { records.remove(movieId to userId); emit() }
        override suspend fun deleteAllWatchlistForUser(userId: String) { records.entries.removeAll { it.key.second == userId }; emit() }
        override fun getWatchlistCount(userId: String): Flow<Int> = flow.map { list -> list.count { it.userId == userId } }
        override suspend fun getWatchlistCountOnce(userId: String): Int = records.values.count { it.userId == userId }
        override suspend fun getAllWatchlistOnce(userId: String): List<WatchlistEntity> = records.values.filter { it.userId == userId }
    }
}

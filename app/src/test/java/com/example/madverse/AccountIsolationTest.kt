package com.example.madverse

import com.example.madverse.core.data.repository.impl.FavoritesRepositoryImpl
import com.example.madverse.core.data.repository.impl.WatchlistRepositoryImpl
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.dao.WatchlistDao
import com.example.madverse.core.database.entity.FavoriteEntity
import com.example.madverse.core.database.entity.WatchlistEntity
import com.example.madverse.core.model.AuthState
import com.example.madverse.core.model.AuthUser
import com.example.madverse.core.model.Movie
import com.example.madverse.fakes.FakeAuthRepository
import com.example.madverse.fakes.FakeCloudSyncService
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

@OptIn(ExperimentalCoroutinesApi::class)
class AccountIsolationTest {

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var cloudSyncService: FakeCloudSyncService
    private lateinit var favoriteDao: TestAccountFavoriteDao
    private lateinit var watchlistDao: TestAccountWatchlistDao
    private lateinit var favoritesRepository: FavoritesRepositoryImpl
    private lateinit var watchlistRepository: WatchlistRepositoryImpl
    private lateinit var sessionManager: com.example.madverse.core.data.repository.impl.SessionManagerImpl
    private lateinit var testPrefDao: TestAccountPreferenceDao

    @Before
    fun setUp() {
        authRepository = FakeAuthRepository(AuthState.Guest)
        cloudSyncService = FakeCloudSyncService()
        favoriteDao = TestAccountFavoriteDao()
        watchlistDao = TestAccountWatchlistDao()
        testPrefDao = TestAccountPreferenceDao()

        sessionManager = com.example.madverse.core.data.repository.impl.SessionManagerImpl(
            authRepository = authRepository,
            favoriteDao = favoriteDao,
            watchlistDao = watchlistDao,
            userPreferenceDao = testPrefDao,
            syncServiceProvider = { cloudSyncService },
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )

        favoritesRepository = FavoritesRepositoryImpl(
            favoriteDao = favoriteDao,
            sessionManager = sessionManager,
            syncService = cloudSyncService
        )

        watchlistRepository = WatchlistRepositoryImpl(
            watchlistDao = watchlistDao,
            sessionManager = sessionManager,
            syncService = cloudSyncService
        )
    }

    private fun createMovie(id: Int, title: String): Movie =
        Movie(id, title, "", null, null, null, 8.0, 100, emptyList(), 1.0)

    @Test
    fun guestData_remainsIsolatedFromAuthenticatedUser() = runTest {
        // 1. Add movie while in Guest session
        favoritesRepository.addToFavorites(createMovie(101, "Guest Movie"))
        watchlistRepository.addToWatchlist(createMovie(201, "Guest Watch"))

        val guestFavs = favoritesRepository.getFavoriteMovies().first()
        val guestWatch = watchlistRepository.getWatchlistMovies().first()
        assertEquals(1, guestFavs.size)
        assertEquals("Guest Movie", guestFavs.first().title)
        assertEquals(1, guestWatch.size)

        // 2. Sign in as User A
        authRepository.setAuthenticated(AuthUser("user_a", "usera@madverse.com", "User A", null, true))

        // 3. User A should have an empty library (no guest data leaked)
        val userAFavs = favoritesRepository.getFavoriteMovies().first()
        val userAWatch = watchlistRepository.getWatchlistMovies().first()
        assertTrue(userAFavs.isEmpty())
        assertTrue(userAWatch.isEmpty())

        // 4. User A adds their own movie
        favoritesRepository.addToFavorites(createMovie(301, "User A Movie"))
        assertEquals(1, favoritesRepository.getFavoriteMovies().first().size)

        // 5. User A signs out -> Returns to Guest
        authRepository.setGuest()

        // 6. Guest's original data must still exist intact!
        val restoredGuestFavs = favoritesRepository.getFavoriteMovies().first()
        val restoredGuestWatch = watchlistRepository.getWatchlistMovies().first()
        assertEquals(1, restoredGuestFavs.size)
        assertEquals("Guest Movie", restoredGuestFavs.first().title)
        assertEquals(1, restoredGuestWatch.size)
    }

    @Test
    fun accountSwitching_isolatesUserAFromUserB() = runTest {
        // User A logs in and adds a favorite
        authRepository.setAuthenticated(AuthUser("user_a", "usera@madverse.com", "User A", null, true))
        favoritesRepository.addToFavorites(createMovie(500, "User A Exclusive"))

        assertEquals(1, favoritesRepository.getFavoriteMovies().first().size)
        assertTrue(favoritesRepository.isFavorite(500).first())

        // Switch to User B
        authRepository.setAuthenticated(AuthUser("user_b", "userb@madverse.com", "User B", null, true))

        // User B must NOT see User A's favorite
        assertFalse(favoritesRepository.isFavorite(500).first())
        assertTrue(favoritesRepository.getFavoriteMovies().first().isEmpty())

        // User B adds their own favorite
        favoritesRepository.addToFavorites(createMovie(600, "User B Exclusive"))
        assertEquals(1, favoritesRepository.getFavoriteMovies().first().size)
        assertEquals("User B Exclusive", favoritesRepository.getFavoriteMovies().first().first().title)

        // Switch back to User A
        authRepository.setAuthenticated(AuthUser("user_a", "usera@madverse.com", "User A", null, true))

        // User A sees their own movie and NOT User B's movie
        val userAFavs = favoritesRepository.getFavoriteMovies().first()
        assertEquals(1, userAFavs.size)
        assertEquals("User A Exclusive", userAFavs.first().title)
        assertFalse(favoritesRepository.isFavorite(600).first())
    }

    private class TestAccountFavoriteDao : FavoriteDao {
        val records = mutableListOf<FavoriteEntity>()
        val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())

        private fun emit() { flow.value = records.toList() }

        override fun getAllFavorites(userId: String): Flow<List<FavoriteEntity>> =
            flow.map { list -> list.filter { it.userId == userId } }

        override suspend fun getFavoriteById(movieId: Int, userId: String): FavoriteEntity? =
            records.find { it.movieId == movieId && it.userId == userId }

        override fun isFavorite(movieId: Int, userId: String): Flow<Boolean> =
            flow.map { list -> list.any { it.movieId == movieId && it.userId == userId } }

        override suspend fun isFavoriteOnce(movieId: Int, userId: String): Boolean =
            records.any { it.movieId == movieId && it.userId == userId }

        override suspend fun insertFavorite(movie: FavoriteEntity) {
            records.removeAll { it.movieId == movie.movieId && it.userId == movie.userId }
            records.add(movie)
            emit()
        }

        override suspend fun deleteFavorite(movie: FavoriteEntity) {
            records.removeAll { it.movieId == movie.movieId && it.userId == movie.userId }
            emit()
        }

        override suspend fun deleteFavoriteById(movieId: Int, userId: String) {
            records.removeAll { it.movieId == movieId && it.userId == userId }
            emit()
        }

        override suspend fun deleteAllFavoritesForUser(userId: String) {
            records.removeAll { it.userId == userId }
            emit()
        }

        override fun getFavoritesCount(userId: String): Flow<Int> =
            flow.map { list -> list.count { it.userId == userId } }

        override suspend fun getFavoritesCountOnce(userId: String): Int =
            records.count { it.userId == userId }

        override suspend fun getAllFavoritesOnce(userId: String): List<FavoriteEntity> =
            records.filter { it.userId == userId }
    }

    private class TestAccountWatchlistDao : WatchlistDao {
        val records = mutableListOf<WatchlistEntity>()
        val flow = MutableStateFlow<List<WatchlistEntity>>(emptyList())

        private fun emit() { flow.value = records.toList() }

        override fun getAllWatchlistMovies(userId: String): Flow<List<WatchlistEntity>> =
            flow.map { list -> list.filter { it.userId == userId } }

        override suspend fun getWatchlistMovieById(movieId: Int, userId: String): WatchlistEntity? =
            records.find { it.movieId == movieId && it.userId == userId }

        override fun isMovieInWatchlist(movieId: Int, userId: String): Flow<Boolean> =
            flow.map { list -> list.any { it.movieId == movieId && it.userId == userId } }

        override suspend fun isMovieInWatchlistOnce(movieId: Int, userId: String): Boolean =
            records.any { it.movieId == movieId && it.userId == userId }

        override suspend fun insertMovie(movie: WatchlistEntity) {
            records.removeAll { it.movieId == movie.movieId && it.userId == movie.userId }
            records.add(movie)
            emit()
        }

        override suspend fun deleteMovie(movie: WatchlistEntity) {
            records.removeAll { it.movieId == movie.movieId && it.userId == movie.userId }
            emit()
        }

        override suspend fun deleteMovieById(movieId: Int, userId: String) {
            records.removeAll { it.movieId == movieId && it.userId == userId }
            emit()
        }

        override suspend fun deleteAllWatchlistForUser(userId: String) {
            records.removeAll { it.userId == userId }
            emit()
        }

        override fun getWatchlistCount(userId: String): Flow<Int> =
            flow.map { list -> list.count { it.userId == userId } }

        override suspend fun getWatchlistCountOnce(userId: String): Int =
            records.count { it.userId == userId }

        override suspend fun getAllWatchlistOnce(userId: String): List<WatchlistEntity> =
            records.filter { it.userId == userId }
    }

    private class TestAccountPreferenceDao : com.example.madverse.core.database.dao.UserPreferenceDao {
        val records = mutableMapOf<String, com.example.madverse.core.database.entity.UserPreferenceEntity>()
        val flow = MutableStateFlow<Map<String, com.example.madverse.core.database.entity.UserPreferenceEntity>>(emptyMap())

        override fun getUserPreferenceProfile(userId: String): Flow<com.example.madverse.core.database.entity.UserPreferenceEntity?> =
            flow.map { it[userId] }

        override suspend fun getUserPreferenceProfileOnce(userId: String): com.example.madverse.core.database.entity.UserPreferenceEntity? =
            records[userId]

        override suspend fun saveUserPreferenceProfile(profile: com.example.madverse.core.database.entity.UserPreferenceEntity) {
            records[profile.userId] = profile
            flow.value = records.toMap()
        }

        override suspend fun clearUserPreferenceProfile(userId: String) {
            records.remove(userId)
            flow.value = records.toMap()
        }
    }
}

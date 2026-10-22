package com.example.madverse

import com.example.madverse.core.data.repository.impl.FavoritesRepositoryImpl
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.entity.FavoriteEntity
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
 * Unit tests for [FavoritesRepositoryImpl] and [FavoriteEntity].
 * Verifies:
 * - Reactive flow observation of saved favorites
 * - Add, remove, and query CRUD operations
 * - Duplicate prevention (OnConflict REPLACE by TMDB movie ID)
 * - Membership checking (both reactive Flow and one-time query)
 * - Metadata retention for offline viewing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesRepositoryTest {

    private lateinit var testDao: TestFavoriteDao
    private lateinit var repository: FavoritesRepositoryImpl

    @Before
    fun setUp() {
        testDao = TestFavoriteDao()
        repository = FavoritesRepositoryImpl(testDao)
    }

    private fun createMovie(
        id: Int = 550,
        title: String = "Fight Club",
        overview: String = "An insomniac office worker and a soap salesman build a club.",
        posterUrl: String? = "https://image.tmdb.org/t/p/w500/poster550.jpg",
        backdropUrl: String? = "https://image.tmdb.org/t/p/w780/backdrop550.jpg",
        releaseDate: String? = "1999-10-15",
        voteAverage: Double = 8.4
    ): Movie {
        return Movie(
            id = id,
            title = title,
            overview = overview,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            releaseDate = releaseDate,
            voteAverage = voteAverage,
            voteCount = 28000,
            genreIds = listOf(18, 53),
            popularity = 85.5
        )
    }

    @Test
    fun getFavoriteMovies_emptyByDefault_emitsEmptyList() = runTest {
        val favorites = repository.getFavoriteMovies().first()
        assertTrue(favorites.isEmpty())
    }

    @Test
    fun addToFavorites_savesMovie_emitsInFavoritesListWithMetadata() = runTest {
        val movie = createMovie(id = 550, title = "Fight Club")
        repository.addToFavorites(movie)

        val favorites = repository.getFavoriteMovies().first()
        assertEquals(1, favorites.size)

        val saved = favorites.first()
        assertEquals(550, saved.id)
        assertEquals("Fight Club", saved.title)
        assertEquals("An insomniac office worker and a soap salesman build a club.", saved.overview)
        assertEquals("https://image.tmdb.org/t/p/w500/poster550.jpg", saved.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w780/backdrop550.jpg", saved.backdropUrl)
        assertEquals("1999-10-15", saved.releaseDate)
        assertEquals(8.4, saved.voteAverage, 0.001)
        assertEquals("1999", saved.releaseYear)
        assertEquals("8.4", saved.formattedRating)
    }

    @Test
    fun addToFavorites_duplicateMovie_updatesRecordWithoutDuplication() = runTest {
        val movieV1 = createMovie(id = 550, title = "Fight Club", voteAverage = 8.0)
        val movieV2 = createMovie(id = 550, title = "Fight Club (Remastered)", voteAverage = 8.5)

        repository.addToFavorites(movieV1)
        repository.addToFavorites(movieV2)

        val favorites = repository.getFavoriteMovies().first()
        assertEquals(1, favorites.size)
        assertEquals("Fight Club (Remastered)", favorites.first().title)
        assertEquals(8.5, favorites.first().voteAverage, 0.001)
    }

    @Test
    fun removeFromFavorites_removesMovieFromFavoritesList() = runTest {
        val movie1 = createMovie(id = 550, title = "Fight Club")
        val movie2 = createMovie(id = 680, title = "Pulp Fiction")

        repository.addToFavorites(movie1)
        repository.addToFavorites(movie2)

        var favorites = repository.getFavoriteMovies().first()
        assertEquals(2, favorites.size)

        repository.removeFromFavorites(550)

        favorites = repository.getFavoriteMovies().first()
        assertEquals(1, favorites.size)
        assertEquals(680, favorites.first().id)
    }

    @Test
    fun removeFromFavorites_nonExistentMovie_doesNotCrashOrAlterList() = runTest {
        val movie = createMovie(id = 550)
        repository.addToFavorites(movie)

        repository.removeFromFavorites(999999)

        val favorites = repository.getFavoriteMovies().first()
        assertEquals(1, favorites.size)
    }

    @Test
    fun isFavorite_reactivelyReflectsMembership() = runTest {
        val movie = createMovie(id = 550)

        assertFalse(repository.isFavorite(550).first())
        assertFalse(repository.isFavoriteOnce(550))

        repository.addToFavorites(movie)

        assertTrue(repository.isFavorite(550).first())
        assertTrue(repository.isFavoriteOnce(550))

        repository.removeFromFavorites(550)

        assertFalse(repository.isFavorite(550).first())
        assertFalse(repository.isFavoriteOnce(550))
    }

    @Test
    fun getFavoriteById_returnsCorrectMovieOrNull() = runTest {
        val movie = createMovie(id = 550, title = "Fight Club")

        assertNull(repository.getFavoriteById(550))

        repository.addToFavorites(movie)

        val found = repository.getFavoriteById(550)
        assertNotNull(found)
        assertEquals(550, found?.id)
        assertEquals("Fight Club", found?.title)
    }

    @Test
    fun favoriteEntity_fromDomainAndToDomain_preservesAllMetadata() {
        val movie = createMovie(
            id = 157336,
            title = "Interstellar",
            overview = "Mankind was born on Earth. It was never meant to die here.",
            posterUrl = "https://image.tmdb.org/t/p/w500/interstellar.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/interstellar_backdrop.jpg",
            releaseDate = "2014-11-05",
            voteAverage = 8.6
        )

        val entity = FavoriteEntity.fromDomain(movie, savedTimestamp = 1234567890L)
        assertEquals(157336, entity.movieId)
        assertEquals("Interstellar", entity.title)
        assertEquals("Mankind was born on Earth. It was never meant to die here.", entity.overview)
        assertEquals("https://image.tmdb.org/t/p/w500/interstellar.jpg", entity.posterPath)
        assertEquals("https://image.tmdb.org/t/p/w780/interstellar_backdrop.jpg", entity.backdropPath)
        assertEquals("2014-11-05", entity.releaseDate)
        assertEquals(8.6, entity.voteAverage, 0.001)
        assertEquals(1234567890L, entity.savedTimestamp)

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
    fun getFavoriteMovies_orderedBySavedTimestampDescending() = runTest {
        val movie1 = createMovie(id = 1, title = "First Added")
        val movie2 = createMovie(id = 2, title = "Second Added")

        testDao.insertFavorite(FavoriteEntity.fromDomain(movie1, savedTimestamp = 1000L))
        testDao.insertFavorite(FavoriteEntity.fromDomain(movie2, savedTimestamp = 2000L))

        val favorites = repository.getFavoriteMovies().first()
        assertEquals(2, favorites.size)
        assertEquals("Second Added", favorites[0].title)
        assertEquals("First Added", favorites[1].title)
    }

    private class TestFavoriteDao : FavoriteDao {
        private val records = mutableMapOf<Pair<Int, String>, FavoriteEntity>()
        private val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())

        private fun emit() {
            flow.value = records.values.sortedByDescending { it.savedTimestamp }
        }

        override fun getAllFavorites(userId: String): Flow<List<FavoriteEntity>> =
            flow.map { list -> list.filter { it.userId == userId } }

        override suspend fun getFavoriteById(movieId: Int, userId: String): FavoriteEntity? =
            records[movieId to userId]

        override fun isFavorite(movieId: Int, userId: String): Flow<Boolean> =
            flow.map { list -> list.any { it.movieId == movieId && it.userId == userId } }

        override suspend fun isFavoriteOnce(movieId: Int, userId: String): Boolean =
            records.containsKey(movieId to userId)

        override suspend fun insertFavorite(movie: FavoriteEntity) {
            records[movie.movieId to movie.userId] = movie
            emit()
        }

        override suspend fun deleteFavorite(movie: FavoriteEntity) {
            records.remove(movie.movieId to movie.userId)
            emit()
        }

        override suspend fun deleteFavoriteById(movieId: Int, userId: String) {
            records.remove(movieId to userId)
            emit()
        }

        override suspend fun deleteAllFavoritesForUser(userId: String) {
            records.entries.removeAll { it.key.second == userId }
            emit()
        }

        override fun getFavoritesCount(userId: String): Flow<Int> =
            flow.map { list -> list.count { it.userId == userId } }

        override suspend fun getFavoritesCountOnce(userId: String): Int =
            records.values.count { it.userId == userId }

        override suspend fun getAllFavoritesOnce(userId: String): List<FavoriteEntity> =
            records.values.filter { it.userId == userId }.sortedByDescending { it.savedTimestamp }
    }
}

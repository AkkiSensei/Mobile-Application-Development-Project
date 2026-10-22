package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.common.dispatchers.IoDispatcher
import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.dao.PendingSyncDao
import com.example.madverse.core.database.dao.UserPreferenceDao
import com.example.madverse.core.database.dao.WatchlistDao
import com.example.madverse.core.database.entity.FavoriteEntity
import com.example.madverse.core.database.entity.PendingSyncEntity
import com.example.madverse.core.database.entity.UserPreferenceEntity
import com.example.madverse.core.database.entity.WatchlistEntity
import com.example.madverse.core.model.MergeSummary
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieEra
import com.example.madverse.core.model.MovieMood
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.SyncState
import com.example.madverse.core.model.UserPreferenceProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSyncServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val favoriteDao: FavoriteDao,
    private val watchlistDao: WatchlistDao,
    private val userPreferenceDao: UserPreferenceDao,
    private val pendingSyncDao: PendingSyncDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CloudSyncService {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var activeUserId: String? = null
    private var isApplyingRemoteUpdate: Boolean = false

    private var favoritesListener: ListenerRegistration? = null
    private var watchlistListener: ListenerRegistration? = null
    private var preferencesListener: ListenerRegistration? = null

    override fun startUserSession(userId: String) {
        if (userId.isBlank() || userId == "guest") {
            stopUserSession()
            return
        }

        if (userId == activeUserId) return

        stopUserSession()
        activeUserId = userId
        _syncState.value = SyncState.Syncing

        attachListeners(userId)
        scope.launch {
            processPendingSyncActions(userId)
            syncAll(userId)
        }
    }

    override fun stopUserSession() {
        favoritesListener?.remove()
        favoritesListener = null

        watchlistListener?.remove()
        watchlistListener = null

        preferencesListener?.remove()
        preferencesListener = null

        activeUserId = null
        _syncState.value = SyncState.Idle
    }

    private fun attachListeners(userId: String) {
        // Favorites listener
        favoritesListener = firestore.collection("users")
            .document(userId)
            .collection("favorites")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _syncState.value = SyncState.Error(error.localizedMessage ?: "Sync error")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                scope.launch {
                    isApplyingRemoteUpdate = true
                    try {
                        val remoteMovieIds = snapshot.documents.mapNotNull { it.getLong("movieId")?.toInt() }.toSet()
                        val localFavs = favoriteDao.getAllFavoritesOnce(userId)

                        // Insert or update remote documents
                        for (doc in snapshot.documents) {
                            val movieId = doc.getLong("movieId")?.toInt() ?: continue
                            val title = doc.getString("title") ?: "Untitled"
                            val posterPath = doc.getString("posterPath")
                            val backdropPath = doc.getString("backdropPath")
                            val releaseDate = doc.getString("releaseDate")
                            val voteAverage = doc.getDouble("voteAverage") ?: 0.0
                            val overview = doc.getString("overview")
                            val savedTimestamp = doc.getLong("savedTimestamp") ?: System.currentTimeMillis()

                            favoriteDao.insertFavorite(
                                FavoriteEntity(
                                    movieId = movieId,
                                    userId = userId,
                                    title = title,
                                    posterPath = posterPath,
                                    backdropPath = backdropPath,
                                    releaseDate = releaseDate,
                                    voteAverage = voteAverage,
                                    overview = overview,
                                    savedTimestamp = savedTimestamp
                                )
                            )
                        }

                        // Remove locally cached records that no longer exist remotely
                        for (local in localFavs) {
                            if (local.movieId !in remoteMovieIds) {
                                favoriteDao.deleteFavoriteById(local.movieId, userId)
                            }
                        }
                        _syncState.value = SyncState.Synced
                    } finally {
                        isApplyingRemoteUpdate = false
                    }
                }
            }

        // Watchlist listener
        watchlistListener = firestore.collection("users")
            .document(userId)
            .collection("watchlist")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _syncState.value = SyncState.Error(error.localizedMessage ?: "Sync error")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                scope.launch {
                    isApplyingRemoteUpdate = true
                    try {
                        val remoteMovieIds = snapshot.documents.mapNotNull { it.getLong("movieId")?.toInt() }.toSet()
                        val localWatch = watchlistDao.getAllWatchlistOnce(userId)

                        for (doc in snapshot.documents) {
                            val movieId = doc.getLong("movieId")?.toInt() ?: continue
                            val title = doc.getString("title") ?: "Untitled"
                            val posterPath = doc.getString("posterPath")
                            val backdropPath = doc.getString("backdropPath")
                            val releaseDate = doc.getString("releaseDate")
                            val voteAverage = doc.getDouble("voteAverage") ?: 0.0
                            val overview = doc.getString("overview")
                            val savedTimestamp = doc.getLong("savedTimestamp") ?: System.currentTimeMillis()

                            watchlistDao.insertMovie(
                                WatchlistEntity(
                                    movieId = movieId,
                                    userId = userId,
                                    title = title,
                                    posterPath = posterPath,
                                    backdropPath = backdropPath,
                                    releaseDate = releaseDate,
                                    voteAverage = voteAverage,
                                    overview = overview,
                                    savedTimestamp = savedTimestamp
                                )
                            )
                        }

                        for (local in localWatch) {
                            if (local.movieId !in remoteMovieIds) {
                                watchlistDao.deleteMovieById(local.movieId, userId)
                            }
                        }
                        _syncState.value = SyncState.Synced
                    } finally {
                        isApplyingRemoteUpdate = false
                    }
                }
            }

        // Preferences listener
        preferencesListener = firestore.collection("users")
            .document(userId)
            .collection("preferences")
            .document("current")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _syncState.value = SyncState.Error(error.localizedMessage ?: "Sync error")
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                scope.launch {
                    isApplyingRemoteUpdate = true
                    try {
                        val genreIds = (snapshot.get("genreIds") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
                        val mood = snapshot.getString("mood") ?: MovieMood.ANY.name
                        val era = snapshot.getString("era") ?: MovieEra.ANY.name
                        val runtimePreference = snapshot.getString("runtimePreference") ?: RuntimePreference.ANY.name
                        val preferredLanguage = snapshot.getString("preferredLanguage")
                        val minRatingThreshold = snapshot.getDouble("minRatingThreshold") ?: 6.0
                        val updatedTimestamp = snapshot.getLong("updatedTimestamp") ?: System.currentTimeMillis()

                        userPreferenceDao.saveUserPreferenceProfile(
                            UserPreferenceEntity(
                                userId = userId,
                                genreIdsCsv = genreIds.joinToString(","),
                                mood = mood,
                                era = era,
                                runtimePreference = runtimePreference,
                                preferredLanguage = preferredLanguage,
                                minRatingThreshold = minRatingThreshold,
                                updatedTimestamp = updatedTimestamp
                            )
                        )
                        _syncState.value = SyncState.Synced
                    } finally {
                        isApplyingRemoteUpdate = false
                    }
                }
            }
    }

    override suspend fun pushFavorite(userId: String, movie: Movie): Result<Unit> =
        withContext(ioDispatcher) {
            if (userId == "guest" || isApplyingRemoteUpdate) return@withContext Result.success(Unit)
            runCatching {
                val data = hashMapOf(
                    "movieId" to movie.id,
                    "title" to movie.title,
                    "posterPath" to movie.posterUrl,
                    "backdropPath" to movie.backdropUrl,
                    "releaseDate" to movie.releaseDate,
                    "voteAverage" to movie.voteAverage,
                    "overview" to movie.overview,
                    "savedTimestamp" to System.currentTimeMillis()
                )
                firestore.collection("users")
                    .document(userId)
                    .collection("favorites")
                    .document(movie.id.toString())
                    .set(data, SetOptions.merge())
                    .awaitTask()

                _syncState.value = SyncState.Synced
            }.onFailure {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "UPSERT",
                        entityType = "FAVORITE",
                        itemId = movie.id.toString()
                    )
                )
                _syncState.value = SyncState.OfflinePending
            }
        }

    override suspend fun deleteFavorite(userId: String, movieId: Int): Result<Unit> =
        withContext(ioDispatcher) {
            if (userId == "guest" || isApplyingRemoteUpdate) return@withContext Result.success(Unit)
            runCatching {
                firestore.collection("users")
                    .document(userId)
                    .collection("favorites")
                    .document(movieId.toString())
                    .delete()
                    .awaitTask()

                _syncState.value = SyncState.Synced
            }.onFailure {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "DELETE",
                        entityType = "FAVORITE",
                        itemId = movieId.toString()
                    )
                )
                _syncState.value = SyncState.OfflinePending
            }
        }

    override suspend fun pushWatchlist(userId: String, movie: Movie): Result<Unit> =
        withContext(ioDispatcher) {
            if (userId == "guest" || isApplyingRemoteUpdate) return@withContext Result.success(Unit)
            runCatching {
                val data = hashMapOf(
                    "movieId" to movie.id,
                    "title" to movie.title,
                    "posterPath" to movie.posterUrl,
                    "backdropPath" to movie.backdropUrl,
                    "releaseDate" to movie.releaseDate,
                    "voteAverage" to movie.voteAverage,
                    "overview" to movie.overview,
                    "savedTimestamp" to System.currentTimeMillis()
                )
                firestore.collection("users")
                    .document(userId)
                    .collection("watchlist")
                    .document(movie.id.toString())
                    .set(data, SetOptions.merge())
                    .awaitTask()

                _syncState.value = SyncState.Synced
            }.onFailure {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "UPSERT",
                        entityType = "WATCHLIST",
                        itemId = movie.id.toString()
                    )
                )
                _syncState.value = SyncState.OfflinePending
            }
        }

    override suspend fun deleteWatchlist(userId: String, movieId: Int): Result<Unit> =
        withContext(ioDispatcher) {
            if (userId == "guest" || isApplyingRemoteUpdate) return@withContext Result.success(Unit)
            runCatching {
                firestore.collection("users")
                    .document(userId)
                    .collection("watchlist")
                    .document(movieId.toString())
                    .delete()
                    .awaitTask()

                _syncState.value = SyncState.Synced
            }.onFailure {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "DELETE",
                        entityType = "WATCHLIST",
                        itemId = movieId.toString()
                    )
                )
                _syncState.value = SyncState.OfflinePending
            }
        }

    override suspend fun pushPreferences(
        userId: String,
        profile: UserPreferenceProfile
    ): Result<Unit> = withContext(ioDispatcher) {
        if (userId == "guest" || isApplyingRemoteUpdate) return@withContext Result.success(Unit)
        runCatching {
            val data = hashMapOf(
                "genreIds" to profile.selectedGenreIds.toList(),
                "mood" to profile.mood.name,
                "era" to profile.era.name,
                "runtimePreference" to profile.runtimePreference.name,
                "preferredLanguage" to profile.preferredLanguage,
                "minRatingThreshold" to profile.minRatingThreshold,
                "updatedTimestamp" to profile.updatedAtTimestamp
            )
            firestore.collection("users")
                .document(userId)
                .collection("preferences")
                .document("current")
                .set(data, SetOptions.merge())
                .awaitTask()

            _syncState.value = SyncState.Synced
        }.onFailure {
            pendingSyncDao.insertAction(
                PendingSyncEntity(
                    userId = userId,
                    actionType = "UPSERT",
                    entityType = "PREFERENCE",
                    itemId = "current"
                )
            )
            _syncState.value = SyncState.OfflinePending
        }
    }

    override suspend fun syncAll(userId: String): Result<Unit> = withContext(ioDispatcher) {
        if (userId == "guest") return@withContext Result.success(Unit)
        _syncState.value = SyncState.Syncing

        runCatching {
            // Update root user document
            firestore.collection("users")
                .document(userId)
                .set(
                    hashMapOf(
                        "uid" to userId,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .awaitTask()

            _syncState.value = SyncState.Synced
        }.onFailure { error ->
            _syncState.value = SyncState.Error(error.localizedMessage ?: "Sync error")
        }
    }

    override suspend fun mergeGuestDataIntoAccount(
        authenticatedUserId: String
    ): Result<MergeSummary> = withContext(ioDispatcher) {
        runCatching {
            require(authenticatedUserId.isNotBlank() && authenticatedUserId != "guest") {
                "Target merge account must be an authenticated user"
            }

            var favCount = 0
            var watchCount = 0
            var prefMerged = false

            // 1. Merge Favorites
            val guestFavs = favoriteDao.getAllFavoritesOnce("guest")
            val userFavs = favoriteDao.getAllFavoritesOnce(authenticatedUserId)
            val existingFavIds = userFavs.map { it.movieId }.toSet()

            for (fav in guestFavs) {
                if (fav.movieId !in existingFavIds) {
                    val mergedFav = fav.copy(userId = authenticatedUserId)
                    favoriteDao.insertFavorite(mergedFav)
                    pushFavorite(authenticatedUserId, mergedFav.toDomain())
                    favCount++
                }
            }

            // 2. Merge Watchlist
            val guestWatch = watchlistDao.getAllWatchlistOnce("guest")
            val userWatch = watchlistDao.getAllWatchlistOnce(authenticatedUserId)
            val existingWatchIds = userWatch.map { it.movieId }.toSet()

            for (w in guestWatch) {
                if (w.movieId !in existingWatchIds) {
                    val mergedWatch = w.copy(userId = authenticatedUserId)
                    watchlistDao.insertMovie(mergedWatch)
                    pushWatchlist(authenticatedUserId, mergedWatch.toDomain())
                    watchCount++
                }
            }

            // 3. Merge Preferences with deterministic resolution
            val guestPref = userPreferenceDao.getUserPreferenceProfileOnce("guest")
            val userPref = userPreferenceDao.getUserPreferenceProfileOnce(authenticatedUserId)

            val shouldMergePref = when {
                guestPref == null -> false
                userPref == null -> true
                userPref.genreIdsCsv.isBlank() && guestPref.genreIdsCsv.isNotBlank() -> true
                guestPref.updatedTimestamp > userPref.updatedTimestamp -> true
                else -> false
            }

            if (shouldMergePref && guestPref != null) {
                val mergedPref = guestPref.copy(userId = authenticatedUserId)
                userPreferenceDao.saveUserPreferenceProfile(mergedPref)
                pushPreferences(authenticatedUserId, mergedPref.toDomain())
                prefMerged = true
            }

            _syncState.value = SyncState.Synced
            MergeSummary(
                favoritesMerged = favCount,
                watchlistMerged = watchCount,
                preferencesMerged = prefMerged
            )
        }
    }

    override suspend fun deleteAllUserData(userId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            stopUserSession()

            // Delete remote favorites
            val favDocs = firestore.collection("users")
                .document(userId)
                .collection("favorites")
                .get()
                .awaitTask()
            for (doc in favDocs.documents) {
                doc.reference.delete().awaitTask()
            }

            // Delete remote watchlist
            val watchDocs = firestore.collection("users")
                .document(userId)
                .collection("watchlist")
                .get()
                .awaitTask()
            for (doc in watchDocs.documents) {
                doc.reference.delete().awaitTask()
            }

            // Delete remote preference
            firestore.collection("users")
                .document(userId)
                .collection("preferences")
                .document("current")
                .delete()
                .awaitTask()

            // Delete root user doc
            firestore.collection("users")
                .document(userId)
                .delete()
                .awaitTask()

            // Delete local account data in Room
            favoriteDao.deleteAllFavoritesForUser(userId)
            watchlistDao.deleteAllWatchlistForUser(userId)
            userPreferenceDao.clearUserPreferenceProfile(userId)
            pendingSyncDao.deleteActionsForUser(userId)

            _syncState.value = SyncState.Idle
        }
    }

    override suspend fun processPendingSyncActions(userId: String) = withContext(ioDispatcher) {
        if (userId == "guest") return@withContext
        val pending = pendingSyncDao.getPendingActionsForUser(userId)
        if (pending.isEmpty()) return@withContext

        for (action in pending) {
            try {
                when (action.entityType) {
                    "FAVORITE" -> {
                        val movieId = action.itemId.toIntOrNull() ?: continue
                        if (action.actionType == "DELETE") {
                            deleteFavorite(userId, movieId)
                        } else {
                            val local = favoriteDao.getFavoriteById(movieId, userId)
                            if (local != null) pushFavorite(userId, local.toDomain())
                        }
                    }
                    "WATCHLIST" -> {
                        val movieId = action.itemId.toIntOrNull() ?: continue
                        if (action.actionType == "DELETE") {
                            deleteWatchlist(userId, movieId)
                        } else {
                            val local = watchlistDao.getWatchlistMovieById(movieId, userId)
                            if (local != null) pushWatchlist(userId, local.toDomain())
                        }
                    }
                    "PREFERENCE" -> {
                        val local = userPreferenceDao.getUserPreferenceProfileOnce(userId)
                        if (local != null) pushPreferences(userId, local.toDomain())
                    }
                }
                pendingSyncDao.deleteActionById(action.id)
            } catch (_: Exception) {
                // Keep in pending queue for next sync retry
            }
        }
    }
}

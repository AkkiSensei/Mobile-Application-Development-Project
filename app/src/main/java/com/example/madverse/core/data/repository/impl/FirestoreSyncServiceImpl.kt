package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.common.dispatchers.IoDispatcher
import com.example.madverse.core.data.account.AccountDeletionJournal
import com.example.madverse.core.data.account.InMemoryAccountDeletionJournal
import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.sync.SyncPayloadSerializer
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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import com.example.madverse.core.database.DatabaseTransactionRunner
import com.example.madverse.core.database.DirectTransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSyncServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val favoriteDao: FavoriteDao,
    private val watchlistDao: WatchlistDao,
    private val userPreferenceDao: UserPreferenceDao,
    private val pendingSyncDao: PendingSyncDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val accountDeletionJournal: AccountDeletionJournal = InMemoryAccountDeletionJournal(),
    private val databaseTransactionRunner: DatabaseTransactionRunner = DirectTransactionRunner()
) : CloudSyncService {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    @Volatile
    private var activeUserId: String? = null
    private val sessionGeneration = AtomicLong(0)
    @Volatile
    private var currentSessionToken: Long = 0L

    private val drainMutex = Mutex()
    private var isApplyingRemoteUpdate: Boolean = false

    private var favoritesListener: ListenerRegistration? = null
    private var watchlistListener: ListenerRegistration? = null
    private var preferencesListener: ListenerRegistration? = null

    override fun startUserSession(userId: String) {
        if (userId.isBlank() || userId == "guest") {
            stopUserSession()
            return
        }

        scope.launch {
            if (accountDeletionJournal.isDeletionPending(userId)) {
                stopUserSession()
                return@launch
            }

            if (userId == activeUserId && currentSessionToken != 0L) return@launch

            stopUserSession()
            activeUserId = userId
            currentSessionToken = sessionGeneration.incrementAndGet()
            _syncState.value = SyncState.Syncing

            attachListeners(userId, currentSessionToken)
            processPendingSyncActions(userId)
            syncAll(userId)
        }
    }

    override fun stopUserSession() {
        sessionGeneration.incrementAndGet()
        currentSessionToken = 0L

        favoritesListener?.remove()
        favoritesListener = null

        watchlistListener?.remove()
        watchlistListener = null

        preferencesListener?.remove()
        preferencesListener = null

        activeUserId = null
        _syncState.value = SyncState.Idle
    }

    private fun attachListeners(userId: String, token: Long) {
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
                if (snapshot.metadata.hasPendingWrites()) {
                    // Local write in progress, do not perform full Room reconciliation yet
                    return@addSnapshotListener
                }

                scope.launch {
                    if (activeUserId != userId || currentSessionToken != token) return@launch
                    if (accountDeletionJournal.isDeletionPending(userId)) return@launch

                    isApplyingRemoteUpdate = true
                    try {
                        val pendingActions = pendingSyncDao.getPendingActionsForUser(userId)
                        val pendingUpsertIds = pendingActions
                            .filter { it.entityType == "FAVORITE" && it.actionType == "UPSERT" }
                            .mapNotNull { it.itemId.toIntOrNull() }
                            .toSet()
                        val pendingDeleteIds = pendingActions
                            .filter { it.entityType == "FAVORITE" && it.actionType == "DELETE" }
                            .mapNotNull { it.itemId.toIntOrNull() }
                            .toSet()

                        val remoteMovieIds = snapshot.documents.mapNotNull { it.getLong("movieId")?.toInt() }.toSet()
                        val localFavs = favoriteDao.getAllFavoritesOnce(userId)
                        val localFavMap = localFavs.associateBy { it.movieId }

                        // Filter to only modified/new favorites to avoid redundant Room disk writes and InvalidationTracker cascades
                        val toInsert = mutableListOf<FavoriteEntity>()
                        for (doc in snapshot.documents) {
                            val movieId = doc.getLong("movieId")?.toInt() ?: continue
                            if (movieId in pendingDeleteIds) {
                                // User deleted this locally while offline; do not resurrect it
                                continue
                            }
                            val title = doc.getString("title") ?: "Untitled"
                            val posterPath = doc.getString("posterPath")
                            val backdropPath = doc.getString("backdropPath")
                            val releaseDate = doc.getString("releaseDate")
                            val voteAverage = doc.getDouble("voteAverage") ?: 0.0
                            val overview = doc.getString("overview")
                            val savedTimestamp = doc.getLong("savedTimestamp") ?: System.currentTimeMillis()

                            val existing = localFavMap[movieId]
                            if (existing == null ||
                                existing.title != title ||
                                existing.posterPath != posterPath ||
                                existing.backdropPath != backdropPath ||
                                existing.releaseDate != releaseDate ||
                                existing.voteAverage != voteAverage ||
                                existing.overview != overview
                            ) {
                                toInsert.add(
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
                        }

                        // Remove locally cached records that no longer exist remotely, protecting local pending additions
                        val toDelete = mutableListOf<Int>()
                        for (local in localFavs) {
                            if (local.movieId !in remoteMovieIds) {
                                if (local.movieId in pendingUpsertIds) {
                                    // User added this locally; do not erase it!
                                    continue
                                }
                                toDelete.add(local.movieId)
                            }
                        }

                        if (toInsert.isNotEmpty() || toDelete.isNotEmpty()) {
                            databaseTransactionRunner.runInTransaction {
                                for (fav in toInsert) {
                                    favoriteDao.insertFavorite(fav)
                                }
                                for (movieId in toDelete) {
                                    favoriteDao.deleteFavoriteById(movieId, userId)
                                }
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
                if (snapshot.metadata.hasPendingWrites()) {
                    // Local write in progress, do not perform full Room reconciliation yet
                    return@addSnapshotListener
                }

                scope.launch {
                    if (activeUserId != userId || currentSessionToken != token) return@launch
                    if (accountDeletionJournal.isDeletionPending(userId)) return@launch

                    isApplyingRemoteUpdate = true
                    try {
                        val pendingActions = pendingSyncDao.getPendingActionsForUser(userId)
                        val pendingUpsertIds = pendingActions
                            .filter { it.entityType == "WATCHLIST" && it.actionType == "UPSERT" }
                            .mapNotNull { it.itemId.toIntOrNull() }
                            .toSet()
                        val pendingDeleteIds = pendingActions
                            .filter { it.entityType == "WATCHLIST" && it.actionType == "DELETE" }
                            .mapNotNull { it.itemId.toIntOrNull() }
                            .toSet()

                        val remoteMovieIds = snapshot.documents.mapNotNull { it.getLong("movieId")?.toInt() }.toSet()
                        val localWatch = watchlistDao.getAllWatchlistOnce(userId)
                        val localWatchMap = localWatch.associateBy { it.movieId }

                        val toInsert = mutableListOf<WatchlistEntity>()
                        for (doc in snapshot.documents) {
                            val movieId = doc.getLong("movieId")?.toInt() ?: continue
                            if (movieId in pendingDeleteIds) {
                                // User deleted this locally while offline; do not resurrect it
                                continue
                            }
                            val title = doc.getString("title") ?: "Untitled"
                            val posterPath = doc.getString("posterPath")
                            val backdropPath = doc.getString("backdropPath")
                            val releaseDate = doc.getString("releaseDate")
                            val voteAverage = doc.getDouble("voteAverage") ?: 0.0
                            val overview = doc.getString("overview")
                            val savedTimestamp = doc.getLong("savedTimestamp") ?: System.currentTimeMillis()

                            val existing = localWatchMap[movieId]
                            if (existing == null ||
                                existing.title != title ||
                                existing.posterPath != posterPath ||
                                existing.backdropPath != backdropPath ||
                                existing.releaseDate != releaseDate ||
                                existing.voteAverage != voteAverage ||
                                existing.overview != overview
                            ) {
                                toInsert.add(
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
                        }

                        val toDelete = mutableListOf<Int>()
                        for (local in localWatch) {
                            if (local.movieId !in remoteMovieIds) {
                                if (local.movieId in pendingUpsertIds) {
                                    // User added this locally; do not erase it!
                                    continue
                                }
                                toDelete.add(local.movieId)
                            }
                        }

                        if (toInsert.isNotEmpty() || toDelete.isNotEmpty()) {
                            databaseTransactionRunner.runInTransaction {
                                for (watch in toInsert) {
                                    watchlistDao.insertMovie(watch)
                                }
                                for (movieId in toDelete) {
                                    watchlistDao.deleteMovieById(movieId, userId)
                                }
                            }
                        }
                        _syncState.value = SyncState.Synced
                    } finally {
                        isApplyingRemoteUpdate = false
                    }
                }
            }

        // Preferences listener (Contract: /users/{uid}/preferences/current)
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
                if (snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }

                scope.launch {
                    if (activeUserId != userId || currentSessionToken != token) return@launch
                    if (accountDeletionJournal.isDeletionPending(userId)) return@launch

                    val pendingActions = pendingSyncDao.getPendingActionsForUser(userId)
                    val hasPendingPrefMutation = pendingActions.any { it.entityType == "PREFERENCE" }
                    if (hasPendingPrefMutation) {
                        // Pending local preference mutations take precedence until synchronized
                        return@launch
                    }

                    isApplyingRemoteUpdate = true
                    try {
                        val genreIds = (snapshot.get("genreIds") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
                        val mood = snapshot.getString("mood") ?: MovieMood.ANY.name
                        val era = snapshot.getString("era") ?: MovieEra.ANY.name
                        val runtimePreference = snapshot.getString("runtimePreference") ?: RuntimePreference.ANY.name
                        val preferredLanguage = snapshot.getString("preferredLanguage")
                        val minRatingThreshold = snapshot.getDouble("minRatingThreshold") ?: 6.0
                        val updatedTimestamp = snapshot.getLong("updatedTimestamp") ?: System.currentTimeMillis()
                        val genreCsv = genreIds.joinToString(",")

                        val currentLocalPref = userPreferenceDao.getUserPreferenceProfileOnce(userId)
                        if (currentLocalPref == null ||
                            currentLocalPref.genreIdsCsv != genreCsv ||
                            currentLocalPref.mood != mood ||
                            currentLocalPref.era != era ||
                            currentLocalPref.runtimePreference != runtimePreference ||
                            currentLocalPref.preferredLanguage != preferredLanguage ||
                            currentLocalPref.minRatingThreshold != minRatingThreshold
                        ) {
                            userPreferenceDao.saveUserPreferenceProfile(
                                UserPreferenceEntity(
                                    userId = userId,
                                    genreIdsCsv = genreCsv,
                                    mood = mood,
                                    era = era,
                                    runtimePreference = runtimePreference,
                                    preferredLanguage = preferredLanguage,
                                    minRatingThreshold = minRatingThreshold,
                                    updatedTimestamp = updatedTimestamp
                                )
                            )
                        }
                        _syncState.value = SyncState.Synced
                    } finally {
                        isApplyingRemoteUpdate = false
                    }
                }
            }
    }

    override suspend fun pushFavorite(
        userId: String,
        movie: Movie,
        enqueueOnFailure: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        if (userId == "guest" || isApplyingRemoteUpdate) return@withContext Result.success(Unit)
        if (accountDeletionJournal.isDeletionPending(userId)) {
            return@withContext Result.failure(IllegalStateException("Account deletion pending for $userId"))
        }

        try {
            val classification = pushFavoriteRemote(userId, movie)
            when (classification) {
                is OperationClassification.Success -> {
                    _syncState.value = SyncState.Synced
                    Result.success(Unit)
                }
                else -> {
                    if (enqueueOnFailure) {
                        pendingSyncDao.insertAction(
                            PendingSyncEntity(
                                userId = userId,
                                actionType = "UPSERT",
                                entityType = "FAVORITE",
                                itemId = movie.id.toString(),
                                payloadJson = SyncPayloadSerializer.movieToJson(movie)
                            )
                        )
                    }
                    _syncState.value = SyncState.OfflinePending
                    Result.failure(IOException("Failed to push favorite: $classification"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (enqueueOnFailure) {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "UPSERT",
                        entityType = "FAVORITE",
                        itemId = movie.id.toString(),
                        payloadJson = SyncPayloadSerializer.movieToJson(movie)
                    )
                )
            }
            _syncState.value = SyncState.OfflinePending
            Result.failure(e)
        }
    }

    override suspend fun deleteFavorite(
        userId: String,
        movieId: Int,
        enqueueOnFailure: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        if (userId == "guest" || isApplyingRemoteUpdate) return@withContext Result.success(Unit)
        if (accountDeletionJournal.isDeletionPending(userId)) {
            return@withContext Result.failure(IllegalStateException("Account deletion pending for $userId"))
        }

        try {
            val classification = deleteFavoriteRemote(userId, movieId)
            when (classification) {
                is OperationClassification.Success -> {
                    _syncState.value = SyncState.Synced
                    Result.success(Unit)
                }
                else -> {
                    if (enqueueOnFailure) {
                        pendingSyncDao.insertAction(
                            PendingSyncEntity(
                                userId = userId,
                                actionType = "DELETE",
                                entityType = "FAVORITE",
                                itemId = movieId.toString()
                            )
                        )
                    }
                    _syncState.value = SyncState.OfflinePending
                    Result.failure(IOException("Failed to delete favorite: $classification"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (enqueueOnFailure) {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "DELETE",
                        entityType = "FAVORITE",
                        itemId = movieId.toString()
                    )
                )
            }
            _syncState.value = SyncState.OfflinePending
            Result.failure(e)
        }
    }

    override suspend fun pushWatchlist(
        userId: String,
        movie: Movie,
        enqueueOnFailure: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        if (userId == "guest" || isApplyingRemoteUpdate) return@withContext Result.success(Unit)
        if (accountDeletionJournal.isDeletionPending(userId)) {
            return@withContext Result.failure(IllegalStateException("Account deletion pending for $userId"))
        }

        try {
            val classification = pushWatchlistRemote(userId, movie)
            when (classification) {
                is OperationClassification.Success -> {
                    _syncState.value = SyncState.Synced
                    Result.success(Unit)
                }
                else -> {
                    if (enqueueOnFailure) {
                        pendingSyncDao.insertAction(
                            PendingSyncEntity(
                                userId = userId,
                                actionType = "UPSERT",
                                entityType = "WATCHLIST",
                                itemId = movie.id.toString(),
                                payloadJson = SyncPayloadSerializer.movieToJson(movie)
                            )
                        )
                    }
                    _syncState.value = SyncState.OfflinePending
                    Result.failure(IOException("Failed to push watchlist: $classification"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (enqueueOnFailure) {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "UPSERT",
                        entityType = "WATCHLIST",
                        itemId = movie.id.toString(),
                        payloadJson = SyncPayloadSerializer.movieToJson(movie)
                    )
                )
            }
            _syncState.value = SyncState.OfflinePending
            Result.failure(e)
        }
    }

    override suspend fun deleteWatchlist(
        userId: String,
        movieId: Int,
        enqueueOnFailure: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        if (userId == "guest" || isApplyingRemoteUpdate) return@withContext Result.success(Unit)
        if (accountDeletionJournal.isDeletionPending(userId)) {
            return@withContext Result.failure(IllegalStateException("Account deletion pending for $userId"))
        }

        try {
            val classification = deleteWatchlistRemote(userId, movieId)
            when (classification) {
                is OperationClassification.Success -> {
                    _syncState.value = SyncState.Synced
                    Result.success(Unit)
                }
                else -> {
                    if (enqueueOnFailure) {
                        pendingSyncDao.insertAction(
                            PendingSyncEntity(
                                userId = userId,
                                actionType = "DELETE",
                                entityType = "WATCHLIST",
                                itemId = movieId.toString()
                            )
                        )
                    }
                    _syncState.value = SyncState.OfflinePending
                    Result.failure(IOException("Failed to delete watchlist: $classification"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (enqueueOnFailure) {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "DELETE",
                        entityType = "WATCHLIST",
                        itemId = movieId.toString()
                    )
                )
            }
            _syncState.value = SyncState.OfflinePending
            Result.failure(e)
        }
    }

    override suspend fun pushPreferences(
        userId: String,
        profile: UserPreferenceProfile,
        enqueueOnFailure: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        if (userId == "guest" || isApplyingRemoteUpdate) return@withContext Result.success(Unit)
        if (accountDeletionJournal.isDeletionPending(userId)) {
            return@withContext Result.failure(IllegalStateException("Account deletion pending for $userId"))
        }

        try {
            val classification = pushPreferencesRemote(userId, profile)
            when (classification) {
                is OperationClassification.Success -> {
                    _syncState.value = SyncState.Synced
                    Result.success(Unit)
                }
                else -> {
                    if (enqueueOnFailure) {
                        pendingSyncDao.insertAction(
                            PendingSyncEntity(
                                userId = userId,
                                actionType = "UPSERT",
                                entityType = "PREFERENCE",
                                itemId = "current",
                                payloadJson = SyncPayloadSerializer.preferenceToJson(profile)
                            )
                        )
                    }
                    _syncState.value = SyncState.OfflinePending
                    Result.failure(IOException("Failed to push preferences: $classification"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (enqueueOnFailure) {
                pendingSyncDao.insertAction(
                    PendingSyncEntity(
                        userId = userId,
                        actionType = "UPSERT",
                        entityType = "PREFERENCE",
                        itemId = "current",
                        payloadJson = SyncPayloadSerializer.preferenceToJson(profile)
                    )
                )
            }
            _syncState.value = SyncState.OfflinePending
            Result.failure(e)
        }
    }

    override suspend fun syncAll(userId: String): Result<Unit> = withContext(ioDispatcher) {
        if (userId == "guest") return@withContext Result.success(Unit)
        if (accountDeletionJournal.isDeletionPending(userId)) {
            return@withContext Result.failure(IllegalStateException("Account deletion pending for $userId"))
        }
        _syncState.value = SyncState.Syncing

        try {
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
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            _syncState.value = SyncState.Error(error.localizedMessage ?: "Sync error")
            Result.failure(error)
        }
    }

    override suspend fun mergeGuestDataIntoAccount(
        authenticatedUserId: String
    ): Result<MergeSummary> = withContext(ioDispatcher) {
        try {
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

            val toMergeFavs = mutableListOf<FavoriteEntity>()
            for (fav in guestFavs) {
                if (fav.movieId !in existingFavIds) {
                    val mergedFav = fav.copy(userId = authenticatedUserId)
                    toMergeFavs.add(mergedFav)
                    favCount++
                }
            }

            // 2. Merge Watchlist
            val guestWatch = watchlistDao.getAllWatchlistOnce("guest")
            val userWatch = watchlistDao.getAllWatchlistOnce(authenticatedUserId)
            val existingWatchIds = userWatch.map { it.movieId }.toSet()

            val toMergeWatch = mutableListOf<WatchlistEntity>()
            for (w in guestWatch) {
                if (w.movieId !in existingWatchIds) {
                    val mergedWatch = w.copy(userId = authenticatedUserId)
                    toMergeWatch.add(mergedWatch)
                    watchCount++
                }
            }

            if (toMergeFavs.isNotEmpty() || toMergeWatch.isNotEmpty()) {
                databaseTransactionRunner.runInTransaction {
                    for (fav in toMergeFavs) {
                        favoriteDao.insertFavorite(fav)
                    }
                    for (watch in toMergeWatch) {
                        watchlistDao.insertMovie(watch)
                    }
                }
            }

            for (fav in toMergeFavs) {
                pushFavorite(authenticatedUserId, fav.toDomain())
            }
            for (watch in toMergeWatch) {
                pushWatchlist(authenticatedUserId, watch.toDomain())
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
            Result.success(
                MergeSummary(
                    favoritesMerged = favCount,
                    watchlistMerged = watchCount,
                    preferencesMerged = prefMerged
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllUserData(userId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
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
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun processPendingSyncActions(userId: String) = withContext(ioDispatcher) {
        if (userId.isBlank() || userId == "guest") return@withContext
        if (accountDeletionJournal.isDeletionPending(userId)) return@withContext

        drainMutex.withLock {
            val currentToken = currentSessionToken
            if (activeUserId != userId || currentToken != sessionGeneration.get()) return@withLock

            val pending = pendingSyncDao.getPendingActionsForUser(userId)
            if (pending.isEmpty()) return@withLock

            for (action in pending) {
                // Pre-execution session & deletion verification
                if (activeUserId != userId || currentSessionToken != currentToken) return@withLock
                if (accountDeletionJournal.isDeletionPending(userId)) return@withLock
                if (action.userId != userId) continue

                val result: OperationClassification = when (action.entityType) {
                    "FAVORITE" -> {
                        val movieId = action.itemId.toIntOrNull()
                        if (movieId == null) {
                            OperationClassification.UnsupportedOrMalformed("Invalid itemId '${action.itemId}' for FAVORITE")
                        } else if (action.actionType == "DELETE") {
                            deleteFavoriteRemote(userId, movieId)
                        } else if (action.actionType == "UPSERT") {
                            val movie = action.payloadJson?.let { SyncPayloadSerializer.jsonToMovie(it) }
                                ?: favoriteDao.getFavoriteById(movieId, userId)?.toDomain()
                            if (movie != null) {
                                pushFavoriteRemote(userId, movie)
                            } else {
                                // Obsolete upsert for local item that has since been purged
                                OperationClassification.Success
                            }
                        } else {
                            OperationClassification.UnsupportedOrMalformed("Unsupported actionType '${action.actionType}' for FAVORITE")
                        }
                    }
                    "WATCHLIST" -> {
                        val movieId = action.itemId.toIntOrNull()
                        if (movieId == null) {
                            OperationClassification.UnsupportedOrMalformed("Invalid itemId '${action.itemId}' for WATCHLIST")
                        } else if (action.actionType == "DELETE") {
                            deleteWatchlistRemote(userId, movieId)
                        } else if (action.actionType == "UPSERT") {
                            val movie = action.payloadJson?.let { SyncPayloadSerializer.jsonToMovie(it) }
                                ?: watchlistDao.getWatchlistMovieById(movieId, userId)?.toDomain()
                            if (movie != null) {
                                pushWatchlistRemote(userId, movie)
                            } else {
                                OperationClassification.Success
                            }
                        } else {
                            OperationClassification.UnsupportedOrMalformed("Unsupported actionType '${action.actionType}' for WATCHLIST")
                        }
                    }
                    "PREFERENCE" -> {
                        if (action.actionType == "DELETE") {
                            deletePreferencesRemote(userId)
                        } else if (action.actionType == "UPSERT") {
                            val pref = action.payloadJson?.let { SyncPayloadSerializer.jsonToPreference(it) }
                                ?: userPreferenceDao.getUserPreferenceProfileOnce(userId)?.toDomain()
                            if (pref != null) {
                                pushPreferencesRemote(userId, pref)
                            } else {
                                OperationClassification.Success
                            }
                        } else {
                            OperationClassification.UnsupportedOrMalformed("Unsupported actionType '${action.actionType}' for PREFERENCE")
                        }
                    }
                    else -> {
                        OperationClassification.UnsupportedOrMalformed("Unsupported entityType '${action.entityType}'")
                    }
                }

                // Post-execution session & deletion verification
                if (activeUserId != userId || currentSessionToken != currentToken) return@withLock
                if (accountDeletionJournal.isDeletionPending(userId)) return@withLock

                when (result) {
                    is OperationClassification.Success -> {
                        // Exact acknowledgement only upon confirmed remote completion
                        pendingSyncDao.deleteActionById(action.id)
                        _syncState.value = SyncState.Synced
                    }
                    is OperationClassification.TransientError -> {
                        _syncState.value = SyncState.OfflinePending
                        break // Pause drain, retain queued actions for retry
                    }
                    is OperationClassification.AuthRequired -> {
                        _syncState.value = SyncState.Error("Authentication required: ${result.message}")
                        break // Pause drain, wait for re-authentication
                    }
                    is OperationClassification.PermissionDenied -> {
                        // Do NOT treat as sign-in expiry; retain intent and report actionable diagnostics
                        _syncState.value = SyncState.Error("Permission denied: ${result.message}. Queue paused.")
                        break // Pause drain
                    }
                    is OperationClassification.UnsupportedOrMalformed -> {
                        // Never acknowledge unsupported or malformed as success; retain and pause
                        _syncState.value = SyncState.Error("Sync paused: ${result.message}. Action retained.")
                        break // Pause drain
                    }
                }
            }
        }
    }

    private suspend fun pushFavoriteRemote(userId: String, movie: Movie): OperationClassification {
        return try {
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
            OperationClassification.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private suspend fun deleteFavoriteRemote(userId: String, movieId: Int): OperationClassification {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("favorites")
                .document(movieId.toString())
                .delete()
                .awaitTask()
            OperationClassification.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private suspend fun pushWatchlistRemote(userId: String, movie: Movie): OperationClassification {
        return try {
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
            OperationClassification.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private suspend fun deleteWatchlistRemote(userId: String, movieId: Int): OperationClassification {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("watchlist")
                .document(movieId.toString())
                .delete()
                .awaitTask()
            OperationClassification.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private suspend fun pushPreferencesRemote(userId: String, profile: UserPreferenceProfile): OperationClassification {
        return try {
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
            OperationClassification.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private suspend fun deletePreferencesRemote(userId: String): OperationClassification {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("preferences")
                .document("current")
                .delete()
                .awaitTask()
            OperationClassification.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private fun classifyException(e: Exception): OperationClassification {
        if (e is FirebaseFirestoreException) {
            return when (e.code) {
                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    OperationClassification.AuthRequired(e.localizedMessage ?: "Unauthenticated")
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    OperationClassification.PermissionDenied(e.localizedMessage ?: "Permission denied")
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    OperationClassification.TransientError(e.localizedMessage ?: "Network unavailable")
                else ->
                    OperationClassification.TransientError(e.localizedMessage ?: "Firestore error: ${e.code}")
            }
        }
        if (e is IOException) {
            return OperationClassification.TransientError(e.localizedMessage ?: "I/O connection error")
        }
        return OperationClassification.TransientError(e.localizedMessage ?: "Unknown error")
    }

    private sealed class OperationClassification {
        object Success : OperationClassification()
        data class TransientError(val message: String) : OperationClassification()
        data class AuthRequired(val message: String) : OperationClassification()
        data class PermissionDenied(val message: String) : OperationClassification()
        data class UnsupportedOrMalformed(val message: String) : OperationClassification()
    }
}

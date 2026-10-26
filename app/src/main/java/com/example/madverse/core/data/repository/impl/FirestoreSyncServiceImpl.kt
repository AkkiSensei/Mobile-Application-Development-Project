package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.common.dispatchers.IoDispatcher
import com.example.madverse.core.data.account.AccountDeletionJournal
import com.example.madverse.core.data.account.DeletionStage
import com.example.madverse.core.data.repository.CloudSyncService
import com.example.madverse.core.data.repository.AuthRepository
import com.example.madverse.core.data.sync.*
import com.example.madverse.core.database.DatabaseTransactionRunner
import com.example.madverse.core.database.dao.*
import com.example.madverse.core.database.entity.*
import com.example.madverse.core.model.*
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSyncServiceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val favoriteDao: FavoriteDao,
    private val watchlistDao: WatchlistDao,
    private val userPreferenceDao: UserPreferenceDao,
    private val pendingSyncDao: PendingSyncDao,
    private val authRepository: AuthRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val accountDeletionJournal: AccountDeletionJournal,
    private val databaseTransactionRunner: DatabaseTransactionRunner
) : CloudSyncService {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private val generation = AtomicLong()
    private val remoteRevisions = listOf("favorites", "watchlist", "preferences").associateWith { AtomicLong() }
    @Volatile private var activeUserId: String? = null
    private var sessionJob: Job? = null
    @Volatile private var worker: SyncQueueWorker? = null
    private val listeners = mutableListOf<ListenerRegistration>()
    private val reconciler = SyncSnapshotReconciler(favoriteDao, watchlistDao, userPreferenceDao, pendingSyncDao, databaseTransactionRunner)

    @Synchronized
    override fun startUserSession(userId: String) {
        if (userId.isBlank() || userId == "guest") {
            stopUserSession()
            return
        }
        if (activeUserId == userId && sessionJob?.isActive == true) {
            worker?.requestDrain()
            return
        }
        stopUserSession()
        activeUserId = userId
        val token = generation.incrementAndGet()
        val job = SupervisorJob()
        sessionJob = job
        val sessionScope = CoroutineScope(job + ioDispatcher)
        sessionScope.launch {
            if (!isCurrent(userId, token)) return@launch
            val newWorker = SyncQueueWorker(userId, pendingSyncDao, databaseTransactionRunner,
                isCurrent = { isCurrent(userId, token) },
                publish = { state -> publish(userId, token, state) },
                execute = { row, op ->
                    val result = executeRemote(userId, token, row, op)
                    if (result == ReplayResult.Success) remoteRevisions.getValue(op.collection).incrementAndGet()
                    result
                })
            synchronized(this@FirestoreSyncServiceImpl) {
                if (activeUserId != userId || generation.get() != token) return@launch
                worker = newWorker
                attachListeners(userId, token, sessionScope)
                newWorker.start(sessionScope)
            }
        }
    }

    @Synchronized
    override fun stopUserSession() {
        generation.incrementAndGet()
        activeUserId = null
        worker = null
        sessionJob?.cancel()
        sessionJob = null
        listeners.forEach { it.remove() }
        listeners.clear()
        _syncState.value = SyncState.Idle
    }

    private suspend fun isCurrent(uid: String, token: Long): Boolean {
        if (activeUserId != uid || generation.get() != token || authRepository.currentUserId != uid) return false
        val deleting = accountDeletionJournal.isDeletionPending(uid)
        return !deleting && activeUserId == uid && generation.get() == token && authRepository.currentUserId == uid
    }

    @Synchronized
    private fun publish(uid: String, token: Long, state: SyncState) {
        if (activeUserId == uid && generation.get() == token) _syncState.value = state
    }

    private fun attachListeners(uid: String, token: Long, sessionScope: CoroutineScope) {
        for (collection in listOf("favorites", "watchlist")) {
            val remoteRevision = remoteRevisions.getValue(collection)
            val snapshotSequence = AtomicLong()
            listeners += firestore.collection("users").document(uid).collection(collection)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    val revision = remoteRevision.get()
                    val sequence = snapshotSequence.incrementAndGet()
                    sessionScope.launch {
                        if (!isCurrent(uid, token)) return@launch
                        if (error != null) {
                            publish(uid, token, SyncState.Error("Cloud listener unavailable; saved changes are retained."))
                            worker?.requestDrain()
                            return@launch
                        }
                        // Cache omissions are not authoritative deletions. Metadata events deliver server confirmation.
                        if (snapshot == null || snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) return@launch
                        try {
                            val rows = snapshot.documents.map { doc ->
                                val id = doc.getLong("movieId")?.toInt()
                                require(id != null && id > 0 && doc.id == id.toString())
                                FavoriteEntity(id, uid, doc.getString("title") ?: "Untitled",
                                    doc.getString("posterPath"), doc.getString("backdropPath"), doc.getString("releaseDate"),
                                    doc.getDouble("voteAverage") ?: 0.0, doc.getString("overview"), doc.getLong("savedTimestamp") ?: 0L)
                            }
                            val current: suspend () -> Boolean = { isCurrent(uid, token) && revision == remoteRevision.get() && sequence == snapshotSequence.get() }
                            if (collection == "favorites") reconciler.favorites(uid, rows, current)
                            else reconciler.watchlist(uid, rows.map { WatchlistEntity(it.movieId, uid, it.title, it.posterPath, it.backdropPath, it.releaseDate, it.voteAverage, it.overview, it.savedTimestamp) }, current)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            publish(uid, token, SyncState.Error("Cloud snapshot could not be applied; local data retained."))
                        }
                    }
                }
        }
        val remoteRevision = remoteRevisions.getValue("preferences")
        val snapshotSequence = AtomicLong()
        listeners += firestore.collection("users").document(uid).collection("preferences").document("current")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                val revision = remoteRevision.get()
                val sequence = snapshotSequence.incrementAndGet()
                sessionScope.launch {
                    if (!isCurrent(uid, token)) return@launch
                    if (error != null) {
                        publish(uid, token, SyncState.Error("Preference listener unavailable; saved changes are retained."))
                        worker?.requestDrain()
                        return@launch
                    }
                    if (snapshot == null || snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) return@launch
                    try {
                        val remote = if (!snapshot.exists()) null else UserPreferenceEntity(
                            userId = uid,
                            genreIdsCsv = ((snapshot.get("genreIds") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()).joinToString(","),
                            mood = snapshot.getString("mood") ?: MovieMood.ANY.name,
                            era = snapshot.getString("era") ?: MovieEra.ANY.name,
                            runtimePreference = snapshot.getString("runtimePreference") ?: RuntimePreference.ANY.name,
                            preferredLanguage = snapshot.getString("preferredLanguage"),
                            minRatingThreshold = snapshot.getDouble("minRatingThreshold") ?: 6.0,
                            updatedTimestamp = snapshot.getLong("updatedTimestamp") ?: 0L)
                        reconciler.preferences(uid, remote) { isCurrent(uid, token) && revision == remoteRevision.get() && sequence == snapshotSequence.get() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        publish(uid, token, SyncState.Error("Cloud preferences could not be applied; local data retained."))
                    }
                }
            }
    }

    // Compatibility entry points also use the durable queue; success means accepted locally, not uploaded.
    // Repositories enqueue atomically themselves and only request a drain.
    private suspend fun enqueue(row: PendingSyncEntity): Result<Unit> = withContext(ioDispatcher) {
        try {
            require(row.userId.isNotBlank() && row.userId != "guest")
            check(!accountDeletionJournal.isDeletionPending(row.userId)) { "Account deletion pending" }
            pendingSyncDao.insertAction(row)
            processPendingSyncActions(row.userId)
            Result.success(Unit)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun pushFavorite(userId: String, movie: Movie, enqueueOnFailure: Boolean) =
        enqueue(PendingSyncEntity(userId = userId, actionType = "UPSERT", entityType = "FAVORITE", itemId = movie.id.toString(), payloadJson = SyncPayloadSerializer.movieToJson(movie)))
    override suspend fun deleteFavorite(userId: String, movieId: Int, enqueueOnFailure: Boolean) =
        enqueue(PendingSyncEntity(userId = userId, actionType = "DELETE", entityType = "FAVORITE", itemId = movieId.toString()))
    override suspend fun pushWatchlist(userId: String, movie: Movie, enqueueOnFailure: Boolean) =
        enqueue(PendingSyncEntity(userId = userId, actionType = "UPSERT", entityType = "WATCHLIST", itemId = movie.id.toString(), payloadJson = SyncPayloadSerializer.movieToJson(movie)))
    override suspend fun deleteWatchlist(userId: String, movieId: Int, enqueueOnFailure: Boolean) =
        enqueue(PendingSyncEntity(userId = userId, actionType = "DELETE", entityType = "WATCHLIST", itemId = movieId.toString()))
    override suspend fun pushPreferences(userId: String, profile: UserPreferenceProfile, enqueueOnFailure: Boolean) =
        enqueue(PendingSyncEntity(userId = userId, actionType = "UPSERT", entityType = "PREFERENCE", itemId = "current", payloadJson = SyncPayloadSerializer.preferenceToJson(profile)))

    override suspend fun processPendingSyncActions(userId: String) {
        if (activeUserId == userId) worker?.requestDrain()
    }

    override suspend fun syncAll(userId: String): Result<Unit> {
        if (accountDeletionJournal.isDeletionPending(userId)) return Result.failure(IllegalStateException("Account deletion pending"))
        if (activeUserId != userId) return Result.failure(IllegalStateException("Session is no longer active"))
        processPendingSyncActions(userId)
        return Result.success(Unit)
    }

    override suspend fun mergeGuestDataIntoAccount(authenticatedUserId: String): Result<MergeSummary> = withContext(ioDispatcher) {
        try {
            val uid = authenticatedUserId
            require(uid.isNotBlank() && uid != "guest")
            check(authRepository.currentUserId == uid) { "Merge target is not signed in" }
            check(!accountDeletionJournal.isDeletionPending(uid))
            val summary = databaseTransactionRunner.runInTransaction {
                var favorites = 0
                var watchlist = 0
                var preferences = false
                val pending = pendingSyncDao.getPendingActionsForUser(uid)
                val favoriteIds = favoriteDao.getAllFavoritesOnce(uid).map { it.movieId.toString() }.toSet() + pending.filter { it.entityType == "FAVORITE" }.map { it.itemId }
                for (row in favoriteDao.getAllFavoritesOnce("guest")) {
                    if (row.movieId.toString() !in favoriteIds) {
                        favoriteDao.insertFavorite(row.copy(userId = uid))
                        pendingSyncDao.insertAction(PendingSyncEntity(userId = uid, actionType = "UPSERT", entityType = "FAVORITE", itemId = row.movieId.toString(), payloadJson = SyncPayloadSerializer.movieToJson(row.toDomain())))
                        favorites++
                    }
                }
                val watchIds = watchlistDao.getAllWatchlistOnce(uid).map { it.movieId.toString() }.toSet() + pending.filter { it.entityType == "WATCHLIST" }.map { it.itemId }
                for (row in watchlistDao.getAllWatchlistOnce("guest")) {
                    if (row.movieId.toString() !in watchIds) {
                        watchlistDao.insertMovie(row.copy(userId = uid))
                        pendingSyncDao.insertAction(PendingSyncEntity(userId = uid, actionType = "UPSERT", entityType = "WATCHLIST", itemId = row.movieId.toString(), payloadJson = SyncPayloadSerializer.movieToJson(row.toDomain())))
                        watchlist++
                    }
                }
                val guestPref = userPreferenceDao.getUserPreferenceProfileOnce("guest")
                val userPref = userPreferenceDao.getUserPreferenceProfileOnce(uid)
                if (guestPref != null && pending.none { it.entityType == "PREFERENCE" } &&
                    (userPref == null || (userPref.genreIdsCsv.isBlank() && guestPref.genreIdsCsv.isNotBlank()) || guestPref.updatedTimestamp > userPref.updatedTimestamp)) {
                    userPreferenceDao.saveUserPreferenceProfile(guestPref.copy(userId = uid))
                    pendingSyncDao.insertAction(PendingSyncEntity(userId = uid, actionType = "UPSERT", entityType = "PREFERENCE", itemId = "current", payloadJson = SyncPayloadSerializer.preferenceToJson(guestPref.toDomain())))
                    preferences = true
                }
                check(!accountDeletionJournal.isDeletionPending(uid))
                check(authRepository.currentUserId == uid) { "Merge session changed" }
                MergeSummary(favorites, watchlist, preferences)
            }
            processPendingSyncActions(uid)
            Result.success(summary)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun purgeRemoteUserData(userId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            check(accountDeletionJournal.isDeletionPending(userId)) { "Deletion journal required" }
            stopUserSession()
            withTimeout(30000) {
                check(authRepository.currentUserId == userId) { "Deletion target is not signed in" }
                // Cancelling our await cannot retract an SDK write. Settle this client's already
                // submitted writes before enumerating the data to purge, or preserve recovery state.
                firestore.waitForPendingWrites().awaitTask()
                for (collection in listOf("favorites", "watchlist", "preferences")) {
                    check(authRepository.currentUserId == userId) { "Deletion target is not signed in" }
                    // A cache-only query cannot establish that the collection was purged.
                    val snapshot = firestore.collection("users").document(userId).collection(collection).get(Source.SERVER).awaitTask()
                    for (doc in snapshot.documents) {
                        check(authRepository.currentUserId == userId) { "Deletion session changed" }
                        doc.reference.delete().awaitTask()
                    }
                }
                check(authRepository.currentUserId == userId) { "Deletion session changed" }
                firestore.collection("users").document(userId).delete().awaitTask()
            }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) { Result.failure(e) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun deleteAllUserData(userId: String): Result<Unit> = withContext(ioDispatcher) {
        // This is now only the local stage, after the coordinator records BOTH remote confirmations.
        try {
            check(accountDeletionJournal.getJournalEntry(userId)?.stage in setOf(DeletionStage.REMOTE_AUTH_CONFIRMED, DeletionStage.ACCOUNT_DATA_CLEANED)) { "Remote deletion is not confirmed" }
            databaseTransactionRunner.runInTransaction {
                favoriteDao.deleteAllFavoritesForUser(userId)
                watchlistDao.deleteAllWatchlistForUser(userId)
                userPreferenceDao.clearUserPreferenceProfile(userId)
                pendingSyncDao.deleteActionsForUser(userId)
            }
            Result.success(Unit)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    private suspend fun executeRemote(uid: String, token: Long, row: PendingSyncEntity, operation: SyncOperation): ReplayResult = when (operation.collection) {
        "favorites" -> if (operation.delete) deleteFavoriteRemote(uid, token, operation.documentId.toInt()) else pushFavoriteRemote(uid, token, requireNotNull(operation.movie), row.createdAt)
        "watchlist" -> if (operation.delete) deleteWatchlistRemote(uid, token, operation.documentId.toInt()) else pushWatchlistRemote(uid, token, requireNotNull(operation.movie), row.createdAt)
        else -> if (operation.delete) deletePreferencesRemote(uid, token) else pushPreferencesRemote(uid, token, requireNotNull(operation.preference))
    }

    private fun <T> beginRemote(uid: String, token: Long, request: () -> com.google.android.gms.tasks.Task<T>): com.google.android.gms.tasks.Task<T> =
        synchronized(this) {
            if (activeUserId != uid || generation.get() != token || sessionJob?.isActive != true || authRepository.currentUserId != uid) {
                throw CancellationException("Sync session changed before remote dispatch")
            }
            request()
        }

    private suspend fun pushFavoriteRemote(userId: String, token: Long, movie: Movie, timestamp: Long): ReplayResult {
        return try {
            val data = hashMapOf(
                "movieId" to movie.id,
                "title" to movie.title,
                "posterPath" to movie.posterUrl,
                "backdropPath" to movie.backdropUrl,
                "releaseDate" to movie.releaseDate,
                "voteAverage" to movie.voteAverage,
                "overview" to movie.overview,
                "savedTimestamp" to timestamp
            )
            beginRemote(userId, token) {
                firestore.collection("users")
                .document(userId)
                .collection("favorites")
                .document(movie.id.toString())
                .set(data, SetOptions.merge())
            }.awaitTask()
            ReplayResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private suspend fun deleteFavoriteRemote(userId: String, token: Long, movieId: Int): ReplayResult {
        return try {
            beginRemote(userId, token) {
                firestore.collection("users")
                .document(userId)
                .collection("favorites")
                .document(movieId.toString())
                .delete()
            }.awaitTask()
            ReplayResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private suspend fun pushWatchlistRemote(userId: String, token: Long, movie: Movie, timestamp: Long): ReplayResult {
        return try {
            val data = hashMapOf(
                "movieId" to movie.id,
                "title" to movie.title,
                "posterPath" to movie.posterUrl,
                "backdropPath" to movie.backdropUrl,
                "releaseDate" to movie.releaseDate,
                "voteAverage" to movie.voteAverage,
                "overview" to movie.overview,
                "savedTimestamp" to timestamp
            )
            beginRemote(userId, token) {
                firestore.collection("users")
                .document(userId)
                .collection("watchlist")
                .document(movie.id.toString())
                .set(data, SetOptions.merge())
            }.awaitTask()
            ReplayResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private suspend fun deleteWatchlistRemote(userId: String, token: Long, movieId: Int): ReplayResult {
        return try {
            beginRemote(userId, token) {
                firestore.collection("users")
                .document(userId)
                .collection("watchlist")
                .document(movieId.toString())
                .delete()
            }.awaitTask()
            ReplayResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private suspend fun pushPreferencesRemote(userId: String, token: Long, profile: UserPreferenceProfile): ReplayResult {
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
            beginRemote(userId, token) {
                firestore.collection("users")
                .document(userId)
                .collection("preferences")
                .document("current")
                .set(data, SetOptions.merge())
            }.awaitTask()
            ReplayResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private suspend fun deletePreferencesRemote(userId: String, token: Long): ReplayResult {
        return try {
            beginRemote(userId, token) {
                firestore.collection("users")
                .document(userId)
                .collection("preferences")
                .document("current")
                .delete()
            }.awaitTask()
            ReplayResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private fun classifyException(e: Exception): ReplayResult = when ((e as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> ReplayResult.AuthRequired
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ReplayResult.PermissionDenied
        else -> ReplayResult.Transient
    }
}

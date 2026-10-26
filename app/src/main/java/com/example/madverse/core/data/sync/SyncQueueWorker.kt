package com.example.madverse.core.data.sync

import com.example.madverse.core.database.DatabaseTransactionRunner
import com.example.madverse.core.database.dao.PendingSyncDao
import com.example.madverse.core.database.entity.PendingSyncEntity
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.SyncState
import com.example.madverse.core.model.UserPreferenceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.random.Random

internal sealed interface ReplayResult {
    data object Success : ReplayResult
    data object Transient : ReplayResult
    data object AuthRequired : ReplayResult
    data object PermissionDenied : ReplayResult
}

/** A validated immutable queue intent; never reconstruct an old write from today's Room row. */
internal data class SyncOperation(
    val collection: String,
    val documentId: String,
    val movie: Movie? = null,
    val preference: UserPreferenceProfile? = null,
    val delete: Boolean = false
) {
    companion object {
        fun decode(row: PendingSyncEntity): SyncOperation? {
            val collection = when (row.entityType) {
                "FAVORITE" -> "favorites"
                "WATCHLIST" -> "watchlist"
                "PREFERENCE" -> "preferences"
                else -> return null
            }
            if (collection == "preferences") {
                if (row.itemId != "current") return null
            } else if (row.itemId.toIntOrNull()?.let { it > 0 && it.toString() == row.itemId } != true) {
                return null
            }
            return when (row.actionType) {
                "DELETE" -> if (row.payloadJson == null) SyncOperation(collection, row.itemId, delete = true) else null
                "UPSERT" -> {
                    val payload = row.payloadJson ?: return null
                    if (collection == "preferences") {
                        val pref = SyncPayloadSerializer.jsonToPreference(payload) ?: return null
                        SyncOperation(collection, row.itemId, preference = pref)
                    } else {
                        val movie = SyncPayloadSerializer.jsonToMovie(payload) ?: return null
                        if (movie.id.toString() != row.itemId) return null
                        SyncOperation(collection, row.itemId, movie = movie)
                    }
                }
                else -> null
            }
        }
    }
}

/** One channel and one owning job per session. Cancelling the job cancels its actual remote await. */
internal class SyncQueueWorker(
    private val userId: String,
    private val dao: PendingSyncDao,
    private val transactions: DatabaseTransactionRunner,
    private val isCurrent: suspend () -> Boolean,
    private val publish: (SyncState) -> Unit,
    private val execute: suspend (PendingSyncEntity, SyncOperation) -> ReplayResult,
    private val backoff: (Int) -> Long = { attempt ->
        val cap = (5000L * (1L shl attempt.coerceIn(0, 4))).coerceAtMost(60000L)
        Random.nextLong(cap / 2, cap + 1)
    }
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)

    fun requestDrain() { requests.trySend(Unit) }

    fun start(scope: CoroutineScope): Job = scope.launch {
        requestDrain() // Startup/restoration always drains durable intent.
        for (ignored in requests) {
            var attempt = 0
            try {
                while (isCurrent()) {
                    val row = dao.getNextPendingActionForUser(userId)
                    if (row == null) {
                        if (isCurrent()) publish(SyncState.Synced)
                        break
                    }
                    val operation = SyncOperation.decode(row)
                    if (operation == null) {
                        publish(SyncState.Error("Sync paused at queue action ${row.id}: invalid or missing payload/operation. Action retained."))
                        break // The original Room row is the durable blocker; never silently discard it.
                    }
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent()) break
                    publish(SyncState.Syncing)
                    val result = try {
                        withTimeout(30000) { execute(row, operation) }
                    } catch (_: TimeoutCancellationException) {
                        ReplayResult.Transient
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        ReplayResult.Transient
                    }
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent()) break
                    when (result) {
                        ReplayResult.Success -> {
                            transactions.runInTransaction {
                                currentCoroutineContext().ensureActive()
                                if (isCurrent()) dao.deleteActionById(row.id)
                                currentCoroutineContext().ensureActive()
                            }
                            attempt = 0
                        }
                        ReplayResult.Transient, ReplayResult.AuthRequired -> {
                            publish(if (result == ReplayResult.AuthRequired) SyncState.Error("Authentication unavailable. Saved changes will retry.") else SyncState.OfflinePending)
                            // Delay is followed by another attempt, including repeated failures with Wi-Fi still up.
                            delay(backoff(attempt.coerceAtMost(4)))
                            attempt = (attempt + 1).coerceAtMost(4)
                        }
                        ReplayResult.PermissionDenied -> {
                            publish(SyncState.Error("Firestore permission denied. Saved changes retained; retry sync after rules/access are repaired."))
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A local read/ack failure must not kill the session's only consumer.
                if (isCurrent()) publish(SyncState.Error("Local sync storage is unavailable. Saved actions will retry."))
                delay(backoff(4))
                requestDrain()
            }
        }
    }
}

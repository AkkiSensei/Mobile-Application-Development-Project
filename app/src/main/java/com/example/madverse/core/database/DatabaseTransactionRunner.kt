package com.example.madverse.core.database

import androidx.room.withTransaction

/**
 * Contract for executing database operations inside an atomic transaction.
 * Ensures local entity mutations and sync queue insertions succeed or roll back together.
 */
interface DatabaseTransactionRunner {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}

/**
 * Production implementation using Room's [RoomDatabase.withTransaction].
 */
class RoomTransactionRunner(
    private val database: MADVerseDatabase
) : DatabaseTransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return database.withTransaction {
            block()
        }
    }
}

/**
 * Direct execution runner used in unit tests where in-memory fakes or mock DAOs are used.
 */
class DirectTransactionRunner : DatabaseTransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return block()
    }
}

package com.example.madverse.core.data.repository.impl

import com.example.madverse.core.data.repository.RecentlyViewedRepository
import com.example.madverse.core.database.DatabaseTransactionRunner
import com.example.madverse.core.database.DirectTransactionRunner
import com.example.madverse.core.database.dao.RecentlyViewedDao
import com.example.madverse.core.database.entity.RecentlyViewedEntity
import com.example.madverse.core.model.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentlyViewedRepositoryImpl @Inject constructor(
    private val recentlyViewedDao: RecentlyViewedDao,
    private val transactionRunner: DatabaseTransactionRunner
) : RecentlyViewedRepository {

    // Overload for isolated unit tests
    constructor(recentlyViewedDao: RecentlyViewedDao) : this(
        recentlyViewedDao = recentlyViewedDao,
        transactionRunner = DirectTransactionRunner()
    )

    override fun getRecentlyViewed(limit: Int): Flow<List<Movie>> {
        return recentlyViewedDao.getRecentlyViewed(limit.coerceIn(0, 20)).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRecentlyViewedOnce(limit: Int): List<Movie> {
        return recentlyViewedDao.getRecentlyViewedOnce(limit.coerceIn(0, 20)).map { it.toDomain() }
    }

    override suspend fun recordMovieView(movie: Movie) {
        require(movie.id > 0) { "History requires a valid movie ID" }
        transactionRunner.runInTransaction {
            // Preserve actual view order when wall-clock timestamps tie or move backwards.
            val latest = recentlyViewedDao.getRecentlyViewedOnce(1).firstOrNull()?.viewedTimestamp ?: 0L
            val timestamp = maxOf(System.currentTimeMillis(), latest.coerceAtMost(Long.MAX_VALUE - 1) + 1)
            val entity = RecentlyViewedEntity.fromDomain(movie, timestamp)
            recentlyViewedDao.insertOrUpdate(entity)
            recentlyViewedDao.trimToMaxLimit(keepCount = 20)
        }
    }

    override suspend fun clearHistory() {
        recentlyViewedDao.clearAll()
    }
}

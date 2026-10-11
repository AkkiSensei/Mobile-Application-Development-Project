package com.example.madverse.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.dao.UserPreferenceDao
import com.example.madverse.core.database.dao.WatchlistDao
import com.example.madverse.core.database.entity.FavoriteEntity
import com.example.madverse.core.database.entity.UserPreferenceEntity
import com.example.madverse.core.database.entity.WatchlistEntity

@Database(
    entities = [
        WatchlistEntity::class,
        FavoriteEntity::class,
        UserPreferenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MADVerseDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun userPreferenceDao(): UserPreferenceDao

    companion object {
        const val DATABASE_NAME = "madverse_database.db"
        const val DATABASE_VERSION = 1

        /**
         * Clean Room migrations registry.
         * Ensures non-destructive schema evolution so user favorites, watchlist records,
         * and quiz preferences are never dropped or wiped.
         */
        val ALL_MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()
    }
}

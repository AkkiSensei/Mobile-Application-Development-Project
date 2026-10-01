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
}

package com.example.madverse.core.database

import android.content.Context
import androidx.room.Room
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.dao.UserPreferenceDao
import com.example.madverse.core.database.dao.WatchlistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MADVerseDatabase {
        return Room.databaseBuilder(
            context,
            MADVerseDatabase::class.java,
            MADVerseDatabase.DATABASE_NAME
        )
            .addMigrations(*MADVerseDatabase.ALL_MIGRATIONS)
            .build()
    }

    @Provides
    @Singleton
    fun provideWatchlistDao(database: MADVerseDatabase): WatchlistDao {
        return database.watchlistDao()
    }

    @Provides
    @Singleton
    fun provideFavoriteDao(database: MADVerseDatabase): FavoriteDao {
        return database.favoriteDao()
    }

    @Provides
    @Singleton
    fun provideUserPreferenceDao(database: MADVerseDatabase): UserPreferenceDao {
        return database.userPreferenceDao()
    }

    @Provides
    @Singleton
    fun providePendingSyncDao(database: MADVerseDatabase): com.example.madverse.core.database.dao.PendingSyncDao {
        return database.pendingSyncDao()
    }
}

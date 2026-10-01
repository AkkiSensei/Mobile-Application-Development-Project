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

    private const val DATABASE_NAME = "madverse_database.db"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MADVerseDatabase {
        return Room.databaseBuilder(
            context,
            MADVerseDatabase::class.java,
            DATABASE_NAME
        )
            .fallbackToDestructiveMigration(true)
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
}

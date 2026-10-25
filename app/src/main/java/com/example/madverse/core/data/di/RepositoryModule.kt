package com.example.madverse.core.data.di

import com.example.madverse.core.data.repository.FavoritesRepository
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.data.repository.WatchlistRepository
import com.example.madverse.core.data.repository.impl.FavoritesRepositoryImpl
import com.example.madverse.core.data.repository.impl.MovieRepositoryImpl
import com.example.madverse.core.data.repository.impl.QuizRepositoryImpl
import com.example.madverse.core.data.repository.impl.WatchlistRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds each repository interface to its concrete implementation.
 *
 * Uses `@Binds` (preferred over `@Provides` for abstract bindings) to let Hilt
 * resolve the interface when it appears as a constructor parameter anywhere in the
 * dependency graph.
 *
 * All implementations are `@Singleton` — one instance per Application process.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMovieRepository(
        impl: MovieRepositoryImpl
    ): MovieRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(
        impl: FavoritesRepositoryImpl
    ): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindWatchlistRepository(
        impl: WatchlistRepositoryImpl
    ): WatchlistRepository

    @Binds
    @Singleton
    abstract fun bindQuizRepository(
        impl: QuizRepositoryImpl
    ): QuizRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: com.example.madverse.core.data.repository.impl.FirebaseAuthRepositoryImpl
    ): com.example.madverse.core.data.repository.AuthRepository

    @Binds
    @Singleton
    abstract fun bindSessionManager(
        impl: com.example.madverse.core.data.repository.impl.SessionManagerImpl
    ): com.example.madverse.core.data.repository.SessionManager

    @Binds
    @Singleton
    abstract fun bindCloudSyncService(
        impl: com.example.madverse.core.data.repository.impl.FirestoreSyncServiceImpl
    ): com.example.madverse.core.data.repository.CloudSyncService

    @Binds
    @Singleton
    abstract fun bindRecentlyViewedRepository(
        impl: com.example.madverse.core.data.repository.impl.RecentlyViewedRepositoryImpl
    ): com.example.madverse.core.data.repository.RecentlyViewedRepository
}

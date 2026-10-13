package com.example.madverse.core.domain.di

import com.example.madverse.core.domain.recommendation.DeterministicRecommendationEngine
import com.example.madverse.core.domain.recommendation.RecommendationEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds the domain-layer contracts to their concrete implementations.
 *
 * [RecommendationEngine] is bound to [DeterministicRecommendationEngine] which
 * operates entirely locally without any AI or network dependency.
 *
 * If an [com.example.madverse.core.domain.recommendation.AiRecommendationEnhancer]
 * is implemented in the future, it should be bound here with a separate `@Binds`
 * method and injected as an optional decorator.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    abstract fun bindRecommendationInsightProvider(
        impl: com.example.madverse.core.domain.recommendation.LocalRecommendationInsightProvider
    ): com.example.madverse.core.domain.recommendation.RecommendationInsightProvider

    @Binds
    @Singleton
    abstract fun bindRecommendationEngine(
        impl: DeterministicRecommendationEngine
    ): RecommendationEngine
}

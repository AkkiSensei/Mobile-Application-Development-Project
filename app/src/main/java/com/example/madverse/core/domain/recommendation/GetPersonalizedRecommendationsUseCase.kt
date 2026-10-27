package com.example.madverse.core.domain.recommendation

import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.model.ScoredMovieRecommendation
import com.example.madverse.core.model.UserPreferenceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain UseCase for generating personalized movie recommendations.
 *
 * Clean Integration Interface for Saanvi's recommendations screen and Shraddha's quiz:
 * 1. Reads persisted [UserPreferenceProfile] from Shraddha's [QuizRepository].
 * 2. Fetches candidate pool from TMDB via Ritunjay's [MovieRepository.discoverCandidateMovies].
 * 3. Deterministically scores and ranks candidates via Ritunjay's [RecommendationEngine].
 *
 * Keeps network retrieval decoupled from scoring algorithms and guarantees 100% reproducible results.
 */
@Singleton
class GetPersonalizedRecommendationsUseCase @Inject constructor(
    private val quizRepository: QuizRepository,
    private val movieRepository: MovieRepository,
    private val recommendationEngine: RecommendationEngine
) {

    /**
     * Fetches candidates and computes top recommendations for the currently saved quiz profile.
     */
    operator fun invoke(limit: Int = 10): Flow<Resource<List<ScoredMovieRecommendation>>> = flow {
        emit(Resource.Loading)

        val profile = quizRepository.getUserPreferenceProfile().first()

        movieRepository.discoverCandidateMovies(
            genreIds = profile.selectedGenreIds,
            startYear = profile.era.startYear,
            endYear = profile.era.endYear,
            minRating = profile.effectiveMinRating
        ).collect { resource ->
            when (resource) {
                is Resource.Loading -> emit(Resource.Loading)
                is Resource.Error -> emit(Resource.Error(resource.message, resource.cause))
                is Resource.Success -> {
                    val eligibleCandidates = resource.data.filter {
                        it.voteAverage.isFinite() && it.voteAverage in profile.effectiveMinRating..10.0
                    }
                    val ranked = recommendationEngine.scoreAndRank(
                        profile = profile,
                        candidates = eligibleCandidates,
                        limit = limit
                    )
                    emit(Resource.Success(ranked))
                }
            }
        }
    }

    /**
     * Computes recommendations for an explicitly supplied profile (e.g. for quiz preview).
     */
    fun invokeWithProfile(
        profile: UserPreferenceProfile,
        limit: Int = 10
    ): Flow<Resource<List<ScoredMovieRecommendation>>> = flow {
        emit(Resource.Loading)

        movieRepository.discoverCandidateMovies(
            genreIds = profile.selectedGenreIds,
            startYear = profile.era.startYear,
            endYear = profile.era.endYear,
            minRating = profile.effectiveMinRating
        ).collect { resource ->
            when (resource) {
                is Resource.Loading -> emit(Resource.Loading)
                is Resource.Error -> emit(Resource.Error(resource.message, resource.cause))
                is Resource.Success -> {
                    val eligibleCandidates = resource.data.filter {
                        it.voteAverage.isFinite() && it.voteAverage in profile.effectiveMinRating..10.0
                    }
                    val ranked = recommendationEngine.scoreAndRank(
                        profile = profile,
                        candidates = eligibleCandidates,
                        limit = limit
                    )
                    emit(Resource.Success(ranked))
                }
            }
        }
    }
}

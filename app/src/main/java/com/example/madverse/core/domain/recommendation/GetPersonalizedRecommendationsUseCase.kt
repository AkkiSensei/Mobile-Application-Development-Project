package com.example.madverse.core.domain.recommendation

import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.data.repository.MovieRepository
import com.example.madverse.core.data.repository.QuizRepository
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.RuntimePreference
import com.example.madverse.core.model.ScoredMovieRecommendation
import com.example.madverse.core.model.UserPreferenceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain UseCase for generating personalized movie recommendations.
 *
 * Clean Integration Interface for Saanvi's recommendations screen and Shraddha's quiz:
 * 1. Reads persisted [UserPreferenceProfile] from Shraddha's [QuizRepository].
 * 2. Fetches candidate pool from TMDB via Ritunjay's [MovieRepository.discoverCandidateMovies].
 * 3. Enriches candidates with runtime metadata using bounded concurrency (max 4) and in-memory caching.
 * 4. Deterministically scores and ranks candidates via Ritunjay's [RecommendationEngine].
 *
 * Keeps network retrieval decoupled from scoring algorithms and guarantees 100% reproducible results.
 */
@Singleton
class GetPersonalizedRecommendationsUseCase @Inject constructor(
    private val quizRepository: QuizRepository,
    private val movieRepository: MovieRepository,
    private val recommendationEngine: RecommendationEngine
) {

    // Session in-memory cache mapping movieId -> runtimeMinutes
    internal val runtimeCache = ConcurrentHashMap<Int, Int>()

    // Bounded concurrency limiting simultaneous detail requests to at most 4
    private val semaphore = Semaphore(4)

    /**
     * Enriches candidate movies with runtime metadata from memory cache or movie details endpoint.
     * Uses bounded concurrency (max 4 parallel calls), caches results in memory, and handles failures gracefully.
     */
    internal suspend fun enrichRuntimes(
        candidates: List<Movie>,
        profile: UserPreferenceProfile
    ): List<Movie> {
        // If the user's preference is ANY or candidates are empty, runtime does not differentiate score
        if (profile.runtimePreference == RuntimePreference.ANY || candidates.isEmpty()) {
            return candidates
        }

        return coroutineScope {
            candidates.map { movie ->
                async {
                    val existingRuntime = movie.runtimeMinutes
                    if (existingRuntime != null && existingRuntime > 0) {
                        runtimeCache[movie.id] = existingRuntime
                        return@async movie
                    }

                    val cachedRuntime = runtimeCache[movie.id]
                    if (cachedRuntime != null) {
                        return@async movie.copy(runtimeMinutes = cachedRuntime)
                    }

                    semaphore.withPermit {
                        try {
                            val detailsResource = movieRepository.getMovieDetails(movie.id)
                                .firstOrNull { it !is Resource.Loading }

                            if (detailsResource is Resource.Success) {
                                val runtime = detailsResource.data.runtimeMinutes
                                if (runtime != null && runtime > 0) {
                                    runtimeCache[movie.id] = runtime
                                    movie.copy(runtimeMinutes = runtime)
                                } else {
                                    movie
                                }
                            } else {
                                movie
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // If an individual detail request fails, retain the movie instead of failing recommendation result
                            movie
                        }
                    }
                }
            }.awaitAll()
        }
    }

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
                    val enrichedCandidates = enrichRuntimes(eligibleCandidates, profile)
                    val ranked = recommendationEngine.scoreAndRank(
                        profile = profile,
                        candidates = enrichedCandidates,
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
                    val enrichedCandidates = enrichRuntimes(eligibleCandidates, profile)
                    val ranked = recommendationEngine.scoreAndRank(
                        profile = profile,
                        candidates = enrichedCandidates,
                        limit = limit
                    )
                    emit(Resource.Success(ranked))
                }
            }
        }
    }
}

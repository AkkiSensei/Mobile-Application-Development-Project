package com.example.madverse

import androidx.lifecycle.SavedStateHandle
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.model.Movie
import com.example.madverse.core.model.MovieDetails
import com.example.madverse.feature.comparison.presentation.ComparisonViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ComparisonViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private fun details(id: Int, title: String = "Movie $id") = MovieDetails(id, title, null, "Overview $id",
        null, null, "2020-01-01", 100 + id, 8.0, 10, emptyList(), "Released", 0, 0, "en")

    @Test fun openingWithoutIdAllowsBothSelections() = runTest(dispatcher) {
        val vm = ComparisonViewModel(DiscoveryTestRepository(), SavedStateHandle())
        assertTrue(vm.state.value.entries.isEmpty())
        vm.select(discoveryMovie(1)); vm.select(discoveryMovie(2)); advanceUntilIdle()
        assertEquals(listOf(1, 2), vm.state.value.entries.map { it.id })
    }

    @Test fun invalidInitialIdTerminatesWithoutRequest() = runTest(dispatcher) {
        var requests = 0
        val repo = object : DiscoveryTestRepository() {
            override fun getMovieDetails(movieId: Int): Flow<Resource<MovieDetails>> { requests++; return emptyFlow() }
        }
        val vm = ComparisonViewModel(repo, SavedStateHandle(mapOf("movieId" to -1)))
        advanceUntilIdle()
        assertFalse(vm.state.value.entries.single().loading)
        assertNotNull(vm.state.value.entries.single().error)
        assertEquals(0, requests)
        vm.replace(-1); vm.select(discoveryMovie(2)); advanceUntilIdle()
        assertEquals(2, vm.state.value.entries.single().id)
    }

    @Test fun initialIdLoadsAndClearedSelectionDoesNotReappearOnRestore() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("movieId" to 7))
        val repo = object : DiscoveryTestRepository() {
            override fun getMovieDetails(movieId: Int) = flowOf(Resource.Success(details(movieId)))
        }
        val vm = ComparisonViewModel(repo, handle); advanceUntilIdle()
        assertEquals(7, vm.state.value.entries.single().details?.id)
        vm.remove(7)
        assertTrue(ComparisonViewModel(repo, handle).state.value.entries.isEmpty())
    }

    @Test fun concurrentDetailsFinishOutOfOrderWithoutMixingColumns() = runTest(dispatcher) {
        val repo = object : DiscoveryTestRepository() {
            override fun getMovieDetails(movieId: Int) = flow {
                delay(if (movieId == 1) 1000 else 10)
                emit(Resource.Success(details(movieId)))
            }
        }
        val vm = ComparisonViewModel(repo, SavedStateHandle())
        vm.select(discoveryMovie(1)); vm.select(discoveryMovie(2))
        advanceTimeBy(20); runCurrent()
        assertTrue(vm.state.value.entries[0].loading)
        assertEquals(2, vm.state.value.entries[1].details?.id)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), vm.state.value.entries.map { it.details?.id })
    }

    @Test fun replacingOrRemovingCancelsOldRequestsAndPreservesOtherMovie() = runTest(dispatcher) {
        val cancelled = mutableListOf<Int>()
        val repo = object : DiscoveryTestRepository() {
            override fun getMovieDetails(movieId: Int) = flow {
                try { delay(if (movieId == 1) 1000 else 10); emit(Resource.Success(details(movieId))) }
                finally { if (!currentCoroutineContext().isActive) cancelled += movieId }
            }
        }
        val vm = ComparisonViewModel(repo, SavedStateHandle())
        vm.select(discoveryMovie(1)); vm.select(discoveryMovie(2)); runCurrent()
        vm.replace(1); vm.select(discoveryMovie(3)); advanceUntilIdle()
        assertEquals(listOf(3, 2), vm.state.value.entries.map { it.details?.id })
        assertEquals(listOf(1), cancelled)
        vm.remove(3); assertEquals(2, vm.state.value.entries.single().details?.id)
    }

    @Test fun sameIdReselectionRejectsNonCooperativeOldResponse() = runTest(dispatcher) {
        var calls = 0
        val repo = object : DiscoveryTestRepository() {
            override fun getMovieDetails(movieId: Int): Flow<Resource<MovieDetails>> {
                val call = ++calls
                return object : Flow<Resource<MovieDetails>> {
                    override suspend fun collect(collector: FlowCollector<Resource<MovieDetails>>) {
                        withContext(NonCancellable) { delay(if (call == 1) 1000 else 10) }
                        collector.emit(Resource.Success(details(movieId, "Request $call")))
                    }
                }
            }
        }
        val vm = ComparisonViewModel(repo, SavedStateHandle())
        vm.select(discoveryMovie(1)); runCurrent(); vm.remove(1)
        vm.select(discoveryMovie(1)); advanceUntilIdle()
        assertEquals("Request 2", vm.state.value.entries.single().details?.title)
    }

    @Test fun mismatchedDetailsAreRejectedAndRetryOnlyFetchesFailedMovie() = runTest(dispatcher) {
        var wrong = true
        val calls = mutableListOf<Int>()
        val repo = object : DiscoveryTestRepository() {
            override fun getMovieDetails(movieId: Int): Flow<Resource<MovieDetails>> {
                calls += movieId
                return flowOf(Resource.Success(details(if (wrong && movieId == 1) 99 else movieId)))
            }
        }
        val vm = ComparisonViewModel(repo, SavedStateHandle())
        vm.select(discoveryMovie(1)); vm.select(discoveryMovie(2)); advanceUntilIdle()
        assertNull(vm.state.value.entries[0].details)
        assertNotNull(vm.state.value.entries[0].error)
        wrong = false; vm.loadDetails(1); advanceUntilIdle()
        assertEquals(listOf(1, 2, 1), calls)
        assertEquals(listOf(1, 2), vm.state.value.entries.map { it.details?.id })
    }

    @Test fun emptyAndLoadingOnlyDetailsBecomeRetryableErrors() = runTest(dispatcher) {
        val repo = object : DiscoveryTestRepository() {
            override fun getMovieDetails(movieId: Int): Flow<Resource<MovieDetails>> =
                if (movieId == 1) emptyFlow() else flowOf(Resource.Loading)
        }
        val vm = ComparisonViewModel(repo, SavedStateHandle())
        vm.select(discoveryMovie(1)); vm.select(discoveryMovie(2)); advanceUntilIdle()
        assertTrue(vm.state.value.entries.all { !it.loading && it.error != null })
    }

    @Test fun thrownSearchErrorRetriesAndSelectionTargetsReplacement() = runTest(dispatcher) {
        var fail = true
        val handle = SavedStateHandle()
        val repo = object : DiscoveryTestRepository() {
            override fun searchMovies(query: String, page: Int, releaseYear: Int?): Flow<Resource<List<Movie>>> {
                if (fail) error("Offline")
                return flowOf(Resource.Success(listOf(discoveryMovie(3))))
            }
        }
        val vm = ComparisonViewModel(repo, handle)
        vm.select(discoveryMovie(1)); vm.select(discoveryMovie(2)); vm.replace(2)
        vm.search("Alien"); advanceUntilIdle(); assertNotNull(vm.state.value.searchError)
        fail = false; vm.search(vm.state.value.query); advanceUntilIdle()
        vm.select(vm.state.value.results.single()); advanceUntilIdle()
        assertEquals(listOf(1, 3), vm.state.value.entries.map { it.id })
        val restored = ComparisonViewModel(repo, handle); advanceUntilIdle()
        assertEquals("Alien", restored.state.value.query)
        assertEquals(listOf(1, 3), restored.state.value.entries.map { it.id })
    }

    @Test fun selfComparisonAndInvalidSelectionGiveFeedback() = runTest(dispatcher) {
        val vm = ComparisonViewModel(DiscoveryTestRepository(), SavedStateHandle())
        vm.select(discoveryMovie(1)); vm.select(discoveryMovie(1))
        assertNotNull(vm.state.value.selectionError)
        vm.select(discoveryMovie(-2)); assertEquals(1, vm.state.value.entries.size)
        vm.replace(999); assertNull(vm.state.value.replacingId)
        advanceUntilIdle()
    }

    @Test fun selectionIsUniqueLimitedAndReplaceable() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = ComparisonViewModel(DiscoveryTestRepository(), handle)
        listOf(1, 1, 2, 3, 4).forEach { vm.select(discoveryMovie(it)) }
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 3), vm.state.value.entries.map { it.id })
        vm.replace(2); vm.select(discoveryMovie(4)); advanceUntilIdle()
        assertEquals(listOf(1, 4, 3), vm.state.value.entries.map { it.id })
        vm.remove(1)
        assertArrayEquals(intArrayOf(4, 3), handle.get<IntArray>("selectedIds"))
        assertEquals(listOf(4, 3), ComparisonViewModel(DiscoveryTestRepository(), handle).state.value.entries.map { it.id })
    }
    @Test fun detailsFailureIsIsolatedAndRetryable() = runTest(dispatcher) {
        val vm = ComparisonViewModel(DiscoveryTestRepository(), SavedStateHandle(mapOf("movieId" to 9)))
        advanceUntilIdle()
        assertEquals(9, vm.state.value.entries.single().id)
        assertEquals("Not available", vm.state.value.entries.single().error)
        vm.loadDetails(9); advanceUntilIdle()
        assertFalse(vm.state.value.entries.single().loading)
        vm.remove(9)
        assertTrue(vm.state.value.entries.isEmpty())
    }
    @Test fun searchDebouncesAndBlankQueryClearsResults() = runTest(dispatcher) {
        val queries = mutableListOf<String>()
        val repo = object : DiscoveryTestRepository() {
            override fun searchMovies(query: String, page: Int, releaseYear: Int?): Flow<Resource<List<Movie>>> {
                queries += query
                return flowOf(Resource.Success(listOf(discoveryMovie())))
            }
        }
        val vm = ComparisonViewModel(repo, SavedStateHandle())
        vm.search("a"); advanceTimeBy(100); vm.search("alien"); advanceUntilIdle()
        assertEquals(listOf("alien"), queries)
        assertEquals(1, vm.state.value.results.size)
        vm.search(""); advanceUntilIdle()
        assertTrue(vm.state.value.results.isEmpty())
    }
}

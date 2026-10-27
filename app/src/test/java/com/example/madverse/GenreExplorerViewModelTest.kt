package com.example.madverse

import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.model.Movie
import com.example.madverse.feature.genres.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class GenreExplorerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val calls = mutableListOf<List<Any?>>()
    private var fail = false
    private val repository = object : DiscoveryTestRepository() {
        override fun discoverCandidateMovies(genreIds: Set<Int>, startYear: Int?, endYear: Int?, minRating: Double?, page: Int): Flow<Resource<List<Movie>>> {
            calls += listOf(genreIds, startYear, endYear, minRating, page)
            return flowOf(if (fail) Resource.Error("Offline") else Resource.Success(when (page) {
                1 -> listOf(discoveryMovie(1))
                2 -> listOf(discoveryMovie(1), discoveryMovie(2))
                else -> emptyList()
            }))
        }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun filtersReachRepositoryAndResetPaging() = runTest(dispatcher) {
        val vm = GenreExplorerViewModel(repository)
        advanceUntilIdle()
        vm.editYears("1990", "2014"); vm.setRating(7); vm.selectGenre(28)
        advanceUntilIdle()
        assertEquals(listOf(setOf(28), 1990, 2014, 7.0, 1), calls.last())
        vm.loadMore(); advanceUntilIdle()
        assertEquals(listOf(1, 2), vm.state.value.movies.map { it.id })
        vm.selectGenre(null); advanceUntilIdle()
        assertEquals(1, vm.state.value.page)
        assertEquals("1990", vm.state.value.startYear)
    }
    @Test fun clearingCombinedFiltersRestoresUnfilteredFirstPage() = runTest(dispatcher) {
        val vm = GenreExplorerViewModel(repository); advanceUntilIdle()
        vm.editYears("1990", "2014"); vm.setRating(8); vm.selectGenre(28); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        vm.editYears("", ""); vm.setRating(0); vm.selectGenre(null); vm.setSort(MovieSort.POPULARITY)
        advanceUntilIdle()
        assertEquals(listOf(emptySet<Int>(), null, null, 0.0, 1), calls.last())
        assertEquals(listOf(1), vm.state.value.movies.map { it.id })
        assertNull(vm.state.value.filterError)
    }

    @OptIn(InternalCoroutinesApi::class)
    @Test fun lateCancelledFilterResponseCannotAppendMoviesOrSetError() = runTest(dispatcher) {
        var lateEvents = 0
        val source = object : DiscoveryTestRepository() {
            override fun discoverCandidateMovies(genreIds: Set<Int>, startYear: Int?, endYear: Int?, minRating: Double?, page: Int): Flow<Resource<List<Movie>>> =
                if (genreIds.isNotEmpty()) flowOf(Resource.Success(listOf(discoveryMovie(2))))
                else object : Flow<Resource<List<Movie>>> {
                    override suspend fun collect(collector: FlowCollector<Resource<List<Movie>>>) {
                        try { awaitCancellation() } catch (_: CancellationException) {
                            withContext(NonCancellable) {
                                delay(100)
                                collector.emit(Resource.Success(listOf(discoveryMovie(1))))
                                collector.emit(Resource.Error("Obsolete failure"))
                                lateEvents += 2
                            }
                        }
                    }
                }
        }
        val vm = GenreExplorerViewModel(source); runCurrent()
        vm.selectGenre(28); advanceUntilIdle()
        assertEquals(2, lateEvents)
        assertEquals(listOf(2), vm.state.value.movies.map { it.id })
        assertNull(vm.state.value.error)
    }

    @Test fun invalidYearRangeDoesNotRequestMovies() = runTest(dispatcher) {
        val vm = GenreExplorerViewModel(repository); advanceUntilIdle()
        vm.editYears("2024", "1900"); vm.applyFilters(); advanceUntilIdle()
        assertEquals(1, calls.size)
        assertNotNull(vm.state.value.filterError)
    }
    @Test fun retryKeepsPageAndExistingMoviesThenStopsAtEmptyPage() = runTest(dispatcher) {
        val vm = GenreExplorerViewModel(repository); advanceUntilIdle()
        fail = true; vm.loadMore(); advanceUntilIdle()
        assertEquals(1, vm.state.value.page)
        assertEquals(1, vm.state.value.movies.size)
        fail = false; vm.loadMore(); advanceUntilIdle()
        assertEquals(2, vm.state.value.page)
        vm.loadMore(); advanceUntilIdle()
        assertFalse(vm.state.value.hasMore)
    }
    @Test fun sortingDoesNotRefetchOrDropMovies() = runTest(dispatcher) {
        val vm = GenreExplorerViewModel(repository); advanceUntilIdle()
        vm.setSort(MovieSort.RATING)
        assertEquals(1, calls.size)
        assertEquals(listOf(1), vm.state.value.sortedMovies.map { it.id })
    }
    @Test fun changingGenreCancelsObsoleteRequest() = runTest(dispatcher) {
        val delayed = object : DiscoveryTestRepository() {
            override fun discoverCandidateMovies(genreIds: Set<Int>, startYear: Int?, endYear: Int?, minRating: Double?, page: Int) = flow {
                delay(if (genreIds.isEmpty()) 1000 else 10)
                emit(Resource.Success(listOf(discoveryMovie(if (genreIds.isEmpty()) 1 else 2))))
            }
        }
        val vm = GenreExplorerViewModel(delayed)
        runCurrent(); vm.selectGenre(28); advanceUntilIdle()
        assertEquals(listOf(2), vm.state.value.movies.map { it.id })
    }
}

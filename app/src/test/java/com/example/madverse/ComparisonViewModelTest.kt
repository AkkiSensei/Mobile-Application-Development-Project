package com.example.madverse

import androidx.lifecycle.SavedStateHandle
import com.example.madverse.core.common.result.Resource
import com.example.madverse.core.model.Movie
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

package com.example.madverse.core.common.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Main-thread UI actions: serialize taps per collection/movie, preserving cancellation. */
class RepositoryActions(private val scope: CoroutineScope) {
    private val pending = mutableSetOf<String>()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun dismissError() { _error.value = null }

    fun run(key: String, action: suspend () -> Unit) {
        if (!pending.add(key)) return
        _error.value = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _error.value = "Could not update your library. Please try the action again." }
            finally { pending.remove(key) }
        }
    }
}

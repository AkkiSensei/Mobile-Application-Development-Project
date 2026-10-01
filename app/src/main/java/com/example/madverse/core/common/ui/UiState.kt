package com.example.madverse.core.common.ui

/**
 * Standard reactive state interface for all MADVerse feature screens.
 * Ensures predictable loading, success, empty, and error handling.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Empty(val message: String = "No results found.") : UiState<Nothing>
    data class Error(val message: String, val isOffline: Boolean = false) : UiState<Nothing>
}

/**
 * Common one-off UI events consumed via SharedFlow/Channel in Compose.
 */
sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
    data class Navigate(val route: String) : UiEvent
    data object NavigateUp : UiEvent
    data class OpenExternalUri(val uri: String) : UiEvent
}

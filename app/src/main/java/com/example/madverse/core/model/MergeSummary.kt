package com.example.madverse.core.model

/**
 * Summary outcome of merging guest-local library and preferences into an authenticated account.
 */
data class MergeSummary(
    val favoritesMerged: Int = 0,
    val watchlistMerged: Int = 0,
    val preferencesMerged: Boolean = false
) {
    val totalItemsMerged: Int
        get() = favoritesMerged + watchlistMerged + (if (preferencesMerged) 1 else 0)
}

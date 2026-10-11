package com.example.madverse.feature.library.presentation

/**
 * Tabs available in the personal Library screen.
 */
enum class LibraryTab(val title: String) {
    WATCHLIST("Watchlist"),
    FAVORITES("Favorites")
}

/**
 * Sorting options for movies in Favorites and Watchlist collections.
 */
enum class LibrarySortOption(val displayName: String) {
    RECENTLY_ADDED("Recently Added"),
    RATING("Top Rated"),
    RELEASE_DATE("Release Date"),
    TITLE("Alphabetical")
}

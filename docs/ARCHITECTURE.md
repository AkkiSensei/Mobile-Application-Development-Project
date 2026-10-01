# MADVerse — Technical Architecture Blueprint

**Version:** 1.0.0-blueprint  
**Date:** October 2026  
**Project:** Mobile Application Development (MAD) Project  
**Team Members:** Ritunjay Deo, Saanvi, Shraddha  

---

## 1. Project Overview

**MADVerse** is an Android movie discovery and recommendation application built using modern Android development practices (Kotlin, Jetpack Compose, Material 3, MVVM, Clean Architecture, Hilt, Retrofit, Room, and Coroutines/Flow). 

The application enables users to:
1. Discover trending, top-rated, and upcoming movies.
2. Search movies with instant debouncing, genre chips, and year filtering.
3. Inspect detailed movie information, cast, and high-resolution posters/backdrops.
4. Watch official movie trailers inside the app using embeddable YouTube playback.
5. Identify official streaming and rental availability (Netflix, Prime, Disney+, Apple TV).
6. Take an interactive Movie Taste Quiz to determine their personalized preference profile.
7. Receive ranked, personalized movie recommendations powered by a deterministic scoring engine with optional Gemini AI assistance.
8. Persist favorite movies and a watchlist offline using local Room database storage.

---

## 2. Architectural Blueprint

MADVerse follows a **Single-Module (`:app`), Clean-Architecture, Package-by-Feature** structure.

```
+-----------------------------------------------------------------------------------+
|                                PRESENTATION LAYER                                 |
|                                                                                   |
|  +--------------------+  +--------------------+  +-----------------------------+  |
|  |   DiscoverScreen   |  |    SearchScreen    |  |     MovieDetailsScreen      |  |
|  | (Trending/Upcoming)|  | (Debounced/Genres) |  |   (Trailers & Providers)    |  |
|  +---------+----------+  +---------+----------+  +--------------+--------------+  |
|            |                       |                            |                 |
|  +---------+----------+  +---------+----------+  +--------------+--------------+  |
|  |     QuizScreen     |  |RecommendationsScreen| |        LibraryScreen        |  |
|  |   (Taste Survey)   |  | (Deterministic+AI) |  |    (Watchlist/Favorites)    |  |
|  +---------+----------+  +---------+----------+  +--------------+--------------+  |
|            |                       |                            |                 |
|            +-----------------------+----------------------------+                 |
|                                    | State / Events                               |
|                                    v                                              |
|                       +--------------------------+                                |
|                       |   ViewModels (StateFlow) |                                |
|                       +-------------+------------+                                |
+-------------------------------------|---------------------------------------------+
                                      |
+-------------------------------------v---------------------------------------------+
|                                  DOMAIN LAYER                                     |
|                                                                                   |
|  +--------------------------+  +----------------------+  +---------------------+  |
|  |   RecommendationEngine   |  |  StreamingResolver   |  |   YouTubeContract   |  |
|  | (Genre/Mood/Era Scoring) |  | (Official Intent URI)|  |   (Lifecycle & UI)  |  |
|  +--------------------------+  +----------------------+  +---------------------+  |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  | Domain Models: Movie, MovieDetails, Genre, WatchProvider, QuizProfile, etc. |  |
|  +-----------------------------------------------------------------------------+  |
+-------------------------------------|---------------------------------------------+
                                      |
+-------------------------------------v---------------------------------------------+
|                                   DATA LAYER                                      |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  | Repositories: MovieRepository, WatchlistRepository, FavoritesRepository,    |  |
|  |               QuizRepository                                                |  |
|  +----------------------+-------------------------------+----------------------+  |
|                         |                               |                         |
|                         v                               v                         |
|  +------------------------------+     +----------------------------------------+  |
|  |    Remote Data Source        |     |           Local Data Source            |  |
|  |  - TmdbApi (Retrofit)        |     |  - MADVerseDatabase (Room)             |  |
|  |  - AuthInterceptor           |     |  - WatchlistDao                        |  |
|  |  - OkHttpClient              |     |  - FavoriteDao                         |  |
|  |  - ApiConstants              |     |  - UserPreferenceDao                   |  |
|  +------------------------------+     +----------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 3. Feature Boundaries & Responsibilities

| Feature | UI Responsibility | ViewModel & State | Data Sources | Inter-Feature Dependencies |
| :--- | :--- | :--- | :--- | :--- |
| **Discover** | Hero Carousel, Trending Row, Top Rated Grid, Upcoming Section | `DiscoverViewModel` exposing `DiscoverUiState` | `MovieRepository` (TMDB) | Navigates to `MovieDetails` |
| **Search** | Real-time debounced query input, genre chips, release year selector, movie grid | `SearchViewModel` exposing `SearchUiState` | `MovieRepository` (TMDB Search & Genres) | Navigates to `MovieDetails` |
| **Movie Details** | Poster, backdrop, metadata, synopsis, cast avatars, trailer view, provider icons | `MovieDetailsViewModel` exposing `MovieDetailsUiState` | `MovieRepository`, `WatchlistDao`, `FavoriteDao` | Launches streaming intents, embedded YouTube |
| **Quiz** | Multi-step card deck: Genre choice, Mood selector, Era preference, Runtime filter | `QuizViewModel` exposing `QuizUiState` | `QuizRepository` (Room `user_preference_profile`) | Navigates to `Recommendations` on submit |
| **Recommendations** | Ranked recommendation cards with Match % badge, "Why this was picked" explanation | `RecommendationsViewModel` exposing `RecommendationsUiState` | `QuizRepository`, `MovieRepository`, `RecommendationEngine` | Navigates to `MovieDetails` |
| **Library** | Tabbed Watchlist & Favorites lists, sorting, quick removal | `LibraryViewModel` exposing `LibraryUiState` | `WatchlistRepository`, `FavoritesRepository` (Room) | Navigates to `MovieDetails` |
| **Streaming** | Provider logos (Netflix, Prime, Disney+), buy/rent/stream labels | Helper within Details state | `MovieRepository` (`watch/providers`), `StreamingContract` | External Android View Intent |
| **YouTube Trailer** | Lifecycle-aware embedded player with overlay play toggle | Player state holder | `MovieRepository` (`videos`), `YouTubePlayerContract` | Embedded inside Details screen |

---

## 4. End-to-End Data Flow

```
[User Action in Compose UI]
       │
       ▼
[ViewModel handles event, launches coroutine in viewModelScope]
       │
       ▼
[Repository fetches data via Flow<Resource<T>>]
       ├─── Check Local Room Database (for Watchlist/Favorites/Preferences)
       └─── Call Remote API via TmdbApi (OkHttp adds API Key via AuthInterceptor)
       │
       ▼
[DTO transformed into pure Domain Model (e.g., MovieDto.toDomain())]
       │
       ▼
[Resource.Success(domainModel) or Resource.Error(message) emitted]
       │
       ▼
[ViewModel maps Resource to UiState (Loading, Success, Empty, Error)]
       │
       ▼
[Compose UI recomposes reactively to StateFlow value]
```

1. **TMDB Data:** Enters system strictly through `TmdbApi`, intercepted by `AuthInterceptor` (keys from `BuildConfig`), mapped via `toDomain()` in `core/network/model`, and emitted via `MovieRepository`.
2. **Recommendation Logic:** Executes in `RecommendationEngine.scoreAndRank()`. Fetches user profile from `UserPreferenceDao`, queries candidate movies from `MovieRepository.discoverCandidateMovies()`, calculates match scores, and presents ranked results.
3. **Quiz Answers:** Stored locally in `user_preference_profile` Room table via `UserPreferenceDao`.
4. **Favorites / Watchlist:** Stored in `favorite_movies` and `watchlist_movies` Room tables; emitted as continuous reactive `Flow<List<Movie>>`.
5. **Streaming Availability:** Resolved from TMDB `watch/providers` endpoint into `WatchProvider` domain objects; launched via external `ACTION_VIEW` intent.
6. **YouTube Trailers:** Resolved from TMDB `videos` endpoint; keys passed to `com.pierfrancescosoffritti.androidyoutubeplayer:core`.

---

## 5. Core Domain Models

```kotlin
// Core catalog item
data class Movie(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseDate: String?,
    val voteAverage: Double,
    val voteCount: Int,
    val genreIds: List<Int>,
    val popularity: Double
)

// Full details with local bookmark flags
data class MovieDetails(
    val id: Int,
    val title: String,
    val tagline: String?,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val voteAverage: Double,
    val voteCount: Int,
    val genres: List<Genre>,
    val status: String,
    val budget: Long,
    val revenue: Long,
    val originalLanguage: String,
    val isFavorite: Boolean = false,
    val isInWatchlist: Boolean = false
)

// Video / Trailer
data class VideoTrailer(
    val id: String,
    val key: String,
    val name: String,
    val site: String,
    val type: String,
    val isOfficial: Boolean
)

// Watch Provider
data class WatchProvider(
    val providerId: Int,
    val providerName: String,
    val logoUrl: String?,
    val type: ProviderType,
    val directLink: String? = null
)

// User Preference Profile (from Quiz)
data class UserPreferenceProfile(
    val selectedGenreIds: Set<Int> = emptySet(),
    val mood: MovieMood = MovieMood.ANY,
    val era: MovieEra = MovieEra.ANY,
    val runtimePreference: RuntimePreference = RuntimePreference.ANY,
    val preferredLanguage: String? = null,
    val minRatingThreshold: Double = 6.0,
    val updatedAtTimestamp: Long = System.currentTimeMillis()
)

// Recommendation Result
data class ScoredMovieRecommendation(
    val movie: Movie,
    val matchPercentage: Int, // 0 to 100%
    val matchReasons: List<String>,
    val aiCuratorNote: String? = null
)
```

---

## 6. TMDB API Contract

MADVerse interacts with TMDB v3 via the following curated endpoints:

| Endpoint | Method | Purpose in MADVerse |
| :--- | :--- | :--- |
| `trending/movie/day` | `GET` | Populates the Home Discover trending hero carousel and rows |
| `movie/top_rated` | `GET` | High-critique acclaimed movies |
| `movie/upcoming` | `GET` | Upcoming theatrical & digital releases |
| `search/movie` | `GET` | Real-time user query search with release year filtering |
| `movie/{movie_id}` | `GET` | Detailed movie breakdown (runtime, tagline, budget, genres) |
| `movie/{movie_id}/credits` | `GET` | Key cast members and director |
| `movie/{movie_id}/videos` | `GET` | Official YouTube trailers and teasers |
| `movie/{movie_id}/watch/providers` | `GET` | Flatrate streaming, rent, and buy provider links |
| `movie/{movie_id}/similar` | `GET` | Related films on details screen |
| `genre/movie/list` | `GET` | Movie genres for search chips and quiz mappings |
| `discover/movie` | `GET` | Dynamic candidate generation for the recommendation engine |

* **Authentication:** Handled automatically by `AuthInterceptor` appending `api_key` query parameter from `BuildConfig.TMDB_API_KEY`.
* **CDN Resolution:** Posters resolve to `https://image.tmdb.org/t/p/w500{path}`. Backdrops resolve to `https://image.tmdb.org/t/p/w780{path}`.

---

## 7. Room Persistence Contract

### Database: `MADVerseDatabase` (`version = 1`)

#### 1. Table: `watchlist_movies`
* Primary Key: `movie_id` (Int)
* Fields: `title`, `poster_path`, `backdrop_path`, `release_date`, `vote_average`, `overview`, `saved_timestamp`
* Operations:
  * `getAllWatchlistMovies(): Flow<List<WatchlistEntity>>`
  * `isMovieInWatchlist(movieId: Int): Flow<Boolean>`
  * `insertMovie(movie: WatchlistEntity)`
  * `deleteMovieById(movieId: Int)`

#### 2. Table: `favorite_movies`
* Primary Key: `movie_id` (Int)
* Fields: `title`, `poster_path`, `backdrop_path`, `release_date`, `vote_average`, `overview`, `saved_timestamp`
* Operations:
  * `getAllFavorites(): Flow<List<FavoriteEntity>>`
  * `isFavorite(movieId: Int): Flow<Boolean>`
  * `insertFavorite(movie: FavoriteEntity)`
  * `deleteFavoriteById(movieId: Int)`

#### 3. Table: `user_preference_profile`
* Primary Key: `id` (Int = 1, singleton profile)
* Fields: `genre_ids_csv`, `mood`, `era`, `runtime_preference`, `preferred_language`, `min_rating_threshold`, `updated_timestamp`
* Operations:
  * `getUserPreferenceProfile(): Flow<UserPreferenceEntity?>`
  * `saveUserPreferenceProfile(profile: UserPreferenceEntity)`
  * `clearUserPreferenceProfile()`

---

## 8. Recommendation Engine Contract

The recommendation engine is an **original academic differentiator**. It functions deterministically without external cloud dependencies, with an optional AI enhancement layer:

### Scoring Formula:
$$\text{MatchScore} = S_{\text{genre}} + S_{\text{mood}} + S_{\text{era}} + S_{\text{quality}}$$

1. **Genre Overlap ($S_{\text{genre}}$, up to 40 pts):**
   * Computes Jaccard/overlap index between movie's genres and user's selected quiz genres:
   $$S_{\text{genre}} = \frac{|\text{MovieGenres} \cap \text{UserGenres}|}{\max(1, |\text{UserGenres}|)} \times 40$$
2. **Mood Alignment ($S_{\text{mood}}$, up to 25 pts):**
   * Compares movie genre IDs against the target mood's primary genre IDs (e.g. `MIND_BENDING` $\rightarrow$ Sci-Fi, Mystery, Thriller).
3. **Era Alignment ($S_{\text{era}}$, up to 15 pts):**
   * Validates movie release year against the selected era (Classic $<1990$, Modern $1990-2014$, Contemporary $2015+$).
4. **Rating Quality Factor ($S_{\text{quality}}$, up to 20 pts):**
   * Scaled based on TMDB vote average:
   $$S_{\text{quality}} = \left(\frac{\min(10.0, \text{VoteAverage})}{10.0}\right) \times 20$$

### Total Match Percentage:
$$\text{Match Percentage} = \min(100, \lfloor \text{MatchScore} \rfloor)$$

### Extension Point (Gemini AI):
```kotlin
interface AiRecommendationEnhancer {
    suspend fun enhanceRecommendationRationale(
        recommendation: ScoredMovieRecommendation,
        profile: UserPreferenceProfile
    ): ScoredMovieRecommendation
}
```
If `GEMINI_API_KEY` is present, Gemini generates a 1-sentence personalized curator note explaining *why* the user will enjoy the film given their mood. If absent or offline, a deterministic rationale string is supplied (e.g., *"Matched your high affinity for Sci-Fi and Mind-Bending themes"*).

---

## 9. Safe Streaming & Video Contract

1. **Zero Scraped / Pirate Streams:** MADVerse complies with academic integrity and copyright law. Protected OTT streams (Netflix, Disney+, Prime) are never scraped, decoded, or played in-app without DRM.
2. **Official Destination Links:** Watch providers display official provider logos and deep-link via `ACTION_VIEW` intent into the provider's official Android app or web portal.
3. **Embedded YouTube Playback:**
   * Uses `com.pierfrancescosoffritti.androidyoutubeplayer:core`.
   * Operates via YouTube IFrame API inside a Compose `AndroidView`.
   * Strictly respects video embed permissions configured by official studio channels on YouTube.
   * Auto-pauses on Android lifecycle `ON_PAUSE` / `ON_STOP` events.

---

## 10. Navigation Contract

Navigation routes are centrally defined in `Screen.kt` and managed by `MADVerseNavHost` within `MADVerseAppShell`:

| Route Pattern | Screen / Destination | Top Bar Title | Bottom Bar Visible? | Arguments |
| :--- | :--- | :--- | :---: | :--- |
| `"discover"` | `DiscoverScreen` | "MADVerse Discover" | Yes | None |
| `"search"` | `SearchScreen` | "Search Movies" | Yes | None |
| `"quiz"` | `QuizScreen` | "Movie Taste Quiz" | Yes | None |
| `"library"` | `LibraryScreen` | "My Library" | Yes | None |
| `"movie_details/{movieId}"` | `MovieDetailsScreen` | "Movie Overview" | No | `movieId: Int` |
| `"quiz_result"` | `RecommendationsScreen` | "Curated for You" | No | None |
| `"see_all/{categoryType}"` | `SeeAllScreen` | Dynamic Category | No | `categoryType: String` |

* Bottom bar tabs use `popUpTo(Screen.Discover.route) { saveState = true }`, `launchSingleTop = true`, and `restoreState = true` to preserve scroll positions across tab switches.
* Detail destinations hide the bottom bar and present an arrow icon in the top bar to pop the back stack.

---

## 11. UI State & Error Contract

All feature view models expose a single immutable `StateFlow<UiState<T>>` combined with a `Channel<UiEvent>` for one-off events:

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Empty(val message: String = "No results found.") : UiState<Nothing>
    data class Error(val message: String, val isOffline: Boolean = false) : UiState<Nothing>
}
```

* **Offline Handling:** When a network exception occurs (e.g. `IOException` or `SocketTimeoutException`), `UiState.Error` sets `isOffline = true`. The UI displays a retry button along with an offline banner, allowing users to navigate to cached Library data without crashing.

---

## 12. Three-Member Development Boundaries

To facilitate parallel development without merge conflicts during the upcoming 15 implementation tasks:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                            TEAM DOMAIN OWNERSHIP                             │
├──────────────────────┬──────────────────────┬────────────────────────────────┤
│      RITUNJAY        │        SAANVI        │            SHRADDHA            │
├──────────────────────┼──────────────────────┼────────────────────────────────┤
│ • Discover Feature   │ • Search Feature     │ • Taste Quiz Feature           │
│   (Trending Carousel,│   (Search Bar, Query │   (Interactive Survey Wizard,  │
│   Top Rated, Upcoming)   Debounce, Filters) │   Question Deck, Profile Save) │
│ • Movie Details Core │ • Streaming Providers│ • Recommendation Engine        │
│   (Hero Backdrop,    │   (TMDB Watch Data,  │   (Deterministic Match Scorer, │
│   Synopsis, Metadata)│   Official Deep-Links│   Ranking, Gemini AI Assist)   │
│ • YouTube Player     │ • Category Browsing  │ • Library Persistence          │
│   (Embedded Trailer  │   ("See All" Lists,  │   (Room Watchlist & Favorites, │
│   Compose Player)    │   Pagination)        │   Reactive Sync, Offline UI)   │
└──────────────────────┴──────────────────────┴────────────────────────────────┘
```

---

## 13. Reference Repository Analysis & Decisions

| Feature in `MovieApp` Reference | MADVerse Implementation Decision | Rationale |
| :--- | :--- | :--- |
| 20+ Gradle Modules | Unified `:app` with strict package boundaries | Avoids Gradle sync failures on Windows with Gradle 9 & AGP 9.4 |
| Firebase Auth & Firestore | Local Room Database Persistence | Scope control for 2-week deadline; focuses grading on original logic |
| Custom Preference Quiz | **Original MADVerse Feature** (Not in reference) | High academic originality value; powers custom recommendations |
| Recommendation Engine | **Original MADVerse Feature** (Not in reference) | Multi-factor weighted match scorer |
| Streaming Provider Badges | **Original MADVerse Feature** (Not in reference) | Real-world utility pointing users to official watch destinations |
| Room 2.6.1 | Upgraded to **Room 2.8.5** | Resolves KSP2 Analysis API `unexpected jvm signature V` compiler bug |
| Dagger Hilt 2.48 | Upgraded to **Dagger Hilt 2.60.1** | Native support for AGP 9.4 and removed `BaseExtension` |

---

## 14. Explicit Out-of-Scope Items

1. **Internet Archive Integration:** Explicitly excluded from the 2-week implementation scope.
2. **Direct DRM Video Extraction:** No unauthorized video playback of subscription streaming services.
3. **Firebase User Authentication:** All preferences and watchlists operate locally via Room for fast, offline-first evaluation.
4. **Complex Multi-Module Build Pipelines:** Kept within a clean single module to guarantee zero dependency friction across developer laptops.

# MADVerse — Technical Architecture Blueprint

**Version:** 1.1.0-r1-foundation  
**Date:** October 2026 (R1 Foundation: 11 Oct 2026 — Ritunjay Deo)  
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

---

## 15. R1 Foundation — Delivery Notes (Ritunjay Deo, 11 Oct 2026)

This section documents what was delivered in the R1 Foundation prompt and serves as the handover reference for Shraddha and Saanvi.

### 15.1 What Was Implemented in R1

| Component | File | Status |
| :--- | :--- | :---: |
| TMDB key → BuildConfig injection | `app/build.gradle.kts` | ✅ Existing |
| `AuthInterceptor` (api_key query param) | `core/network/interceptor/AuthInterceptor.kt` | ✅ Existing |
| `TmdbApi` Retrofit interface (11 endpoints) | `core/network/api/TmdbApi.kt` | ✅ Existing |
| All DTOs + `toDomain()` mappings | `core/network/model/*.kt` | ✅ Existing |
| Domain models (Movie, MovieDetails, Genre, VideoTrailer, WatchProvider, CastMember) | `core/model/*.kt` | ✅ Existing |
| `UserPreferenceProfile` + enums (MovieMood, MovieEra, RuntimePreference) | `core/model/UserPreferenceProfile.kt` | ✅ Existing |
| `ScoredMovieRecommendation` | `core/model/ScoredMovieRecommendation.kt` | ✅ Existing |
| `MovieRepository` interface | `core/data/repository/MovieRepository.kt` | ✅ Existing |
| `FavoritesRepository`, `WatchlistRepository`, `QuizRepository` interfaces | `core/data/repository/*.kt` | ✅ Existing |
| `RecommendationEngine` + `AiRecommendationEnhancer` interfaces | `core/domain/recommendation/RecommendationEngine.kt` | ✅ Existing |
| **`MovieRepositoryImpl`** | `core/data/repository/impl/MovieRepositoryImpl.kt` | 🆕 R1 |
| **`FavoritesRepositoryImpl`** | `core/data/repository/impl/FavoritesRepositoryImpl.kt` | 🆕 R1 |
| **`WatchlistRepositoryImpl`** | `core/data/repository/impl/WatchlistRepositoryImpl.kt` | 🆕 R1 |
| **`QuizRepositoryImpl`** (with quiz question definitions) | `core/data/repository/impl/QuizRepositoryImpl.kt` | 🆕 R1 |
| **`RepositoryModule`** (Hilt @Binds for all 4 repos) | `core/data/di/RepositoryModule.kt` | 🆕 R1 |
| **`DeterministicRecommendationEngine`** | `core/domain/recommendation/DeterministicRecommendationEngine.kt` | 🆕 R1 |
| **`DomainModule`** (Hilt @Binds for engine) | `core/domain/di/DomainModule.kt` | 🆕 R1 |
| **`RecommendationEngineTest`** (9 unit tests) | `test/.../RecommendationEngineTest.kt` | 🆕 R1 |
| **`DtoMappingTest`** (15 unit tests) | `test/.../DtoMappingTest.kt` | 🆕 R1 |

### 15.2 TMDB Authentication Configuration

```
local.properties (git-ignored)  →  TMDB_API_KEY=<your_key>
         ↓
app/build.gradle.kts (Gradle loads local.properties)
         ↓
buildConfigField("String", "TMDB_API_KEY", ...)
         ↓
BuildConfig.TMDB_API_KEY  (injected at compile time)
         ↓
AuthInterceptor.intercept()  → appends ?api_key=... to every OkHttp request
```

**Key is never printed, logged, committed, or hard-coded.**  
Fails silently (empty key → TMDB returns 401, mapped to `Resource.Error`) so the build always succeeds even without the key.

### 15.3 Network Error Handling Contract

All `MovieRepositoryImpl` flows use `safeApiCall {}`:

| Exception | `Resource.Error` Message |
| :--- | :--- |
| `IOException` | "Network error: Check your internet connection." |
| `HttpException(401)` | "TMDB authentication failed. Check your API key configuration." |
| `HttpException(404)` | "Movie not found on TMDB (404)." |
| `HttpException(429)` | "TMDB rate limit exceeded. Please retry in a moment." |
| `HttpException(5xx)` | "TMDB service is temporarily unavailable (HTTP $code)." |
| Other `Exception` | "An unexpected error occurred: $message" |

ViewModels map `Resource.Error` to `UiState.Error(isOffline = e is IOException)`.

### 15.4 Recommendation Engine Formula (Implemented)

```
MatchScore = genreScore(40) + moodScore(25) + eraScore(15) + qualityScore(20)
MatchPercentage = clamp(MatchScore, 0, 100)
```

- **genreScore**: Overlap ratio × 40 pts
- **moodScore**: Mood genre IDs overlap × 25 pts
- **eraScore**: Full 15 pts if release year in era range; 0 otherwise; full if `ANY`
- **qualityScore**: `(voteAverage / 10.0) × 20` — movies below `minRatingThreshold` score 0

TMDB API attribution note is in the class docstring. Data is never forwarded to AI.

### 15.5 Quiz Question Set (Canonical — Do Not Duplicate)

`QuizRepositoryImpl.getQuizQuestions()` is the **single source of truth** for quiz questions.  
Shraddha's `QuizScreen` must call `QuizRepository.getQuizQuestions()` and must NOT define its own question list.

| Step | Question ID | Type | Options |
| :--- | :--- | :--- | :--- |
| 1 | `q_genre` | Multi-select | 12 genre chips (Action, Adventure, Comedy, Drama, Horror, Sci-Fi, Thriller, Romance, Animation, Mystery, Fantasy, Crime) |
| 2 | `q_mood` | Single-select | 6 `MovieMood` values |
| 3 | `q_era` | Single-select | 4 `MovieEra` values |
| 4 | `q_runtime` | Single-select | 4 `RuntimePreference` values |

---

## 16. Handover Instructions

### 16.1 For Shraddha — Quiz Feature

**What you receive:**
- `QuizRepository` interface + `QuizRepositoryImpl` fully implemented (Room-backed)
- `UserPreferenceProfile` domain model with `MovieMood`, `MovieEra`, `RuntimePreference` enums
- `QuizQuestion` / `QuizOption` domain models
- The canonical quiz question set via `QuizRepository.getQuizQuestions()`
- `RecommendationEngine` interface + `DeterministicRecommendationEngine` fully implemented
- `ScoredMovieRecommendation` domain model

**Your deliverables:**
1. `QuizScreen` composable in `feature/quiz/presentation/` — multi-step card deck
2. `QuizViewModel` in `feature/quiz/presentation/` — reads `quizRepository.getQuizQuestions()`, saves profile via `quizRepository.saveUserPreferenceProfile(profile)`
3. `RecommendationsScreen` composable in `feature/quiz/presentation/` (or separate `feature/recommendations/`)
4. `RecommendationsViewModel` — injects `QuizRepository`, `MovieRepository`, and `RecommendationEngine`; calls `movieRepository.discoverCandidateMovies(...)` then `engine.scoreAndRank(...)`
5. Update `MADVerseNavHost` to navigate to `Screen.QuizResult` after quiz completion

**Integration points:**
```kotlin
// In RecommendationsViewModel:
@HiltViewModel
class RecommendationsViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    private val movieRepository: MovieRepository,
    private val recommendationEngine: RecommendationEngine
) : ViewModel()
```

**Room Persistence:** Library (Watchlist + Favorites) is also your scope. Inject `FavoritesRepository` and `WatchlistRepository` — both are fully implemented.

### 16.2 For Saanvi — Search & Streaming Features

**What you receive:**
- `MovieRepository` interface + `MovieRepositoryImpl` fully implemented
- `TmdbApi.searchMovies(query, page, releaseYear)` → ready to call
- `TmdbApi.getMovieWatchProviders(movieId)` → `MovieWatchProviders` domain object
- `Genre` domain model + `TmdbApi.getMovieGenres()` for search genre chips
- `TmdbApi.getSimilarMovies(movieId)` for the "See All Similar" screen

**Your deliverables:**
1. `SearchScreen` composable — debounced query, genre chip filter, release year selector, movie grid
2. `SearchViewModel` — inject `MovieRepository`; use `searchMovies()` and `getGenres()`
3. `StreamingProvidersSection` composable within `MovieDetailsScreen` — display `MovieWatchProviders`
4. `SeeAllScreen` composable for paginated `See All` lists (Trending, Top Rated, Upcoming, Similar)
5. Android `ACTION_VIEW` intent helper to deep-link into provider apps

**Integration points:**
```kotlin
// In SearchViewModel:
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel()

// Streaming providers (wired into MovieDetailsViewModel):
movieRepository.getMovieWatchProviders(movieId, countryCode = "US")
```

**Note on Streaming:** All provider data comes from TMDB `watch/providers`. Link users to `MovieWatchProviders.tmdbWatchPageUrl` or provider deep-links. Never scrape or play protected OTT streams.

### 16.3 Shared Hilt Graph — No Duplicate Modules Needed

All DI modules are installed in `SingletonComponent`. You do **not** need to create new modules for the repositories or engine — they are already bound:

- `NetworkModule` → provides `OkHttpClient`, `Retrofit`, `TmdbApi`
- `DatabaseModule` → provides `MADVerseDatabase`, `FavoriteDao`, `WatchlistDao`, `UserPreferenceDao`
- `RepositoryModule` → binds all 4 repository interfaces
- `DomainModule` → binds `RecommendationEngine`

Just `@Inject constructor` the interface you need in your ViewModel.

### 16.4 TMDB Attribution

Display the following attribution in the app's About or Settings screen:

> "This product uses the TMDB API but is not endorsed or certified by TMDB."
> Include the TMDB logo where space permits.

Required per [TMDB API Terms of Use](https://www.themoviedb.org/documentation/api/terms-of-use).

### 16.5 AI Feature Restriction

The recommendation engine is **deterministic and fully local**. The `AiRecommendationEnhancer` interface exists as an extension point for Gemini AI notes, but:

- The GEMINI_API_KEY must be present in `local.properties`
- TMDB content (titles, overviews, posters) must **NOT** be sent to Gemini unless confirmed as permitted by TMDB terms
- The enhancer is optional and degrades gracefully when the key is absent
- Use Gemini only to generate **conversational rationale strings** based on profile preferences — NOT raw TMDB API response content

---

## 17. R2 Delivery: Home and Movie Discovery

Implemented by **Ritunjay Deo** on branch `feature/ritunjay-discover-screen`.

### 17.1 Architecture & Components Delivered

```
MADVerseAppShell (Scaffold + MADVerseTopBar + MADVerseBottomBar)
  └─ MADVerseNavHost (Screen.Discover route)
       └─ DiscoverScreen (Lifecycle-aware via collectAsStateWithLifecycle())
            ├─ HeroFeaturedMovieCard (Top trending movie spotlight with backdrop image & metadata)
            ├─ Trending Carousel (LazyRow of MoviePosterCard)
            ├─ Top-Rated Carousel (LazyRow of MoviePosterCard)
            ├─ Upcoming Releases Carousel (LazyRow of MoviePosterCard)
            └─ Error & Empty States (SectionErrorCard with retry, SectionEmptyCard, FullFeedErrorView)
```

### 17.2 State Management (`HomeUiState` & `HomeViewModel`)

- **Independent Section Streams:**
  - `trendingState: UiState<List<Movie>>`
  - `topRatedState: UiState<List<Movie>>`
  - `upcomingState: UiState<List<Movie>>`
  - Failures in one category (e.g., Upcoming network glitch) never block rendering of other categories (e.g., Trending or Top Rated).
- **Lifecycle-Aware Collection:** Uses `collectAsStateWithLifecycle()` in Compose for battery and lifecycle safety.
- **Granular Retries:** Per-section retry triggers (`loadTrending()`, `loadTopRated()`, `loadUpcoming()`) plus pull-to-refresh (`refresh()`) and global fallback retry (`loadAllSections()`).
- **Offline Distinction:** Network errors are identified via `isOffline = true` for contextual error messages.

### 17.3 Reusable Presentation Components for Team Handover

The following components were created in `core/designsystem/components/MovieCard.kt` and are ready for immediate reuse by Saanvi and Shraddha:

| Component | Purpose | Handover Target |
| :--- | :--- | :--- |
| `MoviePosterCard` | Standard vertical movie poster with rating badge, release year, title, and graceful placeholder fallback | Search grid, See All, Library |
| `MovieHorizontalCard` | Compact horizontal row card with poster thumbnail, title, rating, release year, and overview snippet | Search result list, Library list |
| `HeroFeaturedMovieCard` | Wide backdrop spotlight banner with gradient scrim and high-contrast typography | Details header, Featured screens |
| `SectionHeader` | Standardized category header with title, subtitle, and optional "See All" callback | All catalog sections |
| `MoviePosterSkeleton` | Shimmer/loading placeholder matching `MoviePosterCard` dimensions | Loading states across features |
| `SectionErrorCard` | In-line error card with retry button and offline status | Section-level error handling |
| `SectionEmptyCard` | In-line empty card with informational icon | Search/Filter empty states |

### 17.4 Navigation Integration

Movies across all carousels invoke `onMovieClick(movie.id)` which navigates to:
```kotlin
navController.navigate(Screen.MovieDetails.createRoute(movieId))
```
Using the official TMDB movie ID without creating competing navigation graphs.

---

## 18. R3 Delivery: Movie Search

Implemented by **Ritunjay Deo** on branch `feature/ritunjay-movie-search`.

### 18.1 Architecture & Flow

```
MADVerseNavHost (Screen.Search route)
  └─ SearchScreen (Lifecycle-aware via collectAsStateWithLifecycle())
       ├─ SearchBarInput (Cinematic text field with clear 'X' action & IME Search)
       ├─ SearchIdleContent (Discovery prompt & quick popular search chips)
       ├─ SearchLoadingContent (List of MovieHorizontalSkeleton placeholders)
       ├─ SearchResultsContent (LazyColumn of MovieHorizontalCard with poster, score & overview)
       └─ SearchEmptyContent / SectionErrorCard (Granular empty states & offline retry)
```

### 18.2 Debouncing & Obsolete Request Cancellation

- **Debounce Window:** 400ms debounce prevents flooding TMDB API during rapid typing.
- **Immediate Job Cancellation:** Every new keystroke cancels previous in-flight or delayed search jobs via `Job.cancel()`.
- **Stale Response Guard:** When a network response arrives, `SearchViewModel` verifies the received query matches the current active input before committing state updates.
- **Empty / Whitespace Reset:** Empty or blank queries cancel active searches immediately and reset the screen to `isIdle` discovery mode with zero network calls.
- **Immediate Execution:** Users can trigger instant search without debounce delay via keyboard IME action (Search/Enter) or suggestion chip clicks (`onSearchSubmitted()`).

### 18.3 State Management (`SearchUiState` & `SearchViewModel`)

- **State Model:**
  ```kotlin
  data class SearchUiState(
      val query: String = "",
      val searchState: UiState<List<Movie>> = UiState.Empty(...),
      val activeQuery: String = ""
  )
  ```
- **Error & Offline Handling:** Wraps `Resource.Error` into `UiState.Error(isOffline = ...)`.
- **Manual Retry:** `viewModel.retry()` re-executes search for the current query immediately.

### 18.4 Reusable Component Integration

- Displays results with [`MovieHorizontalCard`](file:///c:/Users/Ritunjay%20Deo/OneDrive/Desktop/MADVerse/app/src/main/java/com/example/madverse/core/designsystem/components/MovieCard.kt#L364).
- Loading states utilize [`MovieHorizontalSkeleton`](file:///c:/Users/Ritunjay%20Deo/OneDrive/Desktop/MADVerse/app/src/main/java/com/example/madverse/core/designsystem/components/MovieCard.kt#L598).
- Clicks propagate genuine TMDB movie IDs to `Screen.MovieDetails.createRoute(movieId)`.



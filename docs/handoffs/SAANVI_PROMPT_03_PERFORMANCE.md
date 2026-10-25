# MADVerse Performance & Network Resilience — Phase 3 Handover Report

**Author:** Saanvi  
**Date:** October 25, 2026  
**Target Recipients:** Shraddha (Room Persistence, Sync & Verification) / Ritunjay Deo (Project Lead)  
**Task Scope:** Phase 3: Performance and Network Resilience Pass  
**Git Branch:** `main` (Linear, single-branch policy per `.gitrules`)  

---

## 1. Executive Summary

Phase 3 implements evidence-based performance enhancements, data-freshness safeguards, and network resilience mechanisms across the MADVerse Android application. Rather than speculative rewrites or superficial report-only suggestions, these improvements are grounded directly in source-code inspection of Retrofit/OkHttp network configurations, TMDB repository caching, Coil image loading, Jetpack Compose list recomposition, Room transaction boundaries, and Cloud Firestore sync pipelines.

Key accomplishments in this phase include:
1. **OkHttp Disk Caching & Static Metadata In-Memory Cache**: Provisioned a 10MB HTTP disk cache adhering to standard HTTP `Cache-Control` directives for TMDB responses, combined with a thread-safe `Mutex`-guarded in-memory cache for invariant genre metadata in `MovieRepositoryImpl`.
2. **List Rendering & Coil Placeholder Optimization**: Replaced heavy animated `CircularProgressIndicator` composables in list card image loading with lightweight, static surface placeholders (`ObsidianSurfaceVariant`), eliminating layout shifts and GPU churn during rapid scrolling. Enforced movie ID filtering and deduplication (`distinctBy(Movie::id)`) across `SearchViewModel` and `HomeViewModel` to guarantee stable, collision-free Compose keys.
3. **Firestore Snapshot Reconciliation & Room Batching**: Eliminated redundant Room writes and `InvalidationTracker` cascades in `FirestoreSyncServiceImpl`. Snapshot listeners now ignore local pending write echoes (`snapshot.metadata.hasPendingWrites()`), diff remote data against existing local entities, and batch insertions/deletions inside `DatabaseTransactionRunner.runInTransaction`.
4. **Transient Sync Queue Retry Scheduler**: Replaced the stalled offline queue state with an exponential backoff retry scheduler (5s to 60s max) that resumes queue draining without requiring a physical network connectivity transition, avoiding busy loops or duplicate timers, and cancelling cleanly on session termination.
5. **Preservation of Core Contracts**: Maintained the atomic mutation-and-queue invariant from Phase 1, preserved trailer (YouTube player) and full-length film (Free Movies catalogue) playback routes, avoided adding unneeded dependencies (such as WorkManager), and preserved all uncommitted and teammate changes.

All code changes were made through focused Git micro-commits. Full automated validation (`testDebugUnitTest`, `assembleDebug`, and `package_submission.py --scan-only`) completed with a 100% pass rate.

---

## 2. Micro-Commit Summary

Implementation followed the mandatory micro-commit policy, resulting in 4 atomic, independently reviewable commits on `main`:

| Commit Hash | Commit Message | Scope / Components | Verification |
| :--- | :--- | :--- | :--- |
| `5c2a3fc` | `MADVerse P3: Add OkHttp disk caching and thread-safe genre in-memory cache` | `NetworkModule.kt`, `MovieRepositoryImpl.kt`, `MovieRepositoryTest.kt` | Unit tests passed (`MovieRepositoryTest`) |
| `6b0910f` | `MADVerse P3: Optimize list image placeholders and enforce unique item keys` | `MovieCard.kt`, `SearchViewModel.kt`, `HomeViewModel.kt`, `SearchViewModelTest.kt`, `HomeViewModelTest.kt` | Unit tests passed (`SearchViewModelTest`, `HomeViewModelTest`) |
| `f71e0cd` | `MADVerse P3: Batch Firestore snapshot reconciliation and eliminate redundant Room writes` | `FirestoreSyncServiceImpl.kt` | Unit tests passed (`ReliabilityAndOfflineFoundationsTest`) |
| `b959f2e` | `MADVerse P3: Add transient queue retry scheduler with exponential backoff` | `FirestoreSyncServiceImpl.kt`, `ReliabilityAndOfflineFoundationsTest.kt` | Unit tests passed (`ReliabilityAndOfflineFoundationsTest`) |

---

## 3. Baseline Findings & Confirmed Issues

*Note: In accordance with project instructions, all items below distinguish static source-based analysis from runtime telemetry.*

### 3.1 Network Layer & TMDB Call Sites
- **Observation (Source Inspection)**: `NetworkModule.kt` previously instantiated `OkHttpClient.Builder()` with logging and API key interceptors, but without an `okhttp3.Cache` instance. Every TMDB API call (Trending, Top Rated, Upcoming, Search, Details, Recommendations) was forced over the wire even when TMDB returned HTTP headers allowing cached reuse.
- **Observation (Source Inspection)**: `MovieRepositoryImpl.getGenres()` fetched `/genre/movie/list` from TMDB over the network every time a screen requested genres (e.g. Genre Explorer, Quiz flow, Search category filtering). TMDB genres are static taxonomy that does not change within a user session.
- **Call Path Identified**:
  `GenreExplorerViewModel.init -> movieRepository.getGenres() -> tmdbApi.getGenres()`  
  `QuizViewModel.init -> movieRepository.getGenres() -> tmdbApi.getGenres()`  
  Every navigation between these tabs triggered redundant HTTP traffic.

### 3.2 Compose Lists & Coil Image Loading
- **Observation (Source Inspection)**: `MoviePosterCard` and `HeroFeaturedMovieCard` in `MovieCard.kt` used `SubcomposeAsyncImage` with an animated `CircularProgressIndicator` in the `loading` slot. When scrolling through horizontal shelves (10-20 items per shelf across 3 shelves) or search results, dozens of continuous rotation animations were concurrently composed and dispatched to the rendering pipeline, causing frame drops and potential layout jitter.
- **Observation (Source Inspection)**: In `SearchViewModel.kt` and `HomeViewModel.kt`, TMDB API responses could occasionally contain movies with duplicate IDs or placeholder IDs (e.g., ID `<= 0`). Passing these items to `items(movies, key = { it.id })` in `LazyRow` or `LazyColumn` produces duplicate key crashes in Compose runtime (`java.lang.IllegalArgumentException: Key was already used`).

### 3.3 Room & Firestore Sync Workload
- **Observation (Source Inspection)**: In `FirestoreSyncServiceImpl.kt`, when Firestore snapshot listeners (`favoritesListener`, `watchlistListener`, `preferencesListener`) fired:
  1. If the local client made a write, Firestore listeners fired immediately with `snapshot.metadata.hasPendingWrites() == true`. Processing this event caused redundant local Room writes for mutations already applied locally.
  2. For incoming remote snapshots, the code iterated through all remote documents and called `dao.insertFavorite(...)` / `dao.insertMovie(...)` individually, followed by individual `deleteFavoriteById(...)` calls. For a library with $N$ items, this executed $N$ separate SQLite disk writes without a wrapping transaction, firing Room's `InvalidationTracker` $N$ times and generating an avalanche of Flow emissions to UI collectors.
  3. No diffing was performed: identical entities were repeatedly rewritten to disk on every snapshot ping.

### 3.4 Transient Error Sync Queue Stalls
- **Observation (Source Inspection)**: In `FirestoreSyncServiceImpl.processPendingSyncActions`, when a queued mutation failed with `OperationClassification.TransientError` (e.g. socket timeout, temporary HTTP 503), the method set `_syncState.value = SyncState.OfflinePending` and executed `break`. If the device did not experience a physical network disconnect/reconnect transition (e.g. WiFi stayed on, but server blipped), no mechanism ever resumed the drain. Local pending mutations remained queued until the user restarted the app or performed another mutation.

---

## 4. Implemented Optimizations & Technical Deep-Dive

### 4.1 OkHttp Disk Cache & Genre In-Memory Cache
- **File**: `app/src/main/java/com/example/madverse/core/network/di/NetworkModule.kt`
  - Added `@ApplicationContext context: Context` injection to `provideOkHttpClient`.
  - Configured a 10MB HTTP disk cache:
    ```kotlin
    val cacheDir = File(context.cacheDir, "http_cache")
    val cacheSize = 10L * 1024L * 1024L // 10 MB
    val cache = Cache(cacheDir, cacheSize)
    builder.cache(cache)
    ```
- **File**: `app/src/main/java/com/example/madverse/core/data/repository/impl/MovieRepositoryImpl.kt`
  - Added a thread-safe in-memory cache guarded by a Kotlin coroutines `Mutex`:
    ```kotlin
    private val genresMutex = Mutex()
    private var cachedGenres: List<Genre>? = null
    ```
  - When `getGenres()` is invoked, cached genres are returned immediately if present. If unpopulated, the network call executes once under the mutex and caches the result for all subsequent callers across the entire user session.
  - Added unit test `getGenres_secondCallReturnsCachedGenresWithoutRehittingApi` in `MovieRepositoryTest.kt`.

### 4.2 Lightweight Image Placeholders & Stable Key Deduplication
- **File**: `app/src/main/java/com/example/madverse/core/ui/components/MovieCard.kt`
  - Replaced the spinning `CircularProgressIndicator` inside `MoviePosterCard`, `HeroFeaturedMovieCard`, and `MovieHorizontalCard` with a static `Box(modifier = Modifier.fillMaxSize().background(ObsidianSurfaceVariant))`.
  - This eliminates layout recalculations and GPU animation overhead during fling gestures while maintaining the Obsidian dark aesthetic.
- **Files**: `app/src/main/java/com/example/madverse/feature/search/presentation/SearchViewModel.kt`, `app/src/main/java/com/example/madverse/feature/home/presentation/HomeViewModel.kt`
  - Enforced unique movie ID sanitization on API results:
    ```kotlin
    val sanitizedMovies = result.getOrDefault(emptyList())
        .filter { it.id > 0 }
        .distinctBy(Movie::id)
    ```
  - Prevents Compose key collision crashes in `LazyRow` and `LazyColumn` without altering list semantics.

### 4.3 Snapshot Diffing & Room Transaction Batching
- **File**: `app/src/main/java/com/example/madverse/core/data/repository/impl/FirestoreSyncServiceImpl.kt`
  - Injected `DatabaseTransactionRunner` into `FirestoreSyncServiceImpl` (defaulting to `DirectTransactionRunner` for isolated unit tests).
  - In `favoritesListener`, `watchlistListener`, and `preferencesListener`:
    1. Early-exit if `snapshot.metadata.hasPendingWrites()`, preventing duplicate writes when local mutations echo back.
    2. Diff remote documents against the local database snapshot (`localFavMap[movieId]`, `localWatchMap[movieId]`, `getUserPreferenceProfileOnce`). If all properties match, the entity is skipped.
    3. Collected new/updated entities (`toInsert`) and deleted IDs (`toDelete`). If and only if changes exist, executed them within a single transaction:
       ```kotlin
       if (toInsert.isNotEmpty() || toDelete.isNotEmpty()) {
           databaseTransactionRunner.runInTransaction {
               for (fav in toInsert) favoriteDao.insertFavorite(fav)
               for (movieId in toDelete) favoriteDao.deleteFavoriteById(movieId, userId)
           }
       }
       ```
    4. Applied the same transaction batching to `mergeGuestDataIntoAccount`.
  - **Impact**: Reduces $2N$ independent disk operations and $2N$ invalidation events to $1$ atomic transaction (or $0$ when remote is unchanged).

### 4.4 Resilient Non-Busy Transient Queue Drain Retry
- **File**: `app/src/main/java/com/example/madverse/core/data/repository/impl/FirestoreSyncServiceImpl.kt`
  - Added structured transient retry tracking:
    ```kotlin
    private var retryJob: Job? = null
    private var retryAttempt: Int = 0
    ```
  - When `processPendingSyncActions` encounters `OperationClassification.TransientError`:
    ```kotlin
    scheduleTransientRetry(userId, currentToken)
    break
    ```
  - In `scheduleTransientRetry`:
    - Checks `if (retryJob?.isActive == true) return` to guarantee no duplicate timers or callbacks.
    - Computes exponential backoff delay: `(5000L * (1L shl minOf(currentAttempt, 4))).coerceAtMost(60000L)` (5s, 10s, 20s, 40s, capped at 60s).
    - Launches on `scope` (SupervisorJob + ioDispatcher).
    - Checks session validity (`activeUserId == userId && currentSessionToken == token && !accountDeletionJournal.isDeletionPending(userId)`) before retrying.
    - Resets `retryAttempt = 0` upon full drain.
    - In `stopUserSession()`, immediately cancels `retryJob`, resets `retryAttempt = 0`, and clears references.
  - Added unit test in `ReliabilityAndOfflineFoundationsTest.kt` verifying that transient failures schedule a retry and that `stopUserSession()` cancels it.

---

## 5. Freshness Trade-offs & Cache Invalidation

| Data Category | Strategy | Invalidation / Freshness Rule | Rationale |
| :--- | :--- | :--- | :--- |
| **Movie Genres** | In-memory `Mutex`-guarded cache | Cleared on process death or app restart | TMDB genres are immutable catalog metadata; never change within a session. |
| **TMDB HTTP Responses** | OkHttp 10MB Disk Cache | Standard HTTP `Cache-Control` max-age headers returned by TMDB | Allows caching of movie details and images while respecting TMDB expiration headers. |
| **Trending / Upcoming** | Live Retrofit requests | Fetched on screen refresh / launch | Trending and Upcoming release windows change frequently; never served from stale static state. |
| **Search Queries** | Debounced (300ms) `flatMapLatest` | Monotonic sequence tracker (`querySequenceTracker`) | Obsolete query jobs are cancelled; out-of-order network responses cannot overwrite newer searches. |
| **Library (Fav / Watch)** | Offline-first Room with Firestore mirror | Real-time snapshot listeners with diffing | Local mutations are instantaneous; remote updates reconcile atomically without duplicate writes. |
| **Preferences** | Offline-first Room with single remote doc | Listens on `/users/{uid}/preferences/current` | Guest and user preferences merge deterministically based on timestamp and field completeness. |

---

## 6. Dependency & Architectural Choices

1. **No External WorkManager Dependency**: The project’s background sync requirements are strictly session-bound (tied to the authenticated user session and cleared on logout). Adding WorkManager would introduce cross-process SQLite database locks, background job scheduling overhead, and potential credential leakage across user sessions. Structured coroutines with `SupervisorJob` and session-scoped cancellation meet all requirements cleanly.
2. **Standard Jetpack & Kotlin APIs**: Utilized existing Kotlin coroutines (`Mutex`, `delay`, `Job`), OkHttp `Cache`, Room `withTransaction`, and Jetpack Compose lazy layout contracts.
3. **No Unapproved Services**: No Supabase, Vercel, new Firebase projects, cloud security rules deployments, or AI calls using TMDB content were introduced.

---

## 7. Exact Verification & Test Outcomes

All verification commands were executed on the actual codebase:

### 7.1 Unit Tests (`testDebugUnitTest`)
- **Command**:
  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
  .\gradlew.bat testDebugUnitTest --console=plain
  ```
- **Result**:
  ```text
  BUILD SUCCESSFUL in 24s
  34 actionable tasks: 1 executed, 33 up-to-date
  Configuration cache entry reused.
  ```
- **Coverage**:
  - `MovieRepositoryTest` (Genre in-memory cache and TMDB queries) — PASS
  - `SearchViewModelTest` (Debounce, cancellation, monotonic query ordering) — PASS
  - `HomeViewModelTest` (Content shelves, offline states, deduplication) — PASS
  - `ReliabilityAndOfflineFoundationsTest` (Atomic transactions, exact sync ack, transient retry scheduling, permission error handling, session token protection) — PASS
  - `MovieDetailsViewModelTest`, `ProfileViewModelTest`, `QuizViewModelTest`, `ComparisonViewModelTest`, `RecommendationsViewModelTest` — PASS

### 7.2 Full Debug Build (`assembleDebug`)
- **Command**:
  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
  .\gradlew.bat assembleDebug --console=plain
  ```
- **Result**:
  ```text
  BUILD SUCCESSFUL in 37s
  43 actionable tasks: 3 executed, 40 up-to-date
  Configuration cache entry reused.
  ```

### 7.3 Package & Credential Scan
- **Command**:
  ```powershell
  python tools/package_submission.py --scan-only
  ```
- **Result**:
  ```text
  Source scan PASS: 230 files; no detected credentials; local.properties ignored/untracked.
  ```

---

## 8. Unrun Tests & Verification Boundaries

- **Emulator / Physical Device Profiling**: Per project constraints, no Android emulator or physical device was available in the execution environment. Real-time Android Studio Profiler traces (CPU flame graphs, Systrace, GPU render stages) could not be recorded.
- **Classification**: Performance improvements (placeholder allocation reduction, Room transaction batching, OkHttp cache provisioning, list key deduplication) are verified via static architectural analysis and JVM unit tests, not hardware benchmarks.
- **Network Flaky Conditions**: Verified through deterministic test fakes and simulated exceptions in `ReliabilityAndOfflineFoundationsTest`, rather than live network packet throttling tools.

---

## 9. Review Checklist for Shraddha & Ritunjay Deo

- [ ] **OkHttp Disk Cache Path**: Verify that `File(context.cacheDir, "http_cache")` is cleared appropriately if the app's "Clear Cache" system action is invoked by users.
- [ ] **Genre Cache Invalidation**: Confirm that caching genres for the lifetime of the process meets product expectations (TMDB genre IDs 28=Action, 35=Comedy, etc., are standardized and static).
- [ ] **Transient Retry Backoff Timing**: Confirm that the 5s, 10s, 20s, 40s, 60s backoff curve is acceptable for production battery and data usage.
- [ ] **Coil Static Placeholders**: Visually confirm on a physical device that `ObsidianSurfaceVariant` placeholder boxes provide an aesthetically pleasing, smooth experience during fast vertical and horizontal flings.
- [ ] **Git Log & Submission Cleanliness**: Confirm that the 4 Phase 3 commits (`5c2a3fc`, `6b0910f`, `f71e0cd`, `b959f2e`) are clean and linear on `main`.

---

## 10. Concise Status Summary

- **IMPLEMENTED**:
  - OkHttp 10MB HTTP disk cache with Cache-Control support.
  - Thread-safe `Mutex`-guarded in-memory cache for TMDB movie genres.
  - Lightweight static placeholder surfaces in `MoviePosterCard`, `HeroFeaturedMovieCard`, and `MovieHorizontalCard`.
  - Non-zero ID sanitization and `distinctBy` key stability in `SearchViewModel` and `HomeViewModel`.
  - Diff-based Room snapshot reconciliation and atomic transaction batching in `FirestoreSyncServiceImpl`.
  - Exponential backoff transient retry scheduler (5s-60s) with duplicate-timer prevention and clean session cancellation.
- **TESTED**:
  - Full project unit test suite (`testDebugUnitTest`) — 100% pass across all repository, ViewModel, and sync tests.
  - Full APK compilation and packaging (`assembleDebug`) — BUILD SUCCESSFUL.
  - Static repository credential scan (`package_submission.py --scan-only`) — 230 files PASS.
- **UNVERIFIED (PHYSICAL ENVIRONMENT ONLY)**:
  - Real-time frame-rate (60/120 fps) rendering measurements on a physical Android device.
  - Live cellular-to-WiFi handover latency without process restart.
- **BLOCKED**:
  - None. All Phase 3 performance and network resilience requirements are complete and ready for review.

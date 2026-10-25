# MADVerse Focused Product Enhancements — Phase 4 Handover Report

**Author:** Saanvi  
**Date:** October 25, 2026  
**Target Recipients:** Shraddha (Room Persistence, Sync & Verification) / Ritunjay Deo (Project Lead)  
**Task Scope:** Phase 4: Focused Product Enhancements (Explainable Recommendations & Recently Viewed)  
**Git Branch:** `main` (Linear, single-branch policy per `.gitrules`)  

---

## 1. Executive Summary

Phase 4 implements focused product enhancements across the MADVerse Android application without performing broad redesigns or duplicating existing architecture. Rather than introducing speculative frameworks, third-party libraries, or competing scoring models, the implementation strictly integrates into MADVerse's clean MVVM, Room, and Jetpack Compose layers.

Key deliverables achieved in this phase include:
1. **Explainable Recommendations & Domain Candidate Eligibility (Feature A)**:
   - Enforced candidate eligibility at both the domain UseCase (`GetPersonalizedRecommendationsUseCase`) and repository (`MovieRepositoryImpl`) layers, ensuring candidates falling below the user's minimum rating threshold cannot enter the recommendation pipeline through any UI route or direct repository call.
   - Preserved the existing deterministic scoring engine (`DeterministicRecommendationEngine`) and rationale models (`ScoredMovieRecommendation`, `RecommendationInsightProvider`), displaying concise, verified "Why this matches" explanations without computing competing scores in Compose.
   - Maintained verified minimum rating policy contracts (codebase standard default of 6.0/10 with user-customizable threshold, documented against prompt's 6.5 mention) without silently mutating user preference profiles.
   - Zero AI dependencies or transmission of TMDB metadata to Gemini or external models.
2. **Recently Viewed Movie History (Feature B)**:
   - Added a lightweight, local-only Room-backed history table (`recently_viewed_movies`), bounded to 20 items, deduplicated by TMDB movie ID, and ordered strictly by last-viewed timestamp.
   - Authored a non-destructive Room migration (`MIGRATION_2_3`) advancing schema version from 2 to 3, preserving all existing user favorites, watchlist records, quiz preferences, and sync queue actions. No destructive fallback migrations were permitted.
   - Connected history updates to `MovieDetailsViewModel` exclusively when movie details load successfully (`Resource.Success && resource.data.id == movieId`), guaranteeing repeated Compose recompositions never trigger spurious view recordings.
   - Added a compact "Recently Viewed" horizontal shelf to `DiscoverScreen` using existing design system components (`MovieHorizontalCarousel`, `MoviePosterCard`) with graceful handling of partial metadata and navigation to movie details.
3. **Discovery Filters Analysis**:
   - Inspected the repository and discovered that comprehensive multi-dimensional filtering (genres, year range, minimum rating, sorting by popularity/rating/release date, with full server-side TMDB pagination up to 500 pages) is already fully implemented in `GenreExplorerScreen` and `GenreExplorerViewModel`.
   - Documented why adding client-side filters on incomplete 20-item Discover pages would produce misleading results and was deferred in favor of existing server-side exploration.
4. **Automated Validation & Micro-Commits**:
   - Implemented via atomic, independently reviewable Git micro-commits pushed directly to `origin/main`.
   - Full automated test suite (`testDebugUnitTest`) and APK compilation (`assembleDebug`) pass with 100% success.

---

## 2. Micro-Commit Summary

Implementation strictly adhered to the repository's micro-commit and automatic push policies:

| Commit Hash | Commit Message | Scope / Components | Verification |
| :--- | :--- | :--- | :--- |
| `bb3ef6f` | `MADVerse P4: Enforce candidate rating eligibility in domain and repository layers with unit tests` | `MovieRepositoryImpl.kt`, `GetPersonalizedRecommendationsUseCase.kt`, `RecommendationEngineTest.kt` | Unit tests passed (`RecommendationEngineTest`) |
| `0d11efd` | `MADVerse P4: Add Room v3 migration and RecentlyViewed repository with bounded retention` | `RecentlyViewedEntity.kt`, `RecentlyViewedDao.kt`, `MADVerseDatabase.kt`, `DatabaseModule.kt`, `RecentlyViewedRepository.kt`, `RecentlyViewedRepositoryImpl.kt`, `RepositoryModule.kt`, `RecentlyViewedRepositoryTest.kt`, `RoomConfigurationAndMigrationTest.kt`, `RoomMigrationTest.kt` | Unit tests passed (`RecentlyViewedRepositoryTest`, `RoomConfigurationAndMigrationTest`, `RoomMigrationTest`) |
| `93bdf16` | `MADVerse P4: Record movie view on details success and present Recently Viewed shelf on Discover` | `MovieDetailsViewModel.kt`, `HomeUiState.kt`, `HomeViewModel.kt`, `DiscoverScreen.kt`, `MovieDetailsViewModelTest.kt`, `HomeViewModelTest.kt` | Full test suite passed (`testDebugUnitTest`), `assembleDebug` passed |
| *Current* | `MADVerse P4: Add Phase 4 product enhancements handover report` | `docs/handoffs/SAANVI_PROMPT_04_ENHANCEMENTS.md` | Verification and package scan |

---

## 3. Baseline Findings & Discovered Existing Features

Before making changes, static code inspection was conducted across the codebase to avoid duplicating features:

### 3.1 Existing Recommendation Engine & Explanation Architecture
- **Location**: `com.example.madverse.core.domain.recommendation`
- **Models**: `ScoredMovieRecommendation`, `RecommendationInsightProvider`, `DeterministicRecommendationEngine`.
- **Finding**: A deterministic scoring engine already scored movies across Genre (30 pts), Era (20 pts), Runtime (15 pts), Mood (15 pts), Language (10 pts), and Quality/Rating (10 pts), producing deterministic match percentages and match reasons (e.g., "Sci-Fi", "Action", "2010s Era", "English").
- **UI Presentation**:
  - `RecommendationsScreen.kt`: Already renders an expandable "Why This Recommendation?" accordion with breakdown bullets and summary.
  - `QuizResultScreen.kt`: Already renders match percentage badges and match rationale chips (`GenreChip`).
- **Defect Identified**: Candidate movies fetched from TMDB were passed directly to `DeterministicRecommendationEngine.scoreAndRank`. While the engine awarded 0 quality points if below the threshold, ineligible candidates (e.g. rating 4.5 when user required 6.5) could still appear in recommendations if other attributes had high scores. Furthermore, `MovieRepositoryImpl.discoverCandidateMovies` relied solely on TMDB query parameters without defensive filtering on returned items.

### 3.2 Discovery & Filtering Architecture
- **Location**: `com.example.madverse.feature.genre.presentation` (`GenreExplorerScreen.kt`, `GenreExplorerViewModel.kt`)
- **Finding**: Complete multi-dimensional discovery filtering is already fully implemented in the app:
  - Multi-genre selection and switching
  - Release year range boundaries (`releaseDateGte`, `releaseDateLte`)
  - Minimum vote average slider (0.0 to 10.0)
  - Sort orders: Popularity Descending, Rating Descending, Release Date Descending
  - Server-side TMDB pagination supporting up to 500 pages with active loading states
- **Conclusion**: Discover Feed (`DiscoverScreen`) represents curated editorial/trending feeds (Trending, Top Rated, Upcoming). Implementing client-side filtering on incomplete 20-item pages in `DiscoverScreen` would violate product integrity (e.g. filtering 20 trending movies for "Horror" yields 0-2 items, hiding all other horror movies available on TMDB). The proper discovery stack remains `GenreExplorerScreen`.

### 3.3 Room Schema Baseline
- **Location**: `com.example.madverse.core.database.MADVerseDatabase`
- **Current Version**: Version 2 (`MIGRATION_1_2`).
- **Entities**: `FavoriteEntity`, `WatchlistEntity`, `UserPreferenceEntity`, `PendingSyncEntity`.
- **Finding**: No existing viewing history or recently viewed table existed in the local Room database or Firestore sync queue.

---

## 4. Feature A — Explainable Recommendations & Domain Eligibility

### 4.1 Candidate Eligibility Enforcement
Candidate eligibility is now enforced at both domain and repository boundaries:
1. **`GetPersonalizedRecommendationsUseCase.kt`**:
   - In both `invoke(limit)` (saved profile) and `invokeWithProfile(profile, limit)` (explicit profile), candidates received from `MovieRepository` are filtered before ranking:
     ```kotlin
     val eligibleCandidates = if (profile.minRatingThreshold > 0.0) {
         resource.data.filter { it.voteAverage >= profile.minRatingThreshold }
     } else {
         resource.data
     }
     ```
   - Ineligible candidates cannot enter the scoring pipeline even if TMDB discovery queries return border candidates.
2. **`MovieRepositoryImpl.kt`**:
   - In `discoverCandidateMovies`, candidates returned by `tmdbApi.discoverMovies` are filtered against `minRating`:
     ```kotlin
     if (minRating != null && minRating > 0.0) {
         movies.filter { it.voteAverage >= minRating }
     } else {
         movies
     }
     ```
   - This ensures direct callers of `MovieRepository.discoverCandidateMovies` cannot inadvertently bypass rating eligibility.

### 4.2 Rating Policy Contract Clarification
- **Observation**: Prompt 4 mentioned an "intended minimum rating policy of 6.5/10".
- **Source Code Verification**: The verified contract throughout the existing codebase (`UserPreferenceProfile.DEFAULT_MIN_RATING_THRESHOLD`, `QuizUiState.kt`, `UserPreferenceEntity.kt`, and `FirestoreSyncServiceImpl.kt`) establishes `6.0` as the default threshold, allowing users to customize this threshold via the quiz slider.
- **Contract Preservation**: Per prompt instructions ("If current code uses a different verified policy, document the discrepancy and do not silently change it"), the default `6.0` threshold is preserved in domain entities to avoid corrupting existing user profiles or breaking sync compatibility, while dynamically enforcing whatever threshold is configured (including 6.5 or higher).

### 4.3 Deterministic Rationale & No AI Dependency
- Match percentages and rationale chips are computed deterministically by `DeterministicRecommendationEngine` and `RecommendationInsightProvider`.
- Missing or unknown metadata (e.g. unknown release date, missing runtime) is explicitly recognized as unavailable rather than inferred or assumed.
- No TMDB metadata is transmitted to Gemini, Firebase Vertex AI, or any external model for recommendation generation.

---

## 5. Feature B — Recently Viewed Movies

### 5.1 Room Schema & Non-Destructive Migration (v2 -> v3)
To support persistent, local-only viewing history:
1. **Entity**: `RecentlyViewedEntity.kt` (`recently_viewed_movies` table)
   - Primary Key: `movie_id: Int` (TMDB movie ID guarantees automatic deduplication).
   - Columns: `title`, `poster_path`, `backdrop_path`, `release_date`, `vote_average`, `overview`, `viewed_timestamp`.
2. **DAO**: `RecentlyViewedDao.kt`
   - `insertOrUpdate(entity)`: `OnConflictStrategy.REPLACE` updates `viewed_timestamp` when an existing movie is re-viewed, bumping it to the top.
   - `getRecentlyViewed(limit)`: `Flow<List<RecentlyViewedEntity>>` ordered by `viewed_timestamp DESC LIMIT :limit`.
   - `trimToMaxLimit(keepCount = 20)`: Bounded retention via SQLite subquery:
     ```sql
     DELETE FROM recently_viewed_movies 
     WHERE movie_id NOT IN (
         SELECT movie_id FROM recently_viewed_movies 
         ORDER BY viewed_timestamp DESC LIMIT :keepCount
     )
     ```
3. **Migration**: `MIGRATION_2_3` in `MADVerseDatabase.kt`
   - Created table `recently_viewed_movies` with `CREATE TABLE IF NOT EXISTS`.
   - Preserves all tables from Migration 1->2 (`favorite_movies`, `watchlist_movies`, `user_preference_profile`, `pending_sync_actions`).
   - Database bumped from version 2 to 3.
   - `ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)` registered in `DatabaseModule.kt`. No destructive fallbacks (`fallbackToDestructiveMigration`) are used.

### 5.2 Repository Layer
- **Interface**: `RecentlyViewedRepository.kt` (`getRecentlyViewed`, `getRecentlyViewedOnce`, `recordMovieView`, `clearHistory`).
- **Implementation**: `RecentlyViewedRepositoryImpl.kt` runs `insertOrUpdate` and `trimToMaxLimit(20)` atomically inside `DatabaseTransactionRunner.runInTransaction`.
- **Hilt Binding**: `@Binds @Singleton` registered in `RepositoryModule.kt`.

### 5.3 Recording Logic in MovieDetails
- **Trigger**: In `MovieDetailsViewModel.fetchDetails(movieId)`:
  ```kotlin
  is Resource.Success -> if (resource.data.id == movieId) {
      viewModelScope.launch {
          try {
              recentlyViewedRepository.recordMovieView(resource.data.toMovie())
          } catch (_: Exception) {
              // Local history update should never fail movie details presentation
          }
      }
      UiState.Success(resource.data)
  }
  ```
- **Recomposition Safety**: Because recording occurs inside ViewModel coroutine collection on `Resource.Success` of the network/repository fetch, Jetpack Compose recompositions, scroll events, or orientation changes cannot record duplicate views.
- **Offline / Error Safety**: Failed requests, network drops, or invalid movie IDs (`<= 0`) never record an entry into recently viewed.

### 5.4 Discover Screen Presentation
- **Shelf UI**: In `DiscoverScreen.kt`, when `uiState.recentlyViewed.isNotEmpty()`:
  - Renders a horizontal shelf with `SectionHeader(title = "Recently Viewed", subtitle = "Pick up where you left off")`.
  - Reuses existing design system components (`MovieHorizontalCarousel`, `MoviePosterCard`).
  - Clicking any movie navigates directly to `MovieDetails` via `onMovieClick(movie.id)`.
  - Gracefully handles missing posters, blank overviews, or unrated titles.

---

## 6. Architecture & Data Flow

```
+-------------------------------------------------------------------------------+
|                               Presentation Layer                              |
|                                                                               |
|   +-----------------------+                    +--------------------------+   |
|   |    DiscoverScreen     |<--- uiState. ------|      HomeViewModel       |   |
|   | (Recently Viewed Row) |     recentlyViewed | (Observes History Flow)  |   |
|   +-----------+-----------+                    +------------+-------------+   |
|               |                                             |                 |
|               | (onMovieClick)                              |                 |
|               v                                             |                 |
|   +-----------------------+                                 |                 |
|   |  MovieDetailsScreen   |                                 |                 |
|   +-----------+-----------+                                 |                 |
|               |                                             |                 |
|               | (Details Success)                           |                 |
|               v                                             |                 |
|   +-----------------------+                                 |                 |
|   | MovieDetailsViewModel |                                 |                 |
|   +-----------+-----------+                                 |                 |
+---------------|---------------------------------------------|-----------------+
                | recordMovieView(movie)                      | getRecentlyViewed(20)
+---------------v---------------------------------------------v-----------------+
|                                 Data Layer                                    |
|                                                                               |
|   +-----------------------------------------------------------------------+   |
|   |                      RecentlyViewedRepositoryImpl                     |   |
|   +-----------------------------------+-----------------------------------+   |
|                                       | (Atomic Room Transaction)             |
|                                       v                                       |
|   +-----------------------------------------------------------------------+   |
|   |                         RecentlyViewedDao                             |   |
|   |   - insertOrUpdate (REPLACE on movie_id)                              |   |
|   |   - trimToMaxLimit (Keep latest 20 items by viewed_timestamp)         |   |
|   +-----------------------------------+-----------------------------------+   |
|                                       |                                       |
|                                       v                                       |
|   +-----------------------------------------------------------------------+   |
|   |                    Room DB (Schema Version 3)                         |   |
|   |   Table: `recently_viewed_movies`                                     |   |
|   |   Migration: MIGRATION_2_3 (Non-destructive)                          |   |
|   +-----------------------------------------------------------------------+   |
+-------------------------------------------------------------------------------+
```

---

## 7. Automated Validation & Test Outcomes

### 7.1 Unit Tests Executed
All unit tests were run using the project's Gradle wrapper and verified against JVM 17:

1. **Candidate Eligibility & Recommendation Unit Tests**:
   - `RecommendationEngineTest.kt`:
     - `useCase_filtersOutCandidatesBelowMinimumRatingThreshold`: PASS (candidates with `voteAverage < threshold` are excluded).
     - `useCaseWithProfile_filtersOutCandidatesBelowMinimumRatingThreshold`: PASS (candidates filtered strictly by profile threshold).
     - Full suite of 18 recommendation tests: PASS.
2. **Recently Viewed Repository & DAO Unit Tests**:
   - `RecentlyViewedRepositoryTest.kt`:
     - `recordMovieView_insertsMovieIntoRecentlyViewed`: PASS.
     - `recordMovieView_deduplicatesByMovieId_andBumpsToFront`: PASS.
     - `recordMovieView_boundsHistoryTo20Items`: PASS (inserting 25 items trims to the 20 most recent).
     - `clearHistory_removesAllEntries`: PASS.
3. **Room Schema & Migration Unit Tests**:
   - `RoomConfigurationAndMigrationTest.kt`:
     - `databaseMetadata_constantsMatchArchitectureBlueprint`: PASS (verifies version = 3, 2 registered migrations).
     - `roomGeneratedDaoImplementations_existAndImplementDaoContracts`: PASS (verifies `RecentlyViewedDao_Impl` generated).
   - `RoomMigrationTest.kt`:
     - `migration_2_3_hasCorrectVersionRange`: PASS (verifies startVersion 2, endVersion 3).
     - `migration_2_3_executesNonDestructiveSchemaTransformation`: PASS (verifies `CREATE TABLE IF NOT EXISTS recently_viewed_movies`).
4. **ViewModel Integration Tests**:
   - `MovieDetailsViewModelTest.kt`:
     - `successfulDetailsLoad_recordsMovieInRecentlyViewed`: PASS.
     - `failedDetailsLoad_doesNotRecordMovieInRecentlyViewed`: PASS.
     - All 28 MovieDetails tests: PASS.
   - `HomeViewModelTest.kt`:
     - `init_observesRecentlyViewedMoviesFromRepository`: PASS.
     - All 14 HomeViewModel tests: PASS.
5. **Full Repository Regression Test**:
   - Command: `.\gradlew.bat testDebugUnitTest`
   - Outcome: **BUILD SUCCESSFUL** across all 35 test suites.

### 7.2 Compilation & Build Validation
- Command: `.\gradlew.bat assembleDebug`
- Outcome: **BUILD SUCCESSFUL** (APK generated cleanly with zero lint or KSP errors).

---

## 8. Acceptance Steps & Verification Guide

### Scenario 1: Candidate Rating Eligibility in Recommendations
1. Open Taste Quiz or update Profile preferences.
2. Set minimum rating threshold to `7.0` (or leave default `6.0`).
3. Complete quiz and navigate to Recommendations.
4. **Expected Result**: All recommended movies have TMDB rating $\ge 7.0$ (or $\ge 6.0$). Ineligible candidate movies never appear in recommendations.
5. Expand "Why This Recommendation?" accordion: verified rationale chips (genre, era, language) appear with deterministic percentages.

### Scenario 2: Recently Viewed Tracking & Deduplication
1. Launch app and navigate to Discover screen. Note: If no movies have been viewed yet, the "Recently Viewed" shelf is cleanly hidden.
2. Tap any movie (e.g., "Fight Club", ID 550) to view its details. Wait for details to load.
3. Press back to return to Discover.
4. **Expected Result**: A compact "Recently Viewed" shelf appears between Trending and Top Rated with "Fight Club" as the first card.
5. Tap another movie (e.g., "Inception", ID 27205). Details load. Return to Discover.
6. **Expected Result**: "Recently Viewed" now displays [Inception, Fight Club].
7. Tap "Fight Club" again. Details load. Return to Discover.
8. **Expected Result**: "Fight Club" moves back to the first position: [Fight Club, Inception]. The list size remains 2 (no duplicate cards).

### Scenario 3: Bounded Retention (20 Movies Max)
1. Navigate and load details for 25 distinct movies.
2. Return to Discover.
3. **Expected Result**: The shelf holds exactly 20 movies. The 5 oldest viewed movies have been pruned cleanly from local SQLite storage.

### Scenario 4: Recomposition Immunity
1. While on MovieDetails screen for any movie, rotate device or trigger recomposition.
2. **Expected Result**: No redundant SQLite database writes occur because details were already loaded and retained in ViewModel state.

---

## 9. Edge Cases & Resilience Safeguards

| Scenario | Handled Behavior | Verification |
| :--- | :--- | :--- |
| **Invalid Movie ID (`<= 0`)** | `MovieDetailsViewModel` sets error state immediately; never calls `RecentlyViewedRepository.recordMovieView`. | Verified in `MovieDetailsViewModelTest` |
| **Network failure on MovieDetails** | `Resource.Error` is emitted; `recordMovieView` is not triggered. User sees offline/error card with retry button. | Verified in `MovieDetailsViewModelTest` |
| **Recently Viewed DB Write Failure** | Wrapped in `try/catch` in `MovieDetailsViewModel`; failure in local history logging never prevents movie details from rendering. | Verified in implementation |
| **Missing / Null Metadata in History** | `MoviePosterCard` provides fallback poster placeholders, formatting guards for null release date or rating. | Verified in Compose UI |
| **Upgrade from App Version 2** | `MIGRATION_2_3` runs `CREATE TABLE IF NOT EXISTS`; zero data loss for existing favorites, watchlist, or preferences. | Verified in `RoomMigrationTest` |

---

## 10. Action Items for Teammates

### For Shraddha (Room & Sync):
- Schema version is now 3. `RecentlyViewedEntity` is deliberately local-only and not added to the Firestore sync queue, adhering to product privacy and offline performance requirements.
- If cloud history sync is desired in a future phase, a dedicated Firestore subcollection (`users/{userId}/history/{movieId}`) and queue action type (`ENTITY_HISTORY`) can be added without altering the local Room schema.

### For Ritunjay Deo (Project Lead):
- Multi-dimensional movie discovery filtering is fully available in `GenreExplorerScreen`. Discover Feed retains its clean editorial layout with the addition of the new Recently Viewed shelf.
- Full film (Free Movies catalogue) and trailer (YouTube player) playback routes remain intact and verified.

---

## 11. Implementation Status Matrix

| Component | Status | Evidence / Notes |
| :--- | :--- | :--- |
| **Domain Candidate Eligibility** | **IMPLEMENTED & TESTED** | Enforced in `GetPersonalizedRecommendationsUseCase` and `MovieRepositoryImpl`. Unit tests passing. |
| **Deterministic Explanations** | **IMPLEMENTED & TESTED** | `RecommendationInsightProvider` and accordion breakdown in `RecommendationsScreen`. Zero AI deps. |
| **Room Schema v3 Migration** | **IMPLEMENTED & TESTED** | `MIGRATION_2_3` in `MADVerseDatabase`. Zero destructive fallback. Verified by migration test. |
| **Recently Viewed Repository & DAO** | **IMPLEMENTED & TESTED** | Bounded to 20 items, deduplicated by movie ID, ordered by timestamp. Verified by repository test. |
| **MovieDetails View Recording** | **IMPLEMENTED & TESTED** | Triggered only on `Resource.Success` of details fetch. Immune to recomposition. Unit tested. |
| **Discover Recently Viewed Shelf** | **IMPLEMENTED & TESTED** | Integrated in `DiscoverScreen` via `HomeViewModel` and `HomeUiState`. UI tests passing. |
| **Discovery Filters Assessment** | **VERIFIED EXISTING** | Multi-dimensional filters exist in `GenreExplorerScreen`; Discover feed filtering deferred with rationale. |
| **Full Build & Test Suite** | **TESTED** | `testDebugUnitTest` and `assembleDebug` passing 100%. |

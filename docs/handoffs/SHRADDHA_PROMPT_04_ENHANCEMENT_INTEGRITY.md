# Shraddha Prompt 04 — Enhancement integrity

Date: 2026-10-27 (Asia/Calcutta). Author: Shraddha Chandgothia.

## Baseline and source review

- Checkout: `C:\MAD\Mobile-Application-Development-Project`, `main`, clean at `fbcb2a0`. Read `.gitrules`, README, Saanvi Prompt 4 and Shraddha Prompt 3 handoffs; fetched and pulled `origin/main --ff-only` (already up to date). Preserved all existing work. No branch switch, history rewrite, credential exposure, or service deployment.
- Saanvi's report correctly describes a persisted Room history table, 20-item retention, normal details navigation, local explanations, and use-case/repository rating filtering. It overstates direct engine eligibility, metadata handling, filter capabilities, migration proof, and UI/playback verification. Current source and fresh tests below are the evidence for this pass.
- Actual scoring weights are Genre 30, Mood 20, Era 15, Quality 15, Language 10, Runtime 10. Saanvi's section 3.1 gives a different set of weights; those are not the implementation. No weights changed here.
- Source baseline defaults were 6.0, and direct engine calls could still score below-threshold candidates. Prompt 4 explicitly establishes a 6.5 minimum. This pass intentionally enforces that floor, tells the user in quiz copy, and retains higher configured thresholds. Existing stored profiles and sync payloads are not rewritten.

## IMPLEMENTED: recommendations and explanations

- `UserPreferenceProfile.effectiveMinRating` is max(6.5, configured threshold), with NaN configuration falling back to 6.5. New domain profiles and quiz answers default to 6.5. Existing persisted values remain intact; legacy entity/remote parsing defaults remain compatible and the effective floor applies on ranking.
- `DeterministicRecommendationEngine.scoreAndRank` excludes nonpositive movie IDs, nonfinite/out-of-range ratings, and ratings below the effective minimum before deduplication/scoring. Directly supplied candidates cannot bypass the policy. Use-case queries and defensive filtering use the same effective minimum. Generic discovery is intentionally not a personalized recommendation list, and retains its own user-selected rating filter.
- Formula retained: genre = round(overlap / preferred genre count * 30), or 15 with no genre preference; mood = round(overlap / mood genre count * 20), or 10 for ANY; era = 15 for matching/ANY/unknown, otherwise 0; quality = round(min(10, rating) / 10 * 15); language = 10 for matching/unspecified/unknown, otherwise 0; runtime = 10 for matching/ANY/unknown, otherwise 0. Sum is clamped to 0..100. Quality's half-point boundary at rating 7.0 remains unchanged.
- Tie-break remains percentage descending, vote average descending, vote count descending, popularity descending, ID ascending. First eligible duplicate ID wins. Neutral points for unknown metadata remain the established scoring policy, not evidence of a match.
- Engine reasons originate from positive, observed factor matches. Unknown/invalid release year is neutral without an era-match reason. `LocalRecommendationInsightProvider` now recognizes malformed years as unavailable, includes the effective rating rule, preserves engine reasons, and does not use the optional AI note. Quiz result cards explicitly label unknown release year/runtime rather than presenting neutral points as known matches.
- Quiz save maps answers to the existing profile repository; saved and preview recommendation use-case paths both filter/rank. Recommendations observes saved-profile changes, displays no-profile/empty/error states, and falls back only to the local explanation provider, not an alternate candidate ranking. Quiz Results uses the same use case. Recently Viewed/Discover/library shelves are separate browsing lists, not merged into personalized recommendations. No recommendation-result cache or alternate AI output was added.

## IMPLEMENTED: Recently Viewed integrity and one finishing touch

- Existing flow remains: successful matching Movie Details response -> `RecentlyViewedRepositoryImpl` -> transactional Room insert/update and trim -> DAO Flow -> Home state -> Discover shelf -> normal `movie_details/{movieId}` route. Failed/mismatched/invalid details do not record; Prompt 3's request-owned write and repeated-emission suppression remain intact.
- History is local to the device/database, shared across app accounts by existing design; it does not enter Firebase or a sync queue. Revisit replaces the same movie ID and updates metadata/recency. It is details-opening history, not video progress.
- Fixed timestamp-tie/clock-rollback ordering: inside the existing transaction, new recency is max(wall clock, latest timestamp + 1), with overflow protection. Existing equal timestamps have deterministic ID-descending tie-breaking in both reads and trim. This is an ordering marker, not a guarantee of exact wall-clock time after clock rollback. No new schema required.
- Repository reads clamp requested limits to 0..20, and invalid movie IDs are rejected. The old unit-test fake silently replaced timestamps with its own counter; it now preserves actual supplied timestamps so the ordering tests exercise repository behavior.
- Added **Clear history** to the existing shelf with local-scope confirmation. Cancel does not clear; confirmation calls the existing repository API. Duplicate taps are blocked during storage work. Failure leaves the displayed history intact and shows a retryable error; successful deletion propagates through Room Flow. Favorites, Watchlist, preferences, and pending queue are outside this operation. Movies opened after clearing may appear again; confirmation copy states this.
- Per-item removal and playback-progress history were not added. No second history service, analytics, cloud history, dependency, migration, or database-version change.

## Migration and conditional filters

- Existing Room version 3 has `MIGRATION_1_2` and `MIGRATION_2_3`, registered through the database module; no destructive fallback. The v2->v3 migration only creates `recently_viewed_movies`. Existing unit tests inspect SQL strings and version metadata; they alone do not establish preserved data in SQLite.
- Added real SQLite/Room tests: build a historical v1 fixture with saved rows, run the actual 1->2 migration to a v2 file, then open with Room's 2->3 upgrade and schema validation. Check Favorites, Watchlist, Preferences, and pending action ID survive. Also exercise clear isolation, history persistence after closing/reopening, recency/20-item trimming, and transactional rollback if trim fails.
- Genre Explorer supports **one selected genre**, combined year bounds and rating, server-side popularity-ordered pages up to 500, and **local sorting of loaded movies**. Contrary to Saanvi's report, it does not support multi-genre UI selection or server-side rating/release sorting. Changed the UI label to “Sort loaded movies”. No new advanced filters.
- Added a generation guard so obsolete filter/page responses cannot append movies or replace error state after filters change. Tests cover combined filters, clearing existing fields back to an unfiltered first page, and deliberately late cancelled responses. Existing “All genres” clears only genre, not year/rating; no new reset button is implied.

## Changed files and microcommits

Paths are under `app/src/` unless stated otherwise.

- `main/.../core/model/UserPreferenceProfile.kt`; `core/domain/recommendation/{DeterministicRecommendationEngine,GetPersonalizedRecommendationsUseCase,RecommendationInsightProvider}.kt`; `feature/quiz/presentation/{QuizUiState,QuizResultScreen,components/QuizStepNuance}.kt`: eligibility and explanation consistency.
- `main/.../core/data/repository/impl/RecentlyViewedRepositoryImpl.kt`; `core/database/dao/RecentlyViewedDao.kt`: safe recency and bounded reads.
- `main/.../feature/discover/presentation/{HomeUiState,HomeViewModel,DiscoverScreen}.kt`: clear history control and storage state.
- `main/.../feature/genres/presentation/{GenreExplorerViewModel,GenreExplorerScreen}.kt`: stale-response guard and accurate sort label.
- JVM tests: `RecommendationEngineTest`, `RecommendationInsightProviderTest`, `QuizRepositoryTest`, `RecentlyViewedRepositoryTest`, `HomeViewModelTest`, `GenreExplorerViewModelTest`.
- Instrumented tests: new `HistoryPersistenceTest`, `HistoryControlsUiTest`.
- `docs/handoffs/SHRADDHA_PROMPT_04_ENHANCEMENT_INTEGRITY.md`: this handoff. Microcommits so far: `f712848` recommendation policy/explanations; `b68aaa3` filter response integrity; `7f2be3a` history ordering/persistence coverage; `09b5ab6` confirmed local history clearing. This report is committed separately after validation.

## Validation evidence

PowerShell Gradle environment: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'`. Every Gradle command used `--console=plain --max-workers=2`.

### TESTED-FAILED (diagnosed)

- Baseline `.\gradlew.bat testDebugUnitTest --tests '*RecommendationEngineTest'`: **19 tests, 3 failures**. Direct below-threshold candidate was retained; nonfinite ratings caused scoring failure; legacy low threshold bypassed 6.5. These established actual defects.
- First editing script stopped at a Windows text-encoding error after writing domain fixes; remaining edits were applied explicitly as UTF-8. No files were discarded.
- Initial recommendation/quiz pass ran **51 tests, 2 failures**: old default-profile assertion expected 6.0 rather than the newly agreed 6.5; malformed-year explanation test exposed that the provider edit had not yet reached that compiled output. Updated the default-policy assertion and completed the provider edit.
- Initial history/control focused pass failed test compilation because the new `runCurrent` extension was called with a package qualifier. Corrected the import/invocation; no production workaround or suppressed test.

### TESTED-PASSED

- `.\gradlew.bat testDebugUnitTest --tests '*Recommendation*Test' --tests '*Quiz*Test' --tests '*RecentlyViewedRepositoryTest' --tests '*HomeViewModelTest' --tests '*GenreExplorerViewModelTest' assembleDebug assembleDebugAndroidTest`: **75 tests / 8 suites, 0 failures/errors/skips**, both APKs built; BUILD SUCCESSFUL in 1m 25s.
- Full JVM result files from `.\gradlew.bat testDebugUnitTest assembleDebug lintDebug connectedDebugAndroidTest`: **374 tests / 40 suites, 0 failures/errors/skips**. Emulator result XML confirms **13 tests, 0 failures/errors/skips**, and instrumentation exit code **0** on Medium_Phone_API_37.0 / Android 17. This includes both new real Room tests and both new Compose history-control tests, plus prior Room rollback/queue and UX tests. Final command: **BUILD SUCCESSFUL in 5m 16s**, exit 0; 97 tasks (10 executed, 87 up-to-date). Lint: **0 errors, 46 warnings**.
- `python tools/package_submission.py --scan-only`: **259 files, PASS**, no detected credentials; local.properties ignored/untracked.
- `git diff --check`: passed.

### NOT-TESTED / RUNTIME-PENDING / BLOCKED

- **NOT-TESTED:** live TMDB metadata quality, live recommendation service latency, physical-device history UI behavior, process termination during the migration (close/reopen persistence is a different test), full Navigation Compose traversal from history to a live details response, and device performance profiling.
- **RUNTIME-PENDING:** live Firebase/account deletion outcomes from Prompt 1, campus Wi-Fi complete-film and promotional-trailer playback, Galaxy A13 playback/accessibility/performance, and multi-device behavior. Both professor-required in-app media routes remain unchanged; no physical or live playback claim.
- **BLOCKED:** no outstanding architectural or dependency blocker identified in this pass. The remote pre-push check was temporarily prevented by an automatic approval-review usage limit; it succeeded after the user requested continuation (remote divergence: 0). Runtime acceptance is not established by builds or fake-backed tests.

## Prompt 5 / Ritunjay acceptance steps

1. Save a quiz threshold below 6.5 from a legacy profile, then test 6.49/6.5 candidates and a higher threshold. Recommendations must honor the floor and higher threshold, without silently rewriting the stored legacy profile. Read the quiz explanation of the floor.
2. Compare explanation chips to actual genre/mood/era/language/runtime metadata. Unknown metadata may receive neutral points, but must not claim a confirmed match. Quiz results must disclose unknown year/runtime. Verify empty preferences/candidates, API error/retry, and profile changes.
3. Open two movie-details screens, revisit the first, restart the app, and confirm one row per ID with the revisit first. Open an unavailable movie and verify details error/retry without a new history entry. Verify history remains local and available when remote discovery fails.
4. Use Clear history: cancel first, then confirm; verify the shelf disappears and Favorites/Watchlist remain. Inject a storage failure and retry. Understand that opening movie details afterwards records a new entry. Device-global history scope remains intentional and disclosed.
5. Combine existing genre/year/rating filters, clear fields, and change filters during a slow response. Only current-query pages may append. Sort applies to loaded movies, not the entire server catalogue.
6. Retain Prompt 1 deletion-journal limitations and Prompt 3 performance measurement boundaries. This report does not declare submission readiness or replace the final integration pass.

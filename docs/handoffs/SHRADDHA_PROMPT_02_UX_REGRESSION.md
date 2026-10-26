# Shraddha Prompt 02 — UX Regression and Accessibility

Date: 2026-10-27 (Asia/Calcutta). Scope: Prompt 2 only. Prompts 3–5 remain for the next deliveries.

## Baseline and evidence boundaries

- Same working copy: `C:/MAD/Mobile-Application-Development-Project`, `main`, baseline `438c5dd` (completed Prompt 1). Initial tree clean; fetch and `git pull --ff-only origin main` reported already up to date before this pass. The resumed turn contained only this pass's four pending Quiz files.
- Read `.gitrules`, README, `SAANVI_PROMPT_02_UX_POLISH.md`, and `SHRADDHA_PROMPT_01_RELIABILITY_REPAIR.md`; checked actual source, navigation, tests and diffs. No applicable AGENTS.md found. Historical handoffs are preserved.
- Changes use the configured identity `Shraddha Chandgothia <shrachandgothia@gmail.com>` in coherent Conventional Commit microcommits. No branches, history rewrites, dependency additions, migrations, Firebase schema/rule/configuration changes, or recommendation-weight changes.
- Ignored local Firebase configuration was available to Gradle. No configuration values or credentials are in this report or staged changes.
- **IMPLEMENTED:** recovery and interaction fixes below, preserving both in-app media workflows.
- **TESTED-PASSED:** focused JVM tests and builds listed below. Final aggregate results are recorded in the validation section.
- **TESTED-FAILED:** initial emulator execution/collection problems are recorded explicitly below; a successful Gradle invocation with zero tests is not a passing UI test run.
- **NOT-TESTED / RUNTIME-PENDING:** complete application navigation on device, TalkBack traversal, large-font/landscape coverage for every screen, live Firebase sync/deletion/reauthentication, campus Wi-Fi and Galaxy A13 playback.
- **BLOCKED:** no implementation blocker remains in this pass. Prompt 1's ambiguous remote Auth deletion remains a client-side knowledge limitation: keep the journal/data and seek administrator verification when the original account cannot be restored. This pass does not resolve that external condition.

## Saanvi claims checked against source

| Claim | Source finding and disposition |
| --- | --- |
| Search cancels stale queries and clears focus before navigation | Present: sequence is named `currentQuerySequence`; clear cancels work and invalidates sequence. IME submit hides keyboard, result taps clear focus. Kept that behavior. Added a persistent field label and bounded the long results heading. Performance/load behavior belongs to Prompt 3. |
| Discover has independent loading/error/empty shelves | Present. However the full-screen remote error hid local Recently Viewed; corrected that condition while retaining shelf-specific retry. |
| Details has local toggles, missing metadata fallback and provider clarification | Present. Favorites/Watchlist observe Room and keyed repository actions prevent overlapping same-item writes. No claim of cloud completion on local toggle. Found cast/video failures with no retry; added scoped retries without reloading the successful detail card. |
| Library empty states offer Discover CTA and removal targets are 48dp | CTA was absent; wrappers were actually 44dp. Added callback wiring through unified Library and standalone lists, plus 48dp controls. Existing movie-specific remove descriptions retained. |
| Quiz has polished confirmation and recovery | Confirmation exists, but save failure used the restore-error state and Retry could reload older saved preferences over edited answers. Save errors now stay in the confirmation dialog with explicit Retry Save/Review. |
| Compare header targets are 48dp | Replace/remove were actually 44dp; corrected. Existing capped three-movie selection, replacement, duplicate rejection, per-entry error retry and clear-search behavior retained. |
| Profile recent-auth button safely completes recovery | It only navigated to Auth despite the new Prompt 1 instruction to sign out/sign in. It now signs out before navigating, and labels that operation explicitly. Live reauthentication is still unverified. |
| Profile loading/deletion controls are safe | `isLoading` was never consumed by Profile UI and an Auth callback cleared loading while deletion remained active. Added deletion-owned busy state, guarded conflicting actions and visible progress. |
| Player back label identifies trailers | Current shared player uses neutral `Navigate back`, appropriate for both entry paths. No player rewrite needed. Existing catalogue format labels are more precise than the older handoff's generic full-movie label. |

## Flows inspected and behavior retained

These are source traces unless an executed test is explicitly listed; they are not claims that every screen was rendered.

- **Startup/Auth/Profile:** MainActivity → MADVerseTheme → AppShell → Discover. Prompt 1 startup session injection remains. Profile observes Auth, local counts/preferences and sync state. Auth form validation and guest-data merge path remain. Sign-in completion now uses `launchSingleTop` after removing Auth, preventing a second Profile on the existing Profile stack. Repeated Profile links use single-top navigation.
- **Discover/details:** independent shelf skeletons, errors, retries, missing poster handling and movie-ID navigation inspected. History remains available when all remote shelves fail. Detail argument validation and mismatched response protection retained. Loaded details stay visible while only cast/videos retry.
- **Search:** query/debounce/result/clear/retry and active-query headings traced. A typed long query can no longer consume the results-count column. Search field has `Movie title` label; decorative search icon is silent.
- **Library:** unified Watchlist/Favorites tabs, saved sort choice, lazy rows, local removal, contextual removal descriptions, action-error dialog and error retry traced. All three entry routes now provide Explore Movies → Discover using pop-to-Discover/single-top.
- **Quiz/recommendations:** initial preference restore, six steps, previous/next/finish, confirmation, save success/error/retry and recommendation navigation traced. During restore/save, step navigation is disabled; system Back returns to the previous question when applicable. On successful save, Quiz is removed from the stack before Recommendations. Existing Take Quiz/Update Preferences paths remain. Recommendation empty/error/loading, local selection/busy states and explanation accordion inspected; scoring unchanged.
- **Genre/Compare:** genre selector, year validation, applied filters, pagination/error retry, local sort, and two/three-column comparison inspected. Comparison search cancels stale work; replacement/removal preserve selected-ID contract and reject duplicates. Genre and recommendation state engines were not changed because this review found no UI defect requiring a rewrite.
- **Theme/components:** retained existing dark Cinematic Obsidian Material 3 palette/typography. Reviewed shared error/empty components and movie-card fallbacks. No contrast certification is claimed. Shared empty states now scroll when short viewports cannot fit the explanatory text and action.

## Implemented changes and files

Paths below are relative to the repository; production Kotlin paths start at `app/src/main/java/com/example/madverse/`.

| Files / symbols | Before → after |
| --- | --- |
| `feature/quiz/presentation/QuizUiState.kt`, `QuizViewModel.kt`, `QuizScreen.kt` | Save failures mixed with restore failures → dedicated `saveErrorMessage`, preserved answers/step, Retry Save and Review; save/restore guards and step-aware system Back. Dialog body scrolls and error is a polite live region. Summary values wrap within remaining row width. |
| `feature/profile/presentation/ProfileViewModel.kt`, `ProfileScreen.kt` | Auth callbacks could clear deletion busy state → `isDeletingAccount` survives those updates; conflicting sign-out/edit/verification/manual sync/dialog actions are guarded. UI consumes loading state, shows progress, disables unsafe controls. Failure/cancellation releases busy state. Unrelated navigation remains available. |
| Same Profile files | “Delete Everything” and guaranteed synchronization copy → scoped “Delete Account” and truthful pending-sync wording. Recent-auth recovery signs out before Auth navigation. Fixed one pre-existing non-UTF-8 apostrophe in deletion text. Name/edit layout now reserves the edit button's space; edit/banner/verification targets at least 48dp. |
| `feature/library/presentation/{LibraryScreen,FavoritesScreen,WatchlistScreen}.kt`, `core/navigation/MADVerseNavHost.kt` | Empty dead ends → Explore Movies callback wired to Discover. Remove controls 44→48dp. Sign-in returns to existing Profile; repeated fixed-destination Profile links avoid duplicates. Media routes/arguments unchanged. |
| `feature/details/presentation/MovieDetailsViewModel.kt`, `MovieDetailsScreen.kt` | Unrecoverable cast/video error text → SectionErrorCard with `retryCast`/`retryVideos`; loading resets only the affected section and thrown repository failures become retryable UI errors. Successful metadata and library selection stay intact. |
| `feature/discover/presentation/{HomeUiState,DiscoverScreen}.kt` | All remote errors replaced the entire screen → `showFullScreenError` only when there is no local viewing history to show. No history storage/API change. |
| `feature/search/presentation/SearchScreen.kt` | Placeholder-only label and unconstrained heading → persistent label, silent decorative icon, weighted two-line heading with ellipsis. |
| `feature/comparison/presentation/ComparisonScreen.kt`, `core/designsystem/components/MADVerseEmptyState.kt` | 44dp header controls and clipped compact empty states → 48dp controls and scrollable empty content. |
| `app/src/test/java/com/example/madverse/{QuizViewModelTest,ProfileViewModelTest,MovieDetailsViewModelTest,HomeViewModelTest}.kt` | Tests for preserved edited answers across save retry/review, deletion held in flight across Auth changes, section retry without full reload or lost Favorite selection, and history retained during remote failures. |
| `app/src/androidTest/java/com/example/madverse/UxRecoveryUiTest.kt` | Compose checks for both empty-list actions, save-error Retry/Review, disabled duplicate save, and a 320×280dp compact empty state with scroll-to-action. Screenshots stored only in ignored build output. |

## Media contract verification

- `Screen.FreeMoviePlayer`: `free_movie/{catalogueId}`, validated catalogue slug. NavHost passes that ID to the player destination. YouTubePlayerViewModel resolves the catalogue entry, validates source/authorization, retains feature/short-film labels and rejects unsupported entries. Unknown catalogue ID has a Free Movies recovery message. This remains the complete-film workflow.
- `Screen.YouTubePlayer`: `youtube_player/{videoId}?movieTitle=...&videoType=...`. Movie Details sends validated promotional video ID/title/category. Navigation encodes the title once; player does not decode again. Only allowed promotional categories embed through this path; complete-film playback is not routed into it.
- Both destinations still use the in-app embedded player with their separate argument contracts. Read loading, 30-second foreground initialization timeout, attempt-scoped callbacks, retryability, unsupported-source messages, back action and optional external fallback. External fallback is not a replacement for in-app playback.
- No player, catalogue, source validation or route-construction code changed. Live embeddability, actual full-film duration and physical playback remain **RUNTIME-PENDING**.

## Validation commands and results

Gradle commands used PowerShell with `$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'`, `--console=plain --max-workers=2`. No credential contents were printed. Gradle emits an existing Kotlin/JDK-25 target fallback warning; the project compiles with its configured target.

| Command (from repository root) | Result |
| --- | --- |
| `./gradlew.bat testDebugUnitTest --tests '*QuizViewModelTest' assembleDebug` | Successful; 15 tests, zero failures/errors/skips in XML; resumed invocation reported tasks up to date for the already-built sources. |
| `./gradlew.bat testDebugUnitTest --tests '*ProfileViewModelTest' assembleDebug` | Successful; 13 tests, zero failures/errors/skips. |
| `./gradlew.bat testDebugUnitTest --tests '*LibraryViewModelsTest' assembleDebug connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.madverse.UxRecoveryUiTest'` | JVM/build successful, 18 library tests. Initial connected run produced **zero tests** in XML; not counted as UI pass. |
| `./gradlew.bat testDebugUnitTest --tests '*MovieDetailsViewModelTest' assembleDebug` | Successful; 20 tests, zero failures/errors/skips. |
| `./gradlew.bat testDebugUnitTest --tests '*HomeViewModelTest' --tests '*SearchViewModelTest' --tests '*ComparisonViewModelTest' assembleDebug assembleDebugAndroidTest` | Successful; 11 Home + 10 Search + 13 Compare = 34 tests, zero failures/errors/skips. |
| Final `./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` | Successful: 353 tests / 39 suites; zero failures/errors/skips. Debug APK and instrumented-test APK built. Lint: zero errors, 46 warnings (same count as Prompt 1). |
| Final `./gradlew.bat connectedDebugAndroidTest` | Successful: 8 tests, zero failures/errors/skips. Includes 5 UX tests, Room rollback for all six repository mutations, provider click forwarding, and application-context test. XML independently inspected; this supersedes the initial zero-test attempt. |
| `python tools/package_submission.py --scan-only` | Final source scan passed: 253 files (including this handoff), no detected credentials; local.properties ignored/untracked. |
| `git diff 438c5dd --check` and staged diff checks | Passed. Only intended source/test/handoff files staged; no generated artifacts. |

### Emulator execution history and visual evidence

- The initial sandbox adb server failed to start. An elevated inventory worked: no connected device, existing `Medium_Phone_API_37.0` AVD. Started that AVD headlessly; serial `emulator-5554`, Android 17/API 37. No physical device used.
- Initial Gradle connected result was zero tests. Direct `adb install -r` / `am instrument` subsequently reported `Process crashed`; a bounded non-streaming install (`adb install --no-streaming -r`) succeeded for both APKs. A direct runner wrapper timed out at 55 seconds; the runner log later explicitly recorded **4 tests, 0 failed, 0 ignored**. The timeout wrapper also had a text/bytes diagnostic error; neither event is presented as a clean command pass.
- Retrieved initial screenshots using `adb exec-out run-as com.example.madverse cat cache/ux-prompt2/<name>.png`. Quiz save-error dialog rendered the error and both actions legibly. Initial isolated empty-state captures had a white test-host background; production list containers already set the dark background. Corrected the test harness to use the app's Material Surface before the final run; no palette change was needed.
- Final direct screenshot-case run returned `OK (3 tests)` for failed-save Retry/Review and both empty-library actions. The complete updated suite also passed the 320×280dp scroll-to-action test. Initial screenshots were inspected at a 1080×2400 emulator viewport; final dark-Surface screenshots were not retrieved because automatic approval review hit a usage limit before the retrieval action executed. Final screenshot visual review remains NOT-TESTED; interaction assertions passed. Local initial captures are in ignored `app/build/reports/ux-prompt2/`.
- Tests use synthetic local UI state, not live user accounts or network movies. This is a limited interaction/rendering check, not full accessibility, navigation, Firebase, or playback acceptance.

## Residual UX risks and next-pass reproduction checklist

1. **RUNTIME-PENDING — account flow:** on a test Firebase account, queue an offline change, reconnect, sign out/in and verify local vs cloud messages. Start deletion with a recent-login failure; confirm progress/disabled controls, truthful failure, journal retention and same-account retry. Never infer deletion from a null session. Administrator verification remains necessary for unresolved ambiguous Auth outcomes.
2. **RUNTIME-PENDING — navigation:** exercise Profile → Auth → Profile then Back (one Profile), empty standalone/unified lists → Discover, Quiz Back/Review/Retry → Recommendations → Update Preferences, and two/three-movie Compare replacement/removal. No end-to-end NavController instrumentation is claimed by the component click tests.
3. **RUNTIME-PENDING — accessibility:** use TalkBack on Search labels, removal controls, Quiz error live region, Profile progress and playback controls; check action order and selected/disabled announcements. Test 200% font, landscape, long display names/movie titles, missing metadata/posters and long lists on Galaxy A13. Source target sizes do not establish full accessibility compliance.
4. **RUNTIME-PENDING — media:** independently test a catalogue complete film and a details trailer/teaser, initialization failure/retry/back, rotation/background return, regional restrictions, campus Wi-Fi and Galaxy A13. Record actual title, source, duration and outcome without claiming all catalogue entries embed.
5. **Prompt 3:** verify duplicate work, cancellation and lifecycle performance independently; review history recording on repeated detail emissions and search/genre behavior under slow responses. This pass made no performance benchmark claim.
6. **Prompt 4:** audit recommendation/history integrity separately. The Discover fix changes visibility only; shared-device history remains outside account-deletion coverage described by Prompt 1.
7. **Ritunjay final pass:** use this report and the Prompt 1 deletion/queue limits; run integrated acceptance after Prompts 3–5. Passing builds and these local UI tests do not make the app submission-ready.

## Microcommit ledger

- `0f46f2c` — preserve Quiz answers when saving fails.
- `0503be7` — keep Profile account controls safe during deletion.
- `ece0c65` — connect empty libraries to Discover and reuse Profile navigation.
- `a164817` — retry cast/trailer sections independently.
- `4660d2c` — keep local viewing history visible during discovery failure.
- `c6f1dc0` — compact layouts and accessible action targets.
- `00937a5` — five emulator-verified Quiz/library interaction tests.
- This handoff is committed separately without rewriting existing history.

# MADVerse comprehensive source pass — 22 October 2026

Contributor: Shraddha Chandgothia <shrachandgothia@gmail.com>. Baseline: `6c3f11b` on `main`.

This is the current A–I report and Saanvi handover. The [previous report at the baseline](https://github.com/AkkiSensei/Mobile-Application-Development-Project/blob/6c3f11b/docs/LOGIC_DEBUG_PASS.md) remains in Git history. Ritunjay's preceding movie-switch, repository-cancellation and Home-refresh repairs are preserved and were not counted as new work here.

## A. Executive summary

**PASS for the targeted source pass; device acceptance pending.** Ten grouped correctness findings were repaired: three P1 and seven P2, with no P0 identified and no known unresolved P0/P1 finding from this audit. Grouping counts related symptoms with one correction strategy together; it does not count each new assertion or performance change as a separate defect.

Comparison selection, concurrent request association, streaming badge callbacks, movie/region binding, quiz submission and library write recovery were repaired. Eight implementation microcommits and three topic-specific documentation microcommits precede this final report commit. All required automated gates passed. Forty additional JVM tests bring the total from 203 to 243; all original tests remain. The build also compiles a new provider-click instrumentation test, which was not executed.

No ADB, emulator, installation, live app launch, instrumentation execution or physical-device testing was attempted. No FPS, startup, memory or battery improvement is measured. Passing these tests does not establish correctness of every possible app interaction.

## B. Compare Movies repair

See [COMPARISON_REPAIR.md](COMPARISON_REPAIR.md) for navigation, files and display behavior.

The search-result card's callback opened Details instead of selecting its movie. It now selects the next column or the explicit replacement target. Already selected posters still open Details. The app shell opens an empty comparison; Recommendations can seed the first movie. The existing two/three-movie design, independent jobs and navigation routes are retained. No swap feature was introduced.

`ComparisonViewModel.search`, `select`, `replace`, `remove` and `loadDetails` now handle invalid IDs, self-selection feedback, unique results, failed/empty terminal flows, saved query/replacement target, mismatched detail IDs and removed/reselected request versions. Only the targeted column is replaced/retried. Overview and USD budget/revenue use actual metadata; unavailable values are labelled accordingly. `ComparisonScreen` remembers conversion of selected details and retains column identity.

All 13 `ComparisonViewModelTest` cases pass. They cover initial/no/invalid IDs, independent out-of-order requests, cancellation, same-ID reselection, mismatch/retry, search failure/retry, replacement/removal, persistence and duplicate selection. These are state and repository tests; UI taps, back gestures and real Android process recreation remain device checks. No confirmed comparison source defect remains from this pass.

## C. Streaming redirects

See [STREAMING_REDIRECTS.md](STREAMING_REDIRECTS.md) for URL policy and consulted primary sources.

The screen omitted the optional provider callback, leaving badges without a functioning destination action. It now supplies a required callback through `MovieDetailsContent → StreamingProvidersSection → WatchProviderRow → WatchProviderBadgeRow → WatchProviderBadge`. Stream/Rent/Buy preserve their category. Provider-error retry is connected to `fetchWatchProviders`.

`ProviderDestinationResolver` returns typed direct-content, provider-search, regional-fallback or unavailable results with the current movie/provider/category/year/region. Priority is a verified movie-bound link, then a verified official search route, then a valid supplied regional TMDB watch page, then visible unavailability. HTTPS, host, path, movie ID and region checks reject unrelated/unsafe URLs. Titles in a future reviewed search contract are UTF-8 encoded. Stable provider IDs precede exact normalized names.

**Neither a production direct-link registry nor a production provider-search registry is populated.** TMDB supplies regional availability and a TMDB watch page, not a full provider content link. Candidate Netflix/Prime public search URLs could not be sufficiently verified. Current production taps therefore use the valid regional TMDB/JustWatch fallback, or report unavailability. Fixture URLs test resolver mechanics and are not evidence of verified live routes. A loose `WatchProvider.directLink` and an unbound JustWatch slug are not trusted mappings.

`StreamingContract` opens a validated HTTPS ACTION_VIEW intent and reports launch exceptions; fallback messaging identifies regional watch options. Fourteen resolver tests and three dispatch tests pass. `StreamingProviderClickTest` compiles the section-to-badge path for all categories. Actual browser/app resolution, login and availability are unverified.

## D. Full-app bug sweep

Paths in the findings table are under `app/src/main/java/com/example/madverse/`; named tests are under `app/src/test/java/com/example/madverse/`, except the explicitly compiled-only Android UI test.

| ID / severity | File and symbol | Expected versus observed / root cause | Minimal correction and evidence |
|---|---|---|---|
| D1 / P2 | `feature/comparison/presentation/ComparisonScreen.kt`, result-card callback | Tapping a search result should select a column; callback navigated away | Wire card to `select`; selected posters retain Details navigation. Source trace and Android compilation; interaction pending device |
| D2 / P2 | `ComparisonViewModel.kt`, init/search/loadDetails | Invalid/empty operations should terminate; invalid restored IDs and empty/loading-only completed flows could produce no useful result or permanent loading | Validate before requests; explicit retryable terminal errors; comparison tests pass |
| D3 / P1 | `ComparisonViewModel.kt`, loadDetails/select/remove | Responses must belong to the selected movie/request; IDs were not checked and old same-ID requests lacked generation association | Independent generation tokens, cancellation and response-ID validation; concurrent/out-of-order/mismatch/reselection tests pass |
| D4 / P2 | `ComparisonViewModel.kt`, search/replace/persist | Search/replacement should recover and invalid/self selection should explain rejection; query/target were unsaved and invalid target could lose a selection | SavedStateHandle query/target, target validation, selection feedback; restoration and replacement tests pass |
| D5 / P1 | `MovieDetailsScreen.kt`, provider action; `StreamingContract.kt` | Tapped provider should open a defensible destination or explain failure; optional callback was omitted and legacy intent helper was disconnected | Required callback, pure validated resolver, visible fallback/failure; 17 JVM tests pass and UI callback test compiles |
| D6 / P1 | `MovieDetailsViewModel.kt`, setRegion/fetchWatchProviders; `WatchProvidersResponseDto.toDomain` | Availability/link must belong to displayed movie/region; old links survived until scheduled clear, stale request association was insufficient, mapper masked response ID | Synchronous clear, request version, matching IDs, valid region; Details/DTO tests pass |
| D7 / P2 | `WatchProvidersResponseDto.toDomain`, mapProviders | Provider lazy keys must be unique within category; duplicate IDs passed through | Deduplicate IDs per category, retaining category separation; DTO regression passes |
| D8 / P2 | `MovieDetailsViewModel.kt`, init/fetchDetails | Invalid entry should show an error and details must match movie; invalid initial argument stayed loading and mismatched detail payload was accepted | Route invalid IDs through error state; validate details ID and handle exceptions; Details regressions pass |
| D9 / P2 | `QuizViewModel.kt`, restoreExistingPreferences/submitQuiz/resetQuiz | Cancellation should propagate, reset should cancel restore and repeated submit should save/navigate once; generic catches and deferred guard allowed inconsistent state | Cancel restore, rethrow cancellation, synchronous save guard, capture answers at tap, release guard in finally; three additional quiz regressions pass |
| D10 / P2 | Library/Favorites/Watchlist ViewModels and Details toggles; `RepositoryActions.run` | Failed writes should be recoverable and repeated taps should not race; unguarded launch failures escaped and toggles trusted delayed UI membership | Shared keyed write guard and visible error dialog; current repository membership; real default one-shot membership via first(); action/library and retained toggle tests pass |

No confirmed finding in this table is left unfixed. Source review also covered these areas:

| Area | Inspection result / retained behavior | Evidence and remaining scope |
|---|---|---|
| Home/Discover | Separate feed jobs and refresh join from `6c3f11b` retained | Home tests pass; real network timing and gestures pending |
| Search | Existing cancellable Job, default 400 ms delay, query guard, blank reset and retry retained | Search tests pass; IME and device latency pending |
| Movie Details | Details/cast/trailers/library/provider orchestration reviewed; targeted changes above | Details tests pass; lifecycle and UI acceptance pending |
| Favorites/Watchlist/Library | Separate repositories/DAOs and collection membership retained; write errors now visible | Collection isolation, repository and ViewModel suites pass; real SQLite process survival pending |
| Quiz/preferences | Persisted profile remains source for recommendations; duplicate/cancellation repairs above | Quiz repository/VM tests pass; process recreation pending |
| Recommendations | Saved-profile consumption, deterministic ranking and explanation reasons retained | Engine/insight/VM suites pass; candidate pool/network behavior pending |
| Genre Explorer | Filter validation, reset/cancellation, page bound and dedup reviewed; loaded-list sorting explicitly labelled | Genre suite passes; sorting memoized, keyboard/scrolling pending |
| Free Movies | Trusted catalogue and classification retained; no authorization inferred from arbitrary titles/routes | Catalogue/validation tests pass; upload availability and playback pending |
| YouTube/player | Source validation, trusted full-movie routing, callback/lifecycle handling and WebView configuration reviewed | Player/validation tests pass; IFrame/WebView execution not tested |
| Navigation/app shell | Menu comparison entry, optional initial ID, registered destinations, encoded player arguments and Back actions traced | Source review and compile; navigation instrumentation only compiled |
| Repositories/network/DTOs | Existing HTTP/IO error mapping and cancellation preserved; DTO ID/dedup/image fixes above | Repository/mapping/logging suites pass; no live credentials tested |
| Room/DI/startup | Room schema and singleton bindings inspected; lightweight Activity retained, Coil configured lazily | Debug/release compilation; Room/DI runtime release behavior pending |
| Shared Compose components | Lifecycle state collection, stable keys, fallback slots, callback propagation and image sites inspected | Existing JVM checks and UI test compilation; visual/accessibility acceptance pending |

Corrections to earlier audit prose: the actual database is version **1**, with an empty migration registry; no version-2 migration was implemented. Network logging is **NONE**, not BASIC. Search uses a Job with a 400 ms delay, not a 300 ms flatMapLatest chain. Retrofit suspend calls are asynchronous, but DTO work in repository flows is not universally moved to Dispatchers.IO. Recommendation tie-breaking includes popularity before movie ID. No runtime or instrumentation execution is inferred from JVM suites or source inspection. No claim of universally correct application behavior is made.

## E. Low-end source optimization

See [LOW_END_PERFORMANCE.md](LOW_END_PERFORMANCE.md) for rationale, bounds, invalidation, tradeoffs and profiling checks.

- Small provider logos use w154 and cast profiles w185 instead of w500. Posters remain w500 and backdrops w780; unusually dense display sharpness needs review.
- Nine explicit per-composition ImageRequest builders become URL models, retaining global crossfade. Five image sites without custom slots use AsyncImage instead of subcomposition. Existing custom placeholders/fallbacks remain.
- Genre loaded-list sorting is remembered by movie list and selected sort, avoiding repeats from unrelated filter-text composition.
- Coil's existing process singleton gets a lazy ImageLoaderFactory configuration: strong memory cache capped at min(32 MiB, heap/8), disk cache 128 MiB in app cache storage. Standard URL-based cache keys, LRU and HTTP cache behavior remain; smaller caches may reload more often. API authentication is not attached to image requests.
- AGP release optimization is enabled with targeted network DTO reflection and JavaScript bridge rules. R8 completed; its merged configuration includes both custom rules, and mapping includes application/activity and Room database implementation. No dependency, SDK, signing, heap or broad animation change was made.

Tests and debug/release compilation validate source compatibility, not runtime cache efficiency, image quality or player reflection behavior. All hardware performance metrics remain pending.

## F. Automated validation

Baseline gates were run after the safe fast-forward from `62158e4` to `6c3f11b`, before edits. Baseline: 203 JVM tests / 24 suites, zero failures/errors/skips; debug build, lint and Android-test compilation passed; lint had 0 errors / 38 warnings; eight Python tests passed.

Final source revision: `227955b` (later changes are documentation only).

| Command / artifact | Observed final result |
|---|---|
| `.\gradlew.bat testDebugUnitTest --console=plain` | PASS; **243 tests in 27 suites, 0 failures, 0 errors, 0 skipped**; XML totals inspected |
| `.\gradlew.bat testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin --max-workers=1 --console=plain` | PASS, exit 0, BUILD SUCCESSFUL in 2m 2s; 70 tasks, 14 executed / 56 up-to-date; final unit task reused the passing current-source result |
| Debug artifact | `app/build/outputs/apk/debug/app-debug.apk` built; not installed or committed |
| Lint report | `app/build/reports/lint-results-debug.xml`: **0 errors, 37 warnings** |
| Android-test compile | PASS; compilation only, including new provider callback test; no instrumentation execution |
| `python -m unittest discover -s tools -p 'test_*.py' -v` | PASS, eight tests, no failures/errors |
| `.\gradlew.bat assembleRelease --console=plain` | PASS, exit 0, BUILD SUCCESSFUL in 3m 43s; R8 and resource optimization ran; unsigned release APK generated |
| `python tools/package_submission.py --scan-only` | PASS; 195 source files, no detected credentials; local.properties ignored/untracked |
| `git diff origin/main --check` and intended diff review | PASS; source/tests/docs/build rules only; no generated outputs or credential files |

Final lint categories: DefaultLocale 5, GradleDependency 5, InflateParams 1, ModifierParameter 6, NewerVersionAvailable 7, RedundantLabel 1, UnusedResources 7, UseKtx 5. The prior UseKtx count was 6; no new category was introduced. Existing dependency/template suggestions were not grounds for unrelated upgrades or redesign.

Environment warnings: Kotlin's Gradle configuration reported unsupported JDK target 25 with fallback to 24; release native stripping reported libandroidx.graphics.path.so packaged as-is. Both builds completed successfully. Initial sandbox attempts could not write Git metadata/Gradle cache; authorized escalated retries succeeded. No required command remains blocked. No release device verification or signing/deployment occurred.

## G. Git microcommits

The configured contributor identity was verified and left unchanged: **Shraddha Chandgothia <shrachandgothia@gmail.com>**, previously verified GitHub account `shraddhachand`. No teammate identity was used. Existing history, remote and branch were preserved; no branch/PR, force push, reset, rebase or squash was used.

| SHA | Exact commit message | Scope |
|---|---|---|
| `e747999` | fix: repair comparison selection and independent request state | Comparison UI/VM and ten added JVM cases |
| `a7e59f7` | fix: validate movie and region bound streaming destinations | Pure resolver and fourteen tests |
| `dadb52e` | fix: connect streaming badges and report destination launch failures | Callback/intent/retry wiring, three dispatch tests, compiled UI test |
| `9402b6f` | fix: keep watch availability bound to the current movie and region | Details and DTO guards, six tests |
| `2d43db2` | fix: preserve quiz cancellation and prevent duplicate saves | Quiz cancellation/save lifecycle and three tests |
| `609952a` | fix: recover library writes and serialize repeated collection actions | Recoverable write actions, dialogs, membership defaults and four tests |
| `790fbd1` | perf: bound image caches and reduce image and list composition work | Coil/image sizes/request construction/sorting |
| `227955b` | perf: enable release shrinking with targeted reflection rules | AGP optimization and keep rules |
| `85e40c0` | docs: explain comparison repairs and regression coverage | Comparison handover |
| `840be0e` | docs: record streaming destination evidence and limits | Destination evidence and limitations |
| `a9b19ca` | docs: document bounded image caches and device profiling checks | Source optimization evidence |

This report is delivered by the following documentation commit; its SHA cannot be embedded in its own content. Use `git log 6c3f11b..HEAD --format="%h %s"` for the complete ledger including that commit.

At report preparation, branch is `main` with no uncommitted source changes, and normal push to the unchanged `origin` is the final step. Push outcome, final report SHA and exact local/remote agreement are reported in the final chat after the push; this document does not pre-claim remote success.

## H. Saanvi handover

Review D1–D10 and the eight implementation commits independently, especially comparison request-version transitions and SavedStateHandle recovery; the region clear/response guard; trusted URL registry policy and fallback messaging; write guards with cancellation; and R8 reflection/JS rules. These remain the most important review surfaces, not newly confirmed unresolved defects. Source limits are the empty verified provider route registries, small-cache reload tradeoff, and lack of runtime acceptance. No known P0/P1 remains from this pass.

Use the commands in section F and inspect XML rather than assuming compilation executed UI tests. Preserve the existing main-only workflow and contribution history. Do not populate provider routes without evidence binding the correct content and country.

After Saanvi's review, the owner should run these acceptance checks on the Galaxy A13:

1. Open comparison with/without an initial movie; search/select two and three movies, replace/clear each column, reject duplicates, retry one failed request, switch quickly, open Details and return. Verify labels, values, horizontal scroll, font scaling and restoration after rotation/process death.
2. On Movie Details, switch movie and region rapidly with delayed/offline requests. Confirm no previous movie/region badges or URL can be used; error retry reloads only current availability.
3. Tap Stream, Rent and Buy badges and generic watch options. Verify the actual URL's movie and country, clear regional fallback messaging, browser/App Link handling and graceful launch failure. Do not expect provider-specific routes while registries remain empty.
4. Repeatedly toggle Favorites/Watchlist, remove and undo entries, induce a persistence failure where feasible, dismiss/retry, then restart. Confirm collection isolation and final stored membership.
5. Submit quiz rapidly; verify one save/navigation, retry failed save, reset during restore and recommendation updates from saved preferences. Check cancellation/background transitions.
6. Exercise Home refresh, rapid search, genre filters/paging/sorting and navigation Back behavior under slow/offline network conditions with a valid local TMDB configuration.
7. Run the compiled navigation, Room, design-system and provider-click instrumentation suites. Check TalkBack, large text, touch targets, image loading/error states and TMDB attribution visually.
8. Validate trailers and trusted full movies in the real YouTube/WebView player: play/pause/seek, errors, lifecycle release and source restrictions. No playback claim is established by this source pass.
9. Test a locally signed optimized release: Room creation/reopen, Retrofit/Gson mapping, Hilt startup and YouTube JavaScript callbacks. Retain R8 mapping locally for diagnosis.
10. Profile cold/warm launch, long scrolling, frame timing, peak/steady memory, network/image cache reloads and battery under repeatable conditions. Compare image sharpness and cache pressure; record actual values rather than extrapolating from source changes.

## I. Final status

**PASS — targeted source repairs and automated gates complete; independent review and device acceptance remain pending.** The Git push result is verified separately after committing this report. This status does not certify live streaming destinations, playback, process-death persistence or measured Galaxy A13 performance.

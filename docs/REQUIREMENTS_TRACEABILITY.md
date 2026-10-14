# Requirements traceability — 2026-10-14

| Requirement | Implementation / evidence | Status and limits |
|---|---|---|
| Player initialization | XML disables constructor auto-init; explicit 13.0.0 initialization; real package-derived origin; loading keeps the native view mounted | Source/build verified; device readiness PENDING |
| User-initiated playback | autoplay=0, cueVideo on ready, visible standard YouTube controls | Actual audiovisual playback PENDING |
| Source validation | DTO requires actual site/key; unknown types stay unknown; strict centralized URL/ID parser; details routes carry category | VideoSourceValidationTest, DtoMappingTest, YouTubePlayerViewModelTest PASS |
| Rights enforcement | Only local FreeMovieRepository IDs grant complete-film access; arbitrary IDs, titles and flags cannot | FreeMovieRepositoryTest and player ViewModel tests PASS |
| Lifecycle / stale callbacks | Per-attempt native instance, lifecycle forwarding, guarded callbacks, observer removal, idempotent release | State/attempt tests PASS; native lifecycle acceptance PENDING |
| Errors / fallback | Distinct unavailable, embedding, identity, HTML5 and initialization errors; 30-second foreground initialization timeout; controlled retry; safe external/share intents with visible failure | Mapping, retry, URL and failure-state tests PASS; Android intent/device cases PENDING |
| Free Movies | Four evidenced records: one feature, three shorts; menu/list/player; publisher and license information | Catalogue invariant tests PASS; full feature playback PENDING |
| Existing integrations | Existing Home/Search/Details/Library/Quiz/Genres/Recommendations/Comparison retained; legacy quiz-result route forwards to current recommendations; unused unregistered SeeAll route removed | Existing JVM suite PASS; UI/persistence smoke tests PENDING |
| Accurate ratings | Top-rated and recommendation language describes TMDB audience scores rather than critics | RecommendationEngineTest PASS |
| Credential protection | Authenticated request URL logging disabled; local.properties ignored; no keys added to source/docs | NetworkLoggingTest PASS; Git scan documented in final report |
| Build and quality | 180 tests, 23 suites, zero failures/errors/skips; assembleDebug PASS | Lint result recorded in final report; no device/instrumentation execution |

JVM repository tests use fakes where applicable; they do not prove real Room migration/storage behavior on Android. Existing deterministic recommendations score genre 30, mood 20, era 15, audience quality 15, language 10 and runtime 10, clamped to 0–100. They do not call Gemini or transmit TMDB/user data to an LLM. No critic-rating feed or general movie-streaming entitlement is implemented.

## Existing feature audit

| Feature / component | Automated/source evidence | Live acceptance |
|---|---|---|
| Discover feeds / HomeViewModel | HomeViewModelTest PASS | PENDING: live API feed and scroll |
| Search / SearchViewModel | SearchViewModelTest PASS | PENDING: debounce, typing, result navigation |
| Details / MovieDetailsViewModel | MovieDetailsViewModelTest, DtoMappingTest PASS | PENDING: metadata, cast, trailer controls |
| Room library / repositories and DAOs | FavoritesRepositoryTest, WatchlistRepositoryTest, LibraryViewModelsTest, CollectionIsolationTest, RoomConfigurationAndMigrationTest PASS | PENDING: actual Room migration and persistence after restart |
| Quiz / preferences | QuizViewModelTest, QuizRepositoryTest PASS | PENDING: complete/save/reopen on device |
| Recommendations | RecommendationEngineTest, RecommendationInsightProviderTest, RecommendationsViewModelTest PASS | PENDING: profile-to-screen demo |
| Genre Explorer | GenreExplorerViewModelTest PASS | PENDING: filter/pagination UI |
| Comparison | ComparisonViewModelTest PASS | PENDING: select, compare, remove, open details |
| Regional providers | MovieDetailsViewModelTest and DTO mapping PASS; existing region/provider source inspected | PENDING: India default, switch region, provider destination |
| API configuration | ArchitectureFoundationTest and NetworkLoggingTest PASS; ignored local configuration | PENDING: device API authentication with developer's own key |
| Navigation | Route unit tests PASS; NavHost registrations inspected | PENDING: all back stacks on Android |
| Attribution | Existing TmdbAttribution non-endorsement text retained; catalogue attributions added | PENDING: visible placement/accessibility on device; no automated legal compliance claim |

All live cells above are PENDING because no device was available, not because tests demonstrated runtime failure. Automated PASS applies only to the named checks.

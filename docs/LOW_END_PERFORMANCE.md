# Low-end source optimization — Galaxy A13 target

Date: 2026-10-22. No hardware performance measurements were made.

| Demonstrated source cost | Applied change | Rationale / tradeoff |
|---|---|---|
| 56 dp provider logos requested w500; 68 dp portraits also requested w500 | TMDB w154 logos and w185 profiles; blank paths omitted | Less source image data for these small views; visual sharpness at unusually high density remains a device check |
| Nine composable call sites rebuilt ImageRequest.Builder objects on every recomposition | Pass stable URL models; crossfade configured once on the shared loader | Removes caller-side builder allocation while retaining Coil constraint-based decoding and animation |
| Five image sites used SubcomposeAsyncImage without custom loading/error slots | AsyncImage in Details and historical QuizResult view | Avoid unnecessary subcomposition; custom card/logo loading/error slots remain unchanged |
| GenreExplorerState.sortedMovies re-sorted on every unrelated recomposition | remember keyed by movies and sort | Typing years/adjusting filters no longer repeats list sorting; new data/sort still invalidates |
| Coil's default percentage cache had no app-specific absolute cap | Configure the existing process singleton lazily through ImageLoaderFactory | Strong bitmap cache capped at min(32 MiB, max heap / 8), disk at 128 MiB; lower retention can cause extra reloads |
| Release optimization explicitly disabled under AGP 9.4.1 | Enable optimization with narrow DTO and JavaScript bridge rules | R8 removes unused code/resources; reflection/player behavior still requires release device acceptance |

Posters remain w500 because card dimensions/density can justify it. Backdrops remain w780. No original-size requests, bitmap copies, new loaders per composable, largeHeap, artificial heap increase or blanket animation removal were added. Existing state collection uses collectAsStateWithLifecycle. Existing stable movie/cast/video keys are retained; provider duplicates are removed before keyed rendering. MainActivity remains lightweight; Coil initializes lazily.

## Cache contract

Coil's singleton owns its normal memory/disk LRU caches. Keys derive from the full image URL and Coil's decode/transform metadata. URL size variants are distinct. Memory entries live only within the process and are evicted by LRU/trim events; weak references do not hold bitmaps alive. The disk cache lives in the app cache directory, is bounded to 128 MiB, respects Coil's standard HTTP cache headers, and can be evicted by Android or cleared with app cache/data. Changed image URLs invalidate the key. No persistent movie-data cache or stale provider-availability cache was introduced. The API-authenticated OkHttp client is deliberately not reused for CDN requests.

## Release and validation

AGP 9.4.1 uses `release.optimization.enable = true` to enable code and resource shrinking with default Android rules. Project keep rules protect only reflective network DTOs and annotated YouTube bridge methods. Retrofit/Hilt/Room/Coil consumer rules remain enabled. No dependency, SDK or signing changes were made. The release artifact is unsigned and must not be treated as deployed.

[Android's optimization guide](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization) documents the AGP 9.3+ DSL. [Coil 2.7 ImageLoaderFactory](https://github.com/coil-kt/coil/blob/2.7.0/coil-singleton/src/main/java/coil/ImageLoaderFactory.kt) configures the singleton. See LOGIC_DEBUG_PASS.md for exact final test/build/lint results, including release outcome.

## Pending Galaxy A13 profiling

After Saanvi's source review, the owner should compare cold/warm launch, long poster/cast/provider scrolling, image sharpness and fallback states, cache eviction/reloads, repeated search/filter/region changes, background/foreground, process recreation, and release-mode Room/Retrofit/YouTube behavior. Capture frame timing, peak/steady memory, startup latency and battery/network use under the same conditions before and after. None of FPS, startup time, memory savings or battery improvement is measured or claimed here.

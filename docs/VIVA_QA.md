# MADVerse — Viva Q&A (answers come from the code, not from the README)

> **Snapshot this was written against:** git `HEAD d0903b8` ("test(logic): add end-to-end user journeys suite and repair sync queue replay"), 124 commits, checked 2026-10-23. Working tree had one untracked file (`2000shra.md`). **No source file was changed.** I did **not** run Gradle or any test, so every "tests pass" statement below is quoted from team docs, not re-verified.
>
> **Path shorthand:** `M/` = `app/src/main/java/com/example/madverse/`. `T/` = `app/src/test/java/com/example/madverse/`.
>
> **Rule I followed:** if code, README and `docs/` disagree, the code wins and I say so.

---

## 0. READ THIS FIRST — where your assumptions, the README and the code disagree

These are the traps. Learn them before the viva.

| # | What people assume (or docs say) | What the code actually does | Where |
|---|---|---|---|
| 1 | Player tracks Ready / Playing / **Paused** / Error | The ViewModel has booleans `isLoading`, `isReady`, `isPlaying` and an `errorMessage`. **Paused is not a separate state** — `isPlaying=false` covers paused, cued, buffering and ended. A sealed `YouTubePlayerState` (Idle/Ready/Playing/Paused/Error/Unavailable) exists but **nothing references it** (grep found only its own file). | `M/feature/player/presentation/YouTubePlayerUiState.kt`; `M/core/domain/streaming/YouTubePlayerContract.kt` |
| 2 | `YouTubeUrlSanitizer.buildEmbedUrl` builds the embed URL | `buildEmbedUrl` is **never called in production code**. Only a unit test calls it. The player uses the library's IFrame player, not a URL. | `M/core/domain/streaming/YouTubeUrlSanitizer.kt` L39; `T/YouTubePlayerViewModelTest.kt` L48-49 |
| 3 | Search has genre chips and year filter (ARCHITECTURE.md L32, L107) | `SearchViewModel` only does text search (400 ms debounce). **No genre/year filter.** The repository has an unused `releaseYear` parameter. Genre + year + rating filtering lives in **Genre Explorer**. | `M/feature/search/presentation/SearchViewModel.kt`; `M/feature/genres/presentation/GenreExplorerViewModel.kt` |
| 4 | `QuizResultScreen` shows the quiz result | `QuizResultScreen`/`QuizResultViewModel` exist but **no NavHost route uses them**. Route `quiz_result` is mapped to `RecommendationsScreen`. | `M/core/navigation/MADVerseNavHost.kt` L63-70 |
| 5 | Quiz questions come from `getQuizQuestions()` | `QuizRepositoryImpl.getQuizQuestions()` (4 questions) is **not used by the UI**. The UI uses the 6-step `QuestionStep` enum. Only a test calls it. | `M/core/data/repository/impl/QuizRepositoryImpl.kt` L87; `M/feature/quiz/presentation/QuizUiState.kt` L11-46 |
| 6 | Multi-genre quiz widens the pool | `with_genres` is comma-joined → TMDB treats comma as **AND**. More genres = **narrower** pool. | `MovieRepositoryImpl.discoverCandidateMovies` L183 |
| 7 | Runtime preference affects ranking | Candidates come from `/discover`, and `MovieDto.toDomain()` never sets `runtimeMinutes` → it is always `null` → the runtime factor **always gives full 10 points** in the real app. | `M/core/network/model/MovieDto.kt` L27-41; `DeterministicRecommendationEngine.scoreRuntime` L220 |
| 8 | Gemini AI assists recommendations (ARCHITECTURE.md L37, L307-316, L587) | **No code uses Gemini.** SDK dependency + `GEMINI_API_KEY` BuildConfig field exist, but nothing reads them. `AiRecommendationEnhancer` is an interface with **no implementation and no Hilt binding**. README and `local.properties.example` themselves say "reserved". | `app/build.gradle.kts` L22-24, 43, 99-100; `M/core/domain/recommendation/RecommendationEngine.kt` L33 |
| 9 | "23 test suites, 192 tests" (README L17) | Today there are **33 test classes** and **~289 `@Test` annotations** (I counted with a script; not executed). README is from before the Firebase commits. | `T/` |
| 10 | README: "`2000shra.md` is absent from this checkout" | `2000shra.md` **is present** in the repo root as an **untracked** file (`git status` shows `??`). | repo root |
| 11 | Docs describe the app as TMDB + Room only | Docs (README, DEMO_AND_VIVA) **do not mention Firebase auth / Firestore sync at all**. That code landed in `083db84` after the doc pass. | `M/core/data/repository/impl/Firebase*`, `firestore.rules` |
| 12 | RegionConfig says "fallback to US" | `FALLBACK_COUNTRY_CODE = "US"` is **defined but never used**. A country with no data just shows "No streaming options available in this region." `ApiConstants.DEFAULT_REGION` is also unused. | `M/core/common/config/RegionConfig.kt` L21 |
| 13 | CONTRIBUTION_EVIDENCE: 45 / 28 / 14 commits | Today's `git shortlog`: **49 Ritunjay Deo, 39 Saanvi Chandel, 36 Shraddha Chandgothia**. Doc is a baseline snapshot. | `docs/CONTRIBUTION_EVIDENCE.md` L7 |
| 14 | Fullscreen supported | **Not implemented.** `IFramePlayerOptions...fullscreen(0)`. README says so too. | `YouTubePlayerView.kt` L80 |
| 15 | Stale code comment | `MovieDetailsScreen.kt` L685 still says "Shraddha will integrate the embedded YouTube Player Core here" — it is already integrated. | `M/feature/details/presentation/MovieDetailsScreen.kt` L685 |

---

# SECTION 1 — YOUTUBE IN-APP PLAYBACK

## Q1.1 Walk me through what happens from tapping a trailer to seeing the player.

**(a) 30-second spoken answer**
"On the Details screen the ViewModel asks TMDB for the movie's videos. The repository keeps only YouTube trailers and teasers with a valid 11-character ID and puts official ones first. When the user taps one, the screen builds a route containing the video ID, a title and a category, and navigates to the player screen. A ViewModel reads those arguments from the saved state, validates the ID again, and the screen embeds the android-youtube-player library inside Compose. The player is only *cued*, so the user presses YouTube's own Play button."

**(b) Deeper technical answer — the full path**
1. `MovieDetailsViewModel.loadMovieDetails` → `fetchVideos(movieId)` collects `movieRepository.getMovieVideos(movieId)` and maps `Resource` → `UiState` (Empty message: "No official trailers or clips listed.").
2. `MovieRepositoryImpl.getMovieVideos` (L115-125): `tmdbApi.getMovieVideos` (GET `movie/{movie_id}/videos`) → `.mapNotNull { it.toDomain() }` → `.filter { it.isEmbeddableYouTubeTrailer }` → `.sortedByDescending { it.isOfficial }`. All inside `safeApiCall`.
3. `VideoDto.toDomain()` (`VideosResponseDto.kt` L19-32) drops items with blank key/site, and drops YouTube items whose key is not a valid 11-char ID.
4. `VideoTrailer.isEmbeddableYouTubeTrailer` (L60-62) = `isPlayableInApp` AND type is "Trailer" or "Teaser". `isPlayableInApp` (L54-58) = site is YouTube AND valid ID AND category in {TRAILER, TEASER, CLIP, FEATURETTE, BEHIND_THE_SCENES}.
5. `MovieDetailsScreen` (L181-194) `onOpenVideo`: if not `isPlayableInApp` → Toast; else if `onNavigateToPlayer != null` → `onNavigateToPlayer(video.key, "<movie title> - <video name>", video.category)`; else fallback `Intent.ACTION_VIEW` (only when no nav callback, i.e. not the normal path).
6. `MADVerseNavHost` L129-131 calls `Screen.YouTubePlayer.createRoute(videoId, title, category)`.
7. `Screen.YouTubePlayer.createRoute` (`Screen.kt` L41-46): `require(isValidVideoId(...))`, URL-encodes the title once (`+`→`%20`), appends `videoType=<enum name>`.
8. NavHost route (L135-157) declares args `videoId` (String), `movieTitle` (String, default "", nullable), `videoType` (String, default "OTHER") → `YouTubePlayerScreen`.
9. `YouTubePlayerViewModel` (Hilt, gets `SavedStateHandle` populated by Navigation) `init` (L24-54): no `catalogueId` → reads `videoType` with `runCatching { VideoCategory.valueOf(..) }.getOrDefault(OTHER)` → `setVideo(...)`.
10. `setVideo` (L57-73): validates ID, checks category is allowed, builds `YouTubePlayerUiState` (`isLoading = valid && allowed`, `attempt = previous + 1`, `errorMessage` if invalid/not allowed).
11. `YouTubePlayerScreen` (L151-161): `if (state.canEmbed) YouTubePlayerView(...)`. `canEmbed = isValid && !isRestricted && errorMessage == null` (`YouTubePlayerUiState.kt` L21).
12. `YouTubePlayerView` composable (`components/YouTubePlayerView.kt`) → `AndroidView` → XML-inflated library view → `initialize(...)` → on `onReady` calls `cueVideo(videoId, 0f)` and reports ready.

**(c) Code location:** files named in each step above.

**(d) Likely follow-up:** *"Where is the YouTube video ID validated? Is once enough?"*
Answer: Four places, on purpose (defence in depth): `VideoDto.toDomain` (data layer), `Screen.YouTubePlayer.createRoute` (route builder, throws), `YouTubePlayerViewModel.setVideo` (ViewModel), and `YouTubePlayerView` factory (L74). All use `YouTubeUrlSanitizer.isValidVideoId` = regex `[A-Za-z0-9_-]{11}` with a full match.

**(e) Do NOT claim:**
- That TMDB says a video is embeddable. `isEmbeddableYouTubeTrailer` is a **misleading name**: it only checks site + ID shape + type. YouTube decides embeddability at playback time (that is why error 101/150 handling exists).
- That the app plays "full movies" from TMDB. Full films only come from the curated catalogue (Q1.11).

---

## Q1.2 Which library plays the video, why, and how is it embedded in Compose?

**(a) 30-second spoken answer**
"We use the open-source `android-youtube-player` library, version 13.0.0. It wraps YouTube's official IFrame player inside a WebView, so we follow YouTube's rules instead of scraping or downloading video. The library is an old-style Android `View`, so we embed it in Compose using `AndroidView`, inflating a tiny XML layout. In that XML we turn automatic initialization off, so we can attach our own listener before the player starts."

**(b) Deeper technical answer**
- Dependency: `libs.youtube.player.core` = `com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0` (`gradle/libs.versions.toml`, `app/build.gradle.kts` L97).
- Layout `app/src/main/res/layout/view_youtube_player.xml`: root is the library's `YouTubePlayerView` with `app:enableAutomaticInitialization="false"` and `app:autoPlay="false"`.
- Composable `YouTubePlayerView` (L47-49): `LayoutInflater.from(context).inflate(R.layout.view_youtube_player, null, false) as NativePlayer` then `enableAutomaticInitialization = false` again in code, so the library constructor never auto-initialises. We then call `initialize(listener, true, options, videoId)` ourselves.
- Options (L80): `IFramePlayerOptions.Builder(context).controls(1).autoplay(0).fullscreen(0).build()` → YouTube's native controls visible, no autoplay, no fullscreen button.
- Sizing (L46): `fillMaxWidth().heightIn(min = 200.dp).aspectRatio(16f/9f)`.
- Callbacks are wrapped in `rememberUpdatedState` (L37-40) so the long-lived listener always calls the latest lambdas.

**(c) Code location:** `M/feature/player/presentation/components/YouTubePlayerView.kt`; `app/src/main/res/layout/view_youtube_player.xml`; `app/build.gradle.kts` L97.

**(d) Likely follow-up:** *"Why not just open YouTube in a browser/Intent?"*
Answer: The brief wanted in-app playback. We *also* keep that as a fallback (`Open in YouTube`, Q1.8). Embedding via the official IFrame API is permitted by YouTube; downloading or re-hosting is not, and the app does neither (README L13).

**(e) Do NOT claim:** that it plays offline, that it is "our own player", that we control ads/quality, or that it has been verified on a device (Q1.12).

---

## Q1.3 Why `cueVideo` and not `loadVideo`?

**(a) 30-second spoken answer**
"`loadVideo` starts playing right away. `cueVideo` only prepares the video and shows the thumbnail. We chose `cueVideo` so nothing autoplays and the user has to press YouTube's Play button. That avoids surprise audio, matches the `autoplay(0)` option and keeps the user in control."

**(b) Deeper technical answer**
In `AbstractYouTubePlayerListener.onReady` (L82-87): `youTubePlayer.cueVideo(videoId, 0f)` then `readyCallback()`. Options also set `autoplay(0)` and the XML has `autoPlay="false"`. The UI text matches: "Player ready. Tap the YouTube play control to begin." (`YouTubePlayerScreen.kt` L212). The `videoId` is also passed as the 4th argument of `initialize(...)` (L98); what the library does with that extra argument is library behaviour that I did not verify.

**(c) Code location:** `YouTubePlayerView.kt` L80, L85.

**(d) Likely follow-up:** *"Does `onReady` mean the video can actually play?"*
Answer: No. `onReady` only means the IFrame player object is initialised. A later `onError` (e.g. 101/150) can still fire when the user presses Play. That is why the doc table says "a ready callback alone does not pass".

**(e) Do NOT claim:** that "Ready" proves playback works.

---

## Q1.4 How are Ready/Playing/Error tracked? What is `attempt` and `key(videoId, attempt, owner)`?

**(a) 30-second spoken answer**
"The library calls our listener. Our listener forwards events to the ViewModel, which keeps one `UiState` with flags for loading, ready, playing and an error message. Every time we start a fresh player — first load or Retry — we bump a number called `attempt`. Each event carries its attempt number, and the ViewModel ignores events from an older attempt. In Compose, `key(videoId, attempt, owner)` forces a brand-new player when any of those change, so an old player's late callbacks can't change the new player's state."

**(b) Deeper technical answer**
- Listener → callbacks (`YouTubePlayerView.kt` L81-97): `onReady` → `readyCallback()`; `onError` → `errorCallback(PlayerFailure.from(error))`; `onStateChange` → `playingCallback(state == PlayerState.PLAYING)`.
- Screen wires them to the ViewModel with the captured attempt (`YouTubePlayerScreen.kt` L152-160): `viewModel.initializing(attempt)`, `onReady(attempt)`, `setPlaying(attempt, it)`, `fail(attempt, it)`.
- ViewModel (`YouTubePlayerViewModel.kt` L75-96): every transition goes through `updateAttempt(attempt) { ... }` which applies only `if (it.attempt == attempt && it.canEmbed)`. Stale or post-error events are dropped.
- `retry()` (L84-90): allowed only if `retryable && isValid && !isRestricted`; sets `attempt + 1`, `isLoading = true`, clears the error.
- `key(videoId, attempt, owner)` (L41): `owner` is `LocalLifecycleOwner.current`. A change in any of the three throws away the old `AndroidView` and builds a new one. A per-instance `active` boolean (L43) means a released instance ignores late callbacks.
- **Subtle fact:** after an error, `errorMessage != null` → `canEmbed == false` → the `if (state.canEmbed)` block in the screen removes the player view from composition (so the WebView is released). The user sees the error card + Retry/Open in YouTube, not a dead player.
- Tested on the JVM: `timeoutAndRetryCreateFreshAttemptAndIgnoreOldCallbacks`, `validVideoStartsInitializingAndOnlyCallbackMakesItReady` (`T/YouTubePlayerViewModelTest.kt`).

**(c) Code location:** `YouTubePlayerViewModel.kt` L75-96; `YouTubePlayerView.kt` L41-97; `YouTubePlayerScreen.kt` L152-160.

**(d) Likely follow-up:** *"Why not use `remember(videoId)` instead of `key`?"*
Answer: `remember` would keep the same `AndroidView` instance on retry; `key` guarantees a **new** native view and a new `active` flag per attempt, which is what we need to discard a failed/hung WebView. (This is the design reason in the code comment: "Each video/attempt/owner gets one player", L25.)

**(e) Do NOT claim:** "Paused" is tracked (it isn't), or that the sealed `YouTubePlayerState` drives the UI (it is unused).

---

## Q1.5 How is the player lifecycle handled? What prevents duplicate audio and leaks?

**(a) 30-second spoken answer**
"We add a lifecycle observer to the screen's lifecycle owner. On STOP we tell the ViewModel it is no longer playing, and we forward the event to the library so it can pause. On DESTROY we release the player. Release is guarded by a flag so calling it twice — from the observer and from Compose's `onRelease` — does nothing the second time. Because each attempt gets its own player and the old one is released, there should be only one player making sound."

**(b) Deeper technical answer** (`YouTubePlayerView.kt` L52-72, L104)
- `release[0]` lambda: `if (active[0]) { active[0]=false; owner.lifecycle.removeObserver(observer); nativePlayer.release(); trace("released") }` → **idempotent**.
- `observer = LifecycleEventObserver`: `ON_DESTROY` → `release[0]?.invoke()`; otherwise, if active → `nativePlayer.onStateChanged(source, event)` (the library's own lifecycle handling) and on `ON_STOP` → `playingCallback(false)`.
- `AndroidView(onRelease = { release[0]?.invoke() })` — Compose disposal path also releases.
- Callbacks check `if (!active[0]) return` so a released player never touches state.
- Timeout coroutine only runs while `STARTED` (Q1.6).

**(c) Code location:** `YouTubePlayerView.kt` L43-44, L52-72, L104.

**(d) Likely follow-up:** *"Where exactly is the video paused when the app goes to background?"*
Answer: The app code calls `nativePlayer.onStateChanged(source, event)` and sets `isPlaying=false` on `ON_STOP`. The actual pause is done inside the library's `onStateChanged`; I did not read the library source, so say "we delegate to the library's lifecycle handling" and that device background/foreground testing is pending (DEMO_AND_VIVA.md row "Background/foreground").

**(e) Do NOT claim:** "verified no duplicate audio" or "no memory leaks". The code is *designed* to prevent them; nothing was measured (no LeakCanary, no device run). Also I did not verify the exact order in which Compose releases the old `key` group vs. creates the new one.

---

## Q1.6 What is the 30-second initialization timeout and how does retry work?

**(a) 30-second spoken answer**
"If the player is still 'loading' after 30 seconds in the foreground, we mark it failed with a friendly message and show a Retry button. The timer only counts while the screen is visible, and it restarts if the user leaves and comes back. Retry bumps the attempt number and builds a fresh player."

**(b) Deeper technical answer** (`YouTubePlayerScreen.kt` L83-88)
```kotlin
LaunchedEffect(state.attempt, state.isLoading, owner) {
    if (state.isLoading) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        delay(30_000)
        viewModel.initializationTimedOut(state.attempt)
    }
}
```
- `initializationTimedOut(attempt)` (ViewModel L81-83): `if (isLoading) fail(attempt, PlayerFailure.INITIALIZATION)` → message "The player could not become ready. Check your connection and try again." and `retryable = true`.
- Retry → Q1.4. The effect re-keys on `attempt`, so the timer is cancelled/restarted per attempt.
- Unit test: `timeoutAndRetryCreateFreshAttemptAndIgnoreOldCallbacks`. That tests the **ViewModel logic**; the actual `delay(30_000)` in Compose is not unit-tested.

**(c) Code location:** `YouTubePlayerScreen.kt` L83-88; `YouTubePlayerViewModel.kt` L81-90; `PlayerFailure.kt` L11.

**(d) Likely follow-up:** *"What if the user backgrounds the app at second 29?"*
Answer: `repeatOnLifecycle(STARTED)` cancels the block on STOP and starts it again (with a fresh 30 s) on the next START, so background time doesn't count and the timer restarts.

**(e) Do NOT claim:** that 30 s is configurable (hard-coded) or that it was measured on a slow network.

---

## Q1.7 How do the error mappings work (100, 101/150, 153, 5)? What does the user see for a non-embeddable video?

**(a) 30-second spoken answer**
"YouTube's player reports numeric errors. We map each to one of our own `PlayerFailure` values, each with a plain-English message and a flag saying whether Retry makes sense. For example 101/150 means the owner disabled embedding: we show 'The owner does not allow embedded playback' with no Retry, but an 'Open in YouTube' button. 100 means removed/private. 153 means YouTube couldn't identify the app. 5 means an HTML5 player problem."

**(b) Deeper technical answer** (`PlayerFailure.kt`)
| Library enum | → `PlayerFailure` | Message summary | Retryable |
|---|---|---|---|
| `INVALID_PARAMETER_IN_REQUEST` | `INVALID_VIDEO` | ID missing/invalid | no |
| `VIDEO_NOT_FOUND` (100) | `UNAVAILABLE` | removed, private or unavailable (100) | no |
| `VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER` (101/150) | `EMBEDDING_DISABLED` | owner doesn't allow embedded playback; try YouTube | no |
| `REQUEST_MISSING_HTTP_REFERER` (153) | `CLIENT_IDENTITY` | YouTube could not identify this app (153) | yes |
| `HTML_5_PLAYER` (5) | `HTML5` | could not play in embedded player (5) | yes |
| `UNKNOWN` | `UNKNOWN` | unknown playback error | yes |
Also `INITIALIZATION` (timeout/exception) → retryable. The mapping is an exhaustive `when` (L15-22). Test: `errorClassesRemainDistinctAndEmbeddingFailureIsNotCopyrightClaim`.

What the user sees for a non-embeddable video: after `onError`, `fail()` sets `errorMessage`, so (see Q1.4) the player view is removed and a red-outlined card shows the message; **Retry** is hidden (retryable=false); **Open in YouTube** and **Share Video** remain (`YouTubePlayerScreen.kt` L233-247, L249, L271).

**(c) Code location:** `PlayerFailure.kt`; `YouTubePlayerViewModel.kt` L78-80; `YouTubePlayerScreen.kt` L233-320.

**(d) Likely follow-up:** *"Does the app work around embedding restrictions?"*
Answer: No — explicitly not. README L13: "does not … bypass embedding restrictions or spoof client identity". The code comment at `YouTubePlayerView.kt` L79 says the origin is derived from `context.packageName` and must not be spoofed.

**(e) Do NOT claim:** that error 101/150 has been reproduced on a device (DEMO_AND_VIVA.md row "Non-embeddable video" = PENDING). The numeric codes come from the library enum names + the message text in `PlayerFailure`; the app does not read the raw number itself.

---

## Q1.8 How does "Open in YouTube" work?

**(a) 30-second spoken answer**
"We first check the link is a safe HTTPS YouTube link with a valid ID. Then we try an Intent aimed specifically at the YouTube app. If that fails we try a normal Intent so any browser can handle it. If that also fails, we show a visible error message."

**(b) Deeper technical answer** (`YouTubePlayerScreen.kt` L90-105)
```kotlin
if (YouTubeUrlSanitizer.isPermittedUrl(url)) {
  try { startActivity(Intent(ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.youtube")) }
  catch (_: Exception) { try { startActivity(Intent(ACTION_VIEW, Uri.parse(url))) }
                         catch (_: Exception) { viewModel.externalFailed() } }
}
```
- `externalFailed()` sets `externalError = "No YouTube app or browser could open this link."`, shown under the buttons (L322-324). It does not change ready/loading state (test `failedExternalLaunchIsVisibleWithoutChangingReadiness`).
- `url = state.webWatchUrl = YouTubeUrlSanitizer.buildWebWatchUrl(id)` = `https://www.youtube.com/watch?v=<id>` — only built from a validated ID; empty string otherwise (`YouTubeUrlSanitizer.kt` L44-46).
- `isPermittedUrl` requires the URL to start with `https://` **and** `extractVideoId(url) != null` (L48).
- If `isPermittedUrl` is false, the click does nothing silently (no error).
- Nuance (from reading `setVideo`/`init`): the button shows whenever `webWatchUrl` is non-blank, **including** for a valid-ID video that is "restricted" (e.g., not-allowed category). It never shows for invalid IDs or non-YouTube sites.
- There is also a **Share Video** button using `Intent.createChooser(ACTION_SEND)`.

**(c) Code location:** `YouTubePlayerScreen.kt` L90-105, L271-320; `YouTubePlayerViewModel.kt` L91-93.

**(d) Likely follow-up:** *"Why not use the `youtube://` scheme?"*
Answer: We use the HTTPS watch URL so the same link works with the YouTube app (via `setPackage`) or any browser; a custom scheme would fail without the app.

**(e) Do NOT claim:** that this was tested with/without the YouTube app installed (PENDING in DEMO_AND_VIVA.md). Do not claim this is "embedded success" — it leaves the app.

---

## Q1.9 What does `YouTubeUrlSanitizer` validate and why? Is `buildEmbedUrl` used?

**(a) 30-second spoken answer**
"It is a small safety gate for anything that looks like a YouTube link or ID. A valid ID is exactly 11 letters, digits, dash or underscore. For a full URL we parse it properly and only accept http/https, no username/password, no port and no fragment, and only a short list of YouTube hosts. That stops tricks like `evil.com/?youtube.com/watch?v=...`. Honest note: one helper, `buildEmbedUrl`, is not used by the app — only by a unit test."

**(b) Deeper technical answer** (`YouTubeUrlSanitizer.kt`)
- `idPattern = [A-Za-z0-9_-]{11}`, `isValidVideoId` uses `matches` (whole string).
- `watchHosts = {youtube.com, www.youtube.com, m.youtube.com}`; `embedHosts = watchHosts + {youtube-nocookie.com, www.youtube-nocookie.com}`.
- `extractVideoId`: trims; accepts a bare ID; else `URI(value)` — requires scheme http/https, `userInfo == null`, `port == -1`, `rawFragment == null`. Then by host/path: `youtu.be/<id>`; `watch` path with exactly one `v` param (`singleOrNull` — duplicate `v` params rejected); `/embed/<id>` on embed hosts; `/shorts/<id>` on watch hosts. Result must pass `isValidVideoId`. Everything wrapped in `runCatching` → null on any exception.
- Header comment: "Parses entire URLs, never trusted-looking substrings inside an untrusted URL".
- **`buildEmbedUrl` honesty:** grep across `app/src` finds it only in its definition (L39) and in `T/YouTubePlayerViewModelTest.kt` L48-49. **It is dead code in production.** Returns `https://www.youtube-nocookie.com/embed/<id>?enablejsapi=1&autoplay=0&rel=0`.
- `isPermittedUrl` (L48) is used by `YouTubePlayerScreen.openExternal`. `buildWebWatchUrl` used by `VideoTrailer.youtubeWatchUrl`, the player ViewModel.

**(c) Code location:** `M/core/domain/streaming/YouTubeUrlSanitizer.kt`; tests `hostileAndAmbiguousUrlsAreRejected`, `supportedUrlsResolveEntireId`, `fallbackAndEmbedUrlsNeverContainInvalidInputOrAutoplay`.

**(d) Likely follow-up:** *"Why check the host list if the URL comes from your own `buildWebWatchUrl`?"*
Answer: `isPermittedUrl` re-parses whatever string is in state right before launching an Intent — defence in depth in case a future code path sets a different URL.

**(e) Do NOT claim:** that `buildEmbedUrl` powers the player, or that the sanitizer makes the video itself "safe" — it only constrains IDs/URLs.

---

## Q1.10 Security of the embedded WebView — what is hardened and what isn't?

**(a) 30-second spoken answer**
"After the library creates its WebView, we walk the view tree and turn off file access and content-provider access and block pop-up windows opened by JavaScript. We left DOM storage on because YouTube's player needs it. We don't fake an origin: the library derives it from our package name. We also never log titles, URLs or keys from the player."

**(b) Deeper technical answer** (`YouTubePlayerView.kt` L114-126, L109-112, L79)
```kotlin
child.settings.apply { allowFileAccess=false; allowContentAccess=false
                       domStorageEnabled=true; javaScriptCanOpenWindowsAutomatically=false }
```
- Applied recursively to any `WebView` child found at that moment, before `initialize`.
- `trace()` logs only static event names and only when `BuildConfig.DEBUG` (L109-112).
- No `setWebViewClient` / URL allow-list / `setJavaScriptEnabled` calls in this app's code — JavaScript is whatever the library enables (the IFrame API requires JS). Don't claim JS is disabled.
- Network side: `HttpLoggingInterceptor.Level.NONE` so the TMDB key in the URL is never logged (`NetworkModule.kt` L22-27).
- Manifest has only `INTERNET` and `ACCESS_NETWORK_STATE` permissions.

**(c) Code location:** `YouTubePlayerView.kt` L79-80, L109-126.

**(d) Likely follow-up:** *"Is the WebView sandboxed from your app data?"*
Answer: We disable file/content access and window opening. We did not add a custom `WebViewClient` or URL filter, and no security test exercises the WebView. I would say "reasonable hardening, not a full audit".

**(e) Do NOT claim:** "origin spoofing is prevented by code" — rather, "we deliberately don't spoof it" (error 153 exists when YouTube can't identify the app). Don't claim a penetration test.

---

## Q1.11 Free Movies path — why a separate catalogue, shorts vs feature, and why titles can't grant permission?

**(a) 30-second spoken answer**
"TMDB trailers are *promotional* clips. Complete films need a rights check, so we keep a small, hand-curated catalogue in code: one feature (Star Wreck, CC BY-NC-ND) and three Blender short films (CC BY 3.0). Each record stores the publisher, license evidence and verification date. The route only carries a catalogue ID like `big-buck-bunny`. The ViewModel looks that ID up in the repository and gets the authorization from there. A movie title or a flag in a navigation argument can never turn on permission."

**(b) Deeper technical answer**
- `FreeMovieRepository` (`M/core/data/repository/FreeMovieRepository.kt`): `@Singleton`, hard-coded list of 4 `FreeMovie`s (L12-35). `validateCatalogue` (L51-61) enforces: unique catalogue IDs and video IDs, ID regex `[a-z0-9-]+`, valid 11-char video ID, non-blank publisher/attribution/verificationNotes, HTTPS source/rights URLs, `yyyy-mm-dd` verifiedOn.
- `FilmFormat { SHORT_FILM, FEATURE_FILM }`, `AuthorizationBasis { PUBLISHER_CC_BY_3, PUBLISHER_CC_BY_NC_ND, RIGHTSHOLDER_PERMISSION, UNKNOWN }` (`M/core/model/FreeMovie.kt` L3-4). `isAuthorized` false only for `UNKNOWN` (L24-28). `isEligibleForYouTube` = authorized AND site YouTube AND valid ID (L29-30).
- Route: `Screen.FreeMoviePlayer.createRoute(catalogueId)` does `require(matches [a-z0-9-]+)`; `NavHost` L77-79 shows `YouTubePlayerScreen`.
- `YouTubePlayerViewModel.init` (L25-47): `catalogue.find(id)`; null → "This catalogue film is unavailable". Feature flag = `film.format == FEATURE_FILM`; `isAuthorizedFullLength = feature && film.isAuthorized`; error if not YouTube / invalid / unauthorised.
- **Why shorts vs feature:** classification is about the *content's length*, separate from *permission*. A short is still eligible and playable but shows label "Complete short film" and neither full-length flag is set; only Star Wreck shows "Feature-length film" (`FilmFormat.label`). (docs/FREE_MOVIE_CATALOGUE.md L22).
- **Promo trailers vs catalogue films:** promo path (`videoType` argument; allowed categories TRAILER/TEASER/CLIP/FEATURETTE/BEHIND_THE_SCENES; `FULL_MOVIE` category is *not allowed* → "eligibility … has not been established. Use the verified Free Movies catalogue", `setVideo` L59-69). Catalogue path (`catalogueId` argument; rights from the repository).
- `VideoTrailer.isAuthorizedFullLength` is documented as "Legacy metadata only" (`VideoTrailer.kt` L28) and ignored for permission.
- Free Movies list screen: "Watch in App" is `enabled = film.isEligibleForYouTube` (`FreeMoviesScreen.kt` L254, L271).
- Tests: `titleNeverGrantsFullFilmRights`, `fullFilmBooleanInTmdbModelCannotGrantPlayback`, `injectedAuthorizationFlagsCannotAuthorizeAnUnknownCatalogueId`, `unknownCatalogueEntryHasNoFallbackToArbitraryVideo`, `authorizedFeatureRetainsFullLengthClassification`, plus one per short (`T/YouTubePlayerViewModelTest.kt`, `T/FreeMovieRepositoryTest.kt`).

**(c) Code location:** `FreeMovieRepository.kt`, `FreeMovie.kt`, `YouTubePlayerViewModel.kt` L24-54, `Screen.kt` L30-35.

**(d) Likely follow-up:** *"How do you know the publisher really has the right to license these? Is Star Wreck legal to show?"*
Answer: "Source and licence evidence is recorded per entry (`verificationNotes`, `rightsUrl`, `licenseUrl`, `verifiedOn = 2026-10-14`), but the project's own docs say the latest pass **could not re-fetch** the publisher pages (402 / fetch failure), so **0 of 4 chains were fully re-verified live**. Star Wreck's CC BY-NC-ND licence is non-commercial and no-derivatives; we present it unmodified for an academic demo only." (FREE_MOVIE_CATALOGUE.md L18-20, README L19).

**(e) Do NOT claim:** that rights are legally cleared or re-verified today, that a licence guarantees embedding or India availability (docs say "India availability remains PENDING for every entry"), or that the app can detect "full movie" from a title.

---

## Q1.12 Honesty note — what exactly can I claim as verified?

`docs/DEMO_AND_VIVA.md` L13-34 lists **every device check as PENDING** (initialisation, user-initiated playback, trailer from Details, pause/back, background/foreground, non-embeddable error, external fallback, Star Wreck full feature, every catalogue entry, etc.). README L17 states "device playback acceptance is PENDING" and "These results do not establish actual YouTube playback, Room persistence across process restarts, or device lifecycle behavior."

| You **CAN** say | Basis |
|---|---|
| "The video ID/URL validation rules work as coded." | Code + `YouTubePlayerViewModelTest` (JVM) |
| "The ViewModel ignores stale-attempt callbacks, times out, retries, and blocks unauthorised catalogue entries." | `YouTubePlayerViewModel` + JVM tests with fakes |
| "Failure types map to distinct user messages." | `PlayerFailure` + test |
| "Catalogue records carry source, licence and date, and are validated at start-up." | `FreeMovieRepository.validateCatalogue` |
| "The team's docs record a green `assembleDebug` and unit-test run on 2026-10-21." | README L17 — a **claim in docs**; I did not re-run |

| You **CANNOT** say | Why |
|---|---|
| "The player plays a trailer / film on a device." | PENDING; no test touches the `YouTubePlayerView` composable or a WebView |
| "Back button / Home button stops audio; no duplicate audio." | PENDING |
| "Error 101/150 / 153 / 5 were reproduced." | PENDING |
| "Open in YouTube works with and without the app." | PENDING |
| "Star Wreck plays end-to-end in India." | PENDING; docs only cite YouTube *metadata* `playableInEmbed=true` (a 2026-10-14 record) |
| "No memory leaks." | not measured |

**Safe sentence for the examiner:** *"The logic around the player is unit-tested and the lifecycle code is written defensively, but I have not yet completed the on-device acceptance checklist, so I can't claim verified playback."*

---

# SECTION 2 — QUIZ, "GRADING" AND RECOMMENDATIONS

## Q2.1 Is the quiz graded? Trace the whole pipeline.

**(a) 30-second spoken answer**
"There are no right or wrong answers, and nothing is graded. The quiz builds a *taste profile*. Six steps: genres, language, mood, era, runtime, minimum rating. We turn the answers into a profile, save it in Room, ask TMDB for candidate movies that fit the genre/era/rating, then score each candidate out of 100 with fixed weights and show the best ones with reasons."

**(b) Deeper technical answer**
1. `QuizScreen` → `MoviePreferenceQuizScreen` (`QuizScreen.kt` L72-117) shows steps from `QuestionStep` (GENRES, LANGUAGE, MOOD, ERA, RUNTIME, RATING). Next is enabled only when `isCurrentStepValid && !isSaving`.
2. **Validation** (`QuizUiState.kt` `QuizAnswers.isValidForStep` L106-115): only GENRES needs ≥1 selection; RATING needs `minRatingThreshold in 0.0..10.0`; others always valid. `goToNextStep` (ViewModel L73-83) returns early if the step is invalid; on the last step it shows a confirmation dialog.
3. Confirm → `QuizViewModel.submitQuiz` (L173-201): `answers.toUserPreferenceProfile()` → `quizRepository.saveUserPreferenceProfile(profile)` → `isComplete = true` → `onComplete()` → NavHost navigates to `Recommendations` with `popUpTo(Quiz){inclusive=true}` (`MADVerseNavHost` L52-61).
4. `QuizAnswers` → `QuizProfileMapper.mapToUserProfile` (L18-31): copies genres/mood/era/runtime, trims language (blank→null), clamps rating to 0..10, stamps time → `UserPreferenceProfile`.
5. `QuizRepositoryImpl.saveUserPreferenceProfile` (L65-71): `userPreferenceDao.saveUserPreferenceProfile(UserPreferenceEntity.fromDomain(profile, userId))` (Room) then `syncService.pushPreferences(userId, profile)` (cloud; no-op for guest).
6. `RecommendationsViewModel.refresh` (L51-80): `quiz.hasSavedProfile()` → if none: "Complete your taste quiz…" else `getUserPreferenceProfile()` → `recommendations.invokeWithProfile(profile, limit = 20)`.
7. `GetPersonalizedRecommendationsUseCase.invokeWithProfile` (L63-88): `movieRepository.discoverCandidateMovies(genreIds, era.startYear, era.endYear, minRatingThreshold)` → on success `recommendationEngine.scoreAndRank(profile, candidates, limit)`.
8. `DeterministicRecommendationEngine.scoreAndRank` (L33-51): dedupe → score → sort → take(limit).
9. Back in the ViewModel, each result is paired with `RecommendationInsightProvider.explain(...)` (try/catch with `LocalRecommendationInsightProvider` fallback, L67-69) → `RecommendationRow` → `RecommendationsScreen`.

**(c) Code location:** files named per step.

**(d) Likely follow-up:** *"The ViewModel can `submitQuiz` without genres — is that a bug?"*
Answer: Step validation prevents reaching the last step with no genres, and `QuizAnswers.isComplete()` exists, but `submitQuiz` itself doesn't re-check it; `goToStep(step)` also skips validation. In normal UI flow it can't happen; it's a missing second guard, not a visible bug.

**(e) Do NOT claim:** that it "grades" the user, uses AI/ML, or that `QuizResultScreen` is part of the flow (Section 0 #4).

---

## Q2.2 How are mood, era and runtime mapped to genre IDs / years / minutes?

**(a) 30-second spoken answer**
"Each answer is an enum with fixed values. Mood maps to a list of TMDB genre IDs, era maps to a year range, and runtime is a minutes range. Era is sent to TMDB as a date filter; mood and runtime are only used later when scoring."

**(b) Deeper technical answer** (`M/core/model/UserPreferenceProfile.kt` L18-39)
- **Mood → genre IDs** (score only): MIND_BENDING `[878, 9648, 53]` (Sci-Fi, Mystery, Thriller); ADRENALINE `[28, 12]`; FEEL_GOOD `[35, 10751, 16]` (Comedy, Family, Animation); DARK_AND_SPOOKY `[27, 53]`; EMOTIONAL `[18, 10749]`; ANY `[]`.
- **Era → years:** CLASSIC `null..1989`; MODERN `1990..2014`; CONTEMPORARY `2015..null`; ANY none. In discovery: `startYear?.let{"$it-01-01"}`, `endYear?.let{"$it-12-31"}` → `primary_release_date.gte/lte` (`MovieRepositoryImpl` L184-185, `TmdbApi` L77-78). In scoring the engine re-derives the same ranges from the movie's release-year (`scoreEra` L138-143).
- **Runtime → minutes** (score only): QUICK `< 100`, STANDARD `100..140`, EPIC `> 140` — hard-coded in `scoreRuntime` (L222-227). (`RuntimePreference.maxMinutes` exists on the enum but the engine does not read it.)
- **Language:** `en, hi, fr, es, ja, ko` or none; **not** sent to TMDB — only scored.
- **Genres:** 12 canonical TMDB IDs in `QuizGenreItem.CANONICAL_GENRES` (28, 12, 35, 18, 27, 878, 53, 10749, 16, 9648, 14, 80).
- **Min rating** default 6.0 (`QuizAnswers`), sent as `vote_average.gte` AND used in scoring.

**(c) Code location:** as above.

**(d) Likely follow-up:** *"Why is Thriller (53) in both Mind-Bending and Dark & Chilling?"*
Answer: Genres overlap by nature; the mood score is an overlap *fraction* of each mood's own list, so a thriller scores 1/3 of 20 for Mind-Bending but 1/2 of 20 for Dark & Chilling.

**(e) Do NOT claim:** that mood/runtime filter TMDB results.

---

## Q2.3 Give me the exact formula with a worked example.

**(a) 30-second spoken answer**
"Score = Genre up to 30, Mood 20, Era 15, Quality 15, Language 10, Runtime 10 — total 100 — then clamped between 0 and 100. Genre and Mood are fractions: how many of your picks does the movie match, times the weight, rounded. Era, Language and Runtime are all-or-nothing. Quality is the TMDB rating over 10 times 15."

**(b) Deeper technical answer** (`DeterministicRecommendationEngine.kt`; constants L241-248)
```
Genre   = round( |selected ∩ movieGenres| / |selected| × 30 )     (empty selection → 15)
Mood    = round( |moodGenres ∩ movieGenres| / |moodGenres| × 20 ) (mood ANY → 10)
Era     = 15 if in range, else 0                                   (ANY or missing year → 15)
Quality = 0 if rating < minRating else round( min(rating,10)/10 × 15 )
Language= 10 if equal (case-insens.) else 0                        (none/missing → 10)
Runtime = 10 if in range else 0                                    (ANY/missing/≤0 → 10)
Total   = clamp(sum, 0, 100)
```
`round` = Kotlin `roundToInt()` (ties round **up**, e.g. 7.5 → 8).

**Worked example (real constants).**
Profile: genres `{28 Action, 878 Sci-Fi}`, mood `MIND_BENDING` (`[878, 9648, 53]`), era `CONTEMPORARY` (≥2015), runtime `STANDARD`, language `"en"`, min rating `6.0`.
Movie: genreIds `[28, 878, 12]`, release `2019-05-10`, voteAverage `7.8`, voteCount `5000`, popularity `80`, language `"en"`, runtimeMinutes `null`.

| Factor | Calculation | Points |
|---|---|---|
| Genre | overlap 2 of 2 → 2/2 × 30 | **30** |
| Mood | only 878 matches → 1/3 × 20 = 6.67 → rounds | **7** |
| Era | 2019 ≥ 2015 | **15** |
| Quality | 7.8 ≥ 6.0 → 0.78 × 15 = 11.7 → rounds | **12** |
| Language | "en" = "en" | **10** |
| Runtime | `null` → neutral | **10** |
| **Total** | 30+7+15+12+10+10 | **84 → "84%"** |

Reasons generated, in order: "Matches 2 of your preferred genres", "Fits your "Mind-Bending & Thought-Provoking" mood", "From the contemporary & recent (2015 - present)", "Well-rated (7.8★)", "Matches your preferred language (EN)". (Runtime adds no reason because the movie's runtime is missing; "Popular…" not added because popularity 80 < 100 and votes 5000 < 10 000.)

Contrast (engine-only maths; the real TMDB filter would have excluded it): genres `[28, 878]`, 2010, rating 6.5, language `fr`, runtime 150 → Genre 30, Mood 7, Era 0, Quality round(9.75)=10, Language 0, Runtime 0 (150 is outside 100..140) = **47**. (If the runtime were `null` as in real discover data it would be 57.)

**(c) Code location:** `scoreGenre` L80-95, `scoreMood` L101-120, `scoreEra` L126-151, `scoreQuality` L157-178, `scoreLanguage` L184-204, `scoreRuntime` L210-235, clamp L66-67. Matches `docs/ARCHITECTURE.md` L770-818 and `DEMO_AND_VIVA.md` L9. Tests: `T/RecommendationEngineTest.kt` (14 tests, e.g. `scoreAndRank_matchReasons_arePopulatedAccurately`).

**(d) Likely follow-up:** *"Why clamp if the max is 100 already?"*
Answer: Defensive: the six capped integer parts can't exceed 100 today, but if someone changes a weight the output stays a valid percentage.

**(e) Do NOT claim:** it is a probability or accuracy of liking the movie; or that popularity adds points (it only adds a reason and acts in tie-breaking).

---

## Q2.4 Neutral / missing-metadata rules, and the inconsistencies

**(a) 30-second spoken answer**
"If you didn't state a preference, or the movie has no data for a factor, we give neutral points so nobody is punished unfairly. But the neutral value is not the same everywhere: for genre and mood it is *half* marks; for era, language and runtime it is *full* marks. That's an inconsistency I can explain but it is a design choice we didn't document."

**(b) Deeper technical answer**
| Factor | No preference | Movie metadata missing | Notes |
|---|---|---|---|
| Genre (30) | empty selection → **15** (half) | movie `genreIds` empty → overlap 0 → **0** | No neutral for missing movie genres |
| Mood (20) | `ANY` → **10** (half) | movie genres empty → **0** | |
| Era (15) | `ANY` → **15** (full) | release date null/unparsable → **15** | |
| Quality (15) | no "no preference"; threshold default 6.0 | rating null → `0.0` in `MovieDto.toDomain` → below 6.0 → **0** | Missing rating **is** penalised |
| Language (10) | none → **10** | movie language blank → **10** | |
| Runtime (10) | `ANY` → **10** | null / ≤0 → **10** | |

Inconsistencies to be ready for:
1. **Half vs full neutral.** Genre/Mood ANY = half; Era/Lang/Runtime ANY = full. Max neutral baseline for an all-"any" profile = 15+10+15+10+10 = **60** + quality.
2. **Quality has no neutral** and treats a missing rating as 0.
3. **Missing-metadata "fairness" is only for era/language/runtime** (the KDoc "never penalizes … (language, era, runtime)" L26 is accurate for those three only).
4. **Reason without points:** "Popular with global audiences" (popularity ≥ 100 or votes ≥ 10 000) is shown but awards no points.

**Honest justification (my reasoning — the docs only say "neutral fallback", they do not justify the half/full split):** Genre and Mood are *affinity* signals — "no stated taste" gives a middle value so a movie that does match stated tastes can outrank others. Era, Language, Runtime behave like *constraints* — "no constraint" means every movie satisfies it, so everyone gets full marks, and "missing data" gets full marks so we never lose a good movie just because TMDB's list endpoint omitted a field. In the real app the UI forces ≥1 genre, so the empty-genre neutral path is rarely hit.

**(c) Code location:** `DeterministicRecommendationEngine.kt` L85, L107-110, L132-136, L162, L194-196, L216-220; `MovieDto.kt` L35.

**(d) Likely follow-up:** *"Isn't giving full marks for missing runtime unfair to movies that have a known, matching runtime?"*
Answer: Yes — a movie with a known matching runtime also gets 10, so they tie. Worse, in the live app runtime is *never known* for candidates (Section 0 #7), so the runtime question currently doesn't change ranking. It is implemented and unit-tested, but not effective end-to-end.

**(e) Do NOT claim:** that the neutral values were derived from data or user research.

---

## Q2.5 Tie-breaking, deduplication, determinism, and why "weighted match score" not probability

**(a) 30-second spoken answer**
"We remove duplicate movies by ID, score everything, and sort by percentage. Ties are broken by TMDB rating, then number of votes, then popularity, and finally the lowest movie ID — so the same input always gives the same order. It's deterministic: no randomness, no clock, no network inside the scoring. The percentage is just 'how many of your stated preferences this movie satisfies, weighted' — it's not the chance you'll like it."

**(b) Deeper technical answer** (`DeterministicRecommendationEngine.kt` L33-51)
- `candidates.distinctBy { it.id }` (keeps the first occurrence).
- `sortedWith(compareByDescending{matchPercentage}.thenByDescending{voteAverage}.thenByDescending{voteCount}.thenByDescending{popularity}.thenBy{id})`; `.take(limit)`.
- Empty candidates or `limit <= 0` → empty list.
- Deterministic: scoring functions are pure, only `Movie` and `UserPreferenceProfile` inputs. (Note `UserPreferenceProfile.updatedAtTimestamp` defaults to `System.currentTimeMillis()` but the engine never reads it.)
- Tests: `scoreAndRank_deterministicTieBreaking_breaksTiesByAttributesAndId`, `scoreAndRank_deduplicatesCandidateMovies`, `scoreAndRank_resultsAreSortedDescendingByMatchPercentage`.
- "Weighted match score": the weights 30/20/15/15/10/10 are our own design choices; there's no trained model or calibrated likelihood. `LocalRecommendationInsightProvider.detail` (L28-30) tells the user "neither score guarantees you will enjoy a movie".

**(c) Code location:** as above; `M/core/domain/recommendation/RecommendationInsightProvider.kt` L28-31.

**(d) Likely follow-up:** *"Where do the weights 30/20/15/15/10/10 come from?"*
Answer: They are design decisions by the team; the code has no data-driven calibration. ARCHITECTURE.md even shows older drafts (40/25/15/20) that were replaced — those are labelled "historical plans" (ARCHITECTURE.md L5).

**(e) Do NOT claim:** statistical validity, A/B testing or "AI accuracy".

---

## Q2.6 How are the "reasons" generated?

**(a) 30-second spoken answer**
"Each scoring function adds a sentence to a list only when that factor actually matched — for example 'Matches 2 of your preferred genres'. There is no text generation. A second class just tidies that list and builds a one-line summary."

**(b) Deeper technical answer**
- Engine: `reasons += "..."` inside `scoreGenre`/`scoreMood`/`scoreEra`/`scoreQuality`/`scoreLanguage`/`scoreRuntime` (strings built from constants and the profile/movie fields); stored in `ScoredMovieRecommendation.matchReasons`.
- `LocalRecommendationInsightProvider.explain` (`RecommendationInsightProvider.kt` L14-34): trims, drops blanks, de-duplicates the engine's reasons ("The engine is the authority for scoring factors. Never infer extra factors from metadata."), summary = `"<pct>% preference match: <first reason>"` (or a no-factor message), and a fixed `detail` paragraph + "Unavailable metadata: release date / language / runtime" when those fields are missing.
- Interface is documented as "Local, synchronous extension point. Implementations must not transmit movie data externally." (L9).

**(c) Code location:** as above; test `T/RecommendationInsightProviderTest.kt`.

**(d) Likely follow-up:** *"Can a reason appear for a factor that gave zero points?"*
Answer: Only the "Popular with global audiences" reason (popularity ≥ 100 or ≥ 10 000 votes) — it is not a scored factor (Q2.4 #4). All other reasons are added only when points > 0 (genre overlap > 0, in-era, language match, in-runtime, rating ≥ 7).

**(e) Do NOT claim:** reasons are AI-written, or that they explain 100% of the score.

---

## Q2.7 How is the candidate pool built? AND vs OR, page, empty pool, limitations

**(a) 30-second spoken answer**
"We call TMDB's discover endpoint once, page 1, sorted by popularity, with your genres, era dates and minimum rating. One honest limitation: we send genres joined by commas, which TMDB treats as AND. So if you pick three genres, TMDB only returns movies that have all three. If nothing comes back, the screen says 'No matches found. Try updating your preferences.'"

**(b) Deeper technical answer**
- `MovieRepositoryImpl.discoverCandidateMovies` L174-199: `genreParam = genreIds.joinToString(",")` (or null if empty); `sortBy = "popularity.desc"`; `page` default 1 (`MovieRepository.kt` L32). The use case never passes a page → **page 1 only**.
- **AND semantics.** TMDB docs: comma = AND, pipe `|` = OR (I confirmed via web search of the TMDB API behaviour; this is external API behaviour, not visible in the repo). So `with_genres=28,878` returns only movies tagged Action **and** Sci-Fi. This is not noted in any project doc (grep for `with_genres`/AND/OR in docs found nothing about it).
- Page size is 20 (TMDB behaviour, not in code); `RecommendationsViewModel` asks for `limit = 20` (L61), so the engine reorders **at most ~20 movies**; it can't surface anything outside page 1.
- Consequences that follow from reading the code:
  1. With AND filtering every candidate contains **all** selected genres → **Genre score is always 30** for real candidates.
  2. Era is also a TMDB date filter (`primary_release_date`) → the engine's Era score is almost always 15.
  3. `minRating` is sent as `vote_average.gte` **and** used as a scoring cut-off → Quality is always `round(rating/10×15)`.
  4. Runtime is always neutral (null) (Section 0 #7).
  5. So in practice only **Mood, Language, Quality-gradient** discriminate between candidates. The formula is correct as code and in unit tests, but the real pool is pre-filtered, which flattens three of the six factors.
  6. No `vote_count.gte` is sent, so obscure titles with a few high votes can pass the rating filter (popularity sort mitigates it).
- **Empty pool:** `Resource.Success(emptyList)` → `scoreAndRank` returns empty → `RecommendationsViewModel` L65 sets `UiState.Empty("No matches found. Try updating your preferences.")`. Network errors → `UiState.Error(message)`.
- Genre Explorer also reuses `discoverCandidateMovies` but with at most one genre and pagination (`page < 500`).

**(c) Code location:** `MovieRepositoryImpl.kt` L174-199; `TmdbApi.kt` L74-82; `GetPersonalizedRecommendationsUseCase.kt` L63-88; `RecommendationsViewModel.kt` L51-80.

**(d) Likely follow-up:** *"Then why score genre overlap at all?"*
Answer: The engine is a reusable, tested component: it works on any candidate list (the unit tests feed mixed lists), and `discoverCandidateMovies` is just one source. With the current AND-style fetch the genre term is saturated at 30; switching to pipe-separated (OR) would make it meaningful — that's a one-line future improvement, which I haven't made (and the task forbids code changes).

**(e) Do NOT claim:** "we fetch hundreds of candidates", "OR semantics", or that runtime/era change the ranking in practice.

---

## Q2.8 Gemini — is it used? "Do you use AI?"

**(a) 30-second spoken answer (safe answer to "Do you use AI?")**
"No. Recommendations are computed locally by a fixed, deterministic scoring formula — no machine learning and no external AI service, and we don't send TMDB data anywhere. The project does contain an unused Gemini dependency and a reserved API-key field from an earlier plan, but no code calls it."

**(b) Deeper technical answer**
- `app/build.gradle.kts`: `val geminiApiKey` read from `local.properties`/env (L22-24); `buildConfigField("String","GEMINI_API_KEY",...)` (L43); `implementation(libs.google.generativeai)` (L99-100) = `com.google.ai.client.generativeai:generativeai:0.9.0`.
- **Usage check:** grep over `app/src` for `GenerativeModel`, `generativeai`, `GEMINI`: **no matches in Kotlin source**. The BuildConfig field is generated but never referenced.
- `AiRecommendationEnhancer` (`RecommendationEngine.kt` L33-38): interface only; no class implements it; `DomainModule` binds only `RecommendationInsightProvider` (local) and `RecommendationEngine`.
- Docs: README L8 "Gemini configuration is reserved; recommendations currently run locally"; `local.properties.example` "Reserved; no active external AI recommendation layer"; `DEMO_AND_VIVA.md` L3 "rather than an external AI service". But `ARCHITECTURE.md` L37/L316 still describe "optional Gemini AI assistance" (labelled historical at L5).
- Why it matters: leaving the SDK in the APK adds size and a (blank-by-default) key field; it is harmless but unnecessary.

**(c) Code location:** `app/build.gradle.kts` L22-24, 43, 99-100; `DomainModule.kt` L11-35.

**(d) Likely follow-up:** *"Then why is the dependency there?"*
Answer: "It was a planned optional 'curator note' extension (documented as reserved). It was not built; it's unused and could be removed. Nothing leaves the device for recommendations."

**(e) Do NOT claim:** "uses Gemini", "AI-powered recommendations", or "personalised by machine learning".

---

## Q2.9 Where is the profile stored; does it survive restarts; how does the quiz prefill?

**(a) 30-second spoken answer**
"The profile is saved in a Room table called `user_preference_profile`, with one row per user ID, using 'guest' when signed out. Room is a real database file on the phone, so it survives restarts. When you reopen the quiz, the ViewModel loads the saved profile and fills the answers back in."

**(b) Deeper technical answer**
- `UserPreferenceEntity` (`@Entity("user_preference_profile")`): PK `user_id` (default "guest"), `genre_ids_csv` (comma-joined IDs), `mood`/`era`/`runtime_preference` as enum **names**, `preferred_language?`, `min_rating_threshold`, `updated_timestamp`. `toDomain()` parses with safe fallbacks (`valueOf` in try/catch → ANY), so malformed rows don't crash (test `userPreferenceEntity_malformedData_recoversGracefullyWithDefaults`).
- `REPLACE` insert → one row per user ("singleton per user"). `QuizRepositoryImpl` keys by `sessionManager.currentUserId`; flows use `currentUserIdFlow.flatMapLatest` so UI follows account switches.
- Prefill: `QuizViewModel.init → restoreExistingPreferences` (L39-68): `getUserPreferenceProfileOnce()`; if `selectedGenreIds.isNotEmpty()` → `QuizAnswers.fromUserProfile(...)` → `initialProfileRestored = true`; else stays empty. `resetQuiz()` clears local answers (does not delete the saved row).
- `hasSavedProfile()` distinguishes "no quiz yet" from "defaults" (test `savedProfilePresenceDistinguishesDefaultsFromNoQuiz`).
- Cloud: for a signed-in user `pushPreferences` writes `users/{uid}/preferences/current`; a Firestore listener writes remote changes back into Room (Q3.4).
- Restart persistence is **JVM-tested only with a fake DAO** (`processRestartSimulation_savedStateRestoredInFreshRepositoryInstance`); README L17 says Room persistence across process restarts is **not** device-verified.

**(c) Code location:** `M/core/database/entity/UserPreferenceEntity.kt`; `M/core/database/dao/UserPreferenceDao.kt`; `QuizRepositoryImpl.kt` L44-76; `QuizViewModel.kt` L39-68.

**(d) Likely follow-up:** *"Why store genre IDs as a CSV string and not a separate table?"*
Answer: Simplicity — the set is tiny and always read/written as a whole. Downside: not queryable by genre and relies on string parsing (which we guard with `toIntOrNull`).

**(e) Do NOT claim:** that persistence across restarts is device-verified; that retaking the quiz keeps history (REPLACE overwrites).

---

# SECTION 3 — REMAINING IMPLEMENTATION QUESTIONS

(Format: spoken answer · code location · follow-up. "Don't claim" added where it matters.)

## Q3.1 Architecture: MVVM + clean layers, DTO → domain → UiState, StateFlow, Resource/UiState, Hilt

**Spoken:** "Each screen has a Compose UI and a ViewModel. The ViewModel exposes one `StateFlow` of UI state. ViewModels call repository interfaces. Repositories call Retrofit or Room and convert network objects (DTOs) into plain domain models. Hilt wires everything. Repositories return a `Resource` (Loading/Success/Error); ViewModels turn that into a `UiState` (Loading/Success/Empty/Error) the UI can draw."
**Code:** layers `M/core/{network,database,data,domain,model,common,di,navigation,designsystem}` + `M/feature/*/presentation`. DTO→domain: `MovieDto.toDomain()`, `VideoDto.toDomain()`, `WatchProvidersResponseDto.toDomain(...)`. `Resource`: `core/common/result/Resource.kt` (sealed class, `Success/Error(message, cause)/Loading`). `UiState`: `core/common/ui/UiState.kt` (sealed interface `Loading/Success/Empty/Error(message,isOffline)`). Hilt: `@HiltAndroidApp MADVerseApp`, `@AndroidEntryPoint MainActivity`, modules `NetworkModule`, `DatabaseModule`, `RepositoryModule` (`@Binds` interface→impl), `DomainModule`, `FirebaseModule` (+ `DispatcherModule` providing `@IoDispatcher`, which I did not open).
**Follow-up:** *"Is this strict Clean Architecture?"* — "No, 'clean-ish': there is a `domain` package, but only recommendations and streaming use real domain classes/use-case; most ViewModels call repositories directly. There are two similar sealed types (`Resource` and `UiState`) by design: data-layer result vs UI-layer state."
**Don't claim:** a separate Gradle module per layer (single `app` module; ARCHITECTURE.md L411 says so).

## Q3.2 Networking: Retrofit/OkHttp/Gson, AuthInterceptor, logging NONE, safeApiCall

**Spoken:** "Retrofit calls the TMDB v3 API. An OkHttp interceptor adds our `api_key` to every request from `BuildConfig`, which is filled from `local.properties` at build time so the key isn't in source control. Logging is turned off because the key sits in the URL. A helper wraps every call and turns exceptions into friendly errors."
**Code:** `NetworkModule.kt` (base URL `https://api.themoviedb.org/3/`, 30 s connect/read/write timeouts, `GsonConverterFactory`); `AuthInterceptor.kt` L19-27 (adds `api_key` query param if `BuildConfig.TMDB_API_KEY` is non-blank); `app/build.gradle.kts` L18-20, 42 (key from `local.properties`, else env `TMDB_API_KEY`, else ""); logging `Level.NONE` L22-27 (comment: "AuthInterceptor puts credentials in the URL"). `safeApiCall` (`MovieRepositoryImpl.kt` L209-232): `IOException` → "Network error: Check your internet connection."; `HttpException` 401 → auth failed message, 404 "Movie not found", 429 rate limit, 500..599 "temporarily unavailable", else "Unexpected API error (HTTP n)"; rethrows `CancellationException`; any other `Exception` → generic error. Tests: `T/MovieRepositoryTest.kt`, `T/NetworkLoggingTest.kt`, `T/DtoMappingTest.kt`.
**Follow-up:** *"Is the API key secret?"* — "It stays out of Git, but it is compiled into the APK's `BuildConfig` and travels in every request URL, so a determined user could extract it. It is a free-tier TMDB key; the standard TMDB mobile pattern." (My assessment.) If key is blank, no param is added → TMDB returns 401 → "TMDB authentication failed…".
**Don't claim:** key is encrypted/obfuscated; certificate pinning (none).

## Q3.3 Room: entities, DAOs, DB v2, MIGRATION_1_2, `exportSchema=false`

**Spoken:** "Room stores four tables: favourites, watchlist, user preferences and a queue of pending cloud-sync actions. We're on version 2. Version 1 had no user concept; migration 1→2 copies old rows into new tables tagged with user 'guest', makes the key (movie, user), changes the preferences key to user ID, and creates the pending-sync table. No data is dropped."
**Code:** `MADVerseDatabase.kt` L16-25 (`version = 2`, `exportSchema = false`), `MIGRATION_1_2` L47-130 (create `*_new`, `INSERT … SELECT … 'guest'`, drop old, rename; create `pending_sync_actions`); `DatabaseModule.kt` L22-28 (`addMigrations(*ALL_MIGRATIONS)`, **no** `fallbackToDestructiveMigration`). Entities: `FavoriteEntity`/`WatchlistEntity` (composite PK `movie_id,user_id`), `UserPreferenceEntity` (PK `user_id`), `PendingSyncEntity` (autogenerate id; `action_type` UPSERT/DELETE; `entity_type` FAVORITE/WATCHLIST/PREFERENCE; `item_id`; `payload_json` unused). DAOs: `FavoriteDao`, `WatchlistDao`, `UserPreferenceDao`, `PendingSyncDao` — all per-user queries, `OnConflictStrategy.REPLACE`; default overloads use `"guest"`.
**Why `exportSchema=false` matters:** Room won't write schema JSON files, so there's no committed history of v1/v2 schemas, no `MigrationTestHelper`-style migration tests on a real database, and no auto-migration. Our migration test (`T/RoomMigrationTest.kt`) uses a **proxy `SupportSQLiteDatabase` that only records the SQL strings** — it proves the right statements are issued, **not** that they succeed on real SQLite or that Room's schema validation accepts the result.
**Follow-up:** *"What if migration fails?"* — "Because there is no destructive fallback, the app would crash on open rather than silently wipe user data — safer for data, worse for availability. There is no instrumented upgrade test, so I can't claim a real 1→2 upgrade was verified." 
**Don't claim:** real-device migration tested; schema exported.

## Q3.4 Firebase: email + Google sign-in, guest mode, Firestore sync, offline queue, merge, rules

**Spoken:** "Users can browse as a guest; their data is stored under the ID 'guest'. They can sign in with email/password or with Google through Android's Credential Manager. Once signed in, a sync service mirrors favourites, watchlist and preferences to Firestore under `users/{uid}`. If a push fails, the action is saved in a Room queue and retried later. When a guest signs in, a dialog offers to merge guest data into the account. Firestore rules make sure only the signed-in owner can read/write their own data."
**Code:**
- Auth: `FirebaseAuthRepositoryImpl` (email sign-in/up, Google `signInWithCredential`, password reset, verification mail, update name, delete, sign-out; `authState` via `AuthStateListener`; `currentUserId = uid ?: "guest"`). Google: `AuthScreen.kt` L243-265 uses `CredentialManager` + `GetGoogleIdOption(setFilterByAuthorizedAccounts(false), serverClientId = R.string.default_web_client_id)` → `AuthViewModel.signInWithGoogle(idToken)`. `google-services.json` is **gitignored** (`.gitignore` L33).
- Validation: email regex, password ≥ 6, confirm match (`AuthViewModel` L79-131).
- Sync: `SessionManagerImpl` init collects `authState` → `startUserSession(uid)` / `stopUserSession()`. `FirestoreSyncServiceImpl.startUserSession` (L56-73) attaches 3 snapshot listeners (favorites, watchlist, preferences/current), then `processPendingSyncActions` + `syncAll`. Repos push after each Room write (`FavoritesRepositoryImpl.addToFavorites` L61-65). Failure → `PendingSyncEntity` + `SyncState.OfflinePending` (e.g. L268-280).
- Pending replay: `processPendingSyncActions` L561-619 (per-action guard `action.userId == userId`; delete on success; keep on exception).
- Merge: `AuthViewModel.handleAuthSuccess` → `sessionManager.hasGuestData()` → merge dialog → `onMergeConfirmed` → `mergeGuestDataIntoAccount(uid)` (L445-511): copies guest favourites/watchlist not already on the account (re-keyed to uid, pushed); preferences copied if account has none, or account's genres are blank, or guest's `updatedTimestamp` is newer. `onMergeDismissed` keeps data separate.
- Rules: `firestore.rules` — everything under `/users/{userId}` requires `request.auth.uid == userId`; field/type checks (`movieId is int`, `title` string, `savedTimestamp` number; preferences doc id must be `current`); default deny `/{document=**}`.
- Persistent Firestore cache enabled in `FirebaseModule` (`PersistentCacheSettings`).
- Tests (fakes): `AuthViewModelTest`, `AccountIsolationTest`, `EndToEndUserJourneysTest` flows 2-7 (`FakeAuthRepository`, `FakeCloudSyncService`).
**Follow-up & limits (from reading the code — not run):**
1. Guest rows are **copied, not deleted** after merge (no delete call), so guest data remains under "guest".
2. The three listeners treat Firestore as source of truth: any local row missing remotely is **deleted locally** (L133-138). Because listeners attach *before* pending replay, an offline-created item could be removed by the first snapshot, then the pending upsert finds no local row and is dropped as "obsolete" (L578-583). Plausible race; untested.
3. Pending replay runs only on `startUserSession` (new login/app start) and the Profile "manual sync" button (`ProfileViewModel.triggerManualSync` L195-203) — **not** on connectivity change.
4. Firestore normally queues writes offline and completes the write `Task` only after server acknowledgment, so a plain "offline" may not throw; the queue path then triggers on errors rather than on disconnect (my inference about the SDK; not tested here).
5. `AuthScreen` swallows non-cancel Google sign-in exceptions with only a comment "Show user-friendly error" (L262-264) — no visible error.
6. `deleteAccount` deletes cloud+local data **first**, then the auth account (`ProfileViewModel` L236-237); if Firebase demands recent login, the data is already gone though the account remains.
7. `PendingSyncEntity.payload_json` is never filled; replay re-reads the local row.
**Don't claim:** "real-time conflict resolution", "end-to-end encrypted", "tested on a real Firebase project" (no instrumented/emulator tests found), or that the README/docs describe this (they don't).

## Q3.5 Search: 400 ms debounce, stale-result protection, filtering

**Spoken:** "As you type, we wait 400 ms of silence before calling TMDB. Each new keystroke cancels the previous waiting job. If an older response arrives after you've changed the text, we ignore it. Genre and year filtering are not in the Search screen; they're in Genre Explorer."
**Code:** `SearchViewModel.kt`: `debounceDurationMillis = 400L` (L44, test-configurable), `onQueryChanged` cancels `searchJob` and delays (L51-75), blank query → immediate reset to prompt (L54-65), `onSearchSubmitted` skips debounce (L81-90), stale guard `if (_uiState.value.query.trim() != queryToSearch) return@collect` (L125-127), offline detection `cause is IOException || message contains network/connection/offline` (L148-153). `include_adult=false` (repository L83). Genre Explorer filters: genre (single), year range validated 1870..2100 with start ≤ end, min rating 0-10, sort (popularity/rating/release), pagination to page 500 (`GenreExplorerViewModel.kt` L65-100). Test: `T/SearchViewModelTest.kt` (9), `GenreExplorerViewModelTest` (5).
**Follow-up:** *"Is ARCHITECTURE.md wrong?"* — "It describes the planned design (genre chips + year filter in Search); the shipped Search has neither. The code wins."
**Don't claim:** genre/year filters in Search; stale-protection beyond query comparison (it compares text, not request IDs).

## Q3.6 Library: Favorites vs Watchlist isolation; per-user queries with `flatMapLatest`

**Spoken:** "Favorites and Watchlist are two different Room tables with their own DAOs, so one can't affect the other, and a movie can be in both. Every query includes the current user ID. The repository listens to the current-user flow and uses `flatMapLatest`, so when the account changes the list switches to the new user's data automatically."
**Code:** `FavoritesRepositoryImpl.getFavoriteMovies` L39-44 and `WatchlistRepositoryImpl.getWatchlistMovies` L39-44 (`sessionManager.currentUserIdFlow.flatMapLatest { dao…(userId) }`); DAO queries `WHERE user_id = :userId ORDER BY saved_timestamp DESC`; `LibraryViewModel` (default tab `WATCHLIST`, sort options, `combine` with sort flow, retry trigger via `flatMapLatest`, `stateIn(WhileSubscribed(5000))`); `RepositoryActions` serialises taps per key and surfaces an error. Tests: `CollectionIsolationTest` (5), `AccountIsolationTest` (2), `FavoritesRepositoryTest`, `WatchlistRepositoryTest`, `LibraryViewModelsTest`.
**Follow-up:** *"Why is the Library default tab Watchlist?"* — it is just the initial value of `_selectedTab` (L39).
**Don't claim:** that saved movies carry genres/vote counts — `FavoriteEntity.toDomain()` fills `genreIds = emptyList()`, `voteCount = 0`, `popularity = 0.0` (L40-52).

## Q3.7 Comparison screen: logic and persistence

**Spoken:** "You can compare up to three movies side by side. Search is debounced at 350 ms, and each selected movie loads its details separately. The selected IDs survive screen rotation and process death because they're stored in `SavedStateHandle`. You can't add a duplicate, and you can replace or remove one."
**Code:** `ComparisonViewModel.kt`: `init` restores `selectedIds` (or `movieId` arg) → `take(3)` (L33-40); `search` with 350 ms delay and `searchVersion` stale guard (L42-63); `select` rejects invalid/duplicate IDs, supports replacement (L65-81); `persist()` writes `selectedIds`/`replacingId` to `SavedStateHandle` (L93-96); `loadDetails` per-ID version map and ID-match check (L98-125). Routes: `comparison?movieId={movieId}` (`Screen.kt` L36-38); reachable from Recommendations `onCompare` and the top-bar overflow menu. Test: `T/ComparisonViewModelTest.kt` (13).
**Follow-up:** *"Is the comparison saved to Room?"* — No. `SavedStateHandle` survives config change/process death but not "clear app data" or a new session; it's not in Room/Firestore.
**Don't claim:** database persistence of comparisons.

## Q3.8 Watch providers: region selection (default India), stale-country handling

**Spoken:** "TMDB returns providers for many countries in one response. We pick one country's slice, defaulting to India, and the user can switch among six regions. If the user switches quickly, a version counter makes sure only the newest request can update the screen. If a country has no data we show an empty state and never show another country's list."
**Code:** `RegionConfig.kt` (`DEFAULT_COUNTRY_CODE="IN"`; IN, US, GB, CA, AU, JP). `WatchProvidersResponseDto.toDomain` L14-37: `require(id == movieId)`, `results?.get(countryCode)` only (L16-17 comment: "never label another country's catalog as this region's"). `MovieDetailsViewModel.setRegion` validates against supported list (L247-254); `fetchWatchProviders` increments `providerVersion`, cancels previous job, clears lists, ignores results with a stale version or other movie (L260-346). Empty → "No streaming options available in this region." Providers sorted by name. `ProviderDestinationResolver` only ever returns a TMDB regional watch page (or UNAVAILABLE) — **no direct Netflix/Prime deep links** are populated ("No exact content links … verified in this pass", L28-29). Tests: `ProviderDestinationResolverTest` (14), `StreamingContractTest`, `MovieDetailsViewModelTest`.
**Follow-up:** *"Is the selected region remembered?"* — No; `selectedCountryCode` lives in `MovieDetailsUiState` only, resets to IN per Details screen. The "US fallback" is not implemented (Section 0 #12).
**Don't claim:** deep links into streaming apps; that availability = entitlement (README says it's a guide).

## Q3.9 Navigation Compose: routes, arguments, back stack

**Spoken:** "`Screen` is a sealed class of route strings. The bottom bar has four tabs: Discover, Search, Quiz, Library, with saved/restored tab state. Other screens — Genres, Free Movies, Recommendations, Comparison, Profile, Auth — come from the top-bar menu or from other screens. Detail and player screens take typed arguments."
**Code:** `Screen.kt` (routes + `createRoute` helpers; arg keys `movieId`, `videoId`, `movieTitle`, `videoType`, `catalogueId`); `NavItem.kt` (4 tabs); `MADVerseAppShell.kt` L99-114 (`popUpTo(Discover){saveState=true}; launchSingleTop; restoreState`), overflow menu L79-93, back arrow shown when not on a bottom-bar route; `MADVerseNavHost.kt`: start destination `Discover`; Quiz completion `popUpTo(Quiz){inclusive=true}` (L55-58); Auth success `popUpTo(Auth){inclusive=true}` → Profile (L162-164); sign-out → `popUpTo(Discover){inclusive=false}` (L187-190). Typed args: `MovieDetails` int; `Comparison` int default 0; `YouTubePlayer` strings with defaults.
**Follow-up:** *"Why is the title URL-encoded in `createRoute` but not decoded in the ViewModel?"* — Navigation already decodes arguments; code comment `YouTubePlayerViewModel.kt` L56: "never URL-decode them a second time" (test `routeEncodesSpecialCharactersOnceAndIncludesCategory`).
**Don't claim:** deep links (none declared in the manifest) or type-safe navigation via the newer serialization API.

## Q3.10 Testing: what the suites cover and what they do NOT prove

**Spoken:** "We have pure JVM unit tests — about 289 test methods across 33 classes by my count — covering the recommendation engine, quiz ViewModel/repository, player ViewModel and URL sanitiser, Room migration SQL, account/collection isolation, search, comparison, auth, and end-to-end 'user journey' flows using fakes. They run without a phone."
**Coverage map:** Engine `RecommendationEngineTest` (14), `RecommendationInsightProviderTest`, `RecommendationsViewModelTest`; Quiz `QuizViewModelTest` (13), `QuizRepositoryTest` (9); Player `YouTubePlayerViewModelTest` (26), `VideoSourceValidationTest`, `FreeMovieRepositoryTest`; Migrations `RoomMigrationTest`, `RoomConfigurationAndMigrationTest`; Isolation `AccountIsolationTest`, `CollectionIsolationTest`, `FavoritesRepositoryTest`, `WatchlistRepositoryTest`; others: `SearchViewModelTest`, `ComparisonViewModelTest`, `AuthViewModelTest`, `ProfileViewModelTest`, `MovieDetailsViewModelTest`, `ProviderDestinationResolverTest`, `EndToEndUserJourneysTest` (11 flows), UI-polish regression tests.
**What they do NOT prove:**
- No real Android: DAOs are in-memory fakes (e.g. `TestFavoriteDao` in `CollectionIsolationTest`); migration test is a SQL-recording proxy. Real SQLite/Room behaviour, schema validation and process-restart persistence are unproven (README L17 says so).
- No WebView / `YouTubePlayerView` composable test; no device playback; no background/foreground behaviour.
- Firebase is faked (`FakeAuthRepository`, `FakeCloudSyncService`); **no** Firestore rules tests, no emulator tests, no real Google sign-in.
- No visual/UX checks (only 2 `androidTest` files — `ExampleInstrumentedTest`, `StreamingProviderClickTest` — which I did not open or run).
- Numbers "192 tests / 23 suites" in README are stale vs 289 / 33 today; I didn't run the suite, so I can't state a pass count for the current HEAD.
**Follow-up:** *"Do passing unit tests mean the app works?"* — "They show the logic is consistent with our expectations. Device, rendering and network behaviour are separate acceptance steps, and several are marked PENDING in our own docs."

## Q3.11 Security / packaging: API keys, .gitignore, SAFE zip script

**Spoken:** "Secrets never go in Kotlin source. The TMDB and Gemini keys are read from the git-ignored `local.properties` (or environment variables) and injected through BuildConfig. `.gitignore` also blocks keystores, `google-services.json`, `.env` files and build outputs. For submission we don't zip the folder; a Python script packages only tracked/allowed files, excludes secrets and `.git`, scans for credential patterns, verifies the archive and prints a hash."
**Code:** `.gitignore` (L11 `local.properties`, L26-30 keystores/signing, L33 `google-services.json`, L35-38 env/secrets); `local.properties.example` (placeholders); `.gitrules` §8 (secret policy) ; `tools/package_submission.py` (+ `tools/test_package_submission.py`, README says 8 Python tests); `docs/SAFE_SUBMISSION.md` (commands `--scan-only` then `--output ../MADVerse-Submission-SAFE.zip`; "Archive PASS"; "targeted credential check, not proof that every possible secret format is detectable").
**Follow-up:** *"The docs say an earlier ZIP contained a real key. What did you do?"* — SAFE_SUBMISSION.md L18: that archive wasn't available to inspect, "do not redistribute it"; **no key rotation was performed** in that pass. Say you would rotate the TMDB key.
**Don't claim:** the APK is free of the key (BuildConfig embeds it); I did not run the packaging script.

## Q3.12 Team split — derived from evidence, not guessed

**Declared plan (`.gitrules` L24-26):** Ritunjay Deo — TMDB integration, data layer, Home, Search, Details, Recommendation Engine. Shraddha — Room persistence, Favorites/Watchlist UI, Preference Quiz, Streaming discovery. Saanvi — Shared UI design system, Genre Explorer, Recommendation UI, Movie Comparison. Workflow: single `main` branch, sequential hand-overs (§3), own Git identity per developer (§5).
**Code comments agree:** `GetPersonalizedRecommendationsUseCase.kt` L17-20 (Saanvi's recommendations screen, Shraddha's quiz/QuizRepository, Ritunjay's `discoverCandidateMovies` and `RecommendationEngine`); `QuizRepositoryImpl.kt` L23, L84-85; `MovieDetailsScreen.kt` L685 (player was to be integrated by Shraddha).
**Git history today (`git shortlog -sne HEAD`):** Ritunjay Deo 49, Saanvi Chandel 39, Shraddha Chandgothia 36 (124 commits). `CONTRIBUTION_EVIDENCE.md` L7 recorded 45/28/14 at baseline `d04f62a` and says counts "are not a measure of effort".
**Per-area commit authors today (my read-only `git shortlog` per folder):** player — Saanvi 6, Shraddha 2; quiz — Saanvi 9, Shraddha 3, Ritunjay 1; recommendation engine — Ritunjay 4, Shraddha 2; Room (`core/database`) — Ritunjay 2, Saanvi 1; library — Saanvi 3, Shraddha 2, Ritunjay 1; auth+profile — Ritunjay 2; `FirestoreSyncServiceImpl`, `firestore.rules` — Ritunjay; network — Shraddha 5, Ritunjay 3; Free Movies — Shraddha 2, Saanvi 1.
**What this means:** the **declared** split (`.gitrules`) and **commit authorship** don't line up one-to-one — e.g. most quiz and player commits are Saanvi's, Room and Firebase commits are Ritunjay's. `CONTRIBUTION_EVIDENCE.md` explains that the 2026-10-21 corrective pass used Shraddha's configured Git identity and that "Git metadata alone cannot prove who physically executed a prompt".
**Safe answer:** "The agreed responsibilities are in `.gitrules`; the actual commit history shows overlapping contributions. For any file, I'd point to `git log -- <path>` rather than guess who wrote which line."
**Don't claim:** exact ownership beyond what you personally wrote; that `2000shra.md` is verified (see Q4.2 #9; it is an untracked file whose claims are unverified).

## Q3.13 Compose UI / design system (brief)

**Spoken:** "A shared dark 'Obsidian/Cinematic' theme and reusable components (movie card, rating badge, empty/error/loading states, top/bottom bars) live in `core/designsystem`. Screens use `collectAsStateWithLifecycle` so state collection stops in the background."
**Code:** `M/core/designsystem/theme/*`, `components/*`; `YouTubePlayerScreen.kt` L78 uses `collectAsStateWithLifecycle`; tests `DesignSystemComponentsTest`, `UiPolishRegressionTest`. Docs: `UI_UX_POLISH_PASS.md`, `LOW_END_PERFORMANCE.md` (I did not open these two).
**Don't claim:** accessibility audit completed (DEMO_AND_VIVA.md lists accessibility/layout checks as still needed).

---

# SECTION 4 — EXTRAS

## Q4.1 One-page cheat sheet

**Architecture in 5 lines**
1. Single `app` module, Kotlin + Compose + Material 3; MVVM — Screen ↔ `@HiltViewModel` ↔ `StateFlow<UiState>`.
2. Repositories (interfaces bound by Hilt) hide TMDB (Retrofit/OkHttp/Gson, `AuthInterceptor` adds `api_key`) and Room (v2, per-user rows).
3. DTO → domain model (`toDomain`) → `Resource` (data) → `UiState` (UI).
4. Firebase Auth + Firestore sync mirrors Room per `uid`; guest = `"guest"`; failed pushes queue in `pending_sync_actions`.
5. Recommendation scoring is a local deterministic engine; no AI service is called.

**YouTube flow in 6 lines**
1. `fetchVideos` → TMDB `/movie/{id}/videos` → keep YouTube Trailer/Teaser with valid 11-char ID, official first.
2. Tap → `MovieDetailsScreen.onOpenVideo` → `Screen.YouTubePlayer.createRoute(id, title, category)` (ID validated, title encoded once).
3. NavHost → `YouTubePlayerScreen` / `YouTubePlayerViewModel` (reads `SavedStateHandle`, validates, builds state; catalogue route uses `catalogueId`).
4. `YouTubePlayerView` = `AndroidView` + XML view with auto-init off, IFrame options `controls=1, autoplay=0, fullscreen=0`, `cueVideo` on ready.
5. Events tagged by `attempt`; `key(videoId, attempt, owner)` rebuilds; 30 s foreground timeout; errors → `PlayerFailure`; lifecycle observer + idempotent release.
6. Fallback: "Open in YouTube" (YouTube app Intent → browser Intent → visible error). **Device validation: PENDING.**

**Scoring formula in 3 lines**
1. `Score = Genre(30) + Mood(20) + Era(15) + Quality(15) + Language(10) + Runtime(10)`, clamped 0–100; Genre/Mood = overlap fraction × weight (rounded), Era/Language/Runtime = all-or-nothing, Quality = rating/10 × 15 (0 if below your minimum).
2. Neutral: genre ANY 15, mood ANY 10; era/language/runtime none-or-missing → full marks.
3. Sort by score → rating → votes → popularity → lowest ID; dedupe by ID; reasons come from factors that scored.

---

## Q4.2 The 10 hardest questions an examiner could ask — with safe answers

**1. "Do you use AI?"** — No. Local deterministic formula; no ML; no external call. A Gemini SDK/key field exists but nothing in the code uses it (it was a reserved extension). *Evidence:* `app/build.gradle.kts` L22-24/99-100, grep for `GenerativeModel` returns nothing.

**2. "Does your app actually play YouTube videos?"** — "The player code is implemented and its ViewModel logic is unit-tested, but the on-device acceptance checklist in `docs/DEMO_AND_VIVA.md` is still PENDING, so I won't claim verified playback." (Q1.12)

**3. "If I pick Action, Sci-Fi and Thriller, what do I get?"** — Only movies tagged with **all three**, from page 1 of TMDB sorted by popularity (comma = AND), up to ~20; maybe an empty-state message. Limitation known; OR (`|`) would be a future improvement. (Q2.7)

**4. "Does the runtime question change my recommendations?"** — In the engine yes, in the live app no: candidates come from `/discover`, which doesn't give runtime, so `runtimeMinutes` is null and every candidate gets full runtime points. (Section 0 #7)

**5. "Why does neutral mood give 10 but neutral era give 15?"** — Design inconsistency: genre/mood are affinity signals (neutral = half), era/language/runtime are constraints (no constraint = full). Docs say only "neutral fallback", no stated rationale; this explanation is mine. (Q2.4)

**6. "Is the 87% a probability I'll like the movie?"** — No; it is a weighted match score of stated preferences vs. TMDB metadata. Deterministic, uncalibrated. (Q2.5)

**7. "Is your free-movie catalogue legally cleared?"** — Evidence is recorded (publisher license pages, dates 2026-10-14), but the latest pass could not re-fetch sources (0/4 chains re-verified live), Star Wreck is CC BY-NC-ND (non-commercial, unmodified, academic only), and regional availability in India is PENDING. Not legal advice. (Q1.11)

**8. "`buildEmbedUrl` — where is it used?"** — Nowhere in production code; it's only exercised by a unit test. (Q1.9) Likewise the `YouTubePlayerState` sealed interface is unused.

**9. "Your docs say `2000shra.md` is absent — is it?"** — It exists in the repo root, **untracked** (not in Git history). `README` L25 and `CONTRIBUTION_EVIDENCE.md` L11 say it's absent from the checkout, which is no longer true. It is a contributor delivery report for Shraddha (`@2000shra`); its claims (e.g. "28 commits, +7,443 lines") are **not verified** by me. (Section 0 #10; I only read its first 25 lines.)

**10. "Is your data sync safe when offline?"** — "Writes go to Room first, so the UI works offline. Failed cloud pushes are queued and replayed at next login or on the Profile 'sync' button — not automatically on reconnect. Remote snapshots overwrite local state, so there is a potential race where an offline-added item could be dropped; not tested on a real Firebase project; `pending_sync_actions` replay is only unit-tested with fakes." (Q3.4 limits 2-4)

*Bonus tricky ones:* (a) "Are the unit tests passing?" → "README says yes at 2026-10-21 (192 tests); today's tree has more tests (~289) and I haven't re-run them." (b) "Room migration tested?" → SQL-recording proxy only; no real DB upgrade test; `exportSchema=false`. (c) "Who wrote the quiz?" → see Q3.12; use `git log`.

---

## Q4.3 3-minute demo script (follows the real screens)

> Run the playback beats **only if you have personally verified them on your device that day**; otherwise say "playback acceptance is pending" and skip beat 8.

| Time | Screen (route) | What you do / say |
|---|---|---|
| 0:00–0:15 | **Discover** (`discover`, bottom tab 1) | "Trending / top-rated / upcoming from TMDB. Data flows DTO → domain → StateFlow → Compose." |
| 0:15–0:35 | **Search** (tab 2) | Type a title slowly. "400 ms debounce; older responses are ignored." Open a result. |
| 0:35–1:05 | **Movie Details** (`movie_details/{movieId}`) | Point out cast, **region chip (India default)** → switch to US to show stale-safe provider reload. Tap **Favorite** and **Watchlist**. |
| 1:05–1:20 | **Trailer card** → **YouTube Player** (`youtube_player/...`) | Only if verified: show Ready state, tap YouTube's Play. Mention `cueVideo`, Retry, "Open in YouTube". |
| 1:20–1:35 | **Library** (tab 4, default Watchlist tab) | Show both lists are separate; optionally relaunch to show persistence (say "Room"). |
| 1:35–2:10 | **Quiz** (tab 3) | 6 steps; Next disabled until a genre is chosen; confirm dialog → saves profile → **Recommendations** (`recommendations`). Read one reason line and explain the 30/20/15/15/10/10 formula. Mention honestly: AND-genre pool, no AI. |
| 2:10–2:25 | Top-bar ⋮ menu → **Genre Explorer** (`genres`) | Genre + years + rating filters, pagination. |
| 2:25–2:40 | ⋮ → **Compare movies** (`comparison?movieId=…`) or "Compare" from a recommendation | Pick 2–3 movies; replace one; "selection survives rotation via SavedStateHandle". |
| 2:40–2:55 | ⋮ → **Free Movies** (`free_movies`) | "Curated, rights-labelled catalogue: 1 feature + 3 shorts." Open an entry (only if verified: Watch in App). |
| 2:55–3:00 | Top-bar person icon → **Profile / Auth** | "Guest by default; email/Google sign-in, Firestore sync, guest-merge dialog." |

---

## UNVERIFIED (claims I could not confirm from this repo)

1. Any statement that tests currently pass — I did not run Gradle/tests; README/docs numbers (192 tests / 23 suites; 0 lint errors; assemble OK) are quoted, and today's counts (33 classes, ~289 `@Test`) are my text-scan counts, not run results.
2. Android/YouTube library behaviour: what `android-youtube-player` 13.0.0 does inside `onStateChanged` (pause on STOP), what its `initialize(..., videoId)` 4th argument does, and the order in which Compose releases an old `key` group vs. creates the new one. (Library source not read.)
3. The numeric YouTube error codes (100, 101/150, 153, 5, 2): taken from the enum names/`PlayerFailure` messages plus general YouTube IFrame API knowledge; the app never reads raw numbers.
4. TMDB behaviour: comma = AND for `with_genres` (confirmed by one web search, not by repo); 20 results per page; whether `/discover` omits `runtime`.
5. Firestore SDK behaviour offline (write `Task` completes only after server ack) and the possible sync race in Q3.4 — inferences from code reading, not tested.
6. Whether Room's schema validation accepts the migrated tables on a real 1→2 upgrade (no instrumented migration test).
7. Contents of files I did not open: `DispatcherModule.kt`, `LibraryScreen.kt`/`FavoritesScreen.kt`/`WatchlistScreen.kt`, `ComparisonScreen.kt`, `StreamingProvidersSection.kt`, `androidTest/StreamingProviderClickTest.kt`, `tools/package_submission.py` internals, `docs/UI_UX_POLISH_PASS.md`, `docs/LOW_END_PERFORMANCE.md`, `docs/LOGIC_DEBUG_PASS.md`, `docs/S2-S5-HANDOVER.md`; most of `2000shra.md` and `ARCHITECTURE.md`.
8. Whether a local `app/google-services.json` exists (gitignored); Google sign-in requires it and the `default_web_client_id` resource it generates.
9. Real-device behaviour of anything: playback, lifecycle, persistence across restarts, regional availability, accessibility.


---

## Verification log

Re-checked by opening the files:
1. DeterministicRecommendationEngine.kt L242-247: weights 30/20/15/15/10/10 confirmed.
2. SearchViewModel.kt L44: debounceDurationMillis = 400L confirmed; delay at L71.
3. YouTubePlayerScreen.kt L85: delay(30_000) confirmed.
4. YouTubePlayerView.kt L85: cueVideo(videoId, 0f) confirmed (doc may say ~L80; treat as approximate).
5. DeterministicRecommendationEngine.kt L141 and L173: Era CONTEMPORARY >= 2015; quality popularity>=100 or voteCount>=10000 confirmed.

Line numbers are approximate; function names are the reliable anchors.

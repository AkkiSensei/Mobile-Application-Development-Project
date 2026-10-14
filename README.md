# MADVerse

Android movie discovery, preference quiz, explainable recommendations, saved library, comparison, trailers and a curated Free Movies catalogue. Kotlin, Jetpack Compose/Material 3, Hilt, Retrofit/OkHttp, Room and Navigation Compose underpin the existing feature-based architecture.

## Run and validate

1. Open this repository in Android Studio. Install Android SDK 37; minimum supported device API is 24.
2. Copy `local.properties.example` to ignored `local.properties`, configure the SDK path and your own TMDB API key. Never commit credentials. Gemini configuration is reserved; recommendations currently run locally without an external AI service.
3. Use Android Studio's bundled JDK and the committed Gradle wrapper (9.6). The project uses AGP 9.4.1 and Java source/target 17.
4. Run `gradlew.bat testDebugUnitTest assembleDebug` and `gradlew.bat lintDebug --max-workers=1` on Windows (use `./gradlew` elsewhere).
5. Install `app/build/outputs/apk/debug/app-debug.apk` on a device later. Open the menu's **Free Movies** entry for the independently curated catalogue; open a TMDB movie's Details for promotional trailers.

The player uses android-youtube-player 13.0.0, cues videos for explicit user playback, preserves YouTube controls and offers an external fallback. It does not download, rehost, bypass embedding restrictions or spoof client identity. Fullscreen mode is not implemented. Only repository-approved catalogue IDs authorize complete films; titles and navigation flags cannot grant permission.

## Acceptance status

**PARTIALLY READY — device playback acceptance is PENDING.** On 2026-10-14, 180 JVM tests passed with zero failures/errors/skips, and `assembleDebug` passed. These results do not establish actual YouTube playback, Room persistence across process restarts, or device lifecycle behavior. See the [integration report](docs/FINAL_INTEGRATION_REPORT.md), [requirements traceability](docs/REQUIREMENTS_TRACEABILITY.md) and [manual demo checklist](docs/DEMO_AND_VIVA.md).

The catalogue contains one complete feature and three complete short films, explicitly distinguished in the UI. Publisher/rights metadata was checked on 2026-10-14; embedded playback and regional availability in India remain pending. [Catalogue evidence and restrictions](docs/FREE_MOVIE_CATALOGUE.md) includes the feature's noncommercial license restriction. This academic debug build is not a signed, store-ready release.

Recommendations score genre/mood/era/quality/language/runtime with weights 30/20/15/15/10/10 and show factor-based reasons. Details offers regional subscription/rent/buy information, defaulting to India; provider availability is a guide, not a viewing entitlement. This product uses the TMDB API but is not endorsed or certified by TMDB. Existing in-app attribution is retained; film attribution and licenses are listed separately in the catalogue.

## Contributions and Git

Preserve the existing team history and follow `.gitrules`: sequential work on `main`, meaningful Conventional Commits, no history rewrites, no secrets and no generated artifacts. Team ownership is recorded there; this integration does not reattribute earlier work. Requested reference file `2000shra.md` was not present, so no claims are made from it.

# MADVerse

Android movie discovery, preference quiz, explainable recommendations, saved library, comparison, trailers and a curated Free Movies catalogue. Kotlin, Jetpack Compose/Material 3, Hilt, Retrofit/OkHttp, Room and Navigation Compose underpin the existing feature-based architecture.

## Run and validate

1. Open this repository in Android Studio. Install Android SDK 37; minimum supported device API is 24.
2. Copy `local.properties.example` to ignored `local.properties`, configure the SDK path and your own TMDB API key. Never commit credentials. Gemini configuration is reserved; recommendations currently run locally without an external AI service.
3. Use Android Studio's bundled JDK and the committed Gradle wrapper (9.6). The project uses AGP 9.4.1 and Java source/target 17.
4. Run `gradlew.bat testDebugUnitTest assembleDebug` and `gradlew.bat lintDebug --max-workers=1` on Windows (use `./gradlew` elsewhere).
5. Install `app/build/outputs/apk/debug/app-debug.apk` on a device later. Open the menu's **Free Movies** entry for the independently curated catalogue; open a TMDB movie's Details for promotional trailers.

The player uses android-youtube-player 13.0.0, cues videos for explicit user playback, preserves YouTube controls and offers an external fallback. It does not download, rehost, bypass embedding restrictions or spoof client identity. Fullscreen mode is not implemented. Catalogue IDs resolve repository-held format and authorization. Authorized shorts remain playable with both full-length flags false; unknown authorization and non-YouTube sources are restricted. Titles and navigation flags cannot grant permission.

## Acceptance status

**PARTIALLY READY — device playback acceptance is PENDING.** On 2026-10-21, **192 JVM tests in 23 suites passed**, with zero failures/errors/skips (180-test pre-change baseline; 12 new regression cases). `assembleDebug` and `compileDebugAndroidTestKotlin` passed. `lintDebug --max-workers=1` passed with **0 errors and 38 existing warnings**. Separately, all **8 Python packaging safety tests** passed. These results do not establish actual YouTube playback, Room persistence across process restarts, or device lifecycle behavior. See the [integration report](docs/FINAL_INTEGRATION_REPORT.md), [requirements traceability](docs/REQUIREMENTS_TRACEABILITY.md) and [manual demo checklist](docs/DEMO_AND_VIVA.md).

The catalogue contains one complete feature and three complete short films, explicitly distinguished in the UI. Historical publisher/rights metadata is dated 2026-10-14. This pass reviewed all four records; live retrieval limits prevented full re-verification of all four exact upload/rights chains. Embedded playback and regional availability in India remain pending. [Catalogue evidence and restrictions](docs/FREE_MOVIE_CATALOGUE.md) includes the feature's noncommercial license restriction. This academic debug build is not a signed, store-ready release.

Recommendations score genre/mood/era/quality/language/runtime with weights 30/20/15/15/10/10 and show factor-based reasons. Details offers regional subscription/rent/buy information, defaulting to India; provider availability is a guide, not a viewing entitlement. This product uses the TMDB API but is not endorsed or certified by TMDB. Existing in-app attribution is retained; film attribution and licenses are listed separately in the catalogue.

## Contributions and Git

Preserve the existing team history and follow `.gitrules`: sequential work on `main`, meaningful Conventional Commits, no history rewrites, no secrets and no generated artifacts. Team ownership is recorded there; this integration does not reattribute earlier work. The corrective brief reports `2000shra.md` in a delivered artifact, but it is absent from this inspected checkout and its available Git path history. The separate artifact/report was unavailable here, so its claims remain unverified; no report was deleted or invented. See [contribution evidence](docs/CONTRIBUTION_EVIDENCE.md). This pass uses the existing Shraddha Chandgothia identity associated with GitHub `shraddhachand`; Git author metadata and the person executing a prompt are distinct evidence.

## Credential-safe submission

Do not ZIP the working directory directly. After the Gradle gates, run `python -m unittest discover -s tools -p 'test_*.py' -v`, then `python tools/package_submission.py --scan-only` and `python tools/package_submission.py --output ../MADVerse-Submission-SAFE.zip`. Use a new output name if that path exists. [Packaging details](docs/SAFE_SUBMISSION.md).

The source archive keeps source/resources/tests, build scripts, the Gradle wrapper JAR, documentation and placeholder configuration. It excludes local.properties, .git, caches, IDE state, build outputs and signing/secret files; contents and CRC are checked before `Archive PASS`. The active local.properties stays ignored/untracked and unchanged. A source ZIP has no commit history, so use the genuine repository to verify authorship. The task handover records the created archive path and digest.

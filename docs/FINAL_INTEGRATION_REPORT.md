# Final corrective integration report — 2026-10-21

## A. Summary

**PARTIALLY READY.** The confirmed format-classification defect is corrected and all available automated gates pass. No Android device/emulator was available or used. Trailer playback, authorized full-length playback, real Room persistence, device lifecycle and external fallback remain **PENDING**. This is an academic debug source submission prepared for device acceptance, not a fully verified playback demo or signed store release.

## B. Classification correction

Previously, the trusted catalogue branch in `YouTubePlayerViewModel` assigned both full-length flags true for every film, incorrectly including three shorts. It now derives `isFullLengthMovie` from `FilmFormat.FEATURE_FILM` and `isAuthorizedFullLength` from feature format plus the entry's explicit authorization. Shorts keep both flags false and remain eligible. Unknown authorization is restricted; non-YouTube sources have no embedded player or YouTube fallback. Unknown IDs never fall back to injected video IDs/titles/flags. Invalid IDs are rejected by repository validation.

Changed source: `core/model/FreeMovie.kt`, `core/data/repository/FreeMovieRepository.kt`, `feature/player/presentation/YouTubePlayerViewModel.kt`, and `feature/freemovies/presentation/FreeMoviesScreen.kt`. The existing repository remains the single catalogue, with defensive snapshots and a package-internal fixture constructor. Its injected production constructor still supplies the same four entries and historical rights claims. The UI disables unavailable playback and explains the restriction.

Added **12 regression cases** across `FreeMovieRepositoryTest` and `YouTubePlayerViewModelTest`: the feature, each of the three shorts, unknown rights for both formats despite a Free Movie title, non-YouTube catalogue source, exact IDs through every catalogue route, ignored injected authorization flags, promotional ID preservation, the exact four formats, invalid repository input and catalogue mutation protection. Existing tests also cover missing sites, invalid IDs/URLs, arbitrary full-film metadata, retries, stale callbacks and error mapping. All pass.

## C. Documentation and contribution integrity

The brief's assertion that delivered `2000shra.md` exists cannot be established from this checkout: it is absent from the filesystem/tracked inventory, and its Git path history is empty. The separately delivered artifact was not supplied here. README and this report now distinguish checkout evidence from unverified artifact claims. No contributor report was deleted, renamed or fabricated. [Contribution evidence](CONTRIBUTION_EVIDENCE.md) records this limitation and verified Git metadata.

README, architecture notes, catalogue evidence, requirements matrix and demo guide now distinguish current results, historical results and unperformed acceptance. Historical design plans remain labeled in ARCHITECTURE.md; its earlier proposed formulas are not the active recommendation algorithm. DEMO_AND_VIVA.md describes the actual six-factor 30/20/15/15/10/10 scoring, neutral defaults, thresholds and tie-breaks.

Baseline Git metadata records 45 Ritunjay, 28 Saanvi and 14 Shraddha commits. These counts are historical metadata, not proof of who operated a computer or how much effort each person contributed. Existing history is preserved. The configured identity is Shraddha Chandgothia <shrachandgothia@gmail.com>; the GitHub API associates baseline `d04f62a` with `shraddhachand` as author and committer. The user identifies Shraddha as this pass's operator. No identity settings or old commits were changed.

## D. Security

`local.properties` is **ignored and untracked**; `.gitignore` excludes it, signing files and secrets. The active configuration was retained without modification, printing or staging. `local.properties.example` contains placeholder keys and a commented SDK-path template only. Gradle still reads local properties/environment and injects BuildConfig. Authenticated URL logging remains disabled; no TLS/WebView weakening, spoofed Referer, download/rehosting or embedding bypass was introduced.

The source inventory scan found **no detected credentials** across tracked and non-ignored project files. It compares known local key values in memory and checks token/private-key patterns and suspicious assignments, reporting only paths/descriptions. A BuildConfig variable reference was correctly distinguished from a credential literal and covered by a regression test. The scan has finite pattern coverage; it is not a claim to detect every unknown secret format. Generated BuildConfig, APKs and caches can contain developer credentials and are excluded from the source artifact. No external scanning service, key rotation or invalidation was used.

The brief reports that an earlier archive contained local.properties with a populated key. That archive was unavailable for independent inspection and is not endorsed for sharing. Use the new validated source ZIP, not that earlier artifact.

## E. Free-movie evidence

| Film / YouTube ID | Format | Publisher | Recorded authorization | Embedded playback |
|---|---|---|---|---|
| Star Wreck: In the Pirkinning / RHAPXlVq5lk | Feature, about 103 minutes | Energia Productions (@emperorpirk) | Publisher CC BY-NC-ND; noncommercial/unmodified presentation | PENDING |
| Big Buck Bunny / aqz-KE-bpKQ | Short | Blender (@BlenderOfficial) | Publisher CC BY 3.0 | PENDING |
| Sintel / eRsGyueVLvQ | Short | Blender (@BlenderOfficial) | Publisher CC BY 3.0 | PENDING |
| Tears of Steel / R6MlUcmOul8 | Short | Blender (@BlenderOfficial) | Publisher CC BY 3.0 | PENDING |

All **4 historical evidence records** were reviewed and retained with verification date **2026-10-14**. **0 of 4 exact upload/publisher/license chains were fully reverified live in this pass** because retrieval failed; indexed publisher content partially corroborated Sintel. This is not a new authorization promotion. The [catalogue evidence](FREE_MOVIE_CATALOGUE.md) preserves source/license URLs, publisher declarations, earlier metadata observations and current retrieval limitations. Exact-upload rights/availability rechecks and India embedded access remain unresolved; syntactic ID validation is not proof of authorization. No arbitrary search results were added. Device playback completed: **0 of 4**.

## F. Automated validation

Commands used Windows PowerShell and Android Studio's bundled JDK. All listed tasks completed and returned exit code **0**:

| Command | Actual result |
|---|---|
| `.\gradlew.bat testDebugUnitTest --console=plain` (pre-change) | BUILD SUCCESSFUL; test task UP-TO-DATE, existing XML baseline 180 tests / 23 suites / 0 failures, errors or skips |
| `.\gradlew.bat testDebugUnitTest --console=plain` (after source/tests) | BUILD SUCCESSFUL in 43s; test task executed; XML **192 tests, 192 passed, 0 failures, 0 errors, 0 skipped, 23 suites** |
| `.\gradlew.bat assembleDebug --console=plain` | BUILD SUCCESSFUL in 12s; debug APK present |
| `.\gradlew.bat lintDebug --max-workers=1 --console=plain` | BUILD SUCCESSFUL in 1m 28s; XML **0 errors, 38 warnings** |
| `.\gradlew.bat compileDebugAndroidTestKotlin --console=plain` | BUILD SUCCESSFUL in 9s; instrumentation Kotlin compilation only |
| `python -m unittest discover -s tools -p 'test_*.py' -v` | **8 passed**; independent of JVM count |
| `python tools/package_submission.py --scan-only` | PASS; no detected source credentials; local.properties ignored/untracked |

No JVM regressions were observed; the count increased by 12. Lint warning categories match the existing baseline: DefaultLocale 5, GradleDependency 5, InflateParams 1, ModifierParameter 6, NewerVersionAvailable 7, RedundantLabel 1, UnusedResources 7, UseKtx 6. No warning was suppressed. The existing InflateParams warning is the player XML inflated without a parent; Compose supplies its layout sizing. The current tooling also emits the existing Java 25/Kotlin target-fallback warning; application Java source/target remains 17. These are not claimed fixed.

The 2026-10-14 integration historically reported 180 passing JVM tests, build success and 38 lint warnings. Its earlier fixes disabled unsafe request logging, upgraded the player to 13.0.0, repaired initialization/lifecycle/error handling, added catalogue routes and changed audience-score wording. This pass retained **13.0.0**, verified in the actual version catalogue, and did not change that dependency. Historical outcomes do not substitute for the current run above.

## G. Manual device validation — to be completed later

All **16 checklist rows remain PENDING**, with exact steps, expected and observed-result columns in [DEMO_AND_VIVA.md](DEMO_AND_VIVA.md). They cover launch/discovery; Search/Details; initialization; actual user-initiated trailer playback; pause/resume/back; background/foreground; embedding-disabled errors; correct external fallback; the complete Star Wreck feature; each eligible catalogue entry; all three short labels; quiz/profile/recommendations; Favorites/Watchlist restart persistence; genres/comparison/regions; and navigation/attribution integration.

No actual native playback, Android lifecycle or intent launch was observed. Instrumentation was compiled but not executed. Required TMDB text remains wired into Discover, Details, Genres, Recommendations and Comparison: "This product uses the TMDB API but is not endorsed or certified by TMDB." Source inspection confirms the text and calls; physical visibility/accessibility is PENDING.

## H. Git and release procedure

Persistent branch: **main**. Origin remains `https://github.com/AkkiSensei/Mobile-Application-Development-Project.git`. Baseline: `d04f62aea34e5b072e8580464d0011283e84be3d`; initial working tree clean and fetch/fast-forward already up to date. Commit identity: **Shraddha Chandgothia <shrachandgothia@gmail.com>**. The requested focused Conventional microcommits follow completed validation and diff/security review. No history rewrite, branch creation, force-push or contributor-file deletion is part of this pass.

Commit hashes, final working-tree state and actual push outcome are recorded in the task handover after committing/pushing, rather than pre-claimed inside a document committed before those operations. Verify `git log d04f62a..HEAD` in the genuine checkout. The source ZIP omits `.git` and cannot independently establish commit-level authorship.

## I. Sanitized source archive

[SAFE_SUBMISSION.md](SAFE_SUBMISSION.md) and `tools/package_submission.py` define the packaging/verification process. The requested destination is `../MADVerse-Submission-SAFE.zip` outside the repository; an existing file is never overwritten. The handover records the actual absolute output path, SHA-256 and verification outcome after final packaging.

Kept: Kotlin, resources, manifest, Gradle scripts/version catalogue, wrapper scripts/properties/**JAR**, tests, README, .gitignore/.gitrules, placeholder example and necessary project/attribution/licensing documentation. Excluded: local.properties, .git, caches, build outputs, IDE workspace state, API-key/signing files, keystores, private keys, logs and temporary artifacts. ZIP verification checks exact members, CRC readability, byte equality and rescans archived contents for credentials. A ZIP is shareable only after the command prints **Archive PASS**. It is never added to Git.

## J. Final decision

1. **Source-level correction complete? Yes.** Classification and authorization/source regressions are covered and passing.
2. **Automated checks passing? Yes.** 192 JVM tests, 8 packaging tests, debug assembly, lint evaluation and instrumentation compilation passed; 38 existing lint warnings remain.
3. **Archive safe to share? Only the newly generated ZIP after Archive PASS.** The final task handover supplies its verified path/digest; the earlier reported credential-bearing ZIP is not cleared.
4. **YouTube playback actually verified? No — PENDING.**
5. **Authorized full-length in-app playback actually verified? No — PENDING.**
6. **Ready for a demo? Partially ready.** Prepared for a supervised device acceptance session; uninterrupted playback and end-to-end feature demonstration are not certified.
7. **Remaining prerequisites?** A device/emulator for manual acceptance; accessible publisher/upload evidence for a fresh exact-chain recheck; the original 2000shra.md/delivered artifact to verify its claims. Production signing, privacy/store review and rights review remain separate release work.

## K. Subsequent UI/UX & Reliability Pass (2026-10-22)

A comprehensive, non-destructive UI/UX, accessibility, and reliability pass was conducted by Saanvi Chandel. Complete documentation of screen-by-screen enhancements, component accessibility semantics, preserved YouTube playback, and regression test suites is available in [UI_UX_POLISH_PASS.md](UI_UX_POLISH_PASS.md).

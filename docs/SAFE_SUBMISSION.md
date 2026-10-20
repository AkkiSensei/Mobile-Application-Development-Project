# Credential-safe source submission

Run the unit tests, debug build and lint gates in README first. Keep the developer's ignored `local.properties` in place; it is never packaged, printed, staged or modified. Use Python 3.10+ and Git from the genuine checkout:

```powershell
python tools/package_submission.py --scan-only
python tools/package_submission.py --output ../MADVerse-Submission-SAFE.zip
```

The output must be outside the repository and must not already exist. Choose a dated or numbered filename on subsequent runs. Distribute only after the command prints `Archive PASS`. If verification fails after creation, preserve the failed ZIP for investigation and do not share it.

The command uses tracked files plus non-ignored new files, checks required source/resource/test/documentation paths, and keeps the Gradle wrapper JAR. It rejects forbidden tracked files, symlinks, missing required files and outputs inside the working tree. It scans source and archived bytes for known local credentials, common token/private-key signatures and suspicious credential assignments. Only paths and safe descriptions are printed; no scanning service receives values. The example permits placeholder API keys only.

Excluded: `.git`, `.gradle`, `.kotlin`, IDE caches/configuration, build outputs, local.properties, environment files, signing/keystore/private-key files, logs, temporary dumps and binary distribution archives. Included: Kotlin, resources/manifest, build scripts/version catalogue, wrapper/scripts/JAR, tests, README, project policy, configuration example and documentation with attribution/licensing references. A source ZIP has no Git history and cannot independently establish commit authorship.

CRC readability, exact members, byte equality and a second credential scan are checked after packaging. A SHA-256 digest identifies the output. This is a targeted credential check, not a proof that every possible unknown secret format is detectable. Generated BuildConfig/APK/cache files may contain the configured developer key; they are deliberately excluded and are not source submission artifacts.

The previously supplied archive was reported to include an active TMDB key. That archive was not available for inspection in this corrective pass; do not redistribute it. No key rotation, invalidation or local configuration replacement was performed.

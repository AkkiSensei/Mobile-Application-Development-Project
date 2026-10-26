# Shraddha Prompt 01 — Reliability Repair

Date: 2026-10-26 (Asia/Calcutta). Scope: Prompt 1 only; Prompts 2–5 have not been run.

## Status and evidence boundaries

**IMPLEMENTED:** focused repairs to queue ordering, session-owned replay, reconciliation, and truthful deletion recovery. Existing Kotlin/Compose/Hilt/Room/Firebase architecture is retained. No new dependency, Room schema change, backend, or rules deployment.

**TESTED-PASSED:** full JVM suite: 348 tests, 39 suites, zero failures/errors/skips; `assembleDebug`; `compileDebugAndroidTestKotlin`; 8 Python packaging safety tests; source credential scan; `git diff --check`. Final post-review combined validation also passed; details below.

**NOT-TESTED / RUNTIME-PENDING:** instrumented Room execution, Android process death, Firebase emulator/live Auth/Firestore, actual deletion, network recovery on device, and either playback workflow. A build is not runtime acceptance or submission readiness.

**BLOCKED:** the client cannot determine whether an ambiguous Auth deletion actually happened if the original identity cannot be restored. Keep the journal and account data; obtain administrator verification. Malformed/legacy payload-less UPSERT rows intentionally pause that user's queue until reviewed; there is no automatic destructive repair.

## Baseline and Saanvi handoff review

- Working copy: `C:/MAD/Mobile-Application-Development-Project`, branch `main`.
- Initial HEAD `d0903b8`, clean tree. `.git`, `.gitrules`, README and status inspected before editing. No applicable AGENTS.md was found in the repository or checked parent directories.
- Local checkout initially lacked Saanvi's delivery. `git fetch origin`, then `git pull --ff-only origin main`, advanced the clean checkout to **3987d80**. No branch switch, history rewrite, remote change, reset, clean, or teammate-file discard occurred.
- Read `docs/handoffs/SAANVI_PROMPT_01_RELIABILITY.md` and inspected actual current implementation, including later performance changes. Historical reports remain untouched.
- The report overstates several facts: actual Room is **2.8.5**, not 2.6.1; actual database version is **3**. SharedPreferences used `apply()`, not checked synchronous commits. There is no WorkManager consumer or Robolectric dependency. The old tests predominantly exercised a separate `TestCloudSyncService`; their simulated rollback runner cannot establish actual SQLite rollback. Several report stage/symbol names do not match source.
- Already correct and retained: repository mutation-plus-queue Room transactions; immutable JSON payload storage on ordinary writers; per-user Room keys; exact queue row ID deletion in the dispatcher; explicit Firestore permission/auth classifications; non-destructive migration registration; pending deletion journal concept; separate full-film and promotional-video routes.

## Confirmed defects and implemented repairs

| Defect / reproduction in baseline | Repair and evidence |
| --- | --- |
| Queue an offline ADD, then DELETE after connectivity returns: repository directly sends/acknowledges DELETE before replaying older ADD, potentially resurrecting it. | All repository writes now only enqueue within their existing transaction and signal the worker. Single ordered dispatcher is the only upload/ack path. `RepositoryQueueRepairTest`, `SyncReliabilityRepairTest`. |
| Device clock moves backward between actions: `created_at` primary ordering can invert intent. | DAO and worker order by autoincrement `id`; timestamps remain payload metadata. No migration required. |
| Guest merge commits rows before enqueue/upload, so a crash can strand unsynchronized account rows. | Merge reads, local writes and immutable queue entries share one transaction; pending target mutations are protected; UID/deletion state rechecked before commit. Source-confirmed; actual Room guest-merge failure injection remains pending. |
| `isApplyingRemoteUpdate` makes direct push return success without sending, allowing an acknowledgement of unsent intent. | Removed this global suppression and the direct push/ack path. Snapshot writes go directly through DAOs; compatibility cloud entry points durably enqueue. |
| First retry fails while retryJob remains active, preventing another scheduled retry. | `SyncQueueWorker` loops after each bounded jittered delay, with no dependence on an unrelated user action. Tests simulate three consecutive failures and recovery. |
| Old session launch/drain and snapshot callbacks can outlive sign-out; a mutex alone does not cancel the actual Firebase await. | Each session owns a SupervisorJob, worker, and conflated channel. Stop cancels that job and listeners synchronously. UID/generation/current Auth checks protect dispatch, callbacks and acknowledgement. Firebase task creation and session transitions share a lock. |
| Missing/bad payload falls back to mutable Room state; missing local row becomes successful upload. | Strict operation decoding; no fallback. Invalid action/type/ID/payload retains the durable row and pauses the affected user's queue. Safe diagnostic gives row ID, not payload or UID. |
| Snapshot reads pending/local rows outside transaction; remote UPSERT can overwrite a pending local update. | `SyncSnapshotReconciler` reads pending/local state and writes within one Room transaction; protects all pending IDs for the relevant entity, updates unrelated rows, rechecks session before commit. |
| Cached empty snapshots can erase local data; missing cloud preference is ignored. | Only authoritative server snapshots with no pending SDK writes reconcile. Metadata-inclusive listeners deliver server confirmation. Authoritative absent preference clears only an unprotected profile. |
| Out-of-order asynchronous callbacks can apply stale data after newer snapshots or acknowledgement. | Per-collection callback sequence and remote-write revision invalidate stale work; transactional recheck rolls back stale reconciliation. Exact SDK event timing remains a runtime check. |
| Auth deleted before Firestore purge; owner rules then reject purge. Profile ignores `Result.failure`, records success and clears journal. | Coordinator checks cloud purge before Auth deletion while owner session exists; checks every Result; confirmed Auth success precedes transactional local cleanup. No journal clearing in success flow. |
| Journal `apply()` can return before persistence, and unknown stage becomes IDLE. | Checked `commit()` on IO; failure stops destructive work. Unknown stage conservatively becomes STARTED. |
| Auth deletion uses runCatching, absorbing cancellation; no target identity check. | UID-specific Auth deletion captures/checks FirebaseUser, rethrows cancellation, treats 30-second timeout as ambiguous failure, and never uses null user as evidence. |
| Sync only starts when something happens to construct SessionManager. | Application injects SessionManager so authenticated startup restores durable replay even on Discover. Hilt compilation passed; actual cold-start runtime pending. |

## Queue operation and payload contract

All documents are under `/users/{uid}`. Guest changes remain local and never enqueue. IDs are durable autoincrement Long values; only `deleteActionById(row.id)` after confirmed remote success can acknowledge a replay. Session changes/cancellation leave the row. Room primary key makes acknowledgement exact across users.

| Entity | Action | Document | Payload | Remote effect |
| --- | --- | --- | --- | --- |
| FAVORITE | UPSERT | `favorites/{positive canonical movieId}` | Immutable serialized Movie, ID must equal itemId | Merge same fields at stable document ID |
| FAVORITE | DELETE | same | null | Idempotent delete |
| WATCHLIST | UPSERT | `watchlist/{positive canonical movieId}` | Immutable serialized Movie, ID must equal itemId | Merge same fields at stable document ID |
| WATCHLIST | DELETE | same | null | Idempotent delete |
| PREFERENCE | UPSERT | `preferences/current` | Immutable UserPreferenceProfile, validated required fields/enums/rating | Merge preference fields |
| PREFERENCE | DELETE | `preferences/current` | null | Idempotent delete |

Movie JSON keeps existing field names: id, title, overview, posterUrl, backdropUrl, releaseDate, voteAverage, voteCount, genreIds, popularity, optional originalLanguage/runtimeMinutes. Preference JSON keeps selectedGenreIds, mood, era, runtimePreference, optional preferredLanguage, minRatingThreshold and updatedAtTimestamp. No payload format migration was invented. Missing required data, invalid JSON, unknown enum/action/entity, mismatched ID, or a non-null DELETE payload blocks replay. Old payload-less UPSERTs are not reconstructible evidence of historical intent; preserve them for explicit repair.

Movie uploads reuse queue `createdAt` for savedTimestamp on every attempt; preference uploads reuse the payload's updatedAtTimestamp. Replaying after remote success before acknowledgement therefore repeats the same stable document operation. Add/update/remove converges in ID order. This is at-least-once delivery, not exactly-once delivery.

Ordinary writers are FavoritesRepositoryImpl, WatchlistRepositoryImpl, QuizRepositoryImpl, plus guest merge in FirestoreSyncServiceImpl. Compatibility `push*`/`delete*` methods now enqueue and return acceptance; their retained `enqueueOnFailure` argument is source compatibility only. No production repository treats that acceptance as upload confirmation. `syncAll` requests replay and does not overwrite errors with an unrelated root-document success.

## Worker lifecycle, retry and cancellation

- One worker/channel per active UID/generation. No shared channel with competing session consumers. New-session worker starts with a drain request; newly committed actions signal it.
- Startup, same-user auth refresh, manual sync and listener errors can wake it. Transient failures and authentication unavailability retry automatically: capped exponential jitter ranges 2.5–5s, 5–10s, 10–20s, 20–40s, then 30–60s. No connectivity callback is required for recovery while Wi-Fi remains available.
- Remote await timeout is 30 seconds; timeout retains intent and retries. Cancellation propagates. Local queue read/ack exceptions retain intent and schedule another attempt rather than killing the consumer.
- PERMISSION_DENIED pauses with an access/rules diagnostic. Authentication unavailable has a distinct message and retry behavior. Neither removes intent.
- Unknown/malformed rows pause the affected user queue. The original Room row is the durable blocking record; there is **no quarantine table**, and an in-memory message is not represented as durable quarantine. No schema expansion or silent deletion was made.
- Worker is process-owned, not WorkManager. No guarantee of execution after OS termination; durable queue resumes on next process/session startup. Doze/network behavior is RUNTIME-PENDING.
- Cancelling a coroutine cancels its await, **not an already submitted Firestore SDK task**. A previously submitted A request may still finish remotely. It retains A's document path; stale callbacks cannot acknowledge or publish into B. Client cannot retract a server-accepted request.

## Reconciliation contract and limits

Pending rows for the collection protect corresponding local adds, edits and deletes, regardless of queue action order. Other IDs reconcile normally, including empty server snapshots. Preferences are protected whenever a pending preference mutation exists. Identical entities avoid redundant writes, including timestamp comparison. All decisions and mutations occur in the same Room transaction.

Listener jobs are children of the session job. UID, generation, current authenticated UID, deletion journal, per-collection callback sequence, and write revision gate application. A session/revision change before transaction completion causes rollback. Cache-only snapshots and local SDK pending-write snapshots are skipped, not interpreted as authoritative emptiness.

NOT-TESTED: real SDK metadata ordering, a server snapshot delivered just before acknowledgement, cache behavior after process recreation, and data freshness after a stale callback is discarded. Verify convergence with a later server snapshot/reconnect. No live listener ordering guarantee is claimed from fake DAO tests.

## Account deletion state truth table

New flow: STARTED -> CLOUD_PURGED -> AUTH_DELETE_REQUESTED -> REMOTE_AUTH_CONFIRMED -> ACCOUNT_DATA_CLEANED -> COMPLETED.

| Persisted stage / observation | What the client knows | Allowed recovery |
| --- | --- | --- |
| No stage / IDLE | No recorded deletion | Start only for the signed-in target UID. Persist STARTED first. |
| STARTED | Purge not confirmed; it may be partially applied | Keep local data and queue, suppress sync. Same-account authenticated retry repeats idempotent purge. |
| CLOUD_PURGED | All awaited deletes for enumerated in-scope locations succeeded | Identity remains unconfirmed; same-account retry rechecks/purges before requesting Auth deletion. |
| AUTH_DELETE_REQUESTED; timeout, cancellation, crash or null currentUser | Auth outcome ambiguous | Keep journal/local data. Sign in to same account and explicitly retry. If unavailable, administrator verification is required. Null user is never success. |
| REMOTE_AUTH_CONFIRMED | This coordinator observed both cloud purge success and Auth delete success | Resume local cleanup even if no current user. Persist confirmation in NonCancellable context once success was observed. |
| ACCOUNT_DATA_CLEANED | Confirmed remote stages and completed local transaction | Repeat local cleanup safely, mark COMPLETED. |
| COMPLETED (new flow) | This flow observed all its required stages | No further pending recovery; journal record retained. Does not prove erasure on other devices or unknown backend locations. |
| Legacy REMOTE_AUTH_DELETED / REMOTE_DATA_DELETED / LOCAL_DATA_CLEANED | Old implementation could record these without checking cloud purge | Do not promote automatically. Preserve and request administrator verification. |
| Unknown stored stage | Untrustworthy state | Treat as pending STARTED, never idle/complete. |

Purge first awaits this Firestore client's already-submitted writes (within the same 30-second timeout), since coroutine cancellation cannot retract an SDK write. Failure/timeout preserves recovery state. It then enumerates `favorites`, `watchlist`, and `preferences` via Source.SERVER, awaits every document delete, then awaits root document deletion. It is not a recursive backend purge. Cloud saved data can already be removed when Auth later requires recent login; recovery text says so. Account-local favorites/watchlist/preferences/queue are deleted in one Room transaction only after confirmed remote stages. Shared Recently Viewed history, Firestore SDK cache, external-device copies, and unknown nested/subcollections are not claimed erased.

Profile reads pending entries on opening/auth changes, shows same-account retry/administrator recovery instructions, and resumes only explicitly confirmed local cleanup. An ambiguous account is not automatically completed. No local-only cleanup action was added or described as remote deletion. Repeated deletion taps are guarded by a job. Existing legacy COMPLETED entries cannot be retroactively verified from their stored data; this pass makes no claim about their historical outcome.

## Changed files and scope

Production paths relative to `app/src/main/java/com/example/madverse/`:

- `MADVerseApp.kt`: eager SessionManager injection.
- `core/database/dao/PendingSyncDao.kt`: durable sequence ordering (including isolated fake).
- `core/data/repository/impl/{Favorites,Watchlist,Quiz}RepositoryImpl.kt`: remove bypass uploads/acks, preserve transactions and enqueue signal.
- `core/data/repository/impl/FirestoreSyncServiceImpl.kt`: session lifecycle, Firebase transport, transactional merge, listener integration, staged remote/local deletion.
- `core/data/sync/SyncQueueWorker.kt` (new): actual production queue decoder and worker, injectable remote operation/backoff for targeted JVM testing.
- `core/data/sync/SyncSnapshotReconciler.kt` (new): production reconciliation transaction logic.
- `core/data/sync/SyncPayloadSerializer.kt`: reject structurally invalid immutable payloads.
- `core/data/account/AccountDeletionCoordinator.kt` (new): checked deletion sequencing and recovery.
- `core/data/account/AccountDeletionJournal.kt`: synchronous durability, pending-entry discovery, conservative parsing/new stage names.
- `core/data/repository/{AuthRepository,CloudSyncService}.kt`: target-specific Auth deletion and separated remote purge/local cleanup contracts.
- `core/data/repository/impl/FirebaseAuthRepositoryImpl.kt`: target UID, cancellation and ambiguity behavior.
- `feature/profile/presentation/{ProfileViewModel,ProfileScreen}.kt`: checked coordinator, preserved recovery information and accurate deletion scope text.

Tests: new `SyncReliabilityRepairTest` (11), `SnapshotReliabilityRepairTest` (6), `AccountDeletionRepairTest` (10), `RepositoryQueueRepairTest` (1); new Android `RoomQueueAtomicityTest` (one test exercising six mutations). Updated `ReliabilityAndOfflineFoundationsTest` to expose DAO fakes and use corrected deletion stage, `FakeCloudSyncService` to support a real shared queue fixture, and `EndToEndUserJourneysTest` to stop manually inventing/acknowledging its offline row. The production-worker tests, not the old fake-only worker tests, establish the new worker logic evidence.

Migrations/dependencies: **none added or changed**. Room 2.8.5 with room-ktx supports the existing withTransaction implementation, confirmed by compilation. Database remains v3 with existing 1->2 and 2->3 migrations and no destructive fallback. Actual on-device migration execution remains pending.

## Commands and outcomes

All commands ran from the repository root in PowerShell. Gradle commands used:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
```

1. `git status`, `git branch --show-current`, `git log`, `git diff`, source reads: inspected actual baseline. Initial `git fetch origin` failed on sandbox `.git/FETCH_HEAD` access; elevated fetch and `git pull --ff-only origin main` succeeded.
2. `gradlew.bat testDebugUnitTest --tests '*ReliabilityAndOfflineFoundationsTest' --tests '*ProfileViewModelTest' --console=plain`: first blocked by sandbox Gradle cache lock; elevated attempt **TESTED-FAILED** at processDebugGoogleServices because google-services.json was missing. No test execution claimed for that attempt.
3. Temporary credential-free validation used ignored `build/validation.gradle` and a noncredential debug string resource. Attempt with `-x processDebugGoogleServices` **TESTED-FAILED** due Gradle generated-resource provider ordering. Using the temporary script's `onlyIf { false }` instead passed the two focused suites. This was compilation/JVM validation only, not Firebase configuration validation.
4. User added root `google-services.json`. Package match checked without printing contents; copied into ignored `app/google-services.json`. Both paths verified ignored. No credentials, console settings, or Firebase project changed.
5. Normal focused command: `gradlew.bat testDebugUnitTest --tests '*SyncReliabilityRepairTest' --tests '*SnapshotReliabilityRepairTest' --tests '*AccountDeletionRepairTest' --tests '*ReliabilityAndOfflineFoundationsTest' --tests '*ProfileViewModelTest' --console=plain`: **TESTED-PASSED**, 54 tests across five suites, zero failures/errors/skips.
6. `gradlew.bat testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin --console=plain`: first **TESTED-FAILED** on a newly introduced syntax error in retry exception handling; corrected. Next run with `--max-workers=2` built APK/Android tests, but **TESTED-FAILED** one of 348 tests (`flow5_returningAccount_scopesAllOperationsToActiveUid`): its fake assumed direct repository upload. Fixture repaired to share a queue and replay; offline test now asserts repository-created intent instead of manufacturing it.
7. `gradlew.bat testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin --console=plain --max-workers=2`: **TESTED-PASSED**, BUILD SUCCESSFUL in 52s; XML totals 348 tests / 39 suites / 0 failures / 0 errors / 0 skips. Normal Firebase task included. This preceded the final task-creation lock/callback ordering review; final rerun recorded below.
8. `python -m unittest discover -s tools -p 'test_*.py' -v`: **TESTED-PASSED**, 8 tests.
9. `python tools/package_submission.py --scan-only`: **TESTED-PASSED**, 250 source files before this report, no detected credentials, local.properties ignored/untracked.
10. `git diff --check`: **TESTED-PASSED**.
11. `gradlew.bat lintDebug --max-workers=1 --console=plain`: **TESTED-PASSED**, BUILD SUCCESSFUL in 2m 38s, 0 errors and 46 warnings. Warnings include dependency-update notices, locale/UI/resource suggestions and two UseKtx suggestions on journal edits; direct checked commit is intentional. No warning was suppressed. A pre-change lint baseline was not rerun, so the entire warning set is not asserted pre-existing.
12. Final command after task-creation locking, per-collection callback ordering, and the pending-SDK-write deletion safeguard: `gradlew.bat testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin lintDebug --console=plain --max-workers=1`: **TESTED-PASSED**, BUILD SUCCESSFUL in 2m 20s. XML confirms **348 tests / 39 suites / 0 failures / 0 errors / 0 skips**; lint **0 errors / 46 warnings**. Android instrumentation was compiled, not executed.
13. Final source scan after adding this report: **TESTED-PASSED**, 251 source files, no detected credentials.

Gradle emitted existing JDK 25/Kotlin target fallback notices and native-library strip warning. These were not suppressed. No real credential values were printed. Temporary validation files are ignored build outputs and are not part of the change. An automatic approval review temporarily failed because the account usage limit was reached; the attempted edit was not executed. Work resumed only after the user's explicit continuation.

## Pending checks, blockers and reproduction

- **RUNTIME-PENDING — actual Room rollback:** run `RoomQueueAtomicityTest` on an authorized emulator/device. It uses the real Room database and deliberately throws after queue insertion for favorite add/remove, watchlist add/remove and preference save/clear; asserts the prior entities and queue survive. It has only been compiled here.
- **RUNTIME-PENDING — Firebase queue:** use a disposable test account, go offline, add/update/remove one movie, change/clear preferences, restore connectivity without another UI action. Confirm final documents and exact row acknowledgements. Repeat with Firestore unavailable while Wi-Fi remains connected and with PERMISSION_DENIED (do not deploy rules as part of this pass).
- **RUNTIME-PENDING — session races:** hold an A write, switch to B, release callback; inspect that B sees no A data, no A row is acknowledged by stale work, and no new A task starts. Repeat A->B->A and process recreation.
- **RUNTIME-PENDING — SDK tasks:** a timeout/cancel cannot cancel already accepted Firestore writes. Test delayed completion and duplicate replay; stable IDs/payloads converge within one active device's queue. Concurrent writes from other devices have no server-side deletion lock or total order.
- **RUNTIME-PENDING — snapshots:** deliver empty and nonempty authoritative snapshots with pending additions, updates, deletes and preferences. Exercise cache-to-server metadata events and callback/ack order. Discarding a stale callback favors local safety; verify later reconnect/server snapshots restore freshness.
- **BLOCKED — ambiguous Auth deletion:** interrupt immediately after user.delete submission and restart signed out. Expected: journal remains AUTH_DELETE_REQUESTED, local account rows retained, no success callback. Successful same-account sign-in permits an explicit retry; failed sign-in/null session is not proof of deletion. If identity cannot be restored, client-only design needs administrator confirmation; no invented server repair was added.
- **BLOCKED — legacy malformed row:** create/import a payload-less UPSERT ahead of valid rows. Expected: safe queue-row-ID error and all rows retained. No automatic guessing from current Room state; owner-reviewed repair policy remains needed.
- **RUNTIME-PENDING — deletion scope:** verify every server purge result and recent-login failure on a disposable account. Cloud library may be gone while Auth remains. Unknown nested subcollections, other devices, shared history and SDK cache are outside the guarantee. Old falsely completed journals cannot prove historical erasure.
- **NOT-TESTED — lifecycle/background:** no WorkManager scheduling, force-stop persistence, Doze, actual SharedPreferences disk-failure/process-death or Android SQLite migration execution was performed.
- **RUNTIME-PENDING — media:** both professor-required in-app complete-film catalogue and details trailer/teaser workflows are preserved; no player, route, catalogue or playback code changed. Live embeddability, campus Wi-Fi and Galaxy A13 playback remain pending.

## Next-agent handoff

Shraddha Prompt 2: inspect git status and this report first; preserve these reliability changes. Check Profile recovery text on guest/authenticated states, repeat-tap behavior, loading during deletion, sync-state messages and navigation after completion. Do not describe queue acceptance as upload success or null Auth as deletion. Keep both media routes distinct. Follow with the user's remaining prompts sequentially.

Ritunjay final pass: execute real Room rollback and migration tests, Firebase emulator/disposable-account interruption matrices, restored-auth retry, listener metadata ordering, and physical playback on campus Wi-Fi/Galaxy A13. Independently confirm deletion scope with backend ownership. A compiled debug APK and passing JVM tests are not complete runtime acceptance.

## Repository delivery

One coherent local Conventional Commit records these source/test/report changes using the existing configured Shraddha Chandgothia identity, as required by .gitrules. No push was performed. Firebase configuration and all generated outputs remain excluded. The report is the handoff for Prompt 2; this is not a claim of runtime acceptance.

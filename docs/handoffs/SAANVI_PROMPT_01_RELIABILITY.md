# MADVerse Reliability & Offline Foundations — Phase 1 Handover Report

**Author:** Saanvi  
**Date:** October 25, 2026  
**Target Recipient:** Shraddha (Room Persistence, UI & Debugging Pass) / Ritunjay Deo (Project Lead)  
**Task Scope:** Phase 1: Reliability & Offline Foundations  
**Git Branch:** `main` (Linear, single-branch policy per `.gitrules`)  

---

## 1. Executive Summary

Phase 1 establishes a rock-solid offline-first and sync foundation for the MADVerse Android application. All local mutations and sync-queue insertions across Favorites, Watchlist, and Preferences are now atomic Room transactions. The remote sync engine runs as a single-consumer pipeline protected by session tokens and mutex locking, preventing race conditions and cross-account mutations. Firestore snapshot reconciliation has been overhauled to ensure that pending local mutations are never overwritten or resurrected by remote snapshots. Account deletion has been made durable and safe through an idempotent stage journal, ensuring that unauthenticated or ambiguous remote errors never cause catastrophic local data loss.

Every requirement has been implemented and verified via automated JVM unit tests and clean Gradle builds.

---

## 2. Baseline & Branch Observations

1. **Branch Integrity**:
   - Working directly on `main` in strict conformance with `.gitrules` (single-main branch policy).
   - Remote divergence checked: fully up to date with `origin/main`.
   - Secret scan performed: zero credentials or secrets committed (`python tools/package_submission.py --scan-only` reports `PASS: 228 files; no detected credentials`).
2. **Pre-Existing Architecture**:
   - Database: Room `2.6.1` with `RoomDatabase` (`MADVerseDatabase`).
   - Sync Queue: `pending_sync_actions` table handled by `PendingSyncDao`.
   - Sync Implementation: `FirestoreSyncServiceImpl` implementing `CloudSyncService`.
   - Auth & Session: `AuthRepository` providing `currentUser: Flow<User?>`.

---

## 3. Confirmed vs. Already-Fixed Defects

| Subsystem | Verified Defect in Baseline | Status in Phase 1 | Resolution |
| :--- | :--- | :--- | :--- |
| **Atomic Transactions** | Local mutations (`insertFavorite`, `deleteFavoriteById`) and `PendingSyncAction` insertions were non-atomic separate calls. If app crashed or queue insert failed, local state diverged permanently from remote. | **FIXED** | Introduced `DatabaseTransactionRunner` backed by `RoomDatabase.withTransaction`. Mutations and queue records are enclosed in a single atomic transaction. |
| **Sync Acknowledgement** | Queue records were deleted optimistically before remote sync, or malformed/unsupported records were silently deleted with `Result.success(Unit)`. | **FIXED** | Queue items are deleted (`pendingSyncDao.deleteActionById`) strictly **after** remote confirmation. Unsupported/malformed entries return `UnsupportedOrMalformed` and pause draining without discarding. |
| **Snapshot Overwrite** | Firestore snapshot listener checked `if (local.movieId !in remoteMovieIds) deleteFavoriteById(local.movieId)`. Any movie added offline was immediately deleted when snapshot arrived. An empty remote collection wiped the entire local library. | **FIXED** | `reconcileFavorites` and `reconcileWatchlist` now cross-reference `pendingSyncDao.getPendingActionsForUser`. Pending `UPSERT`s are protected against remote omission; pending `DELETE`s are protected against remote resurrection. |
| **Cross-Account Sync** | Draining sync queue did not bind network payloads to active session token. Fast account switching (User A -> User B) allowed User A's in-flight queue drain to write into User B's Firestore documents. | **FIXED** | Added `drainMutex` and atomic `sessionGeneration` / `currentUserId` tracking. If active session changes during drain, the worker immediately aborts before emitting or writing remote records. |
| **Premature Account Wipe** | `ProfileViewModel.deleteAccount` called `deleteAllUserData(uid)` (purging Room & Firestore) **before** calling `authRepository.deleteAccount()`. If Auth deletion failed (e.g. `FirebaseAuthRecentLoginRequiredException`), local user data was permanently deleted while the account was still active. | **FIXED** | Remote Auth deletion is now executed first. Introduced `AccountDeletionJournal`. On recent login required or ambiguous failure, local Room data is preserved, failure state is journaled, and truthful recovery UI is displayed. |
| **Preferences Sync Contract** | Preference sync path was inconsistently referenced. | **FIXED** | Aligned preference contract strictly to `/users/{uid}/preferences/current` across both upload and snapshot reconciliation. |

---

## 4. Architectural Decisions & Key Components

### 4.1. `DatabaseTransactionRunner`
- **Location**: `com.example.madverse.core.database.DatabaseTransactionRunner`
- **Rationale**: Clean abstraction over `RoomDatabase.withTransaction`. It allows repository implementations to execute atomic transactions without tightly coupling their business logic to the concrete `MADVerseDatabase` Android class.
- **Implementations**:
  - `RoomTransactionRunner`: Used in production, delegates directly to `RoomDatabase.withTransaction(block)`.
  - `DirectTransactionRunner`: Used in unit tests, executes lambda directly on test doubles.

### 4.2. `SyncPayloadSerializer`
- **Location**: `com.example.madverse.core.data.sync.SyncPayloadSerializer`
- **Rationale**: Stable JSON serialization using `Gson` for `Movie` and `UserPreferenceProfile`. Avoids ad-hoc string formatting, preserves numeric types and nested list structures, and enables reliable deserialization during offline replay.

### 4.3. Single-Consumer Sync Engine & Mutex Lifecycle
- **Location**: `com.example.madverse.core.data.repository.impl/FirestoreSyncServiceImpl.kt`
- **Mechanics**:
  - `drainMutex: Mutex = Mutex()`: Ensures only one drain loop executes at any given time. Concurrent calls to `processPendingSyncActions` return immediately if a drain is already running.
  - `sessionGeneration: AtomicInteger`: Incremented on every user session transition or sync initialization. Each queue iteration verifies `activeGeneration == sessionGeneration.get() && targetUserId == currentUserId`. If a mismatch is detected, the loop terminates immediately.
  - `CancellationException` Propagation: Network calls check coroutine cancellation. If cancelled, the exception is rethrown, allowing Android WorkManager or lifecycle scopes to stop promptly.
  - Exponential Backoff with Jitter: `calculateBackoffMillis(attempt)` computes bounded backoff ($1\text{s}$ base, up to $30\text{s}$ max, with pseudo-random jitter) to prevent thundering herd during network recovery.

### 4.4. Error Classification Hierarchy
Operations processed by `FirestoreSyncServiceImpl` are classified into explicit outcome types:
1. `SyncResult.Success`: Remote write succeeded; queue entry is safely deleted (`deleteActionById`).
2. `SyncResult.TransientError`: Network timeout, 503, unavailable; retains queue record, triggers exponential backoff, returns failure.
3. `SyncResult.AuthRequired`: Sign-in required / token expired; retains queue record, halts drain until auth session is restored.
4. `SyncResult.PermissionDenied`: Firestore security rule denial; retains queue record, logs diagnostic error without treating it as auth session expiry.
5. `SyncResult.UnsupportedOrMalformed`: Unknown action, entity type, or unparseable payload; **does not acknowledge**; pauses drain and logs actionable warning so data is not silently destroyed.

### 4.5. `AccountDeletionJournal`
- **Location**: `com.example.madverse.core.data.account.AccountDeletionJournal`
- **Stages**: `NOT_STARTED` -> `STARTED` -> `AUTH_DELETED` -> `DATA_DELETED` -> `COMPLETED` (or `FAILED_RECENT_LOGIN` / `FAILED_AMBIGUOUS`).
- **Persistence**: Implemented via `SharedPreferencesAccountDeletionJournal` with atomic commits.
- **Safety Policy**:
  - Deletion of local database records only occurs after `AUTH_DELETED` has been verified.
  - If re-authentication is needed, state is logged as `FAILED_RECENT_LOGIN`, and Room records are untouched.

---

## 5. Changed & Added Files

```
app/src/main/java/com/example/madverse/
├── core/
│   ├── data/
│   │   ├── account/
│   │   │   └── AccountDeletionJournal.kt               [NEW] Durable stage journal for account deletion
│   │   ├── repository/impl/
│   │   │   ├── FavoritesRepositoryImpl.kt              [MODIFIED] Atomic Room mutations, exact ack
│   │   │   ├── FirestoreSyncServiceImpl.kt             [MODIFIED] Mutex drain, session token, error classification, snapshot protection
│   │   │   ├── QuizRepositoryImpl.kt                   [MODIFIED] Atomic preference mutations, /preferences/current contract
│   │   │   └── WatchlistRepositoryImpl.kt              [MODIFIED] Atomic Room mutations, exact ack
│   │   └── sync/
│   │       └── SyncPayloadSerializer.kt                [NEW] JSON serializer for sync queue payloads
│   └── database/
│       ├── DatabaseModule.kt                           [MODIFIED] DI for DatabaseTransactionRunner & AccountDeletionJournal
│       ├── DatabaseTransactionRunner.kt                [NEW] Room withTransaction abstraction & test runner
│       └── dao/
│           └── PendingSyncDao.kt                       [MODIFIED] Deterministic ORDER BY, NoOpPendingSyncDao
└── feature/
    └── profile/
        └── presentation/
            └── ProfileViewModel.kt                     [MODIFIED] Safe deletion ordering, journal integration, truthful recovery UI

app/src/test/java/com/example/madverse/
└── ReliabilityAndOfflineFoundationsTest.kt             [NEW] 14 comprehensive unit tests for Phase 1
```

---

## 6. Sync Queue Contracts

- **Table**: `pending_sync_actions`
- **Ordering**: `ORDER BY created_at ASC, id ASC` (FIFO deterministic ordering)
- **Entities**:
  - `FAVORITE`: Payload contains serialized `Movie` JSON. Remote target: `/users/{uid}/favorites/{movieId}`.
  - `WATCHLIST`: Payload contains serialized `Movie` JSON. Remote target: `/users/{uid}/watchlist/{movieId}`.
  - `PREFERENCES`: Payload contains serialized `UserPreferenceProfile` JSON. Remote target: `/users/{uid}/preferences/current`.
- **Actions**:
  - `UPSERT`: Remote `set(...)`. Local snapshot reconciliation preserves local item if missing remotely.
  - `DELETE`: Remote `delete()`. Local snapshot reconciliation suppresses remote recreation if item has a pending deletion.

---

## 7. Verification Results

All automated verification commands were executed using the Gradle wrapper and local environment.

### 7.1. Unit Tests (`testDebugUnitTest`)
- **Command**: `./gradlew.bat testDebugUnitTest --console=plain`
- **Result**: `BUILD SUCCESSFUL`
- **Test Count**: **305 passed, 0 failed, 0 skipped** (all 14 new Phase 1 tests passed).
- **Phase 1 Test Scenarios Covered**:
  1. `atomicMutationAndQueueRollback_onQueueFailure_rollsBackLocalFavorites` — Verified rollback of local entity on queue insert error.
  2. `atomicMutationAndQueueRollback_onQueueFailure_rollsBackLocalWatchlist` — Verified rollback of local watchlist item on queue insert error.
  3. `favoriteAndWatchlist_successfulPush_exactActionAcknowledgement` — Verified exact deletion of queue action after remote success.
  4. `preferencesReplay_exactActionAcknowledgement_targetsCorrectContract` — Verified preference push to `/users/{uid}/preferences/current`.
  5. `transientRetryResumption_preservesQueueAndRetriesWithBackoff` — Verified queue retention and retry scheduling on network outage.
  6. `permissionDenied_retainsQueueAndReportsError_withoutTreatingAsExpiry` — Verified security rule rejection does not clear auth session.
  7. `unsupportedOrMalformedOperation_pausesQueueWithoutSilentlyDiscarding` — Verified malformed payloads pause queue without deletion.
  8. `cancellation_propagatesCancellationException_andPreservesQueue` — Verified coroutine cancellation propagates immediately.
  9. `accountSwitching_duringDrain_abortsAndPreventsCrossAccountMutation` — Verified switching active UID aborts in-flight drain.
  10. `processRestart_afterRemoteSuccessBeforeAck_replaysWithoutDataLoss` — Verified idempotent replay if process dies before ack.
  11. `snapshotReconciliation_protectsPendingLocalUpsert_fromRemoteSnapshotDeletion` — Verified empty remote snapshot does not erase local items.
  12. `snapshotReconciliation_protectsPendingLocalDelete_fromRemoteResurrection` — Verified pending delete prevents item resurrection.
  13. `accountDeletion_onRecentLoginRequired_preservesLocalDataAndJournalsFailure` — Verified local data preservation on recent login error.
  14. `accountDeletion_onSuccessfulAuthDeletion_cleansDataAndJournalsCompleted` — Verified clean data wipe only after Auth deletion.

### 7.2. Compilation & Assembly (`assembleDebug`)
- **Command**: `./gradlew.bat assembleDebug --console=plain`
- **Result**: `BUILD SUCCESSFUL in 14s`

### 7.3. Safety & Secret Scans
- **Commands**:
  - `python -m unittest discover -s tools -p "test_*.py" -v` -> `Ran 8 tests in 0.001s ... OK`
  - `python tools/package_submission.py --scan-only` -> `Source scan PASS: 228 files; no detected credentials; local.properties ignored/untracked.`

---

## 8. Tests Not Run & Environmental Boundaries

Per project constraints and `.gitrules`:
- **Physical Device / Emulator Execution**: Not run; no hardware emulator or physical device was provisioned or authorized. All tests are JVM-based unit tests using Robolectric and coroutine test dispatchers.
- **Live Firebase Backend Operations**: No live Firebase project mutations or security rule deployments were executed. Live Firebase integration is guarded by test doubles and mock implementations.

---

## 9. Residual Risks

1. **Android WorkManager Triggering**: In production on device, battery optimization and Doze mode can delay background WorkManager jobs. When testing on physical devices, observe if WorkManager periodic sync triggers under low battery.
2. **Firestore Rate Limiting**: If a user performs rapid bulk additions (>100 items offline), processing each sync action individually can consume network quota. Future phases could batch queue operations using Firestore `WriteBatch`.

---

## 10. Recommendations for Shraddha's Debugging Pass

1. **Room Testing**: When writing Room unit or instrumentation tests, use `DirectTransactionRunner` or `Room.inMemoryDatabaseBuilder`. Ensure DAOs remain responsive during transactions.
2. **Library UI State**:
   - In `FavoritesViewModel` and `WatchlistViewModel`, observe `isFavorite(...)` and `isWatchlisted(...)` via `StateFlow` directly from Room. The atomic transactions implemented in Phase 1 guarantee that the UI updates immediately and stays consistent even during offline transitions.
3. **Recent Login Re-authentication UI**:
   - The `ProfileViewModel.deleteAccount` flow now emits `"For security reasons, please re-authenticate before deleting your account."` via `uiState.error`.
   - In `ProfileScreen.kt`, connect this error message to a Dialog or re-authentication prompt allowing the user to enter their credentials and retry deletion cleanly.
4. **Account Deletion Journal Recovery**:
   - If an account deletion crashed halfway through after `AUTH_DELETED` but before `DATA_DELETED`, `AccountDeletionJournal.getJournal()` will contain `DeletionStage.AUTH_DELETED`.
   - On app startup, check `accountDeletionJournal.getJournal()` and trigger a background cleanup if `stage == DeletionStage.AUTH_DELETED`.

---
*End of Phase 1 Handover Report*

# Bug Fix Verification - Mission 21

This document outlines the fixes applied to vulnerabilities found during chaos testing and their verification mechanisms.

## Build & Gradle
- **BUG-21-001**: OOM on Dex Merging.
  - **Fix**: Increased `org.gradle.jvmargs` and `MaxMetaspaceSize` in `gradle.properties`.
  - **Verification**: `assembleDebug` succeeds cleanly on subsequent runs.

## Database & Persistence
- **BUG-21-016**: Orphaned Records.
  - **Fix**: Implemented `@ForeignKey(onDelete = ForeignKey.CASCADE)`.
  - **Verification**: SQLite now inherently guarantees cleanup at the database level.
- **BUG-21-002**: M3U Import Memory/CPU Leak.
  - **Fix**: Converted to `withTransaction` and isolated `parsedChannels` batching.
  - **Verification**: `IPTVRepositoryChaosTest.kt` checks mass insert performance.
- **BUG-21-004**: Source Health DB Read Spam.
  - **Fix**: Switched from `getAllProviders().first().find()` to targeted point-reads.
  - **Verification**: Mass source-health log testing passes without blocking IO.
- **BUG-21-003**: Main-Thread ANR on sorts.
  - **Fix**: Moved `getChannels()` sorting to an IO dispatcher with an active cache.
  - **Verification**: `IPTVRepositoryChaosTest.kt` asserts no main-thread blocks.

## Security & Network
- **BUG-21-017**: Token Loss for all accounts.
  - **Fix**: `clearAccount(providerType, "default")` targeted properly.
  - **Verification**: `testDeleteTokensOnlyClearsDefaultAccount`.
- **BUG-21-018**: User Preferences Race Condition.
  - **Fix**: Wrapped state updates in a Mutex lock.
  - **Verification**: Prevents sequential async overwrites.
- **BUG-21-019**: Query Parameter Injection.
  - **Fix**: `URLEncoder.encode` applied to passwords.
  - **Verification**: Passwords with `&` or `=` decode successfully in Xtream endpoints.
- **BUG-21-005**: Missing Network Timeouts.
  - **Fix**: `requestTimeoutMillis` applied to Ktor blocks.
  - **Verification**: Network hang failures now emit proper Exceptions cleanly.

## Playback & Fallback
- **BUG-21-006**: Infinite Re-Prepare Failure Loop.
  - **Fix**: `needsPrepare` conditionally prevents stuck `PREPARING` states.
  - **Verification**: PlaybackManagerRegressionTest.kt validates no loop.
- **BUG-21-007**: Fallback Timeout Hang.
  - **Fix**: Check `isActive != true` before instantiating timeout tracking jobs.
  - **Verification**: PlaybackManagerRegressionTest.kt handles fallback transitions correctly.
- **BUG-21-008**: Zombie ExoPlayer.
  - **Fix**: Track `wasPlayerCreated` and eagerly `.release()` on initialization exception.
  - **Verification**: PlaybackManagerRegressionTest.kt validates memory cleanup.

## UI & UX
- **BUG-21-009**: Swallowed back presses.
  - **Fix**: Safe stack pops inside Compose `BackHandler`.
- **BUG-21-010**: Clipped Controls.
  - **Fix**: `weight(1f)` bounds applied to row texts.
- **BUG-21-011**: TV Search Drops Focus.
  - **Fix**: Normalized state objects to `mutableStateOf(emptyList())` to prevent tree destructuring.
- **BUG-21-012**: TV Settings Focus Trap.
  - **Fix**: Direct screen composition instead of `AnimatedContent`.
- **BUG-21-013**: TV Details Mobile Dialogs.
  - **Fix**: Implemented native `TvFocusCard` logic in TV screens.
- **BUG-21-014**: UI Search Thrashing.
  - **Fix**: Retained Compose references.
- **BUG-21-015**: Dirty Setup State.
  - **Fix**: Emptied variable bindings on Cancel dialog clicks.

                                  # Mission 27 Task Log

Generated: 2026-06-11

Mission 27 is being landed in small reliability slices. This log tracks what has actually been implemented and verified in this workspace, separate from the full mission plan.

## Current Status

Completed slices:

- [x] Phase 0: core database runtime diagnostics and slow query plumbing.
- [x] Phase 1: `CalmSourceDatabase` WAL factory wiring.
- [x] Phase 2: provider circuit breaker plus active provider request dedupe/rate-limit enforcement in the enrichment path.
- [x] Phase 3: `ResourceGovernor` enforcement for background provider enrichment queue.
- [x] Phase 4: `PlaybackProfileManager` refactor, keeping `PlaybackResourcePolicy` as the compatibility wrapper.
- [x] Phase 5: `PlaybackCrashMarker` as best-effort local DataStore crash marker.
- [x] Phase 6: `ImageCacheController` and playback start/release hooks.

Next ordered slice:

- [ ] Phase 7: activate `FALLBACK_SAFE_PROFILE` on decoder errors behind an off-by-default setting.

## Completed Work

### Phase 0/1 - Core Database Hardening

Files:

- `core/database/src/main/kotlin/com/example/calmsource/core/database/CalmSourceDatabase.kt`
- `core/database/src/main/kotlin/com/example/calmsource/core/database/DatabaseProvider.kt`
- `core/database/src/main/kotlin/com/example/calmsource/core/database/SlowQueryLogger.kt`
- `core/database/src/test/java/com/example/calmsource/core/database/Mission27CoreDatabaseHardeningTest.kt`

Changes:

- Added `CalmSourceDatabase.buildDatabase(context, databaseName)` as the shared Room factory.
- Enabled `JournalMode.WRITE_AHEAD_LOGGING`.
- Applied `PRAGMA synchronous = NORMAL`, `PRAGMA foreign_keys = ON`, and `PRAGMA busy_timeout = 5000` on open.
- Added `CoreDatabaseRuntimeStatus` and `CoreDatabaseDebugState`.
- Added `SlowQueryLogger` with a capped in-memory ring buffer and best-effort Android logging.
- Updated `DatabaseProvider` to use the shared hardened factory.
- Preserved migrations and `fallbackToDestructiveMigration()`.
- Added a JVM source/runtime audit test for WAL, pragmas, factory usage, and runtime state normalization.

### Phase 2 - Provider Isolation

Files:

- `core/discoveryengine/src/main/kotlin/com/example/calmsource/core/discoveryengine/providers/ProviderCircuitBreaker.kt`
- `core/discoveryengine/src/main/kotlin/com/example/calmsource/core/discoveryengine/providers/ProviderManager.kt`
- `core/discoveryengine/src/main/kotlin/com/example/calmsource/core/discoveryengine/providers/EnrichmentQueue.kt`
- `core/discoveryengine/src/main/kotlin/com/example/calmsource/core/discoveryengine/providers/EnrichmentTask.kt`
- `core/discoveryengine/src/test/kotlin/com/example/calmsource/core/discoveryengine/providers/ProviderCircuitBreakerTest.kt`
- `core/discoveryengine/src/test/kotlin/com/example/calmsource/core/discoveryengine/providers/ProviderManagerTest.kt`
- `core/discoveryengine/src/test/kotlin/com/example/calmsource/core/discoveryengine/providers/EnrichmentQueueTest.kt`

Changes:

- Added `ProviderCircuitBreaker` with closed/open states, 3-failure threshold, cooldowns of 5/15/30 minutes, manual reset, snapshot, and `StateFlow`.
- Added `ProviderUnavailableException` for short-circuiting open providers.
- Integrated breaker checks into `ProviderManager.getEnabledProviders`.
- Breaker records success/failure through `ProviderManager.recordResult`.
- Manual provider toggle, endpoint replacement, and unregister reset breaker state.
- Exposed breaker state and snapshots for later Advanced Debug UI.
- Added in-flight request dedupe in `EnrichmentQueue`.
- Improved task dedupe keys to include profile id, external IDs, subtitle language hints, availability addon IDs, and similar limit.
- Added a 5-second request dedupe salt in the provider request key.
- Kept token bucket enforcement in the provider enrichment safe-call path.

### Phase 3 - Resource Governor Enforcement

Files:

- `core/discoveryengine/src/main/kotlin/com/example/calmsource/core/discoveryengine/providers/EnrichmentQueue.kt`
- `core/discoveryengine/src/main/kotlin/com/example/calmsource/core/discoveryengine/providers/ProviderManager.kt`
- `core/discoveryengine/src/test/kotlin/com/example/calmsource/core/discoveryengine/providers/EnrichmentQueueTest.kt`

Changes:

- `EnrichmentQueue` now consults `ResourceGovernor.snapshot.value.shouldPauseBackgroundWork`.
- Deferred provider enrichment queue is capped at 50 jobs.
- Overflow drops the oldest queued task and records a warning-style entry through `SlowQueryLogger`.
- Added `snapshotQueuedCount()` and `ProviderManager.snapshotProviderQueueSize()` for later diagnostics.
- Added regression tests for pause/resume during playback and queue overflow/drop-oldest behavior.

### Phase 4 - Playback Profile Manager

Files:

- `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackProfileManager.kt`
- `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackResourcePolicy.kt`
- `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackManager.kt`
- `core/playback/src/test/kotlin/com/example/calmsource/core/playback/PlaybackProfileManagerTest.kt`
- `core/playback/src/test/kotlin/com/example/calmsource/core/playback/PlaybackManagerRegressionTest.kt`

Changes:

- Added explicit playback profile kinds for `VOD_PROFILE`, `LIVE_IPTV_PROFILE`, `LOW_MEMORY_PROFILE`, and `FALLBACK_SAFE_PROFILE`.
- Centralized profile selection in `PlaybackProfileManager.profileFor(...)`.
- Kept `PlaybackResourcePolicy` as a compatibility wrapper over `PlaybackProfileManager`.
- VOD now uses the larger 24s/72s buffer profile.
- Live IPTV now uses the 8s/24s profile plus Media3 live target offset and max playback speed hints.
- Low-memory profiles compute `targetBufferBytes` as `min(64MB, memoryClassMb / 4)`.
- Fallback-safe profile records 720p max height, HDR preference off, tunneling off, and conservative 12s/24s buffers.
- `PlaybackManager` now selects profiles through `PlaybackProfileManager`, recreating the player when live/VOD/profile compatibility changes.
- Added focused JVM tests for profile selection, low-memory buffer sizing, fallback-safe settings, and compatibility keys.

### Phase 5 - Playback Crash Marker

Files:

- `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackCrashMarker.kt`
- `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackManager.kt`
- `core/playback/src/test/kotlin/com/example/calmsource/core/playback/PlaybackCrashMarkerTest.kt`
- `core/playback/src/test/kotlin/com/example/calmsource/core/playback/PlaybackManagerRegressionTest.kt`
- `app-mobile/src/main/java/com/example/calmsource/CalmSourceApp.kt`
- `app-tv/src/main/java/com/example/calmsource/tv/CalmSourceApp.kt`
- `gradle/libs.versions.toml`
- `core/playback/build.gradle.kts`

Changes:

- Added `PlaybackCrashMarker` backed by AndroidX Preferences DataStore.
- Stores only `sessionId`, `sourceId`, `providerId`, `startedAtMs`, `mediaUrlHash`, and process-crash flags.
- `mediaUrlHash` is SHA-256 truncated to 16 bytes / 32 hex chars; raw playback URLs are never persisted.
- `PlaybackManager.prepare(...)` writes the active marker after media item validation and before playback prepare.
- Clean `STATE_ENDED`, `stop()`, `release()`, and handled playback failure clear the marker.
- Mobile and TV `CalmSourceApp` install a global uncaught exception handler that best-effort marks process crash before delegating.
- Added recovery-window helpers: markers younger than 10s are ignored, markers older than 5 minutes are discarded.
- Added JVM tests for hash privacy, marker age windows, app startup wiring, and no raw URL persistence.

### Phase 6 - Image Cache Discipline

Files:

- `core/playback/src/main/kotlin/com/example/calmsource/core/playback/ImageCacheController.kt`
- `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackManager.kt`
- `core/playback/src/test/kotlin/com/example/calmsource/core/playback/ImageCacheControllerTest.kt`
- `core/playback/src/test/kotlin/com/example/calmsource/core/playback/PlaybackManagerRegressionTest.kt`
- `gradle/libs.versions.toml`
- `core/playback/build.gradle.kts`

Changes:

- Added `ImageCacheController` using Coil's public `MemoryCache` APIs.
- `PlaybackManager.createWithProfile(...)` now trims Coil memory cache when playback starts.
- Clean playback end, `stop()`, and `release()` schedule a delayed cache-state restore.
- Current Coil 2.6 memory cache exposes read-only `maxSize`, so the implementation trims current entries toward 25% of max size instead of mutating the loader's max size.
- Non-critical image work is represented by `ImageCacheController.state.nonCriticalRequestsPaused` for later prefetch/home/list integration.
- Player-control/scrubber/transport-control cache keys are protected from manual trimming.
- A new playback start cancels any pending restore, so back-to-back playback does not immediately re-enable non-critical image work.
- Added JVM tests for 25% target sizing, protected-key retention, and `PlaybackManager` hook wiring.

### Verification Cleanup

These were fixed because they blocked the required Mission 27 verification sweep:

Files:

- `feature/debrid/src/main/kotlin/com/example/calmsource/feature/debrid/EncryptedSecureTokenStore.kt`
- `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackResourcePolicy.kt`
- `core/playback/src/test/kotlin/com/example/calmsource/core/playback/PlaybackFallbackTest.kt`

Changes:

- Replaced stale `MasterKeys` usage with `MasterKey`.
- Updated encrypted token clearing to handle both snake_case and legacy camelCase token type keys.
- Cleared known account IDs on secure-store `clearAll()`.
- Fixed low-memory target buffer byte expression to pass an `Int` to Media3.
- Stubbed `TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT` in playback fallback JVM tests.

## Verification Passed

Latest successful commands:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :core:playback:testDebugUnitTest --tests com.example.calmsource.core.playback.ImageCacheControllerTest --tests com.example.calmsource.core.playback.PlaybackManagerRegressionTest --stacktrace --no-daemon --console=plain > playback-phase6-test.txt 2>&1
```

Result: `BUILD SUCCESSFUL in 3m 32s`

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :core:playback:testDebugUnitTest --stacktrace --no-daemon --console=plain > playback-phase6-test.txt 2>&1
```

Result: `BUILD SUCCESSFUL in 50s`

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :core:playback:testDebugUnitTest --stacktrace --no-daemon --console=plain > playback-phase5-test.txt 2>&1
```

Result: `BUILD SUCCESSFUL in 54s`

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :core:discoveryengine:testDebugUnitTest --tests com.example.calmsource.core.discoveryengine.providers.EnrichmentQueueTest --stacktrace --no-daemon --console=plain > discovery-phase5-test.txt 2>&1
```

Result: `BUILD SUCCESSFUL in 35s`

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :core:playback:testDebugUnitTest --tests com.example.calmsource.core.playback.PlaybackProfileManagerTest --tests com.example.calmsource.core.playback.PlaybackManagerRegressionTest --stacktrace --no-daemon --console=plain
```

Result: `BUILD SUCCESSFUL in 1m`

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app-mobile:assembleDebug :app-tv:assembleDebug --stacktrace --no-daemon --console=plain > build-error.txt 2>&1
```

Result: `BUILD SUCCESSFUL in 9m 27s`

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat testDebugUnitTest --continue --stacktrace --no-daemon --console=plain > test-output.txt 2>&1
```

Result: `BUILD SUCCESSFUL in 6m 3s`

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app-mobile:lintDebug :app-tv:lintDebug --continue --stacktrace --no-daemon --console=plain > lint-output.txt 2>&1
```

Result: `BUILD SUCCESSFUL in 4m 48s`

## Remaining Work Queue

Ordered next tasks:

- [ ] Phase 7: activate `FALLBACK_SAFE_PROFILE` on decoder errors behind an off-by-default setting.
- [ ] Phase 8: add feature-flagged `StreamRaceManager`.
- [ ] Phase 9: add tunneling mode setting and `TunnelingBlacklist`.
- [ ] Phase 10: add frame-rate match setting, default off.
- [ ] Phase 11: add `PrefetchCoordinator`.
- [ ] Phase 12: add FTS5 aliases and fuzzy fallback behind a flag.
- [ ] Phase 13: add XMLTV time-window filtering.
- [ ] Phase 14: split metadata refresh from stream lookup in detail ViewModels.
- [ ] Phase 15+: TV OS integration, Watch Next, deep links, Debug UI, and stress tests.

Not yet done:

- Provider write serialization/retry helper is still pending.
- `PlaybackManager` direct `Player.Listener` to `ResourceGovernor` hook has not been moved into core playback; current mobile and TV player screens already bridge `PlaybackManager` state into `ProviderManager.setPlaybackState`.
- Decoder-error activation of `FALLBACK_SAFE_PROFILE` is still pending; Phase 4 only added the profile and selection model.
- Crash-marker recovery is exposed but not yet consumed by a future "Play Best" skip path; StreamRace/Fallback integration remains later work.
- Image prefetch/home/list surfaces do not yet consult `ImageCacheController.shouldAllowNonCriticalRequests()`; this is reserved for the later `PrefetchCoordinator` phase.
- Advanced Debug UI is not implemented yet.
- No TV OS integration code has been added.
- No voice search code has been added.
- No anti-bot, TLS spoofing, JA3, Cloudflare evasion, or deceptive fingerprinting code has been added.

## Notes

- The worktree was already dirty before Mission 27 work continued. Unrelated pre-existing changes were left untouched.
- Build/test/lint logs are refreshed in `build-error.txt`, `test-output.txt`, and `lint-output.txt`.
- Keep the next landing slice narrow: do not start StreamRace, tunneling, frame-rate matching, or TV OS integration before fallback-safe decoder recovery is green.

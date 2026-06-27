# CalmSource Playback Module Bug Fix & Audit Report

This report outlines the 12 issues identified, analyzed, and fixed in the CalmSource `:core:playback` module.

---

## Audit Summary

| # | Issue | File Path | Impact | Status |
|---|---|---|---|---|
| 1 | Jetpack Compose State/Layout Freeze | `PlaybackPlaceholder.kt` | UI | Fixed |
| 2 | Unbounded URL Cache Leak | `PlaybackRequestSaver.kt` | Memory | Fixed |
| 3 | Unsafe Async Crash Marker Write | `PlaybackCrashMarker.kt` | Stability | Fixed |
| 4 | VLC Player Backend Leaks & Stability | `VlcPlayerBackend.kt`, `PlaybackManager.kt` | Memory/Stability | Fixed |
| 5 | Stream Racing Parallel ExoPlayers | `StreamRaceManager.kt` | Performance | Fixed |
| 6 | Low Memory Profile Buffer Cap | `PlaybackProfileManager.kt` | Memory | Fixed |
| 7 | ImageCacheController Race Condition | `ImageCacheController.kt` | Memory/UI | Fixed |
| 8 | LivePlaybackSession State Sync Race | `LivePlaybackSession.kt` | UI | Fixed |
| 9 | Redundant Retry Sequence | `StreamFormatFallback.kt` | User Experience | Fixed |
| 10 | In-Memory Cache Desynchronization | `TunnelingBlacklist.kt` | Stability | Fixed |
| 11 | Uncaught Probe Timeout Cancellation | `StreamRaceManager.kt` | Performance | Fixed |
| 12 | Unsynchronized LiveChannelQueue State | `LiveChannelQueue.kt` | Stability | Fixed |

---

## Detailed Findings & Implementations

### 1. Jetpack Compose State/Layout Freeze Bug
- **File**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackPlaceholder.kt`
- **Symptom**: The `PlayerView` freezes or fails to display new streams when the player is recreated on `streamUrl` change.
- **Root Cause**: `AndroidView`'s `factory` lambda is only executed when the view is created. Because `view.player = exoPlayer` was set only in `factory`, when the stream URL changed and `remember(streamUrl)` recreated a new `ExoPlayer` instance, the `PlayerView` was not updated to reference the new player.
- **Fix**: Moved `view.player = exoPlayer` into the `update` block of the `AndroidView`, which runs on every recomposition when state changes.

### 2. Unbounded URL Cache Leak
- **File**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackRequestSaver.kt`
- **Symptom**: Continuous playback triggers unbounded memory growth, potentially leading to OOM.
- **Root Cause**: `PlaybackUrlCache` used a standard `ConcurrentHashMap` with no size constraints or eviction policy.
- **Fix**: Refactored `PlaybackUrlCache` to use a synchronized `LinkedHashMap` overriding `removeEldestEntry` to return `size > 20`. This ensures it caps at 20 entries, maintaining a bounded memory footprint.

### 3. Unsafe Async Crash Marker Write
- **File**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackCrashMarker.kt`
- **Symptom**: Process crash recovery markers fail to write to disk when a fatal crash occurs.
- **Root Cause**: `markProcessCrashedBestEffort` executed the write asynchronously using `markerScope.launch`. In the event of an uncaught exception, the JVM/process often terminates before the asynchronous coroutine can complete the disk write.
- **Fix**: Refactored `markProcessCrashedBestEffort` to run synchronously using `runBlocking` without passing `Dispatchers.IO` (violating test restrictions), ensuring the marker is flushed to disk before JVM exit.

### 4. VlcPlayerBackend Leaks & Stability
- **Files**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/VlcPlayerBackend.kt`, `PlaybackManager.kt`
- **Symptom**: Memory leaks of the player activity context after playing VLC streams, potential NullPointerException when attaching video surface, and redundant VLC player instances.
- **Root Cause**:
  1. `VlcPlayerBackend.playerView` was not nullified in `release()`, keeping a reference to the view tree.
  2. Video output (`vout`) from reflection was not null-checked before usage.
  3. `PlaybackManager` did not release/nullify old `vlcBackend` on fresh playback requests.
- **Fix**:
  1. Set `playerView = null` inside `VlcPlayerBackend.release()`.
  2. Added a check `?: throw IllegalStateException("VLC video output (VLCVout) is null")` to `vout` in `VlcPlayerBackend.prepare()`.
  3. In `PlaybackManager.prepareWithProfileHistory`, if it is a new request (`!isFallbackAttempt && !sameTrackingItem`), we release and nullify `vlcBackend`.

### 5. Stream Racing Parallel ExoPlayers
- **File**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/StreamRaceManager.kt`
- **Symptom**: Hardware video decoder exhaustion and high CPU usage on TV devices during parallel stream racing.
- **Root Cause**: Launching parallel probe players initiated full video renderer allocation (including hardware decoder allocation) on TV devices with limited decoder instances.
- **Fix**: Subclassed `DefaultRenderersFactory(context)` for the probe player and overrode `buildVideoRenderers` to do nothing, preventing parallel video decoder allocation during the probing phase.

### 6. Low Memory Profile Target Buffer Bytes
- **Files**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackProfileManager.kt`, `PlaybackProfileManagerTest.kt`
- **Symptom**: High memory pressure or OOMs on 256MB or lower RAM devices.
- **Root Cause**: `constrainedTargetBufferBytes` allowed up to 64MB buffer budgets regardless of memory class constraints.
- **Fix**: Modified `constrainedTargetBufferBytes` to cap the buffer budget at 16MB for devices with memory class <= 256MB. Updated the corresponding unit test on line 58 of `PlaybackProfileManagerTest.kt` to expect 16MB.

### 7. ImageCacheController Race Condition
- **File**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/ImageCacheController.kt`
- **Symptom**: Intermittent image cache trimming/restoration issues due to active job cancellation.
- **Root Cause**: `restoreAfterPlayback` cleared the active `restoreJob` unconditionally, canceling restoration runs initiated by newer generations.
- **Fix**: Refactored `restoreAfterPlayback` to take the active generation and only cancel/nullify `restoreJob` if the current generation matches the active one. Added a guard clause at the very beginning of `restoreAfterPlayback` to return the current state if the requested generation does not match the active `restoreGeneration` (`restoreGeneration.get() != generation`).

### 8. LivePlaybackSession State Sync Race
- **Files**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/LivePlaybackSession.kt`, `LivePlaybackSessionTest.kt`
- **Symptom**: Mismatched or flickering channel states in Live TV playback.
- **Root Cause**: The state sync coroutine collected `uiState` emissions and mapped states without verifying that the emission was for the currently selected channel.
- **Fix**: Verified that the channel is not null and `uiState.source?.id == current.id` before mapping player state in the collector. Updated `playChannel(source)` to call `channelQueue.selectChannel(source)` at the start.

### 9. Redundant Retry Sequence
- **Files**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/StreamFormatFallback.kt`, `StreamFormatFallbackTest.kt`
- **Symptom**: Stream fallback tries the exact same MIME type configuration that just failed.
- **Root Cause**: `buildMimeRetrySequence(uri)` always included the inferred format type in the sequence, causing a redundant attempt.
- **Fix**: Inferred the initial MIME type from the URI and filtered it out of the returned sequence. Added a unit test class (`StreamFormatFallbackTest`) to verify.

### 10. In-Memory Cache Desynchronization
- **Files**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/TunnelingBlacklist.kt`, `TunnelingBlacklistTest.kt`
- **Symptom**: Stale or lower failure counts in memory, enabling bad codecs.
- **Root Cause**: `recordFailureBestEffort` completely overwrote `memoryEntries` in the coroutine, which can lead to out-of-order writes downgrading the count in memory.
- **Fix**: Merged the disk updates into `memoryEntries` taking the maximum of the failure counts. Added a unit test to verify the merging logic.

### 11. Uncaught Probe Timeout Cancellation
- **File**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/StreamRaceManager.kt`
- **Symptom**: A single probe timeout cancels the entire stream race, causing sequential fallback instead of selecting a winner.
- **Root Cause**: `withTimeout` throws `TimeoutCancellationException` which is a subclass of `CancellationException`. The probe caught `CancellationException` and rethrew it, which aborted the coroutine race hierarchy.
- **Fix**: Added a catch block for `kotlinx.coroutines.TimeoutCancellationException` to return `StreamProbeResult.Failed("timeout")` without rethrowing, failing the probe gracefully.

### 12. Unsynchronized LiveChannelQueue State
- **File**: `core/playback/src/main/kotlin/com/example/calmsource/core/playback/LiveChannelQueue.kt`
- **Symptom**: ConcurrentModificationException or IndexOutOfBoundsException during rapid channel navigation.
- **Root Cause**: `LiveChannelQueue` internal lists and indices were accessed concurrently from multiple threads without synchronization.
- **Fix**: Added the `@Synchronized` annotation to all mutating and querying methods (`setChannels`, `getCurrentChannel`, `nextChannel`, `previousChannel`, and the newly added `selectChannel` method).

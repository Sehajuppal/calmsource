# Real Source Playback Stabilization

This document outlines the stabilization steps and fixes implemented for real source playback.

## Media3 Playback Stabilization (SA5)

### Fallback Looping Prevention
A critical bug where the fallback logic would prompt infinitely has been fixed. This was caused by `isFallbackAllowed` incorrectly returning true even when candidate streams were empty. The system now correctly identifies when no fallback candidates remain and aborts cleanly.

### Zombie Player Mitigation
Previously, a race condition allowed zombie `ExoPlayer` instances to spin up in the background if a user rapidly exited (hit "back") on a dead stream. This occurred because asynchronous fallback jobs were executing immediately after the player session was released.
These jobs have been stabilized by:
- Properly binding jobs to the correct lifecycle scope.
- Explicitly cancelling them during the player `release()` phase.
- Gating `prepare()` and playback actions behind an `isActive` lifecycle flag.
This prevents invisible audio playback and background memory crashes.

### Testing and Prevention
- Two new explicit regression tests were introduced in `PlaybackFallbackTest.kt` to ensure these lifecycle contracts are strictly enforced.

## TV UI and Performance Flow (SA7 & SA9)

### Main Thread ANR Prevention (Verified)
A **CRITICAL ANR BUG** was identified where `TvIptvSettingsSection` and `TvPlayerScreen` called `IPTVRepository.getChannels()` synchronously inside their `@Composable` blocks on the Main thread. Given the size of typical playlists (10,000+ items), this caused massive UI freezing. 
**Resolution:** This has been successfully patched and verified. The synchronous list-sorting operations are now abstracted into background `LaunchedEffect(Dispatchers.IO)` coroutines, completely unblocking the main thread during Compose renders.

### TV UI Dead Zones and Navigation Fixes
- **`TvDetailsScreen`:** In the Advanced Stream Sources panel, UI clipping and broken D-pad scrolling occurred because 50+ elements were packed into a single lazy `item` block. Final D-pad bounds checks were implemented by unrolling into `items()`, properly restoring native D-pad navigation without clipping.
- **`TvPlayerScreen`:** When opening the live channel switcher, D-pad focus would remain trapped on the hidden player canvas. Focus correctly steals `FocusRequester` into the switcher drawer when opened.




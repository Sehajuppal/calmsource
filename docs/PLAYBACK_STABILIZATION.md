# Playback Stabilization (Mission 11.5)

This document outlines the stabilization updates made to the Media3 playback foundation.

## What Was Tested and Fixed
- **Player Lifecycle & Memory**: The `PlaybackManager` was updated to aggressively cancel background coroutines (`progressJob`) upon player release, preventing infinite looping or memory leaks.
- **URI Parsing Integrity**: The `buildMediaItem` function was hardened with `try-catch` and empty string validation (`isBlank()`) to safely emit `PlaybackError.SourceUnavailable` when invalid stream URLs are encountered. Raw URLs are safely kept out of exception messages.
- **Handoff & Authentication Blocks**: `DetailsScreen` and `TvDetailsScreen` were audited to gracefully capture configurations or missing auth states (e.g., Debrid disconnected). Instead of blindly handing off an unplayable source to ExoPlayer, the UI now triggers a calm "Blocked" or "Unavailable" dialogue to the user.
- **TV & Mobile Error UI**: Added `TvErrorOverlay` and streamlined the Mobile Player error overlays to reliably show human-readable text derived from `PlaybackError`. Raw file names and URLs are completely hidden from these screens.

## Privacy & Redaction Review
- **`PlaybackSource.redactUrl` Fortification**: The redaction method was upgraded to properly parse and omit query parameters (`?`) and URL fragments (`#`). This definitively prevents API tokens from leaking out of the hostname in missing-path URL edge cases.
- **Logcat Sweeps**: A comprehensive codebase scan was completed to guarantee that zero instances of `Log.d`, `println`, or stack trace dumping are leaking private stream URLs.

## Known Limitations & Next Steps
- Real Live Channel switching inside the player is currently a placeholder.
- Full subtitle/audio track selector is not yet built.
- Offline playback and background PIP are pending.

**Recommended Next Mission**: Add Stremio Addon Compatibility or advanced Stream Resolving.

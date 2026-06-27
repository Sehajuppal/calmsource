# Live TV Playback Stabilization

This document outlines the stabilization efforts and bug fixes related to Live TV and playback inside the CalmSource project, particularly dealing with ExoPlayer integration, Xtream Codes compatibility, EPG performance, and UI refinements.

## What was Tested/Fixed
*   Replaced linear $O(N)$ EPG list scanning with an $O(1)$ Map approach.
*   Fixed a bug where the TV player would unnecessarily recreate the ExoPlayer instance during simple channel changes.
*   Fixed the memory leak where ExoPlayer was not properly released upon exiting the live player scope.
*   Added `getNowNextForChannels` for highly performant, D-pad optimized scrolling on low-end TVs.
*   All Mission 12 subagent-specific tests and integration builds executed and passed without regressions.

## Player Lifecycle Notes
*   **Player Setup:** ExoPlayer is correctly scoped using Jetpack Compose's `Lifecycle.Event.ON_START` and `ON_STOP` hooks to guarantee decoders are fully released when backgrounded.
*   **Player Session Maps:** The state mapper accurately reflects `TUNING`, `BUFFERING`, `PLAYING`, `PAUSED`, `SWITCHING`, and `FAILED` states. Mapped to `PlayerUiState`.

## Switching Notes
*   **Safe Player Reuse:** Instead of destroying the player on every channel up/down interaction, `PlaybackManager` updates the `MediaItem` in place, avoiding a full teardown of the MediaCodec on low-end Android TV chipsets.
*   **Queue Navigation:** `ChannelQueueManager` allows wrapping (looping from end to start) and gracefully skips missing/unhealthy channels seamlessly.

## EPG Notes
*   **Now/Next Computations:** Introduced the `EpgNowNext` model and an efficient mapping lookup (`getNowNextForChannels()`).
*   **UI Recomposition Shielding:** By taking the logic out of `LazyColumn` items and computing the mapping upfront, the UI doesn't stall for massive list filters while navigating using a TV remote.

## UI Notes
*   **Mobile UI:** A clean sliding bottom sheet handles channel switching. Player overlay clearly shows play/pause, the current stream source, and progress percentage.
*   **TV UI:** Dedicated `ChannelUp`/`ChannelDown` keys instantly switch live channels. D-pad Up/Down opens a transparent side-drawer channel list. No focus traps exist. All text is scaled to `14.sp` or `16.sp` for couch readability.

## Xtream Setup Notes
*   **Xtream Integration:** The app now officially supports adding Xtream Codes API server playlists securely through the main Setup flow.
*   **Encrypted Tokens:** Xtream username and password pairs are serialized and saved only within `EncryptedIptvSecureTokenStore`, preventing them from ever leaking via Room DB exports. Room only stores the unprivileged metadata (server URL without credentials, provider name, sync dates).

## Privacy
*   **URL Redaction:** `PlaybackSource.redactUrl` intercepts all raw IP endpoints and queries, completely masking the port, paths, and API keys. The `NetworkClient` correctly masks the `username=` argument in logcat.
*   **Player Errors:** ExoPlayer native crash strings are caught and mapped directly to user-friendly messages like "Source Unavailable", ensuring no exception dumps leak the stream endpoint on screen.

## Performance
*   **$O(1)$ EPG Data Maps:** `LazyColumn` lookups for `EPGMatch` arrays went from $O(N)$ sequential to $O(1)$ Map index retrieval.
*   **State Debouncing:** Separating the timeline ticks from `PlaybackManager` to `progressState` means only the progress bar slider recomposes every second, leaving the heavy `AndroidView` video canvas and UI overlays untouched.

## Remaining Limitations
*   No automatic EPG offset shifting for timezone drift is available yet.
*   Missing a full screen interactive TV guide with timeline rows. Only vertical current/next data is implemented.

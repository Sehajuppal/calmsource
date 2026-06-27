# Playback & Media3 Foundation

This document outlines the architecture, integration, and user experience of the Media3-based playback foundation in CalmSource.

## Architecture & Integration
The application uses AndroidX Media3 (ExoPlayer) as its core media engine. It abstracts playback through a `PlaybackState` flow and UI components, cleanly separating the player lifecycle from the UI layer.

### PlaybackSource
We utilize a `PlaybackSource` interface to represent playable media across domains (IPTV, Extensions, Stremio Addons, Debrid, Xtream).
* `url`: The raw stream URL. Must not be persisted or logged to preserve privacy.
* `title`: Display name of the content.
* `subtitle`: Secondary text, often stream quality or source name.
* `mimeType`: Optional, for hints to ExoPlayer.

### Xtream Stream URL Construction
For Xtream-sourced content, stream URLs are **constructed lazily at playback time** — never persisted in Room or logged:
* **Live**: `{server}/live/{username}/{password}/{stream_id}.ts`
* **VOD**: `{server}/movie/{username}/{password}/{stream_id}.{container_extension}`
* **Series**: `{server}/series/{username}/{password}/{episode_id}.{ext}`

The `stream_id` is retrieved from Room entities, the server URL from `IPTVProviderEntity.playlistUrl`, the username from Room, and the password from `IptvSecureTokenStore`. The `UrlRedactor` strips credentials from the URL before any logging or UI display.

> For full Xtream URL construction details, see [XTREAM_SYNC.md](./XTREAM_SYNC.md).

### Player Lifecycle
The player (`ExoPlayer`) is managed tightly to ensure no memory or audio leaks.
* **Initialization**: The player is created dynamically when entering the playback screen.
* **Release**: The player is explicitly released (`player.release()`) within the `onDispose` block of the Compose lifecycle or when the ViewModel is cleared.
* **Backgrounding**: The application currently handles pausing when pushed to the background, preventing rogue audio playback.

## Supported Streams
ExoPlayer out-of-the-box supports various stream formats.
* HLS (`.m3u8`)
* DASH (`.mpd`)
* MP4 / MKV containers
* SubRip (SRT) and WebVTT subtitles (embedded and external via Stremio integration)

## Player UI
We implemented a custom Compose-based UI overlaid on top of the `AndroidView` holding the `PlayerView` (which is configured to hide its default controllers).
* **Mobile UI**: Tap-to-toggle overlay visibility. Includes Play/Pause, Rewind/Fast-Forward, and a scrubber/slider.
* **TV UI**: Uses D-Pad navigation. The overlay auto-hides after a few seconds of inactivity and responds cleanly to directional keys.
* **Handoff**: We support smooth transitions from details or search screens directly to the playback screen.

## Subtitles
* ExoPlayer parses embedded tracks automatically.
* Stremio addons that provide external subtitle URLs are integrated via Media3's `MediaItem.SubtitleConfiguration`.

## Error Handling & Privacy
* **Error Mapping**: ExoPlayer errors are caught via `Player.Listener.onPlayerError`. We map them to user-friendly generic messages. The app handles specific error conditions via subclassed events, e.g., `PlaybackError.ServerRefused` for HTTP 403 / cleartext blocked, and separate timeout classifications.
* **Privacy & Source Intelligence**: The **Source Intelligence** layer strictly controls the exposure of raw URLs (`PlaybackSource.url`) and metadata. Raw URLs are **hidden**. They are:
  - Not shown in the UI.
  - Redacted from standard logging.
  - Never stored in Room database.
  - If a player error occurs, the URL is NOT included in the user-facing error message.
* **Dynamic Fallback Policy**: If a stream encounters a playback error, `PlaybackManager` updates the source's health status and automatically resolves the next best alternative `WatchOption` associated with the media item, enabling a continuous viewing experience without returning to the main menu.

## Threading & Performance
* **Main Thread Bound**: All methods interacting with the ExoPlayer instance are strictly annotated with `@MainThread` to prevent thread-safety violations and race conditions within Media3.
* **Rapid Channel Switch Debounce**: A 150ms debounce prevents rapid channel swaps (via D-pad up/down) from flooding ExoPlayer with redundant `prepare()` or `play()` calls, preserving memory and CPU on low-end TV chipsets.

## What is Real vs. Placeholder
* **Real**: Media3 ExoPlayer integration, stream playback (HLS, MP4), PlaybackSource data model, basic Player UI (Play/Pause, Seek), error detection, privacy redaction, dynamic fallback switching, thread safety contracts, 150ms debounce.
* **Placeholder**: Advanced subtitle selection UI, audio track selection UI, PiP (Picture-in-Picture) mode, continuous auto-play for series.

## Next Steps
* Add a full Subtitle & Audio Track picker UI overlay.
* Support Picture-in-Picture (PiP).
* Implement "Up Next" / Auto-play functionality for TV shows.
* Resume point saving (saving the current playback position locally).

## Further Documentation
* For details on recent stabilization efforts, see [LIVE_TV_PLAYBACK_STABILIZATION.md](./LIVE_TV_PLAYBACK_STABILIZATION.md).
* For detailed stream health tracking and fallback strategies, see [SOURCE_HEALTH_AND_FALLBACK.md](./SOURCE_HEALTH_AND_FALLBACK.md).

# CalmSource IPTV & EPG Specifications

This document outlines the core models, supported M3U/XMLTV fields, synchronization state flows, EPG matching strategy, and Universal Search integrations for the CalmSource IPTV foundation.

---

## 1. Supported M3U Fields
The M3U parser line-by-line engine extracts attributes inside `#EXTINF` tags:
*   `tvg-id`: The unique channel identifier matching EPG source channels.
*   `tvg-name`: The official broadcast name for guide lookup.
*   `tvg-logo`: URL link to the channel logo image icon.
*   `group-title`: The channel category folder grouping (e.g., "Movies", "Sports", "IPTV VOD").
*   **Channel Name**: Extracted from the trailing string following the last comma `,` on the `#EXTINF` line.
*   **Stream URL**: Captured from the non-comment line directly succeeding the `#EXTINF` declaration.

---

## 2. Supported XMLTV Fields
The XMLTV EPG parser tokenizes and extracts:
*   `<channel id="...">`: Maps to channel identifiers.
    *   `<display-name>`: Channel guide titles.
*   `<programme start="..." stop="..." channel="...">`: Represents scheduled TV show entries.
    *   `<title>`: The show title.
    *   `<desc>` / `<description>`: Plot outlines.
    *   `<sub-title>`: Episode or part subtitles.
    *   `<category>`: Classification tags (e.g., "Movies", "News").
    *   `<language>`: ISO language codes.
    *   `<episode-num>`: Season/episode indexes.

---

## 3. Data Models (`:core:model`)

### IPTVProvider
Configures playlist definitions:
```kotlin
data class IPTVProvider(
    val id: String,
    val name: String,
    val playlistUrl: String,
    val isEnabled: Boolean = true,
    val health: ProviderHealth = ProviderHealth.HEALTHY
)
```

### EPGSource
Configures program guide definitions:
```kotlin
data class EPGSource(
    val id: String,
    val providerId: String,
    val name: String,
    val url: String,
    val lastSyncMs: Long = 0
)
```

### IPTVChannel
Represents parsed playlist items:
```kotlin
data class IPTVChannel(
    val id: String,
    val tvgId: String?,
    val tvgName: String?,
    val tvgLogo: String?,
    val groupTitle: String?,
    val name: String,
    val streamUrl: String,
    val providerId: String,
    val rawAttributes: Map<String, String> = emptyMap()
)
```

### EPGProgram
Represents parsed guide items:
```kotlin
data class EPGProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val subtitle: String? = null,
    val category: String? = null,
    val language: String? = null,
    val episodeNum: String? = null
)
```

### EPGMatch
Maps channels to EPG records:
```kotlin
data class EPGMatch(
    val channelId: String,
    val epgId: String,
    val matchType: String // "EXACT_ID", "NORMALIZED_NAME", "MANUAL", "NONE"
)
```

---

## 4. Synchronization State Flows

Synchronization states are updated progressively using flows inside `IPTVRepository`:
```kotlin
data class ProviderSyncState(
    val providerId: String,
    val status: ProviderSyncStatus,
    val progressPercent: Int = 0,
    val error: String? = null
)
```
States update from `IDLE` -> `SYNCING` -> `SUCCESS` or `ERROR`. The UI listens to these flows, showing linear progress indicators during active parsing.

---

## 5. EPG Matching Strategy
Automated matching executes in decreasing priority:
1.  **Exact tvg-id match**: Matches M3U `tvg-id` directly to XMLTV `<channel id="...">`.
2.  **Normalized tvg-name match**: Standardizes casing, removes spacing and non-alphanumeric symbols between M3U `tvg-name` and XMLTV `<display-name>`.
3.  **Normalized name match**: Standardizes and compares raw channel names to XMLTV display names.
4.  **Fuzzy fallback contains match**: Evaluates if the normalized name of one is contained within the other.

Unmatched channels fallback to a calm `"No guide data available"` state in the guides.

---

## 6. Universal Search Integration
Dynamic search providers index parsed IPTV data:
*   `IPTVSearchProviderImpl` queries live channels matching query text.
*   `EPGSearchProviderImpl` queries airing and upcoming programs.
*   `VODSearchProviderImpl` queries IPTV VOD channels (recognized by category "VOD" or "Movies"), mapping and merging them into title-first Movie results to avoid duplicate layout cards.

---

## 7. Error Handling
*   Malformed lines in M3U (missing comma separators or malformed tags) are skipped safely, adding a warning detail to the import result.
*   EPG elements with malformed dates or missing channels log warnings instead of crashing.
*   Sync progress reports error states to the UI so users see warning banners with details.

---

## 8. Known Limitations
*   All state lists are saved in memory inside the repository singleton and reset on app exit.
*   Sync actions simulate local files using mock contents until real HTTP requests are implemented.

---

## 9. Next Steps
*   **Persistent Storage**: Connect SQLite Room database tables to cache providers and channels.
*   **WorkManager Tasks**: Orchestrate background sync tasks with periodic intervals.
*   **Ktor Networking**: Stream M3U and XMLTV directly from remote HTTP URL endpoints to disk.
*   **Active Health Checks**: Leverage telemetry to dynamically downgrade inactive IPTV playlists.

## 10. Further Documentation
* For comprehensive live television architectural rules, see [LIVE_TV_PLAYBACK.md](./LIVE_TV_PLAYBACK.md).
* For details on recent stabilization efforts, see [LIVE_TV_PLAYBACK_STABILIZATION.md](./LIVE_TV_PLAYBACK_STABILIZATION.md).
* For detailed stream health tracking and fallback strategies, see [SOURCE_HEALTH_AND_FALLBACK.md](./SOURCE_HEALTH_AND_FALLBACK.md).

## 11. Test Architecture
A real-source smoke test architecture was successfully executed to validate IPTV streams, M3U parsing, and EPG mapping flows against real endpoints.

## 12. Xtream-Compatible API Sync

In addition to M3U/XMLTV-based IPTV, CalmSource supports direct synchronization with Xtream-compatible API servers. This enables users with lawfully subscribed IPTV services to connect their servers and sync live TV categories/channels, VOD content, and series metadata directly through the Xtream API protocol.

### Key Integration Points
*   **Authentication**: Credentials are validated via `player_api.php`. Passwords are stored exclusively in `IptvSecureTokenStore` (Android Keystore-backed encryption) and never in Room.
*   **Live Channels**: Retrieved via `get_live_categories` and `get_live_streams` endpoints. Channels are stored in the shared `IPTVChannelEntity` table alongside M3U-sourced channels.
*   **VOD & Series**: Retrieved via `get_vod_streams` and `get_series` endpoints. Stored in dedicated `XtreamVodEntity` and `XtreamSeriesEntity` tables.
*   **Stream URL Construction**: Live and VOD stream URLs are constructed lazily at playback time from the `stream_id` and credentials — never persisted.
*   **EPG**: Short EPG data is fetched per-stream when available via `get_short_epg`.

### Sync Pipeline Stages
`VALIDATING` → `LIVE_CATEGORIES` → `LIVE_STREAMS` → `VOD_CATEGORIES` → `VOD_STREAMS` → `SERIES` → `EPG` → `COMPLETE` or `FAILED`

> For full Xtream API endpoint specifications, sync pipeline details, credential storage rules, and playback integration, see [XTREAM_SYNC.md](./XTREAM_SYNC.md).

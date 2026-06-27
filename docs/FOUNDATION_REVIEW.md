# Foundation Review - CalmSource (Mission 2.5)

This document provides a review, stabilization status, and safety audit of the initial CalmSource multi-module Android foundation.

## Build Status
*   **Mobile Module (`:app-mobile`)**: Renders all main touch screens, bottom-rail layouts, details sheets, player overlay, and settings configurations. Built successfully into a debug APK.
*   **TV Module (`:app-tv`)**: Renders TV side-rail layout, card list carousels, Live EPG guides, and search keyboard panels. Built successfully into a debug APK.
*   **Shared Libraries**: All core (`:core:model`, `:core:playback`, `:core:database`, `:core:network`, `:core:parser`) and feature (`:feature:search`, `:feature:iptv`, `:feature:extensions`, `:feature:debrid`) modules build and bundle without dependencies conflicts.
*   **Gradle Build Command**: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug` completed successfully.

## Test Status
*   **Unit Tests Suite**: Located in `feature/search/src/test/java/com/example/calmsource/feature/search/SearchRankingTest.kt`.
*   **Target Coverage**: Merging deduplication, language ranking calculations, and the target "Spider-Man: Homecoming" search result.
*   **Result**: 100% pass rate. All assertions run successfully in `1m 8s`.

## Screens Implemented (With Fake Data)
1.  **Mobile Home**: Continue watching tracker, movie and TV show carousels.
2.  **TV Home**: Zoom-scaled cards with D-pad navigation rail.
3.  **Universal Search**: Debounced query processing with progressive loading indicators.
4.  **Search Results**: Consolidated list (no duplicate cards for multiple stream formats).
5.  **Details Screen**: Consolidated summary with primary/secondary watch actions.
6.  **Watch Options / Stream Picker**: Layout for selecting IPTV VOD, Dual Audio, Hindi, or English streams.
7.  **Mobile Live TV**: Tabbed category view of live channels with air progress indicators.
8.  **TV Live Guide**: Scrollable grid with EPG channel timelines.
9.  **Player Overlay**: Media3 ExoPlayer container playing sample stream overlays.
10. **Settings Screens**: Sections for IPTV service entries, Extension provider listings, Debrid credentials linking, Priority ranking preferences, and Language weights.

## Verification of Target Search Behavior (Spider-Man Homecoming)
*   **Deduplication**: Searching for "Spider-Man Homecoming" aggregates all IPTV VOD, Extension, and Debrid streams under a single title result, avoiding card duplicates.
*   **Details Layout Watch Actions**: Opening the result displays:
    *   *Play Best Match*: Dynamically pre-calculates the highest-scored stream matching preferences.
    *   *IPTV VOD*: Actionable shortcut to play exact IPTV catalog stream.
    *   *Dual Audio*: Actionable shortcut for dual tracks.
    *   *Hindi*: Shortcut to play in Hindi.
    *   *English*: Shortcut to play in English.
    *   *Advanced - Manual Stream Sources*: Collapsible panel toggle. Clicking it reveals the full list of all 6 individual source streams sorted by calculated preference score.

## Architecture Review
*   **Model Isolation**: All core interfaces reside cleanly inside `:core:model` (separated from presentation).
*   **UI & Data Separation**: Components only reference `FakeData` or call `SearchEngine` queries. No view layouts contain hardcoded mock definitions.
*   **Testability**: The scoring math (`calculateScore`) and stream merger (`mergeAndRank`) are static, pure functions, making it trivial to assert ranking changes without launching a device runtime.
*   **TV and Mobile Layout Separation**: Kept distinct and modular under `:app-mobile` and `:app-tv` components, ensuring TV views contain only compose-tv layout properties.

## Performance Review
*   **List Keys**: All `LazyColumn` and `LazyRow` layout groups utilize stable, immutable keys (e.g., `key = { it.id }` or `key = { channel.id }`).
*   **Recomposition Checks**: Avoided heavy iterations or parsing inside composables. Data sorting, filtering, and scoring occur once inside state preparation blocks.
*   **Asynchronous Non-Blocking Search**: Flow queries run concurrently in coroutine scopes. Slow providers (e.g., simulated 1200ms delay) emit progressive results when ready without blocking UI keyboard typing.
*   **D-pad Navigation Focus**: Utilizes custom `TvFocusCard` using standard `Modifier.focusable()` and focus-changed listeners. No manual focus request hacks, enabling predictable system routing.

## Legal & Safety Review
*   **No Copyright Violations**: No illegal M3U links, scraped portals, pirated indices, or DRM decryption keys are bundled.
*   **No Code Scraping**: Code is written from scratch. Absolutely no codebase snippets are copied from Torrentio, AIOStreams, Stremio, or Kodi.
*   **Safe Media Sample**: The Media3 ExoPlayer loads a public, legal sample stream (Tears of Steel demo stream from Unified Streaming) rather than unverified links.

## Fixes Completed (Mission 2.5)
1.  **Refactored SearchEngine Filtering**: Cleaned up implicit lambda shadowing (`it`) in [SearchEngine.kt](file:///d:/Program%20Files/iptv/feature/search/src/main/kotlin/com/example/calmsource/feature/search/SearchEngine.kt) by changing parameter to `source -> ...` and replacing a redundant `it.id == it.id` condition with `allMediaSources.any { it.id == source.id }`.
2.  **Fixed TV Watch Shortcuts**: Added the missing `hindiOption` to the watch shortcuts row in [TvDetailsScreen.kt](file:///d:/Program%20Files/iptv/app-tv/src/main/java/com/example/calmsource/tv/ui/TvDetailsScreen.kt).
3.  **Collapsed TV Advanced Sources**: Wrapped manual TV details streams inside an expandable "Advanced - Manual Stream Sources" toggle focus card, matching the mobile UX rules.

## Recommended Next Mission
**Mission 3: Core Database, Keystore & Parser Integrations**
*   Implement real Room database entities for cached channels and offline EPG schedules.
*   Configure real DataStore and EncryptedSharedPreferences for secure IPTV/Debrid token credentials.
*   Integrate Ktor HTTP clients to pull live M3U playlists and parse XMLTV EPG timelines into database entities.

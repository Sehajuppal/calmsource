# CalmSource IPTV & EPG Stabilization Report

This document reviews the stabilization changes, fixes, and limitations of the IPTV and EPG implementation foundation in CalmSource.

---

## 1. What Was Tested
A comprehensive suite of JUnit test cases was created in `IPTVParserTest.kt` to cover the entire parsing, matching, and search merging pipeline:
*   **Valid M3U Parsing**: Verified that standard `#EXTINF` tags, key-value attributes (`tvg-id`, `tvg-name`, `tvg-logo`, `group-title`), and stream URLs parse correctly.
*   **Malformed M3U Handling**: Verified that malformed lines (such as missing name-separator commas) are skipped gracefully without crashing the app, and generate understandable warnings.
*   **Duplicate Channel Handling**: Confirmed that obvious duplicates (by stream URL) are deduplicated, and warnings are logged.
*   **Valid XMLTV Parsing**: Confirmed that XMLTV `<programme>` elements, categories, start/stop times, and languages are extracted correctly.
*   **Malformed XMLTV Handling**: Verified that missing channel IDs or malformed date fields fail safely without crashing.
*   **EPG matching**: Verified all four EPG matching heuristics:
    1.  Exact match on `tvg-id` vs EPG `<channel id>`.
    2.  Normalized match on `tvg-name` vs EPG `<display-name>`.
    3.  Normalized match on parsed M3U channel name.
    4.  Fuzzy fallback contains matching.
*   **Universal Search Integration**: Verified that dynamic VOD channels are merged into title-first results matching existing catalog movies (e.g. "Spider-Man: Homecoming"), and do not create duplicate card rows.

---

## 2. What Was Fixed
During stabilization, the following issues were resolved:
1.  **Gradle Compilation Issue**: `:feature:iptv` lacked dependency declarations on Kotlin Coroutines. Added `implementation(libs.kotlinx.coroutines.core)` to `feature/iptv/build.gradle.kts`.
2.  **Cross-Module Smart Cast Failure**: Kotlin compiler complained that a nullable property `channel.tvgId` from `:core:model` could not be smart-cast because it was from another module. Fixed by copying `tvgId` to a local variable before evaluation.
3.  **Cross-Module Test Configuration**: Added `:core:parser` dependency to `:feature:search` so parser classes are fully accessible in tests.
4.  **Static VOD Result Filter Bug**: `SearchResultMerger` previously filtered out custom parsed IPTV VOD sources because they weren't statically registered in `FakeData.getSourcesForMedia(...)`. Updated the filter logic to dynamically match stream sources to media items via normalized titles.
5.  **Noisy Duplicate VOD Cards**: When IPTV VOD results (e.g. "Spider-Man: Homecoming [IPTV]") matched the search query, they appeared as a separate card from "Spider-Man: Homecoming" because their titles and IDs differed. Updated `VODSearchProviderImpl` to check for title matches in catalog movies, mapping them to the same ID to deduplicate results.
6.  **TV Guide / Live TV Data Binding**: Switched TV Settings, TV Guide, and Mobile Live screens from static mock datasets to live `IPTVRepository` flows.

---

## 3. Remaining IPTV Limitations
*   **No Real Network Pulls**: Playlists and EPG guides are loaded from string parameters during sync. Network loading of `.m3u` / `.xml` URLs needs real HTTP client setups (e.g. Ktor or Retrofit) in the next phase.
*   **In-Memory Storage**: Custom playlists, EPG structures, and matching tables are saved in-memory and reset when the application process restarts. Persistence layers (such as Room SQLite) must be fully bound to these models.
*   **No Xtream Login UI**: Standard Xtream-Codes UI forms remain placeholder shells until login backend APIs are set up.

---

## 4. Parser Limitations
*   **Strict String Loading**: The current XMLTV parser expects the XML content to be loaded into memory as a `String`. For very large EPG files (e.g. 50MB+), this can cause memory pressure. A streaming input stream scanner should be added when migrating to persistent database insertion.
*   **M3U Attribute Formatting**: Key-value attribute parsing expects standard double-quoted values (e.g., `key="value"`). Unquoted or single-quoted attributes might not parse correctly.

---

## 5. EPG Matching Limitations
*   **Normalized Title Collision**: Normalized titles strip all non-alphanumeric characters. Rare channel names with similar words (e.g., "HBO East" vs "HBO West") could trigger incorrect fuzzy matches if normalization is too aggressive.
*   **Manual Correction State**: While `IPTVRepository.updateManualMatch(...)` exists, there is no UI component in this checkpoint to let users manually override automated matches.

---

## 6. Performance Notes
*   **Main-Thread Safety**: All M3U and XMLTV parsing runs inside `withContext(Dispatchers.Default)` to ensure the main UI thread never freezes.
*   **Lazy List Key Stability**: Both Mobile and TV lists (channels, schedules) enforce stable keys using channel/program IDs, minimizing recompositions.
*   **Dynamic Category Tabs**: On Mobile, category tabs are dynamically generated from parsed channel groups, showing only relevant tabs.

---

## 7. Recommended Next Mission
**Mission 5: Database Persistence & HTTP Sync Engine**
*   Implement Room database entities for `IPTVProvider`, `IPTVChannel`, `EPGSource`, `EPGProgram`, and `EPGMatch`.
*   Replace in-memory lists in `IPTVRepository` with Room DAO queries and paging flows.
*   Implement real background synchronization workers (using Android WorkManager) to fetch, stream-parse, and persist M3U/XMLTV files from URLs using Ktor.

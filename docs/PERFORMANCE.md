# CalmSource Performance Strategy

This document details the micro and macro UI performance design choices implemented in the CalmSource architecture, with special focus on CPU/RAM limitations of low-end Android TV boxes (which often operate on 1GB - 1.5GB RAM and entry-level quad-core ARM chipsets).

---

## 1. Jetpack Compose Optimizations

### A. Stable Keys in Lazy Lists
For all vertical and horizontal lists, we enforce stable keys to avoid unnecessary item recompositions when items are re-ordered, added, or removed:
```kotlin
LazyRow(
    horizontalArrangement = Arrangement.spacedBy(16.dp)
) {
    items(
        items = mediaList,
        key = { it.id } // Ensures Compose remembers state across scrolling cycles
    ) { item ->
        TvVODCard(item = item, onClick = { ... })
    }
}
```

### B. Offloading Computations from Recomposition
All scoring calculations (such as `SearchEngine.calculateScore`) and EPG date format mappings are performed outside the UI rendering loops (either during data preparation in the ViewModels/Engines or lazily remembered):
```kotlin
val sortedOptions = remember(rawSources, prefs) {
    rawSources.map { source ->
        val score = SearchEngine.calculateScore(source, prefs)
        // map to WatchOption...
    }.sortedByDescending { it.score }
}
```
Similarly, for Live TV EPG data, we group EPG programs by channel ID using `groupBy { it.channelId }` outside the `LazyColumn` context, and use a map for $O(1)$ lookup. This avoids executing $O(N)$ large list scans for every visible row during recomposition or when quickly switching channels using the TV D-pad, significantly reducing UI thread stalling and D-pad lag.

This guarantees that scrolling a manual stream list with $20+$ options or navigating an EPG guide does not trigger redundant mathematical calculations on every frame.

### C. State Debouncing
TV keyboard input and quick scrolling can cause search actions to fire rapidly, triggering excessive memory allocations. We enforce a **400ms input debounce** in `TvSearchScreen` (and 300ms in `SearchScreen` for mobile) to skip parsing transient keystrokes.

> [!NOTE]
> **Debounce Discrepancy**: The `TvSearchScreen` KDoc states 400ms debounce, but the actual `delay()` value at line 78 is 300ms — identical to the mobile `SearchScreen`. The documentation should be corrected, or the TV debounce should be increased to the documented 400ms to better accommodate TV D-pad input latency.

---

## 2. Low-End Android TV Mitigations

### A. Non-blocking Progress Updates & Extension/Debrid Timeouts
Universal Search aggregates data from multiple remote extension and Debrid cached check endpoints. If a provider is slow or experiences high latency, the search engine emits partial local results (such as IPTV live guide channels and local IPTV VOD matches) immediately:
*   Users can choose to play a local match instantly while remote scrapers and debrid caches continue resolving in the background.
*   Slow extensions and debrid cached checks are evaluated inside separate asynchronous coroutines wrapped in error catch blocks to prevent a single timeout or provider failure from breaking the main player or search thread.
*   **Extension & Debrid Timeout Policies**: Enforces strict timeout policy thresholds (default $5000$ms limit, with custom per-provider configurations) using `withTimeoutOrNull` wrappers. Any provider that fails to respond within its policy limit is safely dropped from the current search emission.
*   **Asynchronous Details Resolution**: Detail screens (Mobile and TV) open instantly by initializing local IPTV/VOD watch options first, and then fetch stream and subtitle options from all active extensions in parallel via asynchronous coroutines, updating the stream picker UI dynamically as links resolve.
*   **Debrid Resiliency**: If a connected Debrid provider is offline, returns HTTP 500, or experiences API rate limiting, it will not block search execution. Search results from IPTV and active extensions will still populate correctly.
*   **Manifest Caching**: Manifests fetched over the network via Ktor are parsed and cached in Room database entities. This prevents expensive HTTP requests and JSON schema parsing operations on every search query cycle.

### B. Image Size Scaling & Cache Eviction
Unscaled 4K backdrop poster graphics will crash low-end TVs with Out-Of-Memory (OOM) exceptions. We configure `Coil` image load parameters:
*   Scale all poster bitmaps to exact display coordinates (e.g. `size(130.dp, 180.dp)` for TV posters) before drawing them.
*   Enforce disk caching for offline operation, and limit maximum memory cache to 15% of total system heap space.

> [!WARNING]
> **IMG-1: No explicit Coil ImageLoader configuration found.** The project uses `coil.compose.AsyncImage` throughout (HomeScreen, SearchScreen, DetailsScreen, all TV screens) but no custom `ImageLoader` builder or `ImageLoaderFactory` was located in the codebase. This means Coil is running with default configuration — unbounded memory cache, no disk cache size limits, and no downsampling. On 1GB RAM Android TV devices loading channel logos and movie posters from IPTV playlists (1000+ channels), this can cause OOM crashes. **Recommendation:** Add a custom `ImageLoaderFactory` in the Application class with `memoryCachePolicy`, `diskCachePolicy`, and `size()` constraints.

### C. D-pad Focus Boundaries & Settings List Stability
To prevent the Android focus selector from "flying away" or disappearing when a dynamic row is loading:
*   We use stable placeholders for loading items.
*   We align layout containers explicitly so D-pad movements (`onFocusChanged`) scale cards by a subtle factor ($1.08x$) and glow boundaries instead of using expensive post-processing blur effects.
*   **Stable Keys in Extensions Screen**: The Extension settings pane renders entries using stable list keys. The split-pane layout separates focus between the scrollable list on the left and the configuration action cards on the right. Focusing an extension updates the details panel live via a non-allocating ID state-comparison pointer, avoiding recomposition of the list itself.

---

## 3. Secure Storage Performance

### A. Lazy Encryption Initialization
Initializing `EncryptedSharedPreferences` and the Android Keystore system requires cryptographic key generation/retrieval (AES master keys) and file decryption. Doing this synchronously on app startup can introduce significant UI thread lag (up to several hundred milliseconds on low-end TV boxes).
*   **Lazy Instantiation**: Production token store (`EncryptedSecureTokenStore`) initialization is deferred until first access.
*   **Startup Safeguard**: The keystore and preference file are only accessed when a feature specifically requests credentials (e.g., resolving a streaming link or viewing/managing connected accounts in settings). Standard app startup remains completely unblocked.

---

## 4. Playback and Video Player Optimizations

### A. ExoPlayer Lifecycle Integration and Safe Reuse
`PlaybackManager` and the Compose UI layers (`PlayerScreen` and `TvPlayerScreen`) observe Jetpack Compose's native `Lifecycle.Event.ON_START` and `ON_STOP` events. `ExoPlayer` instances are actively released (and their decoders freed) on `ON_STOP` to prevent orphaned background audio and memory leaks when switching inputs or backgrounding the application. Upon returning (`ON_START`), the stream dynamically reconstructs and resumes playback precisely from its suspended position.

To prevent dropping frames when channel switching or selecting different streams while the player is already active, `PlaybackManager` uses **safe player reuse**. Instead of destroying and recreating the player, it updates the `MediaItem` and retains the existing `ExoPlayer` instance. This avoids costly MediaCodec teardown and recreation sequences on low-end Android TV chipsets.

### B. Progress State Separation
Fast-changing timeline parameters such as `currentPositionMs`, `durationMs`, and `bufferedPositionMs` are intentionally separated from the main player state into a dedicated `progressState` Flow. By scoping these ticking reads only to progress bar and timer components, we eliminate massive, one-second-interval UI recompositions across the heavier `AndroidView` video overlays and controller containers. This minimizes GC pressure and allows the primary thread to easily achieve a stable 60 FPS on low-resource ARM chipsets.

---

## 5. Mission 14 Performance Audit (Comprehensive)

This section documents findings from a deep read-only audit of the five core performance-sensitive components, conducted during Mission 14.

### Audit Summary Table

| Component | Severity | Finding Count | Critical | Medium | Low/Info |
|---|---|---|---|---|---|
| M3UParser | ✅ Healthy | 3 | 0 | 1 | 2 |
| XMLTVParser | ⚠️ Needs Attention | 4 | 1 | 2 | 1 |
| IPTVRepository | ⚠️ Improved | 5 | 0 | 2 | 3 |
| UniversalSearchEngineImpl | ✅ Healthy | 3 | 0 | 1 | 2 |
| PlaybackManager | ✅ Healthy | 3 | 0 | 1 | 2 |

---

### A. M3UParser — `core/parser/M3UParser.kt`

**Overall Assessment: ✅ Healthy — well-structured streaming parser**

The parser correctly uses `BufferedReader` wrapping an `InputStreamReader` on the raw `InputStream`, processing line-by-line. This is an efficient approach for M3U files which are inherently line-oriented.

| # | Severity | Finding | Location |
|---|---|---|---|
| M3U-1 | 🟡 Medium | **InputStream not closed on success path.** The `BufferedReader` is created at line 52 but never explicitly closed. If an exception occurs, the `catch` block returns early without closing. On the success path, the reader is also never closed. While the caller (`IPTVRepository.syncPlaylist`) may close the original `InputStream`, the `BufferedReader` wrapping it should be closed to flush internal buffers. **Recommendation:** Wrap reader creation in `reader.use { ... }` or a `try/finally`. | Lines 52–101 |
| M3U-2 | 🟢 Low | **Regex compiled per `parseChannel` invocation.** The `Regex("""([a-zA-Z0-9_-]+)\s*=\s*"([^"]*)"…""")` at line 117 is instantiated inside `parseChannel`, meaning it's recompiled for every single channel entry. For playlists with thousands of channels, this adds unnecessary object allocation. **Recommendation:** Hoist the regex to a `companion object` or top-level `private val`. | Line 117 |
| M3U-3 | 🟢 Info | **`seenUrls` HashSet grows unbounded.** The `mutableSetOf<String>()` used for duplicate detection at line 57 will hold all stream URLs in memory. For typical IPTV playlists (1K–10K channels), this is negligible. For extreme playlists (100K+), it could contribute to memory pressure. Current implementation is acceptable. | Line 57 |

---

### B. XMLTVParser — `core/parser/XMLTVParser.kt`

**Overall Assessment: ⚠️ Needs Attention — memory risk with large EPG files**

The parser uses `Scanner` with a `</programme>` delimiter to chunk-process XML. This avoids loading the entire file into a single `String`, which is a good design choice. However, several concerns exist.

| # | Severity | Finding | Location |
|---|---|---|---|
| XMLTV-1 | 🔴 Critical | **Scanner reads entire token between delimiters into memory.** If an EPG file has a very large preamble before the first `<programme>` tag (e.g., a `<channel>` section listing thousands of channels with logos/URLs), the first `scanner.next()` call will read that entire preamble into a single `String`. Real-world XMLTV files from providers like WebGrab+Plus can have 50MB+ preambles. This defeats the purpose of chunked parsing and can cause OOM on 1GB Android TV devices. **Recommendation:** Pre-scan and skip past the first `<programme` tag using a small `BufferedReader` before handing off to Scanner, or switch to a true SAX/XmlPullParser for the `<channel>` section. | Lines 77–81 |
| XMLTV-2 | 🟡 Medium | **Regex per chunk is costly.** The `programmeRegex` at line 78 uses `DOT_MATCHES_ALL`, and `extractAttribute`/`extractTagValue` each compile a *new* `Regex` per invocation (lines 144, 149). For EPG files with 50K+ programmes, this means 50K × 5+ regex compilations = 250K+ `Regex` objects. **Recommendation:** Hoist `extractAttribute` and `extractTagValue` regexes to precompiled companion-object values using parameterized patterns or a regex cache. | Lines 143–151 |
| XMLTV-3 | 🟡 Medium | **`SimpleDateFormat` is not thread-safe.** Two `SimpleDateFormat` instances are created per `parse()` call (lines 58–59) and captured in the `parseDate` closure. While single-threaded use is currently safe, if `parse()` is ever called concurrently (e.g., syncing multiple EPG sources in parallel), date parsing will produce corrupted results silently. The KDoc notes awareness of this and uses `synchronized` access in `parseDate` — this is a viable mitigation but adds contention. **Recommendation:** Use `java.time.format.DateTimeFormatter` (thread-safe) or document that `parse()` must not be called concurrently. | Lines 58–59, 75–88 |
| XMLTV-4 | 🟢 Info | **Scanner not closed in `finally` block.** `scanner.close()` at line 131 is inside the `try` block. If an exception occurs between `scanner.next()` and `scanner.close()`, the scanner (and underlying InputStream) leaks. The outer `catch` at line 132 catches the error but doesn't close the scanner. **Recommendation:** Use `scanner.use { ... }` or a `finally` block. | Lines 77–131 |

---

### C. IPTVRepository — `feature/iptv/IPTVRepository.kt`

**Overall Assessment: ⚠️ Improved — thread-safety addressed since initial audit**

Since the initial audit, `dataLock` synchronization has been applied to all mutable state accesses (`parsedChannels`, `parsedPrograms`, `matches`, `sourceHealthMap`). The O(N×M) matchEPGSync complexity has been reduced. Remaining issues are lower severity.

| # | Severity | Finding | Status |
|---|---|---|---|
| IPTV-1 | ✅ Fixed | **`parsedPrograms` synchronization.** `syncEPG()` at lines 288–292 now wraps the read-modify-write in `synchronized(dataLock)`. Read paths (`getPrograms()` at line 216, `getNowNextForChannel()` at line 388, `getNowNextForChannels()` at line 406) all acquire `dataLock` before reading. | Lines 73–74, 288, 216, 388, 406 |
| IPTV-2 | ✅ Fixed | **`matches` map synchronization.** `matchEPGSync()` at line 324 wraps write in `synchronized(dataLock)`. `getMatchStatusForChannel()` at line 369, `updateManualMatch()` at line 373, and `getNowNextForChannels()` at line 397 all use `synchronized(dataLock)`. | Lines 324, 369, 373, 397 |
| IPTV-3 | ✅ Fixed | **`dataLock` is now used.** All shared mutable state is protected by `synchronized(dataLock)` blocks. | Throughout |
| IPTV-4 | ✅ Fixed | **`matchEPGSync()` now O(N+M).** Pre-computed `normalizedEpgIdMap` (line 318: `groupBy { it.normalize() }`) enables O(1) lookup for tiers 1–3. Only tier 4 (fuzzy match) requires a linear scan of the EPG ID set, but this is bounded to channels that failed tiers 1–3. | Lines 310–360 |
| IPTV-5 | 🟡 Medium | **Tier 4 fuzzy match still calls `.normalize()` per EPG ID.** In `findMatch()` at line 354, the fuzzy match iterates `epgIdSet.firstOrNull { it.normalize().contains(normName) ... }`. Each call to `.normalize()` allocates a new string via regex replacement. For channels that reach tier 4, this is O(M) normalizations per channel. **Recommendation:** Pre-compute a `Map<String, String>` of `epgId -> normalizedEpgId` and pass it to `findMatch` for O(1) lookups in the fuzzy tier. | Lines 353–356 |
| IPTV-6 | 🟢 Info | **`UniversalSearchEngineImpl()` instantiated per search.** Both `SearchScreen` (line 97) and `TvSearchScreen` (line 88) create a new `UniversalSearchEngineImpl()` on every debounced query emission. While the constructor is lightweight, the internal provider list allocation is redundant. The `SearchEngine` object already holds a `lazy` singleton, but the screens don't use it. **Recommendation:** Use `remember { UniversalSearchEngineImpl() }` or the existing `SearchEngine.universalEngine` singleton. | SearchScreen:97, TvSearchScreen:88 |

---

### D. UniversalSearchEngineImpl — `feature/search/UniversalSearchEngineImpl.kt`

**Overall Assessment: ✅ Healthy — well-designed concurrent search with proper timeout isolation**

The search engine correctly launches all providers concurrently, wraps each in `withTimeout`, re-throws `CancellationException` (line 83), and awaits results in priority order for progressive emission.

| # | Severity | Finding | Location |
|---|---|---|---|
| SEARCH-1 | 🟡 Medium | **Priority-ordered `await()` can delay emission of fast low-priority providers.** The engine awaits deferreds in descending priority order (line 95–97). If a high-priority provider is slow (e.g., `HistorySearchProviderImpl` doing a database query), the engine blocks on its `await()` even if all lower-priority providers have already completed. The user sees no results until the highest-priority provider finishes. **Recommendation:** Consider using a `Channel`-based or `select`-based approach that emits results in completion order while still sorting the accumulated results by priority. This would give the user immediate feedback from any provider that finishes first. | Lines 94–110 |
| SEARCH-2 | 🟢 Low | **`SearchResultMerger.merge()` called N times with growing `accumulatedResults`.** Each provider completion triggers a full re-merge of all accumulated results (line 102–108). With 9 providers, the last merge operates on all 9 results. If `merge()` is expensive (sorting, deduplication), this adds up. Current implementation is acceptable for 9 providers. | Lines 102–109 |
| SEARCH-3 | 🟢 Info | **Hardcoded empty lists for `favorites` and `history` in merge call.** Lines 106–107 pass `emptyList()` for favorites and history to the merger. This means the merger's favorite-boosting and history-boosting logic is effectively disabled. This is a functional concern, not a performance one, but it means the ranking algorithm is operating with incomplete data. | Lines 106–107 |

---

### E. PlaybackManager — `core/playback/PlaybackManager.kt`

**Overall Assessment: ✅ Healthy — well-structured player lifecycle with good state separation**

The manager correctly separates progress state from player UI state, uses safe player reuse via `MediaItem` comparison, and properly manages the progress polling coroutine.

| # | Severity | Finding | Location |
|---|---|---|---|
| PB-1 | 🟡 Medium | **`player` field is not thread-safe.** `player` (line 28) is a nullable `var` accessed from multiple contexts: `create()` and `release()` may be called from the UI thread, while `playerListener` callbacks come from ExoPlayer's internal thread. The `startProgressUpdates` coroutine reads `player` on `Dispatchers.Main`. If `release()` sets `player = null` while `startProgressUpdates` reads it between the null-check (line 188) and the `.currentPosition` access (line 195), a race condition can occur. The `if (p == null)` check at line 189 mitigates this by capturing a local reference, which is good, but `create()` (line 79–84) has no similar guard against concurrent `release()`. **Recommendation:** Ensure `create()`, `release()`, and all public methods are only called from the main thread, and document this contract explicitly. | Lines 28, 79–84, 135–141, 188–195 |
| PB-2 | 🟢 Low | **Progress polling interval is 1 second.** The `delay(1000)` at line 200 means the progress bar updates once per second. For a smoother progress bar (especially on seek-bar scrubbing), a 250ms or 500ms interval might be preferable. However, the current 1-second interval is better for low-end devices as it reduces main-thread work. This is a reasonable tradeoff. | Line 200 |
| PB-3 | 🟢 Info | **`progressJob?.cancel()` inside the job itself.** At line 190, the running coroutine cancels its own job reference (`progressJob?.cancel()`). This works but is redundant — `break` on line 191 already exits the `while(true)` loop, and the coroutine will complete naturally. The self-cancel is harmless but unnecessary. | Lines 190–191 |

---

## 6. Extended Performance Audit

This section documents additional findings from the full 16-component performance audit covering pipeline, networking, health tracking, channel management, and UI screens.

### Extended Audit Summary Table

| Component | Severity | Finding Count | Critical | Medium | Low/Info |
|---|---|---|---|---|---|
| SearchResultPipeline | 🔴 Critical Risk | 5 | 2 | 2 | 1 |
| ChannelQueueManager | ✅ Healthy | 1 | 0 | 0 | 1 |
| StremioAddonClient | ⚠️ Needs Attention | 2 | 0 | 2 | 0 |
| SourceHealthRepository | ✅ Healthy | 1 | 0 | 0 | 1 |
| UI Screens (Mobile + TV) | ✅ Healthy | 2 | 0 | 1 | 1 |

---

### F. SearchResultPipeline — `feature/search/SearchResultPipeline.kt`

**Overall Assessment: 🔴 Critical Risk — `runBlocking` on the scoring hot path**

The scoring pipeline is invoked for every search result on every search query. Two critical `runBlocking` calls block the calling coroutine to perform database lookups, creating a severe performance bottleneck.

| # | Severity | Finding | Location |
|---|---|---|---|
| PIPE-1 | 🔴 Critical | **`runBlocking(Dispatchers.IO)` for source health lookup on every score calculation.** `calculateSourceScore()` at line 134 calls `kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { SourceHealthRepository.getSourceHealth(source.id) }`. This blocks the calling thread (which may be the main thread or a coroutine pool thread) while performing a Room database query. With 20+ sources per media item, this creates 20+ synchronous database queries per search. For a search returning 50 results with 5 sources each, this is 250 blocking DB calls per search. **Recommendation:** Pre-fetch all relevant source health data before scoring, or make `calculateSourceScore` a `suspend fun` and call it within coroutine context. | Line 134 |
| PIPE-2 | 🔴 Critical | **`runBlocking(Dispatchers.IO)` for provider health lookup on every score calculation.** At line 163, a second `runBlocking` call fetches `SourceHealthRepository.getProviderHealth(source.extensionId)`. This doubles the blocking database overhead described in PIPE-1. Combined, each `calculateSourceScore` invocation makes **two blocking database round-trips**. **Recommendation:** Same as PIPE-1 — batch-prefetch or convert to suspend. | Line 163 |
| PIPE-3 | 🟡 Medium | **`normalizeForTitleMatch` recompiles 5 `Regex` objects per invocation.** Lines 264–271 call `Regex(...)` five times inside `normalizeForTitleMatch()`. This function is called at least twice per ranked result (once for the title, once for the query). With 50 results, that's 500 regex compilations per search. **Recommendation:** Hoist all 5 regex patterns to `companion object` `private val` declarations. | Lines 264–271 |
| PIPE-4 | 🟡 Medium | **`Regex("[^a-z0-9]")` recompiled inside `merge()` hot loop.** Lines 472–473 inside `SearchResultMerger.merge()` create `Regex("[^a-z0-9]")` twice per media item in the title-matching logic. For searches touching many media items (e.g., catalog-wide matches), this adds unnecessary allocation pressure. **Recommendation:** Hoist to a companion object constant. | Lines 472–473 |
| PIPE-5 | 🟢 Info | **`healthLabelCache` uses `ConcurrentHashMap` — acceptable but grows unbounded.** The module-level `ConcurrentHashMap` at line 26 caches health labels keyed by source ID. This map is never evicted or bounded. Over many search sessions with different providers, it can accumulate stale entries. For typical usage (hundreds of unique source IDs), the memory impact is negligible, but it should be documented. | Line 26 |

---

### G. ChannelQueueManager — `feature/iptv/ChannelQueueManager.kt`

**Overall Assessment: ✅ Healthy — clean, bounded algorithms**

The queue manager uses efficient data structures and bounded iteration patterns.

| # | Severity | Finding | Location |
|---|---|---|---|
| CQ-1 | 🟢 Info | **`nextChannel()`/`previousChannel()` worst-case wraps entire queue.** If `skipBlocked` is true and all channels are blocked, the `do-while` loop traverses the entire queue before falling through. This is bounded by queue size (typically < 10K) and exits immediately on the first unblocked channel. The fallback at lines 96–97 and 114–115 correctly prevents infinite loops. | Lines 82–116 |

---

### H. StremioAddonClient — `core/network/StremioAddonClient.kt`

**Overall Assessment: ⚠️ Needs Attention — per-request JSON parser allocation**

| # | Severity | Finding | Location |
|---|---|---|---|
| NET-1 | 🟡 Medium | **`Json { ... }` parser instantiated per HTTP request.** Line 168 creates a new `kotlinx.serialization.json.Json` instance inside `safeGet<T>()` for every network request. `Json` builder calls are expensive — they configure serializers, compile descriptor caches, and allocate module registries. With parallel extension/stream fetches (10+ concurrent requests during search), this creates significant GC pressure. **Recommendation:** Hoist the `Json` instance to a `companion object` `private val`. | Line 168 |
| NET-2 | 🟡 Medium | **Response body fully materialized as `String` before size check.** At line 162, `response.bodyAsText()` reads the entire response into a `String`, followed by a `text.length > 5MB` check at line 163. If the `Content-Length` header is missing (common with chunked transfer encoding), a response larger than 5MB will be fully loaded into memory before being rejected. **Recommendation:** Use streaming body reading with a `ByteReadChannel` that aborts after 5MB, or configure Ktor's `ContentNegotiation` plugin with a size limit. | Lines 162–167 |

---

### I. SourceHealthRepository — `core/database/SourceHealthRepository.kt`

**Overall Assessment: ✅ Healthy — proper async patterns with IO dispatching**

| # | Severity | Finding | Location |
|---|---|---|---|
| SH-1 | 🟢 Info | **Read-then-write pattern in `getSourceHealth()` triggers conditional DB write.** At lines 76–78, if the domain object's health score differs from the stored entity (due to time-based recovery), the repository writes the updated entity back. This is intentional (lazy recovery persistence) but means read operations can trigger writes. On low-end devices under heavy read load (e.g., scoring 50 sources), this could cause unexpected write contention. The mitigation is that `withContext(dispatcher)` ensures these are on IO threads. | Lines 73–80 |

---

### J. UI Screens — Mobile & TV

**Overall Assessment: ✅ Healthy — proper Compose patterns with minor observations**

| # | Severity | Finding | Location |
|---|---|---|---|
| UI-1 | 🟡 Medium | **`DetailsScreen` / `TvDetailsScreen` launch unbounded parallel coroutines for each active extension.** Lines 181–304 in `DetailsScreen.kt` iterate `activeExtensions.forEach { launch(Dispatchers.IO) { ... } }` three times (meta, streams, subtitles). With 10 active extensions, this creates 30 concurrent coroutines performing network requests. On low-end devices with limited network bandwidth and CPU, this can cause thread pool exhaustion and ANR. **Recommendation:** Use `Semaphore(maxConcurrency)` or `limitedParallelism()` to cap concurrent extension requests (e.g., 4–6 at a time). | DetailsScreen.kt:181–304, TvDetailsScreen.kt:~170–290 |
| UI-2 | 🟢 Info | **`TvLiveGuideScreen` uses `remember { SimpleDateFormat(...) }` — safe within single Compose scope.** The `SimpleDateFormat` at line 90 is created once per composition lifecycle and only used within the single-threaded Compose recomposition loop. No thread-safety concern here. | TvLiveGuideScreen.kt:90 |

---

## 7. Prioritized Recommendations Summary

### 🔴 Critical (Fix Before Release)

1. **XMLTV-1**: Scanner preamble OOM risk — large `<channel>` sections before `<programme>` tags will be loaded entirely into memory. Affects low-end Android TV devices with 1GB RAM.
2. **PIPE-1**: `runBlocking(Dispatchers.IO)` in `calculateSourceScore()` — blocks the calling thread for source health DB lookup on every score calculation. Creates 250+ blocking DB calls per search.
3. **PIPE-2**: Second `runBlocking(Dispatchers.IO)` in `calculateSourceScore()` — doubles the blocking overhead with provider health DB lookup.

### 🟡 Medium (Should Fix)

4. **PIPE-3**: `normalizeForTitleMatch` regex recompilation — 5 regexes compiled per invocation, ~500 compilations per search.
5. **PIPE-4**: Inline regex compilation in `merge()` hot loop.
6. **NET-1**: `Json { ... }` parser instantiated per HTTP request — expensive constructor called 10+ times concurrently during search.
7. **NET-2**: Response body fully materialized before size check — potential 5MB+ String allocation without `Content-Length`.
8. **IPTV-5**: Tier 4 fuzzy match calls `.normalize()` per EPG ID without caching.
9. **XMLTV-2**: Regex recompilation per programme — hoist to companion object.
10. **XMLTV-3**: `SimpleDateFormat` thread-safety — switch to `DateTimeFormatter` or keep synchronized access.
11. **M3U-1**: `BufferedReader` resource leak — use `.use { }` block.
12. **SEARCH-1**: Priority-ordered await blocks fast results — consider completion-order emission.
13. **PB-1**: Document main-thread contract for `PlaybackManager` public API.
14. **UI-1**: Unbounded parallel extension coroutines in detail screens — add concurrency limit.
15. **IMG-1**: No custom `ImageLoader` configuration — default Coil settings risk OOM on low-end TV.

### 🟢 Low / Info (Nice to Have)

16. **M3U-2**: Hoist regex in `parseChannel`.
17. **XMLTV-4**: Scanner resource leak in exception path.
18. **SEARCH-2**: Repeated merge calls — acceptable at current scale.
19. **PB-2**: Progress polling granularity tradeoff — current 1s is acceptable.
20. **IPTV-6**: `UniversalSearchEngineImpl()` instantiated per search — use singleton or `remember`.
21. **PIPE-5**: `healthLabelCache` grows unbounded — acceptable for current scale.
22. **CQ-1**: Queue traversal worst-case — bounded and safe.
23. **SH-1**: Read-triggered write in health recovery — intentional design.
24. **UI-2**: `SimpleDateFormat` in Compose — safe within single-threaded scope.

---

## 8. Source Health & Telemetry Performance Strategy

To ensure telemetry gathering and health scoring do not introduce UI thread lag, battery drain, or database write thrashing, CalmSource implements a highly optimized health auditing strategy:

### A. Non-Blocking, Computationally Cheap Health Scoring
*   **Volatile Memory State Retrieval**: Instead of querying Room database tables synchronously during a search or stream selection, health status is read directly from memory-cached streams (`StateFlow<List<ExtensionProvider>>` and `StateFlow<List<IPTVProvider>>`). This eliminates disk I/O from the critical scoring path.
*   **O(1) / Small-Bounded Array Lookups**: Lookups are performed using lightweight linear scans (`firstOrNull`) over lists that represent configured providers. Because users typically configure fewer than 10 extensions or playlists, the overhead is negligible and runs in a fraction of a millisecond.
*   **Precomputed Scores for Rendering**: Detail screens (`DetailsScreen` and `TvDetailsScreen`) precompute and cache watch option scores using `remember(watchOptions, preferences)` instead of executing `SearchEngine.calculateScore` during Compose's recomposition loop.
*   **Primitive Parameter Passing**: The calculated score is passed directly as an `Int` parameter to list items (`ManualSourceItem` and `TvManualSourceItem`). This completely eliminates redundant StateFlow collections (`preferences.collectAsState`) in individual list item composables, preventing D-pad lag on low-end TVs.

> [!IMPORTANT]
> **Contradiction with PIPE-1/PIPE-2**: Section 8A describes the *intended* design where health status is read from memory-cached flows. However, `SearchResultPipeline.calculateSourceScore()` (PIPE-1, PIPE-2) currently bypasses this design and uses `runBlocking` to query the Room database synchronously. This is a gap between documentation and implementation that should be resolved by implementing the pre-fetch pattern described above.

### B. Asynchronous, Debounced Database Writes
*   **Off-UI-Thread Execution**: All database writes updating provider health (e.g., transitioning to `FAILED` or `ACTIVE`) are executed off the main thread on `Dispatchers.IO` using repository-specific supervised coroutine scopes.
*   **Write Debouncing**: Writes to the Room database for health updates are debounced or batched to prevent database write thrashing. Since health updates are only triggered on discrete lifecycle events (e.g., initial validation, consecutive connection failures, manual updates) rather than periodic progress ticks, database write frequency remains extremely low.
*   **Memory-First Broadcasting**: Telemetry changes update local memory cache flows immediately so that search and playback components react instantly. The corresponding SQLite database persistence completes asynchronously in the background.

### C. Search and Fallback Optimization
*   **Dynamic Health-Based Search Timeout Clamps**: Providers marked as `SLOW` or `FAILED` are automatically clamped to a shorter query timeout (e.g., $1000$ ms) during search dispatch, ensuring that slow endpoints do not bottleneck the progressive streaming of healthy results.
*   **Lightweight TV Playback Failure UI**: The TV playback failure overlay utilizes a minimal design (a single overlay container with high-contrast D-pad focus elements) instead of resource-intensive blur filters or background animations, keeping memory allocations negligible on 1GB RAM devices.
*   For complete details, see [SOURCE_HEALTH_AND_FALLBACK.md](./SOURCE_HEALTH_AND_FALLBACK.md).

## 9. Source Intelligence Performance Strategy

*   **Efficient Parsing & Modeling**: The Source Intelligence layer transforms raw, unstructured metadata into a uniform `ParsedSource` model using highly optimized parsers. We utilize compiled regular expressions and minimal allocations.
*   **No Parsing in Composables**: UI elements like `DetailsScreen` and `TvDetailsScreen` never directly invoke parser code during their layout pass or recomposition. Extracted properties are wrapped in `remember` blocks (or fetched via ViewModel state) to prevent heavy string-manipulation loops dragging down the UI frame rate.
*   **Parser Caching Strategy**: To avoid repeatedly parsing identical stream names (e.g. popular releases from different extensions returning the exact same filename), `SourceIntelligence.process()` is fronted by an `LruCache`. Re-evaluating 1,000 duplicated items now completes in micro-seconds.
*   **Ranking Performance Strategy**: Ranking operations within `SearchEngine` scale up efficiently because they execute off the main thread inside `Dispatchers.Default`. Database lookups for decayed source health are also set to read-only during search, preventing synchronous DB write spam that could stall pipeline emission.
*   **Low-End TV Risks**: On memory-constrained devices (1-2GB RAM Android TV boxes), iterating over thousands of streams simultaneously could trigger severe Garbage Collection pauses (GC jank) if not carefully tracked. By combining caching with non-blocking evaluation, UI scrolling through large manual source lists stays perfectly fluid.

## 10. Xtream Sync Performance Strategy (Mission 17)

### A. Batch Room Inserts
*   Large Xtream provider syncs (providers with 10,000+ channels and VOD entries) use batch Room inserts of **500 items per transaction**. This prevents SQLite transaction overhead from accumulating across thousands of individual inserts.
*   Each sync stage (live channels, VOD, series) uses `@Transaction` for atomic replacement (delete old provider data → insert new data), ensuring consistency without partial states.

### B. Off-Main-Thread Sync Pipeline
*   The entire Xtream sync pipeline (authentication → categories → channels → VOD → series → EPG) executes on `Dispatchers.IO`.
*   UI progress updates are posted to `StateFlow` from the IO thread and collected on the main thread via `collectAsState()`.
*   Each sync stage is individually cancellable without blocking the UI thread.

### C. Bounded Network Responses
*   Xtream API responses are subject to the existing Ktor response interceptor (5MB max per response body).
*   Providers with extremely large channel lists use chunked JSON parsing to avoid materializing the full response body as a single String in memory.
*   Network requests use Ktor `HttpClient` with configurable timeouts (connect: 10s, read: 30s) to prevent sync hangs on unresponsive servers.

### D. Lazy Stream URL Construction
*   Stream URLs are not pre-computed or cached during sync. They are built on-the-fly at playback time from `stream_id` (Room) + credentials (SecureTokenStore), eliminating the memory and storage overhead of persisting thousands of full URLs.


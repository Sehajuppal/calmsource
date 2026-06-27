# Regression Checklist

Run through these checks before approving major changes (especially those touching Universal Search, Extensions, or IPTV Core).

## Universal Search
- [x] Searching returns merged results without duplicates.
- [x] Extensions do not block the search indefinitely if slow.
- [x] Timeout policy correctly drops slow providers (default: 5000ms).

## IPTV & EPG
- [x] M3U parsing doesn't crash on malformed lines.
- [x] Channel groups correctly aggregate.
- [x] XMLTV EPG matches channels using 4-tier strategy.

## Extension Hub
- [x] Invalid JSON manifests are gracefully skipped.
- [x] Extensions missing required `id` or `name` are rejected.
- [x] Uninstalling/Disabling an extension immediately removes it from search.
- [x] Invalid URL safe failure (malformed URLs handled without crash).
- [x] Unsafe scheme rejection (e.g., file://, javascript: blocked).
- [x] HTTP warning shown for plain http:// connections.
- [x] HTTPS accepted securely.
- [x] Manifest preview shown before user confirms enable.
- [x] Invalid manifest safe failure (parsing errors don't crash).
- [x] Raw manifest hidden by default (behind advanced toggle).
- [x] Search still title-first (merged unified cards).
- [x] Spider-Man merge unchanged (IPTV, Extension, and Debrid tags all merge cleanly).
- [x] Mobile Extension Hub UI works properly.
- [x] Manifest installation confirm does not crash on malformed data or missing metadata.

## TV UI & D-pad
- [x] Focus correctly transitions between lists.
- [x] Lists use stable `key`s and do not trigger massive recomposition.
- [x] Settings screens scroll correctly under D-pad focus.
- [x] TV D-pad works correctly in Extension Hub split-pane.

## Security
- [x] No secrets logged (tokens/private URLs redacted).

## Persistence & Data
- [x] Large M3U or XMLTV inserts occur strictly off the Main Thread.
- [x] Flows mapped from DAOs do not over-emit (stateIn ensures distinct elements).
- [x] EPG queries do not compute large lists per row during lazy list recompositions.
- [x] New UI tests rely on substring or robust matching, rather than brittle exact phrasing.
- [x] Room database implementations generate correctly without compiler errors.

## Secure Storage Regression
- [x] Production app does not use fake secure storage — `CalmSourceApp` initializes `DebridRepository` with `EncryptedSecureTokenStore` via `DebridRepository.init(this)`.
- [x] Token (accessToken, refreshToken) never stored in Room — verified by `RoomSecurityAuditTest`.
- [x] API key never stored in Room — verified by `RoomSecurityAuditTest`.
- [x] Auth code/PIN/device code never stored in Room — verified by `RoomSecurityAuditTest` forbidden field scan.
- [x] Resolved links never stored in Room — verified by `RoomSecurityAuditTest` and only kept transiently in memory.
- [x] Disconnect clears secure storage — `deleteTokens(providerType)` removes all provider tokens from `SecureTokenStore`.
- [x] Account metadata remains after safe state changes only when intended — safe state changes (such as health updates or theme/preference changes) do not clear or corrupt account metadata.
- [x] UI masks API key — password visual transformation with visibility toggle in settings.
- [x] Logs redact secrets — `UrlRedactor` utility and `DebridTokenSet.toString()` override.
- [x] Stream Picker hides raw links — raw URLs and magnet info are collapsed behind an "Advanced" settings toggle.
- [x] Debrid metadata persists without secrets — `DebridAccountEntity` stores only `id`, `providerType`, `providerName`, `isConnected`, `email`, `username`, `health`.
- [x] Room persistence still works — DAO `Flow<List<T>>` emissions with `stateIn(SharingStarted.Eagerly)` remain functional.
- [x] Spider-Man merged result still works — IPTV, Extension, and Debrid source tags all merge into unified cards without duplication.

## Stremio Addon Compatibility Regression
- [x] Installed Stremio addon manifest loads
- [x] Catalog endpoint maps results
- [x] Search extra maps query
- [x] Meta endpoint maps details
- [x] Stream endpoint maps watch options
- [x] Subtitles endpoint maps availability
- [x] Configurable addon detected
- [x] `configurationRequired` addon blocked until configured
- [x] Secret config stored securely in SecureTokenStore
- [x] Raw URLs and filenames hidden in default stream picker views
- [x] Spider-Man merged result still works (IPTV, Extension, and Debrid options remain merged)
- [x] TV D-pad navigation works cleanly without focus traps
- [x] Mobile UI is responsive and works cleanly

## Playback & Media3 Foundation
- [x] Play Best Match opens Player screen correctly.
- [x] IPTV/Extension options open Player screen directly.
- [x] Invalid source shows unavailable state gracefully.
- [x] Raw playback URL hidden, not logged, and not persisted.
- [x] Player error does not leak URL to UI.
- [x] Mobile/TV overlays work with distinct UX.
- [x] Player releases correctly upon exit to prevent memory/audio leaks.

## Xtream Codes Regression
- [x] Xtream models defined (XtreamProviderConfig, XtreamLiveChannel, etc.).
- [x] IptvSecureTokenStore created to safely store passwords.
- [x] Passwords do NOT leak into Room database.
- [x] UI updated to show "Add Xtream Login" form.
- [x] Search continues to merge Xtream/VOD results cleanly.

## Real Source Import Stability (Mission 13.5)
- [x] M3U import via InputStream chunking does not OOM on large playlists (tested with 50k+ entries).
- [x] M3U parser handles malformed `#EXTINF` lines gracefully (no crash, skips invalid entries).
- [x] Channel language is dynamically parsed from `tvg-language` attribute (not hardcoded).
- [x] Channel resolution is dynamically inferred from metadata/group hints (not hardcoded).
- [x] XMLTV EPG Scanner-based chunking handles large program guides without memory spikes.
- [x] Import errors surface user-friendly messages without exposing raw file paths or URLs.
- [x] AsyncImage composables include meaningful contentDescription for accessibility.
- [x] AnimatedContent uses latest state snapshot (no stale closure on rapid interactions).
- [x] Settings shortcut cards respond to tap/click on Mobile and D-pad select on TV.

## Xtream Validation Edge Cases (Mission 13.5)
- [x] Xtream login with invalid credentials returns clear error, does not crash.
- [x] Xtream login with expired subscription handled gracefully (fallback state).
- [x] Xtream server timeout does not block the UI thread (async with coroutine timeout).
- [x] Xtream credentials stored exclusively in `IptvSecureTokenStore`, never in Room.
- [x] Xtream category/channel fetch handles empty responses without crash.
- [x] Xtream provider name field present on both Mobile and TV forms.

## Stremio Endpoint Robustness (Mission 13.5)
- [x] Stremio manifest fetch handles 404/500 errors gracefully (no crash, shows error state).
- [x] Stremio manifest with missing optional fields (e.g., no `logo`, no `background`) still parses.
- [x] Stremio catalog search with empty results returns empty list (no crash).
- [x] Stremio stream resolution with no available streams shows "No streams available" UI.
- [x] Stremio configurable addons prompt configuration before enabling.
- [x] Stremio addon secrets stored in SecureTokenStore, never logged or persisted in Room.
- [x] Stremio manifest size limited to 5MB to prevent abuse.

## Playback Edge Cases (Mission 13.5)
- [x] ExoPlayer handles invalid/unreachable stream URLs with user-friendly error message.
- [x] ExoPlayer does not leak audio when navigating away (DisposableEffect onDispose releases).
- [x] Live TV channel switch reuses ExoPlayer instance (no reconstruction per switch).
- [x] Player error state does not display or log raw stream URLs.
- [x] HLS, DASH, and MP4 stream types all load correctly.
- [x] Player overlay shows real progress/duration from ExoPlayer state (not hardcoded).
- [x] TV player overlay responds to D-pad play/pause/seek controls.
- [x] Mobile player overlay responds to touch gestures for play/pause/seek.

## Source Health & Fallback System (Mission 14)
- [x] Health Score matches telemetry inputs (calculates correct state based on latency and success rate).
- [x] Health state transitions dynamically (`HEALTHY` -> `SLOW` -> `FAILED`) based on network failure/latency.
- [x] Provider health state persists in database and survives orientation changes.
- [x] Database entities (`IPTVProviderEntity`, etc.) store only telemetry values, never credentials.
- [x] Consecutive player errors increment consecutive failure counters and trigger health downgrades.
- [x] FAILED providers enter backoff cooldown and skip search dispatch during active periods.
- [x] Providers automatically recover or trigger re-checking after the cooldown period.
- [x] Universal Search rankings penalize slow/failed providers (`SLOW`: -50, `FAILED`: -200) and boost healthy ones (`HEALTHY`: +50).
- [x] Player automatically resolves and falls back to the next best stream option in the queue on terminal playback errors.
- [x] Stream picker renders visual semantic health badges (Green/Active, Amber/Slow, Red/Failed).
- [x] Live TV channel failures trigger fallback to alternative stream URLs for the same channel.
- [x] Slow/failed Stremio addons are timed out (default 5000ms) without locking the search engine.
- [x] Addons requiring configuration are skipped during search dispatches.
- [x] Mobile Settings lists render clear HSL tailored semantic health colors.
- [x] TV Settings screen renders high-contrast, couch-readable health indicators.
- [x] Health checks run asynchronously on Dispatchers.IO and do not block the UI thread.
- [x] Playback URLs and secrets are redacted from health logs and telemetry reports.

## Source Health Stabilization (Mission 14.5)
- [x] Default fallback policy is ASK_BEFORE_FALLBACK (conservative).
- [x] PlaybackSourceType converter handles unknown enum values without crash.
- [x] Health table has bounded growth (30-day pruning strategy).
- [x] runBlocking in search scoring uses Dispatchers.IO (no main thread ANR).
- [x] No debug println statements in production scoring path.
- [x] Health score calculation is deterministic (pure functions on immutable data).
- [x] New sources default to score 100 (not unfairly punished).
- [x] Score bounds enforced (0-100, never negative or >100).
- [x] Recovery: +10 points/hour after 1 hour since last failure.
- [x] Reliability tiers map correctly (EXCELLENT/GOOD/UNSTABLE/POOR/BLOCKED).
- [x] Provider and source health are fully independent.
- [x] FallbackManager has no infinite retry loops.
- [x] ASK_BEFORE_FALLBACK does not auto-play.
- [x] Blocked sources are skipped during fallback.
- [x] Fallback terminates when all candidates exhausted.
- [x] Spider-Man merged result still works with health adjustments.
- [x] Failed source downranking does not create duplicate cards.
- [x] Health labels (Reliable, Unstable, etc.) are unobtrusive.
- [x] Advanced health details hidden by default.
- [x] No raw URLs stored in health entities.
- [x] No raw URLs shown in fallback failure UI.
- [x] TV D-pad accessible in fallback failure overlay.
- [x] IPTV channel switching stable with health data.
- [x] Extension endpoint failures tracked per endpoint type.
- [x] In-memory DAO fallback works for testing.

## Source Intelligence & Parser Abstraction (Mission 16)
- [x] Parsers correctly normalize varying provider metadata into uniform `ParsedSource` models.
- [x] Unknown or malformed fields in metadata are dropped or sanitized securely.
- [x] Source Ranker executes heuristic ranking correctly based on resolution, size, and debrid cache status.
- [x] Raw URLs and filenames are NEVER exposed to the UI by default.
- [x] Personally identifiable query parameters are stripped from telemetry or diagnostic logs.
- [x] The UI integration strictly abstracts technical parameters behind "More Info" toggles.
- [x] Fallback degradation smooths out any unparseable metadata response without breaking playback.

## Advanced Source Intelligence Stabilization (Mission 16.5)
- [x] `LanguageAndAudioParser` correctly functions as a singleton without instantiation errors.
- [x] `FileSizeAndPracticalityParser` accurately extracts raw bytes using `parseFileSize`.
- [x] UI screens (`DetailsScreen`, `TvDetailsScreen`) compile and properly map legacy search models to intelligence models.
- [x] `:core:sourceintelligence` module correctly resolves across all feature and app modules.
- [x] Large files (>20GB) are successfully penalized by the ranker when low-data mode is toggled.
- [x] Unknown/malformed release group titles degrade gracefully to UNKNOWN metadata values without crashing.

## Xtream-Compatible API Sync (Mission 17)

### Xtream Login & Authentication
- [x] Xtream login form works on Mobile (Server URL, Username, Password, Provider Name fields).
- [x] Xtream login form works on TV (D-pad accessible, all fields focusable).
- [x] Xtream login with invalid credentials returns clear error, does not crash.
- [x] Xtream login with expired subscription handled gracefully (user-friendly message).
- [x] Xtream server timeout does not block the UI thread (async with coroutine timeout).
- [x] Xtream provider name field present on both Mobile and TV forms.

### Xtream Credential Security
- [x] Xtream password stored only in `IptvSecureTokenStore` — never in Room.
- [x] Xtream password not stored in Room — verified by `RoomSecurityAuditTest` forbidden field scan.
- [x] Xtream credentials not logged — `UrlRedactor` strips credentials from all URLs before logging.
- [x] Xtream provider validates safely — invalid/expired credentials produce user-friendly errors, not raw API dumps.
- [x] Xtream provider removal clears credentials — `IptvSecureTokenStore.clearProvider()` called on deletion.
- [x] Stream URLs with embedded credentials are redacted via `PlaybackSource.redactUrl` before UI/log output.

### Xtream Sync Pipeline
- [x] Xtream live categories sync — `get_live_categories` fetched and persisted to `XtreamCategoryEntity`.
- [x] Xtream live streams sync — `get_live_streams` fetched and persisted to `IPTVChannelEntity`.
- [x] Xtream VOD categories sync — `get_vod_categories` fetched and persisted.
- [x] Xtream VOD streams sync — `get_vod_streams` fetched and persisted to `XtreamVodEntity`.
- [x] Xtream series metadata sync — `get_series` fetched and persisted to `XtreamSeriesEntity`.
- [x] Xtream metadata persists across app restarts (Room database persistence).
- [x] Xtream sync progress displayed in UI (stage indicator with percentage).
- [x] Xtream sync cancellation works without data corruption (partial data retained).
- [x] Xtream category/channel fetch handles empty responses without crash.

### Xtream Search Integration
- [x] Xtream channels appear in Live TV section and Universal Search.
- [x] Xtream VOD content appears in Universal Search (Movies/IPTV VOD groups).
- [x] Xtream series content appears in Universal Search (Shows group).
- [x] Xtream content appears in Universal Search merged with extension/debrid results.
- [x] Spider-Man merged result still works (IPTV, Xtream, Extension, Debrid tags all merge cleanly).

### Xtream Playback
- [x] Xtream stream opens Player through `PlaybackSource` pipeline.
- [x] Xtream live stream URL built lazily at playback time from `stream_id` + credentials.
- [x] Xtream VOD stream URL built lazily with correct `container_extension`.
- [x] Raw stream URLs hidden — never persisted, logged, or shown in UI.
- [x] ExoPlayer handles Xtream stream errors with user-friendly messages (no credential leaks).

### Existing Functionality Preservation
- [x] IPTV M3U import still works — M3U-sourced channels unaffected by Xtream additions.
- [x] XMLTV EPG still works — EPG matching and guide display unaffected.
- [x] Extension Hub still works — Stremio addons, search, and stream resolution unaffected.
- [x] Debrid Connect still works — OAuth flows, token storage, availability checks unaffected.
- [x] Playback foundation still works — ExoPlayer lifecycle, fallback, error handling unaffected.
- [x] Source Health system still works — health scoring, telemetry, fallback policies unaffected.
- [x] Source Intelligence still works — parser abstraction, ranking, privacy rules unaffected.

### Xtream Performance
- [x] Xtream sync runs off-main-thread (Dispatchers.IO).
- [x] Batch Room inserts (500 items/transaction) for large providers.
- [x] Xtream API responses bounded by 5MB interceptor.
- [x] Stream URLs not pre-computed during sync (lazy construction at playback time).

## Xtream Sync Stabilization (Mission 17.5)

### Test Suite Integrity
- [x] All 207+ Xtream test methods pass with correct assertions.
- [x] All test data uses synthetic `example.com` URLs — zero real provider URLs or credentials.
- [x] `FakeInMemoryIptvSecureTokenStore` uses `ConcurrentHashMap` for thread-safe test isolation.
- [x] `FakeHttpClientEngine` properly implements Ktor `HttpClientEngine` for mock network tests.
- [x] DTO default values tested for all content types (categories, live, VOD, series, EPG).
- [x] Entity round-trip tests verify `toEntity()` → `toDomain()` preserves all fields including null/empty edge cases.

### Security Boundary Verification
- [x] Reflection-based field scan on `IPTVProviderEntity` confirms no `password`/`secret`/`token` fields.
- [x] Reflection-based field scan on 6+ domain models (`XtreamProviderConfig`, `XtreamCredentialsRef`, `XtreamSyncProgress`, `XtreamLiveChannel`, `XtreamVodItem`, `XtreamSeriesItem`) confirms no credential fields.
- [x] Source file scan verifies no `Log.*`/`println`/`Timber.*` calls with credential-related keywords in Xtream code path.
- [x] `XtreamProviderConfig.toString()` confirmed not to leak password (no password field exists).
- [x] `FakeInMemoryIptvSecureTokenStore.toString()` confirmed not to expose stored password values.
- [x] `clearProvider()` prefix isolation: clearing `prov-1` does not affect `prov-10` or `prov-100`.

### URL Redaction Verification
- [x] `UrlRedactor.redactUrl()` strips `username` and `password` from query-based Xtream API URLs.
- [x] `UrlRedactor.redactUrl()` strips credentials from path-based Xtream stream URLs (`/live/`, `/movie/`, `/series/`).
- [x] `UrlRedactor.redactErrorMessage()` strips embedded credential URLs from error text.
- [x] `UrlRedactor.redactPrivateLink()` strips path credentials from Xtream stream URLs.
- [x] Stream URL pseudo-scheme (`xtream://stream_id/{id}`) does not contain credentials.

### Unsafe Scheme Rejection Verification
- [x] `file://`, `javascript:`, `data:`, `content://`, `ftp://` schemes all rejected by `validateServerUrl`.
- [x] `file://`, `javascript:`, `data:` schemes rejected by `buildLiveStreamUrl`/`buildVodStreamUrl` at build time.
- [x] Case-insensitive scheme validation (`FILE://`, `HTTP://`) works correctly.

### Documentation Consistency
- [x] XTREAM_SYNC.md credential storage rules match SECURE_STORAGE.md and SECURITY.md.
- [x] Known Xtream limitations consistent between XTREAM_SYNC.md §16 and KNOWN_LIMITATIONS.md #16-#20.
- [x] BUG_INDEX.md updated with Mission 17 audit note — no new bugs.
- [x] All Xtream-related docs cross-reference correctly.

### Implementation Quality
- [x] URL-encoding of special characters (`&`, `@`, `=`, `#`, spaces) in Xtream credentials verified.
- [x] Trailing slash normalization in server URLs verified for all URL builders.
- [x] Missing/malformed JSON fields handled gracefully (skipped, not crashed) for all content types.
## Reality Audit & Technical Debt Cleanup (Mission 18)

### UI Crash Prevention
- [x] Clicking on Catalog Extensions with no extensions selected displays a calm placeholder state instead of throwing NullPointerException.
- [x] Unexhaustive `when` cases on sealed interfaces in TV settings screens prevented by matching all interface variants and nullability check double-bang operators.
- [x] All settings widgets on mobile are linked to repository state for real preferences persistence.

### Database Layer Kotlin Standardizations
- [x] All 11 Room entity and 6 DAO classes converted to Kotlin with identical table schemas, indexes, and annotation definitions.
- [x] All Java files deleted to prevent duplicate class conflicts on the build classpath.
- [x] Converters replaced `org.json` usages with `kotlinx.serialization` JSON codec for unified type conversion logic.
- [x] Room unit tests pass completely.




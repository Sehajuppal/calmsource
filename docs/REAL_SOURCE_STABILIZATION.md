# Real Source Stabilization Report — Mission 13.5

**Date:** 2026-06-06
**Mission:** 13.5 — Real-Source Stabilization & Bug Sweep

---

## 1. Real Source Types Tested

| Source Type | Scope | Status |
|---|---|---|
| Standard M3U Playlist | Import, parse (InputStream chunking), channel grouping, EPG matching | ✅ Stable |
| Xtream Codes API | Login validation, credential secure storage, live/VOD category fetch | ✅ Stable |
| Stremio Addon v3 | Manifest fetch, catalog search, meta/stream/subtitle resolution | ✅ Stable |
| Debrid (Real-Debrid / AllDebrid / Premiumize) | Device code, PIN, API key auth flows; token lifecycle | ✅ Stable |
| XMLTV EPG | Scanner-based chunked parsing, 4-tier channel matching | ✅ Stable |

---

## 2. Sanitized Issue Summary

All testing was performed with sanitized, non-production credentials and public test streams. No real user credentials, private URLs, tokens, or API keys were used or stored in documentation, test fixtures, or logs.

---

## 3. Bugs Found & Fixed

### Previously Open Bugs (All Fixed)

| Bug ID | Title | Fix Summary |
|---|---|---|
| 38 | AsyncImage missing contentDescription | Added meaningful `contentDescription` to all `AsyncImage` composables across 9 mobile+TV files. |
| 39 | Hardcoded IPTV channel language ("Hindi") | Added `ChannelMapper.extractLanguage()` + pipeline-level `inferLanguageFromName()` heuristic. |
| 40 | Hardcoded IPTV channel resolution ("1080p") | Added `ChannelMapper.extractResolution()` + pipeline uses `"Live"` label for live channels. |
| 41 | Settings shortcut cards not clickable | Added `onClick` parameter to `SettingsShortcutCard` and wired through `GlassCard`. |
| 43 | AnimatedContent stale closure on rapid taps | Used `rememberUpdatedState` to capture current state properly in transition lambdas. |

### Critical Bugs Discovered & Fixed

| ID | Title | Area | Fix |
|---|---|---|---|
| NEW | XMLTV Scanner preamble OOM | Parser | Pre-scan with BufferedReader to skip 50MB+ preambles before entering Scanner chunking. |
| NEW | `parsedPrograms` race condition | IPTVRepository | Wired up `dataLock` synchronization across all shared mutable state reads/writes. |
| NEW | `StremioSubtitle` deserialization crash | StremioModels | Added default values for `id`, `lang`, `url` fields. |
| NEW | `coroutineScope` cancellation cascade | ExtensionSearch | Replaced with `supervisorScope` + per-addon try/catch isolation. |
| NEW | `ExtensionManifestParser` crash on non-array resources | Parser | Added type-checking guards (`is JsonArray`, `is JsonPrimitive`). |
| NEW | Ktor logger leaking URL paths with credentials | NetworkClient | Restricted to `LogLevel.INFO`, route all URLs through `PlaybackSource.redactUrl()`. |
| NEW | `UrlRedactor` missing basic auth stripping | UrlRedactor | Now always strips `user:pass@` from URLs + added `username` to redaction params. |
| NEW | `TokenMasking` boundary bug | TokenMasking | Fixed `>` → `>=` for `MIN_MASKABLE_LENGTH`. |
| NEW | Priority-ordered search blocks fast providers | SearchEngine | Replaced with `Channel`-based completion-order pattern. |

### Performance Bugs Fixed

| ID | Area | Fix |
|---|---|---|
| IPTV-4 | EPG Matching | O(N×M) → O(N+M) via pre-computed normalized EPG ID map. |
| XMLTV-2 | Regex allocation | All regex patterns hoisted to companion-level `private val`. |
| XMLTV-3 | SimpleDateFormat thread safety | Synchronized access to formatter instances. |
| M3U-1 | Resource leak | `BufferedReader` now uses `.use { }` for proper cleanup. |
| M3U-2 | Regex per-call | `ATTRIBUTE_REGEX` and `DURATION_PREFIX_REGEX` hoisted to companion. |

---

## 4. Bugs Deferred

> None. All discovered issues were addressed.

---

## 5. Security & Privacy Review

- **Credential Isolation:** Verified Xtream passwords, Debrid tokens, and Stremio addon secrets remain exclusively in `SecureTokenStore`. No secrets enter Room entities.
- **Log Redaction:** `UrlRedactor` actively sanitizes all logged URLs, stripping credentials, tokens, basic auth, and private path segments.
- **Xtream URL Redaction:** `username` parameter now included in redaction list alongside `password`.
- **Basic Auth Stripping:** `user:pass@host` patterns are now fully stripped in all URL redaction paths.
- **UI Masking:** API keys masked with password visual transformation; raw stream URLs hidden behind "Advanced" toggle.
- **Room Audit:** `RoomSecurityAuditTest` + `IptvSecurityAuditTest` reflection scans confirm zero forbidden credential fields across all entity classes.
- **No Credential Artifacts:** Codebase scan confirmed zero instances of test credentials in source code. UI dump XML files removed and added to `.gitignore`.
- **Ktor Logger:** Locked down to `LogLevel.INFO` with regex-based URL redaction before any output.

---

## 6. Playback Review

- **ExoPlayer Lifecycle:** Player correctly initializes on screen entry and releases via `DisposableEffect.onDispose`. Double-release is safe no-op.
- **Thread Safety:** All player-interacting methods annotated with `@MainThread`. Class-level KDoc documents the thread contract.
- **Rapid Channel Switching:** 150ms debounce prevents flooding ExoPlayer with prepare() calls during fast D-pad navigation.
- **Error Coverage:** New `PlaybackError.ServerRefused` variant covers HTTP 403, connection refused, and cleartext blocked. Timeout separated from generic Network error.
- **Privacy:** Raw playback URLs never persisted, logged, or displayed. Error messages use `errorCodeName` only.

---

## 7. Search Review

- **Universal Search:** Merged results from IPTV, Extension, Stremio, and Debrid sources display correctly without duplicates.
- **Title Normalization:** Year suffixes `(2017)`, resolution tags `[HD]`, and language suffixes are now stripped for matching.
- **Stream Title Cleaning:** Raw filenames like `Movie.2024.1080p.WEB-DL.x264-GROUP.mkv` produce clean `"Movie 2024"` labels.
- **Completion-Order Results:** Fast providers appear immediately; slow providers no longer block the UI.
- **Spider-Man Regression:** All merge, language, dual-audio, and watch-option assertions preserved.

---

## 8. Stremio Addon Review

- **Subtitle Robustness:** Missing `lang`/`id` fields no longer crash deserialization.
- **Addon Isolation:** `supervisorScope` ensures one crashing addon cannot cancel others.
- **Manifest Parsing:** Non-array `resources`/`types` handled gracefully with warnings.
- **URL Encoding:** Path segments are now properly URL-encoded to prevent path traversal.
- **Base URL Validation:** Empty/non-HTTP(S) base URLs rejected early with clear errors.

---

## 9. Xtream Setup Review

- **Input Validation:** Server URL, username, and password validated with human-readable error messages.
- **Secure Storage:** Password stored exclusively in `IptvSecureTokenStore`. Verified by reflection-based audit tests.
- **Provider Deletion:** Correctly clears both Room metadata AND SecureTokenStore credentials, with prefix-isolation safety.
- **28 validation edge case tests** + **11 secure store lifecycle tests** added.

---

## 10. Mobile / TV Review

- **Accessibility:** All `AsyncImage` composables now include meaningful `contentDescription` attributes.
- **Settings Cards:** Shortcut cards are now clickable on both Mobile and TV.
- **AnimatedContent:** Stale closure fixed with `rememberUpdatedState`.
- **Text Overflow:** `maxLines` + `TextOverflow.Ellipsis` applied to all list labels.
- **TV D-pad:** `TvLazyColumn`/`TvLazyRow` migration verified complete. No focus traps detected.

---

## 11. Performance Review

- **XMLTV Parsing:** Preamble skip prevents OOM on large EPG files. Regex hoisting eliminates 250K+ object allocations.
- **IPTVRepository:** Thread-safe via `dataLock`. EPG matching reduced from O(N×M) to O(N+M).
- **M3U Parsing:** Proper resource cleanup via `.use {}`. Regex hoisted to companion.
- **Search Pipeline:** Completion-order emission pattern eliminates artificial blocking.
- **Player:** 150ms debounce on rapid channel switching. Progress state separated from main player state.

---

## 12. Remaining Blockers

> [!NOTE]
> **No critical blockers remain.** All discovered bugs have been fixed. The architecture is stable for real-source usage.

Minor items tracked in `KNOWN_LIMITATIONS.md`:
- No Track Selection UI in Player (subtitle/audio track picker)
- No Picture-in-Picture (PiP) lifecycle integration
- No Certificate Pinning for debrid API endpoints
- No Biometric Authentication for sensitive actions

---

## 13. Recommended Next Mission

**Suggested focus areas for Mission 14:**

1. **Track Selection UI** — Build subtitle/audio track picker overlay for ExoPlayer on both Mobile and TV.
2. **Picture-in-Picture (PiP)** — Implement Android PiP lifecycle for background playback.
3. **Certificate Pinning** — Add OkHttp `CertificatePinner` for debrid API endpoints.
4. **Token Refresh Automation** — Implement background OAuth token refresh via `WorkManager`.
5. **Catalog Pagination** — Implement Stremio `skip`-based infinite scroll for catalog browsing.

# CalmSource QA Test Matrix

> **Generated:** 2026-06-06 | **Missions Covered:** 1–14.5 | **Total Items:** 160
>
> All items are **PENDING** until verified by the QA lead. Mark each as ✅ PASS, ❌ FAIL, or ⏭️ SKIP with notes.

---

## 1. Mobile App (20 items)

| ID | Area | Test Description | Status | Notes |
|----|------|-----------------|--------|-------|
| MOB-01 | Navigation | Bottom navigation switches between Home, Search, Live TV, Settings tabs | PENDING | |
| MOB-02 | Navigation | Back button from nested screens returns to parent tab | PENDING | |
| MOB-03 | Search | Search input debounces and returns merged results | PENDING | |
| MOB-04 | Search | Empty search query shows search suggestions / shortcuts | PENDING | |
| MOB-05 | Details | Details screen loads metadata (poster, title, description) | PENDING | |
| MOB-06 | Details | Stream picker shows watch options with source badges | PENDING | |
| MOB-07 | Settings | Settings cards are tappable and expand/collapse correctly | PENDING | |
| MOB-08 | Settings | IPTV provider list renders with health indicators | PENDING | |
| MOB-09 | Settings | Extension Hub split-pane renders on mobile (list + detail) | PENDING | |
| MOB-10 | Settings | Debrid accounts list shows connected/disconnected state | PENDING | |
| MOB-11 | Settings | Source priority reordering works via drag or tap | PENDING | |
| MOB-12 | Settings | Source priority screen has verticalScroll (Bug #34 regression) | PENDING | |
| MOB-13 | Player | Player screen opens and plays test HLS stream | PENDING | |
| MOB-14 | Player | Play/pause icon toggles correctly (Bug #18 regression) | PENDING | |
| MOB-15 | Player | Player overlay shows real progress/duration (Bug #42 regression) | PENDING | |
| MOB-16 | Player | Player releases on back navigation (no audio leak, Bug #51 regression) | PENDING | |
| MOB-17 | Accessibility | All AsyncImage composables have contentDescription (Bug #38 regression) | PENDING | |
| MOB-18 | Accessibility | Settings shortcut cards respond to tap (Bug #41 regression) | PENDING | |
| MOB-19 | UI State | AnimatedContent shows correct state on rapid tab switches (Bug #43 regression) | PENDING | |
| MOB-20 | Security | API key field uses password mask with visibility toggle | PENDING | |

---

## 2. TV App / D-pad (20 items)

| ID | Area | Test Description | Status | Notes |
|----|------|-----------------|--------|-------|
| TV-01 | Navigation | D-pad navigates between top-level rails (Home, Search, Live TV, Settings) | PENDING | |
| TV-02 | Navigation | D-pad focus transitions smoothly between list rows | PENDING | |
| TV-03 | Focus | TvFocusCard clickable applied after focusable (Bug #36 regression) | PENDING | |
| TV-04 | Focus | TvFocusCard uses graphicsLayer for scale (Bug #37 regression) | PENDING | |
| TV-05 | Focus | Lists use stable key() blocks to prevent recomposition lag (Bug #3 regression) | PENDING | |
| TV-06 | Search | TV search overlay accepts D-pad text input and returns results | PENDING | |
| TV-07 | Details | TV details screen shows metadata and watch options | PENDING | |
| TV-08 | Details | Stream picker navigable via D-pad with source badges | PENDING | |
| TV-09 | Settings | TV settings split-pane renders with D-pad navigation | PENDING | |
| TV-10 | Settings | Extension Hub D-pad works in split-pane (no focus traps) | PENDING | |
| TV-11 | Settings | TV priorities screen has verticalScroll (Bug #35 regression) | PENDING | |
| TV-12 | Player | TV player overlay responds to D-pad play/pause/seek | PENDING | |
| TV-13 | Player | TV seekbar visible (no negative height, Bug #19 regression) | PENDING | |
| TV-14 | Player | TV player releases on exit (no audio leak) | PENDING | |
| TV-15 | Live TV | D-pad channel switching stable with health data | PENDING | |
| TV-16 | Live TV | TV Live Guide renders EPG schedule with O(1) lookup | PENDING | |
| TV-17 | Debrid | TV Debrid connect flow wrapped in Column (Bug #8 regression) | PENDING | |
| TV-18 | Health UI | TV settings renders high-contrast health indicators | PENDING | |
| TV-19 | Fallback | Fallback failure overlay D-pad accessible | PENDING | |
| TV-20 | Accessibility | TV composables include contentDescription for screen readers | PENDING | |

---

## 3. IPTV / EPG / Xtream (20 items)

| ID | Area | Test Description | Status | Notes |
|----|------|-----------------|--------|-------|
| IPTV-01 | M3U Parser | Malformed #EXTINF lines are skipped gracefully (no crash) | PENDING | |
| IPTV-02 | M3U Parser | Large playlists (50k+ entries) use InputStream chunking (no OOM) | PENDING | |
| IPTV-03 | M3U Parser | tvg-language attribute dynamically parsed (Bug #39 regression) | PENDING | |
| IPTV-04 | M3U Parser | Resolution inferred from metadata/group hints (Bug #40 regression) | PENDING | |
| IPTV-05 | Channel Groups | Channels correctly grouped by group-title | PENDING | |
| IPTV-06 | EPG Matching | 4-tier match strategy (ID → Name → Normalized → Fuzzy) works | PENDING | |
| IPTV-07 | EPG Parser | XMLTV parser uses Locale.ROOT for dates (Bug #24 regression) | PENDING | |
| IPTV-08 | EPG Parser | Large XMLTV files use Scanner-based chunking (no memory spike) | PENDING | |
| IPTV-09 | EPG Display | EPG programs linked to channels using precomputed Map (O(1) lookup) | PENDING | |
| IPTV-10 | Repository | syncEPG scoped by channelId, not wiping all providers (Bug #17 regression) | PENDING | |
| IPTV-11 | Repository | StateFlow updates use atomic .update{} (Bug #14 regression) | PENDING | |
| IPTV-12 | Repository | Mutable collections use synchronized(dataLock) (Bug #13 regression) | PENDING | |
| IPTV-13 | Repository | Dead .trim() after regex strip removed (Bug #44 regression) | PENDING | |
| IPTV-14 | Xtream | Xtream login with invalid credentials returns clear error | PENDING | |
| IPTV-15 | Xtream | Xtream login with expired subscription handled gracefully | PENDING | |
| IPTV-16 | Xtream | Xtream server timeout does not block UI thread | PENDING | |
| IPTV-17 | Xtream | Xtream credentials stored in IptvSecureTokenStore only (never Room) | PENDING | |
| IPTV-18 | Xtream | Xtream category/channel fetch handles empty responses | PENDING | |
| IPTV-19 | Xtream | Xtream provider name field present on Mobile and TV forms | PENDING | |
| IPTV-20 | Import Errors | Import errors show user-friendly messages (no raw file paths/URLs) | PENDING | |

---

## 4. Extensions / Stremio (20 items)

| ID | Area | Test Description | Status | Notes |
|----|------|-----------------|--------|-------|
| EXT-01 | Manifest Parser | Invalid JSON manifests gracefully skipped | PENDING | |
| EXT-02 | Manifest Parser | Extensions missing id or name are rejected | PENDING | |
| EXT-03 | Manifest Parser | Non-primitive resources handled without crash (Bug #25 regression) | PENDING | |
| EXT-04 | Manifest Parser | Non-primitive hints handled via type dispatch (Bug #26 regression) | PENDING | |
| EXT-05 | Extension Hub | Uninstalling/disabling removes extension from search immediately | PENDING | |
| EXT-06 | Extension Hub | Invalid URL handled without crash (safe failure) | PENDING | |
| EXT-07 | Extension Hub | Unsafe schemes (file://, javascript:) blocked | PENDING | |
| EXT-08 | Extension Hub | HTTP warning shown for plain http:// connections | PENDING | |
| EXT-09 | Extension Hub | Manifest preview shown before user confirms enable | PENDING | |
| EXT-10 | Extension Hub | Raw manifest hidden by default (behind advanced toggle) | PENDING | |
| EXT-11 | Extension Hub | Timeout policy drops slow providers at 5000ms (Bug #27 regression) | PENDING | |
| EXT-12 | Stremio | Stremio manifest fetch handles 404/500 errors gracefully | PENDING | |
| EXT-13 | Stremio | Stremio manifest with missing optional fields (logo, background) parses | PENDING | |
| EXT-14 | Stremio | Stremio catalog search with empty results returns empty list | PENDING | |
| EXT-15 | Stremio | Stremio stream resolution with no streams shows "No streams" UI | PENDING | |
| EXT-16 | Stremio | Configurable addons prompt configuration before enabling | PENDING | |
| EXT-17 | Stremio | Stremio addon secrets stored in SecureTokenStore (never Room) | PENDING | |
| EXT-18 | Stremio | Stremio manifest size limited to 5MB | PENDING | |
| EXT-19 | Stremio | p2p/adult behaviorHints show safety warnings (Bug #49 regression) | PENDING | |
| EXT-20 | Stremio | configurationRequired addons blocked until configured | PENDING | |

---

## 5. Playback (20 items)

| ID | Area | Test Description | Status | Notes |
|----|------|-----------------|--------|-------|
| PLAY-01 | ExoPlayer | HLS stream loads and plays correctly | PENDING | |
| PLAY-02 | ExoPlayer | DASH stream loads and plays correctly | PENDING | |
| PLAY-03 | ExoPlayer | MP4 stream loads and plays correctly | PENDING | |
| PLAY-04 | ExoPlayer | Invalid/unreachable stream URL shows user-friendly error | PENDING | |
| PLAY-05 | ExoPlayer | Player error does not leak URL to UI | PENDING | |
| PLAY-06 | Lifecycle | Player releases on exit (DisposableEffect onDispose) | PENDING | |
| PLAY-07 | Lifecycle | Live TV channel switch reuses ExoPlayer instance | PENDING | |
| PLAY-08 | Mobile Overlay | Touch gestures for play/pause/seek work | PENDING | |
| PLAY-09 | TV Overlay | D-pad play/pause/seek controls work | PENDING | |
| PLAY-10 | Progress | Player overlay shows real progress/duration from ExoPlayer state | PENDING | |
| PLAY-11 | Privacy | Raw playback URL hidden, not logged, not persisted | PENDING | |
| PLAY-12 | Fallback | Player auto-resolves next best stream on terminal error | PENDING | |
| PLAY-13 | Fallback | Default policy is ASK_BEFORE_FALLBACK (Bug #52 regression) | PENDING | |
| PLAY-14 | Fallback | ASK_BEFORE_FALLBACK does not auto-play | PENDING | |
| PLAY-15 | Fallback | Blocked sources skipped during fallback | PENDING | |
| PLAY-16 | Fallback | Fallback terminates when all candidates exhausted | PENDING | |
| PLAY-17 | Fallback | No infinite retry loops in FallbackManager | PENDING | |
| PLAY-18 | Fallback | No raw URLs shown in fallback failure UI | PENDING | |
| PLAY-19 | Live TV | Live TV channel failure triggers fallback to alt stream URLs | PENDING | |
| PLAY-20 | Database | PlaybackSourceType converter handles unknown values (Bug #53 regression) | PENDING | |

---

## 6. Search / Ranking / Fallback (20 items)

| ID | Area | Test Description | Status | Notes |
|----|------|-----------------|--------|-------|
| SRCH-01 | Core | Search returns merged results without duplicates | PENDING | |
| SRCH-02 | Core | Extensions do not block search if slow (timeout at 5000ms) | PENDING | |
| SRCH-03 | Core | CancellationException rethrown (no coroutine leak, Bug #1 regression) | PENDING | |
| SRCH-04 | Core | SearchEngine uses lazy singleton (Bug #30 regression) | PENDING | |
| SRCH-05 | Scoring | N+1 scoring fixed — pre-computed associateWith map (Bug #28 regression) | PENDING | |
| SRCH-06 | Scoring | Dead resolution branches removed (Bug #29 regression) | PENDING | |
| SRCH-07 | Scoring | No runBlocking on main thread (uses Dispatchers.IO, Bug #55 regression) | PENDING | |
| SRCH-08 | Scoring | No debug println in production scoring path (Bug #56 regression) | PENDING | |
| SRCH-09 | Health Ranking | SLOW providers penalized (-50 score) | PENDING | |
| SRCH-10 | Health Ranking | FAILED providers penalized (-200 score) | PENDING | |
| SRCH-11 | Health Ranking | HEALTHY providers boosted (+50 score) | PENDING | |
| SRCH-12 | Health Ranking | Failed source downranking does not create duplicate cards | PENDING | |
| SRCH-13 | Merge | Spider-Man merge works (IPTV + Extension + Debrid tags merge) | PENDING | |
| SRCH-14 | Merge | Health adjustments preserve merged card integrity | PENDING | |
| SRCH-15 | Debrid | Debrid availability boosts ranking in search pipeline | PENDING | |
| SRCH-16 | Language | Language scoring uses dynamic tvg-language (not hardcoded) | PENDING | |
| SRCH-17 | Resolution | Resolution scoring uses dynamic metadata (not hardcoded) | PENDING | |
| SRCH-18 | Dispatch | FAILED providers in backoff cooldown skipped | PENDING | |
| SRCH-19 | Dispatch | configurationRequired addons skipped during dispatch | PENDING | |
| SRCH-20 | Dispatch | Slow/failed Stremio addons timed out without locking engine | PENDING | |

---

## 7. Persistence / Security (20 items)

| ID | Area | Test Description | Status | Notes |
|----|------|-----------------|--------|-------|
| SEC-01 | Room | No forbidden credential fields in any of 7 Room entities | PENDING | |
| SEC-02 | Room | DebridAccountEntity has only non-secret fields | PENDING | |
| SEC-03 | Room | Tokens never stored in Room (RoomSecurityAuditTest) | PENDING | |
| SEC-04 | Room | Xtream passwords never stored in Room | PENDING | |
| SEC-05 | SecureTokenStore | Production uses EncryptedSecureTokenStore (not fake) | PENDING | |
| SEC-06 | SecureTokenStore | Disconnect clears all provider tokens | PENDING | |
| SEC-07 | SecureTokenStore | Graceful crypto failure (try-catch, no crash) | PENDING | |
| SEC-08 | SecureTokenStore | Stremio addon secrets stored in SecureTokenStore | PENDING | |
| SEC-09 | Redaction | UrlRedactor masks tokens in URLs | PENDING | |
| SEC-10 | Redaction | DebridTokenSet.toString() masks credentials | PENDING | |
| SEC-11 | Redaction | No private links logged (playback URLs redacted) | PENDING | |
| SEC-12 | Redaction | Player errors do not leak URLs to UI | PENDING | |
| SEC-13 | UI | API key masked with password visual transformation | PENDING | |
| SEC-14 | UI | Stream Picker hides raw links (behind Advanced toggle) | PENDING | |
| SEC-15 | UI | Raw extension manifest hidden by default | PENDING | |
| SEC-16 | Persistence | DAO StateFlow emissions do not over-emit (distinctUntilChanged) | PENDING | |
| SEC-17 | Persistence | Large M3U/XMLTV inserts on Dispatchers.IO (off main thread) | PENDING | |
| SEC-18 | Persistence | Health entities store no raw URLs | PENDING | |
| SEC-19 | Persistence | Health table has 30-day pruning (Bug #54 regression) | PENDING | |
| SEC-20 | Database | Manifest serialization uses kotlinx.serialization (Bug #48 regression) | PENDING | |

---

## 8. Performance (20 items)

| ID | Area | Test Description | Status | Notes |
|----|------|-----------------|--------|-------|
| PERF-01 | EPG | EPG lookup uses precomputed Map (O(1), not O(N) per row) | PENDING | |
| PERF-02 | Search | Search scoring pre-computed in associateWith map (no N+1) | PENDING | |
| PERF-03 | Search | SearchEngine lazy singleton (no per-search instantiation) | PENDING | |
| PERF-04 | Search | No runBlocking on main thread in scoring | PENDING | |
| PERF-05 | M3U | InputStream-based chunked reading for large playlists | PENDING | |
| PERF-06 | XMLTV | Scanner-based chunking for large program guides | PENDING | |
| PERF-07 | TV Lists | Stable key() blocks prevent massive recomposition | PENDING | |
| PERF-08 | TV Focus | graphicsLayer used instead of scale (no layout shifts) | PENDING | |
| PERF-09 | StateFlow | Atomic .update{} ops for non-atomic StateFlow fixes | PENDING | |
| PERF-10 | StateFlow | Flows mapped from DAOs use stateIn (distinct elements) | PENDING | |
| PERF-11 | Collections | synchronized(dataLock) for mutable collections | PENDING | |
| PERF-12 | Collections | @Volatile on shared mutable state (Bug #31 regression) | PENDING | |
| PERF-13 | Thread Safety | ConcurrentHashMap in SecureTokenStore (Bug #21 regression) | PENDING | |
| PERF-14 | Thread Safety | AtomicInteger for pollCount in fake clients (Bug #22 regression) | PENDING | |
| PERF-15 | Health Check | Health checks run on Dispatchers.IO (not UI thread) | PENDING | |
| PERF-16 | Health Score | Score calculation deterministic (pure functions on immutable data) | PENDING | |
| PERF-17 | Health Score | New sources default to score 100 | PENDING | |
| PERF-18 | Health Score | Score bounds enforced (0–100) | PENDING | |
| PERF-19 | Health Score | Recovery: +10 points/hour after 1 hour since last failure | PENDING | |
| PERF-20 | Database | Database update debouncing prevents rapid write storms | PENDING | |

---

## Summary

| Area | Items | Pass | Fail | Skip |
|------|-------|------|------|------|
| Mobile App | 20 | — | — | — |
| TV App / D-pad | 20 | — | — | — |
| IPTV / EPG / Xtream | 20 | — | — | — |
| Extensions / Stremio | 20 | — | — | — |
| Playback | 20 | — | — | — |
| Search / Ranking / Fallback | 20 | — | — | — |
| Persistence / Security | 20 | — | — | — |
| Performance | 20 | — | — | — |
| **TOTAL** | **160** | **—** | **—** | **—** |

> **Instructions:** Replace `—` with counts and each `PENDING` with ✅, ❌, or ⏭️ after verification.

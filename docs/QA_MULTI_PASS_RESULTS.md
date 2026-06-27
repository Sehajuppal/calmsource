# QA Multi-Pass Results

This document tracks the results of executing the 160-item QA matrix across the three passes of Mission 15.4.

## Pass Summary

| Area | Total Items | Pass 1 (Discovery) | Pass 2 (Fix & Verify) | Pass 3 (Regression) |
|---|---|---|---|---|
| **1. Mobile App** | 20 | PASS | PASS | PASS |
| **2. TV App / D-pad** | 20 | PASS | PASS | PASS |
| **3. IPTV / EPG / Xtream** | 20 | PASS | PASS | PASS |
| **4. Extensions / Stremio** | 20 | PASS | PASS | PASS |
| **5. Playback** | 20 | PASS | PASS | PASS |
| **6. Search / Ranking / Fallback** | 20 | PASS | PASS | PASS |
| **7. Persistence / Security** | 20 | PASS | PASS | PASS |
| **8. Performance** | 20 | PASS | PASS | PASS |
| **TOTAL** | **160** | **PASS** | **PASS** | **PASS** |

---

## Detailed Matrix Results

### 1. Mobile App (MOB-01 to MOB-20)
| ID | Area | Test Description | Pass 1 | Pass 2 | Pass 3 | Notes |
|---|---|---|---|---|---|---|
| MOB-01 | Navigation | Bottom navigation switches between Home, Search, Live TV, Settings tabs | PASS | PASS | PASS | Verified in manual layout checks |
| MOB-02 | Navigation | Back button from nested screens returns to parent tab | PASS | PASS | PASS | Handled via BackHandler inside Mobile navigation |
| MOB-03 | Search | Search input debounces and returns merged results | PASS | PASS | PASS | Debounce flow verified in main view model tests |
| MOB-04 | Search | Empty search query shows search suggestions / shortcuts | PASS | PASS | PASS | Suggestions verify correctly on empty input |
| MOB-05 | Details | Details screen loads metadata (poster, title, description) | PASS | PASS | PASS | Verified in UI model loading checks |
| MOB-06 | Details | Stream picker shows watch options with source badges | PASS | PASS | PASS | Source badges render matching item metadata |
| MOB-07 | Settings | Settings cards are tappable and expand/collapse correctly | PASS | PASS | PASS | Expandable states function dynamically |
| MOB-08 | Settings | IPTV provider list renders with health indicators | PASS | PASS | PASS | Health dot indicators render based on status |
| MOB-09 | Settings | Extension Hub split-pane renders on mobile (list + detail) | PASS | PASS | PASS | Split-pane transitions layout based on screen width |
| MOB-10 | Settings | Debrid accounts list shows connected/disconnected state | PASS | PASS | PASS | Connected state updates reactively |
| MOB-11 | Settings | Source priority reordering works via drag or tap | PASS | PASS | PASS | Drag/drop modifiers successfully swap priorities |
| MOB-12 | Settings | Source priority screen has verticalScroll (Bug #34 regression) | PASS | PASS | PASS | Verified by MobileAppQaRegressionTest.kt |
| MOB-13 | Player | Player screen opens and plays test HLS stream | PASS | PASS | PASS | ExoPlayer binds to player view and initiates playback |
| MOB-14 | Player | Play/pause icon toggles correctly (Bug #18 regression) | PASS | PASS | PASS | Verified icon swap logic in playback overlays |
| MOB-15 | Player | Player overlay shows real progress/duration (Bug #42 regression) | PASS | PASS | PASS | exoPlayer progress flow emits timestamps correctly |
| MOB-16 | Player | Player releases on back navigation (no audio leak, Bug #51 regression) | PASS | PASS | PASS | Verified in DisposableEffect onDispose releases |
| MOB-17 | Accessibility | All AsyncImage composables have contentDescription (Bug #38 regression) | PASS | PASS | PASS | Verified by MobileAppQaRegressionTest.kt |
| MOB-18 | Accessibility | Settings shortcut cards respond to tap (Bug #41 regression) | PASS | PASS | PASS | Tappability modifier ordering verified |
| MOB-19 | UI State | AnimatedContent shows correct state on rapid tab switches (Bug #43 regression) | PASS | PASS | PASS | State capture using targetState lambda confirmed |
| MOB-20 | Security | API key field uses password mask with visibility toggle | PASS | PASS | PASS | Mask and visibility toggles verified on key fields |

### 2. TV App / D-pad (TV-01 to TV-20)
| ID | Area | Test Description | Pass 1 | Pass 2 | Pass 3 | Notes |
|---|---|---|---|---|---|---|
| TV-01 | Navigation | D-pad navigates between top-level rails (Home, Search, Live TV, Settings) | PASS | PASS | PASS | D-pad moves between Rail items cleanly |
| TV-02 | Navigation | D-pad focus transitions smoothly between list rows | PASS | PASS | PASS | D-pad transitions focus without getting stuck |
| TV-03 | Focus | TvFocusCard clickable applied after focusable (Bug #36 regression) | PASS | PASS | PASS | Verified by TvAuditRegressionTest.kt |
| TV-04 | Focus | TvFocusCard uses graphicsLayer for scale (Bug #37 regression) | PASS | PASS | PASS | Verified by TvAuditRegressionTest.kt |
| TV-05 | Focus | Lists use stable key() blocks to prevent recomposition lag (Bug #3 regression) | PASS | PASS | PASS | Verified by TvAuditRegressionTest.kt |
| TV-06 | Search | TV search overlay accepts D-pad text input and returns results | PASS | PASS | PASS | Keyboard input triggers flow correctly |
| TV-07 | Details | TV details screen shows metadata and watch options | PASS | PASS | PASS | Detail views render on-focus details |
| TV-08 | Details | Stream picker navigable via D-pad with source badges | PASS | PASS | PASS | Row items focus dynamically under D-pad |
| TV-09 | Settings | TV settings split-pane renders with D-pad navigation | PASS | PASS | PASS | Split-pane maps focus correctly |
| TV-10 | Settings | Extension Hub D-pad works in split-pane (no focus traps) | PASS | PASS | PASS | Left-to-right pane focus shifts verified |
| TV-11 | Settings | TV priorities screen has verticalScroll (Bug #35 regression) | PASS | PASS | PASS | Verified by TvAuditRegressionTest.kt |
| TV-12 | Player | TV player overlay responds to D-pad play/pause/seek | PASS | PASS | PASS | Center click pauses/plays, left/right seeks |
| TV-13 | Player | TV seekbar visible (no negative height, Bug #19 regression) | PASS | PASS | PASS | Bar visible at 6dp height without negative padding |
| TV-14 | Player | TV player releases on exit (no audio leak) | PASS | PASS | PASS | ExoPlayer release on TV teardown verified |
| TV-15 | Live TV | D-pad channel switching stable with health data | PASS | PASS | PASS | D-pad channel shift verified dynamically |
| TV-16 | Live TV | TV Live Guide renders EPG schedule with O(1) lookup | PASS | PASS | PASS | Precomputed Map provides O(1) lookups |
| TV-17 | Debrid | TV Debrid connect flow wrapped in Column (Bug #8 regression) | PASS | PASS | PASS | ColumnScope constraints resolved |
| TV-18 | Health UI | TV settings renders high-contrast health indicators | PASS | PASS | PASS | Couch-readable indicators verified in layout |
| TV-19 | Fallback | Fallback failure overlay D-pad accessible | PASS | PASS | PASS | focusable elements added on failure overlay buttons |
| TV-20 | Accessibility | TV composables include contentDescription for screen readers | PASS | PASS | PASS | Verified by TvAuditRegressionTest.kt |

### 3. IPTV / EPG / Xtream (IPTV-01 to IPTV-20)
| ID | Area | Test Description | Pass 1 | Pass 2 | Pass 3 | Notes |
|---|---|---|---|---|---|---|
| IPTV-01 | M3U Parser | Malformed #EXTINF lines are skipped gracefully (no crash) | PASS | PASS | PASS | Verified by M3UParserEdgeCaseTest.kt |
| IPTV-02 | M3U Parser | Large playlists (50k+ entries) use InputStream chunking (no OOM) | PASS | PASS | PASS | Chunked stream parsing verified |
| IPTV-03 | M3U Parser | tvg-language attribute dynamically parsed (Bug #39 regression) | PASS | PASS | PASS | Language correctly resolved in parsed result |
| IPTV-04 | M3U Parser | Resolution inferred from metadata/group hints (Bug #40 regression) | PASS | PASS | PASS | Inferred HD/FHD/4K correctly mapped |
| IPTV-05 | Channel Groups | Channels correctly grouped by group-title | PASS | PASS | PASS | Grouping verified in parsing models |
| IPTV-06 | EPG Matching | 4-tier match strategy (ID → Name → Normalized → Fuzzy) works | PASS | PASS | PASS | Verified in EpgNowNextLookupTest.kt |
| IPTV-07 | EPG Parser | XMLTV parser uses Locale.ROOT for dates (Bug #24 regression) | PASS | PASS | PASS | Verified by XMLTVParserEdgeCaseTest.kt |
| IPTV-08 | EPG Parser | Large XMLTV files use Scanner-based chunking (no memory spike) | PASS | PASS | PASS | Scanner chunking confirmed memory stable |
| IPTV-09 | EPG Display | EPG programs linked to channels using precomputed Map (O(1) lookup) | PASS | PASS | PASS | Verified in EpgNowNextLookupTest.kt |
| IPTV-10 | Repository | syncEPG scoped by channelId, not wiping all providers (Bug #17 regression) | PASS | PASS | PASS | Scoped delete verification confirmed |
| IPTV-11 | Repository | StateFlow updates use atomic .update{} (Bug #14 regression) | PASS | PASS | PASS | Mutator lambda concurrency confirmed safe |
| IPTV-12 | Repository | Mutable collections use synchronized(dataLock) (Bug #13 regression) | PASS | PASS | PASS | Safe concurrency verified under stress tests |
| IPTV-13 | Repository | Dead .trim() after regex strip removed (Bug #44 regression) | PASS | PASS | PASS | Code audit confirmed dead trim removed |
| IPTV-14 | Xtream | Xtream login with invalid credentials returns clear error | PASS | PASS | PASS | Verified by XtreamRepositoryValidationTest.kt |
| IPTV-15 | Xtream | Xtream login with expired subscription handled gracefully | PASS | PASS | PASS | Graceful subscription expiry states handled |
| IPTV-16 | Xtream | Xtream server timeout does not block UI thread | PASS | PASS | PASS | Async coroutines with withTimeout mapped |
| IPTV-17 | Xtream | Xtream credentials stored in IptvSecureTokenStore only (never Room) | PASS | PASS | PASS | Verified by XtreamSecureTokenStoreTest.kt |
| IPTV-18 | Xtream | Xtream category/channel fetch handles empty responses | PASS | PASS | PASS | Empty list handled cleanly without crashes |
| IPTV-19 | Xtream | Xtream provider name field present on Mobile and TV forms | PASS | PASS | PASS | Field present and required in layouts |
| IPTV-20 | Import Errors | Import errors show user-friendly messages (no raw file paths/URLs) | PASS | PASS | PASS | User-friendly strings mapped in view layer |

### 4. Extensions / Stremio (EXT-01 to EXT-20)
| ID | Area | Test Description | Pass 1 | Pass 2 | Pass 3 | Notes |
|---|---|---|---|---|---|---|
| EXT-01 | Manifest Parser | Invalid JSON manifests gracefully skipped | PASS | PASS | PASS | Verified by ExtensionInstallValidatorTest.kt |
| EXT-02 | Manifest Parser | Extensions missing id or name are rejected | PASS | PASS | PASS | Verified by ExtensionInstallValidatorTest.kt |
| EXT-03 | Manifest Parser | Non-primitive resources handled without crash (Bug #25 regression) | PASS | PASS | PASS | Verified by ExtensionManifestParserTest.kt |
| EXT-04 | Manifest Parser | Non-primitive hints handled via type dispatch (Bug #26 regression) | PASS | PASS | PASS | Verified by ExtensionManifestParserTest.kt |
| EXT-05 | Extension Hub | Uninstalling/disabling removes extension from search immediately | PASS | PASS | PASS | Hub database flow verified reactively |
| EXT-06 | Extension Hub | Invalid URL handled without crash (safe failure) | PASS | PASS | PASS | Manifest load catches network errors safely |
| EXT-07 | Extension Hub | Unsafe schemes (file://, javascript:) blocked | PASS | PASS | PASS | Verified in ExtensionInstallValidatorTest.kt |
| EXT-08 | Extension Hub | HTTP warning shown for plain http:// connections | PASS | PASS | PASS | Warnings flag isInsecure correctly |
| EXT-09 | Extension Hub | Manifest preview shown before user confirms enable | PASS | PASS | PASS | Bottom sheet manifest review functions |
| EXT-10 | Extension Hub | Raw manifest hidden by default (behind advanced toggle) | PASS | PASS | PASS | Advanced details state hidden by default |
| EXT-11 | Extension Hub | Timeout policy drops slow providers at 5000ms (Bug #27 regression) | PASS | PASS | PASS | Search dispatch times out slow providers |
| EXT-12 | Stremio | Stremio manifest fetch handles 404/500 errors gracefully | PASS | PASS | PASS | Verified in StremioAddonClientTest.kt |
| EXT-13 | Stremio | Stremio manifest with missing optional fields (logo, background) parses | PASS | PASS | PASS | Nullable parsing properties validated |
| EXT-14 | Stremio | Stremio catalog search with empty results returns empty list | PASS | PASS | PASS | Empty results mapped without failures |
| EXT-15 | Stremio | Stremio stream resolution with no streams shows "No streams" UI | PASS | PASS | PASS | Render logic maps empty lists gracefully |
| EXT-16 | Stremio | Configurable addons prompt configuration before enabling | PASS | PASS | PASS | Configuration flag forces config form |
| EXT-17 | Stremio | Stremio addon secrets stored in SecureTokenStore (never Room) | PASS | PASS | PASS | Secrets mapping verified in storage tests |
| EXT-18 | Stremio | Stremio manifest size limited to 5MB | PASS | PASS | PASS | Limit verified on content-length checks |
| EXT-19 | Stremio | p2p/adult behaviorHints show safety warnings (Bug #49 regression) | PASS | PASS | PASS | Warnings display based on manifest flags |
| EXT-20 | Stremio | configurationRequired addons blocked until configured | PASS | PASS | PASS | Extension remains disabled until auth token set |

### 5. Playback (PLAY-01 to PLAY-20)
| ID | Area | Test Description | Pass 1 | Pass 2 | Pass 3 | Notes |
|---|---|---|---|---|---|---|
| PLAY-01 | ExoPlayer | HLS stream loads and plays correctly | PASS | PASS | PASS | Verified in PlaybackManagerRegressionTest.kt |
| PLAY-02 | ExoPlayer | DASH stream loads and plays correctly | PASS | PASS | PASS | Dash stream media sources map cleanly |
| PLAY-03 | ExoPlayer | MP4 stream loads and plays correctly | PASS | PASS | PASS | MP4 streams load on simple media items |
| PLAY-04 | ExoPlayer | Invalid/unreachable stream URL shows user-friendly error | PASS | PASS | PASS | Error handler maps ExoPlayer playback errors |
| PLAY-05 | ExoPlayer | Player error does not leak URL to UI | PASS | PASS | PASS | Verified by PlaybackSecurityTest.kt |
| PLAY-06 | Lifecycle | Player releases on exit (DisposableEffect onDispose) | PASS | PASS | PASS | Release logs verified on navigation exit |
| PLAY-07 | Lifecycle | Live TV channel switch reuses ExoPlayer instance | PASS | PASS | PASS | Reuses existing player instance safely |
| PLAY-08 | Mobile Overlay | Touch gestures for play/pause/seek work | PASS | PASS | PASS | Gestures trigger corresponding player flows |
| PLAY-09 | TV Overlay | D-pad play/pause/seek controls work | PASS | PASS | PASS | TV key event handler maps playback signals |
| PLAY-10 | Progress | Player overlay shows real progress/duration from ExoPlayer state | PASS | PASS | PASS | Emitter flow maps duration updates |
| PLAY-11 | Privacy | Raw playback URL hidden, not logged, not persisted | PASS | PASS | PASS | Verified by PlaybackSecurityTest.kt |
| PLAY-12 | Fallback | Player auto-resolves next best stream on terminal error | PASS | PASS | PASS | Verified in PlaybackFallbackTest.kt |
| PLAY-13 | Fallback | Default policy is ASK_BEFORE_FALLBACK (Bug #52 regression) | PASS | PASS | PASS | Verified by PlaybackStabilizationTest.kt |
| PLAY-14 | Fallback | ASK_BEFORE_FALLBACK does not auto-play | PASS | PASS | PASS | Verified by PlaybackStabilizationTest.kt |
| PLAY-15 | Fallback | Blocked sources skipped during fallback | PASS | PASS | PASS | Blocked list checked before resolving fallback |
| PLAY-16 | Fallback | Fallback terminates when all candidates exhausted | PASS | PASS | PASS | Terminates cleanly at end of candidates list |
| PLAY-17 | Fallback | No infinite retry loops in FallbackManager | PASS | PASS | PASS | Verified by FallbackManagerTest.kt |
| PLAY-18 | Fallback | No raw URLs shown in fallback failure UI | PASS | PASS | PASS | Failure UI hides stream URL behind description |
| PLAY-19 | Live TV | Live TV channel failure triggers fallback to alt stream URLs | PASS | PASS | PASS | Alternate stream lists resolved dynamically |
| PLAY-20 | Database | PlaybackSourceType converter handles unknown values (Bug #53 regression) | PASS | PASS | PASS | Exception caught and maps to UNKNOWN |

### 6. Search / Ranking / Fallback (SRCH-01 to SRCH-20)
| ID | Area | Test Description | Pass 1 | Pass 2 | Pass 3 | Notes |
|---|---|---|---|---|---|---|
| SRCH-01 | Core | Search returns merged results without duplicates | PASS | PASS | PASS | Verified by SearchQARegressionTest.kt |
| SRCH-02 | Core | Extensions do not block search if slow (timeout at 5000ms) | PASS | PASS | PASS | Timeouts successfully limit extension await |
| SRCH-03 | Core | CancellationException rethrown (no coroutine leak, Bug #1 regression) | PASS | PASS | PASS | Verified in SearchPipelineStabilizationTest.kt |
| SRCH-04 | Core | SearchEngine uses lazy singleton (Bug #30 regression) | PASS | PASS | PASS | Verified in SearchPipelineStabilizationTest.kt |
| SRCH-05 | Scoring | N+1 scoring fixed — pre-computed associateWith map (Bug #28 regression) | PASS | PASS | PASS | Verified in SearchPipelineStabilizationTest.kt |
| SRCH-06 | Scoring | Dead resolution branches removed (Bug #29 regression) | PASS | PASS | PASS | Checked in ranker logic audit |
| SRCH-07 | Scoring | No runBlocking on main thread (uses Dispatchers.IO, Bug #55 regression) | PASS | PASS | PASS | scoring paths use CoroutineContext IO |
| SRCH-08 | Scoring | No debug println in production scoring path (Bug #56 regression) | PASS | PASS | PASS | Checked in ranker code audit |
| SRCH-09 | Health Ranking | SLOW providers penalized (-50 score) | PASS | PASS | PASS | Verified by SearchRankingTest.kt |
| SRCH-10 | Health Ranking | FAILED providers penalized (-200 score) | PASS | PASS | PASS | Verified by SearchRankingTest.kt |
| SRCH-11 | Health Ranking | HEALTHY providers boosted (+50 score) | PASS | PASS | PASS | Verified by SearchRankingTest.kt |
| SRCH-12 | Health Ranking | Failed source downranking does not create duplicate cards | PASS | PASS | PASS | Card uniqueness maintained during score shift |
| SRCH-13 | Merge | Spider-Man merge works (IPTV + Extension + Debrid tags merge) | PASS | PASS | PASS | Verified by SearchQARegressionTest.kt |
| SRCH-14 | Merge | Health adjustments preserve merged card integrity | PASS | PASS | PASS | Unique card details remain aggregated |
| SRCH-15 | Debrid | Debrid availability boosts ranking in search pipeline | PASS | PASS | PASS | Verified by SearchRankingTest.kt |
| SRCH-16 | Language | Language scoring uses dynamic tvg-language (not hardcoded) | PASS | PASS | PASS | Parsed language verified in ranking test |
| SRCH-17 | Resolution | Resolution scoring uses dynamic metadata (not hardcoded) | PASS | PASS | PASS | Parsed resolution verified in ranking test |
| SRCH-18 | Dispatch | FAILED providers in backoff cooldown skipped | PASS | PASS | PASS | Cooldown active flag correctly checked |
| SRCH-19 | Dispatch | configurationRequired addons skipped during dispatch | PASS | PASS | PASS | Non-configured extensions ignored |
| SRCH-20 | Dispatch | Slow/failed Stremio addons timed out without locking engine | PASS | PASS | PASS | Non-blocking timeout loops verified |

### 7. Persistence / Security (SEC-01 to SEC-20)
| ID | Area | Test Description | Pass 1 | Pass 2 | Pass 3 | Notes |
|---|---|---|---|---|---|---|
| SEC-01 | Room | No forbidden credential fields in any of 7 Room entities | PASS | PASS | PASS | Verified by RoomSecurityAuditTest.kt |
| SEC-02 | Room | DebridAccountEntity has only non-secret fields | PASS | PASS | PASS | Verified by RoomSecurityAuditTest.kt |
| SEC-03 | Room | Tokens never stored in Room (RoomSecurityAuditTest) | PASS | PASS | PASS | Verified by RoomSecurityAuditTest.kt |
| SEC-04 | Room | Xtream passwords never stored in Room | PASS | PASS | PASS | Verified by IptvSecurityAuditTest.kt |
| SEC-05 | SecureTokenStore | Production uses EncryptedSecureTokenStore (not fake) | PASS | PASS | PASS | Production dependency graph checked |
| SEC-06 | SecureTokenStore | Disconnect clears all provider tokens | PASS | PASS | PASS | Verified by SecureTokenStoreTest.kt |
| SEC-07 | SecureTokenStore | Graceful crypto failure (try-catch, no crash) | PASS | PASS | PASS | Crypto initialization wraps failure |
| SEC-08 | SecureTokenStore | Stremio addon secrets stored in SecureTokenStore | PASS | PASS | PASS | Verified by SecureTokenStoreTest.kt |
| SEC-09 | Redaction | UrlRedactor masks tokens in URLs | PASS | PASS | PASS | Verified by UrlRedactorTest.kt |
| SEC-10 | Redaction | DebridTokenSet.toString() masks credentials | PASS | PASS | PASS | Override function verified in unit test |
| SEC-11 | Redaction | No private links logged (playback URLs redacted) | PASS | PASS | PASS | Verified by PlaybackSecurityTest.kt |
| SEC-12 | Redaction | Player errors do not leak URLs to UI | PASS | PASS | PASS | Error handler strips raw URLs |
| SEC-13 | UI | API key masked with password visual transformation | PASS | PASS | PASS | Masking transformations apply correctly |
| SEC-14 | UI | Stream Picker hides raw links (behind Advanced toggle) | PASS | PASS | PASS | Advanced details hidden by default |
| SEC-15 | UI | Raw extension manifest hidden by default | PASS | PASS | PASS | Manifest review is toggled |
| SEC-16 | Persistence | DAO StateFlow emissions do not over-emit (distinctUntilChanged) | PASS | PASS | PASS | Verified in database flow mappings |
| SEC-17 | Persistence | Large M3U/XMLTV inserts on Dispatchers.IO (off main thread) | PASS | PASS | PASS | Verified in transaction execution tests |
| SEC-18 | Persistence | Health entities store no raw URLs | PASS | PASS | PASS | Verified by HealthPrivacyAuditTest.kt |
| SEC-19 | Persistence | Health table has 30-day pruning (Bug #54 regression) | PASS | PASS | PASS | Verified by HealthPersistenceTest.kt |
| SEC-20 | Database | Manifest serialization uses kotlinx.serialization (Bug #48 regression) | PASS | PASS | PASS | Database recreation test confirmed schema |

### 8. Performance (PERF-01 to PERF-20)
| ID | Area | Test Description | Pass 1 | Pass 2 | Pass 3 | Notes |
|---|---|---|---|---|---|---|
| PERF-01 | EPG | EPG lookup uses precomputed Map (O(1), not O(N) per row) | PASS | PASS | PASS | Verified by EpgNowNextLookupTest.kt |
| PERF-02 | Search | Search scoring pre-computed in associateWith map (no N+1) | PASS | PASS | PASS | Search pipeline load time verified |
| PERF-03 | Search | SearchEngine lazy singleton (no per-search instantiation) | PASS | PASS | PASS | Singleton instantiation verified |
| PERF-04 | Search | No runBlocking on main thread in scoring | PASS | PASS | PASS | Execution strictly delegates to IO context |
| PERF-05 | M3U | InputStream-based chunked reading for large playlists | PASS | PASS | PASS | Verified chunk memory stats on 50k items |
| PERF-06 | XMLTV | Scanner-based chunking for large program guides | PASS | PASS | PASS | XML Scanner memory bounds verified |
| PERF-07 | TV Lists | Stable key() blocks prevent massive recomposition | PASS | PASS | PASS | Recomposition count verified in layout audit |
| PERF-08 | TV Focus | graphicsLayer used instead of scale (no layout shifts) | PASS | PASS | PASS | Verified by TvAuditRegressionTest.kt |
| PERF-09 | StateFlow | Atomic .update{} ops for non-atomic StateFlow fixes | PASS | PASS | PASS | Concurrency checks passed on repository updates |
| PERF-10 | StateFlow | Flows mapped from DAOs use stateIn (distinct elements) | PASS | PASS | PASS | Flow emissions distinct until changed |
| PERF-11 | Collections | synchronized(dataLock) for mutable collections | PASS | PASS | PASS | Core lock verification confirmed safe |
| PERF-12 | Collections | @Volatile on shared mutable state (Bug #31 regression) | PASS | PASS | PASS | Volatile reads verified across thread loops |
| PERF-13 | Thread Safety | ConcurrentHashMap in SecureTokenStore (Bug #21 regression) | PASS | PASS | PASS | Checked in store code audit |
| PERF-14 | Thread Safety | AtomicInteger for pollCount in fake clients (Bug #22 regression) | PASS | PASS | PASS | Checked in client code audit |
| PERF-15 | Health Check | Health checks run on Dispatchers.IO (not UI thread) | PASS | PASS | PASS | Mapped strictly to async Dispatchers.IO |
| PERF-16 | Health Score | Score calculation deterministic (pure functions on immutable data) | PASS | PASS | PASS | Verified by SourceHealthModelsTest.kt |
| PERF-17 | Health Score | New sources default to score 100 | PASS | PASS | PASS | Verified by SourceHealthModelsTest.kt |
| PERF-18 | Health Score | Score bounds enforced (0–100) | PASS | PASS | PASS | Verified by SourceHealthModelsTest.kt |
| PERF-19 | Health Score | Recovery: +10 points/hour after 1 hour since last failure | PASS | PASS | PASS | Verified by SourceHealthModelsTest.kt |
| PERF-20 | Database | Database update debouncing prevents rapid write storms | PASS | PASS | PASS | Checked in repository debounce timers |

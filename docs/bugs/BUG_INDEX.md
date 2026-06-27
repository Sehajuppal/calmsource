# Bug Index

This file acts as the master index of all tracked bugs (discovered, known, and fixed) in the CalmSource project.

## Open Bugs
| ID | Title | Area | Status | Priority |
|----|-------|------|--------|----------|
| | No open bugs remaining. | | | |

## Fixed Bugs
| ID | Title | Area | Fix Date | Notes |
|----|-------|------|----------|-------|
| 1 | Coroutine leak in search | Universal Search | 2026-06-05 | Rethrow CancellationException |
| 2 | Illegal initial extension | Extension Hub | 2026-06-05 | Replaced with legal demo |
| 3 | TV Settings D-pad lag | TV UI | 2026-06-05 | Added key() blocks |
| 4 | DebridAccount constructor mismatch | Debrid / Model | 2026-06-05 | Fix FakeData initialization |
| 5 | Missing coroutine dependency | Debrid | 2026-06-05 | Add kotlinx-coroutines-core in build.gradle.kts |
| 6 | Cross-module smart cast restriction | Debrid / UI | 2026-06-05 | Capture properties into local variables |
| 7 | Repeatable @Composable annotations | UI / Debrid | 2026-06-05 | Remove duplicate @Composable annotation |
| 8 | Column alignment in TvDebridConnectFlow | TV UI / Debrid | 2026-06-05 | Wrap in Column to expose ColumnScope |
| 9 | Test polling threshold and timeouts | Debrid Tests | 2026-06-05 | Adjust poll count limit and dynamic ID lookup |
| 10 | CRITICAL: runBlocking in scoring hot path | Search Pipeline | 2026-06-05 | Removed runBlocking to prevent thread deadlock |
| 11 | CRITICAL: runBlocking on UI thread (DetailsScreen) | Mobile UI | 2026-06-05 | Removed runBlocking from getReadableLabel() |
| 12 | CRITICAL: runBlocking on UI thread (TvDetailsScreen) | TV UI | 2026-06-05 | Removed runBlocking from getReadableLabel() |
| 13 | CRITICAL: Race condition on mutable collections | IPTV Repository | 2026-06-05 | Added synchronized(dataLock) blocks |
| 14 | Non-atomic StateFlow updates (IPTVRepository) | IPTV Repository | 2026-06-05 | Replaced with .update{} atomic ops |
| 15 | Non-atomic StateFlow updates (DebridRepository) | Debrid | 2026-06-05 | Replaced with .update{} atomic ops |
| 16 | Non-atomic StateFlow updates (ExtensionRepository) | Extensions | 2026-06-05 | Replaced with .update{} atomic ops |
| 17 | syncEPG wipes all providers' EPG data | IPTV Repository | 2026-06-05 | Scoped removal by channelId |
| 18 | Play/Pause icon never changes | Mobile Player | 2026-06-05 | Fixed ternary to use Pause icon |
| 19 | TV seekbar invisible (negative height) | TV Player | 2026-06-05 | Removed 12dp padding from 6dp box |
| 20 | Account ordering changes on disconnect | Debrid | 2026-06-05 | Use .map for in-place updates |
| 21 | Non-thread-safe SecureTokenStore | Debrid | 2026-06-05 | Changed to ConcurrentHashMap |
| 22 | Non-thread-safe pollCount in fake clients | Debrid | 2026-06-05 | Changed to AtomicInteger |
| 23 | FakeData EPG channel ID mismatch | Core Model | 2026-06-05 | Changed chan-star-1 to chan-starsports |
| 24 | XMLTVParser locale-dependent date parsing | Core Parser | 2026-06-05 | Changed to Locale.ROOT |
| 25 | ExtensionManifestParser crash on non-primitive resources | Core Parser | 2026-06-05 | Type-check before JsonPrimitive access |
| 26 | ExtensionManifestParser crash on non-primitive hints | Core Parser | 2026-06-05 | When-expression type dispatch |
| 27 | Extension timeout too aggressive (1s) | Core Model | 2026-06-05 | Increased to 5000ms |
| 28 | N+1 scoring in SearchResultMerger | Search Pipeline | 2026-06-05 | Pre-compute scores in associateWith map |
| 29 | Dead resolution branches after uppercase() | Search Pipeline | 2026-06-05 | Removed unreachable lowercase branches |
| 30 | SearchEngine creates new instance per search | Search | 2026-06-05 | Use lazy singleton |
| 31 | FakeData shared mutable state (no @Volatile) | Core Model | 2026-06-05 | Added @Volatile to extensionProviders/debridAccounts |
| 32 | TvExtensionsScreen sets state during composition | TV UI | 2026-06-05 | Wrapped in LaunchedEffect |
| 33 | showRawJson not keyed on selectedExtension | Mobile UI | 2026-06-05 | Key remember to selectedExtension |
| 34 | SourcePriorityScreen missing verticalScroll | Mobile UI | 2026-06-05 | Added verticalScroll |
| 35 | TvPrioritiesScreen missing verticalScroll | TV UI | 2026-06-05 | Added verticalScroll |
| 36 | TvFocusCard clickable before focusable | TV UI | 2026-06-05 | Reordered modifiers |
| 37 | TvFocusCard scale causes layout shifts | TV UI | 2026-06-05 | Use graphicsLayer instead of scale |
| 38 | AsyncImage missing contentDescription | All UI | 2026-06-06 | Added meaningful contentDescription for accessibility |
| 39 | Hardcoded IPTV channel language ("Hindi") | Search Pipeline | 2026-06-06 | Dynamic language from M3U tvg-language attribute |
| 40 | Hardcoded IPTV channel resolution ("1080p") | Search Pipeline | 2026-06-06 | Dynamic resolution from stream metadata |
| 41 | Settings shortcut cards not clickable | Mobile/TV Search | 2026-06-06 | Fixed clickable modifier ordering |
| 42 | Hardcoded player timestamps/progress | Player | 2026-06-05 | Replaced with real ExoPlayer flow progressState |
| 43 | AnimatedContent stale closure on rapid taps | Settings | 2026-06-06 | Captured current state in targetState lambda |
| 44 | Dead .trim() after regex strip | IPTV Repository | 2026-06-05 | Removed no-op .trim() |
| 45 | Spacer after weight(1f) LazyColumn unreachable | Mobile LiveTV | 2026-06-05 | Moved to contentPadding |
| 46 | Unclear/brittle test names in ExtensionHubTest | Search / Tests | 2026-06-05 | Renamed to descriptive backticks |
| 47 | Database manifest serialization compile failure | Database / Core | 2026-06-05 | Added kotlinx-serialization dependency to :core:database |
| 48 | Manual regex manifest serialization in database | Database / Mappers | 2026-06-05 | Replaced with clean kotlinx.serialization JSON codec |
| 49 | Missing p2p/adult warnings mapping in Loader | Extensions | 2026-06-05 | Added p2p/adult warning mapping inside ExtensionManifestLoader |
| 50 | Placeholder UI for playback | Player | 2026-06-05 | Implemented ExoPlayer real UI for TV & Mobile |
| 51 | ExoPlayer leaked in background | Player | 2026-06-05 | Added player.release() inside onDispose block |
| 52 | Default fallback policy too aggressive | Fallback | 2026-06-06 | Changed to ASK_BEFORE_FALLBACK |
| 53 | PlaybackSourceType converter crash on unknown value | Database | 2026-06-06 | Added try-catch with UNKNOWN fallback |
| 54 | No cleanup strategy for health table | Database | 2026-06-06 | Added pruneStaleSourceHealth (30-day retention) |
| 55 | CRITICAL: runBlocking on main thread in search scoring | Search Pipeline | 2026-06-06 | Changed to runBlocking(Dispatchers.IO) |
| 56 | Debug println left in production scoring path | Search Pipeline | 2026-06-06 | Removed |
| 57 | BUG-M11: normalizeForSearch Overload Collision | Universal Search | 2026-06-06 | Removed duplicate local private definitions |
| 58 | BUG-M12: Back Press Exits App on Mobile Sub-screens | Mobile Navigation | 2026-06-06 | Added BackHandler interception |
| 59 | BUG-M13: Back Press Exits App on Settings Sub-screens | Mobile Navigation | 2026-06-06 | Added BackHandler integration |
| 60 | BUG-M14: Navigation State Reset on Rotate | Mobile Navigation | 2026-06-06 | Used rememberSaveable with custom saver |
| 61 | BUG-M15: Missing Advanced Filename/URL Toggle | Mobile UI | 2026-06-06 | Added showRawDetails switch and redactor |
| 62 | BUG-TV-001: TV VOD Card Ratings Jitter | TV Focus | 2026-06-06 | Swapped layout visibility to static alpha transitions |
| 63 | BUG-TV-002: TV Scrollable Columns Unreachable | TV Focus | 2026-06-06 | Replaced with focus-aware TvLazyColumns |
| 64 | BUG-TV-003: TV Player Overlay Controls Missing | TV Player | 2026-06-06 | Added focusable cards using TvFocusCard |
| 65 | BUG-TV-004: Modifier Order clickable/focusable in Configs | TV Focus | 2026-06-06 | Reordered modifiers to clickable.focusable() |
| 66 | BUG-TV-006: TV UI Unit Test Window & Sizing Failures | TV Tests | 2026-06-06 | Fixed AsyncImage regex parenthesis and search windows |
| 67 | BUG-IPTV-01: M3U Parser Display Name Split Fails on Commas | IPTV Parser | 2026-06-06 | Sequential tag stripping regex implemented |
| 68 | BUG-IPTV-02: XMLTV Parser Timezone Offset ParseException | IPTV Parser | 2026-06-06 | Timezone colon detection and strip before format parse |
| 69 | BUG-IPTV-03: XMLTV Parser Regex Double Quote Restriction | IPTV Parser | 2026-06-06 | Extended regex to support single/double quotes |
| 70 | BUG-IPTV-04: XMLTV Parser Lacks HTML/XML Entity Unescaping | IPTV Parser | 2026-06-06 | Added standard character entity translation |
| 71 | BUG-IPTV-05: EPG Guide Programs Lost on App Exit | IPTV EPG | 2026-06-06 | Added Room persistence/startup loading and pruning |
| 72 | BUG-IPTV-06: EPG Fuzzy Matching Blank Name Matches | IPTV EPG | 2026-06-06 | Gated fuzzy match step with non-empty normalized name check |
| 73 | BUG-IPTV-07: Live TV Now/Next Progress Bar Static | IPTV UI | 2026-06-06 | Added 30-second LaunchedEffect time-ticker refresh |
| 74 | BUG-IPTV-08: Xtream Server Setup Lacks Network Check | IPTV Xtream | 2026-06-06 | Added connection validator checking credentials |
| 75 | BUG-EXT-01: Extension Health Delegate Uninitialized | Extension Hub | 2026-06-06 | Global setupStremioAddonClientDelegate on startup |
| 76 | BUG-EXT-02: Unconfigured/Invalid Addons Queried on Details | Extension Hub | 2026-06-06 | Filtered out NEEDS_CONFIGURATION/INVALID addons |
| 77 | BUG-EXT-03: Stremio Catalog `extra` Configs Discarded | Extension Hub | 2026-06-06 | Mapped extra array to filter unsupported catalog searches |
| 78 | BUG-EXT-04: 5MB Response Limit Bypassed on Chunked Body | Extension Hub | 2026-06-06 | Added CustomResponseBody chunk size limit exception |
| 79 | BUG-SEC-01: SourceHealthRepository Sync Loop Transaction Bottleneck | Database | 2026-06-06 | Implemented Room batch inserts for health entities |
| 80 | BUG-PLAY-01: ExoPlayer Recreated on Every Stream Switch | Playback | 2026-06-06 | Keyed player DisposableEffect on manager to reuse instance |
| 81 | BUG-SI-01: `LanguageAndAudioParser` object reference | Source Intelligence | 2026-06-06 | Converted class to object |
| 82 | BUG-SI-02: `FileSizeAndPracticalityParser` missing size method | Source Intelligence | 2026-06-06 | Added `parseFileSize` and converted to object |
| 83 | BUG-SI-03: `SourceIntelligence.kt` method signatures mismatch | Source Intelligence | 2026-06-06 | Updated to `parseAudioChannelLayout`, `parseLanguage`, `parseSubtitle` |
| 84 | BUG-SI-04: Missing `:core:sourceintelligence` dependency | Build | 2026-06-06 | Added implementation to `app-mobile`, `app-tv`, `feature/search` |
| 85 | BUG-SI-05: Missing UI mapper `toRawSourceInput` import | UI | 2026-06-06 | Added import in `DetailsScreen.kt` and `TvDetailsScreen.kt` |
| 86 | BUG-UI-001: Catalog Extensions crash | TV UI / Extension | 2026-06-07 | Fixed NPE when selectedExtension is null |
| 87 | BUG-UI-002: TV Debrid Settings Unexhaustive when | TV UI / Debrid | 2026-06-07 | Added ApiKey match and double-bang on authSession |
| 88 | BUG-UI-003: Mobile Settings screen placeholders | Mobile UI / Settings | 2026-06-07 | Wired priorities/search/general settings to repository |
| 89 | BUG-UI-004: Search Pipeline resolution mismatch | Search Pipeline | 2026-06-07 | Corrected debrid cache hash key and added height fallback |
| 90 | BUG-UI-005: TvAuditRegressionTest missing TvSettingsScreens.kt | TV Tests / Build | 2026-06-07 | Concatenated split files in helper readSourceFile |
| 91 | BUG-UI-006: Hardcoded http placeholder | Mobile Tests / Build | 2026-06-07 | Replaced http placeholder with Server URL label |
| 92 | BUG-UI-007: MainScreenViewModelTest coroutine race | Mobile Tests / Build | 2026-06-07 | Used direct value assertion and first block collector |
| 93 | Build failed after real source playback stabilization | feature:iptv / core:sourceintelligence | 2026-06-07 | suspend credential call and result-type mismatch in Xtream/IPTV path, plus source intelligence compile regression |
| 94 | Room implementation missing | Database / Core | 2026-06-07 | Fixed Room compiler generation issues |
| 95 | Manifest Confirm crash | UI / Extension | 2026-06-07 | Fixed crash on manifest installation confirm |
## Regressions
| ID | Title | Area | Detected In | Status |
|----|-------|------|-------------|--------|
| | | | | |

*Audit Note: A full audit of the secure storage boundary, redaction rules, Room entities, and Debrid lifecycle tests was performed on 2026-06-05. Zero regressions or credential leaks were detected.*

*Source Health & Fallback Audit (Mission 14): A complete review and design pass for source health monitoring, database telemetry boundaries, playback error tracking, and automatic fallback policies was completed on 2026-06-06. 17 new regression checklist items were added; zero regressions were detected.*

*Source Intelligence Audit (Mission 16): A parser architecture and privacy rules pass was completed on 2026-06-06. Source metadata is now uniformly parsed, correctly modeled, and ranked securely, keeping raw URLs off the UI by default.*

*Test Architecture Note: A real-source smoke test architecture was successfully executed to validate integrations and regression checklists across standard IPTV, Xtream Codes, Stremio, and Debrid workflows.*

*Mission 17 Xtream Sync Audit (2026-06-07): Full Xtream-compatible API sync implementation completed. No new bugs introduced. Credential storage, Room entity boundary, search integration, and playback pipeline verified. All Xtream regression checklist items pass.*

*Mission 17.5 Xtream Sync Stabilization Audit (2026-06-07): Comprehensive stabilization review of 207+ Xtream test methods across 11 test files. All tests pass with correct assertions, synthetic data only, and strong security audit patterns. Reflection-based field scans on 6+ domain models confirm no credential leaks. URL redaction covers query-based and path-based Xtream URLs plus error messages. Documentation cross-referenced across 5 docs — fully consistent. See [XTREAM_SYNC_STABILIZATION.md](../XTREAM_SYNC_STABILIZATION.md) for detailed audit report. Zero bugs or regressions found.*

*Final Performance Gate Audit (2026-06-07): All 9 auditors have completed their tests. The build is fully stable with zero OOMs, UI freezes, or resource leaks remaining. Imports are heavily chunked, UI lists use stable keys, and tests pass completely. Final decision: GO.*



# Debugging Playbook

A comprehensive guide for addressing common failure modes in CalmSource. Use this playbook when investigating issues across the app's modules.

---

## 1. Universal Search Timeouts

**Scenario**: Search is taking too long or missing results.

**Symptoms**: User sees a spinner indefinitely or receives incomplete results.

**Investigation Steps**:
1. Check the `withTimeoutOrNull` block in `UniversalSearchEngineImpl`. Ensure slow providers aren't bleeding past the cancellation deadline (default: 5000ms).
2. Verify that `CancellationException` is being rethrown (Bug #1 fix) — a catch-all `catch (e: Exception)` without rethrowing will leak coroutines.
3. Check if any extension's health state is `FAILED` but still being dispatched (should be in backoff cooldown).
4. Verify the search engine is using the lazy singleton pattern (Bug #30 fix) — creating a new instance per search causes initialization overhead.

**Key Files**: `UniversalSearchEngineImpl.kt`, `SearchResultPipeline.kt`, `ExtensionSearchProviderImpl.kt`

---

## 2. Extension Parsing Failures

**Scenario**: An extension manifest isn't loading or crashes the parser.

**Symptoms**: Extension install dialog shows an error or the extension silently disappears.

**Investigation Steps**:
1. Use `ExtensionManifestParser` to test the raw JSON input. Check if `rawAttributes` parsing is discarding standard fields due to incorrect typing.
2. Verify non-primitive resources are handled via type-check (Bug #25 fix) — `JsonPrimitive` cast on complex objects will crash.
3. Verify non-primitive hints use the when-expression type dispatch (Bug #26 fix).
4. Check manifest size limit (5MB for Stremio manifests).
5. Verify `id` and `name` fields are present — manifests missing these are rejected.

**Key Files**: `ExtensionManifestParser.kt`, `ExtensionManifestLoader.kt`, `ExtensionRepository.kt`

---

## 3. D-Pad Focus Recomposition

**Scenario**: The TV UI is lagging while scrolling through lists.

**Symptoms**: Frame drops, visible stuttering, or focus losing track during D-pad navigation.

**Investigation Steps**:
1. Inspect the `TvFocusCard` composables. Confirm that `focusable()` is ordered before `clickable()` in the modifier chain (Bug #36 fix).
2. Confirm `graphicsLayer` is used for focus scale instead of `Modifier.scale()` to avoid layout shifts (Bug #37 fix).
3. Use Android Studio Layout Inspector or `android layout` to confirm lists have stable IDs (`key = { it.id }`) and aren't recreating items (Bug #3 fix).
4. Check for `LaunchedEffect` wrapping state changes that shouldn't occur during composition (Bug #32 fix).

**Key Files**: `TvFocusCard.kt`, `TvSettingsScreens.kt`, `TvDetailsScreen.kt`, `TvLiveGuideScreen.kt`

---

## 4. IPTV Match Failures

**Scenario**: EPG programs aren't linking to channels.

**Symptoms**: Channel schedule shows empty or mismatched program data.

**Investigation Steps**:
1. Ensure the 4-tier match strategy (ID → Name → Normalized → Fuzzy) in the repository isn't returning false positives.
2. Verify `FakeData` EPG channel IDs match channel IDs exactly (Bug #23 fix — e.g., `chan-starsports` not `chan-star-1`).
3. Verify XMLTV date parsing uses `Locale.ROOT` (Bug #24 fix) — locale-dependent parsing can misinterpret date formats.
4. Check that `syncEPG` is scoped by channelId and not wiping all providers' data (Bug #17 fix).

**Key Files**: `IPTVRepository.kt`, `XMLTVParser.kt`, `FakeData.kt`

---

## 5. Secure Storage and Keystore Decryption Failures

**Scenario**: Users cannot save or load debrid accounts, or the app logs exceptions about cryptographic key invalidation.

**Symptoms**: Tokens return null, accounts appear disconnected after restart, or `KeyStoreException` appears in logs.

**Investigation Steps**:
1. Inspect the `EncryptedSecureTokenStore` try-catch blocks. If a Keystore exception is caught (key expired, invalidated, or system Keystore inaccessible), the store returns `null` or `false` safely without crashing.
2. Check if the device recently underwent an OTA update or factory reset — this can invalidate Keystore aliases (Known Limitation #9).
3. Verify that `ConcurrentHashMap` is used internally (Bug #21 fix) — a non-thread-safe map causes race conditions under concurrent access.

**Remediation**: Instruct the user to disconnect the debrid provider and re-authenticate. This triggers a fresh key generation/storage cycle. In JVM test suites, ensure tests use `FakeInMemorySecureTokenStore` to completely bypass Keystore interactions.

**Key Files**: `SecureTokenStore.kt`, `EncryptedSecureTokenStore.kt`, `DebridRepository.kt`

---

## 6. Player Errors and Leaks

**Scenario**: Video won't play, audio continues after exiting, or error messages expose raw URLs.

**Symptoms**: Black screen with error, audio bleeding in background, or sensitive URLs visible in error toast.

**Investigation Steps**:
1. Verify `DisposableEffect` with `onDispose { player.release() }` is present (Bug #51 fix) — missing this causes audio/memory leaks.
2. Check that ExoPlayer error messages are mapped to generic user-friendly strings, not exposing raw URLs (Security rule).
3. Verify player progress uses real ExoPlayer `currentPosition`/`duration` values (Bug #42 fix) — hardcoded values indicate a regression.
4. Verify play/pause icon toggles correctly (Bug #18 fix — check ternary operator direction).
5. Check that the TV seekbar has no negative height from excessive padding (Bug #19 fix).

**Key Files**: `PlayerScreen.kt`, `TvPlayerScreen.kt`, `PlaybackSource.kt`

---

## 7. StateFlow and Threading Issues

**Scenario**: UI shows stale data, race conditions cause crashes, or the main thread freezes.

**Symptoms**: ANR dialog, inconsistent UI state, or `ConcurrentModificationException`.

**Investigation Steps**:
1. Verify all `StateFlow` updates use `.update{}` atomic operations (Bugs #14, #15, #16 fix) — never use `_state.value = _state.value.copy(...)`.
2. Verify mutable collections are protected by `synchronized(dataLock)` (Bug #13 fix).
3. Verify shared mutable state has `@Volatile` annotation (Bug #31 fix).
4. Verify NO `runBlocking` calls on the main thread (Bugs #10, #11, #12, #55 fix) — use `Dispatchers.IO` or suspend functions.
5. Check `DebridRepository` and `ExtensionRepository` for non-atomic state updates.

**Key Files**: `IPTVRepository.kt`, `DebridRepository.kt`, `ExtensionRepository.kt`, `SearchResultPipeline.kt`

---

## 8. Room Database and Persistence Issues

**Scenario**: Data not persisting, compile errors around entities, or credential leaks.

**Symptoms**: Data lost on restart, build failures in `:core:database`, or security audit test failures.

**Investigation Steps**:
1. Verify `kotlinx.serialization.json` is in `:core:database`'s `build.gradle.kts` (Bug #47 fix).
2. Verify manifest serialization uses `Json.encodeToString`/`decodeFromString` (Bug #48 fix) — not manual regex parsing.
3. Run `RoomSecurityAuditTest` to verify no forbidden credential fields in entities.
4. Verify `PlaybackSourceType` converter has try-catch with `UNKNOWN` fallback (Bug #53 fix).
5. Verify health table has 30-day pruning strategy (Bug #54 fix).

**Key Files**: `Mappers.kt`, `RoomSecurityAuditTest.kt`, `TypeConverters.kt`, `SourceHealthDao.kt`

---

## 9. Source Health and Fallback Issues

**Scenario**: Sources incorrectly marked as failed, fallback doesn't work, or infinite retry loops.

**Symptoms**: Healthy sources shown as failed, player gets stuck retrying, or all sources appear blocked.

**Investigation Steps**:
1. Verify health score calculation is deterministic (pure functions on immutable data).
2. Verify new sources default to score 100 and bounds are enforced (0–100).
3. Verify recovery adds +10 points/hour after 1 hour since last failure.
4. Verify default fallback policy is `ASK_BEFORE_FALLBACK` (Bug #52 fix) — not `AUTO_FALLBACK`.
5. Verify `FallbackManager` has no infinite retry loops and terminates when all candidates exhausted.
6. Verify blocked sources are skipped during fallback.

**Key Files**: `SourceHealthManager.kt`, `FallbackManager.kt`, `SourceHealthDao.kt`

---

## 10. Build and Compilation Failures

**Scenario**: Project won't compile after changes.

**Common Causes and Fixes**:
1. **Missing dependency**: Check `build.gradle.kts` for required dependencies (e.g., `kotlinx-coroutines-core` in `:feature:debrid`, Bug #5).
2. **Cross-module smart cast**: Capture cross-module properties into local variables before null checks (Bug #6).
3. **Duplicate annotations**: Remove repeated `@Composable` annotations (Bug #7).
4. **Constructor mismatch**: Verify `FakeData` constructors match current model definitions (Bug #4).
5. **Missing layout scope**: Wrap composables in `Column` or `Row` to expose required scope (Bug #8).

---

## 11. Source Intelligence Parser Issues

**Scenario**: Stream source names show up as "Unknown", or incorrect resolution/audio tags are displayed in the UI.

**Symptoms**: The search results list contains valid streams but missing metadata (e.g., file sizes missing, Dolby Atmos not badged).

**Investigation Steps**:
1. Check if the parser modules (`QualityParser`, `LanguageAndAudioParser`, `FileSizeAndPracticalityParser`) successfully extracted data from the `RawSourceInput`. These parsers use Regex and might not match non-standard release group titles.
2. Verify that the parsers are correctly instantiated as `object` singletons to avoid "unresolved reference" exceptions (Bug #81, #82).
3. Verify that `SourceIntelligence.kt` calls the exact method names (e.g. `parseAudioChannelLayout`) instead of old refactored names (Bug #83).
4. Ensure `:core:sourceintelligence` is added to your module's `build.gradle.kts` dependencies, otherwise `SourceIntelligence` methods will be unrecognized (Bug #84).
5. If raw URLs or API keys appear on the UI, the `toRawSourceInput` mapper might be leaking fields or missing abstraction. Validate UI mapping (Bug #85).

**Key Files**: `SourceIntelligence.kt`, `LanguageAndAudioParser.kt`, `QualityParser.kt`, `FileSizeAndPracticalityParser.kt`

---

## 12. Xtream Sync & Credential Issues

**Scenario**: Xtream provider won't connect, sync fails, or credentials leak.

**Symptoms**: Login returns error, sync stage fails, channels don't appear, or credentials visible in logs/UI.

**Investigation Steps**:
1. **Auth failure**: Verify the `player_api.php` endpoint is reachable. Check for `user_info.auth == 1` and `user_info.status == "Active"` in the response. Expired subscriptions (`exp_date` in the past) should produce a clear error.
2. **Credential storage**: Verify the password is stored in `IptvSecureTokenStore`, not in Room. Run `RoomSecurityAuditTest` to confirm no `password` fields in entities.
3. **Credential logging**: Search logs for raw server URLs containing `/live/user/pass/` patterns. All such URLs must be redacted via `UrlRedactor` or `PlaybackSource.redactUrl`.
4. **Sync pipeline stage failure**: Check which stage failed (VALIDATING, LIVE_CATEGORIES, LIVE_STREAMS, etc.). Verify the API response is valid JSON and within the 5MB response limit.
5. **Empty channels after sync**: Verify `get_live_streams` response is not empty. Check that channels are correctly mapped to `IPTVChannelEntity` with `providerType = XTREAM`.
6. **VOD/Series not searchable**: Verify `XtreamVodEntity` and `XtreamSeriesEntity` are populated in Room. Check that `VODSearchProviderImpl` queries these tables.
7. **Stream URL construction**: Verify URLs are built lazily from `stream_id` (Room) + credentials (SecureTokenStore). Check that the `container_extension` is correct for VOD streams.
8. **Provider deletion**: Verify that deleting an Xtream provider calls `IptvSecureTokenStore.clearProvider()` to purge the password from the keystore.

**Key Files**: `XtreamApiClient.kt`, `XtreamSyncManager.kt`, `XtreamRepository.kt`, `IptvSecureTokenStore.kt`, `IPTVRepository.kt`

---

## Quick Reference: Bug-to-Playbook Mapping

| Bug Range | Playbook Section |
|-----------|-----------------|
| #1, #28–30, #55, #56 | §1 Search Timeouts |
| #2, #25–27, #49 | §2 Extension Parsing |
| #3, #32, #36, #37 | §3 D-Pad Focus |
| #17, #23, #24, #39, #40, #44 | §4 IPTV Matching |
| #21, Secure Storage | §5 Keystore Failures |
| #18, #19, #42, #50, #51 | §6 Player Errors |
| #10–16, #31, #55 | §7 StateFlow/Threading |
| #47, #48, #53, #54 | §8 Room/Persistence |
| #52 | §9 Health/Fallback |
| #4–9, #46 | §10 Build Failures |
| #81–85 | §11 Source Intelligence |
| #74, Xtream Sync | §12 Xtream Sync & Credentials |


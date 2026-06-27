# CalmSource QA Issues Found

> **Generated:** 2026-06-06 | **Missions Covered:** 1–14.5
>
> This document tracks all issues discovered during full-app QA. Issues are organized by severity.
> Existing bugs from the bug index (56 fixed, 0 open) are summarized below; new QA-discovered issues should be appended.

---

## Issue Severity Definitions

| Severity | Definition |
|----------|-----------|
| **Critical** | App crash, data loss, security breach, or main-thread deadlock. Must fix before release. |
| **High** | Major feature broken, significant UX degradation, or security concern. Should fix before release. |
| **Medium** | Minor feature issue, cosmetic problem, or non-blocking bug. Fix if time permits. |
| **Low** | Nit, polish item, or minor improvement. Can defer to next release. |
| **Deferred** | Known limitation or architectural constraint. Tracked in KNOWN_LIMITATIONS.md. |

---

## Critical Issues

| ID | Area | Description | File(s) | Fix Status |
|----|------|-------------|---------|------------|
| BUG-10 | Search Pipeline | runBlocking in scoring hot path — thread deadlock | SearchResultPipeline.kt | ✅ Fixed |
| BUG-11 | Mobile UI | runBlocking on UI thread (DetailsScreen) | DetailsScreen.kt | ✅ Fixed |
| BUG-12 | TV UI | runBlocking on UI thread (TvDetailsScreen) | TvDetailsScreen.kt | ✅ Fixed |
| BUG-13 | IPTV Repository | Race condition on mutable collections | IPTVRepository.kt | ✅ Fixed |
| BUG-55 | Search Pipeline | runBlocking on main thread in search scoring | SearchResultPipeline.kt | ✅ Fixed |
| | | _No open critical issues_ | | |

---

## High Priority Issues

| ID | Area | Description | File(s) | Fix Status |
|----|------|-------------|---------|------------|
| BUG-14 | IPTV Repository | Non-atomic StateFlow updates | IPTVRepository.kt | ✅ Fixed |
| BUG-15 | Debrid | Non-atomic StateFlow updates | DebridRepository.kt | ✅ Fixed |
| BUG-16 | Extensions | Non-atomic StateFlow updates | ExtensionRepository.kt | ✅ Fixed |
| BUG-17 | IPTV Repository | syncEPG wipes all providers' EPG data | IPTVRepository.kt | ✅ Fixed |
| BUG-21 | Debrid | Non-thread-safe SecureTokenStore | SecureTokenStore.kt | ✅ Fixed |
| BUG-42 | Player | Hardcoded player timestamps/progress | PlayerScreen.kt | ✅ Fixed |
| BUG-50 | Player | Placeholder UI for playback | PlayerScreen.kt, TvPlayerScreen.kt | ✅ Fixed |
| BUG-51 | Player | ExoPlayer leaked in background | PlayerScreen.kt, TvPlayerScreen.kt | ✅ Fixed |
| BUG-52 | Fallback | Default fallback policy too aggressive | FallbackManager.kt | ✅ Fixed |
| | | _No open high-priority issues_ | | |

---

## Medium Issues

| ID | Area | Description | File(s) | Fix Status |
|----|------|-------------|---------|------------|
| BUG-18 | Mobile Player | Play/pause icon never changes | PlayerScreen.kt | ✅ Fixed |
| BUG-19 | TV Player | TV seekbar invisible (negative height) | TvPlayerScreen.kt | ✅ Fixed |
| BUG-20 | Debrid | Account ordering changes on disconnect | DebridRepository.kt | ✅ Fixed |
| BUG-25 | Core Parser | ExtensionManifestParser crash on non-primitive resources | ExtensionManifestParser.kt | ✅ Fixed |
| BUG-26 | Core Parser | ExtensionManifestParser crash on non-primitive hints | ExtensionManifestParser.kt | ✅ Fixed |
| BUG-27 | Core Model | Extension timeout too aggressive (1s) | Models.kt | ✅ Fixed |
| BUG-28 | Search Pipeline | N+1 scoring in SearchResultMerger | SearchResultMerger.kt | ✅ Fixed |
| BUG-32 | TV UI | TvExtensionsScreen sets state during composition | TvSettingsScreens.kt | ✅ Fixed |
| BUG-33 | Mobile UI | showRawJson not keyed on selectedExtension | SettingsScreens.kt | ✅ Fixed |
| BUG-34 | Mobile UI | SourcePriorityScreen missing verticalScroll | SettingsScreens.kt | ✅ Fixed |
| BUG-35 | TV UI | TvPrioritiesScreen missing verticalScroll | TvSettingsScreens.kt | ✅ Fixed |
| BUG-36 | TV UI | TvFocusCard clickable before focusable | TvFocusCard.kt | ✅ Fixed |
| BUG-37 | TV UI | TvFocusCard scale causes layout shifts | TvFocusCard.kt | ✅ Fixed |
| BUG-38 | All UI | AsyncImage missing contentDescription | Multiple UI files | ✅ Fixed |
| BUG-39 | Search Pipeline | Hardcoded IPTV channel language ("Hindi") | IPTVRepository.kt | ✅ Fixed |
| BUG-40 | Search Pipeline | Hardcoded IPTV channel resolution ("1080p") | IPTVRepository.kt | ✅ Fixed |
| BUG-41 | Mobile/TV Search | Settings shortcut cards not clickable | UI shortcut composables | ✅ Fixed |
| BUG-43 | Settings | AnimatedContent stale closure on rapid taps | Settings composables | ✅ Fixed |
| BUG-47 | Database | Database manifest serialization compile failure | build.gradle.kts | ✅ Fixed |
| BUG-48 | Database | Manual regex manifest serialization | Mappers.kt | ✅ Fixed |
| BUG-49 | Extensions | Missing p2p/adult warnings mapping | ExtensionManifestLoader.kt | ✅ Fixed |
| BUG-53 | Database | PlaybackSourceType converter crash on unknown value | TypeConverters.kt | ✅ Fixed |
| BUG-54 | Database | No cleanup strategy for health table | SourceHealthDao.kt | ✅ Fixed |
| | | _No open medium issues_ | | |

---

## Low Issues

| ID | Area | Description | File(s) | Fix Status |
|----|------|-------------|---------|------------|
| BUG-01 | Universal Search | Coroutine leak in search (CancellationException) | UniversalSearchEngineImpl.kt | ✅ Fixed |
| BUG-02 | Extension Hub | Illegal initial extension seeding | ExtensionRepository.kt | ✅ Fixed |
| BUG-03 | TV UI | TV Settings D-pad lag (missing key blocks) | TvSettingsScreens.kt | ✅ Fixed |
| BUG-04 | Debrid / Model | DebridAccount constructor mismatch | FakeData.kt | ✅ Fixed |
| BUG-05 | Debrid | Missing coroutine dependency | build.gradle.kts | ✅ Fixed |
| BUG-06 | Debrid / UI | Cross-module smart cast restriction | Multiple files | ✅ Fixed |
| BUG-07 | UI / Debrid | Repeatable @Composable annotations | SettingsScreens.kt | ✅ Fixed |
| BUG-08 | TV UI / Debrid | Column alignment in TvDebridConnectFlow | TvSettingsScreens.kt | ✅ Fixed |
| BUG-09 | Debrid Tests | Test polling threshold and timeouts | FakeDebridProviderClients.kt | ✅ Fixed |
| BUG-22 | Debrid | Non-thread-safe pollCount in fake clients | FakeDebridProviderClients.kt | ✅ Fixed |
| BUG-23 | Core Model | FakeData EPG channel ID mismatch | FakeData.kt | ✅ Fixed |
| BUG-24 | Core Parser | XMLTVParser locale-dependent date parsing | XMLTVParser.kt | ✅ Fixed |
| BUG-29 | Search Pipeline | Dead resolution branches after uppercase() | SearchResultPipeline.kt | ✅ Fixed |
| BUG-30 | Search | SearchEngine creates new instance per search | SearchEngine.kt | ✅ Fixed |
| BUG-31 | Core Model | FakeData shared mutable state (no @Volatile) | FakeData.kt | ✅ Fixed |
| BUG-44 | IPTV Repository | Dead .trim() after regex strip | IPTVRepository.kt | ✅ Fixed |
| BUG-45 | Mobile LiveTV | Spacer after weight(1f) LazyColumn unreachable | LiveTvScreen.kt | ✅ Fixed |
| BUG-46 | Search / Tests | Unclear/brittle test names in ExtensionHubTest | ExtensionHubTest.kt | ✅ Fixed |
| BUG-56 | Search Pipeline | Debug println left in production scoring path | SearchResultPipeline.kt | ✅ Fixed |
| | | _No open low issues_ | | |

---

## Deferred Issues (Known Limitations)

| ID | Area | Description | Tracking |
|----|------|-------------|----------|
| KL-01 | Player | No built-in P2P stream engine | KNOWN_LIMITATIONS.md #1 |
| KL-02 | Search | Hardcoded search timeouts (static configuration) | KNOWN_LIMITATIONS.md #2 |
| KL-03 | Debrid | No real Debrid/network plugins (fake placeholders) | KNOWN_LIMITATIONS.md #3 |
| KL-04 | Security | No SQLCipher (Room not encrypted at rest) | KNOWN_LIMITATIONS.md #4 |
| KL-05 | Security | No certificate pinning | KNOWN_LIMITATIONS.md #5 |
| KL-06 | Debrid | No token refresh automation | KNOWN_LIMITATIONS.md #6 |
| KL-07 | Security | No biometric authentication for sensitive actions | KNOWN_LIMITATIONS.md #7 |
| KL-08 | Platform | EncryptedSharedPreferences requires API 23+ | KNOWN_LIMITATIONS.md #8 |
| KL-09 | Security | Keystore alias corruption on OTA updates | KNOWN_LIMITATIONS.md #9 |
| KL-10 | Stremio | Catalog extra pagination is basic | KNOWN_LIMITATIONS.md #10 |
| KL-11 | Stremio | Custom Stremio resources ignored | KNOWN_LIMITATIONS.md #11 |
| KL-12 | Player | No track selection UI | KNOWN_LIMITATIONS.md #12 |
| KL-13 | Player | No Picture-in-Picture (PiP) | KNOWN_LIMITATIONS.md #13 |

---

## QA-Discovered Issues (New — Mission 15+)

> Append new issues discovered during QA below. Use the next available BUG-ID (starting from BUG-57).

### Critical

| ID | Area | Description | File(s) | Fix Status |
|----|------|-------------|---------|------------|
| | | _None found yet_ | | |

### High

| ID | Area | Description | File(s) | Fix Status |
|----|------|-------------|---------|------------|
| | | _None found yet_ | | |

### Medium

| ID | Area | Description | File(s) | Fix Status |
|----|------|-------------|---------|------------|
| | | _None found yet_ | | |

### Low

| ID | Area | Description | File(s) | Fix Status |
|----|------|-------------|---------|------------|
| | | _None found yet_ | | |

---

## Statistics

| Metric | Count |
|--------|-------|
| Total bugs tracked | 56 |
| Bugs fixed | 56 |
| Bugs open | 0 |
| Known limitations | 13 |
| Regression checklist items | ~92 |
| QA-discovered (new) | 0 |

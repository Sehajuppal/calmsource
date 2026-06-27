# Full App Audit - CalmSource

This document provides a detailed phase-by-phase audit of the CalmSource codebase, covering architecture, database persistence, mobile and TV runtime behaviors, source/search/playback handling, and privacy and security controls.

---

## Phase 0: Current Reality Check

### Git Status & Baseline Commit
- **Current latest commit**: `6665d56 Fix UI compile blockers after capability routing changes`
- **Current git status**:
  - **Modified files**: 48 files across database, network, model, playback, search, extensions, and IPTV modules.
  - **Untracked files**: 46 files including the new `core:discoveryengine` module, Room database migrations, repository classes, domain models, view models, screens, and test suites.

### Project Modules (`settings.gradle.kts`)
The project contains 13 modules:
1. `:app-mobile` — Production Android Mobile app.
2. `:app-tv` — Production Android TV app.
3. `:core:model` — Shared domain models, data types, and privacy rules.
4. `:core:database` — Room database setup, migrations, DAOs, and entities.
5. `:core:network` — OkHttp and Ktor HTTP clients, URL redactors, and Stremio API clients.
6. `:core:parser` — XMLTV/EPG and Stremio manifest parsers.
7. `:core:playback` — Media playback manager, fallback policies, and watch tracking.
8. `:core:sourceintelligence` — Quality, audio, and language parsing heuristics.
9. `:core:discoveryengine` — In-memory indexing, semantic search, and recommendation ranking.
10. `:feature:iptv` — M3U, EPG, and Xtream playlist sync/playback.
11. `:feature:extensions` — Stremio addon install, configuration, and catalog loading.
12. `:feature:debrid` — Real-Debrid and Premiumize availability checks.
13. `:feature:search` — Unified search pipeline merging all catalog results.

---

## Phase 1: Dirty File Audit

Below is the classification of all modified and untracked files currently in the workspace:

### 1. Intended Changes (Mission 23 / User Memory System)
These files represent the core implementation of the Room-backed user memory system, including migrations, entities, repositories, and UI wiring:
- `core/database/src/main/kotlin/com/example/calmsource/core/database/CalmSourceDatabase.kt`
- `core/database/src/main/kotlin/com/example/calmsource/core/database/DatabaseMigrations.kt` [NEW]
- `core/database/src/main/kotlin/com/example/calmsource/core/database/HiltModules.kt` [NEW]
- `core/database/src/main/kotlin/com/example/calmsource/core/database/dao/UserMemoryDao.kt` [NEW]
- `core/database/src/main/kotlin/com/example/calmsource/core/database/entity/UserMemoryEntities.kt` [NEW]
- `core/database/src/main/kotlin/com/example/calmsource/core/database/repository/UserMemoryRepository.kt` [NEW]
- `core/model/src/main/kotlin/com/example/calmsource/core/model/UserMemoryModels.kt` [NEW]
- `core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackMemoryTracker.kt` [NEW]
- `core/playback/src/test/kotlin/com/example/calmsource/core/playback/PlaybackMemoryTrackerTest.kt` [NEW]
- `app-mobile/src/main/java/com/example/calmsource/ui/LibraryScreen.kt` [NEW]
- `app-mobile/src/main/java/com/example/calmsource/ui/HomeViewModel.kt` [NEW]
- `app-mobile/src/main/java/com/example/calmsource/ui/SearchViewModel.kt` [NEW]
- `app-tv/src/main/java/com/example/calmsource/tv/ui/TvLibraryScreen.kt` [NEW]
- `app-tv/src/main/java/com/example/calmsource/tv/ui/TvHomeViewModel.kt` [NEW]
- `app-tv/src/main/java/com/example/calmsource/tv/ui/TvSearchViewModel.kt` [NEW]

### 2. Generated Files
- `core/database/schemas/` — Room database schema exports (Version 6). Keep to track schema changes.
- `node_modules/.vite/deps/_metadata.json` — Vite frontend compilation metadata. Safe to remove (unrelated).

### 3. Local/Private Configs & Debug-Only Logs
These files are local build logs, test logs, or build failures and should be deleted:
- `build-error.txt`
- `catalog-search-compile.txt`
- `catalog-search-tests.txt`
- `compile.log`
- `compile_output.txt`
- `compile_utf8.log`
- `crash.txt`
- `emulator-mission23-error.log`
- `emulator-mission23.log`
- `emulator_logcat.txt`
- `feature-search-tests.txt`
- `final-build-tests.txt`
- `firetv-build.txt`
- `lint-output.txt`
- `playback_tests.log`
- `test-output.txt`
- `test_info.txt`

---

## Phase 2: Build and Compile Audit

- **`gradlew clean`**: Executed successfully (`BUILD SUCCESSFUL`).
- **`gradlew projects`**: All 13 modules listed correctly.
- **KSP Compilation**:
  - `gradlew :core:database:kspDebugKotlin` ran without errors; Room generates `CalmSourceDatabase_Impl` successfully.
  - `gradlew compileDebugKotlin` completed with 0 errors across both applications.
- **Unit Tests**:
  - `gradlew test --no-build-cache` executed all 302 unit tests from scratch; **100% of tests passed**.
- **Debug Builds**:
  - Both `:app-mobile:assembleDebug` and `:app-tv:assembleDebug` compiled and packaged successfully.
- **Lint**:
  - `lintDebug` checks passed with no blocking errors. Deprecation warnings on UI composables exist but do not block the build.

---

## Phase 3: Core Architecture Audit

1. **Database / Room / KSP**: Version is updated to 6. `CalmSourceDatabase` exports the schema to allow schema verification. `MIGRATION_5_6` correctly constructs the six memory tables.
2. **App Initialization**: DI wiring uses Hilt. `DatabaseProvider` correctly falls back to memory database if context is unavailable in unit tests, preventing startup crashes.
3. **Repository Wiring**: UI screens inject repository singletons. `IPTVRepository`, `ExtensionRepository`, and `UserMemoryRepository` handle calls using structured coroutines.
4. **Navigation**: 
  - Mobile uses an in-memory `MobileScreen` state held by an ordinary `remember` statement, ensuring dynamic/temporary stream metadata is never saved to bundle state.
  - TV uses a navigation rail and maps Back events to the parent destination.
5. **Settings & Profiles**: Account/provider credentials are stored in `SecureTokenStore` (encrypted using AES-256-GCM via Android Keystore in production; memory map in tests).
6. **Fake / Demo Data Boundaries**: Static mock data is completely disabled in production modes using a centralized `TestEnvironment.isTest` check.

---

## Phase 4: Database and Persistence Audit

- **CalmSourceDatabase_Impl**: Correctly generated by KSP.
- **Room Entities**: All six user memory entities (Continue Watching, Favorites, Watch History, Recent Channels, Search History, Preference Signals) utilize primitive fields and hold **no URL or link variables**.
- **Room Security Audit**: Validated by `RoomSecurityAuditTest.kt` ensuring that no private URLs, manifest addresses, `xtream://` pseudo-URLs, or credentials can ever enter SQLite tables.
- **Type Converters**: `Converters.kt` updated to serialize list values using JSON serialization with a fallback to legacy comma-separated lists for backward compatibility.
- **Production Seed State**: Empty. In production mode, the app starts with an empty database. Default presets are provided as read-only presets (like Cinemeta, Torrentio, and AIOStreams) and are not inserted into the DB unless chosen by the user.

---

## Phase 5: Mobile Runtime Audit

- **Launch Flow**: Initializes database, Hilt components, and starts catalog indexing.
- **Home / Library Screen**:
  - Library tab displays Continue Watching, Favorites, History, Recent Channels, and Search history.
  - Deleting an item or clearing a section triggers immediate database removal and updates the UI state reactively.
  - Home screen waits for extension catalogs to load before rendering rows (`refreshDiscoveryCatalogHomeRows` with `forceRefresh = true`).
- **Extensions Hub**:
  - Presets for Torrentio and AIOStreams are displayed as default configurations.
  - Manual manifest URLs can be typed, confirmed, and installed.
- **Search Screen**: Refers to `UniversalSearchEngineImpl` and attaches `SearchSignalSink` and `SearchMemorySnapshot` hooks to rank results based on user preferences.
- **Player Screen**: Launches using `PlaybackRequest` with a secure metadata payload and no plain text URL logs.

---

## Phase 6: TV Runtime and D-pad Audit

- **D-pad Navigation**: All controls on the Library and Settings screens are focusable. Cards use `TvFocusCard` for visual scale feedback.
- **Focus Traps**: Settings text fields do not trap focus. Back keys consistently navigate back.
- **Back Behavior**: Returns to the home/rail navigation state.
- **List Scopes**: Library sections utilize section-scoped stable keys (e.g., `"continue-${it.reference.itemKey}"`), ensuring smooth list updates.
- **Empty States**: If a library section is cleared, the focus shifts to the next surviving card or row, preventing focus from being trapped on unmounted controls.

---

## Phase 7: Source, Search, and Playback Audit

- **IPTV Live Channels**: `IPTVRepository` parses M3U files and writes live channels to the database. Resolves channel details using `findChannel()` and plays live streams.
- **Xtream Integration**: Synchronizes live stream metadata. Xtream series episodes are built into stream sources on the fly.
- **Stremio Manifests**: Parses manifest formats, resolves capabilities (Catalog, Stream, Subtitle, Meta), and handles stream requests using `resolveMediaStreams()`.
- **Playback Handoff**: `PlaybackManager` configures the player and fires fallback sources if the primary stream fails.

---

## Phase 8: Privacy, Security, and Fake Data Audit

- **Secrets Redaction**: `NetworkClient.kt` utilizes a pre-compiled regex `URL_REDACT_REGEX` to wipe tokens, keys, and passwords from all Ktor network logs.
- **Debrid Accounts**: Mock Debrid accounts (`deb-rd`, `deb-pm`) are disabled in production, ensuring only user-provided accounts are displayed.
- **User Memory Privacy**: `UserMemoryPrivacy` validates all identifiers, display titles, and search queries, rejecting any string containing URL schemes, bearer tokens, or email formats.
- **Secured Keystore**: Production builds initialize `EncryptedSecureTokenStore` backed by the Android Keystore.

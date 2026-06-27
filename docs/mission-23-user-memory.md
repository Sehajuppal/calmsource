# Mission 23: Room-Backed User Memory

## Scope and Current Status

Mission 23 adds a privacy-filtered Room foundation for user memory. The current
workspace contains:

- Room entities, DAO operations, a `5 -> 6` migration, and
  `RoomUserMemoryRepository`.
- Hilt providers for the database, DAO, and repository.
- Search interfaces that can accept favorite/history snapshots and record a
  completed query.
- Shared privacy validation for persisted references, queries, and signals.

The mobile and TV apps now wire playback, details, search, favorites, and
dedicated Library screens to `UserMemoryRepository`. Fake Home
"Continue Watching" and VOD rows have been removed. Both search screens supply
Room-backed signal and memory adapters to `UniversalSearchEngineImpl`.

## Room Model

`CalmSourceDatabase` is version 6 and exports its schema. `MIGRATION_5_6`
creates these tables:

| Table | Purpose | Ordering and retention |
| --- | --- | --- |
| `continue_watching` | Resume state for non-live content | Newest `updatedAt` first; keeps 200 |
| `favorites` | Saved content references | Newest `updatedAt` first; no repository trim limit |
| `watch_history` | Non-live watch events and last progress | Newest `lastWatchedAt` first; keeps 500 |
| `recent_channels` | Recently watched live channels | Newest `lastWatchedAt` first; keeps 50 |
| `search_history` | Completed, safe search text | Newest `lastSearchedAt` first; keeps 50 |
| `preference_signals` | Counted opaque preference keys | Observed by highest count, then newest; trims to the 200 most recently signaled keys |

All item tables use an opaque `itemKey` as the primary key. Search history uses
the lowercase normalized query as its primary key. Preference signals use
`signalType + signalKey`.

`observeLastWatchedChannel()` is derived from the first row in
`recent_channels`; there is no separate last-channel record.

## Update Semantics

### Continue Watching

- Accepts `MOVIE`, `SHOW`, `EPISODE`, or `VOD`; it rejects `LIVE_CHANNEL`.
- Upserting the same `itemKey` replaces progress, duration, metadata, and
  `updatedAt`.
- Progress and duration must be non-negative.
- The repository does not clamp progress to duration, define a minimum watch
  threshold, remove completed items, or periodically capture player progress.
  Callers must define those policies when playback is wired.

### Favorites

- `toggleFavorite()` inserts a missing item or removes an existing item.
- `setFavorite()` supports idempotent on/off behavior. Updating a favorite
  preserves its original `createdAt` and refreshes `updatedAt`.
- The repository permits any safe content type, including a live channel.
- Favorites are not automatically trimmed.

### Watch History

- Accepts non-live content and rejects `LIVE_CHANNEL`.
- Each `recordWatchHistory()` call increments `watchCount`, preserves the first
  watch timestamp, and updates the last timestamp, progress, and duration.
- A record call is treated as a watch event. The repository does not decide
  whether startup, a progress checkpoint, completion, or player exit should
  count as that event.

### Recent and Last Live Channel

- `recordRecentChannel()` accepts only `LIVE_CHANNEL`.
- Rewatching the same channel increments `watchCount` and updates
  `lastWatchedAt`.
- Recent live tracking stores channel identity and display metadata only. It
  does not store a playback URL or resume position.
- The latest recent-channel row is the last watched channel.

### Search History and Signals

- `recordSearch()` collapses whitespace, trims text, normalizes the key to
  lowercase, increments `searchCount`, and updates `lastSearchedAt`.
- Unsafe or blank queries return `false` and are not persisted.
- `incrementPreferenceSignal()` accepts `CONTENT_TYPE`, `PROVIDER`, `SOURCE`,
  `GENRE`, or `SEARCH_RESULT_SELECTION`, with increments from 1 through 1000.
- Signals are counted opaque identifiers, not raw source data.

`UniversalSearchEngineImpl` has optional `SearchSignalSink` and
`SearchMemorySnapshot` hooks. When explicitly supplied, it can:

- Apply existing favorite and history ID bonuses during result ranking.
- Record one safe query after all providers complete.
- Ignore optional memory failures so search itself still works.

The production mobile and TV search screens record completed safe queries and
load favorite/history IDs for ranking. Recent queries are shown in Library.
Preference signals are retained for future work but are not used to create a
recommendation engine or personalized recommendation rail.

## Privacy Invariants

User-memory tables may contain only safe display metadata and opaque IDs:

- `itemKey`, `contentType`, `title`, optional `subtitle`, optional
  `providerId`, and optional `sourceId`.
- Progress, duration, timestamps, and aggregate counts.
- Sanitized search text and opaque preference signal keys.

They must never contain:

- Raw playback URLs or resolved stream links.
- Stremio manifest URLs, manifest addresses, or resolved add-on base URLs.
- `xtream://` pseudo URLs or the HTTP URLs produced when they are resolved.
- Xtream server addresses, usernames, passwords, or stream tokens.
- Debrid API keys, bearer values, authorization headers, JWTs, credentials, or
  other secrets.

`UserMemoryPrivacy` enforces this boundary before repository writes:

- Control characters are removed, whitespace is collapsed, and values are
  length-limited.
- IDs reject whitespace, path/query separators, URL schemes, encoded schemes,
  credential assignments, bearer values, JWT-like values, and email-like
  values.
- Display text and search queries reject URL/domain-like and secret-like data.
- Completed search recording additionally rejects source protocols such as
  HTTP, RTSP, magnet, Stremio, Acestream, and Xtream, plus manifest addresses.

Persist a stable, non-secret channel or media ID. Keep
`xtream://stream_id/...` in the playback path until the player resolves it; do
not copy it into user memory. The repository does not log persisted values.

## VOD and Live Tracking Rules

| User action | Non-live content | Live channel |
| --- | --- | --- |
| Save resume progress | `continue_watching` | Never |
| Record watch event | `watch_history` | Never |
| Record recent/last channel | Never | `recent_channels` |
| Favorite | Allowed | Allowed |
| Store playback URL | Never | Never |

Callers should classify from trusted content metadata, not from a resolved URL.
Room enforces the split at repository entry points by checking
`UserMemoryContentType`.

## Remove and Clear Behavior

The repository exposes independent per-item and per-section operations:

- Continue Watching: remove by `itemKey`, or clear all.
- Favorites: remove by `itemKey`, toggle off, or clear all.
- Watch History: remove by `itemKey`, or clear all.
- Recent Channels: remove by `itemKey`, or clear all.
- Search History: remove by normalized safe query, or clear all.
- Preference Signals: clear all only.

There is no single atomic "clear every user-memory table" API. Clearing one
section does not clear another: for example, clearing watch history does not
remove favorites or Continue Watching. Invalid or sensitive item keys are
rejected rather than used in a delete query.

## Mobile and TV Library

The Library UIs observe Room flows directly so updates, removals, and clears
appear without restarting the app. Both apps expose the same data and actions:

- Continue Watching resumes non-live content from stored progress.
- Favorites opens the saved media or live-channel destination.
- History opens non-live details or playback without treating it as resume
  state unless a Continue Watching row also exists.
- Recent Channels and Last Channel start live playback through
  `IPTVRepository.findChannel()` and
  `IPTVRepository.buildLivePlaybackRequest()`.
- Search History can refill or submit a prior safe query.
- Empty, loading, and error states must not fall back to fake production items.
- Per-item remove and per-section clear actions must update both apps
  consistently.

### Mobile Expectations

- Library navigation should be reachable from the normal mobile navigation
  model and preserve a sensible Back destination.
- Touch targets must expose open/resume, remove, favorite/unfavorite, and clear
  actions without revealing source URLs.
- Sections should use the repository order and show progress only for
  non-live Continue Watching or history records.

### TV D-pad Expectations

- Every interactive card and action must be reachable with a remote; no action
  may require touch input.
- Focus must be visible. Library cards should follow the existing
  `TvFocusCard` convention: focused border and scale feedback, with Center/OK
  activating the focused action.
- Left/Right moves within a row; Up/Down moves between sections and the
  navigation rail without trapping focus in text or action controls.
- Back returns to the previous Library/navigation context instead of exiting
  unexpectedly.
- After removing an item, focus should move to a nearby surviving item or the
  section header. After clearing a section, focus should move to the next
  available section or navigation control.
- Long lists must scroll the focused item into view, and empty sections must
  not leave an invisible focus target.

The TV implementation uses stable lazy-list keys and `TvFocusCard` for Library
open, remove, and clear actions.

## Manual Verification Checklist

Use Android Studio's bundled JBR for all Gradle checks:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

### Room and Build

- [ ] Run Room/KSP generation directly. If
  `CalmSourceDatabase_Impl does not exist`, treat it as generation failure.

```powershell
.\gradlew.bat :core:database:kspDebugKotlin --stacktrace --no-daemon --console=plain
```

- [ ] Build both apps and save useful errors.

```powershell
.\gradlew.bat :app-mobile:assembleDebug :app-tv:assembleDebug --stacktrace --no-daemon --console=plain > build-error.txt 2>&1
Select-String -Path build-error.txt -Pattern 'BUILD SUCCESSFUL|BUILD FAILED|FAILED|What went wrong|Execution failed|Exception|e: |error:|Could not|Unresolved reference|Cannot|Room|KSP|ksp'
```

- [ ] Confirm the version 6 schema contains all six memory tables and that a
  version 5 database migrates without destructive recreation.

### Automated Tests and Lint

```powershell
.\gradlew.bat testDebugUnitTest --continue --stacktrace --no-daemon --console=plain > test-output.txt 2>&1
.\gradlew.bat :app-mobile:lintDebug :app-tv:lintDebug --continue --stacktrace --no-daemon --console=plain > lint-output.txt 2>&1
```

- [ ] Check `test-output.txt` for failed privacy, search, repository, and Room
  tests.
- [ ] Check `lint-output.txt` for new lint errors. Existing Android TV
  `TvLazyColumn`/`TvLazyRow` deprecation warnings are non-blocking.
- [ ] Treat new compile errors, lint errors, Room/KSP failures, and app crashes
  as blocking.

### Privacy and Data Behavior

- [ ] Verify safe opaque IDs and normal titles persist.
- [ ] Verify URLs, manifest addresses, `xtream://` values, resolved links,
  credentials, tokens, bearer values, JWTs, and secret-like queries do not
  enter any user-memory table or Logcat.
- [ ] Verify VOD writes cannot enter `recent_channels`, and live channels
  cannot enter Continue Watching or watch history.
- [ ] Verify repeated watches/searches/channels increment counts and refresh
  timestamps.
- [ ] Verify each remove and clear operation affects only its documented
  section.
- [ ] Verify retention limits preserve the newest rows.

### Mobile and TV Manual Checks

- [ ] Confirm both apps show the same Room-backed Library contents after
  playback, favorite, remove, and clear actions are wired.
- [ ] Confirm non-live playback resumes from stored progress and live playback
  never displays a resume bar.
- [ ] Confirm recent/last live playback resolves through `IPTVRepository` and
  no raw URL is placed in UI state intended for persistence.
- [ ] Confirm mobile Back behavior returns to the expected Library context.
- [ ] On TV, traverse every Library item and action using only D-pad
  Left/Right/Up/Down, Center/OK, and Back.
- [ ] On TV, verify visible focus, automatic scrolling, no focus traps, and
  stable focus after removing an item or clearing a section.
- [ ] Confirm search history/ranking is described as active only after real
  Room-backed `SearchSignalSink` and `SearchMemorySnapshot` adapters are wired.
- [ ] Confirm no UI labels or documentation claim personalized
  recommendations, because recommendation behavior is not implemented.

## Code References

- `core/model/src/main/kotlin/com/example/calmsource/core/model/UserMemoryModels.kt`
- `core/database/src/main/kotlin/com/example/calmsource/core/database/entity/UserMemoryEntities.kt`
- `core/database/src/main/kotlin/com/example/calmsource/core/database/dao/UserMemoryDao.kt`
- `core/database/src/main/kotlin/com/example/calmsource/core/database/repository/UserMemoryRepository.kt`
- `core/database/src/main/kotlin/com/example/calmsource/core/database/DatabaseMigrations.kt`
- `core/database/src/main/kotlin/com/example/calmsource/core/database/CalmSourceDatabase.kt`
- `feature/search/src/main/kotlin/com/example/calmsource/feature/search/SearchSignals.kt`
- `feature/search/src/main/kotlin/com/example/calmsource/feature/search/UniversalSearchEngineImpl.kt`
- `app-mobile/src/main/java/com/example/calmsource/Navigation.kt`
- `app-mobile/src/main/java/com/example/calmsource/ui/HomeScreen.kt`
- `app-mobile/src/main/java/com/example/calmsource/ui/SearchScreen.kt`
- `app-tv/src/main/java/com/example/calmsource/tv/TvMainActivity.kt`
- `app-tv/src/main/java/com/example/calmsource/tv/ui/TvHomeScreen.kt`
- `app-tv/src/main/java/com/example/calmsource/tv/ui/TvSearchScreen.kt`
- `app-tv/src/main/java/com/example/calmsource/tv/ui/TvUiComponents.kt`

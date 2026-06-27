# Crash and Error Matrix

This document acts as the central registry for bugs found during Mission 21 chaos testing.

## Bug Registry (PASS 1)

### BUILD & PERFORMANCE
- **BUG-21-001**
  - **Title:** OOM during assembleDebug (Dex merging)
  - **Severity:** High
  - **Area:** Gradle / Build
  - **Found by:** SA1
  - **Status:** Open (Needs memory increase in gradle.properties)

- **BUG-21-002**
  - **Title:** M3U Import Memory/CPU Leak (O(N^2) allocation and I/O spam)
  - **Severity:** Critical
  - **Area:** IPTV Repository / DB
  - **Found by:** SA10
  - **Status:** Open

- **BUG-21-003**
  - **Title:** Main-Thread ANR sorting 100k+ channels
  - **Severity:** Critical
  - **Area:** IPTVRepository
  - **Found by:** SA10
  - **Status:** Open

- **BUG-21-004**
  - **Title:** Source Health DB Read Spam
  - **Severity:** High
  - **Area:** SourceHealthRepository
  - **Found by:** SA10
  - **Status:** Open

- **BUG-21-005**
  - **Title:** Network Timeouts missing on sync routines
  - **Severity:** Medium
  - **Area:** Network / Ktor Clients
  - **Found by:** SA10
  - **Status:** Open

### PLAYBACK & FALLBACK
- **BUG-21-006**
  - **Title:** Infinite Re-Prepare Failure Loop
  - **Severity:** Critical
  - **Area:** PlaybackManager / PlayerScreen
  - **Found by:** SA5
  - **Status:** Open

- **BUG-21-007**
  - **Title:** Fallback Timeout Hang
  - **Severity:** High
  - **Area:** PlaybackManager stateTrackingJob
  - **Found by:** SA5
  - **Status:** Open

- **BUG-21-008**
  - **Title:** Zombie ExoPlayer on Initialization Failure
  - **Severity:** High
  - **Area:** PlaybackManager / Media3
  - **Found by:** SA5
  - **Status:** Open

### UI & UX (MOBILE AND TV)
- **BUG-21-009**
  - **Title:** Swallowed back presses (Double-press cancels stack pop)
  - **Severity:** Medium
  - **Area:** Mobile UI BackHandler
  - **Found by:** SA7
  - **Status:** Open

- **BUG-21-010**
  - **Title:** Clipped Controls on Long Labels
  - **Severity:** Medium
  - **Area:** Mobile/TV Settings Items
  - **Found by:** SA7
  - **Status:** Open

- **BUG-21-011**
  - **Title:** TV D-Pad Search Flow drops focus on typing
  - **Severity:** High
  - **Area:** TV Search
  - **Found by:** SA8
  - **Status:** Open

- **BUG-21-012**
  - **Title:** TV Settings AnimatedContent Focus Trap
  - **Severity:** High
  - **Area:** TV Settings
  - **Found by:** SA8
  - **Status:** Open

- **BUG-21-013**
  - **Title:** TV Details Flow Mobile Dialogs
  - **Severity:** Medium
  - **Area:** TV Details
  - **Found by:** SA8
  - **Status:** Open

- **BUG-21-014**
  - **Title:** UI Search Thrashing (Compose recomposition)
  - **Severity:** Medium
  - **Area:** Search UI
  - **Found by:** SA10
  - **Status:** Open

- **BUG-21-015**
  - **Title:** Dirty State on Setup Cancel
  - **Severity:** Low
  - **Area:** Mobile/TV setup dialogs
  - **Found by:** SA7
  - **Status:** Open

### PERSISTENCE, PRIVACY & SECURITY
- **BUG-21-016**
  - **Title:** Database Leak (Orphaned IPTV/Xtream Records)
  - **Severity:** Critical
  - **Area:** Room DB Entities
  - **Found by:** SA9
  - **Status:** Open

- **BUG-21-017**
  - **Title:** Token Loss for all accounts of same provider type
  - **Severity:** High
  - **Area:** SecureTokenStore
  - **Found by:** SA9
  - **Status:** Open

- **BUG-21-018**
  - **Title:** User Preferences Race Condition
  - **Severity:** High
  - **Area:** UserPreferencesRepository
  - **Found by:** SA9
  - **Status:** Open

- **BUG-21-019**
  - **Title:** Query Parameter Injection (Unencoded passwords)
  - **Severity:** Critical
  - **Area:** XtreamRepository
  - **Found by:** SA9
  - **Status:** Open

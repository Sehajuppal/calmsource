# IPTV & Extension Critical Bug Fix Plan

**Date:** 2026-06-13  
**Scope:** Critical bugs preventing IPTV playback and extension functionality on Fire TV and Android devices.

---

## Summary of Root Causes

After scanning all 4 major modules (IPTV, extensions, playback, model), the 3 recent commits, and Fire TV runtime logs, I found 6 critical failure chains:

### Why IPTV Doesn't Work

1. **Cross-provider health poisoning (BUG-IPTV-1)**: Xtream channels from different providers with the same stream ID share a `safeSourceId` because the pseudo-URL `xtream://stream_id/12345` doesn't include the provider ID. A bad channel on provider A permanently poisons the same stream ID on provider B via shared `SourceHealth` records.

2. **Silent credential loss on Fire TV (BUG-IPTV-2)**: `EncryptedSharedPreferences` fails silently on older Android TV devices. `savePassword` catches all errors and returns without saving, so `addXtreamProvider` never knows credentials weren't stored. The provider appears installed but sync fails with "Credentials not found."

3. **Silent URL resolution failure (BUG-IPTV-3)**: `resolvePlaybackUrl` has a chain of `?: return channel.streamUrl` fallbacks. When any link fails (missing username, password, provider), it returns the unplayable `xtream://` pseudo-URL. The player shows a generic error but the user never learns their credentials are broken.

### Why Extensions Don't Work

4. **Private IP filter bypass (BUG-EXT-1)**: `ExtensionInstallValidator.isLocalOrPrivateIp` strips brackets into `lowerHost` but then calls `host.split(".")` on the **original** host string. `[192.168.1.1].split(".")` yields `["[192", "168", "1", "1]"]`, so `p1 = "[192".toIntOrNull()` returns `null` — the entire IPv4 check is skipped. `InetAddress.getByName(lowerHost)` is the fallback, but if DNS is unavailable the bypass succeeds.

5. **Silent config parse failure (BUG-EXT-2)**: `getAddonConfigList` returns `emptyList()` on any exception. `checkAddonHealth` then can't detect that a manifest requires configuration, marking incorrectly configured extensions as `ACTIVE` instead of `NEEDS_CONFIGURATION`.

### Runtime Stability (from Fire TV logs)

6. **Main thread jank**: `Skipped 99 frames!` on startup — `onCreate` performs heavy synchronous init work (IPTVRepository.init, DiscoveryEngine.initialize)
7. **FTS5 not available** on Fire TV — full-text search silently disabled
8. **CursorWindow full** 15+ times/session — Room queries loading oversized rows during extension catalog ingestion
9. **Heap growth** 22MB → 86MB with frequent GC pauses

---

## Fix Plan

### Fix 1: Add provider ID to Xtream pseudo-URLs (BUG-IPTV-1)

**Files to change:**
- `feature/iptv/src/main/kotlin/.../xtream/XtreamStreamUrlBuilder.kt` — add `providerId` parameter to `createPseudoUrl()`
- `feature/iptv/src/main/kotlin/.../xtream/XtreamMappers.kt` — pass `providerId` when calling `createPseudoUrl`
- `feature/iptv/src/main/kotlin/.../IPTVRepository.kt` — update `resolvePlaybackUrl` to extract providerId from new URL format

**Change:** `createPseudoUrl(streamId)` → `createPseudoUrl(providerId, streamId)` producing `xtream://stream_id/$providerId/$streamId`.

### Fix 2: Make EncryptedIptvSecureTokenStore throw on failure (BUG-IPTV-2)

**Files to change:**
- `feature/iptv/src/main/kotlin/.../IptvSecureTokenStore.kt` — `savePassword` should throw `IllegalStateException("Encrypted storage unavailable")` when `prefs == null`, not silently return

**Change:** Replace `prefs ?: return` with an explicit throw so `addXtreamProvider`'s `runCatching` properly catches it and performs the DB rollback with a clear error message.

### Fix 3: Add error reporting to resolvePlaybackUrl chain (BUG-IPTV-3)

**Files to change:**
- `feature/iptv/src/main/kotlin/.../IPTVRepository.kt` — add a new method `resolvePlaybackUrlOrError(): Result<String>` 

**Change:** Add a `resolvePlaybackUrlOrError(): Result<String>` that returns specific failure reasons ("Provider not found", "Credentials missing", "Invalid stream ID") so the UI can show meaningful errors instead of a generic playback error.

### Fix 4: Fix bracket bypass in isLocalOrPrivateIp (BUG-EXT-1)

**Files to change:**
- `feature/extensions/src/main/kotlin/.../ExtensionInstallValidator.kt` — use `lowerHost` for the `split(".")` call instead of `host`

**Change:** Replace `val ipParts = host.split(".")` with `val ipParts = lowerHost.split(".")` so bracket-stripped IPs are properly checked.

### Fix 5: Log parse failures in getAddonConfigList (BUG-EXT-2)

**Files to change:**
- `feature/extensions/src/main/kotlin/.../ExtensionRepository.kt` — add a warning log when the config JSON parse fails

**Change:** Add `android.util.Log.w("ExtensionRepository", "Failed to parse addon config: ${e.message}")` inside the catch block.

### Fix 6: Defer non-critical init to reduce startup jank

**Files to change:**
- `app-tv/src/main/java/.../CalmSourceApp.kt` 
- `app-mobile/src/main/java/.../CalmSourceApp.kt`

**Change:** Wrap `IPTVRepository.init()`, `DiscoveryEngine.initialize()`, and `IptvSyncScheduler.schedulePeriodicSync()` in a `coroutineScope.launch(Dispatchers.IO)` to avoid blocking `onCreate` on the main thread.

---

## Verification Steps

1. **Build both apps**: `.\gradlew.bat :app-mobile:assembleDebug :app-tv:assembleDebug`
2. **Run unit tests**: `.\gradlew.bat testDebugUnitTest --continue`
3. **Verify Fix 1**: Add two Xtream providers with overlapping stream IDs. Confirm source health is tracked independently per provider.
4. **Verify Fix 2**: On Fire TV (or emulated low-storage device), add an Xtream provider. Confirm credentials are stored or a clear error is shown to the user.
5. **Verify Fix 3**: Try playing a channel with missing credentials. Confirm the error message indicates "Credentials missing" not "Can't play this stream."
6. **Verify Fix 4**: Test `isLocalOrPrivateIp("[192.168.1.1]")` — must return `true`.
7. **Verify Fix 6**: Check logcat — `Skipped frames` should be reduced on startup.

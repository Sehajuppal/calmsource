# AI Handoff Instructions

This repo contains CalmSource Android mobile and TV apps. The user cares about the
Android apps, especially Stremio-style extensions and IPTV/Xtream playback. Do not
spend time on Astro/site build issues unless the user explicitly asks for that.

## Product Rules

- Stremio extensions must behave like Stremio add-ons. Keep Torrentio and
  AIOStreams as the recommended presets.
- Do not seed fake production extensions such as Public Domain Movies, Slow
  Catalog Addon, or Failed Addon Engine. Test-only fixtures are okay.
- Debrid support is optional helper plumbing, not the only way to make streams
  work. Torrentio/AIOStreams/manual manifest URLs must remain usable without a
  Debrid account.
- Xtream credentials and raw playback URLs are sensitive. Do not persist or log
  passwords/tokens/raw URLs. Preserve `xtream://stream_id/...` pseudo URLs until
  the player resolves them for playback.

## Code Rules

- Use `IPTVRepository.getLiveChannels()`, `IPTVRepository.findChannel()`, and
  `IPTVRepository.buildLivePlaybackRequest()` for live playback UI. Do not look
  up real playback channels from `FakeData.liveChannels`.
- Do not call `DatabaseProvider.db` from Hilt providers or other app startup
  paths without a fallback. Use `DatabaseProvider.getDatabase(context)` when a
  `Context` is available.
- If Room says `CalmSourceDatabase_Impl does not exist`, treat it as a KSP/build
  generation problem. Verify `:core:database:kspDebugKotlin`; do not patch around
  it by removing Room usage.
- Extension preview/install flows should catch failures and return UI state
  errors. Do not block TV install on a `Flow.first { ... }` wait for the newly
  installed extension.
- Mobile and TV should stay behaviorally paired. When fixing extension, details,
  home, live TV, or playback flows, check both apps.

## Verification

Use Android Studio's bundled JBR on this Windows workspace:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

Build both apps and save useful errors:

```powershell
.\gradlew.bat :app-mobile:assembleDebug :app-tv:assembleDebug --stacktrace --no-daemon --console=plain > build-error.txt 2>&1
Select-String -Path build-error.txt -Pattern 'BUILD SUCCESSFUL|BUILD FAILED|FAILED|What went wrong|Execution failed|Exception|e: |error:|Could not|Unresolved reference|Cannot|Room|KSP|ksp'
```

Before handing back substantial changes, also run:

```powershell
.\gradlew.bat testDebugUnitTest --continue --stacktrace --no-daemon --console=plain > test-output.txt 2>&1
.\gradlew.bat :app-mobile:lintDebug :app-tv:lintDebug --continue --stacktrace --no-daemon --console=plain > lint-output.txt 2>&1
```

Existing Android TV `TvLazyColumn` / `TvLazyRow` deprecation warnings are
non-blocking. New compile errors, lint errors, Room/KSP failures, and app crashes
are not.

## Workspace Hygiene

The worktree may already be dirty. Do not revert unrelated user changes or delete
old logs/generated folders unless the user asks. Keep edits scoped to the mobile,
TV, feature, or core modules needed for the current Android task.

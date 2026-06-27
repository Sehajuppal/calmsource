# Playback Failure Matrix

This matrix tracks known playback failures and their stabilization resolutions across the application.

## Media3 Playback Issues (SA5)

| Failure Mode | Root Cause | Resolution / Stabilized Behavior |
| :--- | :--- | :--- |
| Infinite prompt loops | `isFallbackAllowed` logic bug returned true even when candidates were empty. | Logic corrected; fallback aborts cleanly when no candidates remain. |
| Zombie `ExoPlayer` (invisible audio or crashes) | Async fallback jobs executing after player release due to rapid UI exit (e.g. user hitting "back" on a dead stream). | Jobs bound to lifecycle, cancelled on `release()`, and gated behind `isActive`. |
| Main Thread ANR on 10,000+ channel playlists | `TvIptvSettingsSection` and `TvPlayerScreen` called `IPTVRepository.getChannels()` synchronously on the Main thread. | Patched into background `Dispatchers.IO` `LaunchedEffect` blocks. |
| `TvPlayerScreen` D-pad Focus Trap | Focus trapped invisibly on the player background canvas when live channel switcher was opened. | Focus explicitly requested into the switcher drawer; `Key.DirectionRight` properly dismisses it. |
| `TvDetailsScreen` Clipped Dead Zone | Advanced Stream Sources panel packed 50+ elements into a single lazy `item { ... }` block, breaking D-pad scrolling. | Unrolled into `items()`, restoring native D-pad navigation. |
| Stream Deduplication Collision | `SearchResultDeduplicator` grouped by normalized title strings, causing unrelated movies with identical names to merge incorrectly. | Grouping logic hardened to strictly use `mediaItem.id` for deduplication. |



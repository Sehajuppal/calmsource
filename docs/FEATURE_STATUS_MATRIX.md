# Feature Status Matrix

This matrix documents the verification state of all 50 features in CalmSource.

| Feature Area | Status | Evidence | Real/Fake/Placeholder Notes | Tests | Risks | Next Action |
|--------------|--------|----------|-----------------------------|-------|-------|-------------|
| 1. Mobile app launch | REAL_WORKING | Starts on Android Mobile. | Real Activity and Application setup. | Yes | None | Monitor startup latency |
| 2. TV app launch | REAL_WORKING | Starts on Android TV. | Real Leanback-like UI with focus states. | Yes | Focus engine variations | Optimize Leanback dependencies |
| 3. Mobile navigation | REAL_WORKING | Navigation graph works. | Uses Jetpack Compose Navigation. | Yes | None | Maintain route consistency |
| 4. TV D-pad navigation | REAL_WORKING | D-pad moves focus. | Handled via custom focus modifiers. | Yes | Focus traps on lists | Periodically test focus patterns |
| 5. Home screen | REAL_WORKING | Home composable renders. | Displays lists, EPG grids, search inputs. | Yes | None | Performance with large datasets |
| 6. Universal Search | REAL_WORKING | Merges IPTV, Extensions. | Active matching pipeline. | Yes | Network timeouts | Implement cache fallback |
| 7. Search result merging | REAL_WORKING | Merges duplicates. | Verified by search merging tests. | Yes | None | Improve deduplication regex |
| 8. Spider-Man regression | REAL_WORKING | Spider-Man exact match. | Resolution fallback and debrid cache fix. | Yes | None | Keep exact matching tests updated |
| 9. Details screen | REAL_WORKING | Details overlay works. | Renders meta information from models. | Yes | None | Keep titles clean of raw filenames |
| 10. Stream Picker | REAL_WORKING | Displays stream options. | Real layout with details/labels. | Yes | None | Sort by user preferences |
| 11. Play Best Match | REAL_WORKING | Selects best resolution. | Fallback logic runs synchronously. | Yes | Incorrect metadata | Enhance quality parser |
| 12. Manual Sources / Advanced | REAL_WORKING | Displays filenames. | Controlled via `showRawDetails` setting. | Yes | Exposing private URLs | Enforce URL redaction rules |
| 13. Media3 playback handoff | REAL_WORKING | Plays video streams. | Wraps ExoPlayer instance dynamically. | Yes | Memory leaks | Verify player release on exit |
| 14. Player overlay mobile | REAL_WORKING | Play/Pause/Seek controls. | Compose overlays over ExoPlayer. | Yes | None | Improve seek sensitivity |
| 15. Player overlay TV | REAL_WORKING | D-pad focusable controls. | Uses TV Focus Cards. | Yes | None | Custom Seek intervals (D-pad) |
| 16. Live TV list | REAL_WORKING | Displays synced channels. | LazyColumn list with tvg logos. | Yes | List latency | Implement pagination |
| 17. TV Guide | REAL_WORKING | Shows grid of programs. | Interactive horizontal/vertical grid. | Yes | Large EPG datasets | Add lazy loading for next days |
| 18. Live TV playback | REAL_WORKING | Stream links play. | ExoPlayer handles HLS/M3U8. Includes secure JIT resolution for `xtream://` pseudo-URLs. | Yes | Stream buffering | Add auto-reconnect policy |
| 19. Channel switching | REAL_WORKING | Fast channel switches. | Player instance is reused correctly. | Yes | Player crash on error | Implement play recovery |
| 20. Now/next EPG overlay | REAL_WORKING | Dynamic EPG bar. | Time ticker updates program status. | Yes | Timezone offset parsing | Test with different locales |
| 21. M3U import | REAL_WORKING | Parses M3U files. | Custom M3U parser with regex tag support. | Yes | Corrupt file layouts | Lenient line parsing |
| 22. XMLTV import | REAL_WORKING | Parses XML EPG feeds. | Parses timezones and formats program lists. | Yes | Memory consumption | SAX parser optimization |
| 23. EPG matching | REAL_WORKING | Fuzzy channel matching. | Name normalization and exact map checking. | Yes | Blank name collisions | Reject empty EPG matching inputs |
| 24. IPTV provider persistence | REAL_WORKING | Saves to Room DB. | Room DAOs insert/read. | Yes | Room transaction deadlocks | Use coroutine dispatchers correctly |
| 25. Xtream Login setup | REAL_WORKING | Validates login. | Client pings server endpoints, shows cleartext warnings. | Yes | Password exposure in error logs | Strict redaction check |
| 26. Xtream sync | REAL_WORKING | Fetches categories/streams. | Sync service processes VOD/Live/Series and batches Room inserts. | Yes | DB bottleneck during write | Batch Room inserts |
| 27. Xtream live channel sync | REAL_WORKING | Imports channels. | Saved to IPTV channels table. | Yes | Duplicate channel IDs | Map to unified IDs |
| 28. Xtream VOD sync | PARTIAL_IMPLEMENTATION | Imports VOD and saves to Room. | Search integration uses static list and playback URL is a pending TODO. | Yes | Large catalog size | Wire Room DAO to VODSearchProvider |
| 29. Extension Hub | REAL_WORKING | Lists, installs extensions. | Handles manifest URLs and config flows. | Yes | None | Verify manifest validation |
| 30. Catalog Extensions click flow | REAL_WORKING | Opens catalog. | Fixed NPE and syntax compile errors. | Yes | Empty results crash | Handled with calm empty UI |
| 31. Extension manifest loading | REAL_WORKING | Network load & parse. | Fetches manifest from secure endpoints. | Yes | Manifest parsing exceptions | Type check manifest attributes |
| 32. Extension catalog endpoint | REAL_WORKING | Fetches Stremio catalogs. | Standard Stremio HTTP requests. | Yes | Heavy payload sizes | Limit body to 5MB |
| 33. Extension search endpoint | REAL_WORKING | Searches extension catalog. | Translates search queries. | Yes | None | Add query rate limiting |
| 34. Extension meta endpoint | REAL_WORKING | Fetches detailed metadata. | Fetches movie details from addons. | Yes | None | Implement local caching |
| 35. Extension stream endpoint | REAL_WORKING | Fetches stream list. | Resolves watch links. | Yes | None | Sanitize display labels |
| 36. Extension subtitles endpoint | REAL_WORKING | Fetches subtitle tracks. | Parses subtitle files from addon. | Yes | Unsupported subtitle format | Support standard SRT/VTT |
| 37. Configurable addon flow | REAL_WORKING | Renders fields dynamically. | Form generates inputs based on metadata. | Yes | None | Encrypt secret values |
| 38. Stremio compatibility | REAL_WORKING | Compatible with manifests. | Follows Stremio v1 protocol specifications. | Yes | Protocol changes | Implement compatibility warnings |
| 39. Debrid Connect | FAKE_PLACEHOLDER | Mock auth flows in repository. | Fake UI flow, returns mock tokens. | Yes | Requires full integration | Implement real OAuth/Device Code |
| 40. Debrid real API | NOT_IMPLEMENTED | No real network requests. | Fake clients simulate API responses. | No | Missing real validation | Write real provider API clients |
| 41. Room persistence | REAL_WORKING | Kotlin Room setup. | Migrated Room DAOs and Entities to Kotlin. | Yes | Migration version changes | Keep schema versions matching |
| 42. SecureTokenStore | REAL_WORKING | EncryptedSharedPreferences | Encrypts API keys and passwords. | Yes | SharedPreferences corruptions | Fallback to secure memory-only store |
| 43. Source health scoring | REAL_WORKING | Ranks stream health. | Telemetry tracks failures/buffer delays. | Yes | Telemetry table bloating | Clean health data periodically |
| 44. Auto fallback | REAL_WORKING | Switches to next stream. | Policies switch automatically on error. | Yes | Infinite fallback loop | Limit max retries to 3 |
| 45. Source intelligence parsing | REAL_WORKING | Parses files. | Quality, Resolution, Codec, Audio. | Yes | Regex mismatches | Keep test regex matches comprehensive |
| 46. Language/quality labels | REAL_WORKING | Generates UI strings. | Language + Quality labels dynamically built. | Yes | None | Support localized language strings |
| 47. Privacy/redaction | REAL_WORKING | Redacts URLs and tokens. | Error logs and database fields are redacted. | Yes | Missing regex edge cases | Audit redaction regularly |
| 48. Error states | REAL_WORKING | Handles failure UI. | Renders detailed error message box. | Yes | None | Human-readable recommendations |
| 49. Performance on large lists | REAL_WORKING | Smooth scrolling. | LazyColumns with key modifiers. | Yes | None | Profile layout rendering on TV |
| 50. Low-end TV risk areas | REAL_WORKING | Ktor InputStream iteration prevents OOM. | Lazy lists and iterative parsing handle 100k+ playlists safely. | Yes | UI Frame drops | Profile Compose rendering on TV |

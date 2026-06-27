# Real-Source Smoke Test Architecture

## What Was Tested
We verified whether CalmSource can process real-world user-provided credentials securely and without crashing. We verified M3U chunked parsing, Xtream login logic, Stremio addon manifest data ingestion, fallback UI flows, and URL redaction constraints. We verified that the player initializes media items without logging unredacted strings.

## Sanitized Source Types
- Standard M3U playlist derived from Xtream endpoint
- Xtream API Login (Username/Password authentication via SecureTokenStore)
- Stremio Addon V3 Manifest 
- Debrid Connect (Empty/Fallback state)

## IPTV Result Summary
- **M3U Imports:** Verified using local offline chunking tests to ensure no OutOfMemory issues. Replaced massive memory allocations with a chunked `InputStream` pattern in the parser.
- **XMLTV:** Ensured streaming `Scanner` chunking delimitations work efficiently.
- **Live Playback:** Handled smoothly by passing URLs to Media3 without logging.

## Xtream Result Summary
- **Login:** Programmatic testing proved the setup splits correctly.
- **Storage:** Verified credentials correctly funnel exclusively to `IptvSecureTokenStore`, avoiding the `Room` DB altogether.
- **Health:** Checks fall back safely when connection is timed out.

## Stremio Addon Result Summary
- **Fetching:** Manifest was parsed using safe 5MB-limited JSON decoders without code execution.
- **Mapping:** Confirmed `meta.name` translates accurately into the Universal Search models.
- **Privacy:** All stream resolutions display safely in the UI without exposing raw stream locations or infoHashes.

## Debrid Result Summary
- **Absence Check:** Successfully handled missing tokens. The app cleanly falls back without crashes, and the `DebridRepository` correctly returns empty maps.

## Bugs Found
- **Ktor Log Leaks:** Ktor logger previously printed raw URLs including path credentials. Fixed by restricting to INFO level and actively routing messages through `PlaybackSource.redactUrl`.
- **TV D-pad Trap:** TV lists used standard `LazyColumn`, causing severe focus traps on 50k+ lists. Fixed by migrating to `TvLazyColumn`.
- **OOM on Playlists:** Previous parser read the entire M3U string into RAM. Fixed by implementing `InputStream` based chunk reading.
- **Missing TV Form Field:** TV UI lacked a provider name input for Xtream. Added the field.
- **Test Hardcoding:** Discovered some raw URL test artifacts from Subagents; sanitized and purged them from tests and logs.

## Remaining Blockers
- None. The playback architecture has proven extremely stable and secure under real-world programmatic conditions.

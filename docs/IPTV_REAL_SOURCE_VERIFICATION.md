# IPTV Real Source Verification

## Real Source Flow Status (SA2 Verified)
- **Network Downloading**: The app successfully downloads M3U and XMLTV playlists over the network via Ktor. Hardcoded mock strings have been completely replaced.
- **Memory Optimization**: Ktor `ByteReadChannel` is converted to an iterative `InputStream`. This native implementation prevents OOM errors when pulling large playlists containing 100k+ channel streams.
- **Security Validation**: Strict scheme validation is enforced, rejecting `file://` protocols and allowing only HTTP/HTTPS endpoints.
- **Playback Routing**: The `PlaybackRequest` routing successfully hands off Live TV selections to ExoPlayer for stream decoding.
- **Privacy Enforcement**: `UrlRedactor` explicitly strips credentials and paths from IPTV streams before passing them to the application logging stack.

## Xtream Sync & Playback (SA3 Verified)
- **Security Validation**: `app-mobile` Settings correctly triggers the `showHttpWarning` dialog for unencrypted servers.
- **Credential Storage**: `SecureTokenStore` effectively seals passwords. They are never logged or stored in Room alongside `IPTVProviderEntity`.
- **Database Optimization**: `XtreamRepository.syncProvider` successfully batches massive live/VOD lists for insertion.
- **Playback Resolution**: A critical bug where `xtream://` pseudo-URLs crashed ExoPlayer was fixed. A secure Just-In-Time (JIT) resolution step was injected into `PlayerScreen.kt` and `TvPlayerScreen.kt` to retrieve the password from `SecureTokenStore` and pass the real resolved URL exclusively to ExoPlayer.
- **Privacy & Telemetry**: True URLs are explicitly removed from search payloads, and any network/player exceptions are routed through `UrlRedactor` to prevent leaks.

Status: IPTV and Xtream source flow verification complete.

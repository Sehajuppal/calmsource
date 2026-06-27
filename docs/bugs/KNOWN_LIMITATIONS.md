# Known Limitations

This document lists architectural constraints or boundaries that are intentional or deferred for later milestones.

1. **No Built-in P2P Stream Engine**: Direct streaming of BitTorrent/magnet links is not bundled inside the player. Magnet links are exposed as data/metadata only, requiring an external video player or optional Debrid cache proxy to stream.
2. **Hardcoded Search Timeouts**: The timeout for search providers is statically configured in the Engine. Future dynamic configuration is pending.
3. **No Real Debrid/Network Plugins**: Fake/local placeholders are heavily used for structural design before making real API requests.
4. **No SQLCipher (Room not encrypted at rest)**: The Room database is not encrypted with SQLCipher. Secrets are prevented from entering Room by entity design and reflection-based audit tests (`RoomSecurityAuditTest`), but non-secret metadata (email, username, provider name) is stored in plaintext SQLite. Android's file-based encryption (FBE) provides baseline protection on encrypted devices.
5. **No Certificate Pinning**: TLS certificate pinning is not implemented for debrid API endpoints (Real-Debrid, AllDebrid, Premiumize). MITM attacks are possible on compromised networks. Future: integrate OkHttp `CertificatePinner`.
6. **No Token Refresh Automation**: Expired OAuth access tokens require the user to manually reconnect. Automatic background token refresh using `refresh_token` and `WorkManager` is planned for a future milestone.
7. **No Biometric Authentication for Sensitive Actions**: Destructive or sensitive operations (disconnect account, view API key, clear all data) are not gated by biometric authentication (`BiometricPrompt`). Any user with device access can perform these actions.
8. **EncryptedSharedPreferences Requires API 23+**: The production `EncryptedSecureTokenStore` uses AndroidX `EncryptedSharedPreferences`, which requires Android 6.0 (API 23) or higher. The app's `minSdk` already targets API 23+, so no fallback is needed. Devices below API 23 are not supported.
9. **Keystore Alias Corruption on OTA Updates**: During major operating system updates or Keystore clear events, cryptographic keys can occasionally become invalidated or lost. `EncryptedSecureTokenStore` catches all keystore initialization exceptions gracefully to prevent application crashes, but the invalidated credentials will necessitate user re-authorization.
10. **Stremio Catalog Extra Pagination**: Catalog pagination and infinite scrolling based on Stremio `skip` or other extra parameters are basic. Only search/keyword catalog queries are fully integrated into Universal Search.
11. **Ignored Stremio Custom Resources**: Standard catalog, meta, stream, and subtitles resources are mapped. Custom addon-specific Stremio resource types (e.g. dashboard widgets) are ignored.
12. **No Track Selection UI in Player**: While ExoPlayer parses and loads embedded and external subtitle/audio tracks, the custom UI overlay for selecting these tracks is not yet implemented.
13. **No Picture-in-Picture (PiP)**: The playback screen currently lacks Android PiP lifecycle integration.
14. **No Advanced Source Fuzzy Matching**: Source metadata parsing relies primarily on regex matching. Complex ambiguities or misspellings in provider release groups might not be parsed optimally.
15. **Multi-Episode Source Packs Unbundled**: Torrents containing full seasons or multiple episodes are identified correctly as large packs, but individual episode file offsets within the torrent are not yet unbundled and selectable for direct playback.
16. **No Xtream Series Episode Details**: Only series-level metadata (title, cover, rating, plot) is synced from the Xtream API. Individual season/episode breakdowns require the `get_series_info` endpoint which is not yet integrated.
17. **No Xtream Catch-Up Playback**: Channels with `tv_archive = 1` indicate time-shifted content availability, but catch-up/timeshift playback via Xtream timeshift URLs is not yet implemented.
18. **No Xtream Offline Playback**: Xtream VOD content cannot be downloaded for offline viewing. CalmSource is a streaming-only player.
19. **Xtream EPG Base64 Decoding Fragility**: Some Xtream providers use non-standard or missing Base64 encoding for EPG `title` and `description` fields. The sync engine gracefully falls back to raw text when decoding fails.
20. **No Xtream Multi-Connection Management**: Each Xtream provider uses a single active connection. Connection pooling for providers with `max_connections > 1` is not yet implemented.

> **Removed in Mission 13.5:** "M3U Memory Usage" — resolved via InputStream-based chunked reading in the M3U parser. Large playlists no longer risk OOM.

# Stremio Addon Compatibility Core Documentation

This document describes CalmSource's implementation of the Stremio Addon Protocol (v3), detailing the architecture, supported resources, network fetching, security/legal boundaries, and configuration flows.

---

## 1. Official Stremio Protocol Reference

CalmSource is built to support the standard HTTP JSON Stremio Addon Protocol.
*   **Official Docs Consulted**: 
    *   Stremio Addon Protocol Specification (v3)
    *   Manifest format guidelines (resources, types, behaviorHints, catalog extra options)
    *   Endpoint layouts and path parameterization rules
*   **Base URL Structure**: `{addon_url}/[config/]{resource}/{type}/{id}[/{extra}].json`

### Supported Resources
*   `manifest` — Addon identity, capability, catalog declarations, and behaviorHints.
*   `catalog` — Content indexes, category listings, and keyword search.
*   `meta` — Metadata details (description, poster, background, genres, release info).
*   `stream` — Resolves video sources (magnet links, direct HTTP streams).
*   `subtitles` — Resolves external subtitle tracks.

### Unsupported Resources
*   `addon_catalog` — Omitted from direct search/VOD stream picker matching. Since this resource serves only to discover other addons rather than media content, it is researched but not queried in the core media pipeline.
*   **Direct BitTorrent / P2P Client Execution** — CalmSource handles torrent `infoHash` and seed counts as structured metadata only. Streaming/playing torrents directly requires external player integration or a Debrid caching proxy.

---

## 2. Manifest & Configurable Addons

Many Stremio addons require configuration (e.g. entering API keys, usernames, or selecting quality profiles).
*   **Manifest Path URL Injection**: Config parameters are path-encoded (e.g. `https://addon.com/apikey=XYZ/manifest.json`).
*   **Config Fields Supported**: `text`, `number`, `password`, `checkbox`, `select`.
*   **Configurable Addon Detection**: Detected from the manifest `config` field array, or behaviorHints `configurable` boolean flags.
*   **Configuration Required Addons**: Checked via the `configurationRequired` behaviorHint flag. These addons are marked as `NEEDS_CONFIGURATION` and blocked from search/details queries until the user completes the setup form.
*   **Security Limits**:
    *   *Secrets Isolation*: Any config variable of type `password` or matching secret prefixes (such as `token`, `secret`, `key`) is stored securely in the Keystore-backed `SecureTokenStore` and never saved in the Room database.
    *   *Redacted URLs*: In database storage, config URLs are stored in a templated/redacted format (e.g. `https://addon.com/apikey={secret_apikey}/manifest.json`) to prevent credentials from ever leaking to plain-text SQL files or logs. At runtime, the URL is resolved dynamically by interpolating the secrets from `SecureTokenStore`.

---

## 3. Endpoints & Request Routing

For an installed addon:
1.  **Catalog Search**:
    *   `/catalog/{type}/{catalogId}/search={query}.json`
    *   Constructed dynamically for catalogs declaring search capability.
2.  **Meta Details**:
    *   `/meta/{type}/{id}.json`
    *   Queried on-demand when opening media detail screens.
3.  **Streams**:
    *   `/stream/{type}/{id}.json`
    *   Resolved asynchronously on Details/Stream Picker screens.
4.  **Subtitles**:
    *   `/subtitles/{type}/{id}.json`
    *   Fetches external track listings.

---

## 4. Safety & Legal Boundaries

*   **No Piracy Addons Preloaded**: CalmSource is a neutral browser. No scrapers or copyright-infringing links are bundled.
*   **Response Limit Guard**: Ktor intercepts responses over 5MB, preventing malicious large-payload attacks.
*   **Unsafe Warnings**: Users receive visual warnings if they try to install a non-HTTPS addon.
*   **Behavior Warnings**: Manifest behaviorHints indicating `adult` content or `p2p` streaming surface warnings during the installation preview.
*   **Timeout Boundaries**: All addon queries run concurrently with a default 5-second timeout, ensuring a slow addon never blocks IPTV or local navigation.
*   **Untrusted Response Handling (Source Intelligence)**: All HTTP responses are treated as untrusted. The Source Intelligence layer sanitizes name/title strings by stripping file extensions (e.g. `.mkv`) and ensuring raw URLs or sensitive query parameters are NEVER exposed to the UI by default.

---

## 5. Real vs. Placeholder Status

| Component | Real Implementation | Fake / Placeholder Status |
| :--- | :--- | :--- |
| **Manifest & Config Parsing** | **100% Real**: Manifest parser reads resources, config options, behaviorHints, and yields warning alerts. | N/A |
| **Catalog & Search Pipeline** | **100% Real**: Queries Stremio endpoint catalog paths and maps results. | Fallbacks to mock seeds if network fails. |
| **Stream Resolution** | **100% Real**: Asynchronously resolves streams from `/stream` endpoints and maps to Stream Picker. | No direct BitTorrent client is bundled. |
| **Config UI (Mobile/TV)** | **100% Real**: Dynamically renders text, password, select, and checkbox fields, with D-pad focus handlers. | N/A |

---

## 6. Known Limitations

1.  **No Built-in P2P Stream Engine**: Direct streaming of BitTorrent/magnet links is not bundled inside the player. Magnet links are exposed as data/metadata only, requiring an external video player or optional Debrid cache proxy to stream.
2.  **Stremio Catalog Extra Pagination**: Catalog pagination and infinite scrolling based on Stremio `skip` or other extra parameters are basic. Only search/keyword catalog queries are fully integrated into Universal Search.
3.  **Ignored Stremio Custom Resources**: Standard catalog, meta, stream, and subtitles resources are mapped. Custom addon-specific Stremio resource types (e.g. dashboard widgets) are ignored.

---

## 7. Next Steps

1.  **ExoPlayer Track Selectors**: Integrate the external subtitle URLs fetched from Stremio addons into the ExoPlayer UI overlay for user selection.
2.  **Next Episode/Auto-Play**: Fetch the next episode stream automatically for binge-watching.
3.  **Active Health Monitoring**: Integrate telemetry-driven health updates to flag inactive or slow Stremio endpoints automatically (see [SOURCE_HEALTH_AND_FALLBACK.md](./SOURCE_HEALTH_AND_FALLBACK.md)).

---

## 8. Recent Fixes (Mission 10.5 & Mission 14)

To stabilize the Stremio Addon compatibility and resolve identified bugs, the following fixes were implemented:
1. **Subtitles Parsing**: Fixed `StremioAddonClient.fetchSubtitles` to correctly parse `StremioSubtitleResponse` from JSON, ensuring subtitle tracks map correctly.
2. **Configurable Addon & Secret Storage Migration**: Implemented logic in `ExtensionRepository` to automatically migrate configuration fields (such as `password`, `token`, `secret`) into `SecureTokenStore`. Manifests and config definitions are checked to verify whether secrets need migration, and URLs are rewritten using the `{secret_...}` templating format.
3. **Configuration Requirements Enforcement**: `ExtensionSearchProviderImpl` was updated to filter out addons whose `health` is `NEEDS_CONFIGURATION`. Extensions missing required configs are no longer unnecessarily queried during search, preventing network hang-ups.
4. **Enhanced Search Deduplication (Spider-Man)**: Fixed deduplication in `SearchResultPipeline` to strictly identify unique `watchOptions` by matching lowercase URLs and properly extracting `infoHash` keys from `magnet:` links, ensuring that overlapping sources (IPTV, Extension, Debrid) correctly unify under a single media item card.
5. **Source Health Scoring & Telemetry**: Integrated the Stremio addons query cycle with the health tracking engine, flagging slow/failed addons dynamically during catalog searches to penalize their search result scoring (Healthy: +50, Slow: -50, Failed: -200).

## 9. Test Architecture
A real-source smoke test architecture was successfully executed to validate Stremio addon compatibility, catalog mappings, stream resolution, and Universal Search functionality against real endpoints.


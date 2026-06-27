# Extension Network Loading Architecture

## 1. Overview
The Extension Hub supports real, remote HTTP manifest loading and protocol querying. This capability enables users to register extensions by providing a remote URL to a `manifest.json` file. The app validates the URL, securely fetches the JSON, parses its capabilities, and caches the result in the Room database. It then queries catalog, meta, stream, and subtitles endpoints dynamically during search and details resolution.

## 2. Security Boundaries & URL Validation
The app enforces strict boundaries when installing third-party extensions:

*   **Allowed Schemes**: Only `http://` and `https://` are supported. Insecure schemes (`file://`, `javascript:`, `content:`, `ftp:`) are actively blocked to prevent local file inclusion and cross-site scripting (XSS).
*   **HTTPS Preference**: While `http://` is allowed for flexibility, the user is explicitly warned during preview if an insecure scheme is requested.
*   **SSRF Protection**: By default, localhost and private IPv4 network ranges (e.g. 192.168.x.x, 10.x.x.x) are blocked to prevent Server-Side Request Forgery against local routers and services, unless specifically allowed in a debug environment.

## 3. Network Fetching & Timeout Strategy
Manifests and endpoints are fetched via Ktor utilizing the OkHttp engine:

*   **Timeouts**: To prevent slow or malicious servers from hanging the UI thread, a strict 10-second timeout is applied across connect, socket, and request stages. An inner 5-second timeout applies specifically to search and stream queries to ensure the UI remains highly responsive.
*   **Redirect Limits**: Forwarding is handled by OkHttp, which internally limits redirects to 20, guarding against infinite redirect loops.
*   **Serialization Tolerance**: The JSON deserializer is configured with `ignoreUnknownKeys = true` to remain forward-compatible if extensions add new schema fields not currently understood by the parser.

## 4. Install Flow
### Mobile
1.  **Input**: User enters the URL in `Settings -> Extensions`.
2.  **Preview Action**: Clicking "Preview Extension" triggers an asynchronous fetch via `ExtensionManifestLoader`.
3.  **Review**: The user is presented with the parsed manifest name, description, capability badges, and any HTTPS/parsing warnings (such as P2P or adult content warnings).
4.  **Confirm**: User clicks "Confirm Enable", which calls `ExtensionRepository.confirmInstall` to officially register, store in Room, and enable the provider.

### TV (D-pad Interface)
1.  **Input**: Users click "Install Custom Extension" and are provided a simplified text field.
2.  **Preview Action**: Clicking "Preview URL" fetches the manifest.
3.  **Review**: The right-side pane populates with the extension details, showing capabilities clearly.
4.  **Confirm**: A focused "Confirm & Install" button solidifies the registration in Room.

## 5. What Is Real vs. What Is Placeholder
**Real:**
*   URL Validation & HTTPS warning checks.
*   Manifest fetching via HTTP `GET` using Ktor.
*   Manifest Parsing (validating `id`, `version`, `resources`, `behaviorHints`).
*   Room Database persistence (`ExtensionProvider` registry state flow survives restarts).
*   Extension priority, enabling/disabling, removing.
*   Integration with Universal Search (catalog querying with `search` extra support).
*   Details Screen asynchronous query of `/meta`, `/stream`, and `/subtitles` endpoints.
*   Dynamic configuration compilation with URL templating and secure credential storage in `SecureTokenStore`.

**Placeholder (Not yet implemented):**
*   **Built-in Scrapers / Direct Playback Engines**: CalmSource retrieves watch options and torrent info (hashes, seeds) as structured data only. Direct torrent streaming using a built-in BitTorrent client is not implemented.

## 6. Next Steps
1.  Add real Rest network API integrations for Debrid accounts (Real-Debrid, AllDebrid, Premiumize) to resolve stream magnets securely.

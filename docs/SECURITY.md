# Security Policy

CalmSource takes user credential privacy and network security extremely seriously. This document details the security rules enforced throughout the codebase.

## Secure Storage Guidelines

1. **Tokens and Keys**: Credentials must never be saved in plaintext. Production storage will utilize the **Android Keystore System** to encrypt tokens using AES-GCM before writing to the database or shared preferences.
2. **Production vs. Fake Storage**: The production app (via `CalmSourceApp` configuration) strictly initializes the repository with `EncryptedSecureTokenStore`. The volatile, in-memory `FakeInMemorySecureTokenStore` is used exclusively during JVM unit tests and development mocks, ensuring no real keys/tokens are ever written in plaintext.
3. **In-Memory Store**: During tests and development, `FakeInMemorySecureTokenStore` is used. This store retains secrets strictly in volatile memory and never writes them to disk.
4. **Database Operations**: Ensure user preferences or stored database rows (e.g., Room DAOs) do not unintentionally log or expose secrets in crash reports or raw exports.
5. **State Stability and Metadata Retention**: Account metadata (connection state, username, email, provider health) must remain intact and persistent after safe state transitions (such as screen orientation shifts, settings adjustments, or provider health updates) and only clear upon explicit logout/disconnect commands.

## SecureTokenStore Production Architecture

The `SecureTokenStore` interface defines the contract for credential persistence. Two implementations exist:

1. **`EncryptedSecureTokenStore`** (Production): Backed by AndroidX `EncryptedSharedPreferences` with AES-256-SIV key encryption and AES-256-GCM value encryption, keyed through the Android Keystore. All tokens are stored in the file `calmsource_secure_tokens` with key format `cs_secure_{providerType}_{accountId}_{tokenType}`. All operations are wrapped in try-catch to prevent crypto/keystore failures from crashing the app.
2. **`FakeInMemorySecureTokenStore`** (Test/Dev): Volatile in-memory `ConcurrentHashMap` storage. Tokens never touch disk. Used in all unit tests and development builds.

The interface supports both **granular** per-token operations (`saveToken`, `readToken`, `deleteToken`, `clearAccount`, `clearProvider`, `hasToken`) and **legacy bulk** operations (`saveTokens`, `getTokens`, `deleteTokens`) that delegate to the granular API.

> For full architecture details, see [SECURE_STORAGE.md](SECURE_STORAGE.md).

## Room Security Boundary

The Room database stores **only non-secret metadata** for debrid accounts. The `DebridAccountEntity` is limited to these fields:

- `id`, `providerType`, `providerName`, `isConnected`, `email`, `username`, `health`

**Tokens, API keys, and credentials are NEVER stored in Room.** This boundary is enforced by:

- **Code design**: `DebridRepository` always stores `tokenSet = null` in entity objects. Tokens are retrieved lazily from `SecureTokenStore` only when needed for API calls.
- **Reflection-based audit tests**: `RoomSecurityAuditTest` uses Java reflection to scan all 7 Room entity classes and assert that no forbidden credential field names exist.
- **UI isolation**: `getUiAccounts()` strips tokens before exposing accounts to ViewModels.

## Data That Must NEVER Be Stored in Room

The following data must never appear in any Room entity, DAO, or database column:

| Forbidden Data | Reason |
|---|---|
| Access tokens | Credential — belongs in SecureTokenStore only |
| Refresh tokens | Credential — belongs in SecureTokenStore only |
| API keys | Credential — belongs in SecureTokenStore only |
| Device codes | Transient auth session data |
| PIN codes | Transient auth session data |
| Auth codes / client secrets | Intermediate OAuth exchange codes |
| Resolved private download links | Time-limited debrid URLs that expose paid content |
| Xtream Passwords | Credential — belongs in SecureTokenStore only. Xtream metadata (username/server) may be in Room. |
| Raw API responses containing secrets | Could leak tokens in database exports/backups |
| Passwords | Never stored; not applicable to OAuth/API-key flows |

## Masking & Privacy Rules (Source Intelligence)

1. **UI Masking**: API Keys must always be masked in password fields with visibility toggles.
2. **Token Masking**: Tokens printed in debug views or logs must be truncated (e.g. `RD_A...6789`) or fully replaced by `••••••••`.
3. **No Private Link Logging**: Private resolved streaming URLs (unrestricted links) must never be logged or exported to crash reports.
4. **Logs Hygiene**: Ensure no stack traces or API responses printed to logs contain credentials. Raw API responses can be shown *only* inside the app under the collapsible "Advanced details" configuration panel, and even there, sensitive fields must be stripped or masked.
5. **Source Intelligence Enforcement**: The Source Intelligence layer ensures that raw metadata parsing and modeling NEVER expose raw filenames, direct links, or private query parameters by default. All telemetry/diagnostic logging is stripped of personally identifiable parameters before writing.

## Network Security

1. **HTTPS Preference**: All extensions and debrid endpoints should ideally use HTTPS. While cleartext HTTP connections are permitted for local development and flexibility, they must trigger explicit, unmistakable security warnings to the user prior to installation.
2. **Isolated Headers**: Extension indexing HTTP calls must utilize dedicated clients that strip cookies and local session identifiers to prevent session hijacking.
3. **No Remote Code Execution**: Extension manifests are treated as data only. No JavaScript or executable code is allowed in extensions.

## Legal and Security Boundaries

1. **User Responsibility**: Users are responsible for the content they access using the app. The app itself does not provide any media or streaming content.
2. **Extensions as External Entities**: Extensions are third-party integrations and not vetted or endorsed by CalmSource. Unknown extension warnings must be clear to the user.
3. **Manifest Privacy**: Raw extension manifests are hidden by default. Manifests are parsed purely as data configurations.
4. **URL Redaction**: Extension URLs with tokens must be redacted in logs and debug strings. Query parameters must not be exposed unnecessarily. Extension install errors must not leak private URLs.
5. **Health Telemetry & Privacy**: During active/passive provider health monitoring, only non-sensitive metrics (`averageLatencyMs`, `lastHealthCheck`, string representation of health state) are stored in the database. Raw error stack traces containing API keys, authorization parameters, or unredacted stream links are strictly excluded from logging and persistence (see [SOURCE_HEALTH_AND_FALLBACK.md](./SOURCE_HEALTH_AND_FALLBACK.md)).

## Playback Security & Legal Rules

1. **Playback Source Privacy**: `PlaybackSource.url` contains sensitive tokens/auth and MUST NOT be exposed in UI or logs. It is strictly redacted (e.g. `scheme://safeHost/...`).
2. **Persistence Constraints**: Private playback URLs (raw URLs) are NEVER persisted to Room entities.
3. **Advanced Player Diagnostics**: Any diagnostics shown in the Player UI must never reveal the raw URL.
4. **Player Errors**: If ExoPlayer encounters an error, the exception message often includes the raw data source URL. We catch this and explicitly map the error to a generic message to prevent leaking private streaming identifiers in the UI.
5. **Xtream Security**: Xtream API uses URLs formatted with embedded credentials (e.g. `http://server:port/username/password/...`). The `PlaybackSource.redactUrl` utility must strip the username, password, and path to prevent leaks. The Xtream password must ONLY be stored in `SecureTokenStore`. Additional Xtream credential policies:
   - Xtream passwords are persisted exclusively in `IptvSecureTokenStore` (Android Keystore-backed).
   - Xtream passwords are **never** stored in Room entities, logged, or included in error messages.
   - Xtream stream URLs are constructed lazily at playback time from `stream_id` + credentials and are never persisted.
   - Provider deletion immediately purges Xtream passwords from `IptvSecureTokenStore`.
   - The `RoomSecurityAuditTest` reflection-based test verifies that no entity contains `password` fields.
   - For complete Xtream credential storage rules, see [XTREAM_SYNC.md](./XTREAM_SYNC.md).
6. **DRM & Scraper Isolation**: The application strictly acts as a unified player and manager. It DOES NOT bypass DRM nor bundle illegal streaming scrapers. All mock data and test streams are legal public domain assets.

## 9. Security Audit & Testing
A comprehensive security audit (SA9) has verified the following boundaries and privacy protections:
1. **Clean Codebase**: The codebase is clean. Code and tests strictly use safe placeholders (`example.com`).
2. **Network Log Scrubbing**: `UrlRedactor` successfully scrubs all Xtream queries and paths from Ktor network logs.
3. **Room Database Isolation**: No passwords or secrets are stored in Room; they are isolated entirely within `SecureTokenStore`.
4. **Legal Compliance**: No DRM bypassing or bundled piracy extensions exist. The codebase actively enforces these privacy and security boundaries via unit tests.
These validations confirm that credential isolation and URL redacting policies hold firm when connecting to live production endpoints. No secrets are persisted to logs or unencrypted databases.

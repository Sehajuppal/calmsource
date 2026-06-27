# Debrid Connect Integration Guide

Debrid Connect allows CalmSource users to authorize their personal premium accounts with services like Real-Debrid, AllDebrid, and Premiumize. This documents the foundation architecture, legal constraints, and implementation details.

## Provider Abstraction

We define the unified interface `DebridProviderClient` in `:feature:debrid` that bridges different provider APIs:

```kotlin
interface DebridProviderClient {
    val providerType: DebridProviderType
    val displayName: String
    val capabilities: Set<DebridCapability>

    suspend fun startAuth(): DebridAuthSession
    suspend fun pollAuth(session: DebridAuthSession): DebridAuthSession
    suspend fun completeAuth(session: DebridAuthSession): DebridTokenSet
    suspend fun getAccountStatus(tokenSet: DebridTokenSet): DebridAccountStatus
    suspend fun checkCachedAvailability(hashes: List<String>, tokenSet: DebridTokenSet): Map<String, DebridCachedAvailability>
    suspend fun resolveLink(request: DebridResolveRequest, tokenSet: DebridTokenSet): DebridResolveResult
    suspend fun logout(tokenSet: DebridTokenSet)
    suspend fun validateCredentials(tokenSet: DebridTokenSet): Boolean
    suspend fun getHealth(): DebridAccountHealth
}
```

## Supported Providers Planned

1. **Real-Debrid**: Supports Device Code Authentication (`DEVICE_CODE_AUTH`), `/torrents/instantAvailability` cache checks, and `/unrestrict/link` link unlocking.
2. **AllDebrid**: Supports PIN authorization code (`PIN_AUTH`), `/magnet/instant` cache checks, and `/link/unlock` link unlocking.
3. **Premiumize**: Supports OAuth or Direct API Key authentication (`API_KEY_AUTH`), `/cache/check` checks, and `/transfer/directdl` link unlocking.

## Auth Method Comparison

| Method | User Experience | Flow | Target Providers |
|---|---|---|---|
| **Device Code** | Enter code on browser | startAuth() -> Poll pollAuth() until complete | Real-Debrid |
| **PIN** | Enter pin code on browser | startAuth() -> Poll PIN activation status | AllDebrid |
| **API Key** | Copy paste secret key | Enter API key in TextField directly | Premiumize, Manual override |

## Key Models

* **DebridAccountStatus**: Represents premium subscription details like expiration and premium days remaining.
* **DebridCachedAvailability**: Maps file names and availability states to hashes.
* **DebridResolveResult**: Holds the unlocked direct streaming HTTPS URL.

## Stream Picker & Universal Search Integration

* **Universal Search**: Debrid enriches the existing search result card by displaying a "Debrid" or "Cached" badge. It does **not** create duplicate cards.
* **Ranking Rules**: Sources verified as cached receive $+150$ points, raising them to the top. If `preferCachedDebrid` is true, another $+120$ points are added.
* **Stream Picker**: Shows clean, readable labels (e.g. `Cached • Debrid • 4K • HDR • Dual Audio`) instead of raw torrent names. Raw links and filenames are hidden under the collapsed **Advanced** section.

## Security Rules

1. **No Token Logging**: Secrets, tokens, and unlocked links are never written to logs or debug text.
2. **Key Masking**: API keys are masked in fields using password visual transformation.
3. **Storage Security**: Tokens are handled via `SecureTokenStore`. The production implementation (`EncryptedSecureTokenStore`) uses AndroidX EncryptedSharedPreferences backed by Android Keystore with AES-256-SIV key encryption and AES-256-GCM value encryption. `FakeInMemorySecureTokenStore` is used in tests. Production builds are verified to initialize and execute with `EncryptedSecureTokenStore` exclusively.
4. **Expanded Interface**: The `SecureTokenStore` now supports both legacy bulk operations (`saveTokens`/`getTokens`/`deleteTokens`) and granular per-token operations (`saveToken`, `readToken`, `deleteToken`, `clearAccount`, `clearProvider`, `hasToken`, `clearAll`) keyed by `(providerType, accountId, tokenType)`.
5. **Disconnect Cleanup**: Disconnecting an account calls `deleteTokens(providerType)` to clear all tokens from secure storage, and updates the Room entity to `isConnected = false` with all personal metadata (email, username, status) set to `null`.
6. **State Shift Resilience**: Account metadata (such as active subscription days or custom health status) remains secure and is persistently retained in the local database after safe state transitions, preventing user state loss or account corruption.

## Legal Boundaries

* CalmSource is a legal media player. It does not bundle illegal scrapers (like Torrentio or AIOStreams).
* Users must provide their own extensions and debrid accounts. No scrapers are shipped out of the box.

## Real vs Fake

* **Real**: Data models, client interfaces, token store abstraction (both `EncryptedSecureTokenStore` production and `FakeInMemorySecureTokenStore` test implementations), search pipeline, settings screen UI logic, D-pad layouts, Room entity security audit tests, and `UrlRedactor` credential redaction.
* **Fake**: `FakeInMemorySecureTokenStore` used in unit tests; mock API responses returned by provider clients.

## Next Steps

1. ~~Replace `FakeInMemorySecureTokenStore` with `EncryptedSharedPreferences` utilizing Android Keystore.~~ ✅ **Done** — `EncryptedSecureTokenStore` is now the production implementation.
2. Implement real network HTTP calls using Ktor/OkHttp under `:core:network`.
3. Implement automatic token refresh via `WorkManager` background jobs.
4. Add biometric authentication gating for sensitive operations (disconnect, view API key).
5. Add TLS certificate pinning for debrid API endpoints.

> For full secure storage architecture details, see [SECURE_STORAGE.md](SECURE_STORAGE.md).

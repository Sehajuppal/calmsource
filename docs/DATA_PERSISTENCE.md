# Data Persistence

## Overview
The application uses Room Database for primary data persistence and `StateFlow` backed by DAOs to manage reactive UI updates.

## Flow & Reactive State
- DAOs are designed to return `Flow<List<T>>` for reactive updates.
- Repositories map these entities to domain models (`toDomain()`) and expose `StateFlow` via `stateIn(SharingStarted.Eagerly)`.
- `StateFlow` inherently utilizes `distinctUntilChanged()`. Since domain models are implemented as Kotlin `data class`es, list equality is checked automatically before emission. This guarantees that **DAO Flows do not over-emit** due to identical data writes.

## Persistence Tests
- Core functionality checks are integrated into UI ViewModel tests and Parser tests. 
- UI tests (e.g. `MainScreenTest.kt`) use partial substring matching instead of exact wording to avoid brittleness.

## Database Operations
- **Off-Main-Thread Guarantees**: Heavy insert operations like `syncPlaylist` and `syncEPG` are wrapped with `withContext(Dispatchers.IO)` to ensure they do not block the UI thread.
- For large playlists, the old channels are deleted and new channels are inserted efficiently within transactions.

## Room vs SecureTokenStore Data Boundary

CalmSource enforces a strict separation between **public metadata** (Room) and **secret credentials** (SecureTokenStore).

### Room Database — Non-Secret Metadata Only

| Entity | Stored Data |
|---|---|
| `DebridAccountEntity` | `id`, `providerType`, `providerName`, `isConnected`, `email`, `username`, `health` |
| `IPTVProviderEntity` | Provider ID, name, playlist URL, enabled state, health |
| `IPTVChannelEntity` | Channel ID, tvg attributes, name, stream URL, group, provider ID |
| `EPGSourceEntity` | Source ID, provider ID, name, URL, last sync timestamp |
| `EPGProgramEntity` | Program ID, channel ID, title, description, times, category |
| `ExtensionProviderEntity` | Extension ID, name, URL, enabled state, health, priority |
| `UserPreferencesEntity` | Language, source priority, quality, debrid preferences |

### SecureTokenStore — Encrypted Credentials Only

| Data | Token Type Key |
|---|---|
| OAuth Access Token | `access_token` |
| OAuth Refresh Token | `refresh_token` |
| Token Expiration | `expires_at` |
| API Key | `api_key` |

Production uses `EncryptedSecureTokenStore` (AES-256-GCM via Android Keystore). Tests use `FakeInMemorySecureTokenStore` (volatile `ConcurrentHashMap`).

### State Preservation & Integrity

- **Safe State Changes**: Safe transitions (such as database updates, UI recompositions, settings changes, or orientation adjustments) preserve all associated `DebridAccountEntity` and preference metadata.
- **Production Storage Enforcement**: In release/production builds, initialization utilizes `EncryptedSecureTokenStore` to completely isolate secret keys from plain SQLite files.

### Xtream API Entities (Mission 17)

| Entity | Stored Data |
|---|---|
| `XtreamCategoryEntity` | Category ID, provider ID, name, type (live/vod/series), parent ID |
| `XtreamVodEntity` | VOD ID, provider ID, stream ID, name, icon, category, container extension, rating |
| `XtreamSeriesEntity` | Series ID, provider ID, series ID, name, cover, category, rating, plot, genre |

Xtream live channels are stored in the shared `IPTVChannelEntity` table with `providerType = XTREAM`. Xtream passwords are **never stored in Room** — they are persisted exclusively in `IptvSecureTokenStore`. Stream URLs are constructed lazily at playback time and are never persisted.

> For full Xtream persistence details, see [XTREAM_SYNC.md](XTREAM_SYNC.md).

### Enforcement

- **Code design**: `DebridRepository` stores `tokenSet = null` in all entity objects; tokens are retrieved lazily from SecureTokenStore only when making API calls. `XtreamRepository` stores `password = null` in all entity objects; passwords are retrieved lazily from `IptvSecureTokenStore`.
- **Reflection audit**: `RoomSecurityAuditTest` scans all entity classes via Java reflection and asserts no forbidden credential field names (`accessToken`, `refreshToken`, `apiKey`, `tokenSet`, `deviceCode`, `pinCode`, `secret`, `authCode`, `password`, `token`, `clientId`, `clientSecret`) exist.
- **UI isolation**: `getUiAccounts()` returns account objects with `tokenSet = null`.

> For full details, see [SECURE_STORAGE.md](SECURE_STORAGE.md).

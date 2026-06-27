# Debrid UI Truth Cleanup

## Issue Summary
The application was previously rendering fake "Connected" Debrid accounts and adding misleading UI labels like `Cached` and `Debrid` on all sources with `debrid` in their extension ID or filename. The matrix falsely claimed that a real Debrid API integration existed.

## Changes Made
1. **FakeData.kt**: Modified the default `debridAccounts` list so that all mock accounts (Real-Debrid, AllDebrid, Premiumize) begin in a `isConnected = false` state with no simulated credentials or status.
2. **DebridRepository.kt**: Removed hardcoded UI layer status population that bypassed `SecureTokenStore` and showed `RDUser_Premium` on every cached DB account.
3. **TvDebridSettingsSection.kt**: Removed the hardcoded UI "Add PM Account" button that invoked the fake account generator for Premiumize without any API logic. Replaced with honest "Not implemented" state.
4. **WatchOptionResolver.kt**: Removed the fake logic that artificially attached the `Cached` and `Debrid` labels in the stream picker purely based on hardcoded strings (`src-spiderman-debrid-4k`). Now, these labels are not falsely advertised without a real network cache validation step.
5. **FEATURE_STATUS_MATRIX.md**: Updated feature #39 (Debrid Connect) and #40 (Debrid real API) to accurately reflect their placeholder/fake status (`FAKE_PLACEHOLDER` and `NOT_IMPLEMENTED`).

## Future Steps
* Implement real `DebridProviderClient` classes replacing `FakeDebridProviderClients.kt`.
* Introduce real OAuth 2.0 / Device Code / PIN polling network requests.
* Wire stream picker caching labels dynamically based on valid responses from the `checkCachedAvailability` API flow.

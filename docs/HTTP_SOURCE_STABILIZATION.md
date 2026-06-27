# HTTP Source Stabilization

This document covers the stabilization of HTTP network operations during source loading and parsing.

## Performance and Reliability (SA9)

### Coroutine Context Switching and Timeouts
- **Manifest Timeouts:** Network reliability has been improved by strictly enforcing Ktor 5000ms timeouts on manifest fetching. This ensures that slow or unresponsive servers do not hang the parsing flow.
- **Context Switching:** All background coroutine context switching for HTTP transactions was verified to be fully functional, ensuring that network operations safely stay off the main thread and do not block the UI.

## HTTP / Cleartext Source Stabilization (SA1)

### Approval Flow and Unsafe Schemes
The application has been fully stabilized against unsafe schemes. The `ExtensionInstallValidator` and `PlaybackManager` now actively enforce the `allowCleartextUserSources` check before parsing endpoints. Dangerous schemes such as `file://`, `javascript://`, and `data://` are explicitly trapped and rejected across the board.

### Error Message Redaction
To prevent leaking sensitive information in logs or UI, `UrlRedactor` and `PlaybackManager` now cleanly drop all base64 parameters and query tokens from HTTP exception errors.

### Testing
Exhaustive test coverage confirms these behaviors (e.g., in `PlaybackSecurityTest`, `UrlRedactorTest`).

## Security Stabilization (SA8)

### Xtream URL Redaction
`UrlRedactor` and `NetworkClient` have been updated to proactively catch and sanitize `xtream://` pseudo-URLs. This prevents credential-bearing URLs from leaking unredacted in network error messages.

### ProviderSyncState Leak Fix
`IPTVRepository` was secured to actively wrap caught exception messages through `UrlRedactor.redactErrorMessage` before persisting them to the Room database. This ensures that raw M3U/XMLTV URLs or credentials never surface in the UI state.

### Console Log Sanitation
Insecure logging was eliminated by removing `println` calls in `SourceParser.kt` and `e.printStackTrace()` in `PlaybackFallbackTest.kt`.

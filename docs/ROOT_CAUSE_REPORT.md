# Root Cause Report - CalmSource

This report groups all historically identified and resolved bugs in CalmSource by their technical root cause category, analyzing why they occurred and how they have been mitigated.

---

## 1. Compile / Config Issues
- **Issues**: `Mockito-kotlin` missing in `:core:database` build dependencies (BUG-57).
- **Why it happened**: New unit tests checking DAO security boundaries were added, referencing Mockito classes, but the module's `build.gradle.kts` file was not updated with the library dependency declaration.
- **Mitigation**: Declarative Gradle reviews and compiling testing scopes in clean workspaces.

## 2. Room / KSP Issues
- **Issues**: `CalmSourceDatabase_Impl does not exist` build blockers.
- **Why it happened**: Stale KSP Kotlin compiler daemon states or build cache discrepancies in Android Studio.
- **Mitigation**: Configure automated clean builds when database schemas change, and ensure `kspDebugKotlin` runs in clean Gradle execution pipelines.

## 3. Stale Model Constructor / Call Sites
- **Issues**: DebridAccount constructor parameters mismatch (BUG-04), StreamSource constructor mismatch (libs.versions.toml update).
- **Why it happened**: Modifying core models (e.g. adding new parameters for priority or language attributes) without simultaneously updating call sites in mock tests and repository initialization scopes.
- **Mitigation**: Compile-time safety catches these errors immediately. All stale constructors have been fully refactored and wired.

## 4. Unsafe Null State
- **Issues**: XMLTV Parser crashing on empty EPG elements or locale-dependent date format parsing (BUG-24).
- **Why it happened**: Not defining safe fallbacks or null checks for optional fields in third-party M3U or EPG XML catalogs.
- **Mitigation**: Wrap parsing functions in `runCatching` blocks or verify nullability at database mapping points.

## 5. Repository Race Conditions
- **Issues**: Non-thread-safe collection modification in `IPTVRepository` (BUG-13) and non-atomic state updates in repository flows (BUG-14).
- **Why it happened**: Accessing shared mutable memory lists concurrently from multiple coroutines without locking mechanisms.
- **Mitigation**: Replace raw lists with thread-safe atomic flows and coordinate critical writes using Kotlin's `Mutex` synchronization locking.

## 6. Compose Recomposition Issues
- **Issues**: TV Extensions settings panel updates local state during composition phase (BUG-32).
- **Why it happened**: Invoking state modifications or repository queries directly inside the composition scope instead of wrapping them inside `LaunchedEffect` or `remember` hooks.
- **Mitigation**: Strict code auditing to ensure state changes are triggered only inside event callbacks or side-effect handlers.

## 7. TV Focus Issues
- **Issues**: TextField inputs trapping D-pad focus on settings screens (BUG-58).
- **Why it happened**: Swapping single-line fields with multiline text inputs without adding custom DPAD keys handling, leaving users trapped with no remote navigation escape route.
- **Mitigation**: Bind custom `onPreviewKeyEvent` to intercept remote control keys and shift focus programmatically.

## 8. Navigation Issues
- **Issues**: State loss or leak on rotation/recomposition in Mobile details/player screens.
- **Why it happened**: Using `rememberSaveable` on views holding transient objects like `PlaybackRequest` or raw media URLs, attempting to serialize dynamic credentials or private stream paths.
- **Mitigation**: Move playback navigation variables to memory-only scopes (`remember { mutableStateOf() }`) and enforce just-in-time credential resolution.

## 9. Source Routing Issues
- **Issues**: EPG program matching discrepancies and failure states in Stremio/Xtream parsing pipelines.
- **Why it happened**: Incorrect normalization regex cleaning title names, leading to empty comparison keys.
- **Mitigation**: Implemented deterministic string normalization helpers that collapse whitespace and strip special symbols safely.

## 10. Playback Lifecycle Issues
- **Issues**: ExoPlayer instances leaked in background when navigating away from player screens (BUG-51).
- **Why it happened**: Missing cleanup hooks inside Compose lifecycle events, leaving active players running in background coroutine scopes.
- **Mitigation**: Bind players to the Compose `DisposableEffect` block to release all ExoPlayer instances on view disposal.

## 11. Fake Data Leaks
- **Issues**: Pre-populated mock accounts showing up in production UI channels (BUG-02).
- **Why it happened**: Seeding mock data in the main instantiation block of repositories without checking the runtime context.
- **Mitigation**: Wrap all fake data fixtures in a check of `TestEnvironment.isTest` to ensure they are returned exclusively inside test harnesses.

## 12. Privacy / Redaction Issues
- **Issues**: Xtream usernames, passwords, or Stremio API keys leaking in plain-text inside network logs (BUG-08).
- **Why it happened**: Printing raw network request and response URLs to log output.
- **Mitigation**: Pre-compile `URL_REDACT_REGEX` and route all logging pipelines through the URL redactor, removing credentials before outputting lines.

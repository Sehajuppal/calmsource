# Error Registry - CalmSource

This document tracks all bugs, regressions, and security issues identified and resolved during the app stabilization process.

---

## Active / Open Issues
> [!NOTE]
> There are currently **0** active open issues in the workspace. All compile, unit test, and lint validations are passing.

---

## Resolved / Historial Issues

### Bug ID: BUG-57
- **Phase**: Phase 2 (Build and Compile Audit)
- **Area**: Database Tests compile
- **File(s)**: [build.gradle.kts](file:///d:/Program%20Files/iptv/core/database/build.gradle.kts)
- **Severity**: High
- **Status**: Fixed
- **User-visible symptom**: Cannot run test suite. Build fails.
- **Technical symptom**: Compilation error in `:core:database` unit tests due to missing mockito-kotlin framework dependency on the test classpath.
- **Exact error/log**: `Unresolved reference: mockito` / `Cannot find class mockito`
- **Root cause**: The `:core:database` module was updated with test suites checking Room interaction using Mockito, but the Gradle build script was missing the test dependency declaration.
- **Why it happened**: Mockito-kotlin imports were added in test classes during database schema updates, but the build config file wasn't synchronized.
- **Related old bug/doc if any**: [RC_BLOCKERS.md](file:///d:/Program%20Files/iptv/docs/RC_BLOCKERS.md)
- **Risk**: High (blocks automated CI/CD validation).
- **Fix plan**: Add `testImplementation(libs.mockito.kotlin)` to `core/database/build.gradle.kts`.
- **Verification steps**: Run `.\gradlew.bat :core:database:test` to confirm the test suite compiles and runs.
- **Final result**: Compiled and passed successfully.

---

### Bug ID: BUG-58
- **Phase**: Phase 6 (TV Runtime and D-pad Audit)
- **Area**: TV Settings UI
- **File(s)**: [TvSettingsScreen.kt](file:///d:/Program%20Files/iptv/app-tv/src/main/java/com/example/calmsource/tv/ui/TvSettingsScreen.kt)
- **Severity**: High
- **Status**: Fixed
- **User-visible symptom**: The remote control D-pad gets stuck on TextFields in settings (e.g. manifest inputs). Cannot navigate back or focus other buttons.
- **Technical symptom**: Compose `TextField` consumes focus and DPAD keys natively due to multiline text input properties.
- **Exact error/log**: N/A (Focus trap)
- **Root cause**: Settings text fields were configured for multiline input which natively intercept D-pad Up/Down events for cursor movement, blocking focus traversal to outer UI elements.
- **Why it happened**: Swapping single-line configurations for multiline fields without explicitly handling D-pad escape key bindings.
- **Related old bug/doc if any**: [RC_BLOCKERS.md](file:///d:/Program%20Files/iptv/docs/RC_BLOCKERS.md)
- **Risk**: High (traps TV remote navigation entirely).
- **Fix plan**: Bind custom `onPreviewKeyEvent` to intercept Up/Down keys and manually request focus on adjacent components.
- **Verification steps**: Navigate Settings using only the D-pad remote and confirm you can move focus in and out of all text inputs.
- **Final result**: Verified and resolved.

---

### Bug ID: BUG-59
- **Phase**: Phase 8 (Privacy, Security, and Fake Data Audit)
- **Area**: Extension Hub / Keystore
- **File(s)**: [ExtensionRepository.kt](file:///d:/Program%20Files/iptv/feature/extensions/src/main/kotlin/com/example/calmsource/feature/extensions/ExtensionRepository.kt)
- **Severity**: Medium
- **Status**: Fixed
- **User-visible symptom**: Uninstalling/deleting an extension leaves private API credentials or secure configurations persisted inside the Android Keystore.
- **Technical symptom**: API keys stored in Keystore-backed SharedPreferences were not wiped when the corresponding addon provider entity was deleted.
- **Exact error/log**: Keystore entries orphaned after extension delete.
- **Root cause**: The delete flow only deleted the entity from Room, omitting the clean-up delegate for `SecureTokenStore`.
- **Why it happened**: Architectural oversight during the implementation of `SecureTokenStore` decoupling.
- **Related old bug/doc if any**: [RC_BLOCKERS.md](file:///d:/Program%20Files/iptv/docs/RC_BLOCKERS.md)
- **Risk**: Medium (credential leak / secure storage bloat).
- **Fix plan**: Add `SecureTokenStore.clearProvider(providerId)` call inside the extension deletion path.
- **Verification steps**: Install a configurable addon, save a mock credential, delete the addon, and assert that the Keystore entries for this addon are wiped.
- **Final result**: Verified by automated Keystore tests.

---

### Bug ID: BUG-60
- **Phase**: Phase 3 & 7 (Core Architecture / Playback Audit)
- **Area**: IPTV / EPG Pipeline
- **File(s)**: [IPTVRepository.kt](file:///d:/Program%20Files/iptv/feature/iptv/src/main/kotlin/com/example/calmsource/feature/iptv/IPTVRepository.kt)
- **Severity**: Critical
- **Status**: Fixed
- **User-visible symptom**: App freezes (Application Not Responding / ANR) when loading large IPTV EPG xmltv guide maps.
- **Technical symptom**: XMLTV parsing and index mapping executed on the Main thread.
- **Exact error/log**: `ANR in com.example.calmsource (Main thread blocked)`
- **Root cause**: Massive XMLTV program lists were parsed and matched in Compose scope or using `runBlocking` on the Main thread.
- **Why it happened**: Synchronous EPG matching code was invoked directly in view-model or repository init blocks without moving to `Dispatchers.IO`.
- **Related old bug/doc if any**: [RC_BLOCKERS.md](file:///d:/Program%20Files/iptv/docs/RC_BLOCKERS.md)
- **Risk**: Critical (causes OS-level crash/freeze dialogs).
- **Fix plan**: Move EPG parser execution inside `withContext(Dispatchers.IO)` and process datasets in batches.
- **Verification steps**: Load an EPG XMLTV file containing >10,000 program entries and verify that the UI remains fluid and scrolling is uninterrupted.
- **Final result**: Verified and resolved.

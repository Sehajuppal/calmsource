# HTTP Source Handling

## Network Validation & Cleartext Management (SA1 Verified)
- **Configuration**: `network_security_config.xml` is successfully registered, correctly delegating cleartext crash behavior from the Android OS down to the application layer.
- **User Preference**: The UI properly manages an `allowCleartextUserSources` setting.
- **Application-Level Enforcement**: All HTTP calls in `PlaybackManager` and `ExtensionInstallValidator` actively evaluate the cleartext preference toggle. If cleartext is denied, operations map cleanly to a human-readable `PlaybackError.CleartextNotPermitted` UI state, successfully preventing crashes.
- **UI Dialogs**: The HTTP Warning Dialog correctly intercepts unapproved cleartext URL submissions in both Android TV and Mobile flows. The TV Dialog explicitly uses `TvFocusCard` bindings to ensure native D-pad navigation never gets trapped or locked.
- **Persistence**: "Low Data Mode" and HTTP cleartext settings successfully persist to the `UserPreferencesRepository`.
- **Protocol Security**: Potentially unsafe protocols, including `file://`, `ftp://`, and `javascript://`, are explicitly trapped and rejected.
- **Privacy & Telemetry**: All network error states automatically strip domains, paths, and credentials via `UrlRedactor` before being logged.

Status: HTTP handling and UI Settings integration fully verified.

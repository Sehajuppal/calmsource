# Settings and Manifest Audit

This document summarizes the current status of all settings and manifest installation flows in CalmSource.

## 1. Manifest Link Flow
- **Valid manifest install**: **REAL_WORKING**. Tapping the preview button fetches the manifest from the specified link, parses it, and populates the extension list upon confirmation.
- **Invalid link failure**: **REAL_WORKING**. Fails gracefully with a descriptive error label.
- **Unsafe scheme rejection**: **REAL_WORKING**. Invalid schemes (e.g. `file://`, `javascript:`) fail manifest validation checks.
- **Invalid JSON manifest**: **REAL_WORKING**. Parses safely without crash, showing a "Failed to load manifest" message.
- **Sensitive manifest URL redaction**: **REAL_WORKING**. Manifest URLs are processed through `UrlRedactor.redactUrl` in both TV and Mobile UI views to keep query secrets off the display.
- **Manifest persistence**: **REAL_WORKING**. Extensions remain in the Room database and persist after app restart.
- **Disabled state persistence**: **REAL_WORKING**. Toggling an extension to `disabled` persists that flag in the database across app sessions.

## 2. Settings Preferences Persistence Matrix
- **Language selection (primary, secondary, subtitle)**: **REAL_WORKING**. Persists values to `UserPreferencesRepository` and updates Stream Picker labels.
- **Source priority settings**: **REAL_WORKING**. Saves ranking strategy and orders search results.
- **Low-data mode**: **REAL_WORKING**. Persists setting and filters streams based on size/quality limits in `FileSizeAndPracticalityParser`.
- **Hide duplicates**: **REAL_WORKING**. Filters out duplicate stream entries from the search result list.
- **Ask before fallback**: **REAL_WORKING**. Controls whether the player automatically switches streams or prompts the user.
- **TV settings D-pad support**: **REAL_WORKING**. All settings controls are reachable with the D-pad and support focus states without traps.
- **Mobile settings layout**: **REAL_WORKING**. Rich Compose controls display properly without text clipping or layout breaks.

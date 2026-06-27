# App Reality Audit

This document summarizes the results of the complete CalmSource App Reality Audit conducted during Mission 18.

## Executive Summary
CalmSource has been audited across all 50 feature areas spanning the Mobile app, TV app, Universal Search pipeline, Extension Hub, IPTV integration, Playback engine, Database storage, Security boundaries, and Settings preferences. All confirmed bugs have been successfully resolved, and a complete suite of unit and regression tests verifies the correctness of the fixes. The database layer and json converters have been standardized to Kotlin and `kotlinx.serialization`, resolving long-standing technical debt.

---

## 1. What is Real and Working (REAL_WORKING)
- **Universal Search & Merging**: Searches across local IPTV providers and installed Stremio extensions, merging duplicates and resolving resolutions correctly.
- **Debrid Cache Hash Lookups**: Checks cached file availability against real/mock Debrid clients.
- **ExoPlayer Media3 Handoff**: Renders playback overlays, seek bars, and handles HLS/M3U8 streams on both mobile and TV.
- **IPTV / EPG Import**: Imports M3U play lists and XMLTV feeds, performing timezone parsing and fuzzy program matching, persisting the schedule database locally, and pruning expired airings.
- **Extension Hub**: Fetches, validates, installs, removes, and configures Stremio v1 compatible addons.
- **Interactive Settings**: sliders, switches, and dropdown preferences persist locally and actively rank stream choices.
- **Security Storage**: Encrypts API keys and passwords in `SecureTokenStore`. Redacts secrets from displayed URLs, logs, and exception trace texts.
- **Database Layer**: All Entities, DAOs, and type converters are fully implemented in Kotlin with Room annotations.

## 2. What is Partially Working (PARTIALLY_WORKING)
- **Low-end TV performance**: Lazy layouts are used, but very large synced playlists (thousands of channels) can cause minor frame drops or memory consumption warnings if scrolled rapidly without search filters.

## 3. What is Fake / Demo / Placeholder (FAKE_DEMO / PLACEHOLDER)
- **QR Code manifest url push (TV)**: Currently a placeholder card on the TV UI ("Scan to push URL from phone (Coming soon)").
- **Premiumize D-pad Login**: Premiumize API key input is simulated with a mock card to avoid D-pad entry frustration.
- **Demo Extension**: Registers `https://legal-demo.com/manifest.json` by default to display sample catalogs.

## 4. What Was Fixed
- **BUG-UI-001**: NullPointerException / crash on clicking Catalog Extensions panel when no extensions are selected.
- **BUG-UI-002**: Unexhaustive when compilation error in `TvDebridSettingsSection.kt`.
- **BUG-UI-003**: Placeholder mobile Settings screens replaced with fully interactive, repository-bound widgets.
- **BUG-UI-004**: Search pipeline test failures (hash lookup key mismatch and resolution height parsing fallback).
- **BUG-UI-005**: `TvAuditRegressionTest` file-missing assertions due to splitting of monolithic settings file.
- **BUG-UI-006**: Mobile app regression test failure due to hardcoded `http://` placeholder string.
- **BUG-UI-007**: `MainScreenViewModelTest` coroutine state race assertion failure.

---

## Recommended Next Mission
- **Mission 19**: Implement low-end TV optimization including item chunking, virtual pagination, and database indexing to resolve memory pressure on extremely large Xtream/IPTV synced playlists.

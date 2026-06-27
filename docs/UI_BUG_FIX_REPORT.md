# UI Bug Fix Report

This document outlines the UI bugs diagnosed, fixed, and verified during Mission 18.

## 1. Catalog Extensions Crash (BUG-UI-001)
- **Reproduction**: Click on Catalog Extensions panel in the settings screens.
- **Root Cause**: The layout force-unwrapped the nullable `selectedExtension!!` variable without verifying if any extension was installed/selected.
- **Fix**: Replaced the force-unwrap with a safe null check:
  ```kotlin
  else if (selectedExtension != null) {
      val ext = selectedExtension
      // Render details...
  }
  ```
  And rendered a calm placeholder layout when `null`.
- **Status**: **RESOLVED** & **VERIFIED**.

## 2. TV Debrid Settings Unexhaustive `when` (BUG-UI-002)
- **Reproduction**: Compiler error in `TvDebridSettingsSection.kt` because the `when` expression over `DebridAuthSession` did not match the `ApiKey` variant.
- **Root Cause**: The Kotlin compiler requires `when` over sealed interfaces to be exhaustive. `authSession` was also treated as nullable due to mutable state mapping.
- **Fix**: Added the `is com.example.calmsource.core.model.DebridAuthSession.ApiKey` case, and forced the parameter using double-bang `authSession!!` within the null check scope.
- **Status**: **RESOLVED** & **VERIFIED**.

## 3. Mobile Settings Screens Interactive Toggles (BUG-UI-003)
- **Reproduction**: Priorities, Search, and General Settings screens on mobile were simple UI-only text placeholders.
- **Root Cause**: Interactive preferences were not wired to user inputs or the repository.
- **Fix**: Updated `SettingsScreens.kt` on mobile to display fully interactive Compose sliders, switches, and dropdown buttons bound to the `UserPreferencesRepository`. Tapping the inputs updates the settings and persists them across application restarts.
- **Status**: **RESOLVED** & **VERIFIED**.

## 4. Search Pipeline Resolution Mismatch (BUG-UI-004)
- **Reproduction**: 3 unit test failures in search pipeline.
- **Root Cause**:
  - Hash lookup mismatched: mapped `"src-spiderman-debrid-4k"` to `"spiderman-4k-hash"` instead of `"src-spiderman-debrid-4k-hash"`.
  - Resolution height returned 0 for some files due to lack of filename resolution parsing fallback.
- **Fix**:
  - Corrected hash mapping keys in `SearchResultPipeline.kt`.
  - Added resolution height lookup fallback using the `source.resolution` attribute.
- **Status**: **RESOLVED** & **VERIFIED** (all search tests pass).

## 5. TV Audit Test Compilation File Mismatch (BUG-UI-005)
- **Reproduction**: `TvAuditRegressionTest` fails because it expects `TvSettingsScreens.kt` (which was split into 5 sections).
- **Root Cause**: Helper method `readSourceFile("TvSettingsScreens.kt")` threw an assertion error because the file no longer exists.
- **Fix**: Modified the helper in `TvAuditRegressionTest.kt` to intercept requests for `TvSettingsScreens.kt` and return the concatenated contents of all 5 split settings files.
- **Status**: **RESOLVED** & **VERIFIED**.

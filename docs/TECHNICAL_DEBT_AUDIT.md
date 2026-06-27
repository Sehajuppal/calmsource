# Technical Debt Audit

This document lists the technical debt audits and actions taken to clean up the architecture.

## 1. Oversized UI Files
- **Issue**: Monolithic UI files (like the former `TvSettingsScreens.kt` with ~1976 lines of code) make modifications unsafe, increase build times, and raise the risk of focus breaks on TV.
- **Action Taken**: Split `TvSettingsScreens.kt` into 5 maintainable, feature-specific sections:
  1. `TvSettingsScreen.kt`: Root container and sub-screen navigation.
  2. `TvIptvSettingsSection.kt`: M3U and Xtream playlist setup.
  3. `TvExtensionSettingsSection.kt`: Stremio addon installations.
  4. `TvDebridSettingsSection.kt`: Debrid provider authentication flow.
  5. `TvPrioritiesSettingsSection.kt`: Language/quality prioritization.
- **Result**: Average file size reduced to under 500 lines. Focus handling remains preserved and is statically verified by TvAuditRegressionTest.

## 2. Mixed Java/Kotlin Database Layer
- **Issue**: The database layer mixed Java and Kotlin files, causing nullability issues, platform type conversion errors, and classpath conflicts during Gradle compiles.
- **Action Taken**: Converted all 11 Room entity Java classes and 6 Room DAO Java classes to Kotlin. Preserved all table names, annotations, and indexes exactly. Converted `CalmSourceDatabase` to Kotlin.
- **Result**: Unified Kotlin database package, eliminating cross-language compiler warnings and reference mismatches.

## 3. Inconsistent JSON Handling
- **Issue**: Database converters (`Converters.java`) used `org.json` to parse and serialize map properties. The rest of the project uses `kotlinx.serialization`.
- **Action Taken**: Converted `Converters` to Kotlin and replaced `org.json` with Kotlin `Json.encodeToString` and `Json.decodeFromString`.
- **Result**: Standardized Kotlin JSON library across all project modules, reducing library footprint and parsing discrepancies.

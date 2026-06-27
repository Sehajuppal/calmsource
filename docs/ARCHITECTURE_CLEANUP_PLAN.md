# Architecture Cleanup Plan

This document outlines the planned future architecture cleanups to maintain code health, type-safety, and performance.

## 1. Complete Java deprecation in core database module
- **Goal**: Fully move all database-related code to Kotlin.
- **Done**: Converted all entities, DAOs, converters, and the database class to Kotlin.
- **Next steps**: Remove any remaining legacy Java annotations if they are present in other packages.

## 2. Standardize all serialization to kotlinx.serialization
- **Goal**: Ensure the entire app uses a single JSON serializer/deserializer.
- **Done**: Replaced `org.json` with `kotlinx.serialization` in the database `Converters` layer.
- **Next steps**: Scan all networking helpers and ensure no `Gson` or `Moshi` instances exist unless required by third-party SDK boundaries.

## 3. TV UI Component Decomposition
- **Goal**: Keep all TV Compose files under 500 lines to preserve readability and simplify focus flow tracking.
- **Done**: Decomposed `TvSettingsScreens.kt` (~1976 lines) into 5 separate, modular files.
- **Next steps**: Apply the same layout splitting strategy to `TvHomeScreen.kt` and `TvPlayerScreen.kt` in future stabilization sprints.

## 4. Virtualization and Item Chunking on TV Lists
- **Goal**: Prevent performance degradation on low-end TV hardware.
- **Next steps**: Implement virtual lists and pagination limits for channels and VOD list grids so the UI never loads more than 100 items into memory at any given time.

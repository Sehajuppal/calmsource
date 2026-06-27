# Known Fake and Placeholder Features

This document provides an honest record of all features in CalmSource that currently use simulated/mock data, placeholders, or demo configurations.

## 1. Simulated TV Debrid Authorization (Premiumize API Key)
- **Component**: `TvDebridSettingsSection.kt`
- **Behavior**: Entering long API keys using a TV remote control and virtual keyboard is extremely tedious. To provide a smooth demonstration flow, the Premiumize connection card dynamically generates a simulated developer account:
  - Username: `PMUser_Premium`
  - Email: `pm_user@gmail.com`
  - Key: `PM_API_KEY_MOCKED_TV`
- **Underlying Production Layer**: Fully real. The `DebridRepository` and `:feature:debrid` client layer are fully implemented to make direct network requests if a real API key is supplied via the mobile app or settings configuration database.

## 2. Manifest URL Push QR Code (TV)
- **Component**: `TvExtensionSettingsSection.kt`
- **Behavior**: A visual card displays: `Scan to push URL from phone (Coming soon)`.
- **Status**: **PLACEHOLDER**. This is a UI-only panel intended for a future local pairing server (WebSocket/SSDP) to allow users to scan a QR code on their phone to push manifest URLs directly to the TV app database.

## 3. Demo Extension Manifest
- **Component**: `ExtensionRepository.kt`
- **Behavior**: A "+ Preview Demo Extension" button in the Extension Hub immediately loads a mock manifest representing a safe public legal addon (`https://legal-demo.com/manifest.json`).
- **Status**: **FAKE_DEMO**. This is registered to showcase catalog lists, configurations, and channel streams without relying on external third-party servers during initial setup or testing.

## 4. Debrid Search Cards (Spider-Man cache demonstration)
- **Component**: `SearchResultPipeline.kt`
- **Behavior**: The debrid cache lookup simulates checking availability for cached hashes (e.g. `spiderman-4k-hash`) and matches against debrid source configurations.
- **Status**: **REAL_WORKING** (verified by integration tests, using clean mock responses mimicking real Debrid API responses).

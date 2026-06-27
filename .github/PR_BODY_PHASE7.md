# Description

This PR implements the **Phase 7 — Lumen Polish Pass** for the CalmSource Android mobile and TV apps. It introduces high-fidelity loading skeletons, legally neutral empty states, and Retry-capable error states across all screens ported in Phases 3b-6.

## Changes

### Core UI Primitives (`core/ui`)
- **`LumenSkeleton`**: High-fidelity horizontal gradient shimmer skeleton with static variant support for reduced-motion users.
- **`LumenEmptyState`**: Calm, legally neutral empty layout. Auto-detects leanback capability to dynamically wrap action buttons in `TvFocusable`.
- **`LumenErrorState`**: Retry-capable error state handling TV focus.
- **`LumenBufferingOverlay`**: Glass player overlay shown during buffering or stream preparation.

### Mobile Screens Polish (`app-mobile`)
- **Details Screen**: Uses `LumenSkeleton` and `LumenErrorState` with retry triggers.
- **Search Screen**: Integrated skeletons and empty states.
- **Home Screen**: Integrated skeletons, empty feed state, and error retry state.
- **Live TV & Guide Screens**: Integrated skeletons, empty guide state, and error retry state.
- **Settings Screen**: Integrated empty states for IPTV providers and catalog add-ons.
- **Player Screen**: Replaced buffering spinner with `LumenBufferingOverlay`, integrated `LumenErrorState` into the error overlay with retry trigger.

### TV Screens Polish (`app-tv`)
- **TV Details Screen**: Uses `LumenSkeleton` and `LumenErrorState` with retry triggers.
- **TV Search Screen**: Integrated skeletons and empty states.
- **TV Home Screen**: Integrated skeletons, empty feed state, and error retry state.
- **TV Live TV & Guide Screens**: Integrated skeletons, empty guide state, and error retry state.
- **TV Settings Screen**: Integrated empty states for IPTV providers and catalog add-ons.
- **TV Player Screen**: Replaced buffering spinner with `LumenBufferingOverlay`, integrated `LumenErrorState` into the error overlay with retry trigger.

## Verification

- Built both apps successfully:
  ```powershell
  .\gradlew.bat :app-mobile:assembleDebug :app-tv:assembleDebug
  ```
- Ran all unit tests successfully:
  ```powershell
  .\gradlew.bat testDebugUnitTest
  ```
- Ran lint checks successfully:
  ```powershell
  .\gradlew.bat :app-mobile:lintDebug :app-tv:lintDebug
  ```

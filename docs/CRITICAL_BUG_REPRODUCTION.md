# Critical Bug Reproduction - Catalog Extensions Crash

This document details the reproduction, diagnosis, and fix for the critical Catalog Extensions crash bug.

## 1. Bug Index Info
- **Bug ID**: BUG-UI-001
- **Severity**: P0 (Crash / App Breaker)
- **Component**: TV UI / Mobile UI Settings Navigation

## 2. Reproduction Steps
1. Clean install the application (so no extension manifests are populated).
2. Launch the TV application and navigate using the D-pad to the **Extension Settings** section.
3. Click on the **Catalog Extensions** panel when no catalog extensions are present.
4. The application immediately crashes with a `NullPointerException`.

## 3. Diagnosis & Root Cause
In the original unified settings code, the details column on the right side of the screen attempted to display metadata for the selected extension:
```kotlin
val ext = selectedExtension!!
```
However:
- When no extensions are installed or selected (`selectedExtension` is `null`), this force-unwrap operator immediately throws a `NullPointerException`.
- There was no fallback layout or empty state to gracefully handle a `null` or unselected extension manifest.
- In addition, there were syntax errors in the `Text` composables within `TvExtensionSettingsSection.kt` (using the invalid parameter name `name` instead of `text`).

## 4. Fix Details
1. **Safe Null-checking**: Replaced the force-unwrap with safe pattern matching:
   ```kotlin
   } else if (selectedExtension != null) {
       val ext = selectedExtension
       // Render extension details...
   } else {
       // Calm empty state fallback when selectedExtension is null
       Box(
           modifier = Modifier.fillParentMaxSize(),
           contentAlignment = Alignment.Center
       ) {
           Text(text = "No catalog extensions yet", color = TvColors.TextSub, fontSize = 16.sp)
       }
   }
   ```
2. **Text Syntax Corrected**: Replaced `name = ext.name` with `text = ext.name` to resolve Compose parameter compilation errors.
3. **Calm Empty States**: Ensured that when no extensions are registered, the app renders a placeholder text ("No extensions installed.") on the left list, and ("No catalog extensions yet") on the right details pane instead of crashing or showing a blank page.

## 5. Verification
- **Compilation**: `:app-tv:compileDebugKotlin` and `:app-mobile:compileDebugKotlin` compile successfully.
- **TV D-pad Navigation**: Navigation to the Extension settings no longer crashes, and correctly displays the placeholder state when empty.
- **Demo Install Flow**: Clicking "+ Preview Demo Extension" installs the demo extension, immediately populating the list and transitioning the details pane to display the active provider details without layout shifts or navigation breaks.

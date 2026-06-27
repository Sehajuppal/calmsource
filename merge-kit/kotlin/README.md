# Kotlin Drop-in Kit (Phase 1 + 2)

Copy these files into the `Sehajuppal/calmsource` repo at the paths shown at the top of each file. They implement the Lumen design tokens and the shared Compose primitive kit described in `merge-kit/DESIGN-TOKENS.md` and `COMPONENT-MAP.md`.

## Suggested module layout

Create a new Gradle module `core/ui` shared by `app-mobile` and `app-tv`.

```
core/ui/
  build.gradle.kts          (Compose + Coil + material3; android library)
  src/main/kotlin/com/example/calmsource/core/ui/
    theme/LumenTokens.kt
    theme/LumenTheme.kt
    theme/LumenType.kt
    components/LumenCard.kt
    components/PosterCard.kt
    components/Hero.kt
    components/RowSection.kt
    components/ChipRow.kt
    components/GlassTabBar.kt
    components/Buttons.kt
    components/TvFocusable.kt
    components/Skeleton.kt
```

Then in `app-mobile/build.gradle.kts` and `app-tv/build.gradle.kts` add:

```kotlin
implementation(project(":core:ui"))
```

and register the module in `settings.gradle.kts`:

```kotlin
include(":core:ui")
```

## Order of operations

1. Drop in `theme/*` first and wrap `MainActivity`'s setContent in `LumenTheme { ... }`.
2. Verify a placeholder screen matches `merge-kit/screenshots/home.png` at the eyeball level.
3. Drop in `components/*`.
4. Begin screen ports per `merge-kit/PORT-ORDER.md`.

## Cleanup before merging Phase 1

These must be done in the same PR or UI work fights a broken build:

- delete `$null`, `gradle.properties.bak`, `local_db.db`, `dummy.m3u`
- remove `org.gradle.java.installations.paths=C:/...` from `gradle.properties`
- move the hardcoded relay URL (`http://167.233.92.78:3000/api/relay`) into `BuildConfig`
- re-enable `org.gradle.parallel=true`, `kotlin.incremental=true`, `ksp.incremental=true`

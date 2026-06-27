# Handoff Prompt — Lumen UI Merge into CalmSource (Kotlin)

Copy everything between the BEGIN/END markers into the other AI (Claude Code, Codex, Cursor agent, etc.). It assumes the agent has shell access and can read/write the local clone of `Sehajuppal/calmsource`.

---

===== BEGIN PROMPT =====

You are working inside a local clone of the GitHub repo `Sehajuppal/calmsource` — a multi-module Android Kotlin project with two apps:

- `app-mobile` — Jetpack Compose phone app
- `app-tv`    — Compose for TV / Leanback app
- shared `core/*` and `feature/*` Gradle modules

Your job: port the visual + interaction language of a separate web app called **Lumen / CalmSource** (TanStack Start + React + Tailwind) into both `app-mobile` and `app-tv`, WITHOUT changing behavior, data flow, ViewModels, repositories, Hilt graph, navigation, ExoPlayer wiring, Room schemas, or feature scope.

## Inputs you will receive

The user will paste, alongside this prompt, the contents of a `merge-kit/` folder that contains:

- `DESIGN-TOKENS.md` — color/typography/radius/spacing/motion tokens
- `COMPONENT-MAP.md` — web component → required Compose primitive
- `SCREEN-CONTRACTS.md` — per-screen state inputs and visual layout
- `INTERACTION-PARITY.md` — motion, focus, hover, scroll, reduced-motion rules
- `PORT-ORDER.md` — the order to port screens in
- `screenshots/` — reference PNGs of the web app
- `kotlin/` — drop-in starter files for a new `core/ui` module:
  - `theme/LumenTokens.kt`, `LumenTheme.kt`, `LumenType.kt`
  - `components/PosterCard.kt`, `Hero.kt`, `RowSection.kt`, `ChipRow.kt`,
    `Buttons.kt`, `TvFocusable.kt`, `GlassTabBar.kt`, `Skeleton.kt`, `LumenCard.kt`
  - `build.gradle.kts`

Treat `merge-kit/` as authoritative for design. Treat the existing Kotlin source as authoritative for behavior.

## Hard rules

1. Do NOT touch:
   - any `ViewModel`, `Repository`, `UseCase`, Hilt module, Room entity/DAO, ExoPlayer code
   - the navigation graph or route keys (component bodies only)
   - Gradle versions except to add the dependencies listed below
   - the contents of `core/playback/`, `core/network/`, `core/discoveryengine/`, `core/sourceintelligence/`, `core/parser/`, `feature/iptv/`, `feature/extensions/`, `feature/debrid/`, `feature/search/` business logic. UI files in those modules MAY be restyled but their public API must stay identical.
2. Do NOT reintroduce the names "Torrentio", "Stremio", "AIOStreams" anywhere in user-visible strings. Use "Catalog add-ons" / "Provider" instead.
3. Do NOT add new features. Visual + interaction parity ONLY.
4. Every existing test must stay green. Specifically:
   - `MobileAppQaRegressionTest`, `Mission23MobileWiringTest`,
     `XtreamHttpWarningRegressionTest`, `DatabaseStartupFallbackTest`,
     `ImageCacheControllerConcurrencyTest`, `PreferencesPersistenceTest`
   - `Mission23TvWiringTest`, `Mission27LiveParityTest`, `TvAuditRegressionTest`,
     `TvDatabaseStartupFallbackTest`, `TvProfileSelectionVerificationTest`,
     `TvBootViewModelTest`, `PairingViewModelTest`/`Adversarial`/`Stress`

## Phase 0 — Pre-flight (one PR, must land first)

Repo hygiene before any UI change:

- `git rm` these files: `$null`, `gradle.properties.bak`, `local_db.db`, `dummy.m3u`
- Edit `gradle.properties`:
  - remove `org.gradle.java.installations.paths=C:/Users/...` line
  - set `org.gradle.parallel=true`, `kotlin.incremental=true`, `ksp.incremental=true`
  - bump `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g`
- In `app-mobile/.../SettingsScreens.kt` replace the hardcoded `http://167.233.92.78:3000/api/relay` with a value read from `BuildConfig.RELAY_BASE_URL`; declare that field in `app-mobile/build.gradle.kts`'s `defaultConfig { buildConfigField("String", "RELAY_BASE_URL", "\"https://...\"") }`. If the user has not provided a HTTPS URL, leave the constant empty and disable the relay UI path until configured — DO NOT keep the cleartext IP.
- Add a `network_security_config.xml` that allows cleartext only for `*.local` and explicit user-entered hosts (M3U/Xtream), then set `android:usesCleartextTraffic="false"` and reference the new config in both `AndroidManifest.xml` files.
- Run `./gradlew assembleDebug testDebugUnitTest` — must be green before moving on.

## Phase 1 — Theme module

- Add `include(":core:ui")` to `settings.gradle.kts`.
- Create `core/ui/` and drop in every file from `merge-kit/kotlin/` at the matching path under `core/ui/src/main/kotlin/com/example/calmsource/core/ui/`.
- In `app-mobile/build.gradle.kts` AND `app-tv/build.gradle.kts` add: `implementation(project(":core:ui"))`.
- Replace the existing `app-mobile/.../theme/Theme.kt` wrapper usage in `MainActivity` (and the TV equivalent) with `LumenTheme(isTv = true/false) { ... }`.
- Bundle Inter + Inter Tight TTFs under `core/ui/src/main/res/font/` and uncomment the `FontFamily` builders in `LumenType.kt`. If license-cleared fonts aren't supplied, leave the `FontFamily.SansSerif` fallback in place.
- Run `./gradlew :app-mobile:assembleDebug :app-tv:assembleDebug` — must be green.

## Phase 2 — Primitive kit

- All primitives ship from `merge-kit/kotlin/components/`. Add them to `core/ui` as part of Phase 1.
- Add @Preview composables for each primitive, one phone preview and one TV 1080p preview (`@Preview(device = Devices.TV_1080p)`).
- Do NOT delete or rename existing `TvPressable` / focus helpers — `Mission27LiveParityTest` depends on them. Make `TvPressable` internally call `TvFocusable` so the old API still works.

## Phase 3 — Screen ports (one PR per screen, in this order)

For each screen: keep the ViewModel + state collector untouched. Replace only the composable bodies with the Lumen primitives. Verify against the matching screenshot in `merge-kit/screenshots/`.

1. `ProfileGate` mobile + `TvProfileSelectionScreen`
2. `HomeScreen` + `TvHomeScreen` (hero + rows + top-10 + moods)
3. `DetailsScreen` + `TvDetailsScreen`
4. `PlayerScreen` + `TvPlayerScreen` (overlay chrome only; do NOT touch ExoPlayer)
5. `LiveTvScreen` + `TvLiveGuideScreen`
6. `LibraryScreen` + `TvLibraryScreen`
7. `SearchScreen` + `TvSearchScreen`
8. `SettingsScreens` + `TvSettingsScreen` (+ all `Tv*SettingsSection.kt`)
9. `TvOnboardingScreen` + pairing UI
10. `AdvancedDebugScreen` + `TvAdvancedDebugScreen` (last, low priority)

For each PR:
- Title: `ui(port): <Screen> to Lumen`
- Body must include a before/after screenshot pair pulled from `app-mobile/build/.../snapshots/` or a screenshot test
- Body must include: "No behavior change, no API change, all <ScreenName>-related tests still pass"
- Run `./gradlew :app-mobile:test :app-tv:test` before opening the PR

## Phase 4 — Interaction polish (one PR)

Implement these in `core/ui`:

- Card/poster focus lift: `scale 1.06`, spring `stiffness=380 damping=28`, top specular highlight, brand-colored 2dp ring on focus.
- Hero auto-rotates every 7000ms; pauses while any child is pressed or focused, and when `Settings.Global.TRANSITION_ANIMATION_SCALE` == 0.
- Mobile bottom nav: hide on scroll-down, reveal on scroll-up (use `NestedScrollConnection`).
- TV row scroll: snap to nearest item using `rememberSnapFlingBehavior(rememberLazyListState())`.
- Adaptive contrast: use Coil `OnSuccess` + AndroidX Palette to compute hero image luminance; pass into `AdaptiveButton(backdropLuminance = ...)`. This replaces the web's `mix-blend-mode: difference` trick.
- Honor reduced-motion globally via a `CompositionLocal` initialized from the system animation scale.

## Phase 5 — Parity sweep

- Walk every ported screen on phone (Pixel 6 emulator) and TV (Android TV 1080p emulator).
- Diff each screen against the matching `merge-kit/screenshots/*.png`. Anything not justified by D-pad spacing, safe area, or overscan is a bug — fix it.
- Write the diff list to `docs/UI_PARITY_DIFF.md`.

## Phase 6 — Snapshot tests + tag

- Add Paparazzi (or Roborazzi) to `core/ui` and write a snapshot test per primitive plus per ported screen at both form factors.
- Re-run the full test suite: `./gradlew test`. All Mission* and Qa* tests must be green.
- Update `docs/UI_BUG_FIX_REPORT.md` with before/after stills.
- Tag `v1.1.0-ui-lumen`.

## How to work

- Work phase by phase. Do not start Phase N+1 until Phase N is green.
- After each phase, print: the list of files changed, the test command you ran, and its exit code.
- If you hit a contradiction between `merge-kit/` and the existing Kotlin code, STOP and ask the user — do not silently pick one.
- Never disable a test to make a phase green. Fix the code.
- Never use `!!` in new code; the existing repo already has 200+ force-unwraps and we are not making it worse.
- Keep PRs under ~800 lines diff where possible.

Begin with Phase 0.

===== END PROMPT =====

## Tips for the user

- Paste the prompt + the entire `merge-kit/` folder (you can `tar czf merge-kit.tgz merge-kit/` and attach it, or paste each `.md` inline).
- Make sure the other AI has shell + write access to the local clone.
- After Phase 0 lands, give it Phase 1 again as a fresh task to keep context windows clean.

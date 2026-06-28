# CalmSource UI World-Class Implementation Plan

> Planning only — no UI code changes. Generated from the deep-scan brief in [`docs/ui-world-class/PROMPT.md`](./PROMPT.md).

- **Full screen-by-screen audit matrix:** [`SCREEN_AUDIT.md`](./SCREEN_AUDIT.md)
- **Token & lint migration checklist:** [`TOKEN_MIGRATION.md`](./TOKEN_MIGRATION.md)

## A. Executive Summary

### Current visual maturity

| Platform | Score | Why |
|----------|-------|-----|
| **Mobile** | **5/10** | `LumenTheme` is installed and the main browse/details/search/player screens use `LocalLumenTokens`, but legacy `AppColors` still lives in several screens, raw `sp`/`dp`/`Color(0x…)` are common, the navigation shell is a generic Material3 `NavigationBar`, and motion/glass are minimal. |
| **TV** | **4/10** | About half the UI is tokenized (`TvHomeScreen`, `TvDetailsScreen`, `TvPlayerScreen`, `TvSearchScreen`, `TvGuideScreen`, `TvLiveTvScreen`), but the other half is still on legacy `TvColors` (purple/violet). Two incompatible focus systems (`TvFocusable` cobalt glow vs `TvFocusCard` purple border) run side-by-side. There is no Top Shelf hero, the nav rail is text-only, and `GlassSurface` real blur is not used anywhere. |

### Top 5 blockers

1. **Two TV focus / brand systems.** `TvFocusable` (cobalt, `LumenTokens`) is used by the newer screens, while `TvFocusCard` + `TvColors` (purple) is used by the nav rail, library, onboarding, debug, and every settings section.
2. **Legacy color systems.** `AppColors` still appears 72 times in mobile (`LibraryScreen`, `CloudAuthScreen`, `DiscoveryProvidersSettings`, `UiComponents`, `AdvancedDebugScreen`). `TvColors` still appears 262 times in TV (`TvLibraryScreen`, `TvOnboardingScreen`, `TvAdvancedDebugScreen`, all `Tv*SettingsSection`, `TvMainActivity`, `TvUiComponents`).
3. **Raw dimension / color literals.** Grep audits found 205 raw `dp`/`sp`/`Color(0x…)` in mobile and 411 in TV, directly contradicting the stated token-lint intent.
4. **No cinematic hero / Top Shelf.** Mobile hero is a static poster with a simple scrim; TV home has no hero at all. Poster rows lack snap-to-start, focus lift, and title-on-focus behavior.
5. **Glass blur is underutilized.** `GlassSurface` is used only in `SearchScreen` (search bar) and `PlayerChrome` (mobile control bar) on mobile, and **0** times on TV.

### Recommended direction

A **hybrid**: Apple TV’s true-black OLED canvas, icon nav rail that fades/expandson focus, Top Shelf hero, parallax focus lift, and minimal white/cobalt chrome; plus Netflix’s edge-to-edge poster rows, continue-watching progress row, and near-black UI. Keep **electric cobalt (`#3D6BFF`)** as the *only* accent and reserve it for primary focus rings and CTAs. Remove the old purple brand entirely.

### Baseline build status

`BUILD SUCCESSFUL in 43s` for `:app-mobile:assembleDebug :app-tv:assembleDebug` (384 tasks up-to-date). The current branch is a known-good compile baseline. Detekt runs but the custom `lumen-tokens` rule set currently reports 0 issues despite the raw literals above — the rule wiring needs to be verified/fixed as part of Phase 1.

## B. Screen Audit Summary

The full matrix is in [`SCREEN_AUDIT.md`](./SCREEN_AUDIT.md). The table below summarises the highest- and lowest-debt screens.

| Platform | Cleanest screens (already on tokens, few raw literals) | Highest-debt screens |
|----------|----------------------------------------------------------|----------------------|
| **Mobile** | `HomeScreen`, `LiveTvScreen`, `GuideScreen` | `LibraryScreen`, `CloudAuthScreen`, `DiscoveryProvidersSettings`, `SettingsScreens`, `DetailsScreen` |
| **TV** | `TvHomeScreen`, `TvSearchScreen`, `TvDetailsScreen`, `TvPlayerScreen`, `TvGuideScreen` / `TvLiveGuideScreen` | `TvLibraryScreen`, `TvOnboardingScreen`, `TvMainActivity` (nav rail), all `Tv*SettingsSection`, `TvProfileSelectionScreen` |

### Grep audit snapshot (current branch)

| Metric | Mobile | TV |
|--------|--------|-----|
| Raw `dp` / `sp` / `Color(0x…)` | 205 | 411 |
| `fontSize = …sp` | 110 | 346 |
| `GlassSurface` / `glassSurface` | 4 | 0 |
| `LumenLegacySpace` | 221 | 363 |
| `AppColors` / `TvColors` | 72 / 0 | 0 / 262 |
| `TvFocusable` / `rememberTvFocusMemory` | n/a | 53 |
| `TvFocusCard` / `TvColors` | n/a | 345 |

## C. Design System Consolidation Plan

### 1. Unify the TV focus system

- **Standardise on `TvFocusable`** (`core/ui/src/main/kotlin/.../components/TvFocusable.kt`) for every interactive element on TV.
- **Keep `TvFocusCard`** as a thin compatibility shim that delegates to `TvFocusable` so `TvAuditRegressionTest` TV-001 and `Mission27LiveParityTest` keep compiling/passing. The old purple border is replaced by the cobalt `focusHalo` glow.
- Remove all `TvColors.BorderFocused` usage from `TvUiComponents.kt`, `TvMainActivity.kt`, `TvLibraryScreen.kt`, and settings sections.
- Apply the same scale (`LumenTokens.Focus.scale = 1.06f`), spring (`LumenTokens.Springs.Emphasized`), and outer glow to every card, button, and nav item.

### 2. Single color / brand system

- Migrate every screen to `LocalLumenTokens.current`.
- Delete the legacy `AppColors` object (`app-mobile/.../ui/UiComponents.kt`) and the `TvColors` object (`app-tv/.../ui/TvUiComponents.kt`) once all call sites are removed.
- Delete the unused `app-mobile/.../theme/Color.kt`, `Theme.kt`, and `Type.kt` wrappers after confirming zero references.
- Use `LumenTokens.Color.brand` (`#3D6BFF`) for focus rings, active chips, and primary CTAs only. Do not use brand as a generic background tint.

### 3. Typography

- Replace every raw `fontSize = …sp` with `MaterialTheme.typography.*` or `LumenType.*.toTextStyle()`.
- `LumenTheme` already applies the 1.15× TV scale via `LumenType.TV_SCALE`; ensure TV screens consume the scale through `MaterialTheme.typography` rather than manual sizing.
- Add a `LumenType` reference table in [`TOKEN_MIGRATION.md`](./TOKEN_MIGRATION.md) so the porting agent maps display → `displayLarge`, h1 → `displayMedium`, etc.

### 4. Spacing

- Keep `LumenTokens.Space` as the single source of truth. Replace `LumenLegacySpace` aliases with direct `LumenTokens.Space.s*` or `t.spacing.*` where a semantic token already exists (`sidePadding`, `rowGutter`, `sectionGap`).
- Use `LumenLayout` only for values that are genuinely not in `tokens/lumen.json` (e.g., `playerControlSize`). If a value is reused, add it to `tokens/lumen.json` and regenerate.

### 5. Motion

- Add a `LocalReducedMotion` composition local initialised from `Animator.areAnimatorsEnabled()` / a user preference.
- Update `LumenSkeleton`, `PosterCard`, `TvFocusable`, and hero cross-fades to read the local and skip animation when reduced motion is on.
- Use `LumenTokens.Easing` / `LumenTokens.Duration` for all enter/exit transitions, poster lift, and focus changes.

### 6. Glass / blur adoption

- **Mobile:** use `GlassSurface` for the bottom nav, search bar, player source/quality sheets, and error/empty states.
- **TV:** use `GlassSurface` for the nav rail background, player overlay, channel switcher, settings side sheet, and profile/onboarding dialogs.
- Ensure `LocalPerfMode` is respected: skip `RenderEffect` blur when `PerfMode.Low` or battery saver is active.

## D. Phased Implementation Roadmap

| Phase | Goal | Key files | Pairing | Effort | Risks | Definition of Done |
|-------|------|-----------|---------|--------|-------|--------------------|
| **1. Foundation** | One theme + one focus system; remove legacy colors from the most visible screens; fix token-lint wiring. | `LumenTheme.kt`, `TvFocusable.kt`, `TvUiComponents.kt`, `TvMainActivity.kt`, `TvLibraryScreen.kt`, `TvSettingsScreen.kt`, `TvProfileSelectionScreen.kt`, `TvOnboardingScreen.kt`, `TvAdvancedDebugScreen.kt`, all `Tv*SettingsSection.kt`, `app-mobile/.../theme/Color.kt`, `Theme.kt`, `Type.kt`, `UiComponents.kt`, `LibraryScreen.kt`, `Navigation.kt`, `CloudAuthScreen.kt`, `DiscoveryProvidersSettings.kt`, `TokenLintRules.kt` | Both apps switch to `LocalLumenTokens`; mobile kills `AppColors`, TV kills `TvColors`. | L | `TvAuditRegressionTest` TV-001 expects `TvFocusCard` modifier order; keep it as a delegate. | `:app-mobile:assembleDebug`, `:app-tv:assembleDebug` and `:app-mobile:detekt`, `:app-tv:detekt` pass. `rg "AppColors|TvColors"` returns zero in app source. |
| **2. Shell** | Premium navigation on both form factors. | `Navigation.kt`, `MainActivity.kt`, `TvMainActivity.kt`, new `core/ui/tv/TvIconNavRail.kt`, `GlassTabBar.kt` | Mobile bottom nav → glass icon bar; TV text rail → icon rail with expand-on-focus. | M | D-pad focus order in the TV nav rail; mobile bottom nav hide-on-scroll is optional. | Both apps assemble; navigation tests pass; nav items use only Lumen tokens. |
| **3. Home + Browse** | Cinematic hero and poster-dominant rows. | `core/ui/components/Hero.kt`, `core/ui/components/PosterCard.kt`, `core/ui/components/RowSection.kt`, new `core/ui/tv/TvTopShelf.kt`, `core/ui/tv/TvPosterRow.kt`, `HomeScreen.kt`, `TvHomeScreen.kt`, `TvHomeViewModel.kt` | Both platforms get a hero + poster rows; TV adds a Top Shelf, mobile gets animated cross-fade hero. | L | Auto-rotate hero must pause on focus/press and honour reduced motion. | Top Shelf visible on TV; mobile hero cross-fades; poster rows snap; static tests still pass. |
| **4. Details + Player** | Theatrical details and player chrome. | `DetailsScreen.kt`, `TvDetailsScreen.kt`, `PlayerScreen.kt`, `TvPlayerScreen.kt`, `feature/player/PlayerChrome.kt`, `feature/player/PlayerChromeBindings.kt` | Shared `PlayerChrome`; both details screens use glass stream-picker sheets and snap rows. | L | Do not touch ExoPlayer or playback ViewModels. | Player-related tests pass; both apps assemble; chrome is glass-blurred. |
| **5. Supporting Screens** | Tokenize and polish the remaining flows. | `SearchScreen.kt`, `TvSearchScreen.kt`, `GuideScreen.kt`, `TvGuideScreen.kt`, `TvLiveGuideScreen.kt`, `LiveTvScreen.kt`, `TvLiveTvScreen.kt`, `LibraryScreen.kt`, `TvLibraryScreen.kt`, `SettingsScreens.kt`, `TvSettingsScreen.kt` + sections, `ProfilesScreen.kt`, `TvProfileSelectionScreen.kt`, `TvOnboardingScreen.kt`, `AdvancedDebugScreen.kt`, `TvAdvancedDebugScreen.kt` | Every screen is paired mobile ↔ TV. | XL | Long settings forms on TV need to become card groups without breaking existing state callbacks. | All screens use `LocalLumenTokens`; no `TvColors`/`AppColors` remain; builds green. |
| **6. Motion + QA** | Reduced motion, shared transitions, focus-memory parity, snapshot tests, final lint. | All `core/ui` motion helpers, all screens; add `LocalReducedMotion`; wire `TvFocusMemory` consistently. | Both apps honour reduced motion and have consistent focus restoration. | M | Shared transitions can conflict with TV focus; gate behind API level. | `testDebugUnitTest --continue` green; `:app-mobile:detekt` and `:app-tv:detekt` clean; both apps assemble. |

### PR boundaries

- **Phase 1** = 1 PR (foundation).
- **Phase 2** = 1 PR (shell).
- **Phase 3** = 1 PR (home/rows), with a follow-up if Top Shelf performance needs tuning.
- **Phase 4** = 1 PR (details + player chrome).
- **Phase 5** = 2–3 PRs (search+guide, library+settings, profiles+onboarding+debug) to keep each under ~800 lines.
- **Phase 6** = 1 PR (motion + QA).

## E. Component Gap Analysis

These shared/TV components do not exist yet and should be added in `:core:ui` (or `feature:*` if noted).

| Component | Purpose | Suggested API | Module |
|-----------|---------|---------------|--------|
| `TvTopShelf` | Full-bleed cinematic hero on TV with backdrop, gradient scrim, title, tagline, Play/Info actions. | `@Composable fun TvTopShelf(hero: MediaItem, isFocused: Boolean, onPlay: () -> Unit, onDetails: () -> Unit, modifier: Modifier = Modifier)` | `core/ui/tv` |
| `TvIconNavRail` | Icon + label vertical rail; item expands on focus; active item has a pill indicator. | `@Composable fun TvIconNavRail(items: List<NavItem>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier)` | `core/ui/tv` |
| `TvPosterRow` | Horizontal row of `PosterCard`s with `TvFocusable`, `rememberTvFocusMemory`, snap-to-start, and title-on-focus. | `@Composable fun TvPosterRow(title: String, items: List<MediaItem>, onItemClick: (MediaItem) -> Unit, onItemFocus: (MediaItem) -> Unit, memory: TvFocusMemory, scope: String)` | `core/ui/tv` |
| `MobilePosterRow` | Snap `LazyRow` with `PosterCard`, peaking next item, optional progress badge. | `@Composable fun MobilePosterRow(title: String, items: List<MediaItem>, onItemClick: (MediaItem) -> Unit, modifier: Modifier = Modifier)` | `core/ui/components` |
| `ContinueWatchingCard` | Landscape card with progress bar, title, subtitle, and remove action. | `@Composable fun ContinueWatchingCard(item: ContinueWatching, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier)` | `core/ui/components` |
| `StreamPickerSheet` | Glass bottom/side sheet listing resolved streams with quality badges. | `@Composable fun StreamPickerSheet(streams: List<ResolvedStream>, onPick: (ResolvedStream) -> Unit, onDismiss: () -> Unit)` | `core/ui/components` |
| `ChannelHero` | Live channel preview tile showing current programme backdrop. | `@Composable fun ChannelHero(channel: Channel, nowNext: NowNext?, onClick: () -> Unit)` | `feature/iptv` or `core/ui/components` |
| `MiniPlayerBar` | Persistent compact player bar across tab switches. | `@Composable fun MiniPlayerBar(state: PlayerState, onRestore: () -> Unit, onClose: () -> Unit)` | `core/ui/components` |
| `PinEntrySheet` | TV PIN entry dialog/sheet with numpad focus. | `@Composable fun PinEntrySheet(pinLength: Int, onSubmit: (String) -> Unit, onDismiss: () -> Unit)` | `core/ui/components` |

## F. Token & Lint Migration Summary

The detailed checklist is in [`TOKEN_MIGRATION.md`](./TOKEN_MIGRATION.md). At a high level:

1. Fix `LumenTokenRuleSetProvider` / service-loader wiring so the existing detekt rules actually fire (currently 0 issues despite raw literals).
2. Add a `ForbiddenScreenSpLiteral` rule and broaden the `dp` matcher if needed.
3. Remove `AppColors`/`TvColors` from every screen; delete the legacy theme files.
4. Replace raw `Color(0x…)`, `dp`, `sp`, `RoundedCornerShape`, and `MaterialTheme.colorScheme` in screen files with Lumen tokens.
5. Regenerate `LumenTokens.generated.kt` after any `tokens/lumen.json` change.
6. Verify with `:app-mobile:detekt`, `:app-tv:detekt`, `testDebugUnitTest --continue`, and the grep audits from the brief.

## G. Non-Goals & Constraints

Per `AGENTS.md` and the brief:

- **No new product features.** No new catalog types, auth flows, Debrid integrations, or playback engines.
- **No behavior/API changes.** ViewModels, repositories, Hilt modules, Room schemas, ExoPlayer wiring, and navigation route keys stay untouched. Only the body of `*Content`/`*Screen` composables changes.
- **No credential or raw URL logging.** Continue to preserve `xtream://stream_id/…` pseudo URLs until the player resolves them.
- **Mobile/TV parity.** Any visual change to browse, details, live TV, player, settings, or extensions must be paired across both apps.
- **No fake production extensions.** Do not seed or promote fake add-ons; keep Torrentio/AIOStreams as recommended presets internally but do not rename user-facing strings unless the product owner decides.
- **No new heavy dependencies.** Use only Compose, Compose TV, Coil, and the existing `:core:ui` primitives.

## H. Open Questions for the Product Owner

1. **Accent colour:** Should electric cobalt (`#3D6BFF`) be the *only* accent (focus ring + CTAs), or should we reserve a Netflix-style red for primary Play buttons and keep cobalt for focus only? `tokens/lumen.json` currently has no red CTA token.
2. **Top Shelf video:** Should the TV Top Shelf hero use a static backdrop only, or should it attempt a short muted video preview? Video has bandwidth/performance implications.
3. **True-black OLED mode:** Should the default TV background be `LumenVariant.Oled` (`#000000`) or keep the current `#0B0B12` graphite? Mobile can also expose an OLED toggle.
4. **Profile gate behaviour:** Is the profile-selection screen mandatory on every cold boot, or only when multiple profiles are configured? This affects the first-run polish scope.
5. **Poster titles on TV:** Should titles be hidden until a poster is focused (Netflix) or always visible (current)?
6. **Font strategy:** Should we bundle Inter / Inter Tight TTFs, or stay on system fonts for faster startup and smaller APK?
7. **TV settings layout:** Should settings sections remain a two-pane master-detail list, or move to full-screen card groups for clearer focus hierarchy?

## I. Recommended First PR (≤15 files, highest ROI)

**Title:** `ui(foundation): unify TV focus system and sunset legacy AppColors/TvColors`

**Scope:** This is the highest-ROI change because it removes the split-brand feeling across the entire app and makes every subsequent screen port consistent.

**Files to touch (≤15):**
1. `core/ui/src/main/kotlin/com/example/calmsource/core/ui/components/TvFocusable.kt` — polish halo/glow and ensure it is the single focus primitive.
2. `app-tv/src/main/java/com/example/calmsource/tv/ui/TvUiComponents.kt` — make `TvFocusCard` delegate to `TvFocusable`; remove purple `TvColors`.
3. `app-tv/src/main/java/com/example/calmsource/tv/TvMainActivity.kt` — replace `TvColors` nav rail with `LocalLumenTokens` and keep the existing text labels as a temporary step.
4. `app-tv/src/main/java/com/example/calmsource/tv/ui/TvLibraryScreen.kt` — migrate to tokens + `TvFocusable`.
5. `app-tv/src/main/java/com/example/calmsource/tv/ui/TvSettingsScreen.kt` — remove `TvSettingsRow` `TvColors` usage; use `TvFocusable` for all rows.
6. `app-tv/src/main/java/com/example/calmsource/tv/ui/TvProfileSelectionScreen.kt` — replace `TvFocusCard` with `TvFocusable` for avatars/dialog actions.
7. `app-tv/src/main/java/com/example/calmsource/tv/ui/TvOnboardingScreen.kt` — migrate to `LocalLumenTokens`.
8. `app-tv/src/main/java/com/example/calmsource/tv/ui/TvAdvancedDebugScreen.kt` — migrate to `LocalLumenTokens`.
9. `app-tv/src/main/java/com/example/calmsource/tv/ui/TvIptvSettingsSection.kt` — migrate to tokens.
10. `app-tv/src/main/java/com/example/calmsource/tv/ui/TvExtensionSettingsSection.kt` — migrate to tokens.
11. `app-tv/src/main/java/com/example/calmsource/tv/ui/TvDebridSettingsSection.kt` — migrate to tokens.
12. `app-tv/src/main/java/com/example/calmsource/tv/ui/TvDiscoveryProvidersSettingsSection.kt` — migrate to tokens.
13. `app-tv/src/main/java/com/example/calmsource/tv/ui/TvPrioritiesSettingsSection.kt` — migrate to tokens.
14. `app-mobile/src/main/java/com/example/calmsource/ui/UiComponents.kt` — remove `AppColors`; migrate `GlassCard` to `LumenCard`/`GlassSurface` or delete it.
15. `app-mobile/src/main/java/com/example/calmsource/ui/LibraryScreen.kt` — migrate to `LocalLumenTokens` and add poster thumbnails.

**Verification:**
- `:app-mobile:assembleDebug :app-tv:assembleDebug` passes.
- `:app-mobile:detekt :app-tv:detekt` passes (or at least the existing baseline is maintained; the new rules can be tightened in the same PR if the wiring fix is small).
- `testDebugUnitTest --continue` passes.
- `rg "AppColors|TvColors" app-mobile/src app-tv/src` returns no matches.

**Expected visual impact:** One consistent cobalt focus language across TV; no more purple screens; the app immediately feels like a single design system. This unblocks Phases 2–6.

---

*Next step: after this plan is approved, begin with the recommended first PR above.*

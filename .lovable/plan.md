# Merge Plan: Lumen (web) UI → CalmSource (Kotlin) Mobile + TV

Goal: make the Android phone app (`app-mobile`) and Android TV app (`app-tv`) in the `Sehajuppal/calmsource` repo look and feel identical to the Lumen web app shipped here, without changing their data layer, ViewModels, repositories, or playback engines.

The Kotlin repo is the source of truth for behavior. The Lumen web app is the source of truth for visual language. The `merge-kit/` folder here is the bridge.

---

## What we are NOT doing

- Not rewriting ViewModels, repos, Room, ExoPlayer, Hilt graph, navigation, or Gradle modules.
- Not adding new features. Visual + interaction parity only.
- Not touching the Lovable web app — it stays the design reference.
- Not re-introducing Torrentio / Stremio naming — keep the legal-safe "Catalog add-ons" copy already in `merge-kit/`.

---

## Phase 0 — Pre-flight (still here, no code changes)

1. Confirm `merge-kit/` is current: `DESIGN-TOKENS.md`, `COMPONENT-MAP.md`, `SCREEN-CONTRACTS.md`, `INTERACTION-PARITY.md`, `PORT-ORDER.md`, `screenshots/`. Regenerate any stale screenshot from the live preview.
2. Confirm Kotlin repo health (from prior scan): delete the junk files (`$null`, `gradle.properties.bak`, `local_db.db`, `dummy.m3u`), remove the hardcoded Windows JDK path, and switch the hardcoded relay URL to BuildConfig before any UI refactor lands — otherwise every UI PR has to fight a broken build.

## Phase 1 — Design tokens in Compose (foundation)

Target files in the Kotlin repo:
- `app-mobile/src/main/java/com/example/calmsource/theme/Color.kt`
- `app-mobile/src/main/java/com/example/calmsource/theme/Theme.kt`
- `app-mobile/src/main/java/com/example/calmsource/theme/Type.kt`
- mirror under `app-tv/src/main/java/com/example/calmsource/tv/theme/` (create if missing)

Translate the Lumen tokens from `src/styles.css` into Compose:

```text
background    oklch(0.06 0.005 270)  → Color(0xFF0B0B10) ish
card          oklch(0.13 0.008 270)
muted         oklch(0.18 0.008 270)
muted-fg      oklch(0.68 0.012 270)
brand         oklch(0.62 0.22 260)   ← Electric Cobalt, primary accent
brand-glow    oklch(0.70 0.24 260)
destructive   oklch(0.62 0.22 25)
radius        1rem  → 16.dp (sm 12, md 14, lg 16, xl 20, 2xl 24)
font-display  Inter Tight
font-sans     Inter
```

Deliverables:
- A single `LumenTokens` object exposed as a `CompositionLocal` (`LocalLumenTokens`) with colors, radii, spacing scale (4/8/12/16/20/24/32/48), elevation, motion durations.
- A `LumenTheme { }` wrapper that sets `MaterialTheme` colors/typography from those tokens, plus an OLED variant matching `:root[data-theme="oled"]`.
- TV theme reuses the same tokens but swaps typography to TV display scale and exposes a `focus` color/halo token.

Acceptance: a placeholder screen rendered through `LumenTheme` matches a still from `merge-kit/screenshots/` at the eyeball level (bg, card, brand, text contrast).

## Phase 2 — Shared Compose primitive kit

New package `core/ui` (new Gradle module) OR a top-level `theme` + `components` package shared by mobile and TV. Pick `core/ui` since the repo already uses `core/*` modules.

Build these primitives, names mirror `merge-kit/COMPONENT-MAP.md`:

- `LumenSurface`, `LumenCard` (with the glass + 1px border + soft shadow)
- `PosterCard` (portrait + landscape variants, with badge slot, progress bar overlay, focus lift)
- `Hero` (full-bleed image, gradient scrim, title block, action row) — auto-rotation handled by caller; this is presentational only
- `RowSection` (title + horizontal `LazyRow`, optional "see all")
- `ChipRow` (mood/genre chips, glass style)
- `GlassTabBar` (mobile bottom nav, TV side rail variant)
- `PrimaryButton`, `GhostButton`, `IconPillButton` (adaptive contrast equivalent of `mix-blend-mode: difference` — implement as luminance-sampled overlay against the hero image)
- `EpisodeRow`, `StreamPickerSheet`, `PinPad`, `SettingsRow`, `EmptyState`, `ErrorState`, `Shimmer`/skeleton
- TV-only: `TvFocusable` wrapper that adds the focus halo + lift + scale spring, and a `FocusGroup` helper for D-pad memory.

Each primitive ships with a `@Preview` (phone) and `@Preview(device = Devices.TV_1080p)` matching the web screenshot.

## Phase 3 — Screen port order

Port screens one at a time. For each screen: keep the ViewModel + state flow untouched, swap the composable body to use Phase 2 primitives, and verify against the web reference.

Order (mirrors `merge-kit/PORT-ORDER.md`, condensed):

```text
1. ProfileGate         → app-mobile … (new ProfilesScreen) + app-tv TvProfileSelectionScreen
2. HomeScreen          (hero + rows + top10 + moods)
3. DetailsScreen       (hero header, metadata, episode list, stream picker entry)
4. PlayerScreen        (overlay chrome, track menu, subtitle UI — visual only)
5. LiveTvScreen        (channel grid + EPG rail) — TV gets TvLiveGuideScreen
6. LibraryScreen       (watchlist + continue watching)
7. SearchScreen        (⌘K-style search on mobile uses a full-screen sheet; TV uses on-screen keyboard pane)
8. SettingsScreens     (provider, IPTV/Xtream, catalog add-ons, PIN, profiles, about)
9. Onboarding / Pairing (TvOnboardingScreen, PairingViewModel UI)
10. AdvancedDebugScreen (low priority, last)
```

For each step: PR title `ui(port): <Screen> to Lumen`, contains only theme/component swaps, no behavior diff. Snapshot tests under `app-*/src/test/.../ui/` updated; Mission* regression tests must stay green.

## Phase 4 — Interaction parity

Pull from `merge-kit/INTERACTION-PARITY.md`:
- Card hover/focus = lift (scale 1.06, elevated shadow, top specular line), spring `stiffness=380, damping=28` — implement with `animateFloatAsState` + `Modifier.graphicsLayer`.
- Hero rotates every 7s, pauses on press / focus / reduced-motion.
- Row scroll snaps to nearest card on TV; free-scroll on phone.
- Bottom nav reveals on scroll-up, hides on scroll-down (mobile).
- Adaptive button contrast: sample hero image dominant luminance on `onSuccess` of Coil load, expose as a state, button picks white-on-dark or black-on-light token pair. (Cleaner than the web's `mix-blend-mode` trick on Compose.)
- Reduced-motion: read `Settings.Global.TRANSITION_ANIMATION_SCALE`; if 0, disable rotations / springs.

## Phase 5 — Mobile ↔ TV parity sweep

Walk every screen on both apps with the matching web screenshot side by side. Diff list goes in `docs/UI_PARITY_DIFF.md` (new). Anything not visually justified by form-factor (D-pad spacing, safe areas, TV overscan) is a bug and fixed before merge.

## Phase 6 — QA + ship

- Re-run existing regression suites: `Mission23*`, `Mission27*`, `*QaRegressionTest`, `*StartupFallbackTest`.
- Add Paparazzi/Showkase (or Roborazzi) snapshot tests for the 10 ported screens at phone + TV 1080p.
- Manual: low-end device profile (1GB RAM emulator) for jank check, since `merge-kit/INTERACTION-PARITY.md` calls out perf-mode.
- Update `docs/UI_BUG_FIX_REPORT.md` with before/after shots.
- Tag `v1.1.0-ui-lumen`.

---

## Risks / open questions

1. **Where does shared UI code live?** New `core/ui` Gradle module vs a `:shared-ui` module vs duplicating in both apps. Recommendation: new `core/ui` module that both `app-mobile` and `app-tv` depend on. Confirm before Phase 2.
2. **Font shipping.** Inter / Inter Tight need to be bundled as `assets/fonts/` and registered via `FontFamily`. Confirm OK to add ~600KB of font assets.
3. **Coil version.** Image loader must support crossfade + palette extraction for adaptive contrast. Confirm Coil 2.6+ is already on classpath (check `gradle/libs.versions.toml`).
4. **TV focus memory.** Repo already has `TvPressable`/focus helpers — Phase 2 should extend those, not replace, to avoid breaking `Mission27LiveParityTest`.
5. **How do you want me to deliver the changes?** I can't push to the Kotlin repo directly. Options: (a) generate a single `patches/` folder with one `.patch` per phase that you apply locally; (b) generate full file contents in chat for copy-paste; (c) you connect the GitHub app so I can open PRs.

---

## Deliverable per phase

| Phase | Output |
|---|---|
| 0 | confirmed merge-kit + green base build |
| 1 | `theme/` files for mobile + TV |
| 2 | `core/ui` module with primitives + previews |
| 3 | 10 screen ports, one PR each |
| 4 | interaction polish PR |
| 5 | parity diff doc, fix PRs |
| 6 | snapshot tests + tag |

Total realistic scope: ~2–3 focused build sessions per phase. Phases 1+2 unblock everything else and should land first.

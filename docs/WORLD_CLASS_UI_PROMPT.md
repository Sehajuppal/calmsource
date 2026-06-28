# CalmSource UI Deep Scan & World-Class Visual Implementation Plan

## Your mission

You are a **senior Android TV / mobile streaming UI architect**. Deep-scan the **CalmSource** monorepo and produce a **detailed, phased implementation plan** (not code yet) to elevate the Android **mobile** (`:app-mobile`) and **TV** (`:app-tv`) apps to **world-class, living-room quality** — visually and interactively on par with **Apple TV** and **Netflix**, while preserving all existing playback, IPTV, and Stremio extension behavior.

**Deliverable:** A structured plan document only. Do **not** implement unless explicitly asked in a follow-up. Do **not** add product features (no new catalogs, auth flows, or playback engines). **Visual shell, motion, typography, layout, and interaction polish only.**

---

## Product context

**CalmSource** is an Android IPTV + Stremio-style media app (phone + Fire TV / Android TV). Core value:

- Live TV / Xtream IPTV
- VOD from Stremio extensions (Torrentio, AIOStreams presets)
- Unified browse → details → player flows on mobile and TV

Read **`AGENTS.md`** at repo root before planning. Respect all product and code rules there (especially IPTV repository usage, no credential logging, mobile/TV behavioral pairing).

---

## Visual north star

Blend these references (not a pixel clone of either):

| Reference | Borrow |
|-----------|--------|
| **Apple TV** | True-black OLED canvas, icon sidebar that fades, **Top Shelf** hero, parallax focus lift, minimal chrome, white/subtle focus rings, cinematic typography |
| **Netflix** | Edge-to-edge hero with Play/Info, **poster-only rows** (metadata on focus), continue-watching progress, near-black UI, red reserved for primary CTAs only, immersive player chrome |

**Target feel:** Premium living-room streaming — calm, dark, content-first, zero “app template” branding on home.

---

## Known current state (verify & extend via scan)

Recent work landed on branch **`feat/lumen-phase-11-hard-parts`** (may be merged or ahead of `master` — check git). Assume:

### Design system (`:core:ui`)
- **`tokens/lumen.json`** → codegen → **`LumenTokens.generated.kt`** (single source of truth; do not fork)
- **Legacy bridge:** `LumenLegacyBridge.kt`, `LumenTokenExtensions.kt`, `LumenLegacySpace` (spacing drift vs generated tokens — apps may still use legacy names)
- **Primitives:** `GlassSurface` (RenderEffect blur API 31+), `TvFocusMemory`, `TvFocusable`, `Hero`, `GlassTabBar`, `PosterCard`, `LumenCard`, skeletons/empty states
- **Detekt token lint:** screen modules must not use raw `dp`/`sp`/`Color(0x…)` literals (allowlisted generated files)

### Feature modules
- **`:feature:epg`** — `EpgGrid` (2D virtualized guide)
- **`:feature:player`** — `PlayerChrome` + `PlayerChromeBindings.kt`

### Mobile (`app-mobile`)
- **Home:** rotating hero, mood chips, `GlassTabBar`, horizontal rows — structurally Netflix-like
- **Details:** backdrop hero + scrim
- **Search:** `GlassSurface` search bar
- **Player:** `PlayerChrome` overlay
- **Guide:** `EpgGrid`

### TV (`app-tv`)
- **Shell:** left **text** nav rail in `TvMainActivity.kt` (`TvNavRailItem`)
- **Home:** static “CalmSource / Your media sanctuary” header + labeled poster rows — **not** cinematic
- **Dual focus systems (critical debt):**
  - Lumen: `TvFocusable` (cobalt halo, ~1.06× scale) in browse screens
  - Legacy: `TvFocusCard` + `TvColors` in `TvUiComponents.kt` (**purple** `#8B5CF6` focus, different scale ~1.08×) — nav rail, settings, profiles, etc.
- **Guide / Search / Player:** wired to new components but layout still utilitarian
- **`TvFocusMemory`** on Home + Search rows

### Gaps vs world-class (hypotheses — confirm in scan)
1. Two incompatible TV focus/color systems
2. No TV top shelf / full-bleed hero
3. Poster titles always visible on TV (Netflix hides until focus)
4. App marketing copy on TV home
5. Mixed raw `sp`/`dp` vs `LumenType` / tokens on TV screens
6. Brand cobalt used everywhere vs restrained accent (Netflix red / Apple white)
7. `merge-kit/` docs and screenshots may be stale vs current Lumen tokens — reconcile
8. Mobile closer to target than TV

---

## What you must deep-scan

### 1. Documentation & references
```
AGENTS.md
tokens/lumen.json
merge-kit/DESIGN-TOKENS.md
merge-kit/COMPONENT-MAP.md
merge-kit/SCREEN-CONTRACTS.md
merge-kit/INTERACTION-PARITY.md
merge-kit/PORT-ORDER.md
merge-kit/screenshots/ (if present)
lovable-e738675c-*/merge-kit/ (if present — Lovable exports)
```

### 2. Code — design system
```
core/ui/src/main/kotlin/.../theme/
core/ui/src/main/kotlin/.../components/
core/ui/src/main/kotlin/.../tv/
detekt-rules/.../TokenLintRules.kt
```

### 3. Code — every screen (mobile + TV)
Inventory each screen composable; note: token usage, primitives used, raw literals, focus pattern, hero presence, row layout.

**Mobile:** `HomeScreen`, `DetailsScreen`, `PlayerScreen`, `SearchScreen`, `GuideScreen`, `LiveTvScreen`, `LibraryScreen`, `SettingsScreens`, `ProfilesScreen`, navigation shell

**TV:** `TvMainActivity` shell, `TvHomeScreen`, `TvDetailsScreen`, `TvPlayerScreen`, `TvSearchScreen`, `TvGuideScreen`, `TvLiveTvScreen`, `TvLibraryScreen`, `TvProfileSelectionScreen`, `TvSettingsScreen` + settings sections, `TvOnboardingScreen`, `TvUiComponents.kt`

### 4. Tests & guardrails
```
app-mobile/src/test/...MobileAppQaRegressionTest.kt
app-mobile/src/test/...Mission23MobileWiringTest.kt
app-tv/src/test/...TvAuditRegressionTest.kt
app-tv/src/test/...Mission23TvWiringTest.kt
```
Note what UI contracts tests enforce — plan must not break them without updating tests.

### 5. Build / verification commands (Windows)
```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app-mobile:assembleDebug :app-tv:assembleDebug --no-daemon
.\gradlew.bat :app-mobile:detekt :app-tv:detekt --no-daemon
.\gradlew.bat testDebugUnitTest --continue --no-daemon
```

### 6. Grep audits (run and tabulate results)
```bash
# Dual focus debt
rg "TvFocusCard|TvColors" app-tv/
rg "TvFocusable|rememberTvFocusMemory" app-tv/

# Token violations
rg "\d+\.dp|\d+\.sp|Color\(0x" app-mobile/ app-tv/ --glob "*.kt"

# Raw typography vs LumenType
rg "fontSize = \d+\.sp" app-tv/ app-mobile/

# Glass adoption
rg "GlassSurface|glassSurface" --glob "*.kt"

# Legacy spacing
rg "LumenLegacySpace" app-mobile/ app-tv/
```

---

## Required output structure

Produce a single markdown plan with these sections:

### A. Executive summary (½ page)
Current visual maturity score (1–10) for mobile and TV separately. Top 5 blockers to world-class. Recommended aesthetic direction (Apple-leaning vs Netflix-leaning vs hybrid — justify).

### B. Screen-by-screen audit matrix
Table columns:
| Screen | Platform | Current primitives | Visual debt (P0/P1/P2) | Reference pattern | Files to touch |

Cover **all** browse, details, player, settings, onboarding, profile, guide, live TV screens.

### C. Design system consolidation plan
- Unify `TvFocusCard`/`TvColors` → Lumen path (migration strategy, file list, order)
- Token changes needed in `tokens/lumen.json` (e.g. OLED black, focus ring color, optional Netflix red CTA token) — **json first, then codegen**
- Typography: eliminate raw `sp` on TV; map to `LumenType.*`
- Spacing: migrate `LumenLegacySpace` → generated `LumenTokens.Space` where safe
- Motion: which easings/springs for hero, focus parallax, row snap
- `GlassSurface` / `HeroBottomScrim` adoption map

### D. Phased implementation roadmap
**4–6 phases**, each with:
- Goal & user-visible outcome
- Exact files/modules per phase
- Mobile/TV pairing requirements
- Risk & regression areas (focus, D-pad order, detekt)
- Estimated effort (S/M/L/XL)
- Definition of done + verification commands
- Suggested PR boundaries (small reviewable PRs)

**Suggested phase themes (adapt after scan):**
1. TV focus/color unification
2. TV shell redesign (icon rail, remove home header, OLED black)
3. TV Top Shelf + poster-only rows + parallax
4. Mobile Netflix polish (continue watching, row density, tab bar)
5. Details & player cinematic pass (both platforms)
6. Parity sweep + screenshot/Roborazzi golden tests

### E. Component gap analysis
List missing shared components needed for world-class UI (e.g. `TopShelf`, `ContinueWatchingCard`, `TvIconNavRail`, `ParallaxPoster`, `MetadataOnFocusOverlay`). For each: API sketch, module (`core/ui` vs `feature/*`), reuse vs new.

### F. Token & lint migration checklist
Step-by-step for editors/agents: edit `lumen.json` → regen → alias pass scripts if any → fix detekt → build both apps.

### G. Non-goals & constraints
Explicitly exclude: new playback features, extension protocol changes, Astro/site, database schema changes, fake catalog seeding.

### H. Open questions for product owner
Only genuine ambiguities (e.g. brand color: keep cobalt vs Netflix red vs neutral; TV profile gate placement).

---

## Quality bar for the plan

- **Specific:** file paths, composable names, token keys — not vague “improve home screen”
- **Prioritized:** P0 = blocks world-class perception; P2 = polish
- **Paired:** every TV browse change notes mobile equivalent or explicit exception
- **Testable:** each phase has measurable DoD (build, detekt, focus restoration, grep counts)
- **Incremental:** no “rewrite all UI in one PR”

---

## Success criteria (for the eventual implementation, not this planning task)

When implemented, a reviewer on a 55" TV at 10 feet should feel:
- Content is the UI; chrome disappears
- Focus is predictable and beautiful (one system, parallax/lift)
- Home has a cinematic hero (Top Shelf) before rows
- Posters dominate; text appears when needed
- Player and details feel theatrical (full-bleed art, glass controls)
- Mobile and TV feel like the same brand, different form factor

---

## Start command

1. Read `AGENTS.md`
2. Run the grep audits and build once to confirm repo health
3. Walk every screen file listed above
4. Produce the plan markdown (**save as `docs/WORLD_CLASS_UI_PLAN.md`** if the repo has a `docs/` folder; otherwise output in chat only)
5. End with a **recommended first PR** (≤15 files, highest visual ROI)

Do not write implementation code in this pass. Plan only.

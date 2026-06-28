# CalmSource UI Deep Scan & World-Class Visual Implementation Plan

## Your mission

You are a **senior Android TV / mobile streaming UI architect**. Deep-scan the **CalmSource** monorepo and produce a **detailed, phased implementation plan** (not code yet) to elevate the Android **mobile** (`:app-mobile`) and **TV** (`:app-tv`) apps to **world-class, living-room quality** — visually and interactively on par with **Apple TV** and **Netflix**, while preserving all existing playback, IPTV, and Stremio extension behavior.

**Deliverable:** Write the plan to the repo at the paths below. Do **not** implement UI code unless explicitly asked in a follow-up. Do **not** add product features (no new catalogs, auth flows, or playback engines). **Visual shell, motion, typography, layout, and interaction polish only.**

---

## Required output location (mandatory)

Create or overwrite these files under **`docs/ui-world-class/`**:

| Path | Required | Contents |
|------|----------|----------|
| **`docs/ui-world-class/IMPLEMENTATION_PLAN.md`** | **Yes** | Main deliverable — full plan (sections A–H below) |
| `docs/ui-world-class/SCREEN_AUDIT.md` | If main plan exceeds ~400 lines | Screen-by-screen audit matrix only |
| `docs/ui-world-class/TOKEN_MIGRATION.md` | If token work is substantial | Token/lint migration checklist only |

**Rules:**
- Do not write the plan only in chat — it must land in **`IMPLEMENTATION_PLAN.md`**.
- Use relative links from the plan to relevant source files (e.g. `` `app-tv/.../TvHomeScreen.kt` ``).
- If you split appendices, link them from the top of `IMPLEMENTATION_PLAN.md`.
- Do not add unrelated docs elsewhere in the repo.

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

Recent work may be on branch **`feat/lumen-phase-11-hard-parts`** (check git vs `master`). Assume:

### Design system (`:core:ui`)
- **`tokens/lumen.json`** → codegen → **`LumenTokens.generated.kt`** (single source of truth; do not fork)
- **Legacy bridge:** `LumenLegacyBridge.kt`, `LumenTokenExtensions.kt`, `LumenLegacySpace`
- **Primitives:** `GlassSurface`, `TvFocusMemory`, `TvFocusable`, `Hero`, `GlassTabBar`, `PosterCard`, `LumenCard`, skeletons/empty states
- **Detekt token lint:** screen modules must not use raw `dp`/`sp`/`Color(0x…)` literals

### Feature modules
- **`:feature:epg`** — `EpgGrid`
- **`:feature:player`** — `PlayerChrome` + `PlayerChromeBindings.kt`

### Mobile (`app-mobile`)
- Home: rotating hero, mood chips, `GlassTabBar`, horizontal rows
- Details: backdrop hero + scrim
- Search: `GlassSurface` search bar
- Player: `PlayerChrome`
- Guide: `EpgGrid`

### TV (`app-tv`)
- Shell: left **text** nav rail in `TvMainActivity.kt`
- Home: static “CalmSource / Your media sanctuary” header + labeled poster rows
- **Dual focus systems:** `TvFocusable` (Lumen cobalt) vs `TvFocusCard` + `TvColors` (purple, `TvUiComponents.kt`)
- Guide / Search / Player wired to new components; layout still utilitarian
- `TvFocusMemory` on Home + Search rows

### Gaps vs world-class (confirm in scan)
1. Two incompatible TV focus/color systems
2. No TV top shelf / full-bleed hero
3. Poster titles always visible on TV
4. App marketing copy on TV home
5. Mixed raw `sp`/`dp` vs `LumenType` on TV
6. Brand cobalt everywhere vs restrained accent
7. `merge-kit/` docs/screenshots may be stale vs current tokens

---

## What you must deep-scan

### 1. Documentation & references
```
AGENTS.md
docs/ui-world-class/README.md
tokens/lumen.json
merge-kit/DESIGN-TOKENS.md
merge-kit/COMPONENT-MAP.md
merge-kit/SCREEN-CONTRACTS.md
merge-kit/INTERACTION-PARITY.md
merge-kit/PORT-ORDER.md
merge-kit/screenshots/
```

### 2. Code — design system
```
core/ui/src/main/kotlin/.../theme/
core/ui/src/main/kotlin/.../components/
core/ui/src/main/kotlin/.../tv/
detekt-rules/.../TokenLintRules.kt
feature/epg/
feature/player/
```

### 3. Code — every screen (mobile + TV)
**Mobile:** `HomeScreen`, `DetailsScreen`, `PlayerScreen`, `SearchScreen`, `GuideScreen`, `LiveTvScreen`, `LibraryScreen`, `SettingsScreens`, `ProfilesScreen`, navigation shell

**TV:** `TvMainActivity`, `TvHomeScreen`, `TvDetailsScreen`, `TvPlayerScreen`, `TvSearchScreen`, `TvGuideScreen`, `TvLiveTvScreen`, `TvLibraryScreen`, `TvProfileSelectionScreen`, `TvSettingsScreen` + settings sections, `TvOnboardingScreen`, `TvUiComponents.kt`

### 4. Tests & guardrails
```
app-mobile/src/test/...MobileAppQaRegressionTest.kt
app-mobile/src/test/...Mission23MobileWiringTest.kt
app-tv/src/test/...TvAuditRegressionTest.kt
app-tv/src/test/...Mission23TvWiringTest.kt
```

### 5. Build verification (Windows)
```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app-mobile:assembleDebug :app-tv:assembleDebug --no-daemon
.\gradlew.bat :app-mobile:detekt :app-tv:detekt --no-daemon
.\gradlew.bat testDebugUnitTest --continue --no-daemon
```

### 6. Grep audits (tabulate in plan)
```bash
rg "TvFocusCard|TvColors" app-tv/
rg "TvFocusable|rememberTvFocusMemory" app-tv/
rg "\d+\.dp|\d+\.sp|Color\(0x" app-mobile/ app-tv/ --glob "*.kt"
rg "fontSize = \d+\.sp" app-tv/ app-mobile/
rg "GlassSurface|glassSurface" --glob "*.kt"
rg "LumenLegacySpace" app-mobile/ app-tv/
```

---

## Required content in `IMPLEMENTATION_PLAN.md`

### A. Executive summary
Current visual maturity (1–10) for mobile and TV. Top 5 blockers. Recommended direction (Apple / Netflix / hybrid).

### B. Screen-by-screen audit matrix
| Screen | Platform | Current primitives | Debt (P0/P1/P2) | Reference pattern | Files to touch |

### C. Design system consolidation plan
Unify TV focus systems, `tokens/lumen.json` changes, typography, spacing, motion, glass adoption.

### D. Phased implementation roadmap
4–6 phases with goals, files, mobile/TV pairing, risks, effort (S/M/L/XL), DoD, suggested PR boundaries.

### E. Component gap analysis
Missing shared components (`TopShelf`, `ContinueWatchingCard`, `TvIconNavRail`, etc.) with API sketches and module placement.

### F. Token & lint migration checklist
`lumen.json` → codegen → detekt → build both apps.

### G. Non-goals & constraints
No playback/features/schema changes per `AGENTS.md`.

### H. Open questions for product owner
Genuine ambiguities only (brand color, profile gate, etc.).

### I. Recommended first PR
≤15 files, highest visual ROI — must be the last section before any appendices.

---

## Quality bar

- **Specific:** file paths, composable names, token keys
- **Prioritized:** P0 / P1 / P2
- **Paired:** mobile + TV for browse/details/player
- **Testable:** build, detekt, grep counts per phase
- **Incremental:** no single mega-PR

---

## Success criteria (for future implementation)

On a 55" TV at 10 feet: content-first UI, one focus system, cinematic home hero, poster-dominant rows, theatrical details/player, consistent brand across form factors.

---

## Start command

1. Read `AGENTS.md` and `docs/ui-world-class/README.md`
2. Run grep audits and a debug build
3. Walk every screen listed above
4. **Write `docs/ui-world-class/IMPLEMENTATION_PLAN.md`** (and optional appendices)
5. Reply in chat with a 5–10 line summary + link to the plan file path

**Do not write implementation code in this pass. Plan files only.**

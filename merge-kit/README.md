# Merge Kit — Lumen → CalmSource (Kotlin)

This kit is the **single source of truth** for porting the Lumen reference UI
(web + Expo mobile, this repo) onto the CalmSource Android codebase
(`c:\Users\Sehaj\Desktop\iptv`).

## Rule of the road
- Lumen = **frozen UI/UX contract**.
- CalmSource = **owns all backend** (IPTV, Room, ExoPlayer, extensions).
- Never import Lumen TS into Gradle. Never use Lumen demo data
  (`catalog.ts`, `FakeData.liveChannels`) in production Kotlin.
- If Lumen mobile and Lumen web disagree, **mobile + CalmSource win**
  (native is the ship target).

## How to use this kit
1. Read `DESIGN-TOKENS.md` → implement in `:core:design` (colors, type, dims).
2. Read `COMPONENT-MAP.md` → one Kotlin composable per Lumen building block.
3. Read `SCREEN-CONTRACTS.md` → only swap `*Content.kt` bodies; keep ViewModels.
4. Read `DATA-CONTRACTS.md` → map ViewModel output to UI state via Lumen field names.
5. Read `INTERACTION-PARITY.md` → user flows that MUST match.
6. Follow `PORT-ORDER.md` step-by-step. Build both apps + run tests after each step.

## Files
| File | Purpose |
| --- | --- |
| `DESIGN-TOKENS.md` | Colors, typography, radii, tile dims (phone + TV), focus ring |
| `COMPONENT-MAP.md` | Every Lumen UI primitive → Kotlin composable name |
| `SCREEN-CONTRACTS.md` | Per-screen state in / callbacks out |
| `DATA-CONTRACTS.md` | Lumen `Title`/`IPTVChannel`/`userdata` ↔ CalmSource models |
| `INTERACTION-PARITY.md` | Flows that must behave the same across web/mobile/Kotlin |
| `PORT-ORDER.md` | Exact ticket order for the Kotlin port agent |
| `screenshots/` | (Optional) Lumen reference screens |

## Lumen reference location
After Phase A, the Lumen sources are copied into the CalmSource repo at:
```
c:\Users\Sehaj\Desktop\iptv\design-reference\lumen\
```
See `design-reference/lumen/VERSION.txt` for the snapshot date.

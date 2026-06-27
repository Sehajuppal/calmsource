# Design Tokens

Canonical values extracted from `src/styles.css`, `mobile/src/components/theme.ts`,
and `mobile/src/lib/tv.ts`. These are the values to implement in `:core:design`
as `LumenColors`, `LumenTypography`, `LumenDimens`.

## Colors (hex / rgba)
| Token | Value | Notes |
| --- | --- | --- |
| `bg` | `#0d0d14` | Midnight background (web `--background`, mobile `colors.bg`) |
| `card` | `rgba(255,255,255,0.04)` | Default surface |
| `border` | `rgba(255,255,255,0.10)` | Hairline divider |
| `borderStrong` | `rgba(255,255,255,0.18)` | Focused / emphasized border |
| `text` | `#ffffff` | Primary text |
| `textMuted` | `rgba(255,255,255,0.60)` | Secondary text |
| `textDim` | `rgba(255,255,255,0.45)` | Eyebrow / meta |
| `accent` | `#2E5BFF` | **Electric Cobalt** — primary brand. Web `oklch(0.62 0.20 264)`. |
| `accentForeground` | `#ffffff` | Text/icon on accent |
| `accentGlow` | `rgba(46,91,255,0.45)` | Focus glow, hero CTA shadow |

## Typography scale
Display family: `Inter` (web) / system display (mobile). Track tight on display sizes.

| Token | Size | Weight | Letter spacing |
| --- | --- | --- | --- |
| `display` | 40 | 700 | -0.8 |
| `h1` | 34 | 700 | -0.6 |
| `h2` | 22 | 700 | -0.3 |
| `rowTitle` | 17 | 700 | -0.2 |
| `body` | 14 | 400 | 0 |
| `meta` | 12 | 500 | 0.2 |
| `eyebrow` | 11 | 700 | 1.5 (uppercase) |

## Radii
| Token | dp |
| --- | --- |
| Tile | 18 |
| Card | 16 |
| Pill / button | 999 (full) |
| Input | 12 |

## Tile dimensions
### Phone (mobile)
| Variant | W × H |
| --- | --- |
| Portrait poster | 150 × 220 |
| Landscape tile | 260 × 150 |
| Row gap | 12 dp |
| Side padding | 20 dp |

### TV (10-foot)
| Variant | W × H |
| --- | --- |
| Portrait poster | 220 × 320 |
| Landscape tile | 380 × 215 |
| Row gap | 24 dp |
| Side padding | 48 dp |

## Focus ring (TV)
- Stroke: `accent` (#2E5BFF), 3 dp.
- Outer glow: `accentGlow`, 16 dp blur.
- Scale-on-focus: 1.08, 180 ms ease-out.
- Outline radius matches tile radius (18 dp).

## Hero min-heights
| Surface | Min height |
| --- | --- |
| Phone home hero | 480 dp |
| Phone details hero | 360 dp |
| TV home hero | 720 dp |
| TV details hero | 540 dp |

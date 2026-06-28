# Token & Lint Migration Checklist

This appendix supports the token and lint migration section of [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md).

## 1. `tokens/lumen.json` — additions only if needed

The existing token set already covers the core visual language (colours, typography, spacing, motion, radii, tile sizes, hero sizes). Add new tokens only when new components introduce genuinely new dimensions:

| Proposed new token group | Why | Suggested keys |
|----------------------------|-----|----------------|
| `navRail` | TV icon nav rail needs collapsed/expanded widths and icon size. | `collapsedWidth: 80dp`, `expandedWidth: 240dp`, `iconSize: 24dp` |
| `posterRow` | Shared row behaviour (snap, fade, title visibility). | `fadeWidth: 56px` (already in `scrim.rowFadeWidthPx`), `peekOffset: 24dp` |
| `topShelf` | Already covered by `hero.minHeight.tv` and `scrim` gradients; no new tokens needed unless a video-specific overlay is added. | — |
| `controlScrim` | `AdaptiveButton` and player overlays use semi-transparent dark/light scrims. | `controlScrimDark: rgba(11,11,16,0.80)`, `controlScrimLight: rgba(250,250,250,0.80)` |

Add these to `tokens/lumen.json` and then regenerate `LumenTokens.generated.kt`. Do not hand-edit the generated file.

## 2. Regenerate `LumenTokens.generated.kt`

- The generated file is at `core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenTokens.generated.kt`.
- It is the single source of truth; re-run the project’s codegen script (or the token-studio / custom generator) after any `tokens/lumen.json` change.
- Verification: `grep -R "LumenTokens.Color.brand" core/ui/src/main/kotlin` should still resolve after regeneration.

## 3. Fix the detekt rule wiring

The custom `lumen-tokens` rule set is implemented in `detekt-rules/src/main/kotlin/.../core/ui/lint/TokenLintRules.kt` and registered in `detekt-rules/src/main/resources/META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`. However, `:app-mobile:detekt` currently reports **0 code smells** despite hundreds of raw `dp`/`sp`/`Color(0x…)` literals in screen files. Before the rules can be used as a gate, verify and fix the wiring:

- [ ] Add a temporary unit test or an offending snippet to prove the rules actually fire. If they do not, debug service-loader registration / `RuleSetProvider` construction.
- [ ] Add a new rule `ForbiddenScreenSpLiteral` to catch raw `fontSize = …sp` in screen files.
- [ ] Confirm `ForbiddenScreenDpLiteral` matches real expressions. The current anchor-based regex (`^…\.dp$`) looks correct, but the fact that it reports nothing needs confirmation.
- [ ] Keep the existing exclusions for `LumenTokens.generated.kt` and `GlassSurface.kt`.
- [ ] Consider whether `SettingsScreens.kt` (plural) should be included; the current screen-file regex `(Screen|Section)\.kt$` matches `Screens.kt` because it ends with `ens.kt`? Actually `Screens.kt` ends with `ens.kt` and does **not** match `Screen.kt`. Check and broaden the regex if needed, e.g. `(Screen|Screens|Section)\.kt$`.

## 4. Screen-level migration checklist

For every screen file (`*Screen.kt`, `*Screens.kt`, `*Section.kt`):

- [ ] Remove all `AppColors` and `TvColors` references; replace with `LocalLumenTokens.current`.
- [ ] Replace raw `Color(0x…)` with `LumenTokens.Color.*`, `LumenExtendedColors.*`, or `Color.Transparent` / `Color.White` / `Color.Black` only where semantically required.
- [ ] Replace raw `dp` with `LumenTokens.Space.*` / `LumenLayout.*` / `LumenTokens.Radius.*` / `LumenTokens.Tile.*`. Allow only `0.dp` and `1.dp` as exceptions (already in the detekt rule).
- [ ] Replace raw `sp` with `MaterialTheme.typography.*` or `LumenType.*.toTextStyle()`.
- [ ] Replace `MaterialTheme.colorScheme` with `t.colors.*`.
- [ ] Replace `RoundedCornerShape(...)` with `LumenTokens.Shape.*`.
- [ ] Replace `LumenLegacySpace` with `LumenTokens.Space` semantic aliases or direct `sN` values.

## 5. Legacy cleanup

- [ ] Confirm `app-mobile/src/main/java/com/example/calmsource/theme/Color.kt`, `Theme.kt`, and `Type.kt` have zero references, then delete them.
- [ ] Delete the `AppColors` object from `app-mobile/src/main/java/com/example/calmsource/ui/UiComponents.kt` once all usages are removed.
- [ ] Delete the `TvColors` object from `app-tv/src/main/java/com/example/calmsource/tv/ui/TvUiComponents.kt` once all usages are removed.
- [ ] Keep `LumenLegacyBridge.kt` and `LumenTokenExtensions.kt` only if they contain values that are intentionally not in `tokens/lumen.json`; otherwise fold them into the token system.

## 6. Typography mapping reference

Use this mapping when replacing raw `fontSize = …sp`:

| Lumen token | Compose slot | Typical usage |
|-------------|--------------|---------------|
| `LumenType.Display` | `MaterialTheme.typography.displayLarge` | Hero title |
| `LumenType.H1` | `MaterialTheme.typography.displayMedium` | Screen title |
| `LumenType.H2` | `MaterialTheme.typography.displaySmall` / `headlineLarge` | Sheet title |
| `LumenType.Title` | `MaterialTheme.typography.headlineMedium` | Card title |
| `LumenType.RowTitle` | `MaterialTheme.typography.titleMedium` | Row header |
| `LumenType.Body` | `MaterialTheme.typography.bodyLarge` | Body text |
| `LumenType.Caption` | `MaterialTheme.typography.bodyMedium` | Captions |
| `LumenType.Meta` | `MaterialTheme.typography.labelLarge` | Meta / duration |
| `LumenType.Eyebrow` | `MaterialTheme.typography.labelSmall` | Uppercase labels |

TV call sites should use `LumenType.*.toTextStyle(scale = LumenType.TV_SCALE)` or rely on `LumenTheme` which already scales the Material slots.

## 7. Verification commands

Run these after each migration batch:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"

.\gradlew.bat :app-mobile:assembleDebug :app-tv:assembleDebug --stacktrace --no-daemon --console=plain
.\gradlew.bat :app-mobile:detekt :app-tv:detekt --no-daemon --console=plain
.\gradlew.bat testDebugUnitTest --continue --no-daemon --console=plain
```

And the grep audits from the brief:

```powershell
Get-ChildItem -Path app-tv -Recurse -Filter *.kt | Select-String -Pattern 'TvFocusCard|TvColors'
Get-ChildItem -Path app-tv -Recurse -Filter *.kt | Select-String -Pattern 'TvFocusable|rememberTvFocusMemory'
Get-ChildItem -Path app-mobile,app-tv -Recurse -Filter *.kt | Select-String -Pattern '(\d+\.dp|\d+\.sp|Color\(0x)'
Get-ChildItem -Path app-mobile,app-tv -Recurse -Filter *.kt | Select-String -Pattern 'fontSize = \d+\.sp'
Get-ChildItem -Path app-mobile,app-tv -Recurse -Filter *.kt | Select-String -Pattern 'GlassSurface|glassSurface'
Get-ChildItem -Path app-mobile,app-tv -Recurse -Filter *.kt | Select-String -Pattern 'LumenLegacySpace'
```

## 8. Success criteria

- `rg "AppColors|TvColors" app-mobile/src app-tv/src` returns no matches.
- Raw `dp`/`sp`/`Color(0x…)` counts in screen files trend to zero (allowing `0.dp`, `1.dp`, `Color.Transparent`/`Color.White`/`Color.Black`).
- `:app-mobile:detekt` and `:app-tv:detekt` run the custom `lumen-tokens` rules and fail on violations.
- `:app-mobile:assembleDebug`, `:app-tv:assembleDebug`, and `testDebugUnitTest` all pass.
- Both apps use one focus system (`TvFocusable`) and one accent colour (`LumenTokens.Color.brand`).

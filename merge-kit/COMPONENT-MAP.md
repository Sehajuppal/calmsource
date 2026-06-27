# Component Map

Every Lumen UI primitive maps to exactly one Kotlin composable in `:core:design`.
Build composables with `@Preview` for both phone and TV form factors where noted.

| Lumen component | Source file | Kotlin composable (`:core:design`) | Used on |
| --- | --- | --- | --- |
| TopNav | `src/routes/index.tsx` | `GlassTopBar` | Shell (all screens) |
| BottomTabBar | `mobile/src/screens/HomeScreen.tsx` | `MobileTabBar` | Mobile shell |
| Hero | `src/routes/index.tsx` | `HeroBanner` | Home, Films, Series |
| HeroMeta | `src/routes/index.tsx` | `HeroMetaRow` | Home, Details |
| PosterCard | `src/routes/index.tsx`, `mobile/HomeScreen.tsx` | `PosterTile` | Home, Movies, Series, Search |
| LandscapeCard | `src/routes/index.tsx` | `LandscapeTile` | Continue watching, Live preview |
| ContentRow | `src/routes/index.tsx` | `ContentRow` | Home, Films, Series |
| RowHeader | `src/routes/index.tsx` | `RowHeader` | All rows |
| EyebrowPill | `mobile/src/components/EyebrowPill.tsx` | `EyebrowPill` | Hero, sections |
| MoodChip | `src/routes/index.tsx` | `MoodChip` | Home moods strip |
| TopTenTile | `src/routes/index.tsx` | `TopTenTile` | Home "Top 10" |
| LeavingSoonCard | `src/routes/index.tsx` | `LeavingSoonCard` | Home "Leaving soon" |
| ChannelTile | `src/components/LiveTV.tsx`, `mobile/LiveTVScreen.tsx` | `ChannelTile` | Live TV grid |
| ChannelRow (now/next) | `src/components/LiveTV.tsx` | `ChannelEpgRow` | Live TV list |
| FilterBar | `src/components/FilterBar.tsx`, `mobile/FilterBar.tsx` | `FilterChipRow` | Films, Series, Live |
| SearchCommand (⌘K) | `src/components/SearchCommand.tsx` | `SearchOverlay` | Web shell |
| SearchScreen | `mobile/src/screens/SearchScreen.tsx` | `SearchScreenContent` | Mobile |
| DetailsModal / Sheet | `src/components/DetailsModal.tsx`, `mobile/DetailsSheet.tsx` | `DetailsScreenContent` | Details |
| EpisodeList | `src/components/EpisodeList.tsx` | `EpisodeList` | Details (series) |
| StreamPicker | `src/components/StreamPicker.tsx` | `StreamPickerSheet` | Details |
| Player chrome | `src/components/Player.tsx`, `mobile/Player.tsx` | `PlayerChrome` | Player (wraps ExoPlayer) |
| MiniPlayer | `src/components/Player.tsx` | `MiniPlayerBar` | Shell |
| ProfileGate | `src/components/ProfileGate.tsx`, `mobile/ProfileGate.tsx` | `ProfileGateScreen` | Boot |
| PinDialog / PinSheet | `src/components/PinDialog.tsx`, `mobile/PinSheet.tsx` | `PinEntrySheet` | Profile gate |
| SettingsPanel | `src/components/SettingsPanel.tsx`, `mobile/SettingsScreen.tsx` | `SettingsScreenContent` | Settings |
| EmptyState | `src/components/EmptyState.tsx` | `EmptyStateBlock` | All empty surfaces |
| ErrorBoundary fallback | `src/components/ErrorBoundary.tsx`, `mobile/AppErrorBoundary.tsx` | `ErrorFallback` | Shell |

## TV-only variants
| Composable | Notes |
| --- | --- |
| `TvFocusRing` | Reusable focus decoration per `DESIGN-TOKENS.md` "Focus ring" |
| `TvPosterTile` | Scales 1.08 on focus, adds ring |
| `TvLandscapeTile` | Same focus behavior, landscape aspect |
| `TvContentRow` | Edge-anchored snap, leading 48 dp gutter |
| `TvHeroBanner` | 720 dp min-height, deeper vignette |
| `scrimGradient` | `LumenTokens.kt` | Extension function to apply vertical gradient for backdrop hero scrim |
| `glassSurface` | `LumenTokens.kt` | Extension function to apply glassmorphic backdrop filter and translucent background |

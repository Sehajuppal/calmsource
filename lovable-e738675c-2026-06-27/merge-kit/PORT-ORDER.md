# Port Order

Exact ticket order for the Kotlin UI port agent. Each step:
- Build both apps: `:app-mobile:assembleDebug` + `:app-tv:assembleDebug`.
- Run wiring tests: `testDebugUnitTest --continue`.
- Visually diff against `design-reference/lumen/` and Lumen mobile.

1. **`CalmSourceTheme` at app root** (mobile + TV).
   - Implement `LumenColors`, `LumenTypography`, `LumenDimens` from
     `DESIGN-TOKENS.md` in `:core:design`.
   - Wrap `setContent { CalmSourceTheme { … } }` in both Activities.

2. **`HomeScreenContent`** ← Lumen Home (`src/routes/index.tsx` HomeTab,
   `mobile/HomeScreen.tsx`).
   - State shape from `SCREEN-CONTRACTS.md` § Home.
   - Wire to existing `HomeViewModel.homeRows`. No new business logic.

3. **`TvHomeScreenContent`** ← Lumen TV home.
   - Use `TvHeroBanner`, `TvContentRow`, `TvPosterTile`.
   - Focus engine: leading row anchor + 1.08 scale + ring.

4. **`LiveTvScreenContent`** ← Lumen LiveTV
   (`src/components/LiveTV.tsx`, `mobile/LiveTVScreen.tsx`).
   - Source: `IPTVRepository.getLiveChannels()`.
   - Now/next via existing EPG repo.

5. **`TvLiveGuideScreenContent`** ← TV live guide grid.

6. **`DetailsScreenContent`** ← `DetailsModal.tsx` / `DetailsSheet.tsx`.
   - Episode list, watchlist toggle, stream picker entry.

7. **`PlayerScreen` chrome only** (keep ExoPlayer).
   - Match `PlayerChrome` controls layout. Mini bar via `MiniPlayerBar`.

8. **`SearchScreenContent`** ← `SearchCommand` / `SearchScreen.tsx`.

9. **Settings restyle** ← `SettingsPanel.tsx` / `SettingsScreen.tsx`.
   - Sections: Profile, Preferences, Catalog add-ons, IPTV, EPG, Data.
   - No Torrentio/AIOStreams/Stremio in user copy.

10. **Profile gate** ← `ProfileGate.tsx` / mobile equivalent.
    - PIN entry via `PinEntrySheet`.

## Stop conditions
- Stop and ask before changing `Navigation.kt` structure.
- Stop and ask before deleting `StremioAddonClient` (it's the addon
  protocol implementation — only the *user-visible naming* needs to change).
- Stop and ask before introducing any new dependency beyond what's already
  in `app-mobile` / `app-tv` / `:core:design`.

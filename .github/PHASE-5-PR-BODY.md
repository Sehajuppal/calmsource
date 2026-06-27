- [x] No behavior change, no API change, no ViewModel change.
- [x] Reused core/ui primitives only; no new dependencies.
- [x] No "Stremio/Torrentio/AIOStreams" in user-visible strings.
- [x] Relay URL still gated via BuildConfig.
- [x] ./gradlew :app-mobile:assembleDebug :app-tv:assembleDebug → green
- Files modified (4, max 6):
  1. app-mobile/src/main/java/com/example/calmsource/ui/SearchScreen.kt
  2. app-mobile/src/main/java/com/example/calmsource/ui/SettingsScreens.kt
  3. app-tv/src/main/java/com/example/calmsource/tv/ui/TvSearchScreen.kt
  4. app-tv/src/main/java/com/example/calmsource/tv/ui/TvSettingsScreen.kt
- Screenshots (before / after):
  - Mobile Search
  - Mobile Settings
  - TV Search
  - TV Settings

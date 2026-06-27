- [x] No behavior change, no API change, no ViewModel change.
- [x] Reused core/ui primitives only; no new dependencies.
- [x] No "Stremio/Torrentio/AIOStreams" in user-visible strings.
- [x] Relay URL still gated via BuildConfig.
- [x] ./gradlew :app-mobile:assembleDebug :app-tv:assembleDebug → green
- Files modified (4, max 6):
  1. app-mobile/src/main/java/com/example/calmsource/ui/LiveTvScreen.kt
  2. app-mobile/src/main/java/com/example/calmsource/ui/GuideScreen.kt
  3. app-tv/src/main/java/com/example/calmsource/tv/ui/TvLiveTvScreen.kt
  4. app-tv/src/main/java/com/example/calmsource/tv/ui/TvGuideScreen.kt
  5. app-tv/src/main/java/com/example/calmsource/tv/ui/TvLiveGuideScreen.kt
- Screenshots (before / after):
  - Mobile LiveTV
  - Mobile Guide
  - TV LiveTV
  - TV Guide

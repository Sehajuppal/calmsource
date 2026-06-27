# Interaction Parity

Flows that **must** behave the same across Lumen web, Lumen mobile, and
CalmSource Kotlin. When web and mobile disagree, **mobile + CalmSource win**.

| Flow | Lumen web | Lumen mobile | CalmSource Kotlin |
| --- | --- | --- | --- |
| Cold start | ProfileGate → Home | ProfileGate → Home | Profile / boot → Home |
| Play VOD | Global `<Player>` overlay (full→mini) | `<Player>` root with mini bar | `PlayerScreen` (full→PIP/mini) |
| Live channel tap | `onPlayChannel` → `usePlayer.open({kind:"live"})` | `usePlayer.open({kind:"live"})` | `buildLivePlaybackRequest(channel)` |
| Continue watching | `userdata.continueWatching` row | same | `UserMemoryRepository.continueWatching()` |
| Watchlist toggle | `userdata.watchlist.toggle()` | same | `WatchlistRepository.toggle()` |
| Empty home | Onboarding CTA → Settings | Onboarding CTA → Settings | Empty state → add-on/IPTV setup |
| Search | ⌘K overlay | Search tab | Search tab/screen |
| Open Details | Modal over current page | Bottom sheet | Full screen (mobile) / overlay (TV) |
| Pick stream | `StreamPicker` modal | `StreamPicker` sheet | `StreamPickerSheet` |
| Minimize player | Click chrome / Esc | Drag down / back | Back / PIP |
| Resume | Use saved `position` from userdata | same | `UserMemoryRepository.resume(mediaId)` |
| Profile switch | Header avatar → ProfileGate | Settings → switch | Settings → switch |
| PIN entry | `PinDialog` | `PinSheet` | `PinEntrySheet` |
| Reduce motion | Pref disables transitions / autoplay previews | same | Honor `Animator.areAnimatorsEnabled()` + pref |

## Rules
1. **Native is canonical**. When in doubt, match `mobile/` over `web/`.
2. **No fake content in production**. Lumen's `catalog.ts` is DEMO ONLY;
   the Kotlin app must source rows from `HomeViewModel` (extensions + IPTV).
3. **One player, two surfaces**. Full and mini share the same store
   (`usePlayer` → `PlayerStateHolder` in Kotlin). Mini bar persists across
   tab switches.
4. **Empty states are first-class screens**, not afterthoughts. Every list
   that can be empty has an `EmptyStateBlock` with a primary CTA.
5. **Add-on add-flow is generic**. Paste-URL is primary; presets are
   secondary chips. No one-tap install of stream/torrent aggregators in
   user-facing UI.

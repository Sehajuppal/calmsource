# Screen Contracts

Per-screen state in / callbacks out. The Kotlin port keeps existing ViewModels
and only swaps the body of `*Content.kt` to match the Lumen layout described
here.

---

## Home
- **Lumen files**: `src/routes/index.tsx` (HomeTab), `mobile/src/screens/HomeScreen.tsx`
- **Kotlin target**: `HomeScreenContent` + `HomeViewModel.homeRows`

```
@state
  tab: "home" | "films" | "series" | "live"
  featured: Title
  continueRow: Title[]
  watchlistRow: Title[]
  extensionRows: { title, items: Title[] }[]
  topTen: Title[]
  leavingSoon: LeavingTitle[]
  moods: Mood[]
  channels: IPTVChannel[]
  loading: boolean
  error: string | null

@callbacks
  onPlayTitle(t: Title)
  onOpenDetails(t: Title)
  onOpenChannel(id: string)
  onSelectMood(id: string)
  onOpenSearch()
  onOpenSettings()
  onSwitchTab(tab)
```

---

## Live TV
- **Lumen files**: `src/components/LiveTV.tsx`, `mobile/src/screens/LiveTVScreen.tsx`
- **Kotlin target**: `LiveTvScreenContent` (mobile) + `TvLiveGuideScreenContent` (TV)

```
@state
  channels: IPTVChannel[]
  groups: string[]
  activeGroup: string | null
  query: string
  nowNext: Record<channelId, { now?, next? }>
  loading: boolean
  error: string | null

@callbacks
  onPlayChannel(c: IPTVChannel)
  onOpenDetails(c: IPTVChannel)
  onSelectGroup(g: string | null)
  onSearch(q: string)
  onRefreshPlaylist()
```

---

## Details
- **Lumen files**: `src/components/DetailsModal.tsx`, `mobile/src/components/DetailsSheet.tsx`
- **Kotlin target**: `DetailsScreenContent`

```
@state
  title: Title
  episodes?: Episode[]
  selectedSeason?: number
  inWatchlist: boolean
  resumePosition?: { episodeId?, seconds }
  streams?: ResolvedStream[]
  streamsStatus: "idle" | "loading" | "ready" | "empty" | "error"

@callbacks
  onPlay()
  onPickStream(s: ResolvedStream)
  onToggleWatchlist()
  onSelectSeason(n: number)
  onSelectEpisode(e: Episode)
  onClose()
```

---

## Player
- **Lumen files**: `src/components/Player.tsx`, `mobile/src/components/Player.tsx`
- **Kotlin target**: `PlayerScreen` (chrome only — keep ExoPlayer)

```
@state
  source: PlayerSource | null   // { kind: "title"|"live", ... }
  mode: "full" | "mini" | "closed"
  position, duration, buffered
  isPlaying, isMuted

@callbacks
  onClose()
  onMinimize()
  onRestore()
  onSeek(ms)
  onTogglePlay()
  onToggleMute()
  onOpenSettings()           // audio/sub tracks
```

---

## Search
- **Lumen files**: `src/components/SearchCommand.tsx` (⌘K), `mobile/src/screens/SearchScreen.tsx`
- **Kotlin target**: `SearchScreenContent`

```
@state
  query: string
  results: { titles: Title[], channels: IPTVChannel[] }
  recents: string[]
  loading: boolean

@callbacks
  onQueryChange(q)
  onOpenTitle(t)
  onOpenChannel(c)
  onClearRecents()
```

---

## Settings
- **Lumen files**: `src/components/SettingsPanel.tsx`, `mobile/src/screens/SettingsScreen.tsx`
- **Kotlin target**: `SettingsScreenContent`

```
@state
  brand, version, channel
  preferences: { autoplay, reduceMotion, theme, ... }
  addons: InstalledExtension[]
  iptvSources: { url, status }[]
  epgSources: { url, status }[]
  profile: Profile

@callbacks
  onUpdatePreference(key, value)
  onInstallAddon(url)
  onRemoveAddon(baseUrl)
  onAddIptv(url)
  onAddEpg(url)
  onSwitchProfile()
  onExportBackup()
  onImportBackup()
```

---

## Profile gate
- **Lumen files**: `src/components/ProfileGate.tsx`, `mobile/src/screens/ProfileGate.tsx`
- **Kotlin target**: `ProfileGateScreen`

```
@state
  profiles: Profile[]
  pinRequired: boolean
  activeProfileId?: string
  pinError?: string

@callbacks
  onSelectProfile(id)
  onSubmitPin(pin)
  onCreateProfile()
  onEditProfiles()
```

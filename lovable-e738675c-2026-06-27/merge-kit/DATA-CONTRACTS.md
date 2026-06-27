# Data Contracts

Lumen TS types ↔ CalmSource Kotlin domain models. The Kotlin port writes
mapper functions (named here, not implemented yet) so existing ViewModels
emit UI state shaped like Lumen's types.

## `Title` ↔ `MediaItem`
| Lumen `Title` (TS) | CalmSource `MediaItem` (Kotlin) |
| --- | --- |
| `id: string` | `id: String` |
| `name: string` | `title: String` |
| `year: number` | `year: Int?` |
| `rating: string` | `contentRating: String?` |
| `duration: string` | `durationLabel: String?` |
| `genres: string[]` | `genres: List<String>` |
| `description: string` | `overview: String` |
| `poster: string` | `posterUrl: String` |
| `backdrop: string` | `backdropUrl: String` |
| `tile: string` (small 16:9) | `tileUrl: String` (fallback to `backdropUrl` low-res) |
| `logo?: string` | `logoUrl: String?` |
| `kind: "film" \| "series"` | `MediaType.FILM` / `MediaType.SERIES` |
| `seasons?: number` | `seasonCount: Int?` |
| `episodes?: number` | `episodeCount: Int?` |

**Mapper**: `fun MediaItem.toTitle(): Title` (Kotlin → UI state).

## `IPTVChannel` ↔ `Channel`
| Lumen `IPTVChannel` | CalmSource `Channel` |
| --- | --- |
| `id: string` | `id: String` |
| `name: string` | `name: String` |
| `url: string` | `streamUrl: String` |
| `group?: string` | `category: String?` |
| `logo?: string` | `logoUrl: String?` |
| `tvgId?: string` | `xmltvId: String?` |

**Mapper**: `fun Channel.toIPTVChannel(): IPTVChannel`.

## `userdata` ↔ CalmSource repositories
| Lumen module export | CalmSource source |
| --- | --- |
| `continueWatching` | `UserMemoryRepository.continueWatching()` |
| `watchlist` | `WatchlistRepository.items()` (or `FavoritesRepository`) |
| `preferences` | `ProfilePreferencesRepository.observe(profileId)` |
| `hydrate()` | Boot: `UserMemoryRepository.hydrate()` |

## `PlayerSource` ↔ `PlaybackRequest`
| Lumen `PlayerSource` | Kotlin |
| --- | --- |
| `{ kind: "title", title, streamUrl }` | `PlaybackRequest.Vod(mediaId, streamUrl, startPositionMs)` |
| `{ kind: "live", channelId, name, streamUrl }` | `buildLivePlaybackRequest(channel)` |

## `Profile` ↔ `Profile`
| Lumen `Profile` | CalmSource `Profile` |
| --- | --- |
| `id` | `id` |
| `name` | `name` |
| `avatar` (asset key) | `avatarKey` |
| `pinHash?` | `pinHash?` |
| `kidsMode?: boolean` | `isKids: Boolean` |

## Repository facade ↔ Kotlin
| Lumen TS | Kotlin |
| --- | --- |
| `CatalogRepository` (`src/lib/repositories.ts`) | `CatalogRepository` (existing) + `HomeViewModel.homeRows` |
| `ChannelRepository` | `IPTVRepository.getLiveChannels()` / `setChannels()` |
| `PlaybackResolver.resolveTitle` | `PlaybackResolver.resolveVod(mediaId)` |
| `PlaybackResolver.resolveLive` | `buildLivePlaybackRequest(channel)` |

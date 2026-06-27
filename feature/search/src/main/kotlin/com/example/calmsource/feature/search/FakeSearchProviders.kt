/**
 * Demo / fake [SearchProvider] implementations for development and testing.
 *
 * These providers use [FakeData] and the in-memory [IPTVRepository] to simulate
 * real search behavior without network access. They cover all provider categories:
 *
 * | Provider                          | Category   | Priority |
 * |-----------------------------------|------------|----------|
 * | [HistorySearchProviderImpl]       | History    | 95       |
 * | [IPTVSearchProviderImpl]          | IPTV Live  | 90       |
 * | [VODSearchProviderImpl]           | IPTV Catalog | 85       |
 * | [EPGSearchProviderImpl]           | EPG        | 80       |
 * | [DebridAvailabilityProviderImpl]  | Debrid     | 75       |
 * | [ExtensionSearchProviderImpl]     | Extensions | 70       |
 * | [MetadataSearchProviderImpl]      | Metadata   | 60       |
 * | [SubtitleSearchProviderImpl]      | Subtitles  | 50       |
 * | [SettingsSearchProviderImpl]      | Settings   | 40       |
 *
 * Simulated delays and failure modes are included to exercise timeout and
 * error-handling paths in [UniversalSearchEngineImpl].
 *
 * @see SearchProvider for the provider contract
 * @see FakeData for the static test data used by these providers
 */
package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private val searchFixturesEnabled: Boolean by lazy {
    TestEnvironment.isTest
}

/** Searches parsed IPTV playlists for live channel matches. */
class IPTVSearchProviderImpl : IPTVSearchProvider {
    override val id = "prov-iptv-live"
    override val name = "IPTV Live Channels"
    override val priority = 90

    override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
        val queryNorm = query.query.normalizeForSearch()
        val matchedChannels = com.example.calmsource.feature.iptv.IPTVRepository.getLiveChannels().filter {
            it.name.normalizeForSearch().contains(queryNorm)
        }.map { iptvChan ->
            Channel(
                id = iptvChan.id,
                name = iptvChan.name,
                logoUrl = iptvChan.tvgLogo,
                streamUrl = iptvChan.streamUrl,
                category = iptvChan.groupTitle
            )
        }
        emit(SearchProviderResult(
            providerId = id,
            providerName = name,
            query = query,
            channels = matchedChannels
        ))
    }
}

/** Searches the Electronic Program Guide (EPG) for matching programs. */
class EPGSearchProviderImpl : EPGSearchProvider {
    override val id = "prov-epg"
    override val name = "Live TV Guide (EPG)"
    override val priority = 80

    override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
        val matchedPrograms = com.example.calmsource.feature.iptv.IPTVRepository.searchPrograms(query.query).mapNotNull { epgProg ->
            val iptvChannelId = com.example.calmsource.feature.iptv.IPTVRepository
                .findChannelIdForEpgChannel(epgProg.channelId)
                ?: return@mapNotNull null
            Program(
                id = epgProg.id,
                channelId = iptvChannelId,
                title = epgProg.title,
                description = epgProg.description,
                startTimeMs = epgProg.startTimeMs,
                endTimeMs = epgProg.endTimeMs
            )
        }
        emit(SearchProviderResult(
            providerId = id,
            providerName = name,
            query = query,
            programs = matchedPrograms
        ))
    }
}

/** Searches IPTV movies, parsed VOD channels, and synced Xtream VOD/series catalogs. */
class VODSearchProviderImpl : VODSearchProvider {
    override val id = "prov-vod"
    override val name = "IPTV Catalogs"
    override val priority = 85

    companion object {
        /** Injectable Xtream VOD items retained for isolated unit tests. */
        @Volatile
        private var testXtreamVodItems: List<XtreamVodItem> = emptyList()

        var xtreamVodItems: List<XtreamVodItem>
            get() = if (TestEnvironment.isTest) testXtreamVodItems else emptyList()
            set(value) {
                if (TestEnvironment.isTest) {
                    testXtreamVodItems = value
                }
            }

        /** Injectable Xtream series items retained for isolated unit tests. */
        @Volatile
        private var testXtreamSeriesItems: List<XtreamSeriesItem> = emptyList()

        var xtreamSeriesItems: List<XtreamSeriesItem>
            get() = if (TestEnvironment.isTest) testXtreamSeriesItems else emptyList()
            set(value) {
                if (TestEnvironment.isTest) {
                    testXtreamSeriesItems = value
                }
            }
    }

    override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
        val queryNorm = query.query.normalizeForSearch()
        val fixtureItems = if (searchFixturesEnabled) FakeData.movies + FakeData.shows else emptyList()
        val matchedMovies = fixtureItems
            .filter { it.type == MediaType.MOVIE && it.title.normalizeForSearch().contains(queryNorm) }
            .toMutableList()
        val matchedShows = fixtureItems
            .filter { it.type == MediaType.SHOW && it.title.normalizeForSearch().contains(queryNorm) }
            .toMutableList()

        // Match from parsed custom VOD channels
        val parsedVodChannels = com.example.calmsource.feature.iptv.IPTVRepository.getChannels().filter {
            it.isVod && it.name.normalizeForSearch().contains(queryNorm)
        }

        val parsedSources = mutableListOf<StreamSource>()
        parsedVodChannels.forEach { channel ->
            val existingMovie = fixtureItems.firstOrNull { movie ->
                if (movie.type != MediaType.MOVIE) return@firstOrNull false
                val channelNorm = channel.name.lowercase().replace(Regex("[^a-z0-9]"), "").replace("iptv", "")
                val movieNorm = movie.title.lowercase().replace(Regex("[^a-z0-9]"), "").replace("iptv", "")
                channelNorm.isNotEmpty() && movieNorm.isNotEmpty() && (channelNorm.contains(movieNorm) || movieNorm.contains(channelNorm))
            }

            val mediaId = existingMovie?.id ?: "iptv-vod-${channel.name.lowercase().replace(" ", "-")}"

            if (existingMovie == null && matchedMovies.none { it.title.equals(channel.name, ignoreCase = true) }) {
                val mediaItem = MediaItem(
                    id = mediaId,
                    title = channel.name,
                    type = MediaType.MOVIE,
                    overview = "IPTV VOD Stream",
                    posterUrl = channel.tvgLogo
                )
                matchedMovies.add(mediaItem)
            }

            parsedSources.add(
                StreamSource(
                    id = channel.id,
                    name = channel.name,
                    url = channel.streamUrl,
                    extensionId = channel.providerId,
                    resolution = "1080p",
                    language = "Unknown"
                )
            )
        }

        // ── Xtream VOD integration ──────────────────────────────────────
        val persistedXtreamVod =
            com.example.calmsource.feature.iptv.IPTVRepository.searchXtreamVod(query.query)
        val matchedXtreamVod = (xtreamVodItems + persistedXtreamVod)
            .distinctBy { it.id.ifEmpty { "${it.providerId}:${it.streamId}" } }
            .filter { it.name.normalizeForSearch().contains(queryNorm) }

        matchedXtreamVod.forEach { vodItem ->
            val hasPersistedProvider = vodItem.providerId.isNotBlank()
            val xtreamMediaId = if (hasPersistedProvider) vodItem.id else "xtream-vod-${vodItem.streamId}"

            // Deduplicate against already-matched movies by normalized title
            val alreadyMatched = matchedMovies.any { existing ->
                existing.title.normalizeForSearch() == vodItem.name.normalizeForSearch()
            }
            if (!alreadyMatched) {
                matchedMovies.add(
                    MediaItem(
                        id = xtreamMediaId,
                        title = vodItem.name,
                        type = MediaType.MOVIE,
                        overview = "Xtream VOD",
                        posterUrl = vodItem.poster,
                        rating = vodItem.rating
                    )
                )
            }

            // Always add the source so it can merge with existing media items
            if (hasPersistedProvider) {
                parsedSources.add(
                    StreamSource(
                        id = vodItem.id,
                        name = vodItem.name,
                        url = com.example.calmsource.feature.iptv.xtream.XtreamStreamUrlBuilder
                            .createPseudoUrl(vodItem.providerId, vodItem.streamId) ?: vodItem.streamId,
                        extensionId = vodItem.providerId,
                        resolution = "VOD",
                        language = "Unknown"
                    )
                )
            }
        }

        val persistedXtreamSeries =
            com.example.calmsource.feature.iptv.IPTVRepository.searchXtreamSeries(query.query)
        val matchedXtreamSeries = (xtreamSeriesItems + persistedXtreamSeries)
            .distinctBy { it.id.ifEmpty { "${it.providerId}:${it.seriesId}" } }
            .filter { it.name.normalizeForSearch().contains(queryNorm) }

        matchedXtreamSeries.forEach { seriesItem ->
            val hasPersistedProvider = seriesItem.providerId.isNotBlank()
            val xtreamMediaId = if (hasPersistedProvider) seriesItem.id else "xtream-series-${seriesItem.seriesId}"

            val alreadyMatched = matchedShows.any { existing ->
                existing.title.normalizeForSearch() == seriesItem.name.normalizeForSearch()
            }
            if (!alreadyMatched) {
                matchedShows.add(
                    MediaItem(
                        id = xtreamMediaId,
                        title = seriesItem.name,
                        type = MediaType.SHOW,
                        overview = "Xtream Series Catalog",
                        posterUrl = seriesItem.poster,
                        rating = seriesItem.rating
                    )
                )
            }

            if (hasPersistedProvider) {
                parsedSources.add(
                    StreamSource(
                        id = seriesItem.id,
                        name = seriesItem.name,
                        url = "",
                        extensionId = seriesItem.providerId,
                        resolution = "Series",
                        language = "Unknown"
                    )
                )
            }
        }

        val matchedItems = matchedMovies + matchedShows
        val sources = mutableListOf<StreamSource>()
        
        matchedItems.forEach { item ->
            val localSources = if (searchFixturesEnabled) {
                FakeData.getSourcesForMedia(item.id).filter { it.extensionId.startsWith("iptv-") }
            } else {
                emptyList()
            }
            sources.addAll(localSources)
        }
        sources.addAll(parsedSources)

        emit(SearchProviderResult(
            providerId = id,
            providerName = name,
            query = query,
            mediaItems = matchedItems,
            streamSources = sources
        ))
    }
}

/** Checks debrid cache availability for matched media. */
class DebridAvailabilityProviderImpl : DebridAvailabilityProvider {
    override val id = "prov-debrid"
    override val name = "Debrid Cache Linker"
    override val priority = 75

    override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
        val accounts = com.example.calmsource.feature.debrid.DebridRepository.listAccounts()
        val connectedAccount = accounts.firstOrNull { it.isConnected }

        if (connectedAccount != null) {
            when (connectedAccount.health) {
                com.example.calmsource.core.model.DebridAccountHealth.SLOW -> {
                    // Keep search responsive; account health is reflected through ranking.
                }
                com.example.calmsource.core.model.DebridAccountHealth.FAILED -> {
                    emit(SearchProviderResult(
                        providerId = id,
                        providerName = name,
                        query = query,
                        error = IllegalStateException("Debrid provider connection failed")
                    ))
                    return@flow
                }
                else -> {
                    // No artificial delay in production search.
                }
            }
        }

        val queryNorm = query.query.normalizeForSearch()
        val matchedItems = if (searchFixturesEnabled) {
            (FakeData.movies + FakeData.shows).filter { it.title.normalizeForSearch().contains(queryNorm) }
        } else {
            emptyList()
        }
        
        val sources = mutableListOf<StreamSource>()
        if (searchFixturesEnabled) {
            matchedItems.forEach { item ->
                val debridSources = FakeData.getSourcesForMedia(item.id).filter { it.extensionId.startsWith("deb-") }

                val filteredDebridSources = if (prefs.hideNonCached) {
                    debridSources.filter { src ->
                        if (connectedAccount != null) {
                            val hash = if (src.id == "src-spiderman-debrid-4k") "spiderman-4k-hash" else src.id + "-hash"
                            val cacheResult = com.example.calmsource.feature.debrid.DebridRepository.checkCachedAvailability(
                                connectedAccount.providerType,
                                listOf(hash)
                             )
                            cacheResult[hash]?.isCached ?: false
                        } else false
                    }
                } else {
                    debridSources
                }
                sources.addAll(filteredDebridSources)
            }
        }

        emit(SearchProviderResult(
            providerId = id,
            providerName = name,
            query = query,
            mediaItems = matchedItems,
            streamSources = sources
        ))
    }
}

/** Reserved subtitle provider. Returns empty results until a real subtitle backend is connected. */
class SubtitleSearchProviderImpl : SubtitleSearchProvider {
    override val id = "prov-subtitles"
    override val name = "Subtitle Searcher"
    override val priority = 50

    override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
        emit(SearchProviderResult(
            providerId = id,
            providerName = name,
            query = query
        ))
    }
}

/** Enriches results with TMDB metadata (poster art, descriptions, etc.). */
class MetadataSearchProviderImpl : MetadataSearchProvider {
    override val id = "prov-metadata"
    override val name = "TMDB Metadata Scraper"
    override val priority = 60

    override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
        val queryNorm = query.query.normalizeForSearch()
        val matchedItems = if (searchFixturesEnabled) {
            (FakeData.movies + FakeData.shows).filter {
                val titleNorm = it.title.normalizeForSearch()
                titleNorm.contains(queryNorm) ||
                    titleNorm.replace(" ", "") == queryNorm.replace(" ", "")
            }
        } else {
            val encodedQuery = java.net.URLEncoder.encode(query.query.trim(), "UTF-8")
                .replace("+", "%20")
            val baseUrl = "https://v3-cinemeta.strem.io"
            val requests = listOf(
                "movie" to "top",
                "series" to "top"
            )
            supervisorScope {
                requests.map { (type, catalogId) ->
                    async(Dispatchers.IO) {
                        when (
                            val response = com.example.calmsource.core.network.StremioAddonClient.getCatalog(
                                resolvedBaseUrl = baseUrl,
                                type = type,
                                catalogId = catalogId,
                                extraArgs = "search=$encodedQuery",
                                providerId = id,
                                timeoutMs = 15_000L
                            )
                        ) {
                            is com.example.calmsource.core.network.StremioResult.Success -> {
                                response.data.metas.orEmpty().mapNotNull { meta ->
                                    if (meta.id.isBlank() || meta.name.isBlank()) {
                                        null
                                    } else {
                                        MediaItem(
                                            id = meta.id,
                                            title = meta.name,
                                            type = if (meta.type == "series" || type == "series") {
                                                MediaType.SHOW
                                            } else {
                                                MediaType.MOVIE
                                            },
                                            overview = meta.description,
                                            posterUrl = meta.poster,
                                            rating = meta.imdbRating?.toDoubleOrNull(),
                                            externalIds = buildMap {
                                                meta.imdbId
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?.let { put("imdb", it) }
                                                if (
                                                    meta.id.startsWith("tt") &&
                                                    meta.id.drop(2).all(Char::isDigit)
                                                ) {
                                                    put("imdb", meta.id)
                                                }
                                                put("stremio", meta.id)
                                            }
                                        )
                                    }
                                }
                            }
                            is com.example.calmsource.core.network.StremioResult.Failure -> emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
        }
        emit(SearchProviderResult(
            providerId = id,
            providerName = name,
            query = query,
            mediaItems = matchedItems.distinctBy { "${it.type}:${it.id}" }
        ))
    }
}

/** Searches the user's watch history for previously viewed content. */
class HistorySearchProviderImpl : HistorySearchProvider {
    override val id = "prov-history"
    override val name = "User Watch History"
    override val priority = 95

    override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
        val matchedItems = mutableListOf<MediaItem>()
        val queryNorm = query.query.normalizeForSearch()
        if (searchFixturesEnabled && FakeData.movieSpiderman.title.normalizeForSearch().contains(queryNorm)) {
            matchedItems.add(FakeData.movieSpiderman)
        }
        emit(SearchProviderResult(
            providerId = id,
            providerName = name,
            query = query,
            mediaItems = matchedItems
        ))
    }
}

/** Matches the search query against in-app settings routes for quick navigation. */
class SettingsSearchProviderImpl : SettingsSearchProvider {
    override val id = "prov-settings"
    override val name = "Settings Indexer"
    override val priority = 40

    override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
        val matchedSettings = mutableListOf<String>()
        val settingsShortcuts = listOf(
            "Source Priorities & Languages",
            "IPTV Playlists & Services",
            "Extensions Setup",
            "Debrid Accounts Setup",
            "General Settings Configuration"
        )
        val queryNorm = query.query.normalizeForSearch()
        settingsShortcuts.forEach { shortcut ->
            if (shortcut.normalizeForSearch().contains(queryNorm)) {
                matchedSettings.add(shortcut)
            }
        }
        emit(SearchProviderResult(
            providerId = id,
            providerName = name,
            query = query,
            settingsRoutes = matchedSettings
        ))
    }
}

package com.example.calmsource.core.discoveryengine

import android.content.Context
import android.util.Log
import com.example.calmsource.core.discoveryengine.database.*
import com.example.calmsource.core.discoveryengine.models.*
import com.example.calmsource.core.discoveryengine.providers.AddonProviderManager
import com.example.calmsource.core.discoveryengine.providers.ExternalIdSet
import com.example.calmsource.core.discoveryengine.providers.ProviderManager
import com.example.calmsource.core.discoveryengine.providers.ProviderType
import com.example.calmsource.core.model.ContinueWatchingItem
import com.example.calmsource.core.model.FavoriteItem
import com.example.calmsource.core.model.WatchHistoryItem
import com.example.calmsource.core.model.SortingPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

object DiscoveryEngine {

    private const val KEY_ACTIVE_PROFILE = "active_profile_id"
    private const val KEY_PROVIDER_LOCAL_ONLY = "provider_local_only_mode"
    private const val KEY_PROVIDER_ENRICHMENT_PREFIX = "provider_enrichment_allowed_"

    @Volatile
    private var activeProfileId: String? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var repository: DiscoveryEngineRepository? = null
    private val repositoryLock = Any()

    private val providerSettingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repositoryOrThrow: DiscoveryEngineRepository
        get() = repository ?: synchronized(this) {
            repository ?: run {
                val ctx = appContext ?: com.example.calmsource.core.database.DatabaseProvider.context
                if (ctx != null) {
                    initialize(ctx)
                    repository ?: throw IllegalStateException("DiscoveryEngine initialization failed!")
                } else {
                    throw IllegalStateException("DiscoveryEngine must be initialized before use!")
                }
            }
        }

    /**
     * Initializes the DiscoveryEngine with an Android context.
     * Setups up the room database connection and the repository.
     */
    fun initialize(context: Context) {
        if (repository == null) {
            synchronized(repositoryLock) {
                if (repository == null) {
                    appContext = context.applicationContext
                    ProviderManager.init(context.applicationContext)
                    val repo = DiscoveryEngineRepository(context.applicationContext)
                    repository = repo
                    // Load provider settings asynchronously — defaults are safe
                    // until the load completes (typically < 100ms on IO thread)
                    providerSettingsScope.launch {
                        try {
                            loadProviderSettings(repo)
                        } catch (e: Exception) {
                            Log.w("DiscoveryEngine", "Failed to load provider settings", e)
                        }
                    }
                    AddonProviderManager.init(context.applicationContext)
                }
            }
        }
    }

    private fun getRepoOrThrow(): DiscoveryEngineRepository {
        return repositoryOrThrow
    }

    private suspend fun loadProviderSettings(repo: DiscoveryEngineRepository) {
        val localOnly = repo.getSetting(KEY_PROVIDER_LOCAL_ONLY).toPersistedBoolean(default = false)
        ProviderManager.setLocalOnlyMode(localOnly)
        ProviderType.entries.forEach { type ->
            val enabled = repo.getSetting(KEY_PROVIDER_ENRICHMENT_PREFIX + type.name)
                .toPersistedBoolean(default = true)
            ProviderManager.setEnrichmentAllowed(type, enabled)
        }
    }

    private fun String?.toPersistedBoolean(default: Boolean): Boolean {
        return when (this?.lowercase()) {
            "true", "1", "yes", "y", "on" -> true
            "false", "0", "no", "n", "off" -> false
            else -> default
        }
    }

    private fun shouldIngest(): Boolean {
        if (isLowRamDevice()) return false
        return try {
            val ctx = appContext
            if (ctx != null) {
                val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                if (activityManager != null) {
                    val memoryInfo = android.app.ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memoryInfo)
                    !memoryInfo.lowMemory && (memoryInfo.availMem > 50 * 1024 * 1024)
                } else true
            } else true
        } catch (_: Exception) {
            true
        }
    }

    private fun isLowRamDevice(): Boolean {
        return try {
            val ctx = appContext
            if (ctx != null) {
                val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                if (activityManager?.isLowRamDevice == true) return true
            }
            Runtime.getRuntime().maxMemory() <= 256L * 1024 * 1024
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ingests a list of Stremio media items (movies, series, episodes) into the database.
     */
    suspend fun ingestStremioItems(items: List<MediaItem>): IngestionStats {
        val startTime = System.currentTimeMillis()
        val insertedCount = if (shouldIngest()) {
            getRepoOrThrow().upsertMediaItems(items).size
        } else {
            Log.w("DiscoveryEngine", "Skipping MediaItem ingestion due to low memory")
            0
        }
        val duration = System.currentTimeMillis() - startTime
        return IngestionStats(insertedCount = insertedCount, durationMs = duration)
    }

    /**
     * Ingests a list of Stremio video stream options into the database.
     */
    suspend fun ingestStremioStreams(streams: List<MediaStream>): IngestionStats {
        val startTime = System.currentTimeMillis()
        val insertedCount = if (shouldIngest()) {
            getRepoOrThrow().upsertMediaStreams(streams)
            streams.size
        } else {
            Log.w("DiscoveryEngine", "Skipping MediaStream ingestion due to low memory")
            0
        }
        val duration = System.currentTimeMillis() - startTime
        return IngestionStats(insertedCount = insertedCount, durationMs = duration)
    }

    /**
     * Ingests a list of IPTV live channels into the database.
     */
    suspend fun ingestIptvChannels(channels: List<IptvChannel>): IngestionStats {
        val startTime = System.currentTimeMillis()
        val insertedCount = if (shouldIngest()) {
            getRepoOrThrow().upsertChannels(channels)
        } else {
            Log.w("DiscoveryEngine", "Skipping IPTV channel ingestion due to low memory")
            0
        }
        val duration = System.currentTimeMillis() - startTime
        return IngestionStats(insertedCount = insertedCount, durationMs = duration)
    }

    /**
     * Ingests EPG TV Guide program listings into the database.
     */
    suspend fun ingestEpgPrograms(programs: List<EpgProgram>): IngestionStats {
        val startTime = System.currentTimeMillis()
        val insertedCount = if (shouldIngest()) {
            getRepoOrThrow().upsertEpgPrograms(programs)
        } else {
            Log.w("DiscoveryEngine", "Skipping EPG ingestion due to low memory")
            0
        }
        val duration = System.currentTimeMillis() - startTime
        return IngestionStats(insertedCount = insertedCount, durationMs = duration)
    }

    /**
     * Retrieves autocomplete search suggestions.
     */
    suspend fun searchSuggestions(query: String, profileId: String, limit: Int): List<SuggestionResult> {
        return getRepoOrThrow().searchSuggestions(query, profileId, limit)
    }

    /**
     * Performs a full-text search across movie/series metadata and IPTV channels.
     */
    suspend fun fullSearch(query: String, profileId: String, filters: Map<String, String>, limit: Int): List<SearchResult> {
        return getRepoOrThrow().fullSearch(query, profileId, limit, filters)
    }

    /**
     * Records a local user media watch progress or completion event.
     */
    suspend fun trackWatchEvent(event: WatchEvent) {
        getRepoOrThrow().insertWatchEvent(event)
    }

    /**
     * Records a local user search query and optional selection event.
     */
    suspend fun trackSearchEvent(event: SearchEvent) {
        getRepoOrThrow().insertSearchEvent(event)
    }

    /**
     * Mirrors the privacy-safe app memory database into the recommendation engine.
     * Playback URLs and headers are not part of these models and never cross this boundary.
     */
    suspend fun syncUserMemory(
        profileId: String,
        continueWatching: List<ContinueWatchingItem>,
        watchHistory: List<WatchHistoryItem>,
        favorites: List<FavoriteItem>
    ) {
        getRepoOrThrow().syncUserMemory(
            profileId = profileId,
            continueWatching = continueWatching,
            watchHistory = watchHistory,
            favorites = favorites
        )
    }

    /**
     * Toggles the favorite state of a media item or channel for a profile.
     */
    suspend fun toggleFavorite(profileId: String, itemId: String, itemType: String, isFavorite: Boolean) {
        getRepoOrThrow().toggleFavorite(profileId, itemId, itemType, isFavorite)
    }

    /**
     * Toggles the hidden state of a media item or channel for a profile.
     */
    suspend fun toggleHidden(profileId: String, itemId: String, itemType: String, isHidden: Boolean) {
        getRepoOrThrow().toggleHidden(profileId, itemId, itemType, isHidden)
    }

    /**
     * Retrieves the watch progress and preferences state for a media item.
     */
    suspend fun getUserItemState(profileId: String, itemId: String): UserItemState? {
        return getRepoOrThrow().getUserItemState(profileId, itemId)
    }

    /**
     * Retrieves the watch and preferences state for a live IPTV channel.
     */
    suspend fun getUserChannelState(profileId: String, channelId: String): UserChannelState? {
        return getRepoOrThrow().getUserChannelState(profileId, channelId)
    }

    /**
     * Creates a new local user profile.
     */
    suspend fun createProfile(name: String): String {
        val id = "profile-${UUID.randomUUID()}"
        val entity = ProfileEntity(
            id = id,
            name = name,
            avatarUrl = null,
            createdAt = System.currentTimeMillis()
        )
        getRepoOrThrow().upsertProfile(entity)
        return id
    }

    /**
     * Retrieves all local user profiles.
     */
    suspend fun getAllProfiles(): List<LocalProfile> {
        return getRepoOrThrow().getAllProfiles().map { entity ->
            LocalProfile(
                id = entity.id,
                name = entity.name,
                avatarUrl = entity.avatarUrl,
                createdAt = entity.createdAt
            )
        }
    }

    /**
     * Deletes a local user profile.
     */
    suspend fun deleteProfile(profileId: String) {
        getRepoOrThrow().deleteProfile(profileId)
        if (activeProfileId == profileId) {
            activeProfileId = null
            getRepoOrThrow().upsertSetting(KEY_ACTIVE_PROFILE, "")
        }
    }

    /**
     * Updates an existing profile's properties.
     */
    suspend fun updateProfile(profileId: String, name: String, avatarUrl: String?) {
        val existing = getRepoOrThrow().getProfile(profileId) ?: return
        val updated = existing.copy(name = name, avatarUrl = avatarUrl)
        getRepoOrThrow().upsertProfile(updated)
    }

    /**
     * Sets the active profile ID.
     */
    suspend fun setActiveProfile(profileId: String) {
        getRepoOrThrow().upsertSetting(KEY_ACTIVE_PROFILE, profileId)
        activeProfileId = profileId
    }

    /**
     * Retrieves the current active profile.
     */
    suspend fun getActiveProfile(): LocalProfile? {
        val cached = activeProfileId
        if (cached != null) {
            return getProfileById(cached)
        }
        val persisted = getRepoOrThrow().getSetting(KEY_ACTIVE_PROFILE)
        if (!persisted.isNullOrEmpty()) {
            activeProfileId = persisted
            return getProfileById(persisted)
        }
        return null
    }

    private suspend fun getProfileById(profileId: String): LocalProfile? {
        val entity = getRepoOrThrow().getProfile(profileId) ?: return null
        return LocalProfile(
            id = entity.id,
            name = entity.name,
            avatarUrl = entity.avatarUrl,
            createdAt = entity.createdAt
        )
    }

    /**
     * Returns structured recommendation rows (e.g. Continue Watching, Recommended Movies).
     */
    suspend fun getHomeRows(profileId: String, forceRefresh: Boolean = false): List<RecommendationRow> {
        return getRepoOrThrow().getHomeRows(profileId, forceRefresh = forceRefresh)
    }

    /**
     * Sets the cold-start seed genres for a profile to handle new-profile cold start.
     */
    suspend fun setSeedGenres(profileId: String, genres: List<String>) {
        val genresString = genres.joinToString(",")
        getRepoOrThrow().upsertSetting("seed_genres_$profileId", genresString)
    }

    /**
     * Retrieves the taste profile containing genre and language affinities.
     */
    suspend fun getTasteProfile(profileId: String): TasteProfile {
        return getRepoOrThrow().getTasteProfile(profileId)
    }

    /**
     * Returns similar content items for a given media catalog item.
     */
    suspend fun getMoreLikeThis(profileId: String, itemId: String): List<RecommendationItem> {
        return getRepoOrThrow().getMoreLikeThis(profileId, itemId)
    }

    /**
     * Returns currently live recommendation items for Live IPTV.
     */
    suspend fun getLiveNowRecommendations(profileId: String): List<RecommendationItem> {
        return getRepoOrThrow().getLiveRecommendations(profileId)
    }

    /**
     * Finds the next episode recommendation for a series by its ID.
     */
    suspend fun getNextEpisode(
        profileId: String,
        seriesId: String,
        customThreshold: Double? = null
    ): NextEpisodeResult {
        val threshold = customThreshold ?: 0.85
        return getRepoOrThrow().getNextEpisode(profileId, seriesId, threshold)
    }

    /**
     * Finds the next episode recommendation for a series by its title.
     */
    suspend fun getNextEpisodeByTitle(
        profileId: String,
        seriesTitle: String,
        customThreshold: Double? = null
    ): NextEpisodeResult {
        val threshold = customThreshold ?: 0.85
        return getRepoOrThrow().getNextEpisodeByTitle(profileId, seriesTitle, threshold)
    }

    /**
     * Registers a downloadable metadata discovery pack.
     */
    suspend fun registerDiscoveryPack(id: String, name: String, description: String?, manifestUrl: String) {
        val pack = DiscoveryPack(id, name, description, manifestUrl, false, null)
        getRepoOrThrow().registerPack(pack)
    }

    /**
     * Installs a downloadable metadata discovery pack, bulk ingesting its items.
     */
    suspend fun installDiscoveryPack(id: String, items: List<MediaItem>, streams: List<MediaStream>) {
        getRepoOrThrow().installPack(id, items, streams)
    }

    /**
     * Uninstalls a discovery pack, purging all its items from the database.
     */
    suspend fun uninstallDiscoveryPack(id: String) {
        getRepoOrThrow().uninstallPack(id)
    }

    /**
     * Retrieves all registered discovery packs.
     */
    suspend fun getAvailableDiscoveryPacks(): List<DiscoveryPack> {
        return getRepoOrThrow().getAvailablePacks()
    }

    /**
     * Enables or disables low-memory optimization mode.
     */
    suspend fun setLowMemoryMode(enabled: Boolean) {
        val repo = getRepoOrThrow()
        repo.lowMemoryMode = enabled
        ProviderManager.setLowMemoryMode(enabled)
        repo.upsertSetting("low_memory_mode", enabled.toString())
    }

    /**
     * Checks if low-memory optimization mode is currently active.
     */
    suspend fun isLowMemoryMode(): Boolean {
        val repo = getRepoOrThrow()
        val persisted = repo.getSetting("low_memory_mode")
        if (persisted != null) {
            repo.lowMemoryMode = persisted.toBoolean()
        }
        return repo.lowMemoryMode
    }

    /**
     * Enqueues a cache-first metadata refresh. This never performs work on the caller thread.
     */
    fun enqueueMetadataRefresh(mediaId: String) {
        ProviderManager.enqueueMetadataRefresh(mediaId)
    }

    /**
     * Fire-and-forget enrichment for detail pages. Search and playback paths should not call this.
     */
    fun enrichItem(item: MediaItem, profileId: String = activeProfileId.orEmpty()) {
        ProviderManager.enrichItem(item.id, profileId, item.toExternalIdSet())
    }

    fun cancelPendingForMedia(mediaId: String) {
        ProviderManager.cancelPendingForMedia(mediaId)
    }

    suspend fun isLocalOnlyMode(): Boolean {
        val enabled = getRepoOrThrow().getSetting(KEY_PROVIDER_LOCAL_ONLY)?.toBoolean() ?: false
        ProviderManager.setLocalOnlyMode(enabled)
        return enabled
    }

    suspend fun setLocalOnlyMode(enabled: Boolean) {
        ProviderManager.setLocalOnlyMode(enabled)
        getRepoOrThrow().upsertSetting(KEY_PROVIDER_LOCAL_ONLY, enabled.toString())
    }

    suspend fun getProviderEnrichmentSettings(types: Set<ProviderType>): Map<ProviderType, Boolean> {
        val repo = getRepoOrThrow()
        return types.associateWith { type ->
            val enabled = repo.getSetting(KEY_PROVIDER_ENRICHMENT_PREFIX + type.name)?.toBoolean() ?: true
            ProviderManager.setEnrichmentAllowed(type, enabled)
            enabled
        }
    }

    suspend fun setProviderEnrichmentAllowed(type: ProviderType, enabled: Boolean) {
        ProviderManager.setEnrichmentAllowed(type, enabled)
        getRepoOrThrow().upsertSetting(KEY_PROVIDER_ENRICHMENT_PREFIX + type.name, enabled.toString())
    }

    /**
     * Triggers database maintenance, pruning expired EPG, old search history, old watch history, and expired cache.
     */
    suspend fun performMaintenance() {
        getRepoOrThrow().performMaintenance()
    }

    /**
     * Performs an on-device semantic search using feature-hashing vector embeddings and cosine similarity.
     */
    suspend fun searchSemantic(profileId: String, query: String, limit: Int = 10): List<SearchResult> {
        return getRepoOrThrow().searchSemantic(profileId, query, limit)
    }

    /**
     * Generates content recommendations using on-device cosine similarity between profile interest vector and media embeddings.
     */
    suspend fun getSemanticRecommendations(profileId: String, limit: Int = 10): List<RecommendationItem> {
        return getRepoOrThrow().getSemanticRecommendations(profileId, limit)
    }

    /**
     * Measures the quality of the search engine (MRR and Precision@3) for a given query and target item ID.
     */
    suspend fun getSearchQualityMetrics(profileId: String, query: String, targetItemId: String): SearchQualityMetrics {
        return getRepoOrThrow().calculateSearchQuality(profileId, query, targetItemId)
    }

    /**
     * Computes the Simpson Index of Diversity representing genre representation entropy of current home recommendations.
     */
    suspend fun getRecommendationDiversity(profileId: String): Double {
        return getRepoOrThrow().calculateRecommendationDiversity(profileId)
    }

    /**
     * Ranks multiple streaming links for a media item using resolution, codecs, language match,
     * and previous playback success/failure rates.
     */
    suspend fun rankStreams(profileId: String, mediaId: String, strategy: SortingPreference = SortingPreference.BEST_MATCH): List<MediaStream> {
        return getRepoOrThrow().rankStreams(profileId, mediaId, strategy)
    }

    /**
     * Tracks stream playback success or failure to inform availability-aware ranking.
     */
    suspend fun trackPlaybackEvent(
        streamId: String,
        mediaId: String,
        source: String,
        status: String,
        reason: String? = null
    ) {
        getRepoOrThrow().trackPlaybackEvent(streamId, mediaId, source, status, reason)
    }

    /**
     * Records direct feedback input for a media item (e.g. "not_interested", "hidden").
     */
    suspend fun trackFeedback(profileId: String, itemId: String, feedbackType: String) {
        getRepoOrThrow().trackFeedback(profileId, itemId, feedbackType)
    }

    /**
     * Updates preferred audio and subtitle languages for a profile.
     */
    suspend fun updateProfileLanguages(
        profileId: String,
        audioLanguages: List<String>,
        subtitleLanguages: List<String>
    ) {
        getRepoOrThrow().updateProfileLanguages(profileId, audioLanguages, subtitleLanguages)
    }

    private fun MediaItem.toExternalIdSet(): ExternalIdSet {
        val customIds = externalIds.toMutableMap()
        customIds["type"] = type
        return ExternalIdSet(
            imdbId = externalIds["imdb"] ?: externalIds["imdb_id"],
            tmdbId = externalIds["tmdb"] ?: externalIds["tmdb_id"],
            tvdbId = externalIds["tvdb"] ?: externalIds["tvdb_id"],
            kitsuId = externalIds["kitsu"],
            malId = externalIds["mal"],
            custom = customIds
        )
    }
}

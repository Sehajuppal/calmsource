package com.example.calmsource.core.discoveryengine.database

import android.content.Context
import android.util.Log
import com.example.calmsource.core.discoveryengine.models.*
import com.example.calmsource.core.discoveryengine.normalization.MetadataNormalizer
import com.example.calmsource.core.discoveryengine.normalization.Levenshtein
import com.example.calmsource.core.discoveryengine.normalization.Vectorizer
import com.example.calmsource.core.discoveryengine.ranking.SearchRanker
import com.example.calmsource.core.discoveryengine.ranking.SmartNextEpisodeFinder
import com.example.calmsource.core.discoveryengine.ranking.TasteProfileBuilder
import com.example.calmsource.core.discoveryengine.ranking.HybridRecommendationRanker
import com.example.calmsource.core.discoveryengine.ranking.SimilarityFinder
import com.example.calmsource.core.discoveryengine.ranking.LiveTvRecommender
import com.example.calmsource.core.discoveryengine.ranking.toStreamSource
import com.example.calmsource.core.discoveryengine.providers.ProviderManager
import com.example.calmsource.core.model.ContinueWatchingItem
import com.example.calmsource.core.model.FavoriteItem
import com.example.calmsource.core.model.WatchHistoryItem
import com.example.calmsource.core.model.UserPreferenceSignal
import com.example.calmsource.core.model.SortingPreference
import android.content.pm.PackageManager
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.core.sourceintelligence.ranking.DeviceStreamProfile
import com.example.calmsource.core.sourceintelligence.ranking.MediaAvailabilityResult
import com.example.calmsource.core.sourceintelligence.ranking.MediaAvailabilityScorer
import com.example.calmsource.core.sourceintelligence.ranking.StreamScoringSupport
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class DiscoveryEngineRepository(
    private val context: Context,
    private val testDao: DiscoveryEngineDao? = null
) {

    private val database = if (testDao == null) DiscoveryEngineDatabase.getInstance(context) else null
    private val dao = testDao ?: database!!.discoveryDao()

    private val upsertQueue = mutableListOf<UpsertRequest>()
    private val upsertMutex = Mutex()
    private val processingMutex = Mutex()
    private val sharedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val similarCache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, List<String>>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean {
                return size > 50
            }
        }
    )

    private data class UpsertRequest(
        val items: List<MediaItem>,
        val deferred: CompletableDeferred<Map<String, String>>
    )

    suspend fun upsertMediaItems(items: List<MediaItem>): Map<String, String> {
        if (items.isEmpty()) {
            return executeUpsertMediaItemsBatch(items)
        }
        if (testDao != null) {
            return executeUpsertMediaItemsBatch(items)
        }
        val deferred = CompletableDeferred<Map<String, String>>()
        upsertMutex.withLock {
            upsertQueue.add(UpsertRequest(items, deferred))
            if (upsertQueue.size == 1) {
                sharedScope.launch {
                    delay(16)
                    drainUpsertQueue()
                }
            }
        }
        return deferred.await()
    }

    /**
     * Drains the upsert queue in a loop until empty, preventing the race where
     * late arrivals add to the queue after the snapshot but before processingMutex
     * releases — which would leave their deferred hanging forever.
     */
    private suspend fun drainUpsertQueue() {
        processingMutex.withLock {
            while (true) {
                val requests = upsertMutex.withLock {
                    val copy = ArrayList(upsertQueue)
                    upsertQueue.clear()
                    copy
                }
                if (requests.isEmpty()) break
                val allItems = requests.flatMap { it.items }
                try {
                    val results = executeUpsertMediaItemsBatch(allItems)
                    for (req in requests) {
                        val reqResults = mutableMapOf<String, String>()
                        for (item in req.items) {
                            reqResults[item.id] = results[item.id] ?: item.id
                        }
                        req.deferred.complete(reqResults)
                    }
                } catch (e: Exception) {
                    for (req in requests) {
                        req.deferred.completeExceptionally(e)
                    }
                }
            }
        }
    }

    private suspend fun executeUpsertMediaItemsBatch(items: List<MediaItem>): Map<String, String> {
        return DiscoveryDatabaseGuard.write("upsertMediaItemsBatch:${items.size}") {
            var resolvedMap = mutableMapOf<String, String>()
            val db = database
            if (db != null) {
                db.runInTransaction {
                    resolvedMap = performUpsertInsideTransaction(items)
                }
            } else {
                resolvedMap = performUpsertInsideTransaction(items)
            }
            resolvedMap
        }
    }

    private fun performUpsertInsideTransaction(items: List<MediaItem>): MutableMap<String, String> {
        val resolvedMap = mutableMapOf<String, String>()
        val batchImdbMap = mutableMapOf<String, String>()
        val batchTmdbMap = mutableMapOf<String, String>()
        val batchTitleYearMap = mutableMapOf<Pair<String, Int>, String>()

        val entities = items.map { item ->
            val safeExternalIds = item.externalIds
            var resolvedId = com.example.calmsource.core.discoveryengine.normalization.EntityResolver.resolveMediaItemId(
                dao = dao,
                title = item.title,
                year = item.releaseYear,
                director = item.director,
                externalIds = safeExternalIds
            )
            if (resolvedId == null) {
                val imdbId = safeExternalIds["imdb"] ?: safeExternalIds["IMDb"]
                if (!imdbId.isNullOrEmpty()) {
                    resolvedId = batchImdbMap[imdbId]
                }
            }
            if (resolvedId == null) {
                val tmdbId = safeExternalIds["tmdb"] ?: safeExternalIds["TMDb"]
                if (!tmdbId.isNullOrEmpty()) {
                    resolvedId = batchTmdbMap[tmdbId]
                }
            }
            if (resolvedId == null && item.releaseYear != null && item.releaseYear > 0) {
                val normalizedTitle = MetadataNormalizer.normalizeTitle(item.title)
                if (normalizedTitle.isNotEmpty()) {
                    resolvedId = batchTitleYearMap[normalizedTitle to item.releaseYear]
                }
            }

            val finalId = resolvedId ?: item.id
            resolvedMap[item.id] = finalId

            // Store in the batch maps for subsequent items in this same batch
            val imdbId = safeExternalIds["imdb"] ?: safeExternalIds["IMDb"]
            if (!imdbId.isNullOrEmpty()) {
                batchImdbMap[imdbId] = finalId
            }
            val tmdbId = safeExternalIds["tmdb"] ?: safeExternalIds["TMDb"]
            if (!tmdbId.isNullOrEmpty()) {
                batchTmdbMap[tmdbId] = finalId
            }
            if (item.releaseYear != null && item.releaseYear > 0) {
                val normalizedTitle = MetadataNormalizer.normalizeTitle(item.title)
                if (normalizedTitle.isNotEmpty()) {
                    batchTitleYearMap[normalizedTitle to item.releaseYear] = finalId
                }
            }

            val extId = safeExternalIds["imdb"] ?: safeExternalIds["tmdb"] ?: safeExternalIds.values.firstOrNull()
            val extJson = Json.encodeToString(safeExternalIds)
            val normTitle = MetadataNormalizer.normalizeTitle(item.title)
            MediaItemEntity(
                id = finalId,
                type = item.type,
                title = item.title,
                overview = item.overview,
                posterUrl = item.posterUrl,
                rating = item.rating,
                releaseYear = item.releaseYear,
                genres = item.genres.joinToString(","),
                cast = item.cast.joinToString(","),
                director = item.director,
                language = item.language,
                durationMs = item.durationMs,
                externalId = extId,
                externalIdsJson = extJson,
                source = item.source,
                seriesId = item.seriesId,
                seasonNumber = item.seasonNumber,
                episodeNumber = item.episodeNumber,
                normalizedTitle = normTitle,
                updatedAt = System.currentTimeMillis()
            )
        }
        val uniqueEntities = entities.distinctBy { it.id }
        dao.upsertMediaItems(uniqueEntities)

        // Upsert normalized external IDs into junction table for indexed lookups
        val externalIdRows = items.flatMap { item ->
            val finalId = resolvedMap[item.id] ?: item.id
            @Suppress("SENSELESS_COMPARISON") // externalIds can be null from mocks/deserialization
            val ids = item.externalIds ?: emptyMap()
            ids.mapNotNull { (type, value) ->
                if (value.isNotBlank()) MediaExternalIdEntity(mediaId = finalId, idType = type, idValue = value)
                else null
            }
        }.distinctBy { it.idType to it.idValue }
        if (externalIdRows.isNotEmpty()) {
            dao.upsertExternalIds(externalIdRows)
        }

        // Generate and upsert vector embeddings for the media items
        val embeddings = uniqueEntities.map { entity ->
            val vector = Vectorizer.vectorize(
                title = entity.title,
                overview = entity.overview,
                genres = entity.genres.split(",").filter { it.isNotEmpty() },
                cast = entity.cast.split(",").filter { it.isNotEmpty() },
                director = entity.director,
                language = entity.language,
                source = entity.source
            )
            MediaEmbeddingEntity(
                itemId = entity.id,
                version = Vectorizer.VERSION,
                dimension = Vectorizer.DIMENSIONS,
                norm = 1.0,
                embedding = Vectorizer.vectorToBytes(vector),
                updatedAt = System.currentTimeMillis()
            )
        }
        dao.upsertEmbeddings(embeddings)

        bumpRecommendationCacheVersionInternal()
        database?.let { db ->
            val rawDb = db.openHelper.writableDatabase
            FtsIndexManager.upsertIndexEntries(
                rawDb,
                uniqueEntities.map { entity ->
                    FtsIndexManager.IndexEntry(
                        id = entity.id,
                        type = entity.type,
                        title = entity.title,
                        normalizedTitle = entity.normalizedTitle,
                        overview = entity.overview,
                        genres = entity.genres,
                        castDirector = "${entity.cast},${entity.director ?: ""}",
                        aliases = MetadataNormalizer.generateTitleAliases(entity.title).joinToString(" ")
                    )
                }
            )
        }
        return resolvedMap
    }

    suspend fun upsertMediaStreams(streams: List<MediaStream>) = DiscoveryDatabaseGuard.write("upsertMediaStreams:${streams.size}") {
        val entities = streams.map { stream ->
            MediaStreamEntity(
                id = stream.id,
                mediaId = stream.mediaItemId,
                title = stream.title,
                url = stream.url,
                resolution = stream.resolution,
                codec = stream.codec,
                quality = stream.quality,
                sizeInBytes = stream.sizeInBytes,
                language = stream.language,
                isSubbed = stream.isSubbed,
                isDubbed = stream.isDubbed,
                source = stream.source,
                updatedAt = System.currentTimeMillis()
            )
        }
        dao.upsertMediaStreams(entities)
        bumpRecommendationCacheVersionInternal()
    }

    suspend fun upsertChannels(channels: List<IptvChannel>): Int = DiscoveryDatabaseGuard.write("upsertChannels:${channels.size}") {
        val entities = channels.map { channel ->
            ChannelEntity(
                id = channel.id,
                name = channel.name,
                logoUrl = channel.logoUrl,
                streamUrl = channel.streamUrl,
                category = channel.category,
                providerId = channel.providerId,
                tvgId = channel.tvgId,
                updatedAt = System.currentTimeMillis()
            )
        }
        val uniqueEntities = entities.distinctBy { it.id }
        dao.upsertChannels(uniqueEntities)
        bumpRecommendationCacheVersionInternal()
        database?.let { db ->
            val rawDb = db.openHelper.writableDatabase
            FtsIndexManager.upsertIndexEntries(
                rawDb,
                uniqueEntities.map { entity ->
                    FtsIndexManager.IndexEntry(
                        id = entity.id,
                        type = "channel",
                        title = entity.name,
                        normalizedTitle = MetadataNormalizer.normalizeChannelName(entity.name),
                        overview = entity.category,
                        genres = null,
                        castDirector = null,
                        aliases = MetadataNormalizer.generateChannelAliases(entity.name).joinToString(" ")
                    )
                }
            )
        }
        uniqueEntities.size
    }

    suspend fun upsertEpgPrograms(programs: List<EpgProgram>): Int = DiscoveryDatabaseGuard.write("upsertEpgPrograms:${programs.size}") {
        val entities = programs.map { program ->
            EpgProgramEntity(
                id = program.id,
                channelId = program.channelId,
                title = program.title,
                description = program.description,
                category = program.category,
                startTime = program.startTimeMs,
                endTime = program.endTimeMs,
                language = program.language,
                episodeNum = program.episodeNum,
                updatedAt = System.currentTimeMillis()
            )
        }
        val uniqueEntities = entities.distinctBy { it.id }
        dao.upsertEpgPrograms(uniqueEntities)
        bumpRecommendationCacheVersionInternal()
        uniqueEntities.size
    }

    suspend fun insertWatchEvent(event: WatchEvent) = DiscoveryDatabaseGuard.write("insertWatchEvent") {
        val entity = WatchEventEntity(
            profileId = event.profileId,
            itemId = event.itemId,
            itemType = event.itemType,
            timestamp = event.timestamp,
            progressMs = event.progressMs,
            durationMs = event.durationMs,
            eventType = event.eventType
        )
        dao.insertWatchEvent(entity)

        // Update corresponding state table
        if (event.itemType == "channel") {
            val existing = dao.getUserChannelState(event.profileId, event.itemId)
            val isNewWatch = existing == null || event.eventType == "start"
            val newWatchCount = (existing?.watchCount ?: 0) + (if (isNewWatch) 1 else 0)
            val state = UserChannelStateEntity(
                profileId = event.profileId,
                channelId = event.itemId,
                isFavorite = existing?.isFavorite ?: false,
                isHidden = existing?.isHidden ?: false,
                lastWatchedAt = event.timestamp,
                watchCount = newWatchCount
            )
            dao.upsertUserChannelState(state)
        } else {
            val existing = dao.getUserItemState(event.profileId, event.itemId)
            val isNewWatch = existing == null || event.eventType == "start" || event.progressMs < (existing.progressMs - 100000)
            val newWatchCount = (existing?.watchCount ?: 0) + (if (isNewWatch) 1 else 0)
            
            // Completed threshold: 95% — must match syncUserMemory threshold
            val threshold = (event.durationMs * 95L) / 100L
            val isCompleted = (existing?.isCompleted == true) || (event.durationMs > 0 && event.progressMs >= threshold)

            val state = UserItemStateEntity(
                profileId = event.profileId,
                itemId = event.itemId,
                isFavorite = existing?.isFavorite ?: false,
                isHidden = existing?.isHidden ?: false,
                lastWatchedAt = event.timestamp,
                progressMs = event.progressMs,
                durationMs = event.durationMs,
                watchCount = newWatchCount,
                isCompleted = isCompleted
            )
            dao.upsertUserItemState(state)
        }
        dao.clearRecommendationCacheForProfile(event.profileId)
    }

    suspend fun insertSearchEvent(event: SearchEvent) = DiscoveryDatabaseGuard.write("insertSearchEvent") {
        val searchEntity = SearchEventEntity(
            profileId = event.profileId,
            query = event.query,
            timestamp = event.timestamp,
            selectedItemId = event.selectedItemId
        )
        dao.insertSearchEvent(searchEntity)
        dao.clearRecommendationCacheForProfile(event.profileId)
    }

    suspend fun syncUserMemory(
        profileId: String,
        continueWatching: List<ContinueWatchingItem>,
        watchHistory: List<WatchHistoryItem>,
        favorites: List<FavoriteItem>
    ) = DiscoveryDatabaseGuard.write("syncUserMemory") {
        val continueById = continueWatching
            .mapNotNull { item -> item.reference.sourceId?.let { it to item } }
            .toMap()
        val historyById = watchHistory
            .mapNotNull { item -> item.reference.sourceId?.let { it to item } }
            .toMap()
        val favoriteIds = favorites.mapNotNullTo(linkedSetOf()) { it.reference.sourceId }
        val existingStates = dao.getUserItemStatesForProfile(profileId).associateBy { it.itemId }
        // Build candidate set, sorted by recent activity so truncation drops
        // the least-recently-active items rather than arbitrary insertion order
        val allCandidateIds = linkedSetOf<String>().apply {
            addAll(continueById.keys)
            addAll(historyById.keys)
            addAll(favoriteIds)
            addAll(existingStates.keys)
        }
        val itemIds = allCandidateIds.sortedByDescending { itemId ->
            maxOf(
                continueById[itemId]?.updatedAt ?: 0L,
                historyById[itemId]?.lastWatchedAt ?: 0L,
                existingStates[itemId]?.lastWatchedAt ?: 0L
            )
        }

        val limit = 250
        val toSync = itemIds.take(limit)
        if (itemIds.size > limit) {
            android.util.Log.w("DiscoveryEngineRepository", "syncUserMemory truncating from ${itemIds.size} to $limit items for profile $profileId — ${itemIds.size - limit} least-recent items will not be synced")
        }
        toSync.forEach { itemId ->
            val existing = existingStates[itemId]
            val continueItem = continueById[itemId]
            val historyItem = historyById[itemId]
            val progressMs = continueItem?.progressMs ?: historyItem?.progressMs ?: existing?.progressMs ?: 0L
            val durationMs = continueItem?.durationMs ?: historyItem?.durationMs ?: existing?.durationMs ?: 0L
            val lastWatchedAt = maxOf(
                continueItem?.updatedAt ?: 0L,
                historyItem?.lastWatchedAt ?: 0L,
                existing?.lastWatchedAt ?: 0L
            ).takeIf { it > 0L }
            val completed = continueItem == null &&
                durationMs > 0L &&
                progressMs >= (durationMs * 95L) / 100L

            dao.upsertUserItemState(
                UserItemStateEntity(
                    profileId = profileId,
                    itemId = itemId,
                    isFavorite = itemId in favoriteIds,
                    isHidden = existing?.isHidden ?: false,
                    lastWatchedAt = lastWatchedAt,
                    progressMs = progressMs,
                    durationMs = durationMs,
                    watchCount = maxOf(
                        existing?.watchCount ?: 0,
                        historyItem?.watchCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
                    ),
                    isCompleted = existing?.isCompleted == true || completed
                )
            )
        }
        dao.clearRecommendationCacheForProfile(profileId)
    }

    suspend fun toggleFavorite(profileId: String, itemId: String, itemType: String, isFavorite: Boolean) = DiscoveryDatabaseGuard.write("toggleFavorite") {
        if (itemType == "channel") {
            val existing = dao.getUserChannelState(profileId, itemId)
            val state = existing?.copy(isFavorite = isFavorite) ?: UserChannelStateEntity(
                profileId = profileId,
                channelId = itemId,
                isFavorite = isFavorite,
                isHidden = false,
                lastWatchedAt = null,
                watchCount = 0
            )
            dao.upsertUserChannelState(state)
        } else {
            val existing = dao.getUserItemState(profileId, itemId)
            val state = existing?.copy(isFavorite = isFavorite) ?: UserItemStateEntity(
                profileId = profileId,
                itemId = itemId,
                isFavorite = isFavorite,
                isHidden = false,
                lastWatchedAt = null,
                progressMs = 0L,
                durationMs = 0L,
                watchCount = 0,
                isCompleted = false
            )
            dao.upsertUserItemState(state)
        }
        dao.clearRecommendationCacheForProfile(profileId)
    }

    suspend fun toggleHidden(profileId: String, itemId: String, itemType: String, isHidden: Boolean) = DiscoveryDatabaseGuard.write("toggleHidden") {
        if (itemType == "channel") {
            val existing = dao.getUserChannelState(profileId, itemId)
            val state = existing?.copy(isHidden = isHidden) ?: UserChannelStateEntity(
                profileId = profileId,
                channelId = itemId,
                isFavorite = false,
                isHidden = isHidden,
                lastWatchedAt = null,
                watchCount = 0
            )
            dao.upsertUserChannelState(state)
        } else {
            val existing = dao.getUserItemState(profileId, itemId)
            val state = existing?.copy(isHidden = isHidden) ?: UserItemStateEntity(
                profileId = profileId,
                itemId = itemId,
                isFavorite = false,
                isHidden = isHidden,
                lastWatchedAt = null,
                progressMs = 0L,
                durationMs = 0L,
                watchCount = 0,
                isCompleted = false
            )
            dao.upsertUserItemState(state)
        }
        dao.clearRecommendationCacheForProfile(profileId)
    }

    suspend fun getUserItemState(profileId: String, itemId: String): UserItemState? = withContext(Dispatchers.IO) {
        val entity = dao.getUserItemState(profileId, itemId) ?: return@withContext null
        UserItemState(
            profileId = entity.profileId,
            itemId = entity.itemId,
            isFavorite = entity.isFavorite,
            isHidden = entity.isHidden,
            lastWatchedAt = entity.lastWatchedAt,
            progressMs = entity.progressMs,
            durationMs = entity.durationMs,
            watchCount = entity.watchCount,
            isCompleted = entity.isCompleted
        )
    }

    suspend fun getUserChannelState(profileId: String, channelId: String): UserChannelState? = withContext(Dispatchers.IO) {
        val entity = dao.getUserChannelState(profileId, channelId) ?: return@withContext null
        UserChannelState(
            profileId = entity.profileId,
            channelId = entity.channelId,
            isFavorite = entity.isFavorite,
            isHidden = entity.isHidden,
            lastWatchedAt = entity.lastWatchedAt,
            watchCount = entity.watchCount
        )
    }
    suspend fun upsertProfile(profile: ProfileEntity) = DiscoveryDatabaseGuard.write("upsertProfile") {
        dao.upsertProfile(profile)
    }

    suspend fun getAllProfiles(): List<ProfileEntity> = withContext(Dispatchers.IO) {
        dao.getAllProfiles()
    }

    suspend fun deleteProfile(profileId: String) = DiscoveryDatabaseGuard.write("deleteProfile") {
        dao.deleteProfile(profileId)
    }

    suspend fun getProfile(profileId: String): ProfileEntity? = withContext(Dispatchers.IO) {
        dao.getProfile(profileId)
    }

    suspend fun getSetting(key: String): String? = withContext(Dispatchers.IO) {
        dao.getSetting(key)
    }

    suspend fun upsertSetting(key: String, value: String) = DiscoveryDatabaseGuard.write("upsertSetting:$key") {
        dao.upsertSetting(EngineSettingEntity(key, value))
    }


    suspend fun clearExpiredEpgPrograms() = DiscoveryDatabaseGuard.write("clearExpiredEpgPrograms") {
        val cutoff = System.currentTimeMillis()
        dao.clearExpiredEpgPrograms(cutoff)
    }

    suspend fun getDatabaseStats(): Map<String, Int> = withContext(Dispatchers.IO) {
        mapOf(
            "profiles" to dao.getProfileCount(),
            "media_items" to dao.getMediaItemCount(),
            "media_streams" to dao.getMediaStreamCount(),
            "channels" to dao.getChannelCount(),
            "epg_programs" to dao.getEpgProgramCount(),
            "watch_events" to dao.getWatchEventCount(),
            "search_events" to dao.getSearchEventCount(),
            "user_item_state" to dao.getUserItemStateCount(),
            "user_channel_state" to dao.getUserChannelStateCount()
        )
    }

    suspend fun printDiscoveryDatabaseStats() {
        val stats = getDatabaseStats()
        Log.d("DiscoveryEngine", "=== DiscoveryEngine Database Stats ===")
        stats.forEach { (table, count) ->
            Log.d("DiscoveryEngine", "- Table '$table': $count rows")
        }
        Log.d("DiscoveryEngine", "======================================")
    }

    fun getDao(): DiscoveryEngineDao = dao

    suspend fun searchFts(query: String, limit: Int = 50): List<Map<String, String>> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext emptyList()
        val rawDb = db.openHelper.writableDatabase
        val cleanLimit = if (isLowMemory()) minOf(limit, 15) else limit
        FtsIndexManager.search(rawDb, query, cleanLimit)
    }

    suspend fun getNextEpisode(
        profileId: String,
        seriesId: String,
        threshold: Double = 0.85
    ): NextEpisodeResult = withContext(Dispatchers.IO) {
        try {
            val backendMeta = com.example.calmsource.core.network.BackendApiClient.getMeta("series", seriesId)
            val videos = backendMeta?.meta?.videos
            if (!videos.isNullOrEmpty()) {
                val entities = videos.map { video ->
                    val episodeId = video.id ?: "${seriesId}:${video.season ?: 1}:${video.episode ?: 1}"
                    MediaItemEntity(
                        id = episodeId,
                        type = "episode",
                        title = video.title ?: "Episode ${video.episode}",
                        overview = video.overview,
                        posterUrl = null,
                        rating = null,
                        releaseYear = null,
                        genres = "",
                        cast = "",
                        director = null,
                        language = null,
                        durationMs = null,
                        externalId = null,
                        externalIdsJson = "{}",
                        source = "backend_tmdb",
                        seriesId = seriesId,
                        seasonNumber = video.season,
                        episodeNumber = video.episode,
                        normalizedTitle = com.example.calmsource.core.discoveryengine.normalization.MetadataNormalizer.normalizeTitle(video.title ?: ""),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                dao.upsertMediaItems(entities)
            }
        } catch (e: Exception) {
            android.util.Log.w("DiscoveryEngineRepository", "Failed to enrich episodes from backend for $seriesId: ${e.message}")
        }
        SmartNextEpisodeFinder.findNextEpisode(dao, profileId, seriesId, threshold)
    }

    suspend fun getNextEpisodeByTitle(
        profileId: String,
        seriesTitle: String,
        threshold: Double = 0.85
    ): NextEpisodeResult = withContext(Dispatchers.IO) {
        SmartNextEpisodeFinder.findNextEpisodeByTitle(dao, profileId, seriesTitle, threshold)
    }

    suspend fun fullSearch(
        query: String,
        profileId: String,
        limit: Int = 20,
        filters: Map<String, String> = emptyMap()
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val isLowMem = isLowMemory()
        val cleanLimit = if (isLowMem) minOf(limit, 10) else limit
        val cleanQuery = MetadataNormalizer.normalizeSearchQuery(query)
        if (cleanQuery.isEmpty() && filters.isEmpty()) return@withContext emptyList()

        val rawResults = if (cleanQuery.isNotEmpty()) {
            searchFts(query, cleanLimit * 3).toMutableList()
        } else {
            mutableListOf()
        }
        val seenIds = rawResults.mapNotNullTo(linkedSetOf()) { it["id"] }

        fun addMediaCandidate(media: MediaItemEntity) {
            if (!seenIds.add(media.id)) return
            rawResults.add(
                mapOf(
                    "id" to media.id,
                    "type" to media.type,
                    "title" to media.title,
                    "normalized_title" to media.normalizedTitle,
                    "overview" to (media.overview ?: ""),
                    "genres" to media.genres,
                    "cast_director" to "${media.cast},${media.director ?: ""}"
                )
            )
        }

        fun addChannelCandidate(channel: ChannelEntity) {
            if (!seenIds.add(channel.id)) return
            rawResults.add(
                mapOf(
                    "id" to channel.id,
                    "type" to "channel",
                    "title" to channel.name,
                    "normalized_title" to MetadataNormalizer.normalizeChannelName(channel.name),
                    "overview" to (channel.category ?: ""),
                    "genres" to "",
                    "cast_director" to ""
                )
            )
        }

        if (cleanQuery.isEmpty()) {
            val typeFilter = filters["type"]?.lowercase()
            val mediaCandidates = if (typeFilter == "channel") emptyList() else dao.getSearchCandidates(if (isLowMem) 100 else 500)
            val channelCandidates = if (typeFilter != null && typeFilter != "channel") emptyList() else dao.getChannelCandidates(if (isLowMem) 50 else 200)
            mediaCandidates.forEach(::addMediaCandidate)
            channelCandidates.forEach(::addChannelCandidate)
        } else {
            dao.searchMediaItemsByNormalizedTitle(cleanQuery, cleanLimit * 4)
                .forEach(::addMediaCandidate)
            dao.searchChannelsByName(query, cleanLimit * 4)
                .forEach(::addChannelCandidate)

            // Typo-tolerant candidates augment FTS/substring results. This matters when
            // FTS returns a few metadata matches but misses the intended title typo.
            val maxDistance = if (cleanQuery.length <= 3) 1 else 2

            val shouldLoadFuzzyCandidates =
                DiscoverySearchFeatureFlags.enableFuzzyFallback && rawResults.size < cleanLimit
            val mediaCandidates = if (shouldLoadFuzzyCandidates) {
                dao.getSearchCandidates(if (isLowMem) 100 else 500)
            } else {
                emptyList()
            }
            val channelCandidates = if (shouldLoadFuzzyCandidates) {
                dao.getChannelCandidates(if (isLowMem) 50 else 200)
            } else {
                emptyList()
            }
            val compactQuery = cleanQuery.replace(" ", "")
            val queryTokens = cleanQuery.split(" ").filter { it.isNotBlank() }

            fun fuzzyTitleMatches(candidate: String): Boolean {
                if (candidate.isBlank()) return false
                if (candidate.contains(cleanQuery)) return true
                val compactCandidate = candidate.replace(" ", "")
                if (
                    compactCandidate == compactQuery ||
                    compactCandidate.contains(compactQuery) ||
                    compactQuery.contains(compactCandidate) ||
                    Levenshtein.distance(compactQuery, compactCandidate) <= maxDistance
                ) {
                    return true
                }
                if (queryTokens.isEmpty()) return false
                val candidateTokens = candidate.split(" ").filter { it.isNotBlank() }
                return queryTokens.all { queryToken ->
                    candidateTokens.any { token ->
                        token.startsWith(queryToken) ||
                            Levenshtein.distance(queryToken, token) <= maxDistance
                    }
                }
            }

            mediaCandidates.forEach { media ->
                val normTitle = media.normalizedTitle
                if (fuzzyTitleMatches(normTitle)) {
                    addMediaCandidate(media)
                }
            }

            channelCandidates.forEach { channel ->
                val normName = MetadataNormalizer.normalizeChannelName(channel.name)
                if (fuzzyTitleMatches(normName)) {
                    addChannelCandidate(channel)
                }
            }
        }

        val effectiveResults = if (filters.isEmpty()) {
            rawResults
        } else {
            applySearchFilters(rawResults, filters)
        }

        SearchRanker.rankSearchResults(
            dao, profileId, query, effectiveResults, cleanLimit, isTelevisionFormFactor()
        )
    }

    /**
     * Applies user-selected facet filters (type / genre / year) to raw search candidate rows.
     * Type and genre are evaluated from data already present on every row; release year is
     * resolved from `media_items` only when a year filter is active. Channels are excluded by
     * genre/year filters because they carry neither field.
     */
    private suspend fun applySearchFilters(
        rows: List<Map<String, String>>,
        filters: Map<String, String>
    ): List<Map<String, String>> {
        val typeFilter = filters["type"]?.lowercase()?.takeIf { it.isNotBlank() && it != "all" }
        val genreFilter = filters["genre"]?.lowercase()?.takeIf { it.isNotBlank() && it != "all" }
        val yearFilter = filters["year"]?.toIntOrNull()

        val yearById: Map<String, Int?> = if (yearFilter != null) {
            val mediaIds = rows.asSequence()
                .filter { (it["type"] ?: "") != "channel" }
                .mapNotNull { it["id"] }
                .toList()
            if (mediaIds.isEmpty()) {
                emptyMap()
            } else {
                runCatching { dao.getMediaItemsByIds(mediaIds) }
                    .getOrDefault(emptyList())
                    .associate { it.id to it.releaseYear }
            }
        } else {
            emptyMap()
        }

        return rows.filter { row ->
            val type = row["type"]?.lowercase() ?: ""
            if (typeFilter != null) {
                val matches = when (typeFilter) {
                    "movie" -> type == "movie"
                    "series", "show" -> type == "series"
                    "channel" -> type == "channel"
                    else -> true
                }
                if (!matches) return@filter false
            }
            if (genreFilter != null) {
                if (type == "channel") return@filter false
                val genres = row["genres"]?.lowercase() ?: ""
                if (!genres.contains(genreFilter)) return@filter false
            }
            if (yearFilter != null) {
                if (type == "channel") return@filter false
                if (yearById[row["id"]] != yearFilter) return@filter false
            }
            true
        }
    }

    suspend fun searchSuggestions(
        query: String,
        profileId: String,
        limit: Int = 10
    ): List<SuggestionResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.lowercase().trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        val isLowMem = isLowMemory()
        val cleanLimit = if (isLowMem) minOf(limit, 5) else limit

        // 1. Try FTS (if active)
        val ftsResults = if (FtsIndexManager.activeMode == FtsIndexManager.FtsMode.FTS5 ||
            FtsIndexManager.activeMode == FtsIndexManager.FtsMode.FTS4) {
            searchFts(cleanQuery, cleanLimit * 2)
        } else {
            emptyList()
        }

        if (ftsResults.isNotEmpty()) {
            return@withContext ftsResults.map { raw ->
                val title = raw["title"] ?: ""
                val normTitle = raw["normalized_title"] ?: ""
                val score = if (normTitle.startsWith(cleanQuery)) 10.0 else 5.0
                SuggestionResult(
                    query = title,
                    score = score,
                    matchedTerm = query
                )
            }.distinctBy { it.query.lowercase() }.take(cleanLimit)
        }

        // 2. Fall back to indexed suggestions table prefix-only match
        val suggestions = dao.getSuggestionsByPrefix(cleanQuery, cleanLimit)
        if (suggestions.isNotEmpty()) {
            return@withContext suggestions.map { entity ->
                SuggestionResult(
                    query = entity.query,
                    score = entity.score,
                    matchedTerm = entity.matchedTerm ?: query
                )
            }
        }

        // 3. Fall back to prefix-only LIKE on media_items and channels
        val cleanQueryAlphaNum = MetadataNormalizer.normalizeSearchQuery(cleanQuery)
        val mediaMatches = dao.getMediaItemsByPrefix(cleanQueryAlphaNum, cleanLimit)
        val channelMatches = dao.getChannelsByPrefix(cleanQuery, cleanLimit)

        val results = mutableListOf<SuggestionResult>()
        mediaMatches.forEach { media ->
            results.add(
                SuggestionResult(
                    query = media.title,
                    score = (media.rating ?: 5.0),
                    matchedTerm = query
                )
            )
        }
        channelMatches.forEach { channel ->
            results.add(
                SuggestionResult(
                    query = channel.name,
                    score = 6.0,
                    matchedTerm = query
                )
            )
        }

        results.distinctBy { it.query.lowercase() }
            .sortedByDescending { it.score }
            .take(cleanLimit)
    }

    suspend fun getHomeRows(
        profileId: String,
        limit: Int = 15,
        forceRefresh: Boolean = false,
        preferenceSignals: List<UserPreferenceSignal> = emptyList()
    ): List<RecommendationRow> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = getCachedRecommendations(profileId, "home_rows", 10 * 60 * 1000L) // 10 minutes TTL
            if (cached != null) {
                return@withContext cached
            }
        }

        val isLowMem = isLowMemory()
        val cleanLimit = if (isLowMem) minOf(limit, 8) else limit

        val rows = mutableListOf<RecommendationRow>()

        // Fetch feedbacks and profile details
        val feedbacks = dao.getUserFeedbacksForProfile(profileId).associateBy { it.itemId }
        val profile = dao.getProfile(profileId)
        val preferredAudio = profile?.preferredAudioLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val preferredSub = profile?.preferredSubtitleLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        // 1. Continue Watching Row
        val continueStates = dao.getContinueWatchingStates(profileId, cleanLimit)
        if (continueStates.isNotEmpty()) {
            val itemIds = continueStates.map { it.itemId }
            val itemsMap = dao.getMediaItemsByIds(itemIds).associateBy { it.id }
            val continueItems = continueStates.mapNotNull { state ->
                val media = itemsMap[state.itemId] ?: return@mapNotNull null
                val feedback = feedbacks[media.id]
                if (feedback?.feedbackType == "not_interested" || feedback?.feedbackType == "hidden") return@mapNotNull null

                val progressPercent = if (state.durationMs > 0) {
                    (state.progressMs.toDouble() / state.durationMs.toDouble() * 100).toInt()
                } else 0
                RecommendationItem(
                    id = media.id,
                    type = media.type,
                    title = media.title,
                    score = 100.0 + (state.lastWatchedAt ?: 0L) / 1000000.0,
                    reason = "Resume watching (${progressPercent}% completed)",
                    scoreBreakdown = ScoreBreakdown(
                        reasons = listOf("Last watched at ${state.lastWatchedAt}")
                    ),
                    subtitle = "${media.type.replaceFirstChar { it.uppercase() }}${media.releaseYear?.let { " - $it" } ?: ""}",
                    posterUrl = media.posterUrl,
                    genres = media.genres.toGenreList(),
                    resumePositionMs = state.progressMs,
                    durationMs = state.durationMs,
                    source = media.source,
                    externalIds = media.externalIds()
                )
            }
            if (continueItems.isNotEmpty()) {
                rows.add(RecommendationRow("Continue Watching", "continue_watching", continueItems.toImmutableList()))
            }
        }

        // 2. Search-interest row — batch-fetch media items to avoid N+1
        val recentSearchEvents = dao.getRecentSelectedSearchEvents(profileId, 10)
        val searchSelectedIds = recentSearchEvents.mapNotNull { it.selectedItemId }
        val searchMediaMap = if (searchSelectedIds.isNotEmpty()) {
            dao.getMediaItemsByIds(searchSelectedIds).associateBy { it.id }
        } else emptyMap()
        val recentSearch = recentSearchEvents.firstOrNull { event ->
            event.selectedItemId != null && searchMediaMap.containsKey(event.selectedItemId)
        }
        if (recentSearch != null) {
            val selectedId = recentSearch.selectedItemId
            val target = selectedId?.let { searchMediaMap[it] }
            if (target != null) {
                val targetItem = target.toRecommendationItem(
                    reason = "You searched for ${recentSearch.query}",
                    score = 100.0
                )
                val similarItems = getMoreLikeThis(profileId, target.id, cleanLimit)
                    .map { item ->
                        item.copy(reason = "Similar to ${target.title}")
                    }
                rows.add(
                    RecommendationRow(
                        title = "Because You Searched for ${target.title}",
                        rowType = "search_interest:${target.id}",
                        items = (listOf(targetItem) + similarItems)
                            .distinctBy { it.id }
                            .take(cleanLimit)
                            .toImmutableList()
                    )
                )
            }
        }

        // 3. Personalized row
        val hasTasteSignals = dao.getUserItemStatesForProfile(profileId).any { state ->
            state.isFavorite || state.watchCount > 0
        }
        if (hasTasteSignals) {
            val personalized = getPersonalizedRecommendations(profileId, cleanLimit, preferenceSignals)
            if (personalized.isNotEmpty()) {
                rows.add(RecommendationRow("For You", "personalized", personalized.toImmutableList()))
            }

            // Embedding-based taste row. Previously implemented but never surfaced on home;
            // shown only on the personalization path and de-duplicated against "For You".
            val personalizedIds = personalized.mapTo(hashSetOf()) { it.id }
            val semantic = getSemanticRecommendations(profileId, cleanLimit)
                .filter { item ->
                    item.id !in personalizedIds &&
                        feedbacks[item.id]?.feedbackType != "not_interested" &&
                        feedbacks[item.id]?.feedbackType != "hidden"
                }
            if (semantic.isNotEmpty()) {
                rows.add(RecommendationRow("More For You", "semantic", semantic.toImmutableList()))
            }
        }

        // 4. Recently Added Row
        val recentMedia = dao.getRecentlyAdded(cleanLimit * 3)
        if (recentMedia.isNotEmpty()) {
            val recentAvailScores = batchAvailabilityScores(recentMedia.map { it.id }, preferredAudio, preferredSub)
            val recentItems = recentMedia.mapNotNull { media ->
                val feedback = feedbacks[media.id]
                if (feedback?.feedbackType == "not_interested" || feedback?.feedbackType == "hidden") return@mapNotNull null

                val availabilityScore = recentAvailScores[media.id] ?: 0.0
                val reasons = mutableListOf<String>()
                reasons.add("Release year: ${media.releaseYear}")
                if (availabilityScore > 0.0) {
                    reasons.add("Playable streams available: +${availabilityScore.toInt()}")
                }

                RecommendationItem(
                    id = media.id,
                    type = media.type,
                    title = media.title,
                    score = (media.rating ?: 5.0) + (media.releaseYear ?: 2000) / 100.0 + availabilityScore,
                    reason = "Recently added to catalog",
                    scoreBreakdown = ScoreBreakdown(
                        availabilityScore = availabilityScore,
                        reasons = reasons
                    ),
                    subtitle = media.releaseYear?.toString(),
                    posterUrl = media.posterUrl,
                    genres = media.genres.toGenreList(),
                    source = media.source,
                    externalIds = media.externalIds()
                )
            }.sortedByDescending { it.score }.take(cleanLimit)
            if (recentItems.isNotEmpty()) {
                rows.add(RecommendationRow("Recently Added", "recently_added", recentItems.toImmutableList()))
            }
        }

        // 5. Top Rated Row
        val topMedia = dao.getTopRated(cleanLimit * 3)
        if (topMedia.isNotEmpty()) {
            val topAvailScores = batchAvailabilityScores(topMedia.map { it.id }, preferredAudio, preferredSub)
            val topItems = topMedia.mapNotNull { media ->
                val feedback = feedbacks[media.id]
                if (feedback?.feedbackType == "not_interested" || feedback?.feedbackType == "hidden") return@mapNotNull null

                val availabilityScore = topAvailScores[media.id] ?: 0.0
                val reasons = mutableListOf<String>()
                reasons.add("Rating: ${media.rating}")
                if (availabilityScore > 0.0) {
                    reasons.add("Playable streams available: +${availabilityScore.toInt()}")
                }

                RecommendationItem(
                    id = media.id,
                    type = media.type,
                    title = media.title,
                    score = (media.rating ?: 0.0) + availabilityScore,
                    reason = "Highly rated by users",
                    scoreBreakdown = ScoreBreakdown(
                        availabilityScore = availabilityScore,
                        reasons = reasons
                    ),
                    subtitle = media.rating?.let { "Rating $it" },
                    posterUrl = media.posterUrl,
                    genres = media.genres.toGenreList(),
                    source = media.source,
                    externalIds = media.externalIds()
                )
            }.sortedByDescending { it.score }.take(cleanLimit)
            if (topItems.isNotEmpty()) {
                rows.add(RecommendationRow("Top Rated", "top_rated", topItems.toImmutableList()))
            }
        }

        // 6. Live TV Row
        val channels = dao.getAllChannels(cleanLimit * 3)
        if (channels.isNotEmpty()) {
            val channelStates = dao.getUserChannelStatesForProfile(profileId).associateBy { it.channelId }
            val now = System.currentTimeMillis()
            val currentEpg = dao.getCurrentEpgPrograms(now).associateBy { it.channelId }

            val channelItems = channels.mapNotNull { chan ->
                val state = channelStates[chan.id]
                if (state?.isHidden == true) return@mapNotNull null
                val feedback = feedbacks[chan.id]
                if (feedback?.feedbackType == "not_interested" || feedback?.feedbackType == "hidden") return@mapNotNull null

                val epg = currentEpg[chan.id]

                var score = 5.0
                val reasons = mutableListOf<String>()

                if (state != null) {
                    if (state.isFavorite) {
                        score += 30.0
                        reasons.add("Favorite channel")
                    }
                    if (state.watchCount > 0) {
                        score += 10.0 + minOf(state.watchCount * 2.0, 15.0)
                        reasons.add("Previously watched")
                    }
                }

                val reasonText = if (epg != null) {
                    "Live Now: ${epg.title}"
                } else {
                    "Live channel"
                }

                RecommendationItem(
                    id = chan.id,
                    type = "channel",
                    title = chan.name,
                    score = score,
                    reason = reasonText,
                    scoreBreakdown = ScoreBreakdown(
                        reasons = reasons
                    ),
                    subtitle = chan.category,
                    posterUrl = chan.logoUrl,
                    source = chan.providerId
                )
            }.sortedByDescending { it.score }.take(cleanLimit)

            if (channelItems.isNotEmpty()) {
                rows.add(RecommendationRow("Live TV Recommendations", "live_tv", channelItems.toImmutableList()))
            }
        }

        if (rows.isNotEmpty()) {
            cacheRecommendations(profileId, "home_rows", rows)
        }
        rows
    }

    suspend fun getTasteProfile(
        profileId: String,
        preferenceSignals: List<UserPreferenceSignal> = emptyList()
    ): TasteProfile = withContext(Dispatchers.IO) {
        TasteProfileBuilder.buildTasteProfile(dao, profileId, preferenceSignals)
    }

    suspend fun getPersonalizedRecommendations(
        profileId: String,
        limit: Int = 15,
        preferenceSignals: List<UserPreferenceSignal> = emptyList()
    ): List<RecommendationItem> = withContext(Dispatchers.IO) {
        val tasteProfile = getTasteProfile(profileId, preferenceSignals)
        val isLowMem = isLowMemory()
        val candidates = getRecommendationCandidates(if (isLowMem) 50 else 200)
        val states = dao.getUserItemStatesForProfile(profileId).associateBy { it.itemId }
        val feedbacks = dao.getUserFeedbacksForProfile(profileId).associateBy { it.itemId }
        val cleanLimit = if (isLowMem) minOf(limit, 8) else limit

        val profile = dao.getProfile(profileId)
        val preferredAudio = profile?.preferredAudioLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val preferredSub = profile?.preferredSubtitleLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val filteredCandidates = candidates.filter { media ->
            val state = states[media.id]
            if (state != null && (state.isHidden || state.isCompleted)) return@filter false

            val feedback = feedbacks[media.id]
            if (feedback?.feedbackType == "not_interested" || feedback?.feedbackType == "hidden") return@filter false

            true
        }
        val candidatesById = filteredCandidates.associateBy { it.id }
        val streamAvailabilityByMedia = batchMediaAvailability(
            filteredCandidates.map { it.id },
            preferredAudio,
            preferredSub,
        )

        val ranked = HybridRecommendationRanker.rank(
            tasteProfile = tasteProfile,
            candidates = filteredCandidates,
            userItemStates = states,
            limit = filteredCandidates.size,
            enrichmentFeatures = { mediaId ->
                val provider = ProviderManager.extractFeatures(mediaId)
                val streamSignal = streamAvailabilityByMedia[mediaId]?.normalizedSignal ?: 0.0
                provider.copy(streamRankAvailability = streamSignal)
            }
        )

        ranked.map { recItem ->
            val media = candidatesById[recItem.id]
            recItem.copy(
                subtitle = media?.releaseYear?.toString(),
                posterUrl = media?.posterUrl,
                genres = media?.genres?.toGenreList().orEmpty(),
                source = media?.source,
                externalIds = media?.externalIds().orEmpty(),
            )
        }.sortedByDescending { it.score }.take(cleanLimit)
    }

    suspend fun getMoreLikeThis(profileId: String, itemId: String, limit: Int = 15): List<RecommendationItem> = withContext(Dispatchers.IO) {
        val target = dao.getMediaItem(itemId) ?: return@withContext emptyList()
        val isLowMem = isLowMemory()
        val states = dao.getUserItemStatesForProfile(profileId).associateBy { it.itemId }
        val feedbacks = dao.getUserFeedbacksForProfile(profileId).associateBy { it.itemId }
        val cleanLimit = if (isLowMem) minOf(limit, 8) else limit

        val profile = dao.getProfile(profileId)
        val preferredAudio = profile?.preferredAudioLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val preferredSub = profile?.preferredSubtitleLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val candidates = getRecommendationCandidates(if (isLowMem) 50 else 200).filter { media ->
            if (media.type != target.type || media.id == target.id) return@filter false
            val state = states[media.id]
            if (state?.isHidden == true) return@filter false
            val feedback = feedbacks[media.id]
            if (feedback?.feedbackType == "not_interested" || feedback?.feedbackType == "hidden") return@filter false
            true
        }
        val candidatesById = candidates.associateBy { it.id }

        val providerSimilarIds = ProviderManager.getCacheStore()
            ?.getSimilar(target.id)
            ?.map { it.similarMediaId }
            ?.toSet()
            .orEmpty()

        val extIds = target.externalIds()
        val apiId = when {
            target.id.startsWith("tt") || target.id.startsWith("kitsu:") -> target.id
            target.externalId?.startsWith("tt") == true || target.externalId?.startsWith("kitsu:") == true -> target.externalId
            extIds.containsKey("imdb") -> extIds["imdb"]!!
            extIds.containsKey("kitsu") -> "kitsu:${extIds["kitsu"]}"
            else -> target.id
        }

        val cachedIds = similarCache.get(apiId)
        val globalSimilarIds = if (cachedIds != null) {
            cachedIds.toSet()
        } else {
            val fetched = kotlinx.coroutines.withTimeoutOrNull(1000) {
                com.example.calmsource.core.network.BackendApiClient.getSimilar(target.type, apiId)
            }
            val mappedIds = fetched?.map { it.id }.orEmpty()
            if (fetched != null) {
                similarCache.put(apiId, mappedIds)
            }
            mappedIds.toSet()
        }

        val similar = SimilarityFinder.findSimilar(
            target = target,
            candidates = candidates,
            limit = candidates.size,
            providerSimilarIds = providerSimilarIds,
            globalSimilarIds = globalSimilarIds
        )
        val similarIds = similar.map { it.id }
        val availScores = batchAvailabilityScores(similarIds, preferredAudio, preferredSub)

        similar.map { item ->
            val media = candidatesById[item.id]
            val availabilityScore = availScores[item.id] ?: 0.0
            val newReasons = item.scoreBreakdown.reasons.toMutableList()
            if (availabilityScore > 0.0) {
                newReasons.add("Playable streams available: +${availabilityScore.toInt()}")
            }
            item.copy(
                score = item.score + availabilityScore,
                subtitle = media?.releaseYear?.toString(),
                posterUrl = media?.posterUrl,
                genres = media?.genres?.toGenreList().orEmpty(),
                source = media?.source,
                externalIds = media?.externalIds().orEmpty(),
                scoreBreakdown = item.scoreBreakdown.copy(
                    availabilityScore = availabilityScore,
                    reasons = newReasons
                )
            )
        }.sortedByDescending { it.score }.take(cleanLimit)
    }

    suspend fun getLiveRecommendations(
        profileId: String,
        limit: Int = 15,
        preferenceSignals: List<UserPreferenceSignal> = emptyList()
    ): List<RecommendationItem> = withContext(Dispatchers.IO) {
        val tasteProfile = getTasteProfile(profileId, preferenceSignals)
        val isLowMem = isLowMemory()
        val channelsLimit = if (isLowMem) 30 else 100
        val channels = dao.getAllChannels(channelsLimit)
        val channelStates = dao.getUserChannelStatesForProfile(profileId).associateBy { it.channelId }
        val feedbacks = dao.getUserFeedbacksForProfile(profileId).associateBy { it.itemId }
        val watchEvents = dao.getChannelWatchEvents(profileId)
        val now = System.currentTimeMillis()
        val currentEpg = dao.getCurrentEpgPrograms(now).associateBy { it.channelId }
        val cleanLimit = if (isLowMem) minOf(limit, 8) else limit

        val filteredChannels = channels.filter { chan ->
            val state = channelStates[chan.id]
            if (state?.isHidden == true) return@filter false
            val feedback = feedbacks[chan.id]
            if (feedback?.feedbackType == "not_interested" || feedback?.feedbackType == "hidden") return@filter false
            true
        }

        LiveTvRecommender.recommend(tasteProfile, filteredChannels, channelStates, watchEvents, currentEpg, now, cleanLimit)
    }

    suspend fun registerPack(pack: DiscoveryPack) = DiscoveryDatabaseGuard.write("registerPack") {
        val entity = DiscoveryPackEntity(
            id = pack.id,
            name = pack.name,
            description = pack.description,
            manifestUrl = pack.manifestUrl,
            checksum = null,
            isInstalled = pack.isInstalled,
            installedAt = pack.installedAt
        )
        dao.upsertDiscoveryPack(entity)
    }

    suspend fun installPack(packId: String, items: List<MediaItem>, streams: List<MediaStream>) = withContext(Dispatchers.IO) {
        // Enforce source = packId
        val itemsWithSource = items.map { it.copy(source = packId) }
        val resolvedMap = upsertMediaItems(itemsWithSource)

        val streamsWithSource = streams.map { stream ->
            val resolvedId = resolvedMap[stream.mediaItemId] ?: stream.mediaItemId
            stream.copy(mediaItemId = resolvedId, source = packId)
        }

        // Upsert streams
        upsertMediaStreams(streamsWithSource)

        val existing = dao.getDiscoveryPack(packId) ?: DiscoveryPackEntity(
            id = packId,
            name = "Pack $packId",
            description = null,
            manifestUrl = "",
            checksum = null,
            isInstalled = true,
            installedAt = System.currentTimeMillis()
        )
        val updated = existing.copy(isInstalled = true, installedAt = System.currentTimeMillis())
        dao.upsertDiscoveryPack(updated)
        bumpRecommendationCacheVersionInternal()
    }

    suspend fun uninstallPack(packId: String) = DiscoveryDatabaseGuard.write("uninstallPack") {
        // Delete external IDs for items from this pack (must happen before media items delete)
        dao.deleteExternalIdsBySource(packId)
        // Delete media items, streams, and embeddings linked to this pack
        dao.deleteEmbeddingsBySource(packId)
        dao.deleteMediaItemsBySource(packId)
        dao.deleteMediaStreamsBySource(packId)

        // Update pack status to not installed
        val existing = dao.getDiscoveryPack(packId)
        if (existing != null) {
            val updated = existing.copy(isInstalled = false, installedAt = null)
            dao.upsertDiscoveryPack(updated)
        }
        bumpRecommendationCacheVersionInternal()
    }

    suspend fun getAvailablePacks(): List<DiscoveryPack> = withContext(Dispatchers.IO) {
        dao.getAllDiscoveryPacks().map { entity ->
            DiscoveryPack(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                manifestUrl = entity.manifestUrl,
                isInstalled = entity.isInstalled,
                installedAt = entity.installedAt
            )
        }
    }

    @Volatile
    var lowMemoryMode: Boolean = false
        set(value) {
            field = value
            isLowMemoryLoaded = true // prevent stale DB read from overwriting
        }

    @Volatile
    private var isLowMemoryLoaded = false

    private val lowMemoryLock = Any()

    private suspend fun isLowMemory(): Boolean = withContext(Dispatchers.IO) {
        if (!isLowMemoryLoaded) {
            synchronized(lowMemoryLock) {
                if (!isLowMemoryLoaded) {
                    lowMemoryMode = dao.getSetting("low_memory_mode")?.toBoolean() ?: false
                    isLowMemoryLoaded = true
                }
            }
        }
        lowMemoryMode
    }

    suspend fun cacheRecommendations(profileId: String, key: String, data: List<RecommendationRow>) = DiscoveryDatabaseGuard.write("cacheRecommendations:$key") {
        val currentVersion = getRecommendationCacheVersionInternal()
        val wrapper = RecommendationCacheWrapper(version = currentVersion, rows = data)
        val serialized = Json.encodeToString(wrapper)
        val entity = RecommendationCacheEntity(
            profileId = profileId,
            cacheKey = key,
            data = serialized,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertRecommendationCache(entity)
    }

    suspend fun getCachedRecommendations(profileId: String, key: String, ttlMs: Long): List<RecommendationRow>? = withContext(Dispatchers.IO) {
        val cached = dao.getRecommendationCache(profileId, key) ?: return@withContext null
        val age = System.currentTimeMillis() - cached.updatedAt
        if (age > ttlMs) {
            dao.deleteRecommendationCache(profileId, key)
            return@withContext null
        }
        try {
            val wrapper = Json.decodeFromString<RecommendationCacheWrapper>(cached.data)
            val currentVersion = getRecommendationCacheVersionInternal()
            if (wrapper.version != currentVersion) {
                dao.deleteRecommendationCache(profileId, key)
                return@withContext null
            }
            wrapper.rows
        } catch (e: Exception) {
            // Fallback for old cache format
            try {
                val rows = Json.decodeFromString<List<RecommendationRow>>(cached.data)
                dao.deleteRecommendationCache(profileId, key)
                rows
            } catch (e2: Exception) {
                dao.deleteRecommendationCache(profileId, key)
                null
            }
        }
    }

    private fun getRecommendationCacheVersionInternal(): Int {
        return dao.getSetting("recommendation_cache_version")?.toIntOrNull() ?: 0
    }

    private fun bumpRecommendationCacheVersionInternal() {
        val current = getRecommendationCacheVersionInternal()
        dao.upsertSetting(EngineSettingEntity("recommendation_cache_version", (current + 1).toString()))
    }

    suspend fun performMaintenance() {
        DiscoveryDatabaseGuard.write("performMaintenance") {
            val now = System.currentTimeMillis()
            // 1. Clear expired EPG programs (ended before now)
            dao.clearExpiredEpgPrograms(now)

            // 2. Prune search events older than 30 days
            val searchCutoff = now - (30L * 24 * 60 * 60 * 1000L)
            dao.pruneSearchEvents(searchCutoff)

            // 3. Prune watch events older than 90 days
            val watchCutoff = now - (90L * 24 * 60 * 60 * 1000L)
            dao.pruneWatchEvents(watchCutoff)

            // 4. Clear expired recommendation cache (older than 24 hours to free space)
            val cacheCutoff = now - (24L * 60 * 60 * 1000L)
            dao.clearExpiredRecommendationCache(cacheCutoff)
        }
        // Provider stores use their own locking — call outside the DAO guard block
        ProviderManager.getCacheStore()?.pruneExpired(System.currentTimeMillis())
        ProviderManager.getTelemetryStore()?.pruneOldFailures()
        ProviderManager.getTelemetryStore()?.pruneOldUsage()
    }

    suspend fun searchSemantic(profileId: String, query: String, limit: Int = 10): List<SearchResult> = withContext(Dispatchers.IO) {
        val isLowMem = isLowMemory()
        val searchLimit = if (isLowMem) 100 else 500

        // 1. Retrieve candidate set from FTS5
        val normQuery = MetadataNormalizer.normalizeSearchQuery(query)
        val rawResults = if (normQuery.isNotEmpty()) {
            val db = database
            val ftsHits = if (db != null) {
                try {
                    FtsIndexManager.search(db.openHelper.readableDatabase, normQuery, searchLimit)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            if (ftsHits.isEmpty()) {
                // Fallback prefix search
                val matched = mutableListOf<Map<String, String>>()
                val media = dao.getMediaItemsByPrefix(normQuery, searchLimit)
                val channels = dao.getChannelsByPrefix(normQuery, searchLimit)
                matched.addAll(media.map {
                    mapOf(
                        "id" to it.id,
                        "type" to it.type,
                        "title" to it.title,
                        "normalized_title" to it.normalizedTitle,
                        "overview" to (it.overview ?: ""),
                        "genres" to it.genres,
                        "cast_director" to "${it.cast},${it.director ?: ""}"
                    )
                })
                matched.addAll(channels.map {
                    mapOf(
                        "id" to it.id,
                        "type" to "channel",
                        "title" to it.name,
                        "normalized_title" to MetadataNormalizer.normalizeChannelName(it.name),
                        "overview" to (it.category ?: ""),
                        "genres" to "",
                        "cast_director" to ""
                    )
                })
                matched
            } else {
                ftsHits
            }
        } else {
            emptyList()
        }

        if (rawResults.isEmpty()) return@withContext emptyList()

        // 2. Rank FTS candidates with FTS score + vector similarity score
        SearchRanker.rankSearchResults(
            dao, profileId, query, rawResults, limit, isTelevisionFormFactor()
        )
    }

    suspend fun getSemanticRecommendations(profileId: String, limit: Int = 10): List<RecommendationItem> = withContext(Dispatchers.IO) {
        val states = dao.getUserItemStatesForProfile(profileId).associateBy { it.itemId }
        val interactedItemIds = states.keys.toList()
        if (interactedItemIds.isEmpty()) {
            return@withContext emptyList()
        }

        // Fetch embeddings for interacted items
        val interactedEmbeddings = dao.getEmbeddingsByIds(interactedItemIds).associateBy { it.itemId }
        if (interactedEmbeddings.isEmpty()) {
            return@withContext emptyList()
        }

        val latestWatchEvents = dao.getLatestWatchEventsForProfile(profileId).associateBy { it.itemId }

        // Compute user taste profile interest vector
        val preferenceVector = FloatArray(Vectorizer.DIMENSIONS)
        var totalWeight = 0.0
        val now = System.currentTimeMillis()

        for (emb in interactedEmbeddings.values) {
            val state = states[emb.itemId] ?: continue
            var weight = 1.0

            if (state.isHidden) {
                weight = -5.0
            } else {
                if (state.isFavorite) {
                    weight += 5.0
                }
                if (state.isCompleted) {
                    weight += 3.0
                }

                if (state.durationMs > 0) {
                    val progressRatio = state.progressMs.toDouble() / state.durationMs.toDouble()
                    weight += when {
                        progressRatio >= 0.7 -> 2.0
                        progressRatio >= 0.2 -> 1.0
                        else -> 0.0
                    }
                }

                // Check quick skip event
                val lastEvent = latestWatchEvents[emb.itemId]
                if (lastEvent?.eventType == "quick_skip") {
                    weight = -2.0
                }
            }

            // Time decay: decay = exp(-0.02 * ageDays)
            val lastWatched = state.lastWatchedAt ?: 0L
            if (lastWatched > 0) {
                val ageDays = (now - lastWatched) / (1000.0 * 60 * 60 * 24)
                val decay = Math.exp(-0.02 * ageDays)
                weight *= decay
            }

            val vec = Vectorizer.bytesToVector(emb.embedding)
            for (i in 0 until Vectorizer.DIMENSIONS) {
                preferenceVector[i] += (vec[i] * weight).toFloat()
            }
            totalWeight += Math.abs(weight)
        }

        if (totalWeight == 0.0) return@withContext emptyList()

        // Normalize preference vector
        var sumSq = 0f
        for (v in preferenceVector) { sumSq += v * v }
        if (sumSq > 0f) {
            val norm = Math.sqrt(sumSq.toDouble()).toFloat()
            for (i in 0 until Vectorizer.DIMENSIONS) {
                preferenceVector[i] /= norm
            }
        }

        // Candidate Generation: fetch top candidates (no full scan)
        val isLowMem = isLowMemory()
        val candidateLimit = if (isLowMem) 100 else 1000
        val candidates = getRecommendationCandidates(candidateLimit).filter { it.id !in interactedItemIds }
        if (candidates.isEmpty()) return@withContext emptyList()

        val candidateIds = candidates.map { it.id }
        val candidateEmbeddings = candidateIds.chunkedFlatMap(SQLITE_MAX_VARS) { dao.getEmbeddingsByIds(it) }
        if (candidateEmbeddings.isEmpty()) return@withContext emptyList()

        val itemsMap = candidates.associateBy { it.id }

        val feedbacks = dao.getUserFeedbacksForProfile(profileId).associateBy { it.itemId }
        val profile = dao.getProfile(profileId)
        val preferredAudio = profile?.preferredAudioLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val preferredSub = profile?.preferredSubtitleLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val availScores = batchAvailabilityScores(candidateIds, preferredAudio, preferredSub)

        val recommended = candidateEmbeddings.mapNotNull { emb ->
            val item = itemsMap[emb.itemId] ?: return@mapNotNull null

            // Exclude hidden/not_interested feedback
            val feedback = feedbacks[item.id]
            if (feedback?.feedbackType == "not_interested" || feedback?.feedbackType == "hidden") return@mapNotNull null

            val itemVec = Vectorizer.bytesToVector(emb.embedding)
            val similarity = Vectorizer.cosineSimilarity(preferenceVector, itemVec)
            if (similarity <= 0.1) return@mapNotNull null // Threshold of 0.1

            val availabilityScore = availScores[item.id] ?: 0.0
            val score = similarity * 50.0 + availabilityScore
            val reasons = mutableListOf<String>()
            reasons.add("Based on your taste profile vector")
            if (availabilityScore > 0.0) {
                reasons.add("Playable streams available: +${availabilityScore.toInt()}")
            }

            RecommendationItem(
                id = item.id,
                type = item.type,
                title = item.title,
                score = score,
                reason = "Similar to content you enjoy",
                scoreBreakdown = ScoreBreakdown(
                    ftsScore = similarity,
                    availabilityScore = availabilityScore,
                    reasons = reasons
                ),
                subtitle = item.releaseYear?.toString(),
                posterUrl = item.posterUrl,
                genres = item.genres.toGenreList(),
                source = item.source,
                externalIds = item.externalIds()
            )
        }
        .sortedByDescending { it.score }
        .take(limit)

        recommended
    }

    private fun getRecommendationCandidates(limit: Int): List<MediaItemEntity> {
        val cleanLimit = limit.coerceAtLeast(1)
        val sidePoolLimit = (cleanLimit / 2).coerceAtLeast(1)
        val searchCandidates = dao.getSearchCandidates(cleanLimit)
        val recentCandidates = dao.getRecentlyAdded(sidePoolLimit)
        val topRatedCandidates = dao.getTopRated(sidePoolLimit)

        return buildList {
            addAll(searchCandidates.take(sidePoolLimit))
            addAll(recentCandidates)
            addAll(topRatedCandidates)
            addAll(searchCandidates.drop(sidePoolLimit))
        }
            .distinctBy { it.id }
            .take(cleanLimit * 2)
    }

    private fun MediaItemEntity.externalIds(): Map<String, String> {
        return runCatching {
            Json.decodeFromString<Map<String, String>>(externalIdsJson)
        }.getOrDefault(emptyMap())
    }

    private fun MediaItemEntity.toRecommendationItem(
        reason: String,
        score: Double
    ): RecommendationItem {
        return RecommendationItem(
            id = id,
            type = type,
            title = title,
            score = score,
            reason = reason,
            scoreBreakdown = ScoreBreakdown(reasons = listOf(reason)),
            subtitle = releaseYear?.toString(),
            posterUrl = posterUrl,
            genres = genres.toGenreList(),
            source = source,
            externalIds = externalIds()
        )
    }

    private fun String.toGenreList(): List<String> = split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(String::lowercase)

    suspend fun lookupMediaPosterUrl(mediaId: String): String? = withContext(Dispatchers.IO) {
        dao.getMediaItem(mediaId)?.posterUrl?.takeIf { it.isNotBlank() }
    }

    suspend fun calculateSearchQuality(profileId: String, query: String, targetItemId: String): SearchQualityMetrics = withContext(Dispatchers.IO) {
        val results = fullSearch(query, profileId, 10)
        val rank = results.indexOfFirst { it.id == targetItemId } + 1
        val mrr = if (rank > 0) 1.0 / rank else 0.0
        val top3 = results.take(3)
        val isHit = top3.any { it.id == targetItemId }
        val hitAt3 = if (isHit) 1.0 else 0.0
        val precisionAt3 = if (isHit) 1.0 / 3.0 else 0.0
        SearchQualityMetrics(mrr, hitAt3, precisionAt3)
    }

    suspend fun calculateRecommendationDiversity(profileId: String): Double = withContext(Dispatchers.IO) {
        val rows = getHomeRows(profileId, forceRefresh = true)
        val allItems = rows.flatMap { it.items }
        if (allItems.isEmpty()) return@withContext 0.0

        val itemIds = allItems.map { it.id }
        val items = dao.getMediaItemsByIds(itemIds)
        val allGenres = items.flatMap { it.genres.split(",").map { g -> g.trim().lowercase() }.filter { g -> g.isNotEmpty() } }

        val total = allGenres.size
        if (total <= 1) return@withContext 0.0

        val counts = allGenres.groupingBy { it }.eachCount()
        val sumSquared = counts.values.sumOf { count ->
            (count.toDouble() / total) * (count.toDouble() / total)
        }
        1.0 - sumSquared
    }

    suspend fun rankStreams(profileId: String, mediaId: String, strategy: SortingPreference = SortingPreference.BEST_MATCH): List<MediaStream> = withContext(Dispatchers.IO) {
        val streamEntities = dao.getStreamsForMediaItem(mediaId)
        val prefs = UserPreferencesRepository.preferences.value
        val ranked = com.example.calmsource.core.discoveryengine.ranking.StreamRanker.rank(
            dao = dao,
            profileId = profileId,
            streams = streamEntities,
            strategy = strategy,
            prefs = prefs,
            isTelevision = isTelevisionFormFactor(),
        )
        ranked.map { entity ->
            MediaStream(
                id = entity.id,
                mediaItemId = entity.mediaId,
                title = entity.title,
                url = entity.url,
                resolution = entity.resolution,
                codec = entity.codec,
                quality = entity.quality,
                sizeInBytes = entity.sizeInBytes,
                language = entity.language,
                isSubbed = entity.isSubbed,
                isDubbed = entity.isDubbed,
                source = entity.source
            )
        }
    }

    suspend fun trackPlaybackEvent(streamId: String, mediaId: String, source: String, status: String, reason: String? = null) = DiscoveryDatabaseGuard.write("trackPlaybackEvent") {
        val entity = StreamPlaybackHistoryEntity(
            streamId = streamId,
            mediaId = mediaId,
            source = source,
            status = status,
            reason = reason,
            timestamp = System.currentTimeMillis()
        )
        dao.insertPlaybackHistoryEntry(entity)
    }

    suspend fun trackFeedback(profileId: String, itemId: String, feedbackType: String) = DiscoveryDatabaseGuard.write("trackFeedback") {
        val entity = UserFeedbackEntity(
            profileId = profileId,
            itemId = itemId,
            feedbackType = feedbackType,
            timestamp = System.currentTimeMillis()
        )
        dao.upsertUserFeedback(entity)
        if (feedbackType == "not_interested" || feedbackType == "hidden") {
            dao.clearRecommendationCacheForProfile(profileId)
        }
    }

    suspend fun updateProfileLanguages(profileId: String, audioLangs: List<String>, subtitleLangs: List<String>) = DiscoveryDatabaseGuard.write("updateProfileLanguages") write@ {
        val existing = dao.getProfile(profileId) ?: return@write
        val updated = existing.copy(
            preferredAudioLanguages = audioLangs.joinToString(","),
            preferredSubtitleLanguages = subtitleLangs.joinToString(",")
        )
        dao.upsertProfile(updated)
        dao.clearRecommendationCacheForProfile(profileId)
    }

    /**
     * Batch-computes unified stream availability for a set of media IDs.
     * Uses 3 total queries instead of 2N+1 per item (where N = number of streams per item).
     * Chunks large ID lists to stay within SQLITE_MAX_VARIABLE_NUMBER (999).
     */
    private suspend fun batchMediaAvailability(
        mediaIds: List<String>,
        preferredAudio: List<String>,
        preferredSub: List<String>,
    ): Map<String, MediaAvailabilityResult> {
        if (mediaIds.isEmpty()) return emptyMap()

        val prefs = UserPreferencesRepository.preferences.value
        val deviceProfile = DeviceStreamProfile.forPlayback(isTelevisionFormFactor(), prefs)

        val allStreams = mediaIds.chunkedFlatMap(SQLITE_MAX_VARS) { dao.getStreamsForMediaItems(it) }
        val streamsByMedia = allStreams.groupBy { it.mediaId }

        val allStreamIds = allStreams.map { it.id }
        val successCountMap = if (allStreamIds.isNotEmpty()) {
            allStreamIds.chunkedFlatMap(SQLITE_MAX_VARS) { dao.getPlaybackSuccessCounts(it) }
                .associate { it.streamId to it.count }
        } else emptyMap()
        val failureCountMap = if (allStreamIds.isNotEmpty()) {
            allStreamIds.chunkedFlatMap(SQLITE_MAX_VARS) { dao.getPlaybackFailureCounts(it) }
                .associate { it.streamId to it.count }
        } else emptyMap()
        val healthById = StreamScoringSupport.prefetchSourceHealth(allStreamIds)

        return mediaIds.associateWith { mediaId ->
            val providerCacheAvailability = ProviderManager.extractFeatures(mediaId).availabilityScore
            val streams = streamsByMedia[mediaId].orEmpty()
            MediaAvailabilityScorer.scoreFromStreams(
                streams = streams.map { it.toStreamSource() },
                prefs = prefs,
                preferredAudio = preferredAudio,
                preferredSub = preferredSub,
                streamSuccessCount = { streamId -> successCountMap[streamId] ?: 0 },
                streamFailureCount = { streamId -> failureCountMap[streamId] ?: 0 },
                sourceHealthById = healthById,
                providerCacheAvailability = providerCacheAvailability,
                deviceProfile = deviceProfile,
            )
        }
    }

    private suspend fun batchAvailabilityScores(
        mediaIds: List<String>,
        preferredAudio: List<String>,
        preferredSub: List<String>,
    ): Map<String, Double> {
        return batchMediaAvailability(mediaIds, preferredAudio, preferredSub)
            .mapValues { it.value.additiveScore }
    }

    private suspend fun getAvailabilityScore(
        mediaId: String,
        preferredAudio: List<String>,
        preferredSub: List<String>,
    ): Double {
        return batchAvailabilityScores(listOf(mediaId), preferredAudio, preferredSub)[mediaId] ?: 0.0
    }

    private fun isTelevisionFormFactor(): Boolean {
        val pm = context.packageManager ?: return false
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

    companion object {
        /**
         * SQLite SQLITE_MAX_VARIABLE_NUMBER default is 999.
         * All IN-clause queries must chunk their ID lists to stay below this.
         */
        private const val SQLITE_MAX_VARS = 900
    }
}

/**
 * Splits a list into chunks of [chunkSize] and flat-maps each chunk through [transform].
 * Prevents SQLiteException from exceeding SQLITE_MAX_VARIABLE_NUMBER in IN clauses.
 */
private inline fun <T, R> List<T>.chunkedFlatMap(chunkSize: Int, transform: (List<T>) -> List<R>): List<R> {
    if (size <= chunkSize) return transform(this)
    return chunked(chunkSize).flatMap(transform)
}

@kotlinx.serialization.Serializable
private data class RecommendationCacheWrapper(
    val version: Int,
    val rows: List<RecommendationRow>
)

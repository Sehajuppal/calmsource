package com.example.calmsource.core.database.repository

import androidx.room.withTransaction
import com.example.calmsource.core.database.CalmSourceDatabase
import com.example.calmsource.core.database.dao.UserMemoryDao
import com.example.calmsource.core.database.entity.ContinueWatchingEntity
import com.example.calmsource.core.database.entity.FavoriteEntity
import com.example.calmsource.core.database.entity.PreferenceSignalEntity
import com.example.calmsource.core.database.entity.RecentChannelEntity
import com.example.calmsource.core.database.entity.SearchHistoryEntity
import com.example.calmsource.core.database.entity.WatchHistoryEntity
import com.example.calmsource.core.model.ContinueWatchingItem
import com.example.calmsource.core.model.FavoriteItem
import com.example.calmsource.core.model.RecentChannelItem
import com.example.calmsource.core.model.SearchHistoryItem
import com.example.calmsource.core.model.UserMemoryContentType
import com.example.calmsource.core.model.UserMemoryPrivacy
import com.example.calmsource.core.model.UserMemoryReference
import com.example.calmsource.core.model.UserPreferenceSignal
import com.example.calmsource.core.model.UserPreferenceSignalType
import com.example.calmsource.core.model.WatchHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserMemoryRepository {
    fun observeContinueWatching(profileId: String = "default"): Flow<List<ContinueWatchingItem>>
    fun observeFavorites(profileId: String = "default"): Flow<List<FavoriteItem>>
    fun observeIsFavorite(itemKey: String, profileId: String = "default"): Flow<Boolean>
    fun observeWatchHistory(profileId: String = "default"): Flow<List<WatchHistoryItem>>
    fun observeRecentChannels(profileId: String = "default"): Flow<List<RecentChannelItem>>
    fun observeLastWatchedChannel(profileId: String = "default"): Flow<RecentChannelItem?>
    fun observeSearchHistory(profileId: String = "default"): Flow<List<SearchHistoryItem>>
    fun observePreferenceSignals(profileId: String = "default"): Flow<List<UserPreferenceSignal>>

    suspend fun upsertContinueWatching(
        reference: UserMemoryReference,
        progressMs: Long,
        durationMs: Long,
        updatedAt: Long = System.currentTimeMillis(),
        profileId: String = "default"
    )

    suspend fun removeContinueWatching(itemKey: String, profileId: String = "default")
    suspend fun clearContinueWatching(profileId: String = "default")

    suspend fun toggleFavorite(
        reference: UserMemoryReference,
        timestamp: Long = System.currentTimeMillis(),
        profileId: String = "default"
    ): Boolean

    suspend fun setFavorite(
        reference: UserMemoryReference,
        favorite: Boolean,
        timestamp: Long = System.currentTimeMillis(),
        profileId: String = "default"
    )

    suspend fun removeFavorite(itemKey: String, profileId: String = "default")
    suspend fun clearFavorites(profileId: String = "default")

    suspend fun recordWatchHistory(
        reference: UserMemoryReference,
        progressMs: Long,
        durationMs: Long,
        watchedAt: Long = System.currentTimeMillis(),
        profileId: String = "default"
    )

    suspend fun removeWatchHistory(itemKey: String, profileId: String = "default")
    suspend fun clearWatchHistory(profileId: String = "default")

    suspend fun recordRecentChannel(
        reference: UserMemoryReference,
        watchedAt: Long = System.currentTimeMillis(),
        profileId: String = "default"
    )

    suspend fun removeRecentChannel(itemKey: String, profileId: String = "default")
    suspend fun clearRecentChannels(profileId: String = "default")

    suspend fun recordSearch(
        query: String,
        searchedAt: Long = System.currentTimeMillis(),
        profileId: String = "default"
    ): Boolean

    suspend fun removeSearch(query: String, profileId: String = "default"): Boolean
    suspend fun clearSearchHistory(profileId: String = "default")

    suspend fun incrementPreferenceSignal(
        signalType: UserPreferenceSignalType,
        signalKey: String,
        incrementBy: Long = 1L,
        signaledAt: Long = System.currentTimeMillis(),
        profileId: String = "default"
    )

    suspend fun clearPreferenceSignals(profileId: String = "default")
}

class RoomUserMemoryRepository(
    private val database: CalmSourceDatabase,
    private val dao: UserMemoryDao = database.userMemoryDao()
) : UserMemoryRepository {

    override fun observeContinueWatching(profileId: String): Flow<List<ContinueWatchingItem>> {
        return dao.observeContinueWatching(limit = CONTINUE_WATCHING_LIMIT, profileId = profileId)
            .map { items -> items.map(ContinueWatchingEntity::toDomain) }
    }

    override fun observeFavorites(profileId: String): Flow<List<FavoriteItem>> {
        return dao.observeFavorites(limit = FAVORITES_LIMIT, profileId = profileId).map { items -> items.map(FavoriteEntity::toDomain) }
    }

    override fun observeIsFavorite(itemKey: String, profileId: String): Flow<Boolean> {
        val safeItemKey = UserMemoryPrivacy.requireSafeIdentifier(itemKey, "itemKey")
        return dao.observeFavorite(itemKey = safeItemKey, profileId = profileId).map { it != null }
    }

    override fun observeWatchHistory(profileId: String): Flow<List<WatchHistoryItem>> {
        return dao.observeWatchHistory(limit = WATCH_HISTORY_LIMIT, profileId = profileId)
            .map { items -> items.map(WatchHistoryEntity::toDomain) }
    }

    override fun observeRecentChannels(profileId: String): Flow<List<RecentChannelItem>> {
        return dao.observeRecentChannels(limit = RECENT_CHANNEL_LIMIT, profileId = profileId)
            .map { items -> items.map(RecentChannelEntity::toDomain) }
    }

    override fun observeLastWatchedChannel(profileId: String): Flow<RecentChannelItem?> {
        return dao.observeLastWatchedChannel(profileId = profileId).map { it?.toDomain() }
    }

    override fun observeSearchHistory(profileId: String): Flow<List<SearchHistoryItem>> {
        return dao.observeSearchHistory(limit = SEARCH_HISTORY_LIMIT, profileId = profileId)
            .map { items -> items.map(SearchHistoryEntity::toDomain) }
    }

    override fun observePreferenceSignals(profileId: String): Flow<List<UserPreferenceSignal>> {
        return dao.observePreferenceSignals(limit = PREFERENCE_SIGNAL_LIMIT, profileId = profileId)
            .map { items -> items.map(PreferenceSignalEntity::toDomain) }
    }

    override suspend fun upsertContinueWatching(
        reference: UserMemoryReference,
        progressMs: Long,
        durationMs: Long,
        updatedAt: Long,
        profileId: String
    ) {
        require(reference.contentType != UserMemoryContentType.LIVE_CHANNEL) {
            "Live channels must use recent-channel tracking, not Continue Watching"
        }
        validateProgress(progressMs, durationMs)
        val safeReference = UserMemoryPrivacy.sanitizeReference(reference)
        database.withTransaction {
            dao.upsertContinueWatching(
                ContinueWatchingEntity(
                    reference = safeReference,
                    progressMs = progressMs,
                    durationMs = durationMs,
                    updatedAt = updatedAt,
                    profileId = profileId
                )
            )
            dao.trimContinueWatching(keep = CONTINUE_WATCHING_LIMIT, profileId = profileId)
        }
    }

    override suspend fun removeContinueWatching(itemKey: String, profileId: String) {
        dao.removeContinueWatching(itemKey = UserMemoryPrivacy.requireSafeIdentifier(itemKey, "itemKey"), profileId = profileId)
    }

    override suspend fun clearContinueWatching(profileId: String) {
        dao.clearContinueWatching(profileId = profileId)
    }

    override suspend fun toggleFavorite(reference: UserMemoryReference, timestamp: Long, profileId: String): Boolean {
        val safeReference = UserMemoryPrivacy.sanitizeReference(reference)
        return database.withTransaction {
            if (dao.getFavorite(itemKey = safeReference.itemKey, profileId = profileId) == null) {
                dao.upsertFavorite(
                    FavoriteEntity(
                        reference = safeReference,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                        profileId = profileId
                    )
                )
                true
            } else {
                dao.removeFavorite(itemKey = safeReference.itemKey, profileId = profileId)
                false
            }
        }
    }

    override suspend fun setFavorite(
        reference: UserMemoryReference,
        favorite: Boolean,
        timestamp: Long,
        profileId: String
    ) {
        val safeReference = UserMemoryPrivacy.sanitizeReference(reference)
        database.withTransaction {
            if (favorite) {
                val existing = dao.getFavorite(itemKey = safeReference.itemKey, profileId = profileId)
                dao.upsertFavorite(
                    FavoriteEntity(
                        reference = safeReference,
                        createdAt = existing?.createdAt ?: timestamp,
                        updatedAt = timestamp,
                        profileId = profileId
                    )
                )
            } else {
                dao.removeFavorite(itemKey = safeReference.itemKey, profileId = profileId)
            }
        }
    }

    override suspend fun removeFavorite(itemKey: String, profileId: String) {
        dao.removeFavorite(itemKey = UserMemoryPrivacy.requireSafeIdentifier(itemKey, "itemKey"), profileId = profileId)
    }

    override suspend fun clearFavorites(profileId: String) {
        dao.clearFavorites(profileId = profileId)
    }

    override suspend fun recordWatchHistory(
        reference: UserMemoryReference,
        progressMs: Long,
        durationMs: Long,
        watchedAt: Long,
        profileId: String
    ) {
        require(reference.contentType != UserMemoryContentType.LIVE_CHANNEL) {
            "Live channels must use recent-channel tracking, not VOD watch history"
        }
        validateProgress(progressMs, durationMs)
        val safeReference = UserMemoryPrivacy.sanitizeReference(reference)
        database.withTransaction {
            val existing = dao.getWatchHistory(itemKey = safeReference.itemKey, profileId = profileId)
            dao.upsertWatchHistory(
                WatchHistoryEntity(
                    reference = safeReference,
                    firstWatchedAt = existing?.firstWatchedAt ?: watchedAt,
                    lastWatchedAt = watchedAt,
                    watchCount = (existing?.watchCount ?: 0L) + 1L,
                    progressMs = progressMs,
                    durationMs = durationMs,
                    profileId = profileId
                )
            )
            dao.trimWatchHistory(keep = WATCH_HISTORY_LIMIT, profileId = profileId)
        }
    }

    override suspend fun removeWatchHistory(itemKey: String, profileId: String) {
        dao.removeWatchHistory(itemKey = UserMemoryPrivacy.requireSafeIdentifier(itemKey, "itemKey"), profileId = profileId)
    }

    override suspend fun clearWatchHistory(profileId: String) {
        dao.clearWatchHistory(profileId = profileId)
    }

    override suspend fun recordRecentChannel(reference: UserMemoryReference, watchedAt: Long, profileId: String) {
        require(reference.contentType == UserMemoryContentType.LIVE_CHANNEL) {
            "Recent-channel tracking accepts live channels only"
        }
        val safeReference = UserMemoryPrivacy.sanitizeReference(reference)
        database.withTransaction {
            val existing = dao.getRecentChannel(itemKey = safeReference.itemKey, profileId = profileId)
            dao.upsertRecentChannel(
                RecentChannelEntity(
                    reference = safeReference,
                    lastWatchedAt = watchedAt,
                    watchCount = (existing?.watchCount ?: 0L) + 1L,
                    profileId = profileId
                )
            )
            dao.trimRecentChannels(keep = RECENT_CHANNEL_LIMIT, profileId = profileId)
        }
    }

    override suspend fun removeRecentChannel(itemKey: String, profileId: String) {
        dao.removeRecentChannel(itemKey = UserMemoryPrivacy.requireSafeIdentifier(itemKey, "itemKey"), profileId = profileId)
    }

    override suspend fun clearRecentChannels(profileId: String) {
        dao.clearRecentChannels(profileId = profileId)
    }

    override suspend fun recordSearch(query: String, searchedAt: Long, profileId: String): Boolean {
        val safeQuery = UserMemoryPrivacy.sanitizeSearchQuery(query) ?: return false
        val normalized = UserMemoryPrivacy.normalizeSearchQuery(safeQuery)
        database.withTransaction {
            val existing = dao.getSearchHistory(normalizedQuery = normalized, profileId = profileId)
            dao.upsertSearchHistory(
                SearchHistoryEntity(
                    profileId = profileId,
                    normalizedQuery = normalized,
                    query = safeQuery,
                    lastSearchedAt = searchedAt,
                    searchCount = (existing?.searchCount ?: 0L) + 1L
                )
            )
            dao.trimSearchHistory(keep = SEARCH_HISTORY_LIMIT, profileId = profileId)
        }
        return true
    }

    override suspend fun removeSearch(query: String, profileId: String): Boolean {
        val safeQuery = UserMemoryPrivacy.sanitizeSearchQuery(query) ?: return false
        dao.removeSearchHistory(normalizedQuery = UserMemoryPrivacy.normalizeSearchQuery(safeQuery), profileId = profileId)
        return true
    }

    override suspend fun clearSearchHistory(profileId: String) {
        dao.clearSearchHistory(profileId = profileId)
    }

    override suspend fun incrementPreferenceSignal(
        signalType: UserPreferenceSignalType,
        signalKey: String,
        incrementBy: Long,
        signaledAt: Long,
        profileId: String
    ) {
        require(incrementBy in 1L..MAX_SIGNAL_INCREMENT) {
            "Preference signal increments must be between 1 and $MAX_SIGNAL_INCREMENT"
        }
        val safeSignalKey = when (signalType) {
            UserPreferenceSignalType.CONTENT_TYPE,
            UserPreferenceSignalType.GENRE -> {
                UserMemoryPrivacy.requireSafeDisplayText(signalKey, "signalKey")
            }
            UserPreferenceSignalType.PROVIDER,
            UserPreferenceSignalType.SOURCE,
            UserPreferenceSignalType.SEARCH_RESULT_SELECTION -> {
                UserMemoryPrivacy.requireSafeIdentifier(signalKey, "signalKey")
            }
        }
        database.withTransaction {
            val existing = dao.getPreferenceSignal(signalType = signalType.name, signalKey = safeSignalKey, profileId = profileId)
            dao.upsertPreferenceSignal(
                PreferenceSignalEntity(
                    profileId = profileId,
                    signalType = signalType.name,
                    signalKey = safeSignalKey,
                    count = (existing?.count ?: 0L) + incrementBy,
                    lastSignaledAt = signaledAt
                )
            )
            dao.trimPreferenceSignals(keep = PREFERENCE_SIGNAL_LIMIT, profileId = profileId)
        }
    }

    override suspend fun clearPreferenceSignals(profileId: String) {
        dao.clearPreferenceSignals(profileId = profileId)
    }

    private fun validateProgress(progressMs: Long, durationMs: Long) {
        require(progressMs >= 0L) { "progressMs must not be negative" }
        require(durationMs >= 0L) { "durationMs must not be negative" }
    }

    private companion object {
        const val CONTINUE_WATCHING_LIMIT = 200
        const val WATCH_HISTORY_LIMIT = 500
        const val RECENT_CHANNEL_LIMIT = 50
        const val SEARCH_HISTORY_LIMIT = 50
        const val PREFERENCE_SIGNAL_LIMIT = 200
        const val FAVORITES_LIMIT = 1000
        const val MAX_SIGNAL_INCREMENT = 1_000L
    }
}

private fun ContinueWatchingEntity.toDomain(): ContinueWatchingItem {
    return ContinueWatchingItem(
        reference = toReference(itemKey, contentType, title, subtitle, providerId, sourceId),
        progressMs = progressMs,
        durationMs = durationMs,
        updatedAt = updatedAt
    )
}

private fun FavoriteEntity.toDomain(): FavoriteItem {
    return FavoriteItem(
        reference = toReference(itemKey, contentType, title, subtitle, providerId, sourceId),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

private fun WatchHistoryEntity.toDomain(): WatchHistoryItem {
    return WatchHistoryItem(
        reference = toReference(itemKey, contentType, title, subtitle, providerId, sourceId),
        firstWatchedAt = firstWatchedAt,
        lastWatchedAt = lastWatchedAt,
        watchCount = watchCount,
        progressMs = progressMs,
        durationMs = durationMs
    )
}

private fun RecentChannelEntity.toDomain(): RecentChannelItem {
    return RecentChannelItem(
        reference = toReference(itemKey, contentType, title, subtitle, providerId, sourceId),
        lastWatchedAt = lastWatchedAt,
        watchCount = watchCount
    )
}

private fun SearchHistoryEntity.toDomain(): SearchHistoryItem {
    return SearchHistoryItem(
        query = query,
        lastSearchedAt = lastSearchedAt,
        searchCount = searchCount
    )
}

private fun PreferenceSignalEntity.toDomain(): UserPreferenceSignal {
    return UserPreferenceSignal(
        signalType = UserPreferenceSignalType.valueOf(signalType),
        signalKey = signalKey,
        count = count,
        lastSignaledAt = lastSignaledAt
    )
}

private fun ContinueWatchingEntity(
    reference: UserMemoryReference,
    progressMs: Long,
    durationMs: Long,
    updatedAt: Long,
    profileId: String
) = ContinueWatchingEntity(
    profileId = profileId,
    itemKey = reference.itemKey,
    contentType = reference.contentType.name,
    title = reference.title,
    subtitle = reference.subtitle,
    providerId = reference.providerId,
    sourceId = reference.sourceId,
    progressMs = progressMs,
    durationMs = durationMs,
    updatedAt = updatedAt
)

private fun FavoriteEntity(
    reference: UserMemoryReference,
    createdAt: Long,
    updatedAt: Long,
    profileId: String
) = FavoriteEntity(
    profileId = profileId,
    itemKey = reference.itemKey,
    contentType = reference.contentType.name,
    title = reference.title,
    subtitle = reference.subtitle,
    providerId = reference.providerId,
    sourceId = reference.sourceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun WatchHistoryEntity(
    reference: UserMemoryReference,
    firstWatchedAt: Long,
    lastWatchedAt: Long,
    watchCount: Long,
    progressMs: Long,
    durationMs: Long,
    profileId: String
) = WatchHistoryEntity(
    profileId = profileId,
    itemKey = reference.itemKey,
    contentType = reference.contentType.name,
    title = reference.title,
    subtitle = reference.subtitle,
    providerId = reference.providerId,
    sourceId = reference.sourceId,
    firstWatchedAt = firstWatchedAt,
    lastWatchedAt = lastWatchedAt,
    watchCount = watchCount,
    progressMs = progressMs,
    durationMs = durationMs
)

private fun RecentChannelEntity(
    reference: UserMemoryReference,
    lastWatchedAt: Long,
    watchCount: Long,
    profileId: String
) = RecentChannelEntity(
    profileId = profileId,
    itemKey = reference.itemKey,
    contentType = reference.contentType.name,
    title = reference.title,
    subtitle = reference.subtitle,
    providerId = reference.providerId,
    sourceId = reference.sourceId,
    lastWatchedAt = lastWatchedAt,
    watchCount = watchCount
)

private fun toReference(
    itemKey: String,
    contentType: String,
    title: String,
    subtitle: String?,
    providerId: String?,
    sourceId: String?
): UserMemoryReference {
    return UserMemoryReference(
        itemKey = itemKey,
        contentType = UserMemoryContentType.valueOf(contentType),
        title = title,
        subtitle = subtitle,
        providerId = providerId,
        sourceId = sourceId
    )
}

class FallbackUserMemoryRepository : UserMemoryRepository {
    override fun observeContinueWatching(profileId: String): Flow<List<ContinueWatchingItem>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeFavorites(profileId: String): Flow<List<FavoriteItem>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeIsFavorite(itemKey: String, profileId: String): Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
    override fun observeWatchHistory(profileId: String): Flow<List<WatchHistoryItem>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeRecentChannels(profileId: String): Flow<List<RecentChannelItem>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeLastWatchedChannel(profileId: String): Flow<RecentChannelItem?> = kotlinx.coroutines.flow.flowOf(null)
    override fun observeSearchHistory(profileId: String): Flow<List<SearchHistoryItem>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observePreferenceSignals(profileId: String): Flow<List<UserPreferenceSignal>> = kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun upsertContinueWatching(reference: UserMemoryReference, progressMs: Long, durationMs: Long, updatedAt: Long, profileId: String) {}
    override suspend fun removeContinueWatching(itemKey: String, profileId: String) {}
    override suspend fun clearContinueWatching(profileId: String) {}
    override suspend fun toggleFavorite(reference: UserMemoryReference, timestamp: Long, profileId: String): Boolean = false
    override suspend fun setFavorite(reference: UserMemoryReference, favorite: Boolean, timestamp: Long, profileId: String) {}
    override suspend fun removeFavorite(itemKey: String, profileId: String) {}
    override suspend fun clearFavorites(profileId: String) {}
    override suspend fun recordWatchHistory(reference: UserMemoryReference, progressMs: Long, durationMs: Long, watchedAt: Long, profileId: String) {}
    override suspend fun removeWatchHistory(itemKey: String, profileId: String) {}
    override suspend fun clearWatchHistory(profileId: String) {}
    override suspend fun recordRecentChannel(reference: UserMemoryReference, watchedAt: Long, profileId: String) {}
    override suspend fun removeRecentChannel(itemKey: String, profileId: String) {}
    override suspend fun clearRecentChannels(profileId: String) {}
    override suspend fun recordSearch(query: String, searchedAt: Long, profileId: String): Boolean = false
    override suspend fun removeSearch(query: String, profileId: String): Boolean = false
    override suspend fun clearSearchHistory(profileId: String) {}
    override suspend fun incrementPreferenceSignal(signalType: UserPreferenceSignalType, signalKey: String, incrementBy: Long, signaledAt: Long, profileId: String) {}
    override suspend fun clearPreferenceSignals(profileId: String) {}
}

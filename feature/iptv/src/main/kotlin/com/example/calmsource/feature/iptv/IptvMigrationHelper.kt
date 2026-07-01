package com.example.calmsource.feature.iptv

import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.database.dao.IPTVDao
import com.example.calmsource.core.database.entity.IPTVChannelEntity
import com.example.calmsource.core.model.generateSafeSourceId
import com.example.calmsource.feature.iptv.xtream.XtreamStreamUrlBuilder
import androidx.room.withTransaction
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object IptvMigrationHelper {
    private val migrationDone = AtomicBoolean(false)
    private val legacyXtreamUrlMigrationDone = AtomicBoolean(false)

    @VisibleForTesting
    internal fun resetLegacyXtreamUrlMigrationForTests() {
        legacyXtreamUrlMigrationDone.set(false)
    }

    suspend fun migrateFallbackToRoom(fallbackDao: IPTVDao) = withContext(Dispatchers.IO) {
        if (migrationDone.get()) return@withContext
        val database = DatabaseProvider.databaseOrNull() ?: return@withContext
        if (!migrationDone.compareAndSet(false, true)) return@withContext

        val roomDao = database.iptvDao()
        val providers = fallbackDao.getAllProviders().firstOrNull() ?: emptyList()
        val channels = fallbackDao.getAllChannels().firstOrNull() ?: emptyList()
        val epgSources = fallbackDao.getAllEPGSources().firstOrNull() ?: emptyList()
        val epgPrograms = fallbackDao.getAllEPGPrograms()
        if (providers.isEmpty() && channels.isEmpty() && epgSources.isEmpty() && epgPrograms.isEmpty()) {
            migrationDone.set(false) // Reset so we can try again if data appears later
            return@withContext
        }
        android.util.Log.i("IptvMigrationHelper", "Migrating fallback in-memory data to Room: ${providers.size} providers, ${channels.size} channels, ${epgSources.size} EPG sources, ${epgPrograms.size} EPG programs")
        database.withTransaction {
            providers.forEach { roomDao.insertProvider(it) }
            if (channels.isNotEmpty()) {
                roomDao.insertChannels(channels)
            }
            epgSources.forEach { roomDao.insertEPGSource(it) }
            if (epgPrograms.isNotEmpty()) {
                roomDao.insertEPGPrograms(epgPrograms)
            }
        }
        providers.forEach { fallbackDao.deleteProvider(it) }
        epgSources.forEach { fallbackDao.deleteEPGSource(it) }
        providers.forEach { p ->
            fallbackDao.deleteChannelsByProvider(p.id)
            fallbackDao.deleteEPGProgramsByProvider(p.id)
        }
    }

    /**
     * Rewrites legacy `xtream://stream_id/{streamId}` pseudo-URLs to the provider-scoped
     * format and re-keys persisted source health so rankings do not collide across providers.
     */
    suspend fun migrateLegacyXtreamPseudoUrls(dao: IPTVDao) = withContext(Dispatchers.IO) {
        if (legacyXtreamUrlMigrationDone.get()) return@withContext
        if (!legacyXtreamUrlMigrationDone.compareAndSet(false, true)) return@withContext

        val channels = dao.getAllChannels().firstOrNull().orEmpty()
        if (channels.isEmpty()) {
            legacyXtreamUrlMigrationDone.set(false)
            return@withContext
        }

        val updates = mutableListOf<IPTVChannelEntity>()
        for (entity in channels) {
            val url = entity.streamUrl
            if (!url.startsWith("xtream://stream_id/")) continue
            if (XtreamStreamUrlBuilder.extractProviderId(url) != null) continue
            val streamId = XtreamStreamUrlBuilder.extractStreamId(url) ?: continue
            val providerId = entity.providerId.takeIf { it.isNotBlank() } ?: continue
            val newUrl = XtreamStreamUrlBuilder.createPseudoUrl(providerId, streamId) ?: continue
            if (newUrl == url) continue

            val oldSafeId = generateSafeSourceId(url)
            val newSafeId = generateSafeSourceId(newUrl)
            if (oldSafeId != newSafeId) {
                SourceHealthRepository.migrateSourceId(oldSafeId, newSafeId)
            }
            updates.add(entity.copy(streamUrl = newUrl))
        }

        if (updates.isEmpty()) return@withContext
        android.util.Log.i(
            "IptvMigrationHelper",
            "Migrating ${updates.size} legacy Xtream pseudo-URLs to provider-scoped format",
        )
        dao.insertChannels(updates)
    }
}

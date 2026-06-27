package com.example.calmsource.feature.iptv

import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.dao.IPTVDao
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object IptvMigrationHelper {
    private val migrationDone = AtomicBoolean(false)

    suspend fun migrateFallbackToRoom(fallbackDao: IPTVDao) {
        if (!migrationDone.compareAndSet(false, true)) return
        val database = DatabaseProvider.databaseOrNull() ?: return
        val roomDao = database.iptvDao()
        val providers = fallbackDao.getAllProviders().firstOrNull() ?: emptyList()
        val channels = fallbackDao.getAllChannels().firstOrNull() ?: emptyList()
        val epgSources = fallbackDao.getAllEPGSources().firstOrNull() ?: emptyList()
        val epgPrograms = fallbackDao.getAllEPGPrograms()
        if (providers.isEmpty() && channels.isEmpty() && epgSources.isEmpty() && epgPrograms.isEmpty()) {
            return
        }
        android.util.Log.i("IptvMigrationHelper", "Migrating fallback in-memory data to Room: ${providers.size} providers, ${channels.size} channels, ${epgSources.size} EPG sources, ${epgPrograms.size} EPG programs")
        withContext(Dispatchers.IO) {
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
    }
}

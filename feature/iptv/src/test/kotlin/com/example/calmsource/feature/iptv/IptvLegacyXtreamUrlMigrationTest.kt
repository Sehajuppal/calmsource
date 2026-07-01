package com.example.calmsource.feature.iptv

import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.database.entity.IPTVChannelEntity
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.SourceHealthSignal
import com.example.calmsource.core.model.generateSafeSourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class IptvLegacyXtreamUrlMigrationTest {

    private val channels = mutableListOf<IPTVChannelEntity>()

    @Before
    fun setUp() {
        SourceHealthRepository.dispatcher = Dispatchers.Unconfined
        IptvMigrationHelper.resetLegacyXtreamUrlMigrationForTests()
        channels.clear()
        runBlocking {
            SourceHealthRepository.clearSourceHealth()
        }
    }

    @Test
    fun `migrateLegacyXtreamPseudoUrls rewrites legacy stream URLs and rekeys health`() = runTest {
        val legacyUrl = "xtream://stream_id/12345"
        channels.add(
            IPTVChannelEntity(
                id = "ch-1",
                name = "News",
                streamUrl = legacyUrl,
                providerId = "provider-a",
            ),
        )
        val oldSafeId = generateSafeSourceId(legacyUrl)
        SourceHealthRepository.recordSignal(
            sourceId = oldSafeId,
            providerId = "provider-a",
            sourceType = PlaybackSourceType.IPTV,
            signal = SourceHealthSignal.PLAYBACK_SUCCESS,
        )

        IptvMigrationHelper.migrateLegacyXtreamPseudoUrls(fakeDao())

        val migrated = channels.single()
        assertEquals("xtream://stream_id/provider-a/12345", migrated.streamUrl)
        val newSafeId = generateSafeSourceId(migrated.streamUrl)
        assertNotEquals(oldSafeId, newSafeId)
        assertEquals(100, SourceHealthRepository.getSourceHealth(newSafeId, readonly = true)?.healthScore)
    }

    @Test
    fun `migrateLegacyXtreamPseudoUrls leaves provider-scoped URLs unchanged`() = runTest {
        val modernUrl = "xtream://stream_id/provider-a/12345"
        channels.add(
            IPTVChannelEntity(
                id = "ch-1",
                name = "News",
                streamUrl = modernUrl,
                providerId = "provider-a",
            ),
        )

        IptvMigrationHelper.migrateLegacyXtreamPseudoUrls(fakeDao())

        assertEquals(modernUrl, channels.single().streamUrl)
    }

    private fun fakeDao() = object : com.example.calmsource.core.database.dao.IPTVDao {
        override fun getAllProviders(): kotlinx.coroutines.flow.Flow<List<com.example.calmsource.core.database.entity.IPTVProviderEntity>> = flowOf(emptyList())
        override fun getProviderById(id: String) = flowOf(null)
        override suspend fun getProviderByIdDirect(id: String) = null
        override fun insertProvider(provider: com.example.calmsource.core.database.entity.IPTVProviderEntity): Long = 1L
        override fun updateProvider(provider: com.example.calmsource.core.database.entity.IPTVProviderEntity): Int = 1
        override fun deleteProvider(provider: com.example.calmsource.core.database.entity.IPTVProviderEntity): Int = 1
        override fun getAllChannels() = flowOf(channels.toList())
        override fun getChannelsByProvider(providerId: String) =
            flowOf(channels.filter { it.providerId == providerId })
        override fun insertChannels(batch: List<IPTVChannelEntity>): List<Long> {
            batch.forEach { updated ->
                val index = channels.indexOfFirst { it.id == updated.id }
                if (index >= 0) channels[index] = updated else channels.add(updated)
            }
            return batch.map { 1L }
        }
        override fun deleteChannelsByProvider(providerId: String): Int {
            val existed = channels.any { it.providerId == providerId }
            channels.removeAll { it.providerId == providerId }
            return if (existed) 1 else 0
        }
        override fun replaceChannels(providerId: String, batch: List<IPTVChannelEntity>) = Unit
        override fun getAllEPGSources(): kotlinx.coroutines.flow.Flow<List<com.example.calmsource.core.database.entity.EPGSourceEntity>> = flowOf(emptyList())
        override fun insertEPGSource(source: com.example.calmsource.core.database.entity.EPGSourceEntity): Long = 1L
        override fun deleteEPGSource(source: com.example.calmsource.core.database.entity.EPGSourceEntity): Int = 1
        override fun deleteEPGSourcesByProvider(providerId: String): Int = 1
        override fun getEPGProgramsByChannel(channelId: String): kotlinx.coroutines.flow.Flow<List<com.example.calmsource.core.database.entity.EPGProgramEntity>> = flowOf(emptyList())
        override fun insertEPGPrograms(programs: List<com.example.calmsource.core.database.entity.EPGProgramEntity>): List<Long> = programs.map { 1L }
        override fun deleteEPGProgramsByChannel(channelId: String): Int = 1
        override fun deleteEPGProgramsByChannels(channelIds: Set<String>): Int = 1
        override fun deleteEPGProgramsByProvider(providerId: String): Int = 1
        override fun replaceEPGPrograms(channelIdsToClear: Set<String>, programs: List<com.example.calmsource.core.database.entity.EPGProgramEntity>) = Unit
        override suspend fun getAllEPGPrograms() = emptyList<com.example.calmsource.core.database.entity.EPGProgramEntity>()
        override fun pruneOldEPGPrograms(cutoffTime: Long): Int = 1
        override suspend fun getUniqueEPGChannelIds() = emptyList<String>()
        override suspend fun getEPGProgramsByChannelDirect(channelId: String) =
            emptyList<com.example.calmsource.core.database.entity.EPGProgramEntity>()
        override suspend fun getEPGProgramsByChannelsDirect(channelIds: List<String>) =
            emptyList<com.example.calmsource.core.database.entity.EPGProgramEntity>()
        override suspend fun searchEPGPrograms(query: String) =
            emptyList<com.example.calmsource.core.database.entity.EPGProgramEntity>()
    }
}

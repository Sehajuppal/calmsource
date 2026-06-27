package com.example.calmsource.core.database.dao

import androidx.room.*
import com.example.calmsource.core.database.entity.EPGProgramEntity
import com.example.calmsource.core.database.entity.EPGSourceEntity
import com.example.calmsource.core.database.entity.IPTVChannelEntity
import com.example.calmsource.core.database.entity.IPTVProviderEntity
import kotlinx.coroutines.flow.Flow

fun String.escapeSqlLike(): String {
    return this.replace("\\", "\\\\")
               .replace("%", "\\%")
               .replace("_", "\\_")
}

@Dao
interface IPTVDao {
    @Query("SELECT * FROM iptv_providers")
    fun getAllProviders(): Flow<List<IPTVProviderEntity>>

    @Query("SELECT * FROM iptv_providers WHERE id = :id")
    fun getProviderById(id: String): Flow<IPTVProviderEntity?>

    @Query("SELECT * FROM iptv_providers WHERE id = :id")
    suspend fun getProviderByIdDirect(id: String): IPTVProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProvider(provider: IPTVProviderEntity)

    @Update
    fun updateProvider(provider: IPTVProviderEntity)

    @Delete
    fun deleteProvider(provider: IPTVProviderEntity)

    /**
     * Returns up to 100,000 rows from the iptv_channels table. The
     * result is capped at 100,000 rows to avoid overflowing a single
     * CursorWindow on devices with very large M3U playlists. The cap is
     * still well above the number of channels shown in the TV home screen
     * (which renders only the first 200), but it leaves headroom for
     * channel-id lookups during EPG matching and search.
     */
    @Query("SELECT * FROM iptv_channels ORDER BY id LIMIT 100000")
    fun getAllChannels(): Flow<List<IPTVChannelEntity>>

    @Query("SELECT * FROM iptv_channels WHERE providerId = :providerId ORDER BY id LIMIT 100000")
    fun getChannelsByProvider(providerId: String): Flow<List<IPTVChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChannels(channels: List<IPTVChannelEntity>)

    @Query("DELETE FROM iptv_channels WHERE providerId = :providerId")
    fun deleteChannelsByProvider(providerId: String)

    @Transaction
    fun replaceChannels(providerId: String, channels: List<IPTVChannelEntity>) {
        deleteChannelsByProvider(providerId)
        channels.chunked(500).forEach { insertChannels(it) }
    }

    @Query("SELECT * FROM epg_sources")
    fun getAllEPGSources(): Flow<List<EPGSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEPGSource(source: EPGSourceEntity)

    @Delete
    fun deleteEPGSource(source: EPGSourceEntity)

    @Query("DELETE FROM epg_sources WHERE providerId = :providerId")
    fun deleteEPGSourcesByProvider(providerId: String)

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId ORDER BY startTimeMs LIMIT 500")
    fun getEPGProgramsByChannel(channelId: String): Flow<List<EPGProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEPGPrograms(programs: List<EPGProgramEntity>)
    
    @Query("DELETE FROM epg_programs WHERE channelId = :channelId")
    fun deleteEPGProgramsByChannel(channelId: String)

    @Query("DELETE FROM epg_programs WHERE channelId IN (:channelIds)")
    fun deleteEPGProgramsByChannels(channelIds: Set<String>)

    @Query(
        """
        DELETE FROM epg_programs
        WHERE channelId IN (
            SELECT id FROM iptv_channels WHERE providerId = :providerId
            UNION
            SELECT tvgId FROM iptv_channels
            WHERE providerId = :providerId AND tvgId IS NOT NULL
        )
        AND channelId NOT IN (
            SELECT id FROM iptv_channels
            WHERE providerId != :providerId
            UNION
            SELECT tvgId FROM iptv_channels
            WHERE providerId != :providerId AND tvgId IS NOT NULL
        )
        """
    )
    fun deleteEPGProgramsByProvider(providerId: String)

    @Transaction
    fun replaceEPGPrograms(channelIdsToClear: Set<String>, programs: List<EPGProgramEntity>) {
        if (channelIdsToClear.isNotEmpty()) {
            deleteEPGProgramsByChannels(channelIdsToClear)
        }
        programs.chunked(500).forEach { insertEPGPrograms(it) }
    }

    /**
     * Returns up to 50,000 EPG program rows. Capping this query is the
     * primary fix for the `W CursorWindow: Window is full` warnings that
     * appeared on the Fire TV during the first 5 seconds of startup:
     * a 7-day EPG for 200 channels (the typical case) is ~10,000 rows;
     * 50,000 is a 5x safety margin and fits well inside an 8 MB cursor
     * window. For users with more programs, the parsed program cache
     * simply reflects the most recent 50,000; older rows are still
     * pruned via [pruneOldEPGPrograms] on a 6-hour rolling window.
     */
    @Query("SELECT * FROM epg_programs LIMIT 50000")
    suspend fun getAllEPGPrograms(): List<EPGProgramEntity>

    @Query("DELETE FROM epg_programs WHERE endTimeMs < :cutoffTime")
    fun pruneOldEPGPrograms(cutoffTime: Long)

    @Query("SELECT DISTINCT channelId FROM epg_programs")
    suspend fun getUniqueEPGChannelIds(): List<String>

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId ORDER BY startTimeMs")
    suspend fun getEPGProgramsByChannelDirect(channelId: String): List<EPGProgramEntity>

    @Query("SELECT * FROM epg_programs WHERE channelId IN (:channelIds) ORDER BY startTimeMs")
    suspend fun getEPGProgramsByChannelsDirect(channelIds: List<String>): List<EPGProgramEntity>

    @Query("SELECT * FROM epg_programs WHERE title LIKE '%' || :query || '%' ESCAPE '\\' LIMIT 100")
    suspend fun searchEPGPrograms(query: String): List<EPGProgramEntity>
}


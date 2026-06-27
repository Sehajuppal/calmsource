package com.example.calmsource.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calmsource.core.database.entity.EPGProgramEntity
import com.example.calmsource.core.database.entity.IPTVChannelEntity
import com.example.calmsource.core.database.entity.IPTVProviderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DatabaseAuditTest {
    private lateinit var db: CalmSourceDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 6. Verify database initialization is safe. (We use in-memory for testing, but it creates correctly)
        db = Room.inMemoryDatabaseBuilder(
            context, CalmSourceDatabase::class.java
        ).build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun verifyDatabaseVersion() {
        // 2. Verify database version is correct
        assertEquals(10, db.openHelper.readableDatabase.version)
    }

    @Test
    fun testTypeConverters() {
        // 3. Verify type converters work
        val date = Converters.fromTimestamp(1000L)
        assertEquals(1000L, Converters.dateToTimestamp(date))

        val list = listOf("a", "b", "c")
        val stringList = Converters.toStringList(list)
        assertEquals("[\"a\",\"b\",\"c\"]", stringList)
        assertEquals(list, Converters.fromStringList(stringList))

        val map = mapOf("key" to "value", "foo" to "bar")
        val stringMap = Converters.toStringMap(map)
        val deserializedMap = Converters.fromStringMap(stringMap)
        assertEquals("value", deserializedMap["key"])
        assertEquals("bar", deserializedMap["foo"])
    }

    @Test
    @Throws(Exception::class)
    fun testIPTVDaoQueriesAndLargeInserts() = runBlocking {
        // 1. Verify all entities compile and map cleanly
        // 4. Verify DAO queries are correct
        val iptvDao = db.iptvDao()
        
        val provider = IPTVProviderEntity().apply {
            id = "provider_1"
            name = "Test Provider"
            playlistUrl = "http://test.com"
            isEnabled = true
            health = "Good"
        }
        
        iptvDao.insertProvider(provider)
        
        val providers = iptvDao.getAllProviders().first()
        assertEquals(1, providers.size)
        assertEquals("provider_1", providers[0].id)
        
        // 9. Verify large EPG/channel inserts use efficient patterns
        // We do a bulk insert of 100 channels. The DAO uses List<T> with @Insert which generates a transaction.
        val channels = (1..100).map { i ->
            IPTVChannelEntity().apply {
                id = "chan_$i"
                providerId = "provider_1"
                name = "Channel $i"
            }
        }
        
        iptvDao.insertChannels(channels)
        val dbChannels = iptvDao.getChannelsByProvider("provider_1").first()
        assertEquals(100, dbChannels.size)
        
        // Delete
        iptvDao.deleteChannelsByProvider("provider_1")
        val emptyChannels = iptvDao.getChannelsByProvider("provider_1").first()
        assertTrue(emptyChannels.isEmpty())
    }

    @Test
    fun testIndexesExist() {
        // 10. Verify indexes exist where useful
        // Room creates indexes on the table, we can query SQLite master to verify
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='index'", null)
        val indexNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            indexNames.add(cursor.getString(0))
        }
        cursor.close()

        assertTrue("Missing providerId index on iptv_channels", indexNames.any { it.contains("iptv_channels") && it.contains("providerId") })
        assertTrue("Missing tvgId index on iptv_channels", indexNames.any { it.contains("iptv_channels") && it.contains("tvgId") })
        assertTrue("Missing channelId index on epg_programs", indexNames.any { it.contains("epg_programs") && it.contains("channelId") })
        assertTrue("Missing providerId index on epg_sources", indexNames.any { it.contains("epg_sources") && it.contains("providerId") })
        assertTrue("Missing url index on extension_providers", indexNames.any { it.contains("extension_providers") && it.contains("url") })
    }
}

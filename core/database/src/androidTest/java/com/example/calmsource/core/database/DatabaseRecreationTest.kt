package com.example.calmsource.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calmsource.core.database.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseRecreationTest {

    private val dbName = "test_calmsource_db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getDatabasePath(dbName).delete()
    }

    @After
    fun tearDown() {
        context.getDatabasePath(dbName).delete()
    }

    @Test
    fun verifyIPTVDataSurvivesRecreation() = runBlocking {
        var db = Room.databaseBuilder(context, CalmSourceDatabase::class.java, dbName).build()
        val iptvDao = db.iptvDao()

        val provider = IPTVProviderEntity().apply {
            id = "provider_1"
            name = "Test Provider"
            playlistUrl = "http://test.com"
            isEnabled = true
            health = "Good"
        }
        iptvDao.insertProvider(provider)

        val channel = IPTVChannelEntity().apply {
            id = "chan_1"
            providerId = "provider_1"
            name = "Test Channel"
        }
        iptvDao.insertChannels(listOf(channel))

        val epgSource = EPGSourceEntity().apply {
            id = "epg_1"
            providerId = "provider_1"
            name = "Test EPG"
            url = "http://test.com/epg"
            lastSyncMs = 12345L
        }
        iptvDao.insertEPGSource(epgSource)

        db.close()

        // Reopen database
        db = Room.databaseBuilder(context, CalmSourceDatabase::class.java, dbName).build()
        val newDao = db.iptvDao()

        val providers = newDao.getAllProviders().first()
        assertEquals(1, providers.size)
        assertEquals("provider_1", providers[0].id)

        val channels = newDao.getChannelsByProvider("provider_1").first()
        assertEquals(1, channels.size)
        assertEquals("chan_1", channels[0].id)

        val epgSources = newDao.getAllEPGSources().first()
        assertEquals(1, epgSources.size)
        assertEquals("epg_1", epgSources[0].id)

        db.close()
    }

    @Test
    fun verifyExtensionDataSurvivesRecreation() = runBlocking {
        var db = Room.databaseBuilder(context, CalmSourceDatabase::class.java, dbName).build()
        val extDao = db.extensionDao()

        val extension = ExtensionProviderEntity().apply {
            id = "ext_1"
            name = "Test Extension"
            url = "http://test.com/ext"
            isEnabled = false
            health = "ACTIVE"
            priority = 10
            manifestJson = "{}"
            permissionsCsv = "INTERNET"
        }
        extDao.insertExtension(extension)

        db.close()

        // Reopen
        db = Room.databaseBuilder(context, CalmSourceDatabase::class.java, dbName).build()
        val newDao = db.extensionDao()

        val extensions = newDao.getAllExtensions().first()
        assertEquals(1, extensions.size)
        val ext = extensions[0]
        assertEquals("ext_1", ext.id)
        assertFalse(ext.isEnabled)
        assertEquals(10, ext.priority)

        db.close()
    }

    @Test
    fun verifyDebridDataSurvivesRecreation() = runBlocking {
        var db = Room.databaseBuilder(context, CalmSourceDatabase::class.java, dbName).build()
        val debridDao = db.debridDao()

        val account = DebridAccountEntity().apply {
            id = "deb_1"
            providerType = "REAL_DEBRID"
            providerName = "Real-Debrid"
            isConnected = true
            email = "test@test.com"
            username = "TestUser"
            health = "HEALTHY"
        }
        debridDao.insertAccount(account)

        db.close()

        // Reopen
        db = Room.databaseBuilder(context, CalmSourceDatabase::class.java, dbName).build()
        val newDao = db.debridDao()

        val accounts = newDao.getAllAccounts().first()
        assertEquals(1, accounts.size)
        val acc = accounts[0]
        assertEquals("deb_1", acc.id)
        assertTrue(acc.isConnected)
        assertEquals("TestUser", acc.username)

        db.close()
    }

    @Test
    fun verifyPreferencesSurviveRecreation() = runBlocking {
        var db = Room.databaseBuilder(context, CalmSourceDatabase::class.java, dbName).build()
        val prefDao = db.preferencesDao()

        val pref = UserPreferencesEntity().apply {
            id = 1
            preferCachedDebrid = true
            preferIptvExactMatch = false
        }
        prefDao.insertPreferences(pref)

        db.close()

        // Reopen
        db = Room.databaseBuilder(context, CalmSourceDatabase::class.java, dbName).build()
        val newDao = db.preferencesDao()

        val prefs = newDao.getPreferences().first()
        assertNotNull(prefs)
        assertTrue(prefs!!.preferCachedDebrid)

        db.close()
    }
}

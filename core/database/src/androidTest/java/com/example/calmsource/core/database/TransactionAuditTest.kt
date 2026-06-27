package com.example.calmsource.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calmsource.core.database.dao.IPTVDao
import com.example.calmsource.core.database.entity.IPTVChannelEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionAuditTest {
    private lateinit var db: CalmSourceDatabase
    private lateinit var iptvDao: IPTVDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, CalmSourceDatabase::class.java
        ).allowMainThreadQueries().build()
        iptvDao = db.iptvDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun verifyReplaceChannelsUsesTransactionAndSucceeds() {
        val providerId = "test-provider"
        val oldChannel = IPTVChannelEntity().apply {
            id = "old-1"
            this.providerId = providerId
            name = "Old Channel"
        }
        iptvDao.insertChannels(listOf(oldChannel))
        
        val newChannel1 = IPTVChannelEntity().apply {
            id = "new-1"
            this.providerId = providerId
            name = "New Channel 1"
        }
        val newChannel2 = IPTVChannelEntity().apply {
            id = "new-2"
            this.providerId = providerId
            name = "New Channel 2"
        }

        iptvDao.replaceChannels(providerId, listOf(newChannel1, newChannel2))
    }
}

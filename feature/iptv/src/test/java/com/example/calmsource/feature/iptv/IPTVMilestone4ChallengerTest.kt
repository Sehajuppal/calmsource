package com.example.calmsource.feature.iptv

import android.content.SharedPreferences
import com.example.calmsource.core.model.IPTVChannel
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class IPTVMilestone4ChallengerTest {

    // ─── 1. EncryptedIptvSecureTokenStore Tests ───

    @Test
    fun testSecureTokenStoreClearProviderAndIsolation() {
        val sharedPrefs = ConcurrentFakeSharedPreferences()
        val store1 = EncryptedIptvSecureTokenStore(sharedPrefs)

        // Save credentials for different providers
        store1.savePassword("prov-1", "userA", "passA")
        store1.savePassword("prov-10", "userB", "passB")
        store1.savePassword("prov-100", "userC", "passC")

        // Verify they are saved
        assertTrue(store1.hasPassword("prov-1", "userA"))
        assertTrue(store1.hasPassword("prov-10", "userB"))
        assertTrue(store1.hasPassword("prov-100", "userC"))

        // Simulate process restart by instantiating a new store instance with same prefs
        val store2 = EncryptedIptvSecureTokenStore(sharedPrefs)
        assertTrue(store2.hasPassword("prov-1", "userA"))
        assertTrue(store2.hasPassword("prov-10", "userB"))
        assertTrue(store2.hasPassword("prov-100", "userC"))

        // Clear provider "prov-1"
        store2.clearProvider("prov-1")

        // Verify prov-1 is cleared but others are unaffected (isolation)
        assertFalse(store2.hasPassword("prov-1", "userA"))
        assertNull(store2.readPassword("prov-1", "userA"))

        assertTrue(store2.hasPassword("prov-10", "userB"))
        assertEquals("passB", store2.readPassword("prov-10", "userB"))
        assertTrue(store2.hasPassword("prov-100", "userC"))
        assertEquals("passC", store2.readPassword("prov-100", "userC"))
    }

    @Test
    fun testSecureTokenStoreConcurrencyStress() {
        val sharedPrefs = ConcurrentFakeSharedPreferences()
        val numThreads = 10
        val operationsPerThread = 100
        val executor = Executors.newFixedThreadPool(numThreads)
        val latch = CountDownLatch(numThreads)

        for (i in 0 until numThreads) {
            executor.submit {
                try {
                    val store = EncryptedIptvSecureTokenStore(sharedPrefs)
                    val providerId = "prov-$i"
                    for (j in 0 until operationsPerThread) {
                        store.savePassword(providerId, "user-$j", "pass-$i-$j")
                        store.readPassword(providerId, "user-$j")
                        if (j % 10 == 0) {
                            store.deletePassword(providerId, "user-${j - 5}")
                        }
                    }
                    store.clearProvider(providerId)
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        // Verify everything was cleared for all provider IDs processed
        val storeVerify = EncryptedIptvSecureTokenStore(sharedPrefs)
        for (i in 0 until numThreads) {
            val providerId = "prov-$i"
            for (j in 0 until operationsPerThread) {
                assertFalse(storeVerify.hasPassword(providerId, "user-$j"))
            }
        }
    }

    // ─── 2. IptvChannelOrganizer Regex Optimizations Tests ───

    @Test
    fun testIptvChannelOrganizerRegexCorrectness() {
        // Test explicit country/language in attributes
        val channel1 = mockChannel("1", "Ch 1", mapOf("tvg-language" to "English", "tvg-country" to "US"))
        assertEquals("English", IptvChannelOrganizer.detectLanguage(channel1))
        assertEquals("United States", IptvChannelOrganizer.detectCountry(channel1))

        // Test regex matching from name
        val channel2 = mockChannel("2", "HBO [English] (US)", emptyMap())
        assertEquals("English", IptvChannelOrganizer.detectLanguage(channel2))
        assertEquals("United States", IptvChannelOrganizer.detectCountry(channel2))

        // Test country codes like UK, USA, FR, DE in braces/separators
        val channel3 = mockChannel("3", "TF1 [FR]", emptyMap())
        assertEquals("France", IptvChannelOrganizer.detectCountry(channel3))

        val channel4 = mockChannel("4", "Sky Sports (UK)", emptyMap())
        assertEquals("United Kingdom", IptvChannelOrganizer.detectCountry(channel4))

        val channel5 = mockChannel("5", "ARD | DE", emptyMap())
        assertEquals("Germany", IptvChannelOrganizer.detectCountry(channel5))

        // Test adult matching
        val channelAdult = mockChannel("6", "Playboy TV xxx", emptyMap())
        // Verify adult channels hidden
        val result = IptvChannelOrganizer.organize(listOf(channelAdult), IptvOptimizationPreferences(hideAdult = true))
        assertEquals(0, result.channels.size)
        assertEquals(1, result.stats.adultHidden)
    }

    @Test
    fun testIptvChannelOrganizerRegexConcurrencyStress() {
        val numThreads = 10
        val runsPerThread = 1000
        val executor = Executors.newFixedThreadPool(numThreads)
        val latch = CountDownLatch(numThreads)

        val channels = listOf(
            mockChannel("1", "HBO [English] (US)", emptyMap()),
            mockChannel("2", "TF1 [FR]", emptyMap()),
            mockChannel("3", "Sky Sports (UK)", emptyMap()),
            mockChannel("4", "ARD | DE", emptyMap()),
            mockChannel("5", "Spanish Channel", emptyMap())
        )

        for (i in 0 until numThreads) {
            executor.submit {
                try {
                    for (j in 0 until runsPerThread) {
                        for (channel in channels) {
                            val lang = IptvChannelOrganizer.detectLanguage(channel)
                            val country = IptvChannelOrganizer.detectCountry(channel)
                            assertTrue(lang.isNotEmpty() || channel.name.contains("Spanish"))
                            assertTrue(country.isNotEmpty() || channel.name.contains("Spanish"))
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
    }

    // ─── 3. EPG bulk delete and query limits static checks ───

    @Test
    fun testEpgBulkDeleteAndQueryLimitsStaticAnalysis() {
        val daoFile = findProjectFile("core/database/src/main/kotlin/com/example/calmsource/core/database/dao/IPTVDao.kt")
        val content = daoFile.readText()

        // 1. Verify EPG Query Limits
        // getEPGProgramsByChannel must have LIMIT 500
        val channelLimitRegex = Regex("@Query\\(\"SELECT \\* FROM epg_programs WHERE channelId = :channelId (?:ORDER BY startTimeMs )?LIMIT 500\"\\)")
        assertTrue(
            "getEPGProgramsByChannel query must be capped at 500 rows!",
            channelLimitRegex.containsMatchIn(content)
        )

        // getAllEPGPrograms must have LIMIT 50000
        val allLimitRegex = Regex("@Query\\(\"SELECT \\* FROM epg_programs LIMIT 50000\"\\)")
        assertTrue(
            "getAllEPGPrograms query must be capped at 50,000 rows to prevent CursorWindow failures!",
            allLimitRegex.containsMatchIn(content)
        )

        // 2. Verify Double-Union Exclusion Query in bulk delete
        val hasBulkDelete = content.contains("fun deleteEPGProgramsByProvider")
        assertTrue("deleteEPGProgramsByProvider must be defined in IPTVDao", hasBulkDelete)

        // Verify the double-union SQL structure
        assertTrue("Bulk delete must use channelId IN subquery", content.contains("WHERE channelId IN ("))
        assertTrue("Bulk delete must select tvgId from iptv_channels", content.contains("SELECT tvgId FROM iptv_channels"))
        assertTrue("Bulk delete must use UNION", content.contains("UNION"))
        assertTrue("Bulk delete must use AND channelId NOT IN", content.contains("AND channelId NOT IN ("))
        assertTrue("Bulk delete must exclude other providers (providerId != :providerId)", content.contains("providerId != :providerId"))
    }

    // Helper functions
    private fun mockChannel(id: String, name: String, attrs: Map<String, String>): IPTVChannel {
        return IPTVChannel(
            id = id,
            tvgId = null,
            tvgName = name,
            tvgLogo = null,
            groupTitle = "General",
            name = name,
            streamUrl = "https://example.com/$id.m3u8",
            providerId = "provider",
            rawAttributes = attrs
        )
    }

    private fun findProjectFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File("d:/Program Files/iptv/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Could not find project file: $relativePath")
    }

    // Concurrent fake implementation of SharedPreferences
    private class ConcurrentFakeSharedPreferences : SharedPreferences {
        private val map = ConcurrentHashMap<String, Any>()

        override fun getAll(): Map<String, *> = map

        override fun getString(key: String, defValue: String?): String? =
            (map[key] as? String) ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            (map[key] as? Set<String>) ?: defValues

        override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = ConcurrentFakeEditor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class ConcurrentFakeEditor : SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any?>()
            private val tempRemoved = mutableSetOf<String>()

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                synchronized(this) {
                    tempMap[key] = value
                    tempRemoved.remove(key)
                }
                return this
            }

            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
                synchronized(this) {
                    tempMap[key] = values?.toSet()
                    tempRemoved.remove(key)
                }
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                synchronized(this) {
                    tempMap[key] = value
                    tempRemoved.remove(key)
                }
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                synchronized(this) {
                    tempMap[key] = value
                    tempRemoved.remove(key)
                }
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                synchronized(this) {
                    tempMap[key] = value
                    tempRemoved.remove(key)
                }
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                synchronized(this) {
                    tempMap[key] = value
                    tempRemoved.remove(key)
                }
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                synchronized(this) {
                    tempRemoved.add(key)
                    tempMap.remove(key)
                }
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                synchronized(this) {
                    tempMap.clear()
                    tempRemoved.addAll(map.keys)
                }
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                synchronized(map) {
                    synchronized(this@ConcurrentFakeEditor) {
                        for (key in tempRemoved) {
                            map.remove(key)
                        }
                        for ((key, value) in tempMap) {
                            if (value != null) {
                                map[key] = value
                            } else {
                                map.remove(key)
                            }
                        }
                    }
                }
            }
        }
    }
}

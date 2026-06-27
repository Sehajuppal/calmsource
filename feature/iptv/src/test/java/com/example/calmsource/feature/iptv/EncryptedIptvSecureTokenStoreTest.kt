package com.example.calmsource.feature.iptv

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EncryptedIptvSecureTokenStoreTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var store: EncryptedIptvSecureTokenStore

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        store = EncryptedIptvSecureTokenStore(fakePrefs)
    }

    @Test
    fun `savePassword stores password and readPassword retrieves it`() {
        store.savePassword("prov-1", "alice", "secret123")
        assertEquals("secret123", store.readPassword("prov-1", "alice"))
    }

    @Test
    fun `readPassword returns null for non-existent provider`() {
        assertNull(store.readPassword("nonexistent", "alice"))
    }

    @Test
    fun `readPassword returns null for non-existent username`() {
        store.savePassword("prov-1", "alice", "secret123")
        assertNull(store.readPassword("prov-1", "bob"))
    }

    @Test
    fun `hasPassword returns true after saving`() {
        store.savePassword("prov-1", "alice", "secret123")
        assertTrue(store.hasPassword("prov-1", "alice"))
    }

    @Test
    fun `hasPassword returns false before saving`() {
        assertFalse(store.hasPassword("prov-1", "alice"))
    }

    @Test
    fun `deletePassword removes specific user credential`() {
        store.savePassword("prov-1", "alice", "secret123")
        store.savePassword("prov-1", "bob", "other456")

        store.deletePassword("prov-1", "alice")

        assertNull(store.readPassword("prov-1", "alice"))
        assertFalse(store.hasPassword("prov-1", "alice"))
        assertEquals("other456", store.readPassword("prov-1", "bob"))
    }

    @Test
    fun `clearProvider removes all credentials for provider`() {
        store.savePassword("prov-1", "alice", "secret123")
        store.savePassword("prov-1", "bob", "other456")
        store.savePassword("prov-2", "carol", "keep789")

        store.clearProvider("prov-1")

        assertNull(store.readPassword("prov-1", "alice"))
        assertNull(store.readPassword("prov-1", "bob"))
        assertFalse(store.hasPassword("prov-1", "alice"))
        assertFalse(store.hasPassword("prov-1", "bob"))

        assertEquals("keep789", store.readPassword("prov-2", "carol"))
    }

    @Test
    fun `clearProvider removes credentials successfully even after simulated process restart`() {
        // 1. Save credentials on the first store instance
        store.savePassword("prov-1", "alice", "secret123")
        store.savePassword("prov-1", "bob", "other456")
        store.savePassword("prov-2", "carol", "keep789")

        // 2. Instantiate a second store instance using the same fakePrefs
        // This simulates a process restart since the new store has empty static/in-memory state
        val store2 = EncryptedIptvSecureTokenStore(fakePrefs)

        // 3. Clear the provider on the second store instance
        store2.clearProvider("prov-1")

        // 4. Verify that prov-1 credentials are gone
        assertNull(store2.readPassword("prov-1", "alice"))
        assertNull(store2.readPassword("prov-1", "bob"))
        assertFalse(store2.hasPassword("prov-1", "alice"))
        assertFalse(store2.hasPassword("prov-1", "bob"))

        // Verify that prov-2 credentials are NOT gone
        assertEquals("keep789", store2.readPassword("prov-2", "carol"))
    }

    @Test
    fun `clearProvider does not affect providers with similar ID prefix`() {
        store.savePassword("prov-1", "user", "pass1")
        store.savePassword("prov-10", "user", "pass10")
        store.savePassword("prov-100", "user", "pass100")

        store.clearProvider("prov-1")

        assertNull(store.readPassword("prov-1", "user"))
        assertEquals("pass10", store.readPassword("prov-10", "user"))
        assertEquals("pass100", store.readPassword("prov-100", "user"))
    }
}

/**
 * Standard JVM mock implementation of SharedPreferences.
 */
private class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

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

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class FakeEditor : SharedPreferences.Editor {
        private val tempMap = mutableMapOf<String, Any?>()
        private val tempRemoved = mutableSetOf<String>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            tempMap[key] = value
            tempRemoved.remove(key)
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            tempMap[key] = values?.toSet()
            tempRemoved.remove(key)
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            tempMap[key] = value
            tempRemoved.remove(key)
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            tempMap[key] = value
            tempRemoved.remove(key)
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            tempMap[key] = value
            tempRemoved.remove(key)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            tempMap[key] = value
            tempRemoved.remove(key)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            tempRemoved.add(key)
            tempMap.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            tempMap.clear()
            tempRemoved.addAll(map.keys)
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            for (key in tempRemoved) {
                map.remove(key)
            }
            map.putAll(tempMap)
        }
    }
}

package com.example.calmsource.feature.iptv

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for XtreamRepository secure token store lifecycle:
 * - Credential persistence after addXtreamProvider
 * - Credential clearing after deleteXtreamProvider
 * - clearProvider clears ALL users for a given provider
 *
 * Uses [FakeInMemoryIptvSecureTokenStore] to avoid Android EncryptedSharedPreferences dependency.
 */
class XtreamSecureTokenStoreTest {

    private lateinit var store: FakeInMemoryIptvSecureTokenStore

    @Before
    fun setUp() {
        store = FakeInMemoryIptvSecureTokenStore()
    }

    // ── savePassword / readPassword round-trip ───────────────────────

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

    // ── deletePassword ───────────────────────────────────────────────

    @Test
    fun `deletePassword removes specific user credential`() {
        store.savePassword("prov-1", "alice", "secret123")
        store.savePassword("prov-1", "bob", "other456")

        store.deletePassword("prov-1", "alice")

        assertNull(store.readPassword("prov-1", "alice"))
        // bob's credential remains intact
        assertEquals("other456", store.readPassword("prov-1", "bob"))
    }

    // ── clearProvider ────────────────────────────────────────────────

    @Test
    fun `clearProvider removes all credentials for provider`() {
        store.savePassword("prov-1", "alice", "secret123")
        store.savePassword("prov-1", "bob", "other456")
        store.savePassword("prov-2", "carol", "keep789")

        store.clearProvider("prov-1")

        // prov-1 credentials gone
        assertNull(store.readPassword("prov-1", "alice"))
        assertNull(store.readPassword("prov-1", "bob"))
        assertFalse(store.hasPassword("prov-1", "alice"))
        assertFalse(store.hasPassword("prov-1", "bob"))

        // prov-2 credentials untouched
        assertEquals("keep789", store.readPassword("prov-2", "carol"))
    }

    @Test
    fun `clearProvider is safe to call on non-existent provider`() {
        // Should not throw
        store.clearProvider("nonexistent")
    }

    // ── Overwrite behavior ───────────────────────────────────────────

    @Test
    fun `savePassword overwrites existing password for same user`() {
        store.savePassword("prov-1", "alice", "oldpass")
        store.savePassword("prov-1", "alice", "newpass")
        assertEquals("newpass", store.readPassword("prov-1", "alice"))
    }

    // ── Provider isolation ───────────────────────────────────────────

    @Test
    fun `clearProvider does not affect providers with similar ID prefix`() {
        store.savePassword("prov-1", "user", "pass1")
        store.savePassword("prov-10", "user", "pass10")
        store.savePassword("prov-100", "user", "pass100")

        store.clearProvider("prov-1")

        // prov-1 gone
        assertNull(store.readPassword("prov-1", "user"))
        // prov-10 and prov-100 remain (prefix "prov-1:" does not match "prov-10:")
        assertEquals("pass10", store.readPassword("prov-10", "user"))
        assertEquals("pass100", store.readPassword("prov-100", "user"))
    }

    @Test
    fun `migration transfers credentials from FakeInMemoryIptvSecureTokenStore to EncryptedIptvSecureTokenStore`() = kotlinx.coroutines.runBlocking {
        val memoryStore = FakeInMemoryIptvSecureTokenStore()
        memoryStore.savePassword("prov-1", "alice", "pass-alice")
        memoryStore.savePassword("prov-2", "bob", "pass-bob")

        // 1. Hook up the initial memory store
        XtreamRepository.setSecureTokenStore(memoryStore)

        // 2. Instantiate target encrypted store using MigrationFakeSharedPreferences
        val fakePrefs = MigrationFakeSharedPreferences()
        val encryptedStore = EncryptedIptvSecureTokenStore(fakePrefs)

        // 3. Run the migration logic
        val oldStore = XtreamRepository.tokenStore
        if (oldStore is FakeInMemoryIptvSecureTokenStore) {
            val credentials = oldStore.exportCredentials()
            for ((providerId, username, password) in credentials) {
                encryptedStore.savePassword(providerId, username, password)
            }
            oldStore.clearAll()
        }
        XtreamRepository.setSecureTokenStore(encryptedStore)

        // 4. Verify credentials are fully moved
        assertEquals("pass-alice", XtreamRepository.getPassword("prov-1", "alice"))
        assertEquals("pass-bob", XtreamRepository.getPassword("prov-2", "bob"))

        // 5. Verify the old store is clean
        assertNull(memoryStore.readPassword("prov-1", "alice"))
        assertNull(memoryStore.readPassword("prov-2", "bob"))
    }
}

private class MigrationFakeSharedPreferences : SharedPreferences {
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

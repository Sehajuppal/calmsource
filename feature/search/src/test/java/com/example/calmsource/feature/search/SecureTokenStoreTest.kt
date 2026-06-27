package com.example.calmsource.feature.search

import android.content.Context
import android.content.SharedPreferences
import com.example.calmsource.core.model.DebridProviderType
import com.example.calmsource.feature.debrid.DebridRepository
import com.example.calmsource.feature.debrid.EncryptedSecureTokenStore
import com.example.calmsource.feature.debrid.FakeInMemorySecureTokenStore
import com.example.calmsource.feature.debrid.SecureTokenStore
import org.junit.After
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.lang.reflect.Field

class SecureTokenStoreTest {

    @After
    fun tearDown() {
        resetDebridRepositoryStore()
    }

    private fun resetDebridRepositoryStore() {
        try {
            val field = DebridRepository::class.java.getDeclaredField("_tokenStore")
            field.isAccessible = true
            field.set(null, null)
        } catch (e: Throwable) {
            // Log or ignore
        }
    }

    @Test
    fun testPrintDebridRepositoryInitError() {
        try {
            Class.forName("com.example.calmsource.feature.debrid.DebridRepository")
        } catch (t: Throwable) {
            println("=== INITIALIZATION FAILURE ===")
            t.printStackTrace()
            t.cause?.printStackTrace()
            println("==============================")
        }
    }

    // Helper fake context to construct EncryptedSecureTokenStore on JVM
    private class FakeAndroidContext : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    // A complete custom in-memory implementation of SharedPreferences for testing EncryptedSecureTokenStore
    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = map

        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue

        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = map[key] as? Set<String> ?: defValues

        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue

        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue

        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(map)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
                tempMap[key] = values
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                tempMap[key] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }

            override fun commit(): Boolean {
                if (clear) map.clear()
                tempMap.forEach { (k, v) ->
                    if (v == null) map.remove(k) else map[k] = v
                }
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }

    private class ThrowingSharedPreferences : SharedPreferences {
        override fun getAll(): Map<String, *> = throw RuntimeException("Simulated keystore error")
        override fun getString(key: String, defValue: String?): String? = throw RuntimeException("Simulated keystore error")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = throw RuntimeException("Simulated keystore error")
        override fun getInt(key: String, defValue: Int): Int = throw RuntimeException("Simulated keystore error")
        override fun getLong(key: String, defValue: Long): Long = throw RuntimeException("Simulated keystore error")
        override fun getFloat(key: String, defValue: Float): Float = throw RuntimeException("Simulated keystore error")
        override fun getBoolean(key: String, defValue: Boolean): Boolean = throw RuntimeException("Simulated keystore error")
        override fun contains(key: String): Boolean = throw RuntimeException("Simulated keystore error")
        override fun edit(): SharedPreferences.Editor = throw RuntimeException("Simulated keystore error")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private fun injectFakePrefs(store: EncryptedSecureTokenStore): FakeSharedPreferences {
        val field: Field = EncryptedSecureTokenStore::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        val fakePrefs = FakeSharedPreferences()
        field.set(store, fakePrefs)
        return fakePrefs
    }

    private fun injectThrowingPrefs(store: EncryptedSecureTokenStore) {
        val field: Field = EncryptedSecureTokenStore::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(store, ThrowingSharedPreferences())
    }

    // --- Tests for FakeInMemorySecureTokenStore ---

    @Test
    fun testFakeStoreSaveReadDeleteHasToken() = runBlocking {
        val store = FakeInMemorySecureTokenStore()
        val provider = DebridProviderType.REAL_DEBRID
        val accountId = "test-acc"
        val tokenType = "access_token"
        val value = "my-secret-token"

        assertFalse(store.hasToken(provider, accountId, tokenType))
        assertNull(store.readToken(provider, accountId, tokenType))

        store.saveToken(provider, accountId, tokenType, value)
        assertTrue(store.hasToken(provider, accountId, tokenType))
        assertEquals(value, store.readToken(provider, accountId, tokenType))

        store.deleteToken(provider, accountId, tokenType)
        assertFalse(store.hasToken(provider, accountId, tokenType))
        assertNull(store.readToken(provider, accountId, tokenType))
    }

    @Test
    fun testFakeStoreClearAccountAndIsolation() = runBlocking {
        val store = FakeInMemorySecureTokenStore()
        
        // Setup two accounts for the same provider
        store.saveToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token", "token1")
        store.saveToken(DebridProviderType.REAL_DEBRID, "acc1_extra", "access_token", "token2")
        store.saveToken(DebridProviderType.ALL_DEBRID, "acc1", "access_token", "token3")

        // Verify all exist
        assertTrue(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token"))
        assertTrue(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1_extra", "access_token"))
        assertTrue(store.hasToken(DebridProviderType.ALL_DEBRID, "acc1", "access_token"))

        // Clear account "acc1" for REAL_DEBRID
        store.clearAccount(DebridProviderType.REAL_DEBRID, "acc1")

        // "acc1" for REAL_DEBRID should be cleared
        assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token"))
        // "acc1_extra" should STILL exist (proves prefix/isolation boundary fixes)
        assertTrue(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1_extra", "access_token"))
        // "acc1" for ALL_DEBRID should STILL exist (proves provider isolation)
        assertTrue(store.hasToken(DebridProviderType.ALL_DEBRID, "acc1", "access_token"))
    }

    @Test
    fun testFakeStoreClearProviderAndIsolation() = runBlocking {
        val store = FakeInMemorySecureTokenStore()

        store.saveToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token", "token1")
        store.saveToken(DebridProviderType.ALL_DEBRID, "acc1", "access_token", "token2")

        store.clearProvider(DebridProviderType.REAL_DEBRID)

        assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token"))
        assertTrue(store.hasToken(DebridProviderType.ALL_DEBRID, "acc1", "access_token"))
    }

    @Test
    fun testFakeStoreClearAll() = runBlocking {
        val store = FakeInMemorySecureTokenStore()

        store.saveToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token", "token1")
        store.saveToken(DebridProviderType.ALL_DEBRID, "acc2", "access_token", "token2")

        store.clearAll()

        assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token"))
        assertFalse(store.hasToken(DebridProviderType.ALL_DEBRID, "acc2", "access_token"))
    }

    // --- Tests for EncryptedSecureTokenStore ---

    @Test
    fun testEncryptedStoreSaveReadDeleteHasToken() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        injectFakePrefs(store)

        val provider = DebridProviderType.REAL_DEBRID
        val accountId = "test-acc"
        val tokenType = "access_token"
        val value = "my-secret-token"

        assertFalse(store.hasToken(provider, accountId, tokenType))
        assertNull(store.readToken(provider, accountId, tokenType))

        store.saveToken(provider, accountId, tokenType, value)
        assertTrue(store.hasToken(provider, accountId, tokenType))
        assertEquals(value, store.readToken(provider, accountId, tokenType))

        store.deleteToken(provider, accountId, tokenType)
        assertFalse(store.hasToken(provider, accountId, tokenType))
        assertNull(store.readToken(provider, accountId, tokenType))
    }

    @Test
    fun testEncryptedStoreClearAccountAndIsolation() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        injectFakePrefs(store)

        store.saveToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token", "token1")
        store.saveToken(DebridProviderType.REAL_DEBRID, "acc1_extra", "access_token", "token2")
        store.saveToken(DebridProviderType.ALL_DEBRID, "acc1", "access_token", "token3")

        store.clearAccount(DebridProviderType.REAL_DEBRID, "acc1")

        assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token"))
        assertTrue(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1_extra", "access_token"))
        assertTrue(store.hasToken(DebridProviderType.ALL_DEBRID, "acc1", "access_token"))
    }

    @Test
    fun testEncryptedStoreClearProviderAndIsolation() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        injectFakePrefs(store)

        store.saveToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token", "token1")
        store.saveToken(DebridProviderType.ALL_DEBRID, "acc1", "access_token", "token2")

        store.clearProvider(DebridProviderType.REAL_DEBRID)

        assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token"))
        assertTrue(store.hasToken(DebridProviderType.ALL_DEBRID, "acc1", "access_token"))
    }

    @Test
    fun testEncryptedStoreClearAll() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        injectFakePrefs(store)

        store.saveToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token", "token1")
        store.saveToken(DebridProviderType.ALL_DEBRID, "acc2", "access_token", "token2")

        store.clearAll()

        assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token"))
        assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token"))
        assertFalse(store.hasToken(DebridProviderType.ALL_DEBRID, "acc2", "access_token"))
    }

    @Test
    fun testDeleteTokensOnlyClearsDefaultAccount() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        injectFakePrefs(store)

        // Save for default account
        store.saveToken(DebridProviderType.REAL_DEBRID, "default", "access_token", "default-token")
        // Save for another account
        store.saveToken(DebridProviderType.REAL_DEBRID, "other_acc", "access_token", "other-token")

        // deleteTokens uses the default account
        store.deleteTokens(DebridProviderType.REAL_DEBRID)

        // Default should be deleted
        assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, "default", "access_token"))
        // Other account should still exist
        assertTrue(store.hasToken(DebridProviderType.REAL_DEBRID, "other_acc", "access_token"))
    }

    @Test
    fun testEncryptedStoreSafeFailsWithThrowingPrefs() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        injectThrowingPrefs(store)

        val provider = DebridProviderType.REAL_DEBRID
        val accountId = "test-acc"
        val tokenType = "access_token"

        // Operations should not crash when underlying storage throws exceptions
        try {
            store.saveToken(provider, accountId, tokenType, "secret")
            assertFalse("readToken should return null on error", store.hasToken(provider, accountId, tokenType))
            assertNull("readToken should return null on error", store.readToken(provider, accountId, tokenType))
            store.deleteToken(provider, accountId, tokenType)
            store.clearAccount(provider, accountId)
            store.clearProvider(provider)
            store.clearAll()
        } catch (e: Exception) {
            fail("Secure token store operations should fail silently without crashing, but got exception: $e")
        }
    }

    @Test
    fun testProductionBindingExists() = runBlocking {
        // In unit test environment, DebridRepository should fall back to FakeInMemorySecureTokenStore
        val defaultStore = DebridRepository.tokenStore
        assertTrue("Default tokenStore should be FakeInMemorySecureTokenStore", defaultStore is FakeInMemorySecureTokenStore)

        // After calling DebridRepository.init with a context, it should initialize to EncryptedSecureTokenStore
        DebridRepository.init(FakeAndroidContext())
        val initializedStore = DebridRepository.tokenStore
        assertTrue("Initialized tokenStore should be EncryptedSecureTokenStore", initializedStore is EncryptedSecureTokenStore)
    }

    @Test
    fun testCustomKeysAreCleared() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        val fakePrefs = injectFakePrefs(store)

        // Save normal and custom keys
        store.saveToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token", "val1")
        store.saveToken(DebridProviderType.REAL_DEBRID, "acc1", "my_custom_secret_key", "custom_val")

        assertTrue(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token"))
        assertTrue(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "my_custom_secret_key"))

        // Clear account
        store.clearAccount(DebridProviderType.REAL_DEBRID, "acc1")

        // Both default and custom keys must be deleted
        assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token"))
        assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, "acc1", "my_custom_secret_key"))
        
        // Also verify the tracking key is removed from prefs
        assertFalse(fakePrefs.contains("cs_secure_keys:${DebridProviderType.REAL_DEBRID.name}:acc1"))
    }

    @Test
    fun testClearProviderAfterAppRestart() = runBlocking {
        val context = FakeAndroidContext()
        val store1 = EncryptedSecureTokenStore(context)
        val fakePrefs = injectFakePrefs(store1)

        // Save token on first store instance
        store1.saveToken(DebridProviderType.REAL_DEBRID, "acc1", "access_token", "val1")
        store1.saveToken(DebridProviderType.REAL_DEBRID, "acc1", "custom_key", "val2")

        assertTrue(fakePrefs.contains("cs_secure_account_ids:${DebridProviderType.REAL_DEBRID.name}"))

        // Simulate app restart by constructing a new store and injecting the same fake preferences
        val store2 = EncryptedSecureTokenStore(context)
        val fieldPrefs: Field = EncryptedSecureTokenStore::class.java.getDeclaredField("prefs")
        fieldPrefs.isAccessible = true
        fieldPrefs.set(store2, fakePrefs)

        // Run the init logic manually to simulate read on restart
        val fieldKnownIds: Field = EncryptedSecureTokenStore::class.java.getDeclaredField("knownAccountIds")
        fieldKnownIds.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val knownAccountIds = fieldKnownIds.get(store2) as MutableMap<DebridProviderType, MutableSet<String>>
        
        for (providerType in DebridProviderType.values()) {
            val saved = fakePrefs.getStringSet("cs_secure_account_ids:${providerType.name}", null)
            if (saved != null) {
                knownAccountIds[providerType] = saved.toMutableSet()
            }
        }

        // Now clear the provider using the restarted store
        store2.clearProvider(DebridProviderType.REAL_DEBRID)

        // All keys including custom keys and the account set should be cleared from prefs
        assertFalse(fakePrefs.contains("cs_secure:${DebridProviderType.REAL_DEBRID.name}:acc1:access_token"))
        assertFalse(fakePrefs.contains("cs_secure:${DebridProviderType.REAL_DEBRID.name}:acc1:custom_key"))
        assertFalse(fakePrefs.contains("cs_secure_account_ids:${DebridProviderType.REAL_DEBRID.name}"))
        assertFalse(fakePrefs.contains("cs_secure_keys:${DebridProviderType.REAL_DEBRID.name}:acc1"))
    }

    @Test
    fun testChallengerSecureTokenStoreExtremeStates() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        val fakePrefs = injectFakePrefs(store)

        // Test special characters in account ID and tokenType
        val specialAcc = "acc:id\n\r🌟"
        val specialType = "token:type\n\r🔥"
        val secretValue = "secret\nvalue"

        store.saveToken(DebridProviderType.REAL_DEBRID, specialAcc, specialType, secretValue)
        assertTrue(store.hasToken(DebridProviderType.REAL_DEBRID, specialAcc, specialType))
        assertEquals(secretValue, store.readToken(DebridProviderType.REAL_DEBRID, specialAcc, specialType))

        // Clear and verify
        store.clearAccount(DebridProviderType.REAL_DEBRID, specialAcc)
        assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, specialAcc, specialType))
        assertNull(store.readToken(DebridProviderType.REAL_DEBRID, specialAcc, specialType))

        // Large number of keys stress test
        val count = 200
        val accId = "heavy_acc"
        for (i in 1..count) {
            store.saveToken(DebridProviderType.REAL_DEBRID, accId, "key_$i", "val_$i")
        }

        // Verify all exist
        for (i in 1..count) {
            assertTrue(store.hasToken(DebridProviderType.REAL_DEBRID, accId, "key_$i"))
        }

        // Clear account and verify all are deleted
        store.clearAccount(DebridProviderType.REAL_DEBRID, accId)
        for (i in 1..count) {
            assertFalse(store.hasToken(DebridProviderType.REAL_DEBRID, accId, "key_$i"))
        }
    }

    @Test
    fun testChallengerSecureTokenStoreConcurrency() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        injectFakePrefs(store)

        val threads = mutableListOf<Thread>()
        val exceptionList = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        // Launch concurrent writes and clears
        for (t in 0 until 10) {
            val thread = Thread {
                try {
                    for (i in 0 until 50) {
                        store.saveToken(DebridProviderType.REAL_DEBRID, "acc_$t", "token_$i", "val_$i")
                        store.readToken(DebridProviderType.REAL_DEBRID, "acc_$t", "token_$i")
                    }
                    store.clearAccount(DebridProviderType.REAL_DEBRID, "acc_$t")
                } catch (e: Throwable) {
                    exceptionList.add(e)
                }
            }
            threads.add(thread)
            thread.start()
        }

        threads.forEach { it.join() }

        assertTrue("Concurrency should not throw exceptions: $exceptionList", exceptionList.isEmpty())
    }

    private class TransientlyFailingSharedPreferences : SharedPreferences {
        var shouldThrow = false
        var shouldThrowOnAccountIds = false
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = map

        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue

        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = map[key] as? Set<String> ?: defValues

        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue

        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue

        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor {
            if (shouldThrow) throw RuntimeException("Transient Keystore failure on edit")
            return FakeEditor(map, this)
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val map: MutableMap<String, Any?>, private val parent: TransientlyFailingSharedPreferences) : SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor { tempMap[key] = value; return this }
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { tempMap[key] = values; return this }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor { tempMap[key] = value; return this }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor { tempMap[key] = value; return this }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor { tempMap[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { tempMap[key] = value; return this }
            override fun remove(key: String): SharedPreferences.Editor { tempMap[key] = null; return this }
            override fun clear(): SharedPreferences.Editor { clear = true; return this }

            override fun commit(): Boolean {
                if (parent.shouldThrow) throw RuntimeException("Transient Keystore failure during commit")
                if (parent.shouldThrowOnAccountIds && tempMap.containsKey("cs_secure_account_ids:${DebridProviderType.REAL_DEBRID.name}")) {
                    throw RuntimeException("Transient Keystore failure writing account IDs during commit")
                }
                if (clear) map.clear()
                tempMap.forEach { (k, v) ->
                    if (v == null) map.remove(k) else map[k] = v
                }
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }

    @Test
    fun testAccountRegistrationLeakUnderTransientFailure() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        val failingPrefs = TransientlyFailingSharedPreferences()
        
        val field: Field = EncryptedSecureTokenStore::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(store, failingPrefs)

        val provider = DebridProviderType.REAL_DEBRID
        val accountId = "transient-acc"

        // 1. Enable throwing to simulate transient error
        failingPrefs.shouldThrow = true

        store.saveToken(provider, accountId, "access_token", "secret1")

        // 2. Disable throwing so store is healthy now
        failingPrefs.shouldThrow = false

        // Try saving a token again. This should succeed, but because of the in-memory state desync,
        // it may fail to update the persisted account IDs index!
        store.saveToken(provider, accountId, "access_token", "secret2")

        val persistedAccountIds = failingPrefs.getStringSet("cs_secure_account_ids:${provider.name}", null)
        
        assertNotNull("Account ID should have been persisted once the store became healthy", persistedAccountIds)
        assertTrue("Account ID should be in the persisted set", persistedAccountIds?.contains(accountId) == true)
    }

    @Test
    fun testChallengerAccountRegistrationLeakUnderTransientRegisterFailure() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        val failingPrefs = TransientlyFailingSharedPreferences()
        
        val field: Field = EncryptedSecureTokenStore::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(store, failingPrefs)

        val provider = DebridProviderType.REAL_DEBRID
        val accountId = "leak-acc"

        // 1. Simulate failure ONLY when writing account IDs set
        failingPrefs.shouldThrowOnAccountIds = true

        store.saveToken(provider, accountId, "access_token", "secret1")

        // 2. Disable throwing so store is healthy now
        failingPrefs.shouldThrowOnAccountIds = false

        // Save another token for the same account.
        store.saveToken(provider, accountId, "refresh_token", "secret2")

        val persistedAccountIds = failingPrefs.getStringSet("cs_secure_account_ids:${provider.name}", null)
        
        // Assert that the account ID has been persisted.
        assertNotNull("Account ID should have been persisted once the store became healthy", persistedAccountIds)
        assertTrue("Account ID should be in the persisted set", persistedAccountIds?.contains(accountId) == true)
    }

    @Test
    fun testChallengerClearProviderTransientFailureDesync() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        val failingPrefs = TransientlyFailingSharedPreferences()
        
        val field: Field = EncryptedSecureTokenStore::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(store, failingPrefs)

        val provider = DebridProviderType.REAL_DEBRID
        val acc1 = "acc-1"
        val acc2 = "acc-2"

        // 1. Save tokens normally
        store.saveToken(provider, acc1, "access_token", "val1")
        store.saveToken(provider, acc2, "access_token", "val2")

        // Check that they are persisted
        val initialAccountIds = failingPrefs.getStringSet("cs_secure_account_ids:${provider.name}", null)
        assertNotNull(initialAccountIds)
        assertEquals(2, initialAccountIds?.size)

        // 2. Clear provider under transient failure
        failingPrefs.shouldThrow = true
        store.clearProvider(provider)

        // 3. The preferences write failed, so the set in preferences should still contain both accounts
        failingPrefs.shouldThrow = false
        val accountsAfterFailedClear = failingPrefs.getStringSet("cs_secure_account_ids:${provider.name}", null)
        assertNotNull(accountsAfterFailedClear)
        assertEquals(2, accountsAfterFailedClear?.size)

        // 4. Now save a token for a new account (acc-3)
        val acc3 = "acc-3"
        store.saveToken(provider, acc3, "access_token", "val3")

        // 5. Check the persisted accounts set.
        val finalAccountIds = failingPrefs.getStringSet("cs_secure_account_ids:${provider.name}", null)
        assertNotNull(finalAccountIds)
        
        // Assert that the old accounts are NOT lost.
        assertTrue("acc-1 should still be registered", finalAccountIds?.contains(acc1) == true)
        assertTrue("acc-2 should still be registered", finalAccountIds?.contains(acc2) == true)
        assertTrue("acc-3 should be registered", finalAccountIds?.contains(acc3) == true)
    }

    @Test
    fun testChallengerAccountUnregistrationLeakUnderTransientUnregisterFailure() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        val failingPrefs = TransientlyFailingSharedPreferences()
        
        val field: Field = EncryptedSecureTokenStore::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(store, failingPrefs)

        val provider = DebridProviderType.REAL_DEBRID
        val accountId = "leak-acc"

        // 1. Save token (registers the account)
        store.saveToken(provider, accountId, "access_token", "secret1")

        // Verify it is registered
        val initialAccountIds = failingPrefs.getStringSet("cs_secure_account_ids:${provider.name}", null)
        assertNotNull(initialAccountIds)
        assertTrue(initialAccountIds?.contains(accountId) == true)

        // 2. Enable throwing only for account IDs during unregistration
        failingPrefs.shouldThrowOnAccountIds = true

        // 3. Clear account (calls unregisterAccount)
        store.clearAccount(provider, accountId)

        // 4. Disable throwing
        failingPrefs.shouldThrowOnAccountIds = false

        // Since the write failed, the account ID should STILL be persisted in SharedPreferences
        val persistedAccountIds = failingPrefs.getStringSet("cs_secure_account_ids:${provider.name}", null)
        assertNotNull("Account ID should still be persisted since the write failed", persistedAccountIds)
        assertTrue("Account ID should still be in the set", persistedAccountIds?.contains(accountId) == true)

        // 5. Call clearAccount again now that it is healthy. It should succeed.
        store.clearAccount(provider, accountId)

        val finalAccountIds = failingPrefs.getStringSet("cs_secure_account_ids:${provider.name}", null)
        assertTrue("Account ID should be completely removed now", finalAccountIds == null || !finalAccountIds.contains(accountId))
    }

    @Test
    fun testChallengerEmptyAccountId() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        val fakePrefs = injectFakePrefs(store)
        val provider = DebridProviderType.REAL_DEBRID

        // Save with empty account ID
        store.saveToken(provider, "", "access_token", "empty-token")
        assertTrue(store.hasToken(provider, "", "access_token"))
        assertEquals("empty-token", store.readToken(provider, "", "access_token"))

        // Clear account with empty ID
        store.clearAccount(provider, "")
        assertFalse(store.hasToken(provider, "", "access_token"))
        assertNull(store.readToken(provider, "", "access_token"))

        // Verify account list is empty
        val accountIds = fakePrefs.getStringSet("cs_secure_account_ids:${provider.name}", null)
        assertTrue(accountIds == null || accountIds.isEmpty())
    }

    @Test
    fun testChallengerSpecialCharactersDelimiterCollision() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        injectFakePrefs(store)
        val provider = DebridProviderType.REAL_DEBRID

        // Test potential key collision issues with delimiters
        val acc1 = "foo:bar"
        val acc2 = "foo"
        val type1 = "baz"
        val type2 = "bar:baz"

        store.saveToken(provider, acc1, type1, "val1")
        store.saveToken(provider, acc2, type2, "val2")

        // They must remain distinct
        assertEquals("val1", store.readToken(provider, acc1, type1))
        assertEquals("val2", store.readToken(provider, acc2, type2))

        // Clear one should not affect the other
        store.clearAccount(provider, acc1)
        assertFalse(store.hasToken(provider, acc1, type1))
        assertTrue(store.hasToken(provider, acc2, type2))
    }

    @Test
    fun testChallengerClearTransientFailureEditBlock() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        val failingPrefs = TransientlyFailingSharedPreferences()
        
        val field: Field = EncryptedSecureTokenStore::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(store, failingPrefs)

        val provider = DebridProviderType.REAL_DEBRID
        val acc = "test-acc-transient"

        store.saveToken(provider, acc, "access_token", "val1")
        assertTrue(store.hasToken(provider, acc, "access_token"))

        // Set shouldThrow to true so prefs.edit() itself throws an exception
        failingPrefs.shouldThrow = true

        // clearAccount should fail silently and keep the token because SharedPreferences couldn't be edited
        store.clearAccount(provider, acc)
        failingPrefs.shouldThrow = false
        assertTrue("Token should still exist since clearAccount failed transiently", store.hasToken(provider, acc, "access_token"))

        // Now clearProvider under the same transient failure
        failingPrefs.shouldThrow = true
        store.clearProvider(provider)
        failingPrefs.shouldThrow = false
        assertTrue("Token should still exist since clearProvider failed transiently", store.hasToken(provider, acc, "access_token"))
    }

    @Test
    fun testChallengerComprehensiveCollisionSearch() = runBlocking {
        val store = EncryptedSecureTokenStore(FakeAndroidContext())
        val fakePrefs = injectFakePrefs(store)
        val provider = DebridProviderType.REAL_DEBRID

        // We want to test if any two distinct (accountId, tokenType) pairs can produce the same key.
        // We will generate combinations of chars ':', '\', 'a' of length up to 4.
        val alphabet = listOf("a", ":", "\\")
        val combinations = mutableListOf<String>()

        fun generate(current: String, depth: Int) {
            combinations.add(current)
            if (depth >= 4) return
            for (char in alphabet) {
                generate(current + char, depth + 1)
            }
        }
        generate("", 0)

        val keysSeen = mutableMapOf<String, Pair<String, String>>()

        for (acc in combinations) {
            for (type in combinations) {
                // Call the private key generator using reflection
                val method = EncryptedSecureTokenStore::class.java.getDeclaredMethod(
                    "key",
                    DebridProviderType::class.java,
                    String::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val key = method.invoke(store, provider, acc, type) as String

                val existing = keysSeen[key]
                if (existing != null && (existing.first != acc || existing.second != type)) {
                    fail("COLLISION FOUND! Key: '$key' produced by both (acc='${existing.first}', type='${existing.second}') and (acc='$acc', type='$type')")
                }
                keysSeen[key] = Pair(acc, type)
            }
        }
        println("Checked ${keysSeen.size} unique combinations. No collisions found!")
    }
}



package com.example.calmsource.core.data.sync

import android.content.Context
import com.example.calmsource.core.data.CloudAuthTokenStore
import com.example.calmsource.core.network.CloudAuthRepository
import com.example.calmsource.core.network.VaultFetchResponse
import com.example.calmsource.core.network.VaultUpdateResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class VaultSyncManagerTest {

    private lateinit var context: Context
    private lateinit var tokenStore: CloudAuthTokenStore
    private lateinit var syncManager: VaultSyncManager

    private var gatheredIpTv: Triple<String, String, String>? = null
    private var restoredIpTv: Triple<String, String, String>? = null

    private var gatheredDebrid: String? = null
    private var restoredDebrid: String? = null

    private var gatheredExtensions = mutableListOf<String>()
    private var restoredExtensions = mutableListOf<String>()

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        tokenStore = CloudAuthTokenStore(context)
        tokenStore.setToken("mock_jwt_token_12345")

        syncManager = VaultSyncManager(tokenStore)

        syncManager.gatherIpTvDelegate = { gatheredIpTv }
        syncManager.restoreIpTvDelegate = { url, user, pass ->
            restoredIpTv = Triple(url, user, pass)
        }
        syncManager.gatherDebridDelegate = { gatheredDebrid }
        syncManager.restoreDebridDelegate = { token ->
            restoredDebrid = token
        }
        syncManager.gatherExtensionsDelegate = { gatheredExtensions }
        syncManager.restoreExtensionsDelegate = { list ->
            restoredExtensions.clear()
            restoredExtensions.addAll(list)
        }
    }

    @After
    fun tearDown() {
        CloudAuthRepository.mockFetchVault = null
        CloudAuthRepository.mockUpdateVault = null
    }

    @Test
    fun testBackupAndRestoreRoundtrip() = runBlocking {
        gatheredIpTv = Triple("http://xtream.server:8080", "testuser", "testpass")
        gatheredDebrid = "debrid_token_xyz"
        gatheredExtensions.addAll(listOf("http://ext1.json", "http://ext2.json"))

        var uploadedCiphertext: String? = null
        CloudAuthRepository.mockUpdateVault = { token, request ->
            assertEquals("mock_jwt_token_12345", token)
            uploadedCiphertext = request.vault_ciphertext
            VaultUpdateResponse(success = true)
        }

        val password = "MySuperSecretBackupPassword123!"
        syncManager.backup(password)

        assertNotNull("Ciphertext should be uploaded", uploadedCiphertext)
        assertTrue("Ciphertext should not be empty", uploadedCiphertext!!.isNotEmpty())

        CloudAuthRepository.mockFetchVault = { token ->
            assertEquals("mock_jwt_token_12345", token)
            VaultFetchResponse(vault_ciphertext = uploadedCiphertext)
        }

        restoredIpTv = null
        restoredDebrid = null
        restoredExtensions.clear()

        syncManager.restore(password)

        assertNotNull(restoredIpTv)
        assertEquals("http://xtream.server:8080", restoredIpTv!!.first)
        assertEquals("testuser", restoredIpTv!!.second)
        assertEquals("testpass", restoredIpTv!!.third)

        assertEquals("debrid_token_xyz", restoredDebrid)

        assertEquals(2, restoredExtensions.size)
        assertTrue(restoredExtensions.contains("http://ext1.json"))
        assertTrue(restoredExtensions.contains("http://ext2.json"))
    }

    @Test
    fun testRestoreWithIncorrectPasswordThrows() = runBlocking {
        gatheredIpTv = Triple("http://xtream.server:8080", "testuser", "testpass")

        var uploadedCiphertext: String? = null
        CloudAuthRepository.mockUpdateVault = { _, request ->
            uploadedCiphertext = request.vault_ciphertext
            VaultUpdateResponse(success = true)
        }

        val password = "CorrectPassword"
        syncManager.backup(password)

        CloudAuthRepository.mockFetchVault = { _ ->
            VaultFetchResponse(vault_ciphertext = uploadedCiphertext)
        }

        var threwException = false
        try {
            syncManager.restore("WrongPassword")
        } catch (e: Exception) {
            threwException = true
        }
        assertTrue("Restoring with incorrect password must throw an exception", threwException)
    }

    @Test
    fun testEmptyVaultRestoreDoesNothing() = runBlocking {
        CloudAuthRepository.mockFetchVault = { _ ->
            VaultFetchResponse(vault_ciphertext = null)
        }

        restoredIpTv = null
        restoredDebrid = null
        restoredExtensions.clear()

        syncManager.restore("AnyPassword")

        assertTrue(restoredIpTv == null)
        assertTrue(restoredDebrid == null)
        assertTrue(restoredExtensions.isEmpty())
    }

    @Test
    fun testCiphertextTamperingThrowsAEADBadTag() = runBlocking {
        gatheredIpTv = Triple("http://xtream.server:8080", "testuser", "testpass")
        var uploadedCiphertext: String? = null
        CloudAuthRepository.mockUpdateVault = { _, request ->
            uploadedCiphertext = request.vault_ciphertext
            VaultUpdateResponse(success = true)
        }

        val password = "StrongPassword123!"
        syncManager.backup(password)
        assertNotNull(uploadedCiphertext)

        val decoded = try {
            java.util.Base64.getDecoder().decode(uploadedCiphertext!!)
        } catch (e: Throwable) {
            android.util.Base64.decode(uploadedCiphertext!!, android.util.Base64.DEFAULT)
        }

        val tamperedCiphertext = decoded.copyOf()
        if (tamperedCiphertext.size > 28) {
            tamperedCiphertext[28] = (tamperedCiphertext[28].toInt() xor 1).toByte()
        } else {
            fail("Ciphertext is too short")
        }

        val base64Tampered = try {
            java.util.Base64.getEncoder().encodeToString(tamperedCiphertext)
        } catch (e: Throwable) {
            android.util.Base64.encodeToString(tamperedCiphertext, android.util.Base64.NO_WRAP)
        }

        CloudAuthRepository.mockFetchVault = { _ ->
            VaultFetchResponse(vault_ciphertext = base64Tampered)
        }

        try {
            syncManager.restore(password)
            fail("Expected AEADBadTagException or GeneralSecurityException when ciphertext is tampered")
        } catch (e: javax.crypto.AEADBadTagException) {
            // Success
        } catch (e: java.security.GeneralSecurityException) {
            // Success
        } catch (e: Throwable) {
            val cause = e.cause
            if (e is java.lang.RuntimeException && (cause is javax.crypto.AEADBadTagException || cause is java.security.GeneralSecurityException)) {
                // Success
            } else {
                fail("Unexpected exception type: ${e.javaClass.name} - ${e.message}")
            }
        }
    }

    @Test
    fun testIvSaltTamperingThrowsAEADBadTag() = runBlocking {
        gatheredIpTv = Triple("http://xtream.server:8080", "testuser", "testpass")
        var uploadedCiphertext: String? = null
        CloudAuthRepository.mockUpdateVault = { _, request ->
            uploadedCiphertext = request.vault_ciphertext
            VaultUpdateResponse(success = true)
        }

        val password = "StrongPassword123!"
        syncManager.backup(password)
        assertNotNull(uploadedCiphertext)

        val decoded = try {
            java.util.Base64.getDecoder().decode(uploadedCiphertext!!)
        } catch (e: Throwable) {
            android.util.Base64.decode(uploadedCiphertext!!, android.util.Base64.DEFAULT)
        }

        val tamperedIv = decoded.copyOf()
        tamperedIv[16] = (tamperedIv[16].toInt() xor 1).toByte()

        val base64TamperedIv = try {
            java.util.Base64.getEncoder().encodeToString(tamperedIv)
        } catch (e: Throwable) {
            android.util.Base64.encodeToString(tamperedIv, android.util.Base64.NO_WRAP)
        }

        CloudAuthRepository.mockFetchVault = { _ ->
            VaultFetchResponse(vault_ciphertext = base64TamperedIv)
        }

        try {
            syncManager.restore(password)
            fail("Expected AEADBadTagException or GeneralSecurityException when IV is tampered")
        } catch (e: javax.crypto.AEADBadTagException) {
            // Success
        } catch (e: java.security.GeneralSecurityException) {
            // Success
        } catch (e: Throwable) {
            val cause = e.cause
            if (e is java.lang.RuntimeException && (cause is javax.crypto.AEADBadTagException || cause is java.security.GeneralSecurityException)) {
                // Success
            } else {
                fail("Unexpected exception type: ${e.javaClass.name} - ${e.message}")
            }
        }

        val tamperedSalt = decoded.copyOf()
        tamperedSalt[0] = (tamperedSalt[0].toInt() xor 1).toByte()

        val base64TamperedSalt = try {
            java.util.Base64.getEncoder().encodeToString(tamperedSalt)
        } catch (e: Throwable) {
            android.util.Base64.encodeToString(tamperedSalt, android.util.Base64.NO_WRAP)
        }

        CloudAuthRepository.mockFetchVault = { _ ->
            VaultFetchResponse(vault_ciphertext = base64TamperedSalt)
        }

        try {
            syncManager.restore(password)
            fail("Expected AEADBadTagException or GeneralSecurityException when salt is tampered")
        } catch (e: javax.crypto.AEADBadTagException) {
            // Success
        } catch (e: java.security.GeneralSecurityException) {
            // Success
        } catch (e: Throwable) {
            val cause = e.cause
            if (e is java.lang.RuntimeException && (cause is javax.crypto.AEADBadTagException || cause is java.security.GeneralSecurityException)) {
                // Success
            } else {
                fail("Unexpected exception type: ${e.javaClass.name} - ${e.message}")
            }
        }
    }

    @Test
    fun testConcurrentBackupRestoreDoesNotDeadlock() = runBlocking {
        gatheredIpTv = Triple("http://xtream.server:8080", "testuser", "testpass")
        var uploadedCiphertext: String? = null
        CloudAuthRepository.mockUpdateVault = { _, request ->
            uploadedCiphertext = request.vault_ciphertext
            VaultUpdateResponse(success = true)
        }
        CloudAuthRepository.mockFetchVault = { _ ->
            VaultFetchResponse(vault_ciphertext = uploadedCiphertext)
        }

        val password = "ConcurrentPassword"

        val jobs = List(10) { i ->
            async(kotlinx.coroutines.Dispatchers.Default) {
                if (i % 2 == 0) {
                    syncManager.backup(password)
                } else {
                    syncManager.restore(password)
                }
            }
        }
        jobs.awaitAll()
        assertTrue(true)
    }

    @Test
    fun testSettingsMergingLogicCorrectness() = runBlocking {
        val databaseProviders = mutableListOf<Triple<String, String, String>>()

        syncManager.restoreIpTvDelegate = { url, user, pass ->
            val exists = databaseProviders.any {
                it.first == url && it.second == user
            }
            if (!exists) {
                databaseProviders.add(Triple(url, user, pass))
            }
        }

        gatheredIpTv = Triple("http://provider-a.com", "userA", "passA")

        var uploadedCiphertext: String? = null
        CloudAuthRepository.mockUpdateVault = { _, request ->
            uploadedCiphertext = request.vault_ciphertext
            VaultUpdateResponse(success = true)
        }
        CloudAuthRepository.mockFetchVault = { _ ->
            VaultFetchResponse(vault_ciphertext = uploadedCiphertext)
        }

        val password = "MergeTestPassword"
        syncManager.backup(password)

        databaseProviders.add(Triple("http://provider-a.com", "userA", "passA"))
        assertEquals(1, databaseProviders.size)

        syncManager.restore(password)
        assertEquals("Should not duplicate existing provider", 1, databaseProviders.size)

        databaseProviders.clear()
        assertEquals(0, databaseProviders.size)

        syncManager.restore(password)
        assertEquals("Should add new provider", 1, databaseProviders.size)
        assertEquals("http://provider-a.com", databaseProviders[0].first)
        assertEquals("userA", databaseProviders[0].second)
    }

    @Test
    fun testPBKDF2IterationsAndKeySize() = runBlocking {
        gatheredIpTv = Triple("http://xtream.server:8080", "testuser", "testpass")
        var uploadedCiphertext: String? = null
        CloudAuthRepository.mockUpdateVault = { _, request ->
            uploadedCiphertext = request.vault_ciphertext
            VaultUpdateResponse(success = true)
        }

        val password = "MyPbkdf2TestPassword"
        syncManager.backup(password)
        assertNotNull(uploadedCiphertext)

        val combined = try {
            java.util.Base64.getDecoder().decode(uploadedCiphertext!!)
        } catch (e: Throwable) {
            android.util.Base64.decode(uploadedCiphertext!!, android.util.Base64.DEFAULT)
        }

        assertTrue(combined.size >= 28)
        val salt = ByteArray(16)
        val iv = ByteArray(12)
        val ciphertext = ByteArray(combined.size - 28)

        System.arraycopy(combined, 0, salt, 0, 16)
        System.arraycopy(combined, 16, iv, 0, 12)
        System.arraycopy(combined, 28, ciphertext, 0, ciphertext.size)

        val keySpec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derivedKeyBytes = factory.generateSecret(keySpec).encoded

        assertEquals("Derived key size must be 256 bits (32 bytes)", 32, derivedKeyBytes.size)

        val secretKey = javax.crypto.spec.SecretKeySpec(derivedKeyBytes, "AES")

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        val decryptedJson = String(decryptedBytes, Charsets.UTF_8)

        assertTrue("Decrypted JSON should contain xtreamUrl", decryptedJson.contains("xtreamUrl"))
        assertTrue("Decrypted JSON should contain testuser", decryptedJson.contains("testuser"))
    }
}

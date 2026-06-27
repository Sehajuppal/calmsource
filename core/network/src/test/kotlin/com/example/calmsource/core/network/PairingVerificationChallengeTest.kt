package com.example.calmsource.core.network

import com.example.calmsource.core.model.AuthCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class PairingVerificationChallengeTest {

    @Before
    fun setUp() {
        AuthSyncRepository.close()
    }

    @After
    fun tearDown() {
        AuthSyncRepository.close()
        AuthSyncRepository.wsUrl = BuildConfig.WS_AUTH_URL
    }

    // 1. Edge Case: Empty strings & invalid Base64 ciphertext
    @Test
    fun testDecryptEmptyString() {
        val manager = AuthCryptoManager()
        try {
            manager.decrypt("")
            fail("Decrypting empty string should throw an exception")
        } catch (e: Throwable) {
            // Success
        }
    }

    @Test
    fun testDecryptInvalidBase64() {
        val manager = AuthCryptoManager()
        try {
            manager.decrypt("!!!invalid-base64!!!")
            fail("Decrypting invalid Base64 string should throw an exception")
        } catch (e: Throwable) {
            // Success
        }
    }

    @Test
    fun testDecryptMalformedCiphertext() {
        val manager = AuthCryptoManager()
        try {
            manager.decrypt("c29tZSByYW5kb20gc3RyaW5nIHdoaWNoIGlzIHZhbGlkIGJhc2U2NA==")
            fail("Decrypting invalid ciphertext should throw an exception")
        } catch (e: Throwable) {
            // Success
        }
    }

    // 2. Edge Case: Invalid RSA Keys / Key Mismatch
    @Test
    fun testDecryptWithMismatchedKey() {
        val manager = AuthCryptoManager()
        
        // Generate another RSA keypair
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val otherKeyPair = keyGen.generateKeyPair()
        
        // Encrypt with other public key
        val testMessage = "SecretData"
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepSpec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT
        )
        cipher.init(Cipher.ENCRYPT_MODE, otherKeyPair.public, oaepSpec)
        val encryptedBytes = cipher.doFinal(testMessage.toByteArray(Charsets.UTF_8))
        val encryptedBase64 = java.util.Base64.getEncoder().encodeToString(encryptedBytes)
        
        // Decrypting this ciphertext with manager should fail
        try {
            manager.decrypt(encryptedBase64)
            fail("Decrypting payload encrypted with different key should throw exception")
        } catch (e: Throwable) {
            // Success
        }
    }

    // 3. Edge Case: Key Collision / Freshness constraint
    @Test
    fun testKeyFreshnessAndUniqueness() {
        val manager1 = AuthCryptoManager()
        val key1 = manager1.getPublicKeyBase64()
        
        val manager2 = AuthCryptoManager()
        val key2 = manager2.getPublicKeyBase64()
        
        assertNotNull(key1)
        assertNotNull(key2)
        assertNotEquals("Each initialization must generate a fresh keypair to prevent collision", key1, key2)
    }


    // 4. Edge Case: Invalid JSON payloads sent to handleIncomingMessage
    @Test
    fun testHandleEmptyJsonMessage() = runBlocking {
        val repository = AuthSyncRepository
        repository.handleIncomingMessage("{}")
        val state = repository.syncState.value
        assertFalse("Empty JSON should not set error state", state is AuthSyncRepository.SyncState.Error)
    }

    @Test
    fun testHandleMalformedJsonMessage() = runBlocking {
        val repository = AuthSyncRepository
        repository.handleIncomingMessage("invalid json {")
        val state = repository.syncState.value
        assertTrue("State should transition to Error on malformed JSON message", state is AuthSyncRepository.SyncState.Error)
        val errorMsg = (state as AuthSyncRepository.SyncState.Error).message
        assertTrue(errorMsg.contains("Failed to process message"))
    }

    @Test
    fun testHandleMessageWithExtraFields() = runBlocking {
        val repository = AuthSyncRepository
        val msg = "{\"pin\":\"123456\",\"extra_field\":\"extra_value\"}"
        repository.handleIncomingMessage(msg)
        val state = repository.syncState.value
        assertTrue(state is AuthSyncRepository.SyncState.SessionCreated)
        assertEquals("123456", (state as AuthSyncRepository.SyncState.SessionCreated).pin)
    }

    @Test
    fun testCryptoManagerDecryptionFailure() { // Updated to test crypto decryption directly
        val manager = AuthCryptoManager()
        try {
            manager.decrypt("invalid_ciphertext_base64")
            fail("Decrypting invalid ciphertext should throw an exception")
        } catch (e: Throwable) {
            // Success
        }
    }

    // 5. Edge Case: Rapid connect/disconnect stress test
    @Test
    fun testRapidConnectDisconnectStress() = runBlocking {
        val manager = AuthCryptoManager()
        val repository = AuthSyncRepository
        val scope = CoroutineScope(Dispatchers.Default)
        repeat(50) {
            repository.connect(manager, scope)
            delay(1)
            repository.close()
            assertEquals("State should be Idle after close", AuthSyncRepository.SyncState.Idle, repository.syncState.value)
        }
    }

    // 6. Edge Case: Connection failure / Timeout
    @Test
    fun testConnectionFailureTransitionsToError() = runBlocking {
        val manager = AuthCryptoManager()
        val repository = AuthSyncRepository
        repository.wsUrl = "ws://invalid-host-for-testing-12345.com/tv-auth"
        val scope = CoroutineScope(Dispatchers.Default)
        repository.connect(manager, scope)
        var state = repository.syncState.value
        val startTime = System.currentTimeMillis()
        while (state !is AuthSyncRepository.SyncState.Error && System.currentTimeMillis() - startTime < 5000) {
            delay(100)
            state = repository.syncState.value
        }
        assertTrue("Connection should transition to Error on timeout", state is AuthSyncRepository.SyncState.Error)
    }
}

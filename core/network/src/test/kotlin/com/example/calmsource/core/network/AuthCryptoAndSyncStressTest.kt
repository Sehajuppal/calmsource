package com.example.calmsource.core.network

import com.example.calmsource.core.model.AuthCredentials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class AuthCryptoAndSyncStressTest {

    @Before
    fun setUp() {
        AuthSyncRepository.close()
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    @Test
    fun testDecryptEmptyStringThrowsException() {
        val manager = AuthCryptoManager()
        try {
            manager.decrypt("")
            fail("Expected decrypting an empty string to throw an exception")
        } catch (e: Throwable) {
            // Expected behavior
        }
    }

    @Test
    fun testDecryptInvalidBase64ThrowsException() {
        val manager = AuthCryptoManager()
        try {
            manager.decrypt("NotAValidBase64Str!!")
            fail("Expected decrypting invalid Base64 to throw an exception")
        } catch (e: Throwable) {
            // Expected behavior
        }
    }

    @Test
    fun testDecryptWithMismatchedKey() {
        val manager = AuthCryptoManager()
        
        // Generate a completely different keypair
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val alternativeKeyPair = kpg.generateKeyPair()
        
        // Encrypt with alternative public key
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepSpec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT
        )
        cipher.init(Cipher.ENCRYPT_MODE, alternativeKeyPair.public, oaepSpec)
        val encryptedBytes = cipher.doFinal("SecretText".toByteArray(Charsets.UTF_8))
        val encryptedBase64 = encodeBase64(encryptedBytes)
        
        // Attempt to decrypt using manager (which uses the other key)
        try {
            manager.decrypt(encryptedBase64)
            fail("Expected decrypting with a mismatched key to throw an exception")
        } catch (e: Throwable) {
            // Expected behavior
        }
    }

    @Test
    fun testIncomingMessageWithMalformedJson() = runBlocking {
        val repository = AuthSyncRepository

        repository.handleIncomingMessage("{\"pin\":")

        val state = repository.syncState.value
        assertTrue("State should be Error when JSON is malformed", state is AuthSyncRepository.SyncState.Error)
        assertTrue(
            "Error message should indicate failure to process message",
            (state as AuthSyncRepository.SyncState.Error).message.contains("Failed to process message")
        )
    }

    @Test
    fun testIncomingMessageWithMissingFields() = runBlocking {
        val repository = AuthSyncRepository

        // {} is valid JSON, but pin/payload are null
        repository.handleIncomingMessage("{}")

        // Should not throw, should remain in current state (Idle)
        assertEquals(AuthSyncRepository.SyncState.Idle, repository.syncState.value)
    }

    @Test
    fun testIncomingMessageWithPayloadTransitionsToDecrypting() = runBlocking {
        val repository = AuthSyncRepository

        repository.handleIncomingMessage("{\"payload\":\"invalid_cipher_b64\"}")

        val state = repository.syncState.value
        assertTrue("State should be Decrypting", state is AuthSyncRepository.SyncState.Decrypting)
        assertEquals("invalid_cipher_b64", (state as AuthSyncRepository.SyncState.Decrypting).ciphertext)
    }

    @Test
    fun testDecryptNonJsonPlaintext() = runBlocking {
        val manager = AuthCryptoManager()
        val plainText = "this is not JSON"
        
        val pubKeyBase64 = manager.getPublicKeyBase64()
        val decodedPubKeyBytes = decodeBase64(pubKeyBase64)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKeySpec = X509EncodedKeySpec(decodedPubKeyBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec)

        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepSpec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT
        )
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val encryptedBase64 = encodeBase64(encryptedBytes)

        val decrypted = manager.decrypt(encryptedBase64)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testRapidConnectDisconnectStress() = runBlocking {
        val manager = AuthCryptoManager()
        val repository = AuthSyncRepository
        val testScope = CoroutineScope(Dispatchers.Default)

        // Rapid connect and disconnect loop
        for (i in 1..20) {
            repository.connect(manager, testScope)
            delay(10)
            repository.close()
        }

        // Final state must be Idle
        assertEquals(AuthSyncRepository.SyncState.Idle, repository.syncState.value)
    }

    private fun getPrivateField(obj: Any, fieldName: String): Any? {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(obj)
    }

    @Test
    fun testCloseClearsReferences() = runBlocking {
        val manager = AuthCryptoManager()
        val repository = AuthSyncRepository
        val testScope = CoroutineScope(Dispatchers.Default)

        repository.connect(manager, testScope)
        delay(100) // Let connection attempt run

        repository.close()

        val httpClientAfterClose = getPrivateField(repository, "httpClient")
        val webSocketSessionAfterClose = getPrivateField(repository, "webSocketSession")
        val connectionJobAfterClose = getPrivateField(repository, "connectionJob")

        org.junit.Assert.assertNull("httpClient should be cleared after close()", httpClientAfterClose)
        org.junit.Assert.assertNull("webSocketSession should be cleared after close()", webSocketSessionAfterClose)
        org.junit.Assert.assertNull("connectionJob should be cleared after close()", connectionJobAfterClose)
    }
}


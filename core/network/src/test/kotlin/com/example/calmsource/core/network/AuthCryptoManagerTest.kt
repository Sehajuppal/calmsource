package com.example.calmsource.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class AuthCryptoManagerTest {

    @Test
    fun encryptForPublicKey_roundTripsWithDecrypt() {
        val manager = AuthCryptoManager()
        val message = """{"xtreamUrl":"http://example.com","username":"user","password":"secret"}"""
        val encrypted = manager.encryptForPublicKey(manager.getPublicKeyBase64(), message)
        val decrypted = manager.decrypt(encrypted)
        assertEquals(message, decrypted)
    }

    @Test
    fun testKeyPairGenerationAndDecryption() {
        val manager = AuthCryptoManager()
        
        // 1. Verify we can export public key
        val pubKeyBase64 = manager.getPublicKeyBase64()
        assertNotNull(pubKeyBase64)
        
        // 2. Reconstruct the public key from Base64
        val decodedPubKeyBytes = try {
            android.util.Base64.decode(pubKeyBase64, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getDecoder().decode(pubKeyBase64)
        }
        
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKeySpec = X509EncodedKeySpec(decodedPubKeyBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec)
        
        // 3. Encrypt a test message using the reconstructed public key
        val testMessage = "SecureToken12345"
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepSpec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT
        )
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
        val encryptedBytes = cipher.doFinal(testMessage.toByteArray(Charsets.UTF_8))
        
        val encryptedBase64 = try {
            android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getEncoder().encodeToString(encryptedBytes)
        }
        
        // 4. Decrypt using AuthCryptoManager
        val decryptedMessage = manager.decrypt(encryptedBase64)
        
        // 5. Verify correctness
        assertEquals(testMessage, decryptedMessage)
    }

    @Test
    fun testHybridEncryptionAndDecryption() {
        val manager = AuthCryptoManager()
        val pubKeyBase64 = manager.getPublicKeyBase64()
        assertNotNull(pubKeyBase64)

        // 1. Reconstruct the public key from Base64
        val decodedPubKeyBytes = try {
            android.util.Base64.decode(pubKeyBase64, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getDecoder().decode(pubKeyBase64)
        }

        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKeySpec = X509EncodedKeySpec(decodedPubKeyBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec)

        // 2. Perform hybrid encryption (AES-256-GCM + RSA key wrap)
        val testMessage = "A".repeat(500) // Large message > 256 bytes

        // Generate transient AES key
        val aesKeyBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(aesKeyBytes)
        val aesKey = javax.crypto.spec.SecretKeySpec(aesKeyBytes, "AES")

        // Generate random IV
        val ivBytes = ByteArray(12)
        java.security.SecureRandom().nextBytes(ivBytes)

        // Encrypt message with AES-GCM
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, ivBytes)
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
        val encryptedPayloadBytes = aesCipher.doFinal(testMessage.toByteArray(Charsets.UTF_8))
        val encryptedPayloadBase64 = try {
            android.util.Base64.encodeToString(encryptedPayloadBytes, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getEncoder().encodeToString(encryptedPayloadBytes)
        }

        // Encrypt AES key with RSA
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepSpec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT
        )
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
        val encryptedAesKeyBytes = rsaCipher.doFinal(aesKeyBytes)
        val encryptedAesKeyBase64 = try {
            android.util.Base64.encodeToString(encryptedAesKeyBytes, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getEncoder().encodeToString(encryptedAesKeyBytes)
        }

        // Encode IV
        val ivBase64 = try {
            android.util.Base64.encodeToString(ivBytes, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getEncoder().encodeToString(ivBytes)
        }

        // Build HybridCryptoEnvelope JSON string
        val envelope = HybridCryptoEnvelope(
            encryptedAesKey = encryptedAesKeyBase64,
            iv = ivBase64,
            ciphertext = encryptedPayloadBase64
        )
        val envelopeJson = kotlinx.serialization.json.Json.encodeToString(HybridCryptoEnvelope.serializer(), envelope)

        // 3. Decrypt using AuthCryptoManager
        val decryptedMessage = manager.decrypt(envelopeJson)

        // 4. Verify correctness
        assertEquals(testMessage, decryptedMessage)
    }

    /**
     * Verifies that the TV can decrypt payloads from the Hetzner web UI,
     * which uses "encryptedKey" instead of "encryptedAesKey".
     */
    @Test
    fun testWebUiHybridEncryptionFormat() {
        val manager = AuthCryptoManager()
        val pubKeyBase64 = manager.getPublicKeyBase64()
        assertNotNull(pubKeyBase64)

        val decodedPubKeyBytes = try {
            android.util.Base64.decode(pubKeyBase64, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getDecoder().decode(pubKeyBase64)
        }

        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKeySpec = X509EncodedKeySpec(decodedPubKeyBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec)

        val testMessage = """{"xtreamUrl":"http://example.com:8080","username":"user123","password":"pass456","debridToken":null,"installedExtensions":[]}"""

        // Generate AES key & IV
        val aesKeyBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(aesKeyBytes)
        val aesKey = javax.crypto.spec.SecretKeySpec(aesKeyBytes, "AES")
        val ivBytes = ByteArray(12)
        java.security.SecureRandom().nextBytes(ivBytes)

        // Encrypt with AES-GCM
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, ivBytes)
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
        val encryptedPayloadBytes = aesCipher.doFinal(testMessage.toByteArray(Charsets.UTF_8))
        val encryptedPayloadBase64 = java.util.Base64.getEncoder().encodeToString(encryptedPayloadBytes)

        // RSA-wrap the AES key
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
        val encryptedAesKeyBytes = rsaCipher.doFinal(aesKeyBytes)
        val encryptedAesKeyBase64 = java.util.Base64.getEncoder().encodeToString(encryptedAesKeyBytes)

        val ivBase64 = java.util.Base64.getEncoder().encodeToString(ivBytes)

        // Build JSON with WEB UI field name: "encryptedKey" (not "encryptedAesKey")
        val webEnvelopeJson = """{"encryptedKey":"$encryptedAesKeyBase64","iv":"$ivBase64","ciphertext":"$encryptedPayloadBase64"}"""

        // Decrypt — must work with the web's field name
        val decryptedMessage = manager.decrypt(webEnvelopeJson)
        assertEquals(testMessage, decryptedMessage)

        // Also test the base64-wrapped variant (web UI does: btoa(JSON.stringify(envelope)))
        val base64Wrapped = java.util.Base64.getEncoder().encodeToString(webEnvelopeJson.toByteArray(Charsets.UTF_8))
        val decryptedFromBase64 = manager.decrypt(base64Wrapped)
        assertEquals(testMessage, decryptedFromBase64)
    }
}

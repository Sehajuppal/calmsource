package com.example.calmsource.core.network

import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HybridCryptoEnvelope(
    val encryptedAesKey: String,
    val iv: String,
    val ciphertext: String
)

/**
 * Alternate envelope format sent by the Hetzner web UI.
 * Uses "encryptedKey" instead of "encryptedAesKey".
 */
@Serializable
data class WebHybridCryptoEnvelope(
    val encryptedKey: String,
    val iv: String,
    val ciphertext: String
)

class AuthCryptoManager {

    private val keyStoreAlias = "tv_auth_ephemeral"
    @Volatile
    private var fallbackKeyPair: KeyPair? = null

    init {
        try {
            generateKeyPair()
        } catch (e: Throwable) {
            generateFallbackKeyPair()
        }
    }

    private fun generateKeyPair() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(keyStoreAlias)) {
            keyStore.deleteEntry(keyStoreAlias)
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            keyStoreAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .build()

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    private fun generateFallbackKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        fallbackKeyPair = keyPairGenerator.generateKeyPair()
    }

    fun getPublicKeyBase64(): String {
        val localKeyPair = fallbackKeyPair
        val publicKey = if (localKeyPair != null) {
            localKeyPair.public
        } else {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val certificate = keyStore.getCertificate(keyStoreAlias)
                ?: throw IllegalStateException("Public key certificate not found in KeyStore")
            certificate.publicKey
        }
        return encodeBase64(publicKey.encoded)
    }

    fun decrypt(ciphertextBase64: String): String {
        var sanitized = ciphertextBase64.trim()

        // The Hetzner web UI wraps the envelope JSON in an additional Base64 layer:
        //   btoa(JSON.stringify(envelope))
        // Try to unwrap it: if the input is not already JSON but decodes to JSON, use the decoded form.
        if (!sanitized.startsWith("{")) {
            try {
                val decoded = String(decodeBase64(sanitized), Charsets.UTF_8).trim()
                if (decoded.startsWith("{") &&
                    (decoded.contains("encryptedAesKey") || decoded.contains("encryptedKey"))) {
                    sanitized = decoded
                }
            } catch (_: Throwable) {
                // Not a base64-wrapped JSON — continue with original input
            }
        }

        // Detect hybrid envelope from mobile ("encryptedAesKey") OR web UI ("encryptedKey")
        val isHybridEnvelope = sanitized.startsWith("{") &&
            (sanitized.contains("encryptedAesKey") || sanitized.contains("encryptedKey"))
        if (isHybridEnvelope) {
            try {
                val jsonParser = Json { ignoreUnknownKeys = true }
                // Extract the RSA-wrapped AES key from whichever field name is present
                val encryptedAesKeyB64 = if (sanitized.contains("encryptedAesKey")) {
                    jsonParser.decodeFromString<HybridCryptoEnvelope>(sanitized).encryptedAesKey
                } else {
                    jsonParser.decodeFromString<WebHybridCryptoEnvelope>(sanitized).encryptedKey
                }
                // IV and ciphertext field names are the same in both formats
                val envelope = jsonParser.decodeFromString<HybridCryptoEnvelope>(
                    // Normalize web format to mobile format for unified parsing
                    if (sanitized.contains("encryptedAesKey")) sanitized
                    else sanitized.replace("\"encryptedKey\"", "\"encryptedAesKey\"")
                )
                val aesKeyBytesEncrypted = decodeBase64(encryptedAesKeyB64)

                val localKeyPair = fallbackKeyPair
                val privateKey = if (localKeyPair != null) {
                    localKeyPair.private
                } else {
                    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    keyStore.getKey(keyStoreAlias, null) as? PrivateKey
                        ?: throw IllegalStateException("Private key not found in KeyStore")
                }

                // Decrypt the AES key
                val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
                val oaepSpec = OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA1,
                    PSource.PSpecified.DEFAULT
                )
                rsaCipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)
                val aesKeyBytes = rsaCipher.doFinal(aesKeyBytesEncrypted)

                // Decrypt the actual payload with AES-GCM
                val ivBytes = decodeBase64(envelope.iv)
                val ciphertextBytes = decodeBase64(envelope.ciphertext)

                val aesKey = SecretKeySpec(aesKeyBytes, "AES")
                val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(128, ivBytes)
                aesCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)

                val decryptedBytes = aesCipher.doFinal(ciphertextBytes)
                return String(decryptedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                try {
                    android.util.Log.e("AuthCryptoManager", "Hybrid decryption failed: ${e.message}", e)
                } catch (_: Throwable) {}
            }
        }

        // Fallback to raw RSA decryption
        val localKeyPair = fallbackKeyPair
        val privateKey = if (localKeyPair != null) {
            localKeyPair.private
        } else {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.getKey(keyStoreAlias, null) as? PrivateKey
                ?: throw IllegalStateException("Private key not found in KeyStore")
        }

        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepSpec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT
        )
        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)

        val encryptedBytes = decodeBase64(ciphertextBase64)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    @SuppressLint("NewApi")
    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    @SuppressLint("NewApi")
    private fun decodeBase64(str: String): ByteArray {
        val sanitized = str.trim().removeSurrounding("\"").removeSurrounding("'")
        return try {
            java.util.Base64.getDecoder().decode(sanitized)
        } catch (_: Throwable) {
            try {
                java.util.Base64.getUrlDecoder().decode(sanitized)
            } catch (_: Throwable) {
                val decoded = Base64.decode(sanitized, Base64.DEFAULT)
                    ?: Base64.decode(sanitized, Base64.URL_SAFE)
                    ?: throw IllegalArgumentException("Invalid base64 payload")
                decoded
            }
        }
    }
}


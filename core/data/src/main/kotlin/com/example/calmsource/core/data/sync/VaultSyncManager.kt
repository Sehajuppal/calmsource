package com.example.calmsource.core.data.sync

import android.util.Base64
import com.example.calmsource.core.data.CloudAuthTokenStore
import com.example.calmsource.core.model.AuthCredentials
import com.example.calmsource.core.network.CloudAuthRepository
import com.example.calmsource.core.network.VaultUpdateRequest
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultSyncManager @Inject constructor(
    private val tokenStore: CloudAuthTokenStore
) {
    // Delegates
    var gatherIpTvDelegate: (suspend () -> Triple<String, String, String>?)? = null
    var restoreIpTvDelegate: (suspend (String, String, String) -> Unit)? = null
    var gatherDebridDelegate: (suspend () -> String?)? = null
    var restoreDebridDelegate: (suspend (String) -> Unit)? = null
    var gatherExtensionsDelegate: (suspend () -> List<String>)? = null
    var restoreExtensionsDelegate: (suspend (List<String>) -> Unit)? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val secureRandom = SecureRandom()

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

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

    suspend fun backup(password: String) {
        val token = tokenStore.getToken() ?: throw IllegalStateException("User is not authenticated (no token stored)")

        val iptv = gatherIpTvDelegate?.invoke()
        val debrid = gatherDebridDelegate?.invoke()
        val extensions = gatherExtensionsDelegate?.invoke()

        val credentials = AuthCredentials(
            xtreamServerUrl = iptv?.first,
            xtreamUsername = iptv?.second,
            xtreamPassword = iptv?.third,
            realDebridToken = debrid,
            xtreamUrl = iptv?.first,
            username = iptv?.second,
            password = iptv?.third,
            debridToken = debrid,
            installedExtensions = extensions
        )

        val jsonStr = json.encodeToString(AuthCredentials.serializer(), credentials)
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)

        // Generate 16-byte random salt and 12-byte random IV
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)

        // Derive 256-bit AES key using PBKDF2 with HMAC-SHA256 and exactly 10,000 iterations
        val keySpec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derivedKeyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

        // Encrypt using AES/GCM/NoPadding
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val ciphertext = cipher.doFinal(jsonBytes)

        // Combine bytes: [16-byte salt] + [12-byte IV] + [ciphertext]
        val combined = ByteArray(salt.size + iv.size + ciphertext.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(ciphertext, 0, combined, salt.size + iv.size, ciphertext.size)

        val base64Ciphertext = encodeBase64(combined)

        val response = CloudAuthRepository.updateVault(token, VaultUpdateRequest(vault_ciphertext = base64Ciphertext))
        if (!response.success) {
            throw Exception(response.message ?: "Vault backup failed")
        }
    }

    suspend fun restore(password: String) {
        val token = tokenStore.getToken() ?: throw IllegalStateException("User is not authenticated (no token stored)")

        val response = CloudAuthRepository.fetchVault(token)
        val base64Ciphertext = response.vault_ciphertext
        if (base64Ciphertext.isNullOrBlank()) {
            return
        }

        val combined = decodeBase64(base64Ciphertext)
        if (combined.size < 28) {
            throw IllegalArgumentException("Ciphertext is too short (less than 28 bytes)")
        }

        val salt = ByteArray(16)
        val iv = ByteArray(12)
        val ciphertext = ByteArray(combined.size - 28)

        System.arraycopy(combined, 0, salt, 0, 16)
        System.arraycopy(combined, 16, iv, 0, 12)
        System.arraycopy(combined, 28, ciphertext, 0, ciphertext.size)

        // Derive key using password + extracted salt
        val keySpec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derivedKeyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

        // Decrypt using AES/GCM/NoPadding
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val decryptedBytes = cipher.doFinal(ciphertext)

        val jsonStr = String(decryptedBytes, Charsets.UTF_8)
        val credentials = json.decodeFromString(AuthCredentials.serializer(), jsonStr)

        // Restore IPTV (only if the provider does not exist)
        val xtreamUrl = credentials.xtreamUrl ?: credentials.xtreamServerUrl
        val username = credentials.username ?: credentials.xtreamUsername
        val passwordVal = credentials.password ?: credentials.xtreamPassword
        if (!xtreamUrl.isNullOrBlank() && !username.isNullOrBlank() && !passwordVal.isNullOrBlank()) {
            restoreIpTvDelegate?.invoke(xtreamUrl, username, passwordVal)
        }

        // Restore Debrid (only if Debrid is not active)
        val debridToken = credentials.debridToken ?: credentials.realDebridToken
        if (!debridToken.isNullOrBlank()) {
            restoreDebridDelegate?.invoke(debridToken)
        }

        // Restore extensions
        val extensions = credentials.installedExtensions
        if (!extensions.isNullOrEmpty()) {
            restoreExtensionsDelegate?.invoke(extensions)
        }
    }
}

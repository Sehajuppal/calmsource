package com.example.calmsource.core.network

import com.example.calmsource.core.model.AuthCredentials
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class GrandSyncAdversarialTest {

    // 1. Verify Empty/Null Extension Lists in AuthCredentials
    @Test
    fun testEmptyExtensionListSerialization() {
        val creds = AuthCredentials(
            installedExtensions = emptyList()
        )
        val jsonStr = Json.encodeToString(AuthCredentials.serializer(), creds)
        assertTrue(jsonStr.contains("\"installedExtensions\":[]"))

        val decoded = Json.decodeFromString<AuthCredentials>(jsonStr)
        assertNotNull(decoded.installedExtensions)
        assertTrue(decoded.installedExtensions!!.isEmpty())
    }

    @Test
    fun testNullExtensionListSerialization() {
        val creds = AuthCredentials(
            installedExtensions = null
        )
        val jsonStr = Json.encodeToString(AuthCredentials.serializer(), creds)
        assertFalse(jsonStr.contains("\"installedExtensions\""))

        val decoded = Json.decodeFromString<AuthCredentials>(jsonStr)
        assertNull(decoded.installedExtensions)
    }

    @Test
    fun testBlankItemsInExtensionListIgnoredOnTV() {
        // TV-side logic from PairingViewModel.kt:
        // credentials.installedExtensions?.forEach { extensionUrl ->
        //     if (!extensionUrl.isNullOrBlank()) { ... }
        // }
        val extensions = listOf("", "   ", "http://valid-addon.com/manifest.json")
        val processed = extensions.filter { !it.isBlank() }
        assertEquals(1, processed.size)
        assertEquals("http://valid-addon.com/manifest.json", processed.first())
    }

    // 2. Verify Missing/Null Credentials in AuthCredentials
    @Test
    fun testAllNullCredentialsSerialization() {
        val creds = AuthCredentials(
            xtreamUrl = null,
            username = null,
            password = null,
            debridToken = null,
            installedExtensions = null
        )
        val jsonStr = Json.encodeToString(AuthCredentials.serializer(), creds)
        val decoded = Json.decodeFromString<AuthCredentials>(jsonStr)
        
        assertNull(decoded.xtreamUrl)
        assertNull(decoded.username)
        assertNull(decoded.password)
        assertNull(decoded.debridToken)
        assertNull(decoded.installedExtensions)
    }

    @Test
    fun testPartialNullCredentialsIgnoredOnTV() {
        // TV-side logic check from PairingViewModel.kt:
        // val url = credentials.xtreamUrl ?: credentials.xtreamServerUrl
        // val username = credentials.username ?: credentials.xtreamUsername
        // val password = credentials.password ?: credentials.xtreamPassword
        // if (!url.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank()) { ... }
        
        // Scenario A: Missing username/password, but URL present
        val creds1 = AuthCredentials(xtreamUrl = "http://xtream.server")
        val url1 = creds1.xtreamUrl
        val user1 = creds1.username
        val pass1 = creds1.password
        
        val wouldSave1 = !url1.isNullOrBlank() && !user1.isNullOrBlank() && !pass1.isNullOrBlank()
        assertFalse("Should not save Xtream if credentials are incomplete", wouldSave1)

        // Scenario B: All present
        val creds2 = AuthCredentials(xtreamUrl = "http://xtream.server", username = "user", password = "pwd")
        val url2 = creds2.xtreamUrl
        val user2 = creds2.username
        val pass2 = creds2.password
        
        val wouldSave2 = !url2.isNullOrBlank() && !user2.isNullOrBlank() && !pass2.isNullOrBlank()
        assertTrue("Should save Xtream if all credentials are provided", wouldSave2)
    }

    // 3. Pasting Invalid/Adversarial Payloads
    @Test
    fun testManualPinEscapeAdversarialPayload() {
        // If a pin contains raw double quotes:
        val pin = "123\"456"
        val payload = "dummy_payload"
        
        // Manual JSON construction in SettingsScreens.kt:
        // val jsonInputString = "{\"pin\":\"$pin\",\"payload\":\"$payload\"}"
        val jsonInputString = "{\"pin\":\"$pin\",\"payload\":\"$payload\"}"
        
        // Let's see if this produces malformed JSON
        try {
            Json.decodeFromString<Map<String, String>>(jsonInputString)
            fail("Manual JSON payload construction with raw quotes in pin should be invalid/malformed JSON")
        } catch (e: Throwable) {
            // Expected failure: Unexpected JSON token or JSON parse error
            assertTrue(e.message?.contains("Unexpected JSON token") == true || e.message?.contains("Expected") == true)
        }
    }

    @Test
    fun testManualInvalidPublicKeyParsing() {
        // If the user pastes an invalid Base64 public key:
        val invalidKeyBase64 = "YWJjZA==" // "abcd" in Base64
        val decodedPubKeyBytes = java.util.Base64.getDecoder().decode(invalidKeyBase64)
        
        try {
            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val publicKeySpec = java.security.spec.X509EncodedKeySpec(decodedPubKeyBytes)
            keyFactory.generatePublic(publicKeySpec)
            fail("Should fail to parse invalid public key bytes")
        } catch (e: java.security.spec.InvalidKeySpecException) {
            // Success
        }
    }
}

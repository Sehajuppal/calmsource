package com.example.calmsource.tv.ui

import com.example.calmsource.core.model.AuthCredentials
import com.example.calmsource.core.network.AuthSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelAdversarialTest {
    private var viewModel: PairingViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        AuthSyncRepository.close()
        com.example.calmsource.core.database.DatabaseProvider.resetForTesting()
    }

    @After
    fun tearDown() {
        viewModel?.cancelPairing()
        AuthSyncRepository.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testCredentialsFlowWithAllNullCredentialsDoesNotCrash() = runBlocking {
        val viewModel = PairingViewModel(mock())
        this@PairingViewModelAdversarialTest.viewModel = viewModel
        viewModel.startPairing()

        // Wait for cryptoManager to be initialized asynchronously
        var manager = viewModel.cryptoManager
        val startTime = System.currentTimeMillis()
        while (manager == null && System.currentTimeMillis() - startTime < 3000) {
            delay(50)
            manager = viewModel.cryptoManager
        }
        org.junit.Assert.assertNotNull(manager)

        // Prepare credentials with all null values
        val creds = AuthCredentials(
            xtreamServerUrl = null,
            xtreamUsername = null,
            xtreamPassword = null,
            realDebridToken = null,
            xtreamUrl = null,
            username = null,
            password = null,
            debridToken = null,
            installedExtensions = null
        )
        
        AuthSyncRepository.handleIncomingMessage("{\"pin\":\"123456\"}")
        
        val plainText = kotlinx.serialization.json.Json.encodeToString(AuthCredentials.serializer(), creds)
        val pubKeyBase64 = manager!!.getPublicKeyBase64()
        val decodedPubKeyBytes = java.util.Base64.getDecoder().decode(pubKeyBase64)

        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val publicKeySpec = java.security.spec.X509EncodedKeySpec(decodedPubKeyBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec)

        val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepSpec = javax.crypto.spec.OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            java.security.spec.MGF1ParameterSpec.SHA1,
            javax.crypto.spec.PSource.PSpecified.DEFAULT
        )
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val encryptedBase64 = java.util.Base64.getEncoder().encodeToString(encryptedBytes)

        AuthSyncRepository.handleIncomingMessage("{\"payload\":\"$encryptedBase64\"}")
        
        var state = viewModel.state.value
        val waitStartTime = System.currentTimeMillis()
        while (state !is PairingState.Success && state !is PairingState.Error && System.currentTimeMillis() - waitStartTime < 3000) {
            kotlinx.coroutines.delay(50)
            state = viewModel.state.value
        }

        // It should complete successfully even with null fields, as it just skips saving anything
        assertEquals(PairingState.Success, state)
    }

    @Test
    fun testCredentialsFlowWithMalformedExtensionUrlsDoesNotCrash() = runBlocking {
        val viewModel = PairingViewModel(mock())
        this@PairingViewModelAdversarialTest.viewModel = viewModel
        viewModel.startPairing()

        // Wait for cryptoManager to be initialized
        var manager = viewModel.cryptoManager
        val startTime = System.currentTimeMillis()
        while (manager == null && System.currentTimeMillis() - startTime < 3000) {
            delay(50)
            manager = viewModel.cryptoManager
        }
        org.junit.Assert.assertNotNull(manager)

        // Prepare credentials with empty/blank/invalid extension URLs
        val creds = AuthCredentials(
            installedExtensions = listOf("", "   ", "invalid_url_without_schema")
        )
        
        AuthSyncRepository.handleIncomingMessage("{\"pin\":\"123456\"}")
        
        val plainText = kotlinx.serialization.json.Json.encodeToString(AuthCredentials.serializer(), creds)
        val pubKeyBase64 = manager!!.getPublicKeyBase64()
        val decodedPubKeyBytes = java.util.Base64.getDecoder().decode(pubKeyBase64)

        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val publicKeySpec = java.security.spec.X509EncodedKeySpec(decodedPubKeyBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec)

        val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepSpec = javax.crypto.spec.OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            java.security.spec.MGF1ParameterSpec.SHA1,
            javax.crypto.spec.PSource.PSpecified.DEFAULT
        )
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val encryptedBase64 = java.util.Base64.getEncoder().encodeToString(encryptedBytes)

        AuthSyncRepository.handleIncomingMessage("{\"payload\":\"$encryptedBase64\"}")
        
        var state = viewModel.state.value
        val waitStartTime = System.currentTimeMillis()
        while (state !is PairingState.Success && state !is PairingState.Error && System.currentTimeMillis() - waitStartTime < 3000) {
            kotlinx.coroutines.delay(50)
            state = viewModel.state.value
        }

        // Malformed extension URLs should be caught/ignored and pairing should succeed
        assertEquals(PairingState.Success, state)
    }
}

package com.example.calmsource.tv.ui

import com.example.calmsource.core.model.AuthCredentials
import com.example.calmsource.core.network.AuthSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.viewModelScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import com.example.calmsource.core.data.AuthPreferencesManager

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {
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
        viewModel?.let { vm ->
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
        AuthSyncRepository.close()
        runBlocking {
            kotlinx.coroutines.yield()
            kotlinx.coroutines.delay(20)
        }
        Dispatchers.resetMain()
    }

    @Test
    fun testStartPairingTransitionsToConnecting() = runBlocking {
        val viewModel = PairingViewModel(mock())
        this@PairingViewModelTest.viewModel = viewModel
        viewModel.startPairing()

        // After startPairing, state should be Connecting or ShowPin
        val state = viewModel.state.value
        assertTrue(state is PairingState.Connecting || state is PairingState.ShowPin)
    }

    @Test
    fun testPinFlowEmitsUpdatesState() = runBlocking {
        val viewModel = PairingViewModel(mock())
        this@PairingViewModelTest.viewModel = viewModel
        viewModel.startPairing()

        // Wait for cryptoManager to be initialized asynchronously
        var manager = viewModel.cryptoManager
        val startTime = System.currentTimeMillis()
        while (manager == null && System.currentTimeMillis() - startTime < 3000) {
            delay(50)
            manager = viewModel.cryptoManager
        }
        org.junit.Assert.assertNotNull(manager)

        // Handle incoming PIN message
        val pin = "123456"
        AuthSyncRepository.handleIncomingMessage("{\"pin\":\"$pin\"}")

        val state = viewModel.state.value
        assertTrue(state is PairingState.ShowPin)
        assertEquals(pin, (state as PairingState.ShowPin).pin)
    }

    @Test
    fun testCredentialsFlowSavesDebridTokenAndSucceeds() = runBlocking {
        val viewModel = PairingViewModel(mock())
        this@PairingViewModelTest.viewModel = viewModel
        viewModel.startPairing()

        // Wait for cryptoManager to be initialized asynchronously
        var manager = viewModel.cryptoManager
        val startTime = System.currentTimeMillis()
        while (manager == null && System.currentTimeMillis() - startTime < 3000) {
            delay(50)
            manager = viewModel.cryptoManager
        }
        if (manager == null) {
            println("Debug: viewModel state is ${viewModel.state.value}")
        }
        org.junit.Assert.assertNotNull(manager)

        // Prepare credentials with only debrid token (avoids Xtream network calls)
        val creds = AuthCredentials(realDebridToken = "my_secret_token_123")
        
        // Directly emit to credentialsFlow
        AuthSyncRepository.handleIncomingMessage("{\"pin\":\"123456\"}")
        
        val plainText = kotlinx.serialization.json.Json.encodeToString(AuthCredentials.serializer(), creds)
        // Encrypt with manager public key to simulate real flow
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
        assertEquals(plainText, manager!!.decrypt(encryptedBase64))

        // Wait until pairing UI is ready before delivering the encrypted payload.
        val pinWaitStart = System.currentTimeMillis()
        while (viewModel.state.value !is PairingState.ShowPin &&
            viewModel.state.value !is PairingState.Decrypting &&
            System.currentTimeMillis() - pinWaitStart < 3_000
        ) {
            delay(50)
        }
        // Deliver payload message
        AuthSyncRepository.handleIncomingMessage("{\"payload\":\"$encryptedBase64\"}")
        var state = viewModel.state.value
        val waitStartTime = System.currentTimeMillis()
        while (state !is PairingState.Success && state !is PairingState.Error && System.currentTimeMillis() - waitStartTime < 3000) {
            kotlinx.coroutines.delay(50)
            state = viewModel.state.value
        }

        assertEquals(PairingState.Success, state)
    }

    @Test
    fun skipAuthenticationPersistsSkipAndSucceeds() = runBlocking {
        val authPrefs = mock<AuthPreferencesManager>()
        val viewModel = PairingViewModel(authPrefs)
        this@PairingViewModelTest.viewModel = viewModel

        viewModel.skipAuthentication()
        kotlinx.coroutines.delay(50)

        org.mockito.kotlin.verify(authPrefs).setAuthSkipped(true)
        assertEquals(PairingState.Success, viewModel.state.value)
    }

    @Test
    fun testCredentialsFlowWithUpgradedSchemaAndFallbacks() = runBlocking {
        val viewModel = PairingViewModel(mock())
        this@PairingViewModelTest.viewModel = viewModel
        viewModel.startPairing()

        // Wait for cryptoManager to be initialized asynchronously
        var manager = viewModel.cryptoManager
        val startTime = System.currentTimeMillis()
        while (manager == null && System.currentTimeMillis() - startTime < 3000) {
            delay(50)
            manager = viewModel.cryptoManager
        }
        org.junit.Assert.assertNotNull(manager)

        // Prepare credentials with the upgraded schema fields
        val creds = AuthCredentials(
            debridToken = "upgraded_debrid_123",
            installedExtensions = emptyList()
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

        assertEquals(PairingState.Success, state)
    }

    @Test
    fun testPairingStateErrorContainsRedactedErrorMessage() = runBlocking {
        val viewModel = PairingViewModel(mock())
        this@PairingViewModelTest.viewModel = viewModel
        viewModel.startPairing()

        // Wait for cryptoManager to be initialized asynchronously
        var manager = viewModel.cryptoManager
        val startTime = System.currentTimeMillis()
        while (manager == null && System.currentTimeMillis() - startTime < 3000) {
            delay(50)
            manager = viewModel.cryptoManager
        }
        org.junit.Assert.assertNotNull(manager)

        // Handle incoming message with raw credentials in a URL inside the invalid JSON
        AuthSyncRepository.handleIncomingMessage("invalid json structure with http://host:port/api?token=supersecretpass&pin=9999")
        
        var state = viewModel.state.value
        val waitStart = System.currentTimeMillis()
        while (state !is PairingState.Error && System.currentTimeMillis() - waitStart < 3000) {
            delay(50)
            state = viewModel.state.value
        }
        assertTrue(state is PairingState.Error)
        val vmError = (state as PairingState.Error).message
        assertTrue(vmError.contains("token=REDACTED"))
    }
}

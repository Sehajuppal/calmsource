package com.example.calmsource.tv.ui

import com.example.calmsource.core.network.AuthCryptoManager
import com.example.calmsource.core.network.AuthSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import com.example.calmsource.core.data.AuthPreferencesManager
import androidx.lifecycle.viewModelScope

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelStressTest {
    private var viewModel: PairingViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        AuthSyncRepository.close()
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

    // 1. Edge Case: Spam/Rapid clicks on startPairing
    @Test
    fun testStartPairingSpam() = runBlocking {
        val viewModel = PairingViewModel(mock())
        this@PairingViewModelStressTest.viewModel = viewModel
        
        // Spam startPairing 20 times in rapid succession
        repeat(20) {
            viewModel.startPairing()
        }
        
        // State should be Connecting or ShowPin and shouldn't crash
        val state = viewModel.state.value
        assertTrue("ViewModel state should be in Connecting or ShowPin, but was: $state",
            state is PairingState.Connecting || state is PairingState.ShowPin)
    }

    // 2. Edge Case: Connection state transitions to Error updates VM state
    @Test
    fun testConnectionErrorUpdatesVmState() = runBlocking {
        val viewModel = PairingViewModel(mock())
        this@PairingViewModelStressTest.viewModel = viewModel
        viewModel.startPairing()

        // Wait for cryptoManager to be initialized asynchronously
        var manager = viewModel.cryptoManager
        val startTime = System.currentTimeMillis()
        while (manager == null && System.currentTimeMillis() - startTime < 3000) {
            delay(50)
            manager = viewModel.cryptoManager
        }
        assertNotNull(manager)
        
        // Simulate a connection error in AuthSyncRepository
        val expectedErrorMsg = "Remote host closed connection abruptly"
        
        // Let's call handleIncomingMessage with malformed message:
        AuthSyncRepository.handleIncomingMessage("invalid_json")
        
        // Allow time for the error state to propagate through the StateFlow
        delay(200)
        
        val state = viewModel.state.value
        assertTrue("ViewModel state should transition to Error when repository encounters error. State was: $state",
            state is PairingState.Error)
        val msg = (state as PairingState.Error).message
        assertTrue("Error message should contain details about process/parsing failure: $msg",
            msg.contains("Failed to process message") && (msg.contains("Expected StartObject") || msg.contains("Unexpected JSON token") || msg.contains("Use 'serializersModule'")))
    }

    // 3. Edge Case: Decryption failure updates VM state to Error
    @Test
    fun testDecryptionFailureUpdatesVmState() = runBlocking {
        val viewModel = PairingViewModel(mock())
        this@PairingViewModelStressTest.viewModel = viewModel
        viewModel.startPairing()

        // Wait for cryptoManager to be initialized asynchronously
        var manager = viewModel.cryptoManager
        val startTime = System.currentTimeMillis()
        while (manager == null && System.currentTimeMillis() - startTime < 3000) {
            delay(50)
            manager = viewModel.cryptoManager
        }
        assertNotNull(manager)
        
        // Deliver message with invalid/corrupt payload to trigger decryption failure
        val payloadMsg = "{\"payload\":\"invalid_ciphertext_base64\"}"
        AuthSyncRepository.handleIncomingMessage(payloadMsg)
        
        // Allow time for the decryption job to run
        var state = viewModel.state.value
        val waitStartTime = System.currentTimeMillis()
        while (state !is PairingState.Error && System.currentTimeMillis() - waitStartTime < 3000) {
            delay(50)
            state = viewModel.state.value
        }

        assertTrue("ViewModel state should transition to Error on decryption failure. State was: $state",
            state is PairingState.Error)
        val msg = (state as PairingState.Error).message
        assertTrue("Error message should be present: $msg", msg.isNotEmpty())
    }
}

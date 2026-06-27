package com.example.calmsource.core.network

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthSyncRepositoryTest {

    @Before
    fun setUp() {
        AuthSyncRepository.close()
    }

    @Test
    fun testPinFrameParsing() = runBlocking {
        val repository = AuthSyncRepository

        val pinMsg = "{\"pin\":\"987654\"}"
        repository.handleIncomingMessage(pinMsg)

        val state = repository.syncState.value
        assertTrue(state is AuthSyncRepository.SyncState.SessionCreated)
        assertEquals("987654", (state as AuthSyncRepository.SyncState.SessionCreated).pin)
    }

    @Test
    fun testPayloadFrameParsing() = runBlocking {
        val repository = AuthSyncRepository

        val ciphertext = "some_encrypted_base64_payload"
        val payloadMsg = "{\"payload\":\"$ciphertext\"}"
        repository.handleIncomingMessage(payloadMsg)

        val state = repository.syncState.value
        assertTrue(state is AuthSyncRepository.SyncState.Decrypting)
        assertEquals(ciphertext, (state as AuthSyncRepository.SyncState.Decrypting).ciphertext)
    }

    @Test
    fun testParsingErrorHandling() = runBlocking {
        val repository = AuthSyncRepository

        // Malformed JSON message
        val malformedMsg = "{\"pin\":" // invalid JSON
        repository.handleIncomingMessage(malformedMsg)

        val state = repository.syncState.value
        assertTrue(state is AuthSyncRepository.SyncState.Error)
        assertTrue((state as AuthSyncRepository.SyncState.Error).message.contains("Failed to process message"))
    }

    @Test
    fun testConnectionFlowAndTeardown() = runBlocking {
        val manager = AuthCryptoManager()
        val repository = AuthSyncRepository

        // 1. Initial State
        assertEquals(AuthSyncRepository.SyncState.Idle, repository.syncState.value)

        // 2. Trigger connection
        val testScope = CoroutineScope(Dispatchers.Default)
        repository.connect(manager, testScope)

        // 3. Check transition
        delay(100)
        val stateAfterConnect = repository.syncState.value
        assertTrue(stateAfterConnect is AuthSyncRepository.SyncState.Connecting || stateAfterConnect is AuthSyncRepository.SyncState.Error)

        // 4. Teardown
        repository.close()
        assertEquals(AuthSyncRepository.SyncState.Idle, repository.syncState.value)
    }
}

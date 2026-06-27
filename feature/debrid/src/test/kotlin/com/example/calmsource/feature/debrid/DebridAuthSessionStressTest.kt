package com.example.calmsource.feature.debrid

import android.os.SystemClock
import com.example.calmsource.core.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.util.concurrent.ConcurrentHashMap

class DebridAuthSessionStressTest {

    private lateinit var mockedSystemClock: MockedStatic<SystemClock>
    private lateinit var rdHttpClient: HttpClient
    private lateinit var adHttpClient: HttpClient

    private val rdMockEngine = MockEngine { request ->
        when {
            request.url.encodedPath.endsWith("/oauth/v2/device/code") -> {
                respond(
                    content = """
                        {
                            "device_code": "dev_code_123",
                            "user_code": "USRCODE",
                            "interval": 1,
                            "expires_in": 60,
                            "verification_url": "https://real-debrid.com/device"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            request.url.encodedPath.endsWith("/oauth/v2/device/credentials") -> {
                delay(200)
                respond(
                    content = """{"error": "authorization_pending"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            request.url.encodedPath.endsWith("/oauth/v2/device/token") -> {
                delay(200)
                respond(
                    content = """
                        {
                            "access_token": "access_token_123",
                            "refresh_token": "refresh_token_123",
                            "expires_in": 3600,
                            "token_type": "Bearer"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            else -> error("Unhandled request: ${request.url}")
        }
    }

    private val adMockEngine = MockEngine { request ->
        when {
            request.url.encodedPath.endsWith("/pin/get") -> {
                respond(
                    content = """
                        {
                            "status": "success",
                            "data": {
                                "pin": "PIN123",
                                "check": "CHECK123",
                                "expires_in": 60,
                                "userUrl": "https://alldebrid.com/pin"
                            }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            request.url.encodedPath.endsWith("/pin/check") -> {
                delay(200)
                respond(
                    content = """
                        {
                            "status": "success",
                            "data": {
                                "activated": false
                            }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            else -> error("Unhandled request: ${request.url}")
        }
    }

    @Before
    fun setUp() {
        RealDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
            (get(null) as ConcurrentHashMap<*, *>).clear()
        }
        AllDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
            (get(null) as ConcurrentHashMap<*, *>).clear()
        }

        mockedSystemClock = Mockito.mockStatic(SystemClock::class.java)
        mockedSystemClock.`when`<Long> { SystemClock.elapsedRealtime() }.thenAnswer { System.currentTimeMillis() }

        rdHttpClient = HttpClient(rdMockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        adHttpClient = HttpClient(adMockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    @After
    fun tearDown() {
        mockedSystemClock.close()
    }

    @Test
    fun testRealDebridPollAuthTimeoutAndCleanup() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val sessionStatesField = RealDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val sessionStates = sessionStatesField.get(rdClient) as ConcurrentHashMap<String, *>

        val originalSession = rdClient.startAuth()
        assertTrue(sessionStates.containsKey(originalSession.id))

        // Create a session with expiresInSeconds = 0 to trigger immediate timeout
        val session = (originalSession as DebridAuthSession.DeviceCode).copy(
            details = originalSession.details.copy(expiresInSeconds = 0)
        )

        try {
            rdClient.pollAuth(session)
            fail("Expected pollAuth to fail with timeout/expiration")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("expired") == true)
        }

        assertFalse("Session state should be cleaned up after timeout", sessionStates.containsKey(session.id))
    }

    @Test
    fun testRealDebridPollAuthCancellationCleanup() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val sessionStatesField = RealDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val sessionStates = sessionStatesField.get(rdClient) as ConcurrentHashMap<String, *>

        val session = rdClient.startAuth()
        assertTrue(sessionStates.containsKey(session.id))

        val job = launch {
            try {
                rdClient.pollAuth(session)
            } catch (e: CancellationException) {
                // expected
            }
        }

        delay(50)
        job.cancelAndJoin()

        assertFalse("Session state should be cleaned up after cancellation", sessionStates.containsKey(session.id))
    }

    @Test
    fun testRealDebridCompleteAuthCancellationCleanup() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val sessionStatesField = RealDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val sessionStates = sessionStatesField.get(rdClient) as ConcurrentHashMap<String, Any>

        val session = rdClient.startAuth()
        assertTrue(sessionStates.containsKey(session.id))

        val stateClass = Class.forName("com.example.calmsource.feature.debrid.RealDebridHttpClient${'$'}RealDebridSessionState")
        val constructor = stateClass.declaredConstructors.first { it.parameterTypes.size == 5 }.apply { isAccessible = true }

        val updatedState = constructor.newInstance(
            "dev_code_123",       // deviceCode
            System.currentTimeMillis() + 60000L, // expiresAtMs
            "client_id_123",      // clientId
            "client_secret_123",  // clientSecret
            null                  // tokenSet
        )
        sessionStates[session.id] = updatedState

        val job = launch {
            try {
                rdClient.completeAuth(session)
            } catch (e: CancellationException) {
                // expected
            }
        }

        delay(50)
        job.cancelAndJoin()

        assertFalse("Session state should be cleaned up after completeAuth is cancelled", sessionStates.containsKey(session.id))
    }

    @Test
    fun testRealDebridInvalidSessionIds() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val invalidSession = DebridAuthSession.DeviceCode(
            id = "non-existent-session-id",
            providerType = DebridProviderType.REAL_DEBRID,
            details = DebridDeviceCodeSession("userCode", "deviceCode", "url", 1, 5)
        )

        try {
            rdClient.pollAuth(invalidSession)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Session state not found", e.message)
        }

        try {
            rdClient.completeAuth(invalidSession)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Session state not found", e.message)
        }
    }

    @Test
    fun testAllDebridPollAuthTimeoutAndCleanup() = runTest {
        val adClient = AllDebridHttpClient(adHttpClient)
        val sessionStatesField = AllDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val sessionStates = sessionStatesField.get(adClient) as ConcurrentHashMap<String, *>

        val originalSession = adClient.startAuth()
        assertTrue(sessionStates.containsKey(originalSession.id))

        // Create a session with expiresInSeconds = 0 to trigger immediate timeout
        val session = (originalSession as DebridAuthSession.Pin).copy(
            details = originalSession.details.copy(
                expiresInSeconds = 0,
                expiresAtMs = System.currentTimeMillis()
            )
        )

        try {
            adClient.pollAuth(session)
            fail("Expected pollAuth to fail with timeout/expiration")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("expired") == true)
        }

        assertFalse("Session state should be cleaned up after timeout", sessionStates.containsKey(session.id))
    }

    @Test
    fun testAllDebridPollAuthCancellationCleanup() = runTest {
        val adClient = AllDebridHttpClient(adHttpClient)
        val sessionStatesField = AllDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val sessionStates = sessionStatesField.get(adClient) as ConcurrentHashMap<String, *>

        val session = adClient.startAuth()
        assertTrue(sessionStates.containsKey(session.id))

        val job = launch {
            try {
                adClient.pollAuth(session)
            } catch (e: CancellationException) {
                // expected
            }
        }

        delay(50)
        job.cancelAndJoin()

        assertFalse("Session state should be cleaned up after cancellation", sessionStates.containsKey(session.id))
    }

    @Test
    fun testAllDebridCompleteAuthCancellationCleanup() = runTest {
        val adClient = AllDebridHttpClient(adHttpClient)
        val sessionStatesField = AllDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val sessionStates = sessionStatesField.get(adClient) as ConcurrentHashMap<String, Any>

        val session = adClient.startAuth()
        assertTrue(sessionStates.containsKey(session.id))

        val stateClass = Class.forName("com.example.calmsource.feature.debrid.AllDebridHttpClient${'$'}AllDebridSessionState")
        val constructor = stateClass.declaredConstructors.first { it.parameterTypes.size == 4 }.apply { isAccessible = true }

        val updatedState = constructor.newInstance(
            "PIN123",     // pin
            "CHECK123",   // check
            System.currentTimeMillis() + 60000L, // expiresAtMs
            "apikey_123"  // apiKey
        )
        sessionStates[session.id] = updatedState

        val job = launch {
            try {
                adClient.completeAuth(session)
            } catch (e: CancellationException) {
                // expected
            }
        }

        delay(50)
        job.cancelAndJoin()

        assertFalse("Session state should be cleaned up after completeAuth is cancelled", sessionStates.containsKey(session.id))
    }

    @Test
    fun testAllDebridInvalidSessionIds() = runTest {
        val adClient = AllDebridHttpClient(adHttpClient)
        val invalidSession = DebridAuthSession.Pin(
            id = "non-existent-session-id",
            providerType = DebridProviderType.ALL_DEBRID,
            details = DebridPinSession("url", "pin", 5)
        )

        try {
            adClient.pollAuth(invalidSession)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Session state not found", e.message)
        }

        try {
            adClient.completeAuth(invalidSession)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Session state not found", e.message)
        }
    }
}

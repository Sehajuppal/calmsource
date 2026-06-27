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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class DebridHttpClientConcurrencyTest {

    private lateinit var mockedSystemClock: MockedStatic<SystemClock>

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
                delay(100)
                respond(
                    content = """
                        {
                            "client_id": "client_id_123",
                            "client_secret": "client_secret_123"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            request.url.encodedPath.endsWith("/oauth/v2/device/token") -> {
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
                delay(100)
                respond(
                    content = """
                        {
                            "status": "success",
                            "data": {
                                "activated": true,
                                "apikey": "apikey_123"
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

    private lateinit var rdHttpClient: HttpClient
    private lateinit var adHttpClient: HttpClient

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
    fun testRealDebridConcurrentStartAuth() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val sessionStatesField = RealDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val sessionStates = sessionStatesField.get(rdClient) as ConcurrentHashMap<String, *>

        val jobList = List(50) {
            async {
                rdClient.startAuth()
            }
        }
        val sessions = jobList.awaitAll()

        // Check that all session IDs are unique
        val sessionIds = sessions.map { it.id }
        assertEquals(50, sessionIds.distinct().size)

        // Check that the sessionStates map has exactly 50 entries
        assertEquals(50, sessionStates.size)

        // Check that all session IDs exist in the map
        for (session in sessions) {
            assertTrue(sessionStates.containsKey(session.id))
        }
    }

    @Test
    fun testRealDebridPollAuthCancellationDoesNotRemoveUpdatedState() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val sessionStatesField = RealDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val sessionStates = sessionStatesField.get(rdClient) as ConcurrentHashMap<String, Any>

        val session = rdClient.startAuth()
        assertTrue(sessionStates.containsKey(session.id))

        val initialState = sessionStates[session.id]
        assertNotNull(initialState)

        // Start pollAuth in a separate coroutine
        val pollJob = launch {
            try {
                rdClient.pollAuth(session)
            } catch (e: Exception) {
                // Expected to be cancelled
            }
        }

        // Allow pollAuth to start and fetch the initial state
        delay(50)

        // Create updated state via reflection
        val stateClass = Class.forName("com.example.calmsource.feature.debrid.RealDebridHttpClient${'$'}RealDebridSessionState")
        val constructor = stateClass.declaredConstructors.first { it.parameterTypes.size == 5 }.apply { isAccessible = true }

        val updatedState = constructor.newInstance(
            "dev_code_123",       // deviceCode
            System.currentTimeMillis() + 60000L, // expiresAtMs
            "client_id_123",      // clientId
            "client_secret_123",  // clientSecret
            null                  // tokenSet
        )

        // Update the session state in the map (simulating a successful concurrently completed poll/auth)
        sessionStates[session.id] = updatedState

        // Now cancel the pollAuth coroutine
        pollJob.cancelAndJoin()

        // Assert that the session state in the map WAS removed by pollAuth's finally block
        val finalState = sessionStates[session.id]
        assertNull("Session state should be removed from the map", finalState)
    }

    @Test
    fun testAllDebridConcurrentStartAuth() = runTest {
        val adClient = AllDebridHttpClient(adHttpClient)
        val sessionStatesField = AllDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val sessionStates = sessionStatesField.get(adClient) as ConcurrentHashMap<String, *>

        val jobList = List(50) {
            async {
                adClient.startAuth()
            }
        }
        val sessions = jobList.awaitAll()

        // Check that all session IDs are unique
        val sessionIds = sessions.map { it.id }
        assertEquals(50, sessionIds.distinct().size)

        // Check that the sessionStates map has exactly 50 entries
        assertEquals(50, sessionStates.size)

        // Check that all session IDs exist in the map
        for (session in sessions) {
            assertTrue(sessionStates.containsKey(session.id))
        }
    }

    @Test
    fun testAllDebridPollAuthCancellationDoesNotRemoveUpdatedState() = runTest {
        val adClient = AllDebridHttpClient(adHttpClient)
        val sessionStatesField = AllDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val sessionStates = sessionStatesField.get(adClient) as ConcurrentHashMap<String, Any>

        val session = adClient.startAuth()
        assertTrue(sessionStates.containsKey(session.id))

        val initialState = sessionStates[session.id]
        assertNotNull(initialState)

        // Start pollAuth in a separate coroutine
        val pollJob = launch {
            try {
                adClient.pollAuth(session)
            } catch (e: Exception) {
                // Expected to be cancelled
            }
        }

        // Allow pollAuth to start and fetch the initial state
        delay(50)

        // Create updated state via reflection
        val stateClass = Class.forName("com.example.calmsource.feature.debrid.AllDebridHttpClient${'$'}AllDebridSessionState")
        val constructor = stateClass.declaredConstructors.first { it.parameterTypes.size == 4 }.apply { isAccessible = true }

        val updatedState = constructor.newInstance(
            "PIN123",     // pin
            "CHECK123",   // check
            System.currentTimeMillis() + 60000L, // expiresAtMs
            "apikey_123"  // apiKey
        )

        // Update the session state in the map (simulating a successful concurrently completed poll/auth)
        sessionStates[session.id] = updatedState

        // Now cancel the pollAuth coroutine
        pollJob.cancelAndJoin()

        // Assert that the session state in the map WAS removed by pollAuth's finally block
        val finalState = sessionStates[session.id]
        assertNull("Session state should be removed from the map", finalState)
    }
}

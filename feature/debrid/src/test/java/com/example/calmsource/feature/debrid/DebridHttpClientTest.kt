package com.example.calmsource.feature.debrid

import android.os.SystemClock
import com.example.calmsource.core.model.DebridAuthSession
import com.example.calmsource.core.model.DebridDeviceCodeSession
import com.example.calmsource.core.model.DebridPinSession
import com.example.calmsource.core.model.DebridProviderType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class DebridHttpClientTest {

    private lateinit var mockedSystemClock: MockedStatic<SystemClock>
    private var simulatedTimeMs = 0L

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
        mockedSystemClock.`when`<Long> { SystemClock.elapsedRealtime() }.thenAnswer { simulatedTimeMs }
    }

    @After
    fun tearDown() {
        if (::mockedSystemClock.isInitialized) {
            mockedSystemClock.close()
        }
    }

    private fun getSessionStates(client: Any): ConcurrentHashMap<String, *> {
        val field = client::class.java.getDeclaredField("sessionStates")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(client) as ConcurrentHashMap<String, *>
    }

    @Test
    fun testRealDebridSuccess() = runTest {
        simulatedTimeMs = 1000L
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/oauth/v2/device/code" -> {
                    respond(
                        content = """{
                            "device_code": "dev-code-123",
                            "user_code": "RD-USERCODE",
                            "interval": 1,
                            "expires_in": 10,
                            "verification_url": "https://real-debrid.com/device"
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                "/oauth/v2/device/credentials" -> {
                    respond(
                        content = """{
                            "client_id": "client-id-123",
                            "client_secret": "client-secret-123"
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = RealDebridHttpClient(httpClient)
        val session = client.startAuth()
        assertTrue(session is DebridAuthSession.DeviceCode)

        val statesMap = getSessionStates(client)
        assertTrue(statesMap.containsKey(session.id))

        val polled = client.pollAuth(session)
        assertSame(session, polled)

        // On success, the session state MUST REMAIN in the map for completeAuth to use
        assertTrue(statesMap.containsKey(session.id))
    }

    @Test
    fun testRealDebridTimeout() = runTest {
        simulatedTimeMs = 1000L
        var pollCount = 0
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/oauth/v2/device/code" -> {
                    respond(
                        content = """{
                            "device_code": "dev-code-123",
                            "user_code": "RD-USERCODE",
                            "interval": 1,
                            "expires_in": 10,
                            "verification_url": "https://real-debrid.com/device"
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                "/oauth/v2/device/credentials" -> {
                    pollCount++
                    // Advance simulated time to exceed the 10 seconds expiration
                    simulatedTimeMs += 15000L
                    respond(
                        content = """{"error": "authorization_pending"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = RealDebridHttpClient(httpClient)
        val session = client.startAuth()
        assertTrue(session is DebridAuthSession.DeviceCode)

        val statesMap = getSessionStates(client)
        assertTrue(statesMap.containsKey(session.id))

        try {
            client.pollAuth(session)
            fail("Expected IllegalStateException due to timeout")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("expired") == true)
        }

        // On timeout/failure, the session state MUST be cleaned up from the map
        assertFalse(statesMap.containsKey(session.id))
    }

    @Test
    fun testRealDebridCancellation() = runTest {
        simulatedTimeMs = 1000L
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/oauth/v2/device/code" -> {
                    respond(
                        content = """{
                            "device_code": "dev-code-123",
                            "user_code": "RD-USERCODE",
                            "interval": 1,
                            "expires_in": 10,
                            "verification_url": "https://real-debrid.com/device"
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                "/oauth/v2/device/credentials" -> {
                    // Stay pending
                    respond(
                        content = """{"error": "authorization_pending"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = RealDebridHttpClient(httpClient)
        val session = client.startAuth()
        assertTrue(session is DebridAuthSession.DeviceCode)

        val statesMap = getSessionStates(client)
        assertTrue(statesMap.containsKey(session.id))

        // Launch in background coroutine and cancel it
        val job = launch {
            try {
                client.pollAuth(session)
            } catch (e: CancellationException) {
                // expected
                throw e
            }
        }

        // Allow some time for coroutine to run
        delay(100)
        job.cancelAndJoin()

        // On cancellation, the session state MUST be cleaned up from the map
        assertFalse(statesMap.containsKey(session.id))
    }

    @Test
    fun testAllDebridSuccess() = runTest {
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v4/pin/get" -> {
                    respond(
                        content = """{
                            "status": "success",
                            "data": {
                                "pin": "AD-PIN-123",
                                "check": "check-123",
                                "expires_in": 10,
                                "userUrl": "https://alldebrid.com/pin"
                            }
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                "/v4/pin/check" -> {
                    respond(
                        content = """{
                            "status": "success",
                            "data": {
                                "activated": true,
                                "apikey": "ad-api-key-123"
                            }
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = AllDebridHttpClient(httpClient)
        val session = client.startAuth()
        assertTrue(session is DebridAuthSession.Pin)

        val statesMap = getSessionStates(client)
        assertTrue(statesMap.containsKey(session.id))

        val polled = client.pollAuth(session)
        assertSame(session, polled)

        // On success, the session state MUST REMAIN in the map for completeAuth to use
        assertTrue(statesMap.containsKey(session.id))
    }

    @Test
    fun testAllDebridTimeout() = runTest {
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v4/pin/get" -> {
                    // Set expires_in = -1 to trigger immediate timeout
                    respond(
                        content = """{
                            "status": "success",
                            "data": {
                                "pin": "AD-PIN-123",
                                "check": "check-123",
                                "expires_in": -1,
                                "userUrl": "https://alldebrid.com/pin"
                            }
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                "/v4/pin/check" -> {
                    respond(
                        content = """{
                            "status": "success",
                            "data": {
                                "activated": false
                            }
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = AllDebridHttpClient(httpClient)
        val session = client.startAuth()
        assertTrue(session is DebridAuthSession.Pin)

        val statesMap = getSessionStates(client)
        assertTrue(statesMap.containsKey(session.id))

        try {
            client.pollAuth(session)
            fail("Expected IllegalStateException due to timeout")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("expired") == true)
        }

        // On timeout/failure, the session state MUST be cleaned up from the map
        assertFalse(statesMap.containsKey(session.id))
    }

    @Test
    fun testAllDebridCancellation() = runTest {
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v4/pin/get" -> {
                    respond(
                        content = """{
                            "status": "success",
                            "data": {
                                "pin": "AD-PIN-123",
                                "check": "check-123",
                                "expires_in": 10,
                                "userUrl": "https://alldebrid.com/pin"
                            }
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                "/v4/pin/check" -> {
                    respond(
                        content = """{
                            "status": "success",
                            "data": {
                                "activated": false
                            }
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json")
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = AllDebridHttpClient(httpClient)
        val session = client.startAuth()
        assertTrue(session is DebridAuthSession.Pin)

        val statesMap = getSessionStates(client)
        assertTrue(statesMap.containsKey(session.id))

        // Launch in background coroutine and cancel it
        val job = launch {
            try {
                client.pollAuth(session)
            } catch (e: CancellationException) {
                // expected
                throw e
            }
        }

        // Allow some time for coroutine to run
        delay(100)
        job.cancelAndJoin()

        // On cancellation, the session state MUST be cleaned up from the map
        assertFalse(statesMap.containsKey(session.id))
    }
}

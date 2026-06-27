package com.example.calmsource.feature.debrid

import android.os.SystemClock
import com.example.calmsource.core.model.*
import com.example.calmsource.core.network.UrlRedactor
import com.example.calmsource.core.network.NetworkClient
import com.example.calmsource.core.network.StremioAddonClient
import com.example.calmsource.core.network.StremioResult
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
import java.util.UUID

class Milestone2ChallengerTest {

    private lateinit var mockedSystemClock: MockedStatic<SystemClock>
    private lateinit var rdHttpClient: HttpClient
    private lateinit var adHttpClient: HttpClient

    private val rdMockEngine = MockEngine { request ->
        when {
            request.url.encodedPath.endsWith("/oauth/v2/device/code") -> {
                respond(
                    content = """
                        {
                            "device_code": "dev_code_challenge_123",
                            "user_code": "CHALLENGE",
                            "interval": 1,
                            "expires_in": 60,
                            "verification_url": "https://real-debrid.com/device"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            else -> error("Unhandled: ${request.url}")
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
                                "pin": "CHALLENGEPIN",
                                "check": "CHALLENGECHECK",
                                "expires_in": 60,
                                "userUrl": "https://alldebrid.com/pin"
                            }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            else -> error("Unhandled: ${request.url}")
        }
    }

    @Before
    fun setUp() {
        // Clear maps via reflection
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

    // =========================================================================
    // Challenge 1: Debrid HTTP Client Session Memory Safety & Unbounded Growth
    // =========================================================================

    @Test
    fun testSessionStatesPruningAndUnboundedGrowthChallenge() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val adClient = AllDebridHttpClient(adHttpClient)

        val rdStatesField = RealDebridHttpClient::class.java.getDeclaredField("sessionStates").apply { isAccessible = true }
        val adStatesField = AllDebridHttpClient::class.java.getDeclaredField("sessionStates").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val rdStates = rdStatesField.get(null) as ConcurrentHashMap<String, RealDebridHttpClient.RealDebridSessionState>
        @Suppress("UNCHECKED_CAST")
        val adStates = adStatesField.get(null) as ConcurrentHashMap<String, AllDebridHttpClient.AllDebridSessionState>

        // 1. Verify pruning of expired sessions
        val now = System.currentTimeMillis()
        rdStates["sess-expired-1"] = RealDebridHttpClient.RealDebridSessionState("code1", now - 1000L)
        rdStates["sess-expired-2"] = RealDebridHttpClient.RealDebridSessionState("code2", now - 500L)
        rdStates["sess-active-1"] = RealDebridHttpClient.RealDebridSessionState("code3", now + 10000L)

        adStates["sess-expired-1"] = AllDebridHttpClient.AllDebridSessionState("pin1", "check1", now - 1000L)
        adStates["sess-expired-2"] = AllDebridHttpClient.AllDebridSessionState("pin2", "check2", now - 500L)
        adStates["sess-active-1"] = AllDebridHttpClient.AllDebridSessionState("pin3", "check3", now + 10000L)

        // Trigger startAuth to invoke pruning
        rdClient.startAuth()
        adClient.startAuth()

        // Expired sessions must be pruned
        assertFalse(rdStates.containsKey("sess-expired-1"))
        assertFalse(rdStates.containsKey("sess-expired-2"))
        assertFalse(adStates.containsKey("sess-expired-1"))
        assertFalse(adStates.containsKey("sess-expired-2"))

        // Active session must remain
        assertTrue(rdStates.containsKey("sess-active-1"))
        assertTrue(adStates.containsKey("sess-active-1"))

        // 2. Challenge: Unbounded growth under rapid startAuth requests
        // If a client makes many startAuth calls within the 60-second validity window,
        // the sessionStates map will grow linearly because none of them are expired.
        // We simulate starting 20 active sessions rapidly:
        val numSessions = 20
        repeat(numSessions) {
            rdClient.startAuth()
            adClient.startAuth()
        }

        // Map size should have increased by numSessions (plus 1 active session we added manually and 1 new session per client)
        assertEquals(numSessions + 2, rdStates.size)
        assertEquals(numSessions + 2, adStates.size)

        // This demonstrates that while expired sessions are successfully pruned,
        // the session map lacks a rate limiter or maximum capacity limit, meaning
        // it is susceptible to unbounded growth within the validity window (e.g. 60 seconds).
    }

    // =========================================================================
    // Challenge 2: Ktor Logging Redaction for "code" Query Parameter
    // =========================================================================

    @Test
    fun testCodeQueryParameterRedaction() {
        val urlWithCode = "https://api.real-debrid.com/oauth/v2/device/credentials?client_id=X245&code=secret_device_code_123"
        val redacted = UrlRedactor.redactUrl(urlWithCode)
        
        // Assert that the value of the "code" query parameter is fully redacted
        assertEquals("https://api.real-debrid.com/oauth/v2/device/credentials?client_id=X245&code=REDACTED", redacted)
        
        // Assert that we don't leak "secret_device_code_123"
        assertFalse(redacted.contains("secret_device_code_123"))

        // Test path segment with code key
        val pathWithCode = "/code=secret_device_code_abc&agent=calmsource/manifest.json"
        val redactedPath = UrlRedactor.redactPathSecrets(pathWithCode)
        assertEquals("/code=REDACTED&agent=calmsource/manifest.json", redactedPath)
        assertFalse(redactedPath.contains("secret_device_code_abc"))
    }

    // =========================================================================
    // Challenge 3: Stremio Client Connection Closure / use Behavior
    // =========================================================================

    @Test
    fun testConnectionClosureUnderFailureAndCancellation() = runTest {
        // Here we verify that StremioAddonClient.getManifest and getStreams
        // handle responses properly without letting connections or resources leak.
        // Let's verify with invalid URLs and local closed ports:
        
        // 1. Invalid URL schema - fails early before executing HTTP request
        val resultSchema = StremioAddonClient.getManifest("ftp://addon.com/manifest.json", "test-prov")
        assertTrue(resultSchema is StremioResult.Failure)
        assertEquals("Unsafe or invalid URL scheme", (resultSchema as StremioResult.Failure).error.message)

        // 2. Closed port - throws ConnectException. safeGet must handle it, and since no HttpResponse is successfully
        // obtained, there is no response object to leak.
        val resultClosedPort = StremioAddonClient.getManifest("http://127.0.0.1:59999/manifest.json", "test-prov")
        assertTrue(resultClosedPort is StremioResult.Failure)
        assertTrue((resultClosedPort as StremioResult.Failure).error is ExtensionError.NetworkError)

        // 3. Timeout - Mock timed out request or short timeout:
        val resultTimeout = StremioAddonClient.getManifest(
            url = "http://10.255.255.1/manifest.json",
            providerId = "test-prov-timeout",
            timeoutMs = 1L
        )
        assertTrue(resultTimeout is StremioResult.Failure)
        assertTrue((resultTimeout as StremioResult.Failure).error is ExtensionError.Timeout)
    }
}

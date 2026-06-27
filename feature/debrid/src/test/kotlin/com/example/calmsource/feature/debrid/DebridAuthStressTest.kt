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

@OptIn(ExperimentalCoroutinesApi::class)
class DebridAuthStressTest {

    private lateinit var mockedSystemClock: MockedStatic<SystemClock>
    private lateinit var rdHttpClient: HttpClient
    private lateinit var adHttpClient: HttpClient

    // Mock engine simulating slow / fast credentials endpoint
    private val rdMockEngine = MockEngine { request ->
        when {
            request.url.encodedPath.endsWith("/oauth/v2/device/code") -> {
                respond(
                    content = """
                        {
                            "device_code": "dev_code_stress",
                            "user_code": "STRESS",
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
                delay(200) // Delay to allow easy cancellation mid-request
                respond(
                    content = """
                        {
                            "client_id": "client_stress_id",
                            "client_secret": "client_stress_secret"
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
                                "pin": "PINSTRESS",
                                "check": "CHECKSTRESS",
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
                                "activated": true,
                                "apikey": "apikey_stress"
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }

        adHttpClient = HttpClient(adMockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    @After
    fun tearDown() {
        mockedSystemClock.close()
    }

    @Suppress("UNCHECKED_CAST")
    private fun getRdSessionStates(rdClient: RealDebridHttpClient): ConcurrentHashMap<String, *> {
        val field = RealDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        return field.get(rdClient) as ConcurrentHashMap<String, *>
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAdSessionStates(adClient: AllDebridHttpClient): ConcurrentHashMap<String, *> {
        val field = AllDebridHttpClient::class.java.getDeclaredField("sessionStates").apply {
            isAccessible = true
        }
        return field.get(adClient) as ConcurrentHashMap<String, *>
    }

    @Test
    fun testRealDebridCancellationStressCleanup() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val sessionStates = getRdSessionStates(rdClient)

        val jobCount = 50
        val jobs = List(jobCount) { i ->
            launch {
                val session = rdClient.startAuth()
                try {
                    rdClient.pollAuth(session)
                } catch (e: CancellationException) {
                    // expected when cancelled
                }
            }
        }

        // Wait a small amount of virtual time for pollAuth to start
        delay(50)
        
        // Cancel half of the jobs
        for (i in 0 until jobCount step 2) {
            jobs[i].cancel()
        }

        // Wait for all to finish
        jobs.joinAll()

        val activeSessionIds = sessionStates.keys().toList()
        
        // Let's run completeAuth on all remaining active session IDs
        for (sessionId in activeSessionIds) {
            val dummySession = DebridAuthSession.DeviceCode(
                id = sessionId,
                providerType = DebridProviderType.REAL_DEBRID,
                details = DebridDeviceCodeSession("", "", "", 1, 60)
            )
            try {
                rdClient.completeAuth(dummySession)
            } catch (e: Exception) {
                // ignored
            }
        }

        // Finally, the map MUST be completely empty!
        assertEquals("All session states must be cleaned up, map should be empty", 0, sessionStates.size)
    }

    @Test
    fun testAllDebridCancellationStressCleanup() = runTest {
        val adClient = AllDebridHttpClient(adHttpClient)
        val sessionStates = getAdSessionStates(adClient)

        val jobCount = 50
        val jobs = List(jobCount) { i ->
            launch {
                val session = adClient.startAuth()
                try {
                    adClient.pollAuth(session)
                } catch (e: CancellationException) {
                    // expected
                }
            }
        }

        delay(50)
        for (i in 0 until jobCount step 2) {
            jobs[i].cancel()
        }

        jobs.joinAll()

        val activeSessionIds = sessionStates.keys().toList()
        for (sessionId in activeSessionIds) {
            val dummySession = DebridAuthSession.Pin(
                id = sessionId,
                providerType = DebridProviderType.ALL_DEBRID,
                details = DebridPinSession("", "", 60)
            )
            try {
                adClient.completeAuth(dummySession)
            } catch (e: Exception) {
                // ignored
            }
        }

        assertEquals("All session states must be cleaned up, map should be empty", 0, sessionStates.size)
    }

    @Test
    fun testInvalidSessionIdsDoNotCorruptStates() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val adClient = AllDebridHttpClient(adHttpClient)

        val invalidRdSession = DebridAuthSession.DeviceCode(
            id = "invalid-rd-id",
            providerType = DebridProviderType.REAL_DEBRID,
            details = DebridDeviceCodeSession("", "", "", 1, 60)
        )

        val invalidAdSession = DebridAuthSession.Pin(
            id = "invalid-ad-id",
            providerType = DebridProviderType.ALL_DEBRID,
            details = DebridPinSession("", "", 60)
        )

        // Polling invalid session must throw IllegalStateException
        assertThrows(IllegalStateException::class.java) {
            runBlocking { rdClient.pollAuth(invalidRdSession) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { adClient.pollAuth(invalidAdSession) }
        }

        // Completing invalid session must throw IllegalStateException
        assertThrows(IllegalStateException::class.java) {
            runBlocking { rdClient.completeAuth(invalidRdSession) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { adClient.completeAuth(invalidAdSession) }
        }
    }
}

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
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class DebridAuthSessionRobustnessChallengeTest {

    private lateinit var mockedSystemClock: MockedStatic<SystemClock>
    private lateinit var rdHttpClient: HttpClient
    private lateinit var adHttpClient: HttpClient

    // Mock engine for Real-Debrid simulating device code flow
    private val rdMockEngine = MockEngine { request ->
        when {
            request.url.encodedPath.endsWith("/oauth/v2/device/code") -> {
                respond(
                    content = """
                        {
                            "device_code": "dev_code_robustness",
                            "user_code": "ROBUST",
                            "interval": 1,
                            "expires_in": 1000000,
                            "verification_url": "https://real-debrid.com/device"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            request.url.encodedPath.endsWith("/oauth/v2/device/credentials") -> {
                delay(100000)
                respond(
                    content = """{"error": "authorization_pending"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            else -> error("Unhandled request: ${request.url}")
        }
    }

    // Mock engine for AllDebrid simulating pin check
    private val adMockEngine = MockEngine { request ->
        when {
            request.url.encodedPath.endsWith("/pin/get") -> {
                respond(
                    content = """
                        {
                            "status": "success",
                            "data": {
                                "pin": "PINROBUST",
                                "check": "CHECKROBUST",
                                "expires_in": 1000000,
                                "userUrl": "https://alldebrid.com/pin"
                            }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            request.url.encodedPath.endsWith("/pin/check") -> {
                delay(100000)
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
    fun testRealDebridConcurrentCancellationsStress() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val sessionStates = getRdSessionStates(rdClient)

        val jobCount = 45
        val sessions = mutableListOf<DebridAuthSession>()
        val jobs = List(jobCount) {
            val session = rdClient.startAuth()
            sessions.add(session)
            launch {
                try {
                    rdClient.pollAuth(session)
                } catch (e: CancellationException) {
                    // expected
                }
            }
        }

        // Verify all sessions are tracked
        delay(50)
        assertEquals(jobCount, sessionStates.size)

        // Cancel all jobs concurrently
        jobs.forEach { it.cancel() }
        jobs.joinAll()

        // Verify map is completely cleared on cancellation
        assertEquals(0, sessionStates.size)
    }

    @Test
    fun testAllDebridConcurrentCancellationsStress() = runTest {
        val adClient = AllDebridHttpClient(adHttpClient)
        val sessionStates = getAdSessionStates(adClient)

        val jobCount = 45
        val sessions = mutableListOf<DebridAuthSession>()
        val jobs = List(jobCount) {
            val session = adClient.startAuth()
            sessions.add(session)
            launch {
                try {
                    adClient.pollAuth(session)
                } catch (e: CancellationException) {
                    // expected
                }
            }
        }

        delay(50)
        assertEquals(jobCount, sessionStates.size)

        jobs.forEach { it.cancel() }
        jobs.joinAll()

        assertEquals(0, sessionStates.size)
    }

    @Test
    fun testInvalidSessionIdHandlingAndNonCorruption() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val adClient = AllDebridHttpClient(adHttpClient)

        val invalidIds = listOf(
            "",
            "   ",
            "non-existent-session-id-12345",
            "sess-rd-!@#$%",
            "a".repeat(1000)
        )

        invalidIds.forEach { id ->
            val dummyRdSession = DebridAuthSession.DeviceCode(
                id = id,
                providerType = DebridProviderType.REAL_DEBRID,
                details = DebridDeviceCodeSession("USR", "DEV", "url", 1, 5)
            )
            assertThrows(IllegalStateException::class.java) {
                runBlocking { rdClient.pollAuth(dummyRdSession) }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { rdClient.completeAuth(dummyRdSession) }
            }

            val dummyAdSession = DebridAuthSession.Pin(
                id = id,
                providerType = DebridProviderType.ALL_DEBRID,
                details = DebridPinSession("url", "PIN", 5)
            )
            assertThrows(IllegalStateException::class.java) {
                runBlocking { adClient.pollAuth(dummyAdSession) }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { adClient.completeAuth(dummyAdSession) }
            }
        }
    }

    @Test
    fun testSessionStateSizeBoundaryMemoryLeak() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val sessionStates = getRdSessionStates(rdClient)

        // Run many quick start-then-cancel operations sequentially
        repeat(500) {
            val session = rdClient.startAuth()
            assertEquals(1, sessionStates.size)
            val job = launch {
                try {
                    rdClient.pollAuth(session)
                } catch (e: CancellationException) {
                    // expected
                }
            }
            delay(10)
            job.cancelAndJoin()
            assertEquals(0, sessionStates.size)
        }
    }

    @Test
    fun testStartAuthWithoutPollLeaksMemory() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val sessionStates = getRdSessionStates(rdClient)

        assertEquals(0, sessionStates.size)
        val session = rdClient.startAuth()
        assertEquals(1, sessionStates.size)
        // If we never call pollAuth or completeAuth, the session is leaked
        // Let's assert that there is no TTL/eviction mechanism and it remains in memory
        delay(10000)
        assertEquals("Session is leaked in memory", 1, sessionStates.size)
    }

    @Test
    fun testRealDebridSessionStatesMaxLimitMitigation() = runTest {
        val rdClient = RealDebridHttpClient(rdHttpClient)
        val sessionStates = getRdSessionStates(rdClient)

        // Generate 60 auth sessions
        (1..60).map { rdClient.startAuth() }

        // Size should be capped at 50, meaning 10 sessions got evicted
        assertEquals(50, sessionStates.size)
    }

    @Test
    fun testAllDebridSessionStatesMaxLimitMitigation() = runTest {
        val adClient = AllDebridHttpClient(adHttpClient)
        val sessionStates = getAdSessionStates(adClient)

        // Generate 60 auth sessions
        (1..60).map { adClient.startAuth() }

        // Size should be capped at 50, meaning 10 sessions got evicted
        assertEquals(50, sessionStates.size)
    }

    @Test
    fun testRealDebridSessionStatesSizeLimitBreachedByConcurrentPoll() = runTest {
        val credentialsDeferred = CompletableDeferred<Unit>()
        val customMockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/oauth/v2/device/code") -> {
                    respond(
                        content = """
                            {
                                "device_code": "dev_code_concurrency",
                                "user_code": "CONCUR",
                                "interval": 1,
                                "expires_in": 1000000,
                                "verification_url": "https://real-debrid.com/device"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                request.url.encodedPath.endsWith("/oauth/v2/device/credentials") -> {
                    val resp = respond(
                        content = """
                            {
                                "client_id": "concurrency_client_id",
                                "client_secret": "concurrency_client_secret"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                    credentialsDeferred.await()
                    resp
                }
                else -> error("Unhandled request: ${request.url}")
            }
        }
        val customHttpClient = HttpClient(customMockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        
        val rdClient = RealDebridHttpClient(customHttpClient)
        val sessionStates = getRdSessionStates(rdClient)
        
        // 1. Start a session S1 that we will poll
        val sessionS1 = rdClient.startAuth()
        println("DEBUG RD: Started S1 with ID: ${sessionS1.id}. Map keys: ${sessionStates.keys.toList()}")
        
        // 2. Start polling S1. It will fetch state, execute HTTP check,
        // and while it's executing we can yield and start 55 new sessions to evict S1.
        val pollJob = launch {
            try {
                println("DEBUG RD: Starting pollAuth for ${sessionS1.id}")
                rdClient.pollAuth(sessionS1)
                println("DEBUG RD: Completed pollAuth for ${sessionS1.id}")
            } catch (e: Throwable) {
                println("REALDEBRID POLL ERROR: $e")
                e.printStackTrace()
            }
        }
        
        // Yield to allow pollJob to run up to the await() suspension point
        yield()
        println("DEBUG RD: After yield(), pollJob isActive=${pollJob.isActive}. Map keys: ${sessionStates.keys.toList()}")
        
        // Start 55 new sessions to evict S1 from the map
        repeat(55) {
            rdClient.startAuth()
        }
        println("DEBUG RD: After 55 starts, Map keys count: ${sessionStates.size}. Contains S1: ${sessionStates.containsKey(sessionS1.id)}")
        
        // Verify that S1 has been evicted from the map
        assertNull("S1 should be evicted from the map", sessionStates[sessionS1.id])
        
        // Resume the pending credentials request
        println("DEBUG RD: Completing credentialsDeferred")
        credentialsDeferred.complete(Unit)
        
        // Let the polling job finish and write back
        println("DEBUG RD: Joining pollJob")
        pollJob.join()
        println("DEBUG RD: Joined pollJob, isActive=${pollJob.isActive}, isCompleted=${pollJob.isCompleted}, isCancelled=${pollJob.isCancelled}")
        
        // Verify S1 is NOT re-inserted into the map
        val s1StateAfterPoll = sessionStates[sessionS1.id]
        println("DEBUG RD: s1StateAfterPoll: $s1StateAfterPoll. Map keys: ${sessionStates.keys.toList()}")
        assertNull("S1 should NOT be re-inserted into the map", s1StateAfterPoll)
        
        // Verify if map size is capped at 50
        println("Map size is: ${sessionStates.size}")
        assertTrue("Map size should not exceed 50 limit! Actual size: ${sessionStates.size}", sessionStates.size <= 50)
    }

    @Test
    fun testAllDebridSessionStatesSizeLimitBreachedByConcurrentPoll() = runTest {
        val credentialsDeferred = CompletableDeferred<Unit>()
        val customMockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/pin/get") -> {
                    respond(
                        content = """
                            {
                                "status": "success",
                                "data": {
                                    "pin": "PINCONCUR",
                                    "check": "CHECKCONCUR",
                                    "expires_in": 1000000,
                                    "userUrl": "https://alldebrid.com/pin"
                                }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                request.url.encodedPath.endsWith("/pin/check") -> {
                    val resp = respond(
                        content = """
                            {
                                "status": "success",
                                "data": {
                                    "activated": true,
                                    "apikey": "concurrency_api_key"
                                }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                    credentialsDeferred.await()
                    resp
                }
                else -> error("Unhandled request: ${request.url}")
            }
        }
        val customHttpClient = HttpClient(customMockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        
        val adClient = AllDebridHttpClient(customHttpClient)
        val sessionStates = getAdSessionStates(adClient)
        
        // 1. Start a session S1 that we will poll
        val sessionS1 = adClient.startAuth()
        println("DEBUG AD: Started S1 with ID: ${sessionS1.id}. Map keys: ${sessionStates.keys.toList()}")
        
        // 2. Start polling S1. It will fetch state, execute HTTP check,
        // and while it's executing we can yield and start 55 new sessions to evict S1.
        val pollJob = launch {
            try {
                println("DEBUG AD: Starting pollAuth for ${sessionS1.id}")
                adClient.pollAuth(sessionS1)
                println("DEBUG AD: Completed pollAuth for ${sessionS1.id}")
            } catch (e: Throwable) {
                println("ALLDEBRID POLL ERROR: $e")
                e.printStackTrace()
            }
        }
        
        // Yield to allow pollJob to run up to the await() suspension point
        yield()
        println("DEBUG AD: After yield(), pollJob isActive=${pollJob.isActive}. Map keys: ${sessionStates.keys.toList()}")
        
        // Start 55 new sessions to evict S1 from the map
        repeat(55) {
            adClient.startAuth()
        }
        println("DEBUG AD: After 55 starts, Map keys count: ${sessionStates.size}. Contains S1: ${sessionStates.containsKey(sessionS1.id)}")
        
        // Verify that S1 has been evicted from the map
        assertNull("S1 should be evicted from the map", sessionStates[sessionS1.id])
        
        // Resume the pending pin check request
        println("DEBUG AD: Completing credentialsDeferred")
        credentialsDeferred.complete(Unit)
        
        // Let the polling job finish and write back
        println("DEBUG AD: Joining pollJob")
        pollJob.join()
        println("DEBUG AD: Joined pollJob, isActive=${pollJob.isActive}, isCompleted=${pollJob.isCompleted}, isCancelled=${pollJob.isCancelled}")
        
        // Verify S1 is NOT re-inserted into the map
        val s1StateAfterPoll = sessionStates[sessionS1.id]
        println("DEBUG AD: s1StateAfterPoll: $s1StateAfterPoll. Map keys: ${sessionStates.keys.toList()}")
        assertNull("S1 should NOT be re-inserted into the map", s1StateAfterPoll)
        
        // Verify if map size is capped at 50
        println("Map size is: ${sessionStates.size}")
        assertTrue("Map size should not exceed 50 limit! Actual size: ${sessionStates.size}", sessionStates.size <= 50)
    }
}

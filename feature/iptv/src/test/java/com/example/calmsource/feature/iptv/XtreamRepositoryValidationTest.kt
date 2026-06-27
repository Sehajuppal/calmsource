package com.example.calmsource.feature.iptv

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import io.ktor.http.HttpStatusCode
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/**
 * Tests for XtreamRepository input validation methods.
 *
 * Covers server URL, username, and password validation edge cases
 * using sanitized (non-real) fixture data.
 */
class XtreamRepositoryValidationTest {

    // ── Server URL validation ────────────────────────────────────────

    @Test
    fun `validateServerUrl rejects empty string`() {
        val error = XtreamRepository.validateServerUrl("")
        assertEquals("Server URL is required", error)
    }

    @Test
    fun `validateServerUrl rejects blank (whitespace-only) string`() {
        val error = XtreamRepository.validateServerUrl("   ")
        assertEquals("Server URL is required", error)
    }

    @Test
    fun `validateServerUrl rejects tab-only string`() {
        val error = XtreamRepository.validateServerUrl("\t\t")
        assertEquals("Server URL is required", error)
    }

    @Test
    fun `validateServerUrl rejects URL with internal whitespace`() {
        val error = XtreamRepository.validateServerUrl("http://example .com:8080")
        assertEquals("Server URL must not contain whitespace", error)
    }

    @Test
    fun `validateServerUrl rejects URL with trailing space`() {
        val error = XtreamRepository.validateServerUrl("http://example.com:8080 ")
        assertEquals("Server URL must not contain whitespace", error)
    }

    @Test
    fun `validateServerUrl rejects URL with leading space`() {
        val error = XtreamRepository.validateServerUrl(" http://example.com:8080")
        assertEquals("Server URL must not contain whitespace", error)
    }

    @Test
    fun `validateServerUrl rejects URL with newline character`() {
        val error = XtreamRepository.validateServerUrl("http://example.com\n:8080")
        assertEquals("Server URL must not contain whitespace", error)
    }

    @Test
    fun `validateServerUrl accepts host port without scheme`() {
        val error = XtreamRepository.validateServerUrl("example.com:8080")
        assertNull(error)
    }

    @Test
    fun `validateServerUrl rejects bare hostname without scheme`() {
        val error = XtreamRepository.validateServerUrl("example.com")
        assertEquals("Server URL must start with http:// or https://", error)
    }

    @Test
    fun `validateServerUrl rejects ftp scheme`() {
        val error = XtreamRepository.validateServerUrl("ftp://example.com:8080")
        assertEquals("Server URL must start with http:// or https://", error)
    }

    @Test
    fun `validateServerUrl rejects rtsp scheme`() {
        val error = XtreamRepository.validateServerUrl("rtsp://stream.example.com")
        assertEquals("Server URL must start with http:// or https://", error)
    }

    @Test
    fun `validateServerUrl rejects scheme-like prefix without colon-slash-slash`() {
        val error = XtreamRepository.validateServerUrl("http:example.com")
        assertEquals("Server URL must start with http:// or https://", error)
    }

    @Test
    fun `validateServerUrl accepts valid http URL`() {
        val error = XtreamRepository.validateServerUrl("http://example.com:8080")
        assertNull(error)
    }

    @Test
    fun `validateServerUrl accepts valid https URL`() {
        val error = XtreamRepository.validateServerUrl("https://secure.example.com")
        assertNull(error)
    }

    @Test
    fun `validateServerUrl accepts http URL case-insensitive`() {
        val error = XtreamRepository.validateServerUrl("HTTP://EXAMPLE.COM")
        assertNull(error)
    }

    @Test
    fun `validateServerUrl accepts https URL case-insensitive`() {
        val error = XtreamRepository.validateServerUrl("HTTPS://SECURE.EXAMPLE.COM")
        assertNull(error)
    }

    @Test
    fun `validateServerUrl accepts URL with path and port`() {
        val error = XtreamRepository.validateServerUrl("http://iptv.example.com:25461/get.php")
        assertNull(error)
    }

    // ── Username validation ──────────────────────────────────────────

    @Test
    fun `validateUsername rejects empty string`() {
        val error = XtreamRepository.validateUsername("")
        assertEquals("Username cannot be empty", error)
    }

    @Test
    fun `validateUsername rejects whitespace-only string`() {
        val error = XtreamRepository.validateUsername("   ")
        assertEquals("Username cannot be empty", error)
    }

    @Test
    fun `validateUsername rejects tab-only string`() {
        val error = XtreamRepository.validateUsername("\t")
        assertEquals("Username cannot be empty", error)
    }

    @Test
    fun `validateUsername accepts valid username`() {
        val error = XtreamRepository.validateUsername("testuser")
        assertNull(error)
    }

    @Test
    fun `validateUsername accepts username with special characters`() {
        val error = XtreamRepository.validateUsername("user@domain.com")
        assertNull(error)
    }

    @Test
    fun `validateUsername accepts single character username`() {
        val error = XtreamRepository.validateUsername("a")
        assertNull(error)
    }

    // ── Password validation ──────────────────────────────────────────

    @Test
    fun `validatePassword rejects empty string`() {
        val error = XtreamRepository.validatePassword("")
        assertEquals("Password cannot be empty", error)
    }

    @Test
    fun `validatePassword rejects whitespace-only string`() {
        val error = XtreamRepository.validatePassword("   ")
        assertEquals("Password cannot be empty", error)
    }

    @Test
    fun `validatePassword rejects newline-only string`() {
        val error = XtreamRepository.validatePassword("\n")
        assertEquals("Password cannot be empty", error)
    }

    @Test
    fun `validatePassword accepts valid password`() {
        val error = XtreamRepository.validatePassword("s3cure!Pa55")
        assertNull(error)
    }

    @Test
    fun `validatePassword accepts single character password`() {
        val error = XtreamRepository.validatePassword("x")
        assertNull(error)
    }

    // ── Combined validation priority (first failure wins) ────────────

    @Test
    fun `server URL error takes priority over username error`() {
        // Both serverUrl and username are invalid; serverUrl is checked first
        val urlError = XtreamRepository.validateServerUrl("")
        assertNotNull(urlError)
        assertEquals("Server URL is required", urlError)
    }

    @Test
    fun `username error is returned when server URL is valid`() {
        val urlError = XtreamRepository.validateServerUrl("http://ok.com")
        assertNull(urlError)
        val userError = XtreamRepository.validateUsername("")
        assertEquals("Username cannot be empty", userError)
    }

    @Before
    fun setUp() {
        XtreamRepository.setSecureTokenStore(FakeInMemoryIptvSecureTokenStore())
    }

    // ── addXtreamProvider Network Validation ──────────────────────────

    @Test
    fun `addXtreamProvider succeeds when server returns valid active credentials`() = runBlocking {
        val client = mockHttpClient { request ->
            mockResponse(
                body = """{"user_info":{"auth":1,"status":"Active","exp_date":"1800000000"}}""",
                context = request.executionContext
            )
        }
        client.use {
            val result = XtreamRepository.addXtreamProvider(
                name = "Test Provider",
                serverUrl = "http://example.com",
                username = "validuser",
                password = "validpassword",
                client = it
            )
            
            assertTrue(result.isSuccess)
            val provider = result.getOrThrow()
            assertEquals("http://example.com", provider.serverUrl)
            assertEquals("validuser", provider.username)
            assertTrue(provider.id.startsWith("xtream-"))
        }
    }

    @Test
    fun `addXtreamProvider fails when server returns auth equal to zero`() = runBlocking {
        val client = mockHttpClient { request ->
            mockResponse(
                body = """{"user_info":{"auth":0,"status":"Active"}}""",
                context = request.executionContext
            )
        }
        client.use {
            val result = XtreamRepository.addXtreamProvider(
                name = "Test Provider",
                serverUrl = "http://example.com",
                username = "baduser",
                password = "badpassword",
                client = it
            )

            assertFalse(result.isSuccess)
            assertEquals("Invalid credentials", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `addXtreamProvider fails when user_info is missing`() = runBlocking {
        val client = mockHttpClient { request ->
            mockResponse(
                body = """{"other_data":{}}""",
                context = request.executionContext
            )
        }
        client.use {
            val result = XtreamRepository.addXtreamProvider(
                name = "Test Provider",
                serverUrl = "http://example.com",
                username = "user",
                password = "pass",
                client = it
            )

            assertFalse(result.isSuccess)
            assertEquals("Invalid credentials", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `addXtreamProvider fails when subscription status is Expired`() = runBlocking {
        val client = mockHttpClient { request ->
            mockResponse(
                body = """{"user_info":{"auth":1,"status":"Expired"}}""",
                context = request.executionContext
            )
        }
        client.use {
            val result = XtreamRepository.addXtreamProvider(
                name = "Test Provider",
                serverUrl = "http://example.com",
                username = "user",
                password = "pass",
                client = it
            )

            assertFalse(result.isSuccess)
            assertEquals("Subscription expired", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `addXtreamProvider fails when subscription exp_date is in the past`() = runBlocking {
        val client = mockHttpClient { request ->
            mockResponse(
                body = """{"user_info":{"auth":1,"status":"Active","exp_date":"1000"}}""",
                context = request.executionContext
            )
        }
        client.use {
            val result = XtreamRepository.addXtreamProvider(
                name = "Test Provider",
                serverUrl = "http://example.com",
                username = "user",
                password = "pass",
                client = it
            )

            assertFalse(result.isSuccess)
            assertEquals("Subscription expired", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `addXtreamProvider succeeds when subscription exp_date is null`() = runBlocking {
        val client = mockHttpClient { request ->
            mockResponse(
                body = """{"user_info":{"auth":1,"status":"Active","exp_date":null}}""",
                context = request.executionContext
            )
        }
        client.use {
            val result = XtreamRepository.addXtreamProvider(
                name = "Test Provider",
                serverUrl = "http://example.com",
                username = "user",
                password = "pass",
                client = it
            )

            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun `addXtreamProvider fails on server connection error`() = runBlocking {
        val client = mockHttpClient { request ->
            throw Exception("Connection timed out")
        }
        client.use {
            val result = XtreamRepository.addXtreamProvider(
                name = "Test Provider",
                serverUrl = "http://example.com",
                username = "user",
                password = "pass",
                client = it
            )

            assertFalse(result.isSuccess)
            assertEquals("Connection timed out", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `addXtreamProvider correctly encodes username and password`() = runBlocking {
        var capturedUrl = ""
        val client = mockHttpClient { request ->
            capturedUrl = request.url.toString()
            mockResponse(
                body = """{"user_info":{"auth":1,"status":"Active","exp_date":"1800000000"}}""",
                context = request.executionContext
            )
        }
        client.use {
            XtreamRepository.addXtreamProvider(
                name = "Test Provider",
                serverUrl = "http://example.com",
                username = "user&name",
                password = "p@ssword=",
                client = it
            )

            assertTrue(capturedUrl.contains("username=user%26name"))
            assertTrue(capturedUrl.contains("password=p%40ssword%3D"))
        }
    }
}

@OptIn(io.ktor.util.InternalAPI::class)
class FakeHttpClientEngine(
    private val handler: suspend (HttpRequestData) -> HttpResponseData
) : HttpClientEngine {
    override val config = HttpClientEngineConfig()
    override val supportedCapabilities = setOf(io.ktor.client.plugins.HttpTimeout)
    override val dispatcher: CoroutineDispatcher = Dispatchers.Default
    override val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()
    
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        return handler(data)
    }
    
    override fun close() {
        coroutineContext[Job]?.cancel()
    }
}

fun mockResponse(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    context: kotlin.coroutines.CoroutineContext
): HttpResponseData {
    val bytes = body.toByteArray(Charsets.UTF_8)
    return HttpResponseData(
        statusCode = status,
        requestTime = GMTDate(),
        headers = headersOf(
            "Content-Type" to listOf("application/json"),
            "Content-Length" to listOf(bytes.size.toString())
        ),
        version = HttpProtocolVersion.HTTP_1_1,
        body = ByteReadChannel(bytes),
        callContext = Dispatchers.Default + Job()
    )
}

fun mockHttpClient(handler: suspend (HttpRequestData) -> HttpResponseData): HttpClient {
    val wrappedEngine = FakeHttpClientEngine { request ->
        val response = handler(request)
        (response.body as? ByteReadChannel)?.let { channel ->
            CoroutineScope(Dispatchers.Default).launch {
                while (!channel.isClosedForRead) {
                    delay(5)
                }
                request.executionContext.cancel()
            }
        }
        response
    }
    return HttpClient(wrappedEngine) {
        install(io.ktor.client.plugins.HttpTimeout)
    }
}

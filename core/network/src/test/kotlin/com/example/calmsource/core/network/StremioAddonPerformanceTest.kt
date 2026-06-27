package com.example.calmsource.core.network

import com.example.calmsource.core.model.ExtensionError
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class StremioAddonPerformanceTest {

    private lateinit var server: HttpServer
    private var baseUrl: String = ""

    @Before
    fun setup() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/infinite") { exchange ->
            exchange.sendResponseHeaders(200, 0) // Chunked encoding
            val os = exchange.responseBody
            val chunk = ByteArray(1024 * 1024) // 1MB chunks
            try {
                // Send up to 100MB unless the client disconnects
                for (i in 0 until 100) {
                    os.write(chunk)
                    os.flush()
                }
            } catch (e: Exception) {
                // Client probably disconnected early, which is expected!
            } finally {
                exchange.close()
            }
        }
        server.start()
        baseUrl = "http://localhost:${server.address.port}"
    }

    @After
    fun teardown() {
        server.stop(0)
    }

    @Test
    fun testMemoryUsageIsBoundedAndOOMPrevented() = runBlocking {
        // Run a garbage collection before to get a cleaner reading
        System.gc()
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        val result = StremioAddonClient.getManifest(
            url = "$baseUrl/infinite",
            providerId = "oom-test",
            timeoutMs = 5000L
        )

        // Run garbage collection after
        System.gc()
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        // Assert it failed with a NetworkError (exceeded limit / response too large)
        assertTrue(result is StremioResult.Failure)
        val failure = result as StremioResult.Failure
        
        val message = failure.error.message
        assertTrue(
            "Expected 'Response too large' or 'Response exceeds 5MB limit', got: $message",
            message.contains("too large") || message.contains("Response exceeds") || message.contains("Network failed")
        )

        // Memory delta should be reasonably small, definitely not containing the 100MB
        val memoryDeltaMB = (finalMemory - initialMemory) / (1024 * 1024)
        println("Memory delta: $memoryDeltaMB MB")
        assertTrue("Memory usage should not grow by more than 15MB", memoryDeltaMB < 15)
    }

    @Test
    fun testConcurrentLargeRequestsStressTest() = runBlocking {
        val concurrentCount = 10
        val failuresCount = AtomicInteger(0)

        val jobs = (0 until concurrentCount).map {
            async {
                val result = StremioAddonClient.getManifest(
                    url = "$baseUrl/infinite",
                    providerId = "stress-test-$it",
                    timeoutMs = 5000L
                )
                if (result is StremioResult.Failure) {
                    failuresCount.incrementAndGet()
                }
            }
        }

        jobs.awaitAll()

        // Every request should fail gracefully due to response size limit
        assertTrue(failuresCount.get() == concurrentCount)
    }
}

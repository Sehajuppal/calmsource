package com.example.calmsource.core.network

import com.example.calmsource.core.model.ExtensionError
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class StremioAddonChaosTest {

    private lateinit var server: HttpServer
    private var baseUrl: String = ""

    @Before
    fun setup() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        
        // 1. Invalid JSON
        server.createContext("/invalid-json/catalog/movie/top.json") { exchange ->
            val response = "{ this is not valid json ]"
            exchange.sendResponseHeaders(200, response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        // 2. 404 Not Found
        server.createContext("/not-found/catalog/movie/top.json") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.responseBody.close()
        }

        // 3. 500 Internal Error
        server.createContext("/server-error/catalog/movie/top.json") { exchange ->
            exchange.sendResponseHeaders(500, -1)
            exchange.responseBody.close()
        }

        // 4. Empty Response
        server.createContext("/empty/catalog/movie/top.json") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.close()
        }

        // 5. Very slow response
        server.createContext("/slow/catalog/movie/top.json") { exchange ->
            Thread.sleep(6000) // Sleep past the 5s timeout
            val response = """{"metas": []}"""
            exchange.sendResponseHeaders(200, response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        server.start()
        baseUrl = "http://localhost:${server.address.port}"
    }

    @After
    fun teardown() {
        server.stop(0)
    }

    @Test
    fun testCatalogInvalidJson() = runBlocking {
        val result = StremioAddonClient.getCatalog(
            resolvedBaseUrl = "$baseUrl/invalid-json",
            type = "movie",
            catalogId = "top"
        )
        assertTrue("Should return Failure on invalid JSON", result is StremioResult.Failure)
    }

    @Test
    fun testCatalog404() = runBlocking {
        val result = StremioAddonClient.getCatalog(
            resolvedBaseUrl = "$baseUrl/not-found",
            type = "movie",
            catalogId = "top"
        )
        // Check if 404 is handled gracefully or throws an unhandled exception
        assertTrue("Should return Failure on 404", result is StremioResult.Failure)
        val failure = result as StremioResult.Failure
        assertTrue("Should be NetworkError", failure.error is ExtensionError.NetworkError)
    }

    @Test
    fun testCatalog500() = runBlocking {
        val result = StremioAddonClient.getCatalog(
            resolvedBaseUrl = "$baseUrl/server-error",
            type = "movie",
            catalogId = "top"
        )
        assertTrue("Should return Failure on 500", result is StremioResult.Failure)
    }

    @Test
    fun testCatalogEmptyResponse() = runBlocking {
        val result = StremioAddonClient.getCatalog(
            resolvedBaseUrl = "$baseUrl/empty",
            type = "movie",
            catalogId = "top"
        )
        // Empty response might cause JSON parsing error
        assertTrue("Should return Failure on empty response", result is StremioResult.Failure)
    }

    @Test
    fun testCatalogTimeout() = runBlocking {
        val result = StremioAddonClient.getCatalog(
            resolvedBaseUrl = "$baseUrl/slow",
            type = "movie",
            catalogId = "top",
            timeoutMs = 1000L // 1s timeout
        )
        assertTrue("Should return Failure on timeout", result is StremioResult.Failure)
        val failure = result as StremioResult.Failure
        assertTrue("Should be Timeout Error", failure.error is ExtensionError.Timeout)
    }
}

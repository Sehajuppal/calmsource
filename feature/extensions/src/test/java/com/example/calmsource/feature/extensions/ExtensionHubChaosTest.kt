package com.example.calmsource.feature.extensions

import com.example.calmsource.core.model.ExtensionError
import com.example.calmsource.core.model.ExtensionInstallResult
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class ExtensionHubChaosTest {

    private lateinit var server: HttpServer
    private var baseUrl: String = ""

    @Before
    fun setup() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        
        server.createContext("/invalid-json/manifest.json") { exchange ->
            val response = "{ \"id\": \"missing-quotes }"
            exchange.sendResponseHeaders(200, response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        server.createContext("/not-found/manifest.json") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.responseBody.close()
        }

        server.createContext("/slow/manifest.json") { exchange ->
            Thread.sleep(6000)
            val response = "{}"
            exchange.sendResponseHeaders(200, response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        server.createContext("/empty/manifest.json") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.close()
        }

        server.start()
        baseUrl = "http://localhost:${server.address.port}"
    }

    @After
    fun teardown() {
        server.stop(0)
    }

    @Test
    fun testManifestInvalidJson() = runBlocking {
        val result = ExtensionManifestLoader.loadManifest(
            url = "$baseUrl/invalid-json/manifest.json",
            isDebug = true, // allow http
            forceRefresh = true
        )
        assertFalse(result.isSuccess)
    }

    @Test
    fun testManifest404() = runBlocking {
        val result = ExtensionManifestLoader.loadManifest(
            url = "$baseUrl/not-found/manifest.json",
            isDebug = true,
            forceRefresh = true
        )
        assertFalse(result.isSuccess)
    }



    @Test
    fun testManifestEmpty() = runBlocking {
        val result = ExtensionManifestLoader.loadManifest(
            url = "$baseUrl/empty/manifest.json",
            isDebug = true,
            forceRefresh = true
        )
        assertFalse(result.isSuccess)
    }
}

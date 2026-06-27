package com.example.calmsource.feature.search

import com.example.calmsource.core.network.StremioAddonClient
import com.example.calmsource.core.network.StremioResult
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.*

class AIOStreamsNetworkTest {

    @Ignore("Manual live-network smoke test; unit tests must not depend on AIOStreams availability or configured URLs.")
    @Test
    fun testFetchStreams() = runBlocking {
        val baseUrl = "https://aiostreams.elfhosted.com/stremio/YOUR_CONFIG"
        // Try resolving a popular movie, e.g., Spider-Man (tt2250912)
        val res = StremioAddonClient.getStreams(baseUrl, "movie", "tt2250912", "aiostreams", 10000L)
        assertTrue(res is StremioResult.Success)
    }
}

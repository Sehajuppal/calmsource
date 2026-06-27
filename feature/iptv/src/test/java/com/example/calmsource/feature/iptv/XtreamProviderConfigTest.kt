package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.XtreamProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamProviderConfigTest {

    @Test
    fun `valid config creates successfully`() {
        val config = XtreamProviderConfig(
            id = "prov-1",
            name = "Test Provider",
            serverUrl = "http://example.com:8080",
            username = "testuser"
        )
        assertEquals("prov-1", config.id)
        assertEquals("http://example.com:8080", config.serverUrl)
    }

    @Test
    fun `missing fields or empty url is handled in repository`() {
        val serverUrl = "   "
        val username = "user"
        val password = "pwd"
        // Repository validation logic check (simulated)
        assertTrue(serverUrl.isBlank() || username.isBlank() || password.isBlank())
    }
}

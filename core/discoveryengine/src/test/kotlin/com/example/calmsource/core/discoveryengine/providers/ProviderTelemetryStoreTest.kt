package com.example.calmsource.core.discoveryengine.providers

import com.example.calmsource.core.discoveryengine.providers.fakes.FakeProviderTelemetryDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderTelemetryStoreTest {

    @Test
    fun logsAndPrunesProviderFailuresAndUsage() {
        val store = ProviderTelemetryStore(FakeProviderTelemetryDao())

        val failureId = store.logFailure("provider", "metadata", "media", "timeout", "Timed out")
        val usageId = store.logUsage("provider", "metadata", "media", cacheHit = false, durationMs = 42, success = false)

        assertTrue(failureId > 0)
        assertTrue(usageId > 0)
        assertEquals("timeout", store.getFailuresForProvider("provider").single().errorCode)
        assertEquals(1, store.getRecentUsage().size)

        assertEquals(1, store.pruneOldFailures(ttl = -1))
        assertEquals(1, store.pruneOldUsage(ttl = -1))
    }
}

package com.example.calmsource.core.discoveryengine.providers

import com.example.calmsource.core.discoveryengine.providers.fakes.FakeProviderCacheDao
import com.example.calmsource.core.discoveryengine.providers.fakes.FakeProviderRegistryDao
import com.example.calmsource.core.discoveryengine.providers.fakes.FakeProviderTelemetryDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderManagerTest {

    private lateinit var registryStore: ProviderRegistryStore
    private lateinit var cacheStore: ProviderCacheStore
    private lateinit var telemetryStore: ProviderTelemetryStore

    @Before
    fun setUp() {
        registryStore = ProviderRegistryStore(FakeProviderRegistryDao())
        cacheStore = ProviderCacheStore(FakeProviderCacheDao())
        telemetryStore = ProviderTelemetryStore(FakeProviderTelemetryDao())
        ProviderManager.initForTest(registryStore, cacheStore, telemetryStore)
        ProviderManager.setLocalOnlyMode(false)
        ProviderType.entries.forEach { ProviderManager.setEnrichmentAllowed(it, true) }
    }

    @Test
    fun registerAddonProviderHonorsEnabledPriorityAndCapabilities() = runBlocking {
        val provider = Any()

        ProviderManager.registerAddonProvider(
            addonId = "addon-manager-test",
            addonName = "Manager Test",
            endpointUrl = "https://example.test/manifest.json",
            capabilities = setOf(ProviderType.METADATA, ProviderType.RATING),
            isEnabled = true,
            priority = 20,
            createProvider = { provider }
        )

        val rows = ProviderManager.snapshotProviderStatus()
        assertEquals(1, rows.size)
        assertEquals("addon-manager-test", rows[0].providerId)
        assertEquals(20, rows[0].priority)
        assertTrue(ProviderType.METADATA in rows[0].capabilities)
        assertEquals(listOf(provider), ProviderManager.getEnabledProviders(ProviderType.METADATA))
    }

    @Test
    fun disabledLocalOnlyAndCategoryTogglesFilterEnabledProviders() = runBlocking {
        val provider = Any()
        ProviderManager.registerAddonProvider(
            addonId = "addon-filter-test",
            addonName = "Filter Test",
            endpointUrl = "https://example.test/manifest.json",
            capabilities = setOf(ProviderType.METADATA),
            isEnabled = true,
            priority = 10,
            createProvider = { provider }
        )

        ProviderManager.setProviderEnabled("addon-filter-test", false)
        assertTrue(ProviderManager.getEnabledProviders(ProviderType.METADATA).isEmpty())

        ProviderManager.setProviderEnabled("addon-filter-test", true)
        ProviderManager.setLocalOnlyMode(true)
        assertTrue(ProviderManager.getEnabledProviders(ProviderType.METADATA).isEmpty())

        ProviderManager.setLocalOnlyMode(false)
        ProviderManager.setEnrichmentAllowed(ProviderType.METADATA, false)
        assertTrue(ProviderManager.getEnabledProviders(ProviderType.METADATA).isEmpty())
    }

    @Test
    fun repeatedFailuresAutoQuarantineProvider() = runBlocking {
        ProviderManager.registerAddonProvider(
            addonId = "addon-quarantine-test",
            addonName = "Quarantine Test",
            endpointUrl = "https://example.test/manifest.json",
            capabilities = setOf(ProviderType.METADATA),
            isEnabled = true,
            priority = 10,
            createProvider = { Any() }
        )

        repeat(11) {
            ProviderManager.recordResult(
                providerId = "addon-quarantine-test",
                requestType = "metadata",
                result = ProviderResult.Failure("http_5xx")
            )
        }

        val row = ProviderManager.snapshotProviderStatus().single()
        assertEquals(11, row.failureCount)
        assertEquals(0.0, row.reliabilityScore, 0.001)
        assertTrue(ProviderManager.getEnabledProviders(ProviderType.METADATA).isEmpty())
    }

    @Test
    fun circuitBreakerOpensBeforeReliabilityQuarantineAndResetsOnToggle() = runBlocking {
        val provider = Any()
        ProviderManager.registerAddonProvider(
            addonId = "addon-breaker-test",
            addonName = "Breaker Test",
            endpointUrl = "https://example.test/breaker.json",
            capabilities = setOf(ProviderType.METADATA),
            isEnabled = true,
            priority = 10,
            createProvider = { provider }
        )

        repeat(3) {
            ProviderManager.recordResult(
                providerId = "addon-breaker-test",
                requestType = "metadata",
                result = ProviderResult.Failure("http_5xx")
            )
        }

        val row = ProviderManager.snapshotProviderStatus().single()
        assertEquals(3, row.failureCount)
        assertTrue(row.reliabilityScore > 0.0)
        assertTrue(ProviderManager.isProviderCircuitOpen("addon-breaker-test"))
        assertTrue(ProviderManager.getEnabledProviders(ProviderType.METADATA).isEmpty())

        ProviderManager.setProviderEnabled("addon-breaker-test", true)
        assertTrue(!ProviderManager.isProviderCircuitOpen("addon-breaker-test"))
        assertEquals(listOf(provider), ProviderManager.getEnabledProviders(ProviderType.METADATA))
    }
}

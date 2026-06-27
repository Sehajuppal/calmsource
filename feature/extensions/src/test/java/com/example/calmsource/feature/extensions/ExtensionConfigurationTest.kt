package com.example.calmsource.feature.extensions

import com.example.calmsource.core.model.*
import com.example.calmsource.core.network.ExtensionSecrets
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExtensionConfigurationTest {

    private val secretsMap = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        secretsMap.clear()
        ExtensionSecrets.readDelegate = { providerId, key -> secretsMap["$providerId-$key"] }
        ExtensionSecrets.saveDelegate = { providerId, key, value -> secretsMap["$providerId-$key"] = value }
        ExtensionSecrets.deleteDelegate = { providerId, key -> secretsMap.remove("$providerId-$key") }
        ExtensionSecrets.clearDelegate = { providerId -> secretsMap.keys.removeAll { it.startsWith("$providerId-") } }
    }

    @Test
    fun testConfigurableAddonDetection() {
        val manifestWithConfig = ExtensionManifest(
            id = "test-addon",
            name = "Test Addon",
            rawAttributes = mapOf(
                "config" to """[{"key":"api_key","type":"password","title":"API Key","required":true}]"""
            )
        )
        assertTrue(ExtensionRepository.isConfigurable(manifestWithConfig))

        val manifestWithoutConfig = ExtensionManifest(
            id = "test-addon-no-config",
            name = "Test Addon No Config"
        )
        assertFalse(ExtensionRepository.isConfigurable(manifestWithoutConfig))
    }

    @Test
    fun testConfigurationRequiredDetection() {
        val manifestRequired = ExtensionManifest(
            id = "test-addon",
            name = "Test Addon",
            behaviorHints = mapOf("configurationRequired" to "true")
        )
        assertTrue(ExtensionRepository.isConfigurationRequired(manifestRequired))

        val manifestNotRequired = ExtensionManifest(
            id = "test-addon",
            name = "Test Addon"
        )
        assertFalse(ExtensionRepository.isConfigurationRequired(manifestNotRequired))
    }

    @Test
    fun testConfigURLCompilationAndSecretIsolation() {
        val configs = listOf(
            StremioAddonConfig(key = "api_key", type = "password", required = true),
            StremioAddonConfig(key = "user_lang", type = "text", default = "en")
        )
        val configValues = mapOf(
            "api_key" to "my_secret_token_123",
            "user_lang" to "fr"
        )
        
        val compiledUrl = ExtensionRepository.compileConfigUrl(
            baseUrl = "https://myaddon.com/manifest.json",
            configValues = configValues,
            configs = configs,
            providerId = "test-addon"
        )

        // The URL must contain redacted placeholders and non-secrets
        assertEquals("https://myaddon.com/api_key={secret_api_key}&user_lang=fr/manifest.json", compiledUrl)

        // The secret value must be stored in SecureTokenStore delegate (not in URL)
        assertEquals("my_secret_token_123", ExtensionSecrets.readSecret("test-addon", "api_key"))
    }

    @Test
    fun testHealthCalculationNeedsConfiguration() {
        val manifest = ExtensionManifest(
            id = "test-addon",
            name = "Test Addon",
            behaviorHints = mapOf("configurationRequired" to "true"),
            rawAttributes = mapOf(
                "config" to """[{"key":"api_key","type":"password","required":true}]"""
            )
        )

        val providerUnconfigured = ExtensionProvider(
            id = "test-addon",
            name = "Test Addon",
            url = "https://myaddon.com/manifest.json",
            manifest = manifest,
            health = ExtensionHealth.ACTIVE
        )

        val health = ExtensionRepository.checkAddonHealth(providerUnconfigured)
        assertEquals(ExtensionHealth.NEEDS_CONFIGURATION, health)

        // Let's simulate configuration
        ExtensionSecrets.saveSecret("test-addon", "api_key", "valid_key")
        val providerConfigured = providerUnconfigured.copy(
            url = "https://myaddon.com/api_key={secret_api_key}/manifest.json"
        )
        val healthAfterConfig = ExtensionRepository.checkAddonHealth(providerConfigured)
        assertEquals(ExtensionHealth.ACTIVE, healthAfterConfig)
    }

    @Test
    fun `configuration-required manifest without usable resources needs configuration`() {
        val provider = ExtensionProvider(
            id = "configure-on-web",
            name = "Configure On Web",
            url = "https://addon.example/manifest.json",
            manifest = ExtensionManifest(
                id = "configure-on-web",
                name = "Configure On Web",
                behaviorHints = mapOf(
                    "configurable" to "true",
                    "configurationRequired" to "true"
                )
            ),
            health = ExtensionHealth.ACTIVE
        )

        assertEquals(
            ExtensionHealth.NEEDS_CONFIGURATION,
            ExtensionRepository.checkAddonHealth(provider)
        )
    }

    @Test
    fun `stream request ids preserve external ids and rebuild series episode suffixes`() {
        val item = MediaItem(
            id = "local-normalized-id",
            title = "Example Show",
            type = MediaType.SHOW,
            externalIds = mapOf(
                "imdb" to "tt0903747",
                "stremio" to "catalog:example-show"
            )
        )

        val requestIds = ExtensionRepository.buildStreamRequestIds(
            mediaItem = item,
            metas = emptyList(),
            type = "series",
            episodeId = "local-normalized-id:2:3"
        )

        assertTrue(requestIds.contains("tt0903747:2:3"))
        assertTrue(requestIds.contains("catalog:example-show:2:3"))
    }

    @Test
    fun `default discovery provider exposes official popular movie and series catalogs`() {
        val provider = ExtensionRepository.defaultDiscoveryProviderForTest()
        val manifest = requireNotNull(provider.manifest)

        assertEquals("com.linvo.cinemeta", provider.id)
        assertEquals("https://v3-cinemeta.strem.io/manifest.json", provider.url)
        assertTrue(manifest.resources.contains("catalog"))
        assertTrue(manifest.resources.contains("meta"))
        assertTrue(manifest.catalogs.any { it.type == "movie" && it.id == "top" })
        assertTrue(manifest.catalogs.any { it.type == "series" && it.id == "top" })
    }

    @Test
    fun testHealthTransitionFromFailedToActive() {
        // Verify that checkAddonHealth correctly resets FAILED/SLOW health to ACTIVE
        // when configuration is satisfied (regression for BUG-SA4-03)
        val manifest = ExtensionManifest(
            id = "test-addon-reset",
            name = "Test Addon Reset",
            behaviorHints = mapOf("configurationRequired" to "true"),
            rawAttributes = mapOf(
                "config" to """[{"key":"api_key","type":"password","required":true}]"""
            )
        )
        ExtensionSecrets.saveSecret("test-addon-reset", "api_key", "valid_key")

        val providerFailed = ExtensionProvider(
            id = "test-addon-reset",
            name = "Test Addon Reset",
            url = "https://myaddon.com/api_key={secret_api_key}/manifest.json",
            manifest = manifest,
            health = ExtensionHealth.FAILED
        )
        assertEquals(ExtensionHealth.ACTIVE, ExtensionRepository.checkAddonHealth(providerFailed))

        val providerSlow = providerFailed.copy(health = ExtensionHealth.SLOW)
        assertEquals(ExtensionHealth.ACTIVE, ExtensionRepository.checkAddonHealth(providerSlow))
    }

    @Test
    fun testCheckAddonHealth_configurationRequired_emptyConfig_returnsNeedsConfiguration() {
        val manifest = ExtensionManifest(
            id = "test-req",
            name = "Test Req",
            behaviorHints = mapOf("configurationRequired" to "true"),
            resources = listOf("stream"),
            catalogs = emptyList()
        )
        val provider = ExtensionProvider(
            id = "test-req",
            name = "Test Req",
            url = "https://myaddon.com/manifest.json",
            manifest = manifest,
            health = ExtensionHealth.ACTIVE
        )
        val health = ExtensionRepository.checkAddonHealth(provider)
        assertEquals(ExtensionHealth.NEEDS_CONFIGURATION, health)
    }

    @Test
    fun testUpdateHealth_preservesCriticalStates() {
        val existing = ExtensionRepository.getExtensions().first()
        // Disable it first
        ExtensionRepository.toggleExtension(existing.id, false)
        // Now it's disabled. Try updating health to ACTIVE
        ExtensionRepository.updateHealth(existing.id, ExtensionHealth.ACTIVE)
        // Check health is still DISABLED
        val updated = ExtensionRepository.getExtensions().find { it.id == existing.id }
        assertEquals(ExtensionHealth.DISABLED, updated?.health)
        
        // Restore to enabled
        ExtensionRepository.toggleExtension(existing.id, true)
    }
}

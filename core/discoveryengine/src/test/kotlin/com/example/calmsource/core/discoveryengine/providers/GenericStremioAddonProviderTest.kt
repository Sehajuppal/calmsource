package com.example.calmsource.core.discoveryengine.providers

import com.example.calmsource.core.database.entity.ExtensionProviderEntity
import com.example.calmsource.core.discoveryengine.providers.adapters.GenericStremioAddonProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericStremioAddonProviderTest {

    @Test
    fun detectsCapabilitiesFromInstalledStremioManifest() {
        val entity = extensionEntity(
            manifestJson = """
                {
                  "id": "addon.full",
                  "name": "Full Addon",
                  "resources": ["catalog", "meta", "stream", "subtitles"],
                  "types": ["movie", "series"],
                  "catalogs": [{"type": "movie", "id": "top", "name": "Top"}]
                }
            """.trimIndent()
        )

        val capabilities = GenericStremioAddonProvider.capabilitiesFor(entity)

        assertTrue(ProviderType.CATALOG in capabilities)
        assertTrue(ProviderType.METADATA in capabilities)
        assertTrue(ProviderType.RATING in capabilities)
        assertTrue(ProviderType.ARTWORK in capabilities)
        assertTrue(ProviderType.STREAM in capabilities)
        assertTrue(ProviderType.AVAILABILITY in capabilities)
        assertTrue(ProviderType.SUBTITLE in capabilities)
    }

    @Test
    fun unsupportedResourcesAreNotAdvertised() {
        val entity = extensionEntity(
            manifestJson = """
                {
                  "id": "addon.catalog",
                  "name": "Catalog Addon",
                  "resources": ["catalog"],
                  "types": ["movie"],
                  "catalogs": [{"type": "movie", "id": "top", "name": "Top"}]
                }
            """.trimIndent()
        )

        val capabilities = GenericStremioAddonProvider.capabilitiesFor(entity)

        assertTrue(ProviderType.CATALOG in capabilities)
        assertFalse(ProviderType.STREAM in capabilities)
        assertFalse(ProviderType.AVAILABILITY in capabilities)
        assertFalse(ProviderType.SUBTITLE in capabilities)
    }

    private fun extensionEntity(manifestJson: String): ExtensionProviderEntity {
        return ExtensionProviderEntity().apply {
            id = "addon-test"
            name = "Addon Test"
            url = "https://example.test/manifest.json"
            isEnabled = true
            health = "ACTIVE"
            priority = 10
            this.manifestJson = manifestJson
            permissionsCsv = ""
        }
    }
}

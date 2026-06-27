package com.example.calmsource.feature.extensions

import com.example.calmsource.core.model.ExtensionProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class RecommendedStremioAddonsTest {

    @Test
    fun `recommended presets are real stremio add ons`() {
        val presets = RecommendedStremioAddons.presets

        assertEquals(2, presets.size)
        assertNotNull(presets.firstOrNull { it.name == "Torrentio" })
        assertNotNull(presets.firstOrNull { it.name == "AIOStreams" })
        assertFalse(presets.any { it.manifestUrl.contains("legal-demo.com") })
        assertFalse(presets.any { it.manifestUrl.contains("slowaddon.org") })
        assertFalse(presets.any { it.manifestUrl.contains("failedaddon.com") })
    }

    @Test
    fun `installed provider matches by manifest id or url`() {
        val torrentio = RecommendedStremioAddons.presets.first { it.name == "Torrentio" }
        val installedById = ExtensionProvider(
            id = torrentio.manifestId,
            name = "Torrentio",
            url = "https://mirror.example.com/manifest.json"
        )
        val installedByUrl = ExtensionProvider(
            id = "custom-id",
            name = "Torrentio",
            url = torrentio.manifestUrl
        )

        assertEquals(installedById, RecommendedStremioAddons.installedProvider(torrentio, listOf(installedById)))
        assertEquals(installedByUrl, RecommendedStremioAddons.installedProvider(torrentio, listOf(installedByUrl)))
    }
}

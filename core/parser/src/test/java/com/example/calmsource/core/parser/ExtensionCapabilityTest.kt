package com.example.calmsource.core.parser

import com.example.calmsource.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ExtensionCapabilityTest {

    @Test
    fun `Verify catalog-only addon capabilities`() {
        val json = """
            {
                "id": "ext.catalog",
                "name": "Catalog Only",
                "resources": ["catalog"],
                "types": ["movie", "series"]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        val caps = result.manifest!!.detectCapabilities()
        assertTrue(caps.contains(ExtensionCapability.CatalogProvider))
        assertFalse(caps.contains(ExtensionCapability.StreamProvider))
        val types = result.manifest!!.detectContentTypes()
        assertEquals(setOf("movie", "series"), types)
    }

    @Test
    fun `Verify stream-only addon capabilities`() {
        val json = """
            {
                "id": "ext.stream",
                "name": "Stream Only",
                "resources": ["stream"],
                "types": ["movie"]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        val caps = result.manifest!!.detectCapabilities()
        assertTrue(caps.contains(ExtensionCapability.StreamProvider))
        assertFalse(caps.contains(ExtensionCapability.CatalogProvider))
    }

    @Test
    fun `Verify subtitle-only addon capabilities`() {
        val json = """
            {
                "id": "ext.sub",
                "name": "Sub Only",
                "resources": [{"name": "subtitles", "types": ["movie"]}],
                "types": ["movie"]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        val caps = result.manifest!!.detectCapabilities()
        assertTrue(caps.contains(ExtensionCapability.SubtitleProvider))
    }

    @Test
    fun `Verify meta-only addon capabilities`() {
        val json = """
            {
                "id": "ext.meta",
                "name": "Meta Only",
                "resources": ["meta"],
                "types": ["series"]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        val caps = result.manifest!!.detectCapabilities()
        assertTrue(caps.contains(ExtensionCapability.MetadataProvider))
    }

    @Test
    fun `Verify mixed catalog, meta, stream, subtitles addon capabilities`() {
        val json = """
            {
                "id": "ext.mixed",
                "name": "Mixed",
                "resources": ["catalog", "meta", "stream", "subtitles"],
                "types": ["movie", "series", "anime"]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        val caps = result.manifest!!.detectCapabilities()
        assertTrue(caps.contains(ExtensionCapability.CatalogProvider))
        assertTrue(caps.contains(ExtensionCapability.MetadataProvider))
        assertTrue(caps.contains(ExtensionCapability.StreamProvider))
        assertTrue(caps.contains(ExtensionCapability.SubtitleProvider))
        val types = result.manifest!!.detectContentTypes()
        assertEquals(setOf("movie", "series", "anime"), types)
    }

    @Test
    fun `Verify config-required addon capabilities`() {
        val json = """
            {
                "id": "ext.config",
                "name": "Config",
                "resources": ["stream"],
                "types": ["movie"],
                "behaviorHints": { "configurationRequired": true }
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        val caps = result.manifest!!.detectCapabilities()
        assertTrue(caps.contains(ExtensionCapability.ConfigRequired))
    }

    @Test
    fun `Verify unknown-resource addon capabilities`() {
        val json = """
            {
                "id": "ext.unknown",
                "name": "Unknown",
                "resources": ["magic_resource"],
                "types": ["movie"]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        val caps = result.manifest!!.detectCapabilities()
        assertTrue(caps.contains(ExtensionCapability.UnsupportedResource))
    }

    @Test
    fun `Verify AIOStreams-style multi-catalog manifest fixture`() {
        val json = """
            {
                "id": "ext.aiostreams",
                "name": "AIOStreams",
                "catalogs": [
                    {
                        "type": "movie",
                        "id": "trending",
                        "name": "Trending",
                        "extra": [{"name": "search", "isRequired": false}]
                    },
                    {
                        "type": "series",
                        "id": "top",
                        "name": "Top Series"
                    }
                ],
                "types": ["movie", "series"]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        val caps = result.manifest!!.detectCapabilities()
        assertTrue(caps.contains(ExtensionCapability.CatalogProvider))
        assertTrue(caps.contains(ExtensionCapability.SearchCatalogProvider))
    }
}

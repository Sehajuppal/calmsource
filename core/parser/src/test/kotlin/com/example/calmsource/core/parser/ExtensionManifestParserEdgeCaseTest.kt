package com.example.calmsource.core.parser

import com.example.calmsource.core.model.ExtensionError
import org.junit.Assert.*
import org.junit.Test

class ExtensionManifestParserEdgeCaseTest {

    // ═══════════════════════════════════════════════════════════════════
    // Task 2: Valid Manifest Parsing
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `Verify valid manifests parse correctly`() {
        val validJson = """
            {
                "id": "com.example.test",
                "name": "Test Extension",
                "version": "1.0.0",
                "description": "A test extension",
                "resources": ["catalog", "stream"],
                "types": ["movie", "series"]
            }
        """.trimIndent()

        val result = ExtensionManifestParser.parse(validJson)
        assertTrue("Expected parsing to be successful", result.isSuccess)
        assertNotNull(result.manifest)
        assertEquals("com.example.test", result.manifest?.id)
        assertEquals("Test Extension", result.manifest?.name)
        assertEquals("1.0.0", result.manifest?.version)
        assertEquals(listOf("catalog", "stream"), result.manifest?.resources)
    }

    @Test
    fun `Verify invalid JSON doesn't crash`() {
        val invalidJson = "{ this is not valid json "
        val result = ExtensionManifestParser.parse(invalidJson)
        assertFalse("Expected parsing to fail", result.isSuccess)
        assertTrue("Expected error to be ParseError", result.error is ExtensionError.ParseError)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 2: Missing Optional Fields
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `Verify missing optional fields produce warnings, not crashes`() {
        val json = """
            {
                "id": "com.example.test",
                "name": "Test Extension",
                "catalogs": [
                    { "name": "Incomplete Catalog" }
                ]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue("Expected parsing to be successful even with missing optional fields", result.isSuccess)
        assertEquals("com.example.test", result.manifest?.id)

        assertTrue("Expected warnings to contain missing fields warning",
            result.warnings.any { it.contains("Catalog entry skipped") })
    }

    @Test
    fun `Verify missing logo, description, version are null`() {
        val json = """
            {
                "id": "com.example.minimal",
                "name": "Minimal Addon"
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertNull(result.manifest?.logo)
        assertNull(result.manifest?.description)
        assertNull(result.manifest?.version)
        assertTrue(result.manifest?.resources?.isEmpty() == true)
        assertTrue(result.manifest?.types?.isEmpty() == true)
        assertTrue(result.manifest?.catalogs?.isEmpty() == true)
    }

    @Test
    fun `Verify missing background field is handled via rawAttributes`() {
        // background is not a known key, so it goes to rawAttributes
        val json = """
            {
                "id": "com.example.bg",
                "name": "BG Addon",
                "background": "https://img.com/bg.jpg"
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertEquals("https://img.com/bg.jpg", result.manifest?.rawAttributes?.get("background"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 2: Unknown Resource Types
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `Verify unknown resource types are preserved`() {
        val json = """
            {
                "id": "com.example.unknown",
                "name": "Unknown Resources",
                "resources": ["catalog", "stream", "addon_catalog", "future_resource"]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertEquals(4, result.manifest?.resources?.size)
        assertTrue(result.manifest?.resources?.contains("addon_catalog") == true)
        assertTrue(result.manifest?.resources?.contains("future_resource") == true)
    }

    @Test
    fun `Verify resource objects with name field parsed correctly`() {
        val json = """
            {
                "id": "com.example.resobject",
                "name": "Resource Object Addon",
                "resources": [
                    "catalog",
                    {"name": "stream", "types": ["movie"], "idPrefixes": ["tt"]},
                    {"name": "subtitles"}
                ]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertEquals(3, result.manifest?.resources?.size)
        assertEquals(listOf("catalog", "stream", "subtitles"), result.manifest?.resources)
    }

    @Test
    fun `Verify non-array resources field produces warning`() {
        val json = """
            {
                "id": "com.example.badres",
                "name": "Bad Resources",
                "resources": "catalog"
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertTrue(result.manifest?.resources?.isEmpty() == true)
        assertTrue(result.warnings.any { it.contains("Expected 'resources' to be an array") })
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 2: Empty Catalog Arrays
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `Verify empty catalogs array is handled`() {
        val json = """
            {
                "id": "com.example.emptycatalog",
                "name": "No Catalogs",
                "catalogs": []
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertTrue(result.manifest?.catalogs?.isEmpty() == true)
    }

    @Test
    fun `Verify missing catalogs field defaults to empty`() {
        val json = """
            {
                "id": "com.example.nocatalog",
                "name": "No Catalogs Field"
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertTrue(result.manifest?.catalogs?.isEmpty() == true)
    }

    @Test
    fun `Verify catalogs with missing type or id are skipped with warning`() {
        val json = """
            {
                "id": "com.example.partialcatalog",
                "name": "Partial Catalogs",
                "catalogs": [
                    {"type": "movie", "id": "valid_catalog", "name": "Valid"},
                    {"name": "Missing Type and ID"},
                    {"type": "movie", "name": "Missing ID"},
                    {"id": "missing_type", "name": "Missing Type"}
                ]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertEquals(1, result.manifest?.catalogs?.size)
        assertEquals("valid_catalog", result.manifest?.catalogs?.first()?.id)
        // 3 entries should be skipped
        assertTrue(result.warnings.count { it.contains("Catalog entry skipped") } == 3)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 2: Non-primitive types entries
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `Verify non-primitive types entries are skipped with warning`() {
        val json = """
            {
                "id": "com.example.badtypes",
                "name": "Bad Types",
                "types": ["movie", {"complex": "type"}, "series"]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertEquals(2, result.manifest?.types?.size)
        assertEquals(listOf("movie", "series"), result.manifest?.types)
        assertTrue(result.warnings.any { it.contains("Non-primitive type entry skipped") })
    }

    @Test
    fun `Verify non-array types field produces warning`() {
        val json = """
            {
                "id": "com.example.badtypesfield",
                "name": "Bad Types Field",
                "types": "movie"
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertTrue(result.manifest?.types?.isEmpty() == true)
        assertTrue(result.warnings.any { it.contains("Expected 'types' to be an array") })
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 2: BehaviorHints Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `Verify behaviorHints parsed correctly`() {
        val json = """
            {
                "id": "com.example.hints",
                "name": "Hints Addon",
                "behaviorHints": {
                    "adult": "true",
                    "configurable": "true",
                    "configurationRequired": "true"
                }
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertEquals("true", result.manifest?.behaviorHints?.get("adult"))
        assertEquals("true", result.manifest?.behaviorHints?.get("configurable"))
    }

    @Test
    fun `Verify non-object behaviorHints produces warning`() {
        val json = """
            {
                "id": "com.example.badhints",
                "name": "Bad Hints",
                "behaviorHints": "not_an_object"
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertTrue(result.manifest?.behaviorHints?.isEmpty() == true)
        assertTrue(result.warnings.any { it.contains("Expected 'behaviorHints' to be an object") })
    }

    @Test
    fun `Verify null behaviorHints field is handled`() {
        val json = """
            {
                "id": "com.example.nullhints",
                "name": "Null Hints",
                "behaviorHints": null
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertTrue(result.manifest?.behaviorHints?.isEmpty() == true)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Required Field Validation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `Verify missing required identity fields are handled clearly`() {
        val noIdJson = """
            {
                "name": "Test Extension"
            }
        """.trimIndent()
        val result1 = ExtensionManifestParser.parse(noIdJson)
        assertFalse("Expected parsing to fail due to missing id", result1.isSuccess)
        assertTrue("Expected InvalidManifest error", result1.error is ExtensionError.InvalidManifest)
        assertEquals("Missing required 'id' field", result1.error?.message)

        val noNameJson = """
            {
                "id": "com.example.test"
            }
        """.trimIndent()
        val result2 = ExtensionManifestParser.parse(noNameJson)
        assertFalse("Expected parsing to fail due to missing name", result2.isSuccess)
        assertTrue("Expected InvalidManifest error", result2.error is ExtensionError.InvalidManifest)
        assertEquals("Missing required 'name' field", result2.error?.message)
    }

    @Test
    fun `Verify empty string id is rejected`() {
        val json = """
            {
                "id": "",
                "name": "Test Extension"
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertFalse(result.isSuccess)
        assertEquals("Missing required 'id' field", result.error?.message)
    }

    @Test
    fun `Verify empty manifest content is handled`() {
        val result = ExtensionManifestParser.parse("")
        assertFalse(result.isSuccess)
        assertTrue(result.error is ExtensionError.InvalidManifest)
        assertTrue(result.error?.message?.contains("empty") == true)
    }

    @Test
    fun `Verify blank manifest content is handled`() {
        val result = ExtensionManifestParser.parse("   ")
        assertFalse(result.isSuccess)
        assertTrue(result.error is ExtensionError.InvalidManifest)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Raw Attributes (Unknown Fields)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `Verify unknown fields are preserved safely`() {
        val json = """
            {
                "id": "com.example.test",
                "name": "Test Extension",
                "custom_field": "custom_value",
                "nested_field": { "key": "value" }
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        assertNotNull(result.manifest)
        assertEquals("custom_value", result.manifest?.rawAttributes?.get("custom_field"))
        assertEquals("{\"key\":\"value\"}", result.manifest?.rawAttributes?.get("nested_field")?.replace(" ", ""))
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 2: Real-World AIOStreams-Style Manifest
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `Verify real-world AIOStreams-style manifest parses correctly`() {
        val json = """
            {
                "id": "community.aiostreams",
                "version": "1.0.0",
                "name": "AIOStreams",
                "description": "All-in-one Stremio addon combining multiple stream sources.",
                "logo": "https://aiostreams.example.com/logo.png",
                "resources": [
                    "stream",
                    {"name": "catalog", "types": ["movie", "series"]},
                    "subtitles"
                ],
                "types": ["movie", "series"],
                "catalogs": [
                    {"type": "movie", "id": "aio_movies", "name": "AIO Movies"},
                    {"type": "series", "id": "aio_series", "name": "AIO Series"}
                ],
                "behaviorHints": {
                    "configurable": "true",
                    "configurationRequired": "true"
                },
                "contactEmail": "support@aiostreams.example.com",
                "idPrefixes": ["tt"]
            }
        """.trimIndent()

        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        val manifest = result.manifest!!
        assertEquals("community.aiostreams", manifest.id)
        assertEquals("AIOStreams", manifest.name)
        assertEquals("https://aiostreams.example.com/logo.png", manifest.logo)
        assertEquals(3, manifest.resources.size)
        assertEquals(listOf("stream", "catalog", "subtitles"), manifest.resources)
        assertEquals(2, manifest.catalogs.size)
        assertEquals("true", manifest.behaviorHints["configurable"])
        assertEquals("support@aiostreams.example.com", manifest.rawAttributes["contactEmail"])
    }

    @Test
    fun `Verify JSON array at root level is rejected`() {
        val json = """[{"id": "test", "name": "test"}]"""
        val result = ExtensionManifestParser.parse(json)
        assertFalse(result.isSuccess)
        assertTrue(result.error is ExtensionError.InvalidManifest)
        assertTrue(result.error?.message?.contains("JSON object") == true)
    }

    @Test
    fun `Verify catalog extra configurations are parsed correctly`() {
        val json = """
            {
                "id": "com.example.extra",
                "name": "Catalog Extra Addon",
                "catalogs": [
                    {
                        "type": "movie",
                        "id": "extra_movie",
                        "name": "Extra Movies",
                        "extra": [
                            {
                                "name": "search",
                                "isRequired": true
                            },
                            {
                                "name": "genre",
                                "isRequired": false,
                                "options": ["Action", "Comedy"]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        assertTrue(result.isSuccess)
        val catalog = result.manifest?.catalogs?.firstOrNull()
        assertNotNull(catalog)
        assertEquals("extra_movie", catalog?.id)
        val extra = catalog?.extra
        assertNotNull(extra)
        assertEquals(2, extra?.size)
        assertEquals("search", extra?.get(0)?.name)
        assertTrue(extra?.get(0)?.isRequired == true)
        assertEquals("genre", extra?.get(1)?.name)
        assertFalse(extra?.get(1)?.isRequired == true)
        assertEquals(listOf("Action", "Comedy"), extra?.get(1)?.options)
    }
}


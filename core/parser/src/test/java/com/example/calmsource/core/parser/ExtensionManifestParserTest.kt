package com.example.calmsource.core.parser

import com.example.calmsource.core.model.ExtensionError
import org.junit.Assert.*
import org.junit.Test

class ExtensionManifestParserTest {

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
        
        // Wait, what does the parser do for invalid catalog? 
        // It adds a warning: "Catalog entry skipped due to missing fields (type/id)"
        assertTrue("Expected warnings to contain missing fields warning", 
            result.warnings.any { it.contains("Catalog entry skipped") })
    }

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
}

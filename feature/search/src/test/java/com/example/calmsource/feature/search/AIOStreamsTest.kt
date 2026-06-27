package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import com.example.calmsource.core.parser.ExtensionManifestParser
import org.junit.Assert.*
import org.junit.Test

class AIOStreamsTest {

    @Test
    fun testParseAIOStreamsManifest() {
        val json = """
            {
              "id": "com.aiostreams.viren070.d2435ebb-7bd",
              "name": "AIOStreams",
              "description": "AIOStreams Addon",
              "version": "1.0.0",
              "resources": ["stream"],
              "types": ["movie", "series"]
            }
        """.trimIndent()
        val result = ExtensionManifestParser.parse(json)
        println(result.warnings)
        if (!result.isSuccess) {
            println(result.error?.message)
        }
        assertTrue(result.isSuccess)
        assertNotNull(result.manifest)
        println("RESOURCES: " + result.manifest?.resources)
    }
}

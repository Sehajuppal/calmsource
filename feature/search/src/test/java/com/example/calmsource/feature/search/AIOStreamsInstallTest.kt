package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import com.example.calmsource.core.parser.ExtensionManifestParser
import com.example.calmsource.feature.extensions.ExtensionRepository
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class AIOStreamsInstallTest {

    @Test
    fun testInstallAIOStreams() = runBlocking {
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
        assertTrue(result.isSuccess)
        val manifest = result.manifest!!
        
        val url = "https://aiostreams.elfhosted.com/stremio/d2435ebb/manifest.json"
        val installRes = ExtensionRepository.confirmInstall(manifest, url)
        assertTrue(installRes.isSuccess)
        
        val installed = ExtensionRepository.getExtensions().find { it.id == manifest.id }
        assertNotNull("Extension should be installed", installed)
        assertEquals("com.aiostreams.viren070.d2435ebb-7bd", installed!!.id)
        assertNotNull("Manifest should be mapped", installed.manifest)
    }
}

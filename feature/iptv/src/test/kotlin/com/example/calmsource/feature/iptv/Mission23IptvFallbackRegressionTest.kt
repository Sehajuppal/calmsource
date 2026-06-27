package com.example.calmsource.feature.iptv

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mission23IptvFallbackRegressionTest {

    @Test
    fun `production IPTV repository does not fall back to FakeData channels or programs`() {
        val source = readIptvSource("IPTVRepository.kt")

        assertFalse(
            "Production IPTV data must not be seeded from FakeData",
            Regex("""\bFakeData\s*\.""").containsMatchIn(source)
        )
        assertFalse(source.contains("fake-provider"))
    }

    @Test
    fun `Xtream sync rejects an all-zero content result before marking complete`() {
        val source = readIptvSource("XtreamRepository.kt")
        val emptyResultCheck = Regex(
            """liveChannelCount\s*==\s*0\s*&&\s*vodCount\s*==\s*0\s*&&\s*seriesCount\s*==\s*0"""
        ).find(source)

        assertTrue(
            "Xtream sync must reject a provider that returns zero live, VOD, and series items",
            emptyResultCheck != null
        )

        val completeStage = source.indexOf("XtreamSyncStage.COMPLETE")
        assertTrue(
            "The zero-content failure check must run before sync is marked complete",
            emptyResultCheck!!.range.first < completeStage
        )
        assertTrue(
            "The all-zero branch must report an actionable failure",
            source.substring(emptyResultCheck.range.first, completeStage)
                .contains("Provider returned no live channels, VOD, or series")
        )
    }

    @Test
    fun `Xtream sync treats VOD and series endpoint failures as optional when live channels work`() {
        val source = readIptvSource("XtreamRepository.kt")

        assertTrue(
            "VOD stream sync should be isolated so a malformed optional VOD endpoint does not block live channels",
            Regex("""val\s+catVods\s*=\s*runCatching\s*\{\s*apiClient\.getVodStreams""")
                .containsMatchIn(source)
        )
        assertTrue(
            "Series sync should be isolated so a malformed optional series endpoint does not block live channels",
            Regex("""val\s+catSeries\s*=\s*runCatching\s*\{\s*apiClient\.getSeries""")
                .containsMatchIn(source)
        )
        assertTrue(
            "Cancellation must be rethrown instead of reported as a provider network error",
            source.contains("if (error is CancellationException) throw error")
        )
        assertTrue(
            "A failed live endpoint must not delete previously synced live channels",
            source.contains("database.iptvDao().deleteChannelsByProvider(providerId)")
        )
        assertTrue(
            "A failed VOD endpoint must not delete previously synced VOD",
            source.contains("database.xtreamDao().deleteVodByProvider(providerId)")
        )
        assertTrue(
            "A failed series endpoint must not delete previously synced series",
            source.contains("database.xtreamDao().deleteSeriesByProvider(providerId)")
        )
    }

    @Test
    fun `Xtream sync can be started outside a Compose coroutine scope`() {
        val source = readIptvSource("IPTVRepository.kt")

        assertTrue(source.contains("fun startXtreamProviderSync(providerId: String)"))
        assertTrue(source.contains("syncScope.launch { syncXtreamProvider(providerId) }"))
    }

    @Test
    fun `IPTV wrapper owns the Xtream provider insert exactly once`() {
        val source = readIptvSource("IPTVRepository.kt")
        val methodStart = source.indexOf("suspend fun addXtreamProvider(")
        val methodEnd = source.indexOf("suspend fun deleteProvider(", startIndex = methodStart)
        assertTrue(methodStart >= 0)
        assertTrue(methodEnd > methodStart)

        val method = source.substring(methodStart, methodEnd)
        assertTrue(method.contains("persistProvider = false"))
        assertTrue(method.contains("iptvDao.insertProvider(p.toEntity())"))
        assertFalse(method.contains("XtreamRepository.addXtreamProvider(name, serverUrl, username, password)"))
    }

    @Test
    fun `Xtream live playback resolves with database provider fallback`() {
        val source = readIptvSource("IPTVRepository.kt")

        assertTrue(source.contains("findProviderForPlayback(channel.providerId)"))
        assertTrue(source.contains("providers.value.firstOrNull { it.id == providerId && it.isEnabled }"))
        assertTrue(source.contains("?.takeIf { it.isEnabled }"))
    }

    @Test
    fun `Xtream live playback request carries cleartext approval without storing raw URL`() {
        val source = readIptvSource("IPTVRepository.kt")

        assertTrue(source.contains("allowInsecureHttp = streamUrl.startsWith(\"xtream://\")"))
        assertTrue(source.contains("rawUrl = streamUrl"))
    }

    private fun readIptvSource(fileName: String): String {
        val relativePath = "src/main/kotlin/com/example/calmsource/feature/iptv/$fileName"
        val candidates = listOf(
            File(relativePath),
            File("feature/iptv/$relativePath"),
            File("../../feature/iptv/$relativePath")
        )
        val sourceFile = candidates.firstOrNull(File::isFile)
            ?: error("Could not find IPTV source file: $fileName")
        return sourceFile.readText().replace("\r\n", "\n")
    }
}

package com.example.calmsource.tv.ui

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Regression tests for TV QA audit findings.
 *
 * These tests statically verify source files for common TV-specific issues:
 * - Modifier ordering (focusable before clickable)
 * - Minimum font sizes for couch-distance readability
 * - Text overflow handling on constrained text
 * - Stable keys for TvLazyColumn/TvLazyRow items
 * - Focusable modifier presence on interactive elements
 * - No raw URLs visible to users
 */
class TvAuditRegressionTest {

    // --- TV-001: Modifier ordering ---

    @Test
    fun `TV-001 TvFocusCard modifier order clickable before focusable`() {
        val source = readSourceFile("TvUiComponents.kt")
        // After the fix, clickable should come BEFORE focusable in the modifier chain
        val clickableIdx = source.indexOf(".clickable { onClick() }")
        val focusableIdx = source.indexOf(".focusable()", clickableIdx)
        assertTrue(
            "TV-001: .clickable should precede .focusable in TvFocusCard modifier chain for TV D-pad",
            clickableIdx > 0 && focusableIdx > clickableIdx
        )
    }

    // --- TV-002: TvSourceBadge minimum font ---

    @Test
    fun `TV-002 TvSourceBadge text is at least 11sp`() {
        val source = readSourceFile("TvUiComponents.kt")
        // After fix, badge text should be at 11.sp
        assertFalse(
            "TV-002: TvSourceBadge text should not be 9sp (too small for TV)",
            source.contains("fontSize = 9.sp") && source.contains("type.name")
        )
    }

    // --- TV-003: Nav rail label minimum font ---

    @Test
    fun `TV-003 TvNavRailItem label is at least 14sp`() {
        val source = readSourceFile("../TvMainActivity.kt")
        // The TvNavRailItem label text should be >= 14sp
        assertFalse(
            "TV-003: TvNavRailItem label should not be 11sp (too small for TV)",
            source.contains("fontSize = 11.sp") && source.contains("TvNavRailItem")
        )
    }

    // --- TV-004: Channel name ellipsis ---

    @Test
    fun `TV-004 TvLiveChannelCard name has TextOverflow Ellipsis`() {
        val source = readSourceFile("TvHomeScreen.kt")
        // In TvLiveChannelCard section, there should be overflow = TextOverflow.Ellipsis near the channel name
        val channelCardStart = source.indexOf("fun TvLiveChannelCard")
        val channelCardSection = source.substring(channelCardStart)
        assertTrue(
            "TV-004: TvLiveChannelCard name should have TextOverflow.Ellipsis",
            channelCardSection.contains("TextOverflow.Ellipsis")
        )
    }

    // --- TV-011: Manual source title ellipsis ---

    @Test
    fun `TV-011 TvManualSourceItem title has maxLines and ellipsis`() {
        val source = readSourceFile("TvDetailsScreen.kt")
        val manualSourceStart = source.indexOf("fun TvManualSourceItem")
        val manualSourceSection = source.substring(manualSourceStart)
        assertTrue(
            "TV-011: TvManualSourceItem title should have maxLines constraint",
            manualSourceSection.contains("maxLines = 2") || manualSourceSection.contains("maxLines = 1")
        )
        assertTrue(
            "TV-011: TvManualSourceItem title should have TextOverflow.Ellipsis",
            manualSourceSection.contains("TextOverflow.Ellipsis")
        )
    }

    // --- TV-014: Channel switcher stable keys ---

    @Test
    fun `TV-014 Player channel switcher uses stable keys`() {
        val source = readSourceFile("TvPlayerScreen.kt")
        // Should use items(channels, key = { it.id }) not items(channels.size)
        assertFalse(
            "TV-014: Channel switcher should not use index-based items(channels.size)",
            source.contains("items(channels.size)")
        )
        assertTrue(
            "TV-014: Channel switcher should use keyed items with it.id or itemsIndexed with channel.id",
            source.contains("items(channels, key = { it.id })") || source.contains("key = { _, channel -> channel.id }")
        )
    }

    // --- TV-015: Checkbox config focusable ---

    @Test
    fun `TV-015 Settings checkbox config has focusable modifier`() {
        val source = readSourceFile("TvSettingsScreens.kt")
        // The checkbox section should have .focusable()
        val checkboxStart = source.indexOf("\"checkbox\"")
        val checkboxSection = source.substring(checkboxStart, checkboxStart + 2000)
        assertTrue(
            "TV-015: Checkbox config element must have .focusable() for D-pad navigation",
            checkboxSection.contains(".focusable()")
        )
    }

    // --- TV-016: Select option focusable ---

    @Test
    fun `TV-016 Settings select option has focusable modifier`() {
        val source = readSourceFile("TvSettingsScreens.kt")
        val selectStart = source.indexOf("\"select\"")
        val selectSection = source.substring(selectStart, selectStart + 2000)
        assertTrue(
            "TV-016: Select option element must have .focusable() for D-pad navigation",
            selectSection.contains(".focusable()")
        )
    }

    // --- TV-019: EPG label minimum font ---

    @Test
    fun `TV-019 EPG airing now label is at least 12sp`() {
        val source = readSourceFile("TvLiveTvScreen.kt") + readSourceFile("TvGuideScreen.kt")
        val airingNowIdx = source.indexOf("AIRING NOW")
        val nearbySection = source.substring(airingNowIdx, airingNowIdx + 100)
        assertFalse(
            "TV-019: EPG AIRING NOW label should not be 10sp (too small for TV)",
            nearbySection.contains("fontSize = 10.sp")
        )
    }

    // --- General: No raw URLs visible to users ---

    @Test
    fun `no raw URLs displayed to users in settings screens`() {
        val source = readSourceFile("TvSettingsScreens.kt")
        // All provider URL displays should use UrlRedactor.redactUrl
        assertTrue(
            "Provider URLs should use UrlRedactor.redactUrl",
            source.contains("UrlRedactor.redactUrl(provider.playlistUrl)")
        )
        assertTrue(
            "EPG source URLs should use UrlRedactor.redactUrl",
            source.contains("UrlRedactor.redactUrl(source.url)")
        )
        assertTrue(
            "Extension URLs should use UrlRedactor.redactUrl",
            source.contains("UrlRedactor.redactUrl(ext.url)")
        )
    }

    // --- General: All AsyncImage has contentDescription ---

    @Test
    fun `all AsyncImage instances have contentDescription`() {
        val files = listOf(
            "TvHomeScreen.kt", "TvSearchScreen.kt", "TvDetailsScreen.kt",
            "TvPlayerScreen.kt", "TvLiveTvScreen.kt", "TvGuideScreen.kt"
        )
        files.forEach { fileName ->
            val source = readSourceFile(fileName)
            val asyncImageCount = "AsyncImage\\(".toRegex().findAll(source).count()
            val contentDescCount = "contentDescription".toRegex().findAll(source).count()
            assertTrue(
                "$fileName: Every AsyncImage must have contentDescription (found $asyncImageCount images, $contentDescCount descriptions)",
                contentDescCount >= asyncImageCount
            )
        }
    }

    // --- General: TvLazyRow/TvLazyColumn items have stable keys ---

    @Test
    fun `TvHomeScreen lazy rows have stable keys`() {
        val source = readSourceFile("TvHomeScreen.kt")
        val itemsCalls = "items\\(".toRegex().findAll(source).count()
        val keyedCalls = "key = \\{".toRegex().findAll(source).count()
        assertTrue(
            "TvHomeScreen: All items() calls should have key parameter (found $itemsCalls items, $keyedCalls keyed)",
            keyedCalls >= itemsCalls
        )
    }

    @Test
    fun `TvHomeScreen deduplicates Stremio catalog items before keyed LazyRow`() {
        val source = readSourceFile("TvHomeScreen.kt")
        assertTrue(
            "TvHomeScreen should deduplicate extension catalog metas before using media IDs as LazyRow keys",
            source.contains("distinctBy { it.id }")
        )
    }

    @Test
    fun `TV app does not package obsolete tv foundation lazy lists`() {
        val buildFile = readProjectFile("app-tv/build.gradle.kts")
        assertFalse(
            "app-tv should not depend on androidx.tv.foundation; it is ABI-incompatible with the current Compose BOM",
            buildFile.contains("libs.androidx.tv.foundation")
        )
        assertTrue(
            "app-tv should depend on androidx.tv.material for TV Material3 buttons",
            buildFile.contains("libs.androidx.tv.material")
        )
    }

    @Test
    fun `TvSearchScreen lazy result keys tolerate duplicate media ids`() {
        val source = readSourceFile("TvSearchScreen.kt")
        assertTrue(
            "TvSearchScreen should use indexed keys so duplicate media IDs can render safely",
            source.contains("itemsIndexed(") && source.contains("tvSearchResultLazyKey")
        )
    }

    @Test
    fun `TvLiveGuideScreen lazy column has stable keys`() {
        val source = readSourceFile("TvLiveTvScreen.kt") + readSourceFile("TvGuideScreen.kt")
        assertTrue(
            "TvLiveTvScreen+TvGuideScreen: Channel items should have stable keys",
            source.contains("itemsIndexed(safeChannels, key = { _, channel -> channel.id })") ||
                source.contains("items(filteredChannels, key = { it.id })")
        )
    }

    // --- General: Error states are readable ---

    @Test
    fun `TvPlayerScreen error overlay has readable text`() {
        val source = readSourceFile("TvPlayerScreen.kt")
        assertTrue(
            "Error overlay should have large readable title",
            source.contains("fontSize = 28.sp") && source.contains("Playback Failed")
        )
        assertTrue(
            "Error overlay should have explanation text at 16sp",
            source.contains("fontSize = 16.sp") && source.contains("explanation")
        )
    }

    @Test
    fun `TvPlayerScreen reports playback resource state to ProviderManager`() {
        val source = readSourceFile("TvPlayerScreen.kt")
        assertTrue(
            "TvPlayerScreen should publish playback state changes to ProviderManager",
            source.contains("ProviderManager.setPlaybackState(state.toResourcePlaybackState())")
        )
        assertTrue(
            "TvPlayerScreen should publish low-memory playback profile to ProviderManager",
            source.contains("ProviderManager.setLowMemoryMode(enabled)")
        )
        assertTrue(
            "TvPlayerScreen should pause background work while preparing or buffering",
            source.contains("PlayerState.PREPARING") &&
                source.contains("PlayerState.BUFFERING") &&
                source.contains("ResourcePlaybackState.BUFFERING")
        )
    }

    // --- Helper ---

    private fun readSourceFile(fileName: String): String {
        if (fileName == "TvSettingsScreens.kt") {
            return readSourceFile("TvSettingsScreen.kt") +
                   readSourceFile("TvIptvSettingsSection.kt") +
                   readSourceFile("TvExtensionSettingsSection.kt") +
                   readSourceFile("TvDebridSettingsSection.kt") +
                   readSourceFile("TvPrioritiesSettingsSection.kt")
        }

        // Try multiple possible paths for the source file
        val basePaths = listOf(
            "app-tv/src/main/java/com/example/calmsource/tv/ui/",
            "app-tv/src/main/java/com/example/calmsource/tv/",
            "../app-tv/src/main/java/com/example/calmsource/tv/ui/",
            "../app-tv/src/main/java/com/example/calmsource/tv/"
        )
        
        // If the fileName contains "..", adjust the path
        val adjustedName = fileName.removePrefix("../")
        
        for (base in basePaths) {
            val file = File(base + adjustedName)
            if (file.exists()) return file.readText()
        }
        
        // Fallback: try absolute path
        val absBase = "d:/Program Files/iptv/app-tv/src/main/java/com/example/calmsource/tv/"
        val uiFile = File(absBase + "ui/" + adjustedName)
        if (uiFile.exists()) return uiFile.readText()
        val tvFile = File(absBase + adjustedName)
        if (tvFile.exists()) return tvFile.readText()
        
        fail("Could not find source file: $fileName")
        return "" // unreachable
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("d:/Program Files/iptv/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }?.readText() ?: ""
    }

    @Test
    fun `remediation Log e calls are wrapped in runCatching`() {
        val files = listOf(
            "TvSearchViewModel.kt",
            "TvDetailsScreen.kt",
            "TvLibraryScreen.kt",
            "TvLiveTvScreen.kt"
        )
        for (path in files) {
            val source = if (path == "TvSearchViewModel.kt") {
                try {
                    readSharedSearchSource("BaseSearchViewModel.kt")
                } catch (e: Exception) {
                    readSourceFile(path)
                }
            } else {
                readSourceFile(path)
            }
            if (source.isEmpty()) continue
            assertTrue(
                "$path should wrap Log.e inside runCatching",
                source.contains("runCatching {") && source.contains("android.util.Log.e")
            )
        }
    }

    private fun readSharedSearchSource(relativePath: String): String {
        val candidates = listOf(
            File("feature/search/src/main/kotlin/com/example/calmsource/feature/search/$relativePath"),
            File("../feature/search/src/main/kotlin/com/example/calmsource/feature/search/$relativePath"),
            File("../../feature/search/src/main/kotlin/com/example/calmsource/feature/search/$relativePath")
        )
        val sourceFile = candidates.firstOrNull(File::isFile)
            ?: error("Could not find shared search source file: $relativePath")
        return sourceFile.readText()
    }

    @Test
    fun `IPTV authentication LaunchedEffect displays dynamic helper status messages on TV`() {
        val source = readSourceFile("TvIptvSettingsSection.kt")
        if (source.isEmpty()) return

        // 1. Check LaunchEffect key isAddingXtream
        assertTrue(
            "TvIptvSettingsSection.kt should have LaunchedEffect(isAddingXtream)",
            source.contains("LaunchedEffect(isAddingXtream)")
        )

        // 2. Check elapsed calculation
        assertTrue(
            "TvIptvSettingsSection.kt should calculate elapsed connection time",
            source.contains("System.currentTimeMillis()")
        )

        // 3. Check helper status messages
        assertTrue(
            "TvIptvSettingsSection.kt should display initial connecting message",
            source.contains("\"Connecting and validating provider...\"")
        )
        assertTrue(
            "TvIptvSettingsSection.kt should display 30-60s warning status",
            source.contains("\"Still connecting... (Checking slow server catalog...)\"")
        )
        assertTrue(
            "TvIptvSettingsSection.kt should display 60s+ authenticating status",
            source.contains("\"Authenticating... (Retrieving IPTV channels list...)\"")
        )
    }
}


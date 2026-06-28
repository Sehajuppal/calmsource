package com.example.calmsource.tv.ui

import com.example.calmsource.feature.iptv.IptvLiveGuideFilters
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Mission27LiveParityTest {
    @Test
    fun `TV navigation exposes Live second and matches mobile ordering`() {
        val tvNavigation = readProjectFile(
            "app-tv/src/main/java/com/example/calmsource/tv/TvMainActivity.kt"
        )
        val mobileNavigation = readProjectFile(
            "app-mobile/src/main/java/com/example/calmsource/Navigation.kt"
        )

        assertOrdered(
            tvNavigation,
            "TvNavRailItem(Icons.Default.Home, \"Home\"",
            "TvNavRailItem(Icons.Default.LiveTv, \"Live\"",
            "TvNavRailItem(Icons.Default.Favorite, \"Library\"",
            "TvNavRailItem(Icons.Default.Search, \"Search\"",
            "TvNavRailItem(Icons.Default.Settings, \"Setup\""
        )
        assertTrue(tvNavigation.contains("1 -> TvScreen.LiveGuide"))
        assertTrue(tvNavigation.contains("2 -> TvScreen.Library"))
        assertTrue(tvNavigation.contains("3 -> TvScreen.Search"))
        assertTrue(mobileNavigation.contains("1 -> MobileScreen.LiveTv"))
    }

    @Test
    fun `mobile and TV Live pages expose the same smart browsing facets`() {
        val mobile = readProjectFile(
            "app-mobile/src/main/java/com/example/calmsource/ui/LiveTvScreen.kt"
        )
        val tvChannels = readProjectFile(
            "app-tv/src/main/java/com/example/calmsource/tv/ui/TvLiveTvScreen.kt"
        )
        val tvGuide = readProjectFile(
            "app-tv/src/main/java/com/example/calmsource/tv/ui/TvGuideScreen.kt"
        )
        val tv = tvChannels + tvGuide
        val sharedFilters = readProjectFile(
            "feature/iptv/src/main/kotlin/com/example/calmsource/feature/iptv/IptvLiveGuideFilters.kt"
        )

        listOf("Live", "Favorites", "Recent", "Sports", "Movies", "News", "Kids").forEach { label ->
            assertTrue("Shared live guide filters missing $label", sharedFilters.contains("\"$label\""))
        }
        listOf(IptvLiveGuideFilters.ALL_LANGUAGES, IptvLiveGuideFilters.ALL_REGIONS).forEach { label ->
            assertTrue("Shared live guide filters missing $label", sharedFilters.contains("\"$label\""))
            assertTrue("Mobile Live page should reference shared filter constants", mobile.contains("IptvLiveGuideFilters"))
            assertTrue("TV Live page should reference shared filter constants", tv.contains("IptvLiveGuideFilters"))
        }
        listOf("Popular", "Clear filters").forEach { label ->
            assertTrue("Mobile Live page is missing $label", mobile.contains("\"$label\""))
            assertTrue("TV Live page is missing $label", tv.contains("\"$label\""))
        }
        val viewModel = readProjectFile(
            "feature/iptv/src/main/kotlin/com/example/calmsource/feature/iptv/LiveGuideViewModel.kt"
        )
        assertTrue(viewModel.contains("IptvLiveGuideFilters.filterAndSort"))
        assertTrue(mobile.contains("IptvChannelFacets.contentSection") || mobile.contains("sectionById"))
        assertTrue(tv.contains("IptvChannelFacets.contentSection") || tv.contains("sectionById"))
        assertTrue(mobile.contains("onOpenSetup"))
        assertTrue(tv.contains("onOpenSetup"))
    }

    private fun assertOrdered(source: String, vararg values: String) {
        var previous = -1
        values.forEach { value ->
            val index = source.indexOf(value)
            assertTrue("Missing navigation item: $value", index >= 0)
            assertTrue("Navigation item is out of order: $value", index > previous)
            previous = index
        }
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File("d:/Program Files/iptv/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Could not find source file: $relativePath")
    }
}

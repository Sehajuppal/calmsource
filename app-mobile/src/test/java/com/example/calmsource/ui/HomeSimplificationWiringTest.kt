package com.example.calmsource.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSimplificationWiringTest {

    private fun readMobileSource(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app-mobile/$relativePath"),
            File("../../app-mobile/$relativePath")
        )
        val file = candidates.firstOrNull { it.exists() }
        return file?.readText() ?: throw java.io.FileNotFoundException("Could not find $relativePath")
    }

    @Test
    fun homeScreenIsEditorialFeedWithoutInScreenFilters() {
        val homeSource = readMobileSource("src/main/java/com/example/calmsource/ui/HomeScreen.kt")

        assertFalse(
            "Home must not render GlassTabBar",
            homeSource.contains("GlassTabBar(")
        )
        assertFalse(
            "Home must not render mood ChipRow",
            homeSource.contains("ChipRow(")
        )
        assertFalse(
            "Home must not accept selectedTab filter state",
            homeSource.contains("selectedTab:")
        )
        assertFalse(
            "Home must not accept selectedMood filter state",
            homeSource.contains("selectedMood:")
        )
        assertTrue(
            "Home must show the full editorial feed",
            homeSource.contains("val displayRows = remember(homeRows)")
        )
    }

    @Test
    fun searchKeepsFiltersLocalToSearch() {
        val navSource = readMobileSource("src/main/java/com/example/calmsource/Navigation.kt")
        val searchSource = readMobileSource("src/main/java/com/example/calmsource/ui/SearchScreen.kt")

        assertFalse(
            "Navigation must not wire Search filters back to Home",
            navSource.contains("homeSelectedTab") || navSource.contains("homeSelectedMood")
        )
        assertFalse(
            "Search must not publish filters to Home",
            searchSource.contains("onFilterChanged")
        )
        assertTrue(
            "Search must still expose local filter state",
            searchSource.contains("viewModel.setFilter")
        )
    }

    @Test
    fun testHeroSectionDirectPlayAndDetailsRouting() {
        val navSource = readMobileSource("src/main/java/com/example/calmsource/Navigation.kt")
        val homeSource = readMobileSource("src/main/java/com/example/calmsource/ui/HomeScreen.kt")

        assertTrue(
            "HomeScreen's onPlayClick must invoke playMediaDirectly",
            navSource.contains("onPlayClick = { mediaItem ->") &&
            navSource.contains("playMediaDirectly(mediaItem)")
        )

        assertTrue(
            "playMediaDirectly must resolve streams using IPTVRepository and ExtensionRepository",
            navSource.contains("IPTVRepository.findIptvStreamSources") &&
            navSource.contains("ExtensionRepository.lookupMediaStreams")
        )
        assertTrue(
            "playMediaDirectly must route to Player when bestSource is found",
            navSource.contains("currentScreen = MobileScreen.Player")
        )
        assertTrue(
            "playMediaDirectly must fallback to Details when no source is found",
            navSource.contains("currentScreen = MobileScreen.Details(mediaItem)")
        )

        assertTrue(
            "HomeScreen Hero must have a Play button invoking onPlayClick",
            homeSource.contains("AdaptiveButton") &&
            homeSource.contains("onClick = { onPlayClick(targetFeaturedMediaItem) }")
        )
        assertTrue(
            "HomeScreen Hero must have a More Info button invoking onMediaClick",
            homeSource.contains("GhostButton") &&
            homeSource.contains("onClick = { onMediaClick(targetFeaturedMediaItem) }")
        )
        assertTrue(
            "Hero must prefer backdrop art over poster art",
            homeSource.contains("backdropUrl ?: targetFeaturedItem.posterUrl") ||
                homeSource.contains("backdropUrl ?: featuredItem.posterUrl")
        )
        assertTrue(
            "Hero rotation must crossfade with cinematic timing",
            homeSource.contains("Crossfade") &&
            homeSource.contains("LumenTokens.Duration.cinematic")
        )
    }
}

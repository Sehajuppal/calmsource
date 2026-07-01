package com.example.calmsource.ui

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HomeAndSearchAdversarialTest {

    private fun readSourceFile(module: String, path: String): String {
        val candidates = listOf(
            File("$module/$path"),
            File("../$module/$path"),
            File("../../$module/$path"),
            File(path)
        )
        val file = candidates.firstOrNull { it.exists() }
        return file?.readText() ?: ""
    }

    @Test
    fun testSearchScreenEmptyStateAndFilters() {
        val mobileSearch = readSourceFile("app-mobile", "src/main/java/com/example/calmsource/ui/SearchScreen.kt")
        val tvSearch = readSourceFile("app-tv", "src/main/java/com/example/calmsource/tv/ui/TvSearchScreen.kt")

        if (mobileSearch.isNotEmpty()) {
            // Verify mobile search screen has an empty state when searchResults is empty and query/filters are not empty
            assertTrue(
                "Mobile SearchScreen should show empty state when there are no results",
                mobileSearch.contains("searchResults.isEmpty() && (query.isNotEmpty() || filters.isNotEmpty())")
            )
            assertTrue(
                "Mobile SearchScreen empty state should display LumenEmptyState",
                mobileSearch.contains("LumenEmptyState(")
            )
            assertTrue(
                "Mobile SearchScreen should support clearing filters or show appropriate title",
                mobileSearch.contains("Nothing matched")
            )
        }

        if (tvSearch.isNotEmpty()) {
            // Verify TV search screen has an empty state when searchResults is empty and query/filters are not empty
            assertTrue(
                "TV TvSearchScreen should show empty state when there are no results",
                tvSearch.contains("searchResults.isEmpty() && (query.isNotEmpty() || filters.isNotEmpty())")
            )
            assertTrue(
                "TV TvSearchScreen empty state should display LumenEmptyState",
                tvSearch.contains("LumenEmptyState(")
            )
            assertTrue(
                "TV TvSearchScreen should show search_nothing_matched or Nothing matched the active filters",
                tvSearch.contains("search_nothing_matched") || tvSearch.contains("Nothing matched")
            )
        }
    }

    @Test
    fun testReducedMotionRespectInHeroCrossfade() {
        val mobileHome = readSourceFile("app-mobile", "src/main/java/com/example/calmsource/ui/HomeScreen.kt")
        val tvHome = readSourceFile("app-tv", "src/main/java/com/example/calmsource/tv/ui/TvHomeScreen.kt")

        if (mobileHome.isNotEmpty()) {
            // Verify mobile home screen reads reduced motion setting
            assertTrue(
                "Mobile HomeScreen should check for reduced motion",
                mobileHome.contains("rememberReducedMotion()")
            )
            // Verify mobile home screen Hero crossfade respects reduced motion
            assertTrue(
                "Mobile HomeScreen Hero crossfade should set duration to 0 when reduced motion is enabled",
                mobileHome.contains("durationMillis = if (isReducedMotion) 0 else") ||
                mobileHome.contains("if (isReducedMotion) 0 else")
            )
        }

        if (tvHome.isNotEmpty()) {
            // Verify TV home screen reads reduced motion setting
            assertTrue(
                "TV TvHomeScreen should check for reduced motion",
                tvHome.contains("LocalReducedMotion.current")
            )
            // Verify TV home screen spotlight crossfade respects reduced motion
            assertTrue(
                "TV TvHomeScreen spotlight crossfade should set duration to 0 when reduced motion is enabled",
                tvHome.contains("durationMillis = if (reducedMotion) 0 else") ||
                tvHome.contains("if (reducedMotion) 0 else")
            )
            // Verify TV home screen immersive backdrop crossfade respects reduced motion
            assertTrue(
                "TV TvHomeScreen immersive backdrop crossfade should set duration to 0 when reduced motion is enabled",
                tvHome.contains("animationSpec = if (reducedMotion) tween(0) else tween(500)")
            )
        }
    }

    @Test
    fun testTvAppNoActiveProvidersEmptyState() {
        val tvHome = readSourceFile("app-tv", "src/main/java/com/example/calmsource/tv/ui/TvHomeScreen.kt")

        if (tvHome.isNotEmpty()) {
            // Verify TV home screen handles empty displayRows (no active providers/content)
            assertTrue(
                "TV TvHomeScreen should handle empty displayRows",
                tvHome.contains("displayRows.isEmpty()")
            )
            // Verify it shows empty state telling user to connect provider
            assertTrue(
                "TV TvHomeScreen should show connect provider empty state when no providers/content are active",
                tvHome.contains("empty_nothing_to_browse") && tvHome.contains("empty_connect_provider")
            )
        }
    }
}

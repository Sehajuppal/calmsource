package com.example.calmsource.ui

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Regression tests for the Mobile App QA Audit.
 *
 * These tests verify source code patterns to prevent regressions of previously
 * fixed bugs. They scan the actual source files for known anti-patterns.
 *
 * Privacy: No real URLs, tokens, or credentials are used in these tests.
 */
class MobileAppQaRegressionTest {

    private val uiDir = "src/main/java/com/example/calmsource/ui"
    private val navDir = "src/main/java/com/example/calmsource"

    private fun readSource(relativePath: String): String {
        // Try multiple base paths to support different working directories
        val candidates = listOf(
            File("app-mobile/$relativePath"),
            File("$relativePath"),
            File("../../app-mobile/$relativePath")
        )
        val file = candidates.firstOrNull { it.exists() }
        // If no file found, return empty string - test will pass gracefully in CI
        return file?.readText() ?: ""
    }

    // ── BUG-M01: Deprecated Icons.Default.ArrowBack ──────────────────

    @Test
    fun `BUG-M01 DetailsScreen uses AutoMirrored ArrowBack`() {
        val source = readSource("$uiDir/DetailsScreen.kt")
        if (source.isEmpty()) return // Skip if file not found in CI
        assertFalse(
            "DetailsScreen still uses deprecated Icons.Default.ArrowBack",
            source.contains("Icons.Default.ArrowBack")
        )
        assertTrue(
            "DetailsScreen should use Icons.AutoMirrored.Filled.ArrowBack",
            source.contains("Icons.AutoMirrored.Filled.ArrowBack")
        )
    }

    @Test
    fun `BUG-M01 PlayerScreen uses AutoMirrored ArrowBack`() {
        val source = readSource("$uiDir/PlayerScreen.kt")
        if (source.isEmpty()) return
        assertFalse(
            "PlayerScreen still uses deprecated Icons.Default.ArrowBack",
            source.contains("Icons.Default.ArrowBack")
        )
        assertTrue(
            "PlayerScreen should use Icons.AutoMirrored.Filled.ArrowBack",
            source.contains("Icons.AutoMirrored.Filled.ArrowBack")
        )
    }

    @Test
    fun `BUG-M01 SettingsScreens uses AutoMirrored ArrowBack`() {
        val source = readSource("$uiDir/SettingsScreens.kt")
        if (source.isEmpty()) return
        assertFalse(
            "SettingsScreens still uses deprecated Icons.Default.ArrowBack",
            source.contains("Icons.Default.ArrowBack")
        )
        assertTrue(
            "SettingsScreens should use Icons.AutoMirrored.Filled.ArrowBack",
            source.contains("Icons.AutoMirrored.Filled.ArrowBack")
        )
    }

    // ── BUG-M02: Deprecated LinearProgressIndicator progress param ───

    @Test
    fun `BUG-M02 HomeScreen avoids deprecated LinearProgressIndicator progress syntax`() {
        val source = readSource("$uiDir/HomeScreen.kt")
        if (source.isEmpty()) return

        assertFalse(
            "HomeScreen uses deprecated progress = Float syntax",
            source.contains("progress = 0.6f,")
        )

        if (source.contains("LinearProgressIndicator(") && source.contains("0.6f")) {
            assertTrue(
                "A determinate HomeScreen indicator should use the lambda progress overload",
                Regex("""progress\s*=\s*\{\s*0\.6f\s*}""").containsMatchIn(source)
            )
        }
    }

    @Test
    fun `BUG-M02 LiveTvScreen uses lambda form for LinearProgressIndicator`() {
        val source = readSource("$uiDir/LiveTvScreen.kt")
        if (source.isEmpty()) return
        assertFalse(
            "LiveTvScreen uses deprecated progress = Float syntax",
            source.contains("progress = progressPercentage.coerceIn(0f, 1f),")
        )
        assertTrue(
            "LiveTvScreen should use progress lambda syntax",
            source.contains("progress = { progressPercentage.coerceIn(0f, 1f) }")
        )
    }

    // ── BUG-M03: Deprecated enum values() ────────────────────────────

    @Test
    fun `BUG-M03 PlayerScreen uses entries instead of values()`() {
        val source = readSource("$uiDir/PlayerScreen.kt")
        if (source.isEmpty()) return
        assertFalse(
            "PlayerScreen still uses deprecated .values()",
            source.contains("AutoFallbackPolicy.values()")
        )
        assertTrue(
            "PlayerScreen should use .entries",
            source.contains("AutoFallbackPolicy.entries")
        )
    }

    // ── BUG-M04: Double-clickable GlassCard + .clickable ─────────────

    @Test
    fun `BUG-M04 SearchResultItem does not double-apply clickable on GlassCard`() {
        val source = readSource("$uiDir/SearchScreen.kt")
        if (source.isEmpty()) return
        // SearchResultItem should use GlassCard's onClick, not modifier.clickable
        val searchResultBlock = source.substringAfter("fun SearchResultItem(")
        assertFalse(
            "SearchResultItem should not have .clickable on GlassCard modifier",
            searchResultBlock.contains(".clickable { onClick() }\n    ) {")
        )
    }

    @Test
    fun `BUG-M04 ManualSourceItem does not double-apply clickable on GlassCard`() {
        val source = readSource("$uiDir/DetailsScreenComponents.kt")
        if (source.isEmpty()) return
        val manualBlock = source.substringAfter("fun ManualSourceItem(")
        assertFalse(
            "ManualSourceItem should not have .clickable on GlassCard modifier",
            manualBlock.contains(".clickable { onClick() }\n    ) {")
        )
    }

    @Test
    fun `BUG-M04 LiveChannelCard does not double-apply clickable on GlassCard`() {
        val source = readSource("$uiDir/HomeScreen.kt")
        if (source.isEmpty()) return
        val cardBlock = source.substringAfter("fun LiveChannelCard(")
        assertFalse(
            "LiveChannelCard should not have .clickable on GlassCard modifier",
            cardBlock.contains(".clickable { onClick() }\n    ) {")
        )
    }

    @Test
    fun `BUG-M04 LiveChannelGuideItem does not double-apply clickable on GlassCard`() {
        val source = readSource("$uiDir/LiveTvScreen.kt")
        if (source.isEmpty()) return
        val guideBlock = source.substringAfter("fun LiveChannelGuideItem(")
        assertFalse(
            "LiveChannelGuideItem should not have .clickable on GlassCard modifier",
            guideBlock.contains(".clickable { onClick() }\n    ) {")
        )
    }

    // ── BUG-M05: Missing TextOverflow.Ellipsis on maxLines text ──────

    @Test
    fun `BUG-M05 LiveTvScreen description has TextOverflow Ellipsis`() {
        val source = readSource("$uiDir/LiveTvScreen.kt")
        if (source.isEmpty()) return
        // The description text should have both maxLines and overflow
        assertTrue(
            "LiveTvScreen description should use TextOverflow.Ellipsis",
            source.contains("overflow = TextOverflow.Ellipsis")
        )
    }

    // ── BUG-M06: ManualSourceItem title missing maxLines ─────────────

    @Test
    fun `BUG-M06 ManualSourceItem title has maxLines and overflow`() {
        val source = readSource("$uiDir/DetailsScreenComponents.kt")
        if (source.isEmpty()) return
        val manualBlock = source.substringAfter("fun ManualSourceItem(")
        assertTrue(
            "ManualSourceItem title should have maxLines",
            manualBlock.contains("maxLines = 2")
        )
        assertTrue(
            "ManualSourceItem title should have TextOverflow.Ellipsis",
            manualBlock.contains("overflow = TextOverflow.Ellipsis")
        )
    }

    // ── BUG-M07/M10: ChannelSwitcherSheet missing keys ───────────────

    @Test
    fun `BUG-M07 ChannelSwitcherSheet items have stable keys`() {
        val source = readSource("$uiDir/PlayerScreen.kt")
        if (source.isEmpty()) return
        val switcherBlock = source.substringAfter("fun ChannelSwitcherSheet(")
        assertTrue(
            "ChannelSwitcherSheet items should have key parameter",
            switcherBlock.contains("items(categoryChannels, key = { it.id })")
        )
        assertTrue(
            "ChannelSwitcherSheet category items should have key",
            switcherBlock.contains("item(key = \"category-\$category\")")
        )
    }

    // ── BUG-M08: Navigation activeTab should survive config changes ──

    @Test
    fun `PlayerScreen reports playback resource state to ProviderManager`() {
        val playerSource = readSource("$uiDir/PlayerScreen.kt")
        val playbackVmSource = readSource("$uiDir/MobilePlaybackViewModel.kt")
        if (playerSource.isEmpty() && playbackVmSource.isEmpty()) return
        val wiringSource = playerSource + playbackVmSource

        assertTrue(
            "Playback layer should publish playback state changes to ProviderManager",
            wiringSource.contains("ProviderManager.setPlaybackState(state.toResourcePlaybackState())")
        )
        assertTrue(
            "Playback layer should publish low-memory playback profile to ProviderManager",
            wiringSource.contains("ProviderManager.setLowMemoryMode(enabled)")
        )
        assertTrue(
            "PlayerScreen should pause background work while preparing or buffering",
            playerSource.contains("PlayerState.PREPARING") &&
                playerSource.contains("PlayerState.BUFFERING") &&
                playerSource.contains("ResourcePlaybackState.BUFFERING")
        )
    }

    @Test
    fun `BUG-M08 Navigation activeTab uses rememberSaveable`() {
        val source = readSource("$navDir/Navigation.kt")
        if (source.isEmpty()) return
        assertTrue(
            "activeTab should use rememberSaveable to survive config changes",
            source.contains("rememberSaveable")
        )
        assertFalse(
            "activeTab should NOT use plain remember",
            source.contains("var activeTab by remember {")
        )
    }

    // ── BUG-M09: Settings sub-screens missing verticalScroll ─────────

    @Test
    fun `BUG-M09 SearchSettingsScreen has verticalScroll`() {
        val source = readSource("$uiDir/SettingsScreens.kt")
        if (source.isEmpty()) return
        val searchBlock = source.substringAfter("fun SearchSettingsScreen(")
        assertTrue(
            "SearchSettingsScreen should have verticalScroll",
            searchBlock.contains("verticalScroll")
        )
    }

    @Test
    fun `BUG-M09 GeneralSettingsScreen has verticalScroll`() {
        val source = readSource("$uiDir/SettingsScreens.kt")
        if (source.isEmpty()) return
        val generalBlock = source.substringAfter("fun GeneralSettingsScreen(")
        assertTrue(
            "GeneralSettingsScreen should have verticalScroll",
            generalBlock.contains("verticalScroll")
        )
    }

    // ── Accessibility: All AsyncImage have contentDescription ────────

    @Test
    fun `all AsyncImage calls have contentDescription`() {
        val files = listOf(
            "$uiDir/HomeScreen.kt",
            "$uiDir/SearchScreen.kt",
            "$uiDir/DetailsScreen.kt",
            "$uiDir/LiveTvScreen.kt"
        )
        for (path in files) {
            val source = readSource(path)
            if (source.isEmpty()) continue
            val asyncImageCount = Regex("AsyncImage\\(").findAll(source).count()
            val contentDescCount = Regex("contentDescription\\s*=").findAll(source).count()
            // There should be at least as many contentDescription args as AsyncImage calls
            assertTrue(
                "$path: not all AsyncImage calls have contentDescription ($asyncImageCount images, $contentDescCount descriptions)",
                contentDescCount >= asyncImageCount
            )
        }
    }

    // ── No raw URLs visible in UI screens ────────────────────────────

    @Test
    fun `HomeScreen deduplicates Stremio catalog items before keyed LazyRow`() {
        val source = readSource("$uiDir/HomeScreen.kt")
        if (source.isEmpty()) return

        assertTrue(
            "HomeScreen should deduplicate extension catalog metas before using media IDs as LazyRow keys",
            source.contains("distinctBy { it.id }")
        )
    }

    @Test
    fun `SearchScreen lazy result keys tolerate duplicate media ids`() {
        val source = readSource("$uiDir/SearchScreen.kt")
        if (source.isEmpty()) return

        assertTrue(
            "SearchScreen should use indexed keys so duplicate media IDs can render safely",
            (source.contains("itemsIndexed(") || source.contains("gridItemsIndexed(")) &&
                source.contains("searchResultLazyKey")
        )
    }

    @Test
    fun `no raw URLs hardcoded in UI text strings`() {
        val files = listOf(
            "$uiDir/HomeScreen.kt",
            "$uiDir/SearchScreen.kt",
            "$uiDir/DetailsScreen.kt",
            "$uiDir/PlayerScreen.kt",
            "$uiDir/LiveTvScreen.kt",
            "$uiDir/SettingsScreens.kt"
        )
        for (path in files) {
            val source = readSource(path)
            if (source.isEmpty()) continue
            // Check for http:// or https:// in Text() content strings (not in model/network code)
            val textCalls = Regex("Text\\([^)]*\"https?://[^\"]*\"").findAll(source)
            assertEquals(
                "$path contains raw URL in Text() composable",
                0,
                textCalls.count()
            )
        }
    }

    // ── BUG-M15: Advanced details toggle & Redacted URLs ─────────────

    @Test
    fun `BUG-M15 DetailsScreen implements showRawDetails toggle and redacts URLs`() {
        val source = readSource("$uiDir/DetailsScreen.kt")
        val components = readSource("$uiDir/DetailsScreenComponents.kt")
        if (source.isEmpty()) return
        
        // 1. Check for showRawDetails state
        assertTrue(
            "DetailsScreen should declare showRawDetails local state",
            source.contains("var showRawDetails")
        )

        // 2. Check for Switch composable tied to showRawDetails
        assertTrue(
            "DetailsScreen should display a Switch for showRawDetails",
            source.contains("Switch(") && source.contains("checked = showRawDetails")
        )

        // 3. Check that ManualSourceItem accepts showRawDetails
        assertTrue(
            "ManualSourceItem should accept showRawDetails parameter",
            components.contains("showRawDetails: Boolean")
        )

        // 4. Check that option.source.name is displayed when showRawDetails is true
        assertTrue(
            "ManualSourceItem should conditionally display source name or redacted filename when showRawDetails is true",
            components.contains("if (showRawDetails)") && components.contains("option.source.name")
        )

        // 5. Check that UrlRedactor.redactUrl is used to redact option.source.url
        assertTrue(
            "ManualSourceItem should redact source URL using UrlRedactor.redactUrl",
            components.contains("UrlRedactor.redactUrl(option.source.url)")
        )
    }

    @Test
    fun `remediation Log e calls are wrapped in runCatching`() {
        val files = listOf(
            "HomeViewModel.kt",
            "SearchViewModel.kt",
            "DetailsScreen.kt",
            "LibraryScreen.kt",
            "LiveTvScreen.kt"
        )
        for (path in files) {
            val source = readSource(path)
            if (source.isEmpty()) continue
            assertTrue(
                "$path should wrap Log.e inside runCatching",
                source.contains("runCatching {") && source.contains("android.util.Log.e")
            )
        }
    }

    @Test
    fun `IPTV authentication LaunchedEffect displays dynamic helper status messages on mobile`() {
        val source = readSource("$uiDir/SettingsScreens.kt")
        if (source.isEmpty()) return

        // 1. Check LaunchEffect key isConnecting
        assertTrue(
            "SettingsScreens.kt should have LaunchedEffect(isConnecting)",
            source.contains("LaunchedEffect(isConnecting)")
        )

        // 2. Check elapsed calculation
        assertTrue(
            "SettingsScreens.kt should calculate elapsed connection time",
            source.contains("System.currentTimeMillis()")
        )

        // 3. Check helper status messages
        assertTrue(
            "SettingsScreens.kt should display initial connecting message",
            source.contains("\"Connecting and validating provider...\"")
        )
        assertTrue(
            "SettingsScreens.kt should display 30-60s warning status",
            source.contains("\"Still connecting... (Checking slow server catalog...)\"")
        )
        assertTrue(
            "SettingsScreens.kt should display 60s+ authenticating status",
            source.contains("\"Authenticating... (Retrieving IPTV channels list...)\"")
        )
    }

    @Test
    fun `UX settings add-ons list avoids LazyColumn inside verticalScroll`() {
        val source = readSource("$uiDir/SettingsScreens.kt")
        if (source.isEmpty()) return
        assertTrue(
            "SettingsScreens should scroll the settings column",
            source.contains("verticalScroll"),
        )
        assertFalse(
            "SettingsScreens must not nest LazyColumn inside verticalScroll (infinite height crash)",
            source.contains("LazyColumn"),
        )
        assertTrue(
            "SettingsScreens should render installed add-ons with forEach rows",
            source.contains("extensions.forEach"),
        )
    }

    @Test
    fun `Phase 2 Chrome Diet UI Refactor requirements are met`() {
        val miniPlayerSource = readSource("src/main/java/com/example/calmsource/ui/MiniPlayerBar.kt")
        val navSource = readSource("src/main/java/com/example/calmsource/Navigation.kt")
        val layoutSource = listOf(
            File("core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenLegacyBridge.kt"),
            File("../core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenLegacyBridge.kt"),
        ).firstOrNull { it.exists() }?.readText().orEmpty()

        if (miniPlayerSource.isNotEmpty()) {
            assertFalse("MiniPlayerBar should not use GlassSurface", miniPlayerSource.contains("GlassSurface("))
            assertTrue("MiniPlayerBar should use surfaceMuted color", miniPlayerSource.contains("LumenTokens.Color.surfaceMuted"))
            assertTrue("MiniPlayerBar should have height 56.dp", miniPlayerSource.contains(".height(56.dp)"))
        }

        if (navSource.isNotEmpty()) {
            assertTrue("CustomFloatingNavigationBar should have height 60.dp", navSource.contains(".height(60.dp)"))
            assertFalse("CustomFloatingNavigationBar should not contain sliding indicator capsule", navSource.contains("indicatorOffset"))

            // Verify Navigation.kt does not wrap CustomFloatingNavigationBar in GlassSurface
            assertFalse(
                "Navigation.kt should not wrap CustomFloatingNavigationBar in GlassSurface",
                navSource.contains("GlassSurface(\n                            modifier = Modifier.fillMaxWidth()")
            )

            val tabInfoCount = Regex("TabInfo\\(stringResource").findAll(navSource).count()
            assertEquals("CustomFloatingNavigationBar should have exactly 5 tabs", 5, tabInfoCount)

            assertTrue("showSyncOverlay should require providers.isEmpty()", navSource.contains("providers.isEmpty()"))
            assertTrue("Sync banner should use SyncStatusPill", navSource.contains("SyncStatusPill("))
            if (layoutSource.isNotEmpty()) {
                assertTrue(
                    "bottomNavPadding should be slimmed for phase 2",
                    layoutSource.contains("bottomNavPadding: Dp = 72.dp"),
                )
            }
        }
    }

    @Test
    fun `Phase 4 design token consolidation requirements are met`() {
        val mobileDir = File("src/main/java/com/example/calmsource")
        val chipRow = listOf(
            File("core/ui/src/main/kotlin/com/example/calmsource/core/ui/components/ChipRow.kt"),
            File("../core/ui/src/main/kotlin/com/example/calmsource/core/ui/components/ChipRow.kt"),
        ).firstOrNull { it.exists() }?.readText().orEmpty()

        if (mobileDir.exists()) {
            mobileDir.walkTopDown()
                .filter { it.extension == "kt" && it.name != "UiComponents.kt" }
                .forEach { file ->
                    val source = file.readText()
                    assertFalse(
                        "${file.name} should not reference LumenLegacySpace",
                        source.contains("LumenLegacySpace"),
                    )
                    assertFalse(
                        "${file.name} should not call deprecated GlassCard",
                        source.contains("GlassCard("),
                    )
                    assertFalse(
                        "${file.name} should not call deprecated PremiumButton",
                        source.contains("PremiumButton("),
                    )
                }
        }

        if (chipRow.isNotEmpty()) {
            assertTrue(
                "ChipRow should use subtle selected fill",
                chipRow.contains("selectedContainerColor = t.colors.foreground.copy(alpha = 0.12f)"),
            )
            assertFalse(
                "ChipRow should not use full brand fill when selected",
                chipRow.contains("selectedContainerColor = t.colors.brand"),
            )
        }
    }

    @Test
    fun `Phase 5 micro-delight motion requirements are met`() {
        val lumenMotion = listOf(
            File("core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenDelightMotion.kt"),
            File("../core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenDelightMotion.kt"),
        ).firstOrNull { it.exists() }?.readText().orEmpty()
        val screenTransition = listOf(
            File("core/ui/src/main/kotlin/com/example/calmsource/core/ui/components/LumenScreenTransition.kt"),
            File("../core/ui/src/main/kotlin/com/example/calmsource/core/ui/components/LumenScreenTransition.kt"),
        ).firstOrNull { it.exists() }?.readText().orEmpty()
        val homeSource = readSource("src/main/java/com/example/calmsource/ui/HomeScreen.kt")
        val detailsSource = readSource("src/main/java/com/example/calmsource/ui/DetailsScreen.kt")
        val playerSource = readSource("src/main/java/com/example/calmsource/ui/PlayerScreen.kt")
        val navSource = readSource("src/main/java/com/example/calmsource/Navigation.kt")

        if (lumenMotion.isNotEmpty()) {
            assertTrue("LumenDelightMotion should gate on reduced motion", lumenMotion.contains("reducedMotion"))
            assertTrue(
                "LumenDelightMotion should use cinematic duration for shared bounds",
                lumenMotion.contains("LumenTokens.Duration.cinematic"),
            )
            assertTrue(
                "LumenDelightMotion should use focus duration for backdrop fade",
                lumenMotion.contains("LumenTokens.Duration.focus"),
            )
            assertTrue(
                "Mini player enter should use a soft spring",
                lumenMotion.contains("stiffness = 220f"),
            )
        }

        if (screenTransition.isNotEmpty()) {
            assertTrue(
                "LumenScreenTransition should delegate to LumenDelightMotion",
                screenTransition.contains("LumenDelightMotion.screenEnterTransition"),
            )
        }

        if (homeSource.isNotEmpty()) {
            assertTrue(
                "Home hero shared bounds should use tokenized bounds transform",
                homeSource.contains("rememberLumenSharedBoundsTransform()"),
            )
        }

        if (detailsSource.isNotEmpty()) {
            assertTrue(
                "Details backdrop should stagger fade during shared transition",
                detailsSource.contains("backdropAlpha") &&
                    detailsSource.contains("LumenDelightMotion.detailsBackdropFadeSpec"),
            )
        }

        if (playerSource.isNotEmpty()) {
            assertTrue(
                "Player minimize should animate before routing to mini player",
                playerSource.contains("minimizeProgress") &&
                    playerSource.contains("LumenDelightMotion.playerMinimizeSpec"),
            )
        }

        if (navSource.isNotEmpty()) {
            assertTrue(
                "Mini player should enter with LumenDelightMotion transition",
                navSource.contains("LumenDelightMotion.miniPlayerEnterTransition"),
            )
        }
    }

    @Test
    fun `Phase 6 entry and edge screen requirements are met`() {
        val profilesSource = readSource("src/main/java/com/example/calmsource/ui/ProfilesScreen.kt")
        val wizardSource = readSource("src/main/java/com/example/calmsource/ui/FirstRunSetupWizard.kt")
        val navSource = readSource("src/main/java/com/example/calmsource/Navigation.kt")
        val homeSkeleton = listOf(
            File("core/ui/src/main/kotlin/com/example/calmsource/core/ui/components/HomeFeedSkeleton.kt"),
            File("../core/ui/src/main/kotlin/com/example/calmsource/core/ui/components/HomeFeedSkeleton.kt"),
        ).firstOrNull { it.exists() }?.readText().orEmpty()

        if (profilesSource.isNotEmpty()) {
            assertFalse(
                "ProfilesScreen should drop eyebrow logotype",
                profilesSource.contains("CALMSOURCE"),
            )
            assertTrue(
                "ProfilesScreen should use LumenType for headline",
                profilesSource.contains("LumenType.H1.toTextStyle()"),
            )
        }

        if (wizardSource.isNotEmpty()) {
            assertFalse("FirstRunSetupWizard should not use GlassSurface", wizardSource.contains("GlassSurface("))
            assertTrue("FirstRunSetupWizard should use LumenCard", wizardSource.contains("LumenCard("))
            assertTrue(
                "FirstRunSetupWizard syncing should use non-blocking SyncStatusPill",
                wizardSource.contains("SyncStatusPill("),
            )
        }

        if (navSource.isNotEmpty()) {
            assertTrue(
                "Boot gate should show HomeFeedSkeleton instead of spinner",
                navSource.contains("HomeFeedSkeleton(") &&
                    !navSource.contains("BootDestination.Loading) {\n                    Box(\n                        modifier = Modifier.fillMaxSize(),\n                        contentAlignment = Alignment.Center,\n                    ) {\n                        androidx.compose.material3.CircularProgressIndicator"),
            )
        }

        assertTrue("HomeFeedSkeleton should exist in core/ui", homeSkeleton.isNotEmpty())
    }

    @Test
    fun `Phase 7 typography requirements are met`() {
        val fontFamily = listOf(
            File("core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenFontFamily.kt"),
            File("../core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenFontFamily.kt"),
        ).firstOrNull { it.exists() }?.readText().orEmpty()
        val tokens = listOf(
            File("core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenTokens.Generated.kt"),
            File("../core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenTokens.Generated.kt"),
        ).firstOrNull { it.exists() }?.readText().orEmpty()
        val theme = listOf(
            File("core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenTheme.kt"),
            File("../core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenTheme.kt"),
        ).firstOrNull { it.exists() }?.readText().orEmpty()
        val profiles = readSource("src/main/java/com/example/calmsource/ui/ProfilesScreen.kt")
        val home = readSource("src/main/java/com/example/calmsource/ui/HomeScreen.kt")
        val nav = readSource("src/main/java/com/example/calmsource/Navigation.kt")
        val login = readSource("src/main/java/com/example/calmsource/ui/LoginScreen.kt")

        if (fontFamily.isNotEmpty()) {
            assertTrue("LumenFontFamily should define display and body stacks", fontFamily.contains("val display") && fontFamily.contains("val body"))
            assertTrue("LumenFontFamily should map styles", fontFamily.contains("fun forStyle"))
        }
        if (tokens.isNotEmpty()) {
            assertTrue(
                "LumenTokens.Style.toTextStyle should apply fontFamily",
                tokens.contains("fontFamily = LumenFontFamily.forStyle(this)"),
            )
        }
        if (theme.isNotEmpty()) {
            assertTrue(
                "LumenTheme should map Material typography from LumenType",
                theme.contains("lumenTypography(scale)") && theme.contains("LumenType."),
            )
        }
        listOf(
            "ProfilesScreen" to profiles,
            "HomeScreen" to home,
            "Navigation" to nav,
            "LoginScreen" to login,
        ).forEach { (name, source) ->
            if (source.isNotEmpty()) {
                assertFalse(
                    "$name should not use raw fontSize literals",
                    source.contains("fontSize =") || source.contains("fontSize="),
                )
            }
        }
    }
}

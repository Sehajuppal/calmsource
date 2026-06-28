package com.example.calmsource.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mission23MobileWiringTest {

    @Test
    fun `playback navigation stays memory only and does not serialize private source data`() {
        val source = readMobileSource("Navigation.kt")

        assertTrue(
            "The MobileScreen route must use ordinary remember because Player contains raw URLs and headers",
            Regex(
                """var\s+currentScreen\s+by\s+remember(?:\s*\([^)]*\))?\s*\{\s*mutableStateOf<MobileScreen>"""
            ).containsMatchIn(source)
        )
        assertFalse(
            "MobileScreen must not be saved through rememberSaveable",
            Regex("""rememberSaveable\s*(?:\([^)]*\))?\s*\{[^}]*mutableStateOf<MobileScreen>""")
                .containsMatchIn(source)
        )

        val forbiddenPersistence = listOf(
            "MobileScreenSaver",
            "savePlaybackRequest",
            "saveSource",
            "putString(\"rawUrl\"",
            "putBundle(\"headers\""
        )
        forbiddenPersistence.forEach { token ->
            assertFalse("Navigation must not persist playback source data via $token", source.contains(token))
        }
    }

    @Test
    fun `mobile exposes Library as a navigable screen`() {
        val source = readMobileSource("Navigation.kt")

        assertTrue(Regex("""data\s+object\s+Library\s*:\s*MobileScreen""").containsMatchIn(source))
        assertTrue(Regex("""MobileScreen\.Library\s*->\s*LibraryScreen\s*\(""").containsMatchIn(source))
        assertTrue(
            "The normal navigation bar must expose Library",
            source.contains("contentDescription = \"Library\"") &&
                source.contains("label = \"Library\"")
        )
    }

    @Test
    fun `mobile Library uses section-scoped lazy list keys`() {
        val source = readMobileSource("ui/LibraryScreen.kt")

        listOf(
            "\"continue-${'$'}{it.reference.itemKey}\"",
            "\"favorite-${'$'}{it.reference.itemKey}\"",
            "\"history-${'$'}{it.reference.itemKey}\"",
            "\"recent-${'$'}{it.reference.itemKey}\"",
            "\"search-${'$'}{it.query.lowercase()}\""
        ).forEach { key ->
            assertTrue("Library LazyColumn must use section-scoped key $key", source.contains(key))
        }
    }

    @Test
    fun `mobile Home uses real repositories and has no FakeData rows`() {
        val source = readMobileSource("ui/HomeScreen.kt")

        assertFalse(
            "Home must not render production rows from FakeData",
            Regex("""\bFakeData\s*\.""").containsMatchIn(source)
        )
        assertTrue(source.contains("IPTVRepository.getLiveChannels"))
        assertTrue(source.contains("ExtensionRepository.extensions"))
    }

    @Test
    fun `mobile search supplies safe user memory hooks`() {
        val source = readMobileSource("ui/SearchScreen.kt")
        val viewModel = try {
            readSharedSearchSource("BaseSearchViewModel.kt")
        } catch (e: Exception) {
            readMobileSource("ui/SearchViewModel.kt")
        }

        assertFalse(source.contains("memorySnapshot.load()"))
        assertTrue(viewModel.contains("signalSink = SearchSignalSink"))
        assertTrue(viewModel.contains("memoryRepository.recordSearch(query)"))
        assertTrue(viewModel.contains("memorySnapshot = SearchMemorySnapshot"))
        assertTrue(viewModel.contains("memoryRepository.observeFavorites().first()"))
        assertTrue(viewModel.contains("memoryRepository.observeWatchHistory().first()"))
        assertTrue(viewModel.contains("memoryRepository.observeSearchHistory().first()"))
        assertTrue(viewModel.contains("UniversalSearchEngineImpl("))
        assertTrue(viewModel.contains("signalSink = signalSink"))
        assertTrue(viewModel.contains("memorySnapshot = memorySnapshot"))
        assertTrue(viewModel.contains("toSearchDisplayResults()"))
    }

    @Test
    fun `mobile search keystrokes stay local until explicit submit`() {
        val source = readMobileSource("ui/SearchScreen.kt")
        val viewModel = try {
            readSharedSearchSource("BaseSearchViewModel.kt")
        } catch (e: Exception) {
            readMobileSource("ui/SearchViewModel.kt")
        }

        assertTrue(
            "Passive query changes should call local preview search",
            source.contains("onValueChange = viewModel::search")
        )
        assertTrue(
            "The keyboard Search action should submit connected-provider search explicitly",
            source.contains("KeyboardActions(onSearch = { submitQuery() })") &&
                source.contains("viewModel.submitSearch(submittedQuery)")
        )
        assertTrue(
            "SearchViewModel.search must run the local-only path",
            viewModel.contains("fun search(query: String)") &&
                viewModel.contains("runSearch(query = query, includeConnectedProviders = false")
        )
        assertTrue(
            "SearchViewModel.submitSearch must be the connected-provider path",
            viewModel.contains("fun submitSearch(query: String)") &&
                viewModel.contains("runSearch(query = query, includeConnectedProviders = true")
        )
        assertTrue(
            "UniversalSearchEngine should only run behind the connected-provider gate",
            Regex("""if\s*\(\s*includeConnectedProviders\s*\)[\s\S]{0,800}searchEngine\.search""")
                .containsMatchIn(viewModel)
        )
        assertTrue(
            "Search query and viewport must be retained by the activity-scoped view model",
            viewModel.contains("val query: StateFlow<String>") &&
                viewModel.contains("val scrollPosition: StateFlow<Pair<Int, Int>>") &&
                (source.contains("rememberLazyListState(") || source.contains("rememberLazyGridState(")) &&
                source.contains("viewModel.updateScrollPosition(index, offset)")
        )
    }

    @Test
    fun `mobile Home waits for extension catalog ingestion before reloading rows`() {
        val source = try {
            readSharedSource("BaseHomeViewModel.kt")
        } catch (e: Exception) {
            readMobileSource("ui/HomeViewModel.kt")
        }
        val useCaseSource = try {
            readSharedSource("ObserveHomeDataUseCase.kt")
        } catch (e: Exception) {
            readMobileSource("domain/ObserveHomeDataUseCase.kt")
        }
        val screen = readMobileSource("ui/HomeScreen.kt")

        assertTrue(source.contains("ExtensionRepository.ensureDiscoveryCatalogHomeRows()"))
        assertTrue(source.contains("forceRefresh = true"))
        assertTrue(useCaseSource.contains("IPTVRepository.channels"))
        assertTrue(source.contains("IPTVRepository.getLiveChannelHomeRow()"))
        assertFalse(screen.contains("viewModel.loadHomeRows()"))
    }

    @Test
    fun `mobile Xtream sync is not launched from a disappearing Compose scope`() {
        val source = readMobileSource("ui/SettingsScreens.kt")

        assertTrue(source.contains("IPTVRepository.startXtreamProviderSync(provider.id)"))
        assertFalse(source.contains("IPTVRepository.syncXtreamProvider(provider.id)"))
    }

    @Test
    fun `mobile Live TV does not spin forever when no channels are loaded`() {
        val source = readMobileSource("ui/LiveTvScreen.kt")

        assertTrue(source.contains("uiState.isLoading"))
        assertTrue(source.contains("uiState.syncWarnings"))
    }

    private fun readSharedSource(relativePath: String): String {
        val candidates = listOf(
            File("feature/iptv/src/main/kotlin/com/example/calmsource/feature/iptv/$relativePath"),
            File("../feature/iptv/src/main/kotlin/com/example/calmsource/feature/iptv/$relativePath"),
            File("../../feature/iptv/src/main/kotlin/com/example/calmsource/feature/iptv/$relativePath")
        )
        val sourceFile = candidates.firstOrNull(File::isFile)
            ?: error("Could not find shared source file: $relativePath")
        return sourceFile.readText()
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

    private fun readMobileSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/com/example/calmsource/$relativePath"),
            File("app-mobile/src/main/java/com/example/calmsource/$relativePath"),
            File("../../app-mobile/src/main/java/com/example/calmsource/$relativePath")
        )
        val sourceFile = candidates.firstOrNull(File::isFile)
            ?: error("Could not find mobile source file: $relativePath")
        return sourceFile.readText()
    }
}

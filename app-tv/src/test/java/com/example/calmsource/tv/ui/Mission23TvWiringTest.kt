package com.example.calmsource.tv.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mission23TvWiringTest {

    @Test
    fun `TV exposes Library from the navigation rail`() {
        val source = readTvSource("TvMainActivity.kt")

        assertTrue(Regex("""data\s+object\s+Library\s*:\s*TvScreen""").containsMatchIn(source))
        assertTrue(Regex("""TvScreen\.Library\s*->\s*TvLibraryScreen\s*\(""").containsMatchIn(source))
        assertTrue(
            "The TV navigation rail must expose Library",
            Regex("TvNavRailItem\\s*\\(\\s*Icons\\.Default\\.Favorite\\s*,\\s*\"Library\"").containsMatchIn(source)
        )
    }

    @Test
    fun `TV Home uses real repositories and has no FakeData rows`() {
        val source = readTvSource("ui/TvHomeScreen.kt")

        assertFalse(
            "TV Home must not render production rows from FakeData",
            Regex("""\bFakeData\s*\.""").containsMatchIn(source)
        )
        assertTrue(source.contains("IPTVRepository.getLiveChannels"))
        assertTrue(source.contains("ExtensionRepository.extensions"))
    }

    @Test
    fun `TV search supplies safe user memory hooks`() {
        val source = readTvSource("ui/TvSearchScreen.kt")
        val viewModel = try {
            readSharedSearchSource("BaseSearchViewModel.kt")
        } catch (e: Exception) {
            readTvSource("ui/TvSearchViewModel.kt")
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
    fun `TV search keystrokes stay local until explicit submit`() {
        val source = readTvSource("ui/TvSearchScreen.kt")
        val viewModel = try {
            readSharedSearchSource("BaseSearchViewModel.kt")
        } catch (e: Exception) {
            readTvSource("ui/TvSearchViewModel.kt")
        }
        val components = readTvSource("ui/TvUiComponents.kt")

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
            "TV text fields should expose a remote-friendly explicit search key action",
            source.contains("onSearchAction = { submitQuery() }") &&
                components.contains("KEYCODE_SEARCH") &&
                components.contains("KEYCODE_ENTER")
        )
        assertTrue(
            "TvSearchViewModel.search must run the local-only path",
            viewModel.contains("fun search(query: String)") &&
                viewModel.contains("runSearch(query = query, includeConnectedProviders = false")
        )
        assertTrue(
            "TvSearchViewModel.submitSearch must be the connected-provider path",
            viewModel.contains("fun submitSearch(query: String)") &&
                viewModel.contains("runSearch(query = query, includeConnectedProviders = true")
        )
        assertTrue(
            "UniversalSearchEngine should only run behind the connected-provider gate",
            Regex("""if\s*\(\s*includeConnectedProviders\s*\)[\s\S]{0,800}searchEngine\.search""")
                .containsMatchIn(viewModel)
        )
        assertTrue(
            "TV search query and viewport must survive details navigation",
            viewModel.contains("val query: StateFlow<String>") &&
                viewModel.contains("val scrollPosition: StateFlow<Pair<Int, Int>>") &&
                source.contains("rememberLazyListState(") &&
                source.contains("viewModel.updateScrollPosition(index, offset)")
        )
    }

    @Test
    fun `TV Home waits for extension catalog ingestion before reloading rows`() {
        val source = try {
            readSharedSource("BaseHomeViewModel.kt")
        } catch (e: Exception) {
            readTvSource("ui/TvHomeViewModel.kt")
        }
        val useCaseSource = try {
            readSharedSource("ObserveHomeDataUseCase.kt")
        } catch (e: Exception) {
            readTvSource("domain/ObserveHomeDataUseCase.kt")
        }
        val screen = readTvSource("ui/TvHomeScreen.kt")

        assertTrue(source.contains("ExtensionRepository.ensureDiscoveryCatalogHomeRows()"))
        assertTrue(source.contains("forceRefresh = true"))
        assertTrue(useCaseSource.contains("IPTVRepository.channels"))
        assertTrue(source.contains("IPTVRepository.getLiveChannelHomeRow()"))
        assertFalse(screen.contains("viewModel.loadHomeRows()"))
    }

    @Test
    fun `TV Library requests initial content focus`() {
        val source = readTvSource("ui/TvLibraryScreen.kt")

        assertTrue(source.contains("LaunchedEffect(continueWatching.isNotEmpty())"))
        assertTrue(source.contains("continueFocus.requestFocus()"))
    }

    @Test
    fun `TV Xtream sync is not launched from a disappearing Compose scope`() {
        val source = readTvSource("ui/TvIptvSettingsSection.kt")

        assertTrue(source.contains("IPTVRepository.startXtreamProviderSync(provider.id)"))
        assertFalse(source.contains("IPTVRepository.syncXtreamProvider(provider.id)"))
    }

    @Test
    fun `TV Live guide does not spin forever when no channels are loaded`() {
        val source = readTvSource("ui/TvLiveGuideScreen.kt")

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

    private fun readTvSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/com/example/calmsource/tv/$relativePath"),
            File("app-tv/src/main/java/com/example/calmsource/tv/$relativePath"),
            File("../../app-tv/src/main/java/com/example/calmsource/tv/$relativePath")
        )
        val sourceFile = candidates.firstOrNull(File::isFile)
            ?: error("Could not find TV source file: $relativePath")
        return sourceFile.readText()
    }
}

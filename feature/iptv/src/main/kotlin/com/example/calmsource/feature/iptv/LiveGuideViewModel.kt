package com.example.calmsource.feature.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.ProviderSyncStatus
import com.example.calmsource.core.model.TestEnvironment
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flowOn

data class LiveGuideUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val isEnrichingFacets: Boolean = false,
    val allChannels: List<Channel> = emptyList(),
    val filteredChannels: List<Channel> = emptyList(),
    val categories: List<String> = listOf("All"),
    val languages: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val iptvChannelById: Map<String, com.example.calmsource.core.model.IPTVChannel> = emptyMap(),
    val languageById: Map<String, String> = emptyMap(),
    val countryById: Map<String, String> = emptyMap(),
    val sectionById: Map<String, IptvContentSection> = emptyMap(),
    val searchQuery: String = "",
    val selectedView: IptvLiveGuideView = IptvLiveGuideView.LIVE,
    val selectedLanguage: String = IptvLiveGuideFilters.ALL_LANGUAGES,
    val selectedCountry: String = IptvLiveGuideFilters.ALL_REGIONS,
    val sortMode: IptvLiveGuideSort = IptvLiveGuideSort.RECOMMENDED,
    val selectedCategory: String = "All",
    val syncWarnings: List<String> = emptyList(),
    val reloadToken: Int = 0
)

@OptIn(FlowPreview::class)
class LiveGuideViewModel : ViewModel() {

    private data class MemoryHints(
        val favoriteKeys: Set<String> = emptySet(),
        val recentOrder: Map<String, Int> = emptyMap()
    )

    private data class FiltersState(
        val searchQuery: String = "",
        val selectedView: IptvLiveGuideView = IptvLiveGuideView.LIVE,
        val selectedLanguage: String = IptvLiveGuideFilters.ALL_LANGUAGES,
        val selectedCountry: String = IptvLiveGuideFilters.ALL_REGIONS,
        val sortMode: IptvLiveGuideSort = IptvLiveGuideSort.RECOMMENDED,
        val selectedCategory: String = "All",
        val reloadToken: Int = 0
    )

    private val memoryHints = MutableStateFlow(MemoryHints())
    private val filtersState = MutableStateFlow(FiltersState())

    fun updateMemoryHints(favoriteKeys: Set<String>, recentOrder: Map<String, Int>) {
        memoryHints.value = MemoryHints(favoriteKeys, recentOrder)
    }

    fun setSearchQuery(value: String) {
        filtersState.update { it.copy(searchQuery = value) }
    }

    fun setSelectedView(value: IptvLiveGuideView) {
        filtersState.update { it.copy(selectedView = value) }
    }

    fun setSelectedLanguage(value: String) {
        filtersState.update { it.copy(selectedLanguage = value) }
    }

    fun setSelectedCountry(value: String) {
        filtersState.update { it.copy(selectedCountry = value) }
    }

    fun setSortMode(value: IptvLiveGuideSort) {
        filtersState.update { it.copy(sortMode = value) }
    }

    fun setSelectedCategory(value: String) {
        filtersState.update { it.copy(selectedCategory = value) }
    }

    fun clearFilters() {
        filtersState.value = FiltersState()
    }

    fun bumpReloadToken() {
        filtersState.update { it.copy(reloadToken = it.reloadToken + 1) }
    }

    fun retrySync() {
        bumpReloadToken()
        IPTVRepository.retryFailedProviderSyncs()
    }

    private val guideContext = combine(
        IPTVRepository.liveGuideIndex,
        IPTVRepository.channelsReady,
        IPTVRepository.syncStates,
        IPTVRepository.providers
    ) { index, channelsReady, syncStates, providers ->
        val liveChannelCount = IPTVRepository.getLiveChannelCount()
        val isProviderSyncing = syncStates.values.any { it.status == ProviderSyncStatus.SYNCING }
        val isLoading = IptvLiveGuideFilters.isGuideLoading(
            liveChannelCount = liveChannelCount,
            channelsReady = channelsReady,
            isProviderSyncing = isProviderSyncing,
            hasProviders = providers.isNotEmpty()
        )
        
        val repoChannels = if (index.liveChannels.isNotEmpty()) {
            index.liveChannels
        } else if (liveChannelCount > 0) {
            IPTVRepository.getLiveChannels()
        } else {
            emptyList()
        }

        val progressiveChannels = if (index.uiChannels.isNotEmpty()) {
            index.uiChannels
        } else if (repoChannels.isNotEmpty()) {
            IptvLiveGuideIndex.lightweightUiChannels(repoChannels)
        } else {
            emptyList()
        }

        val activeIndex = if (index.uiChannels.isNotEmpty()) {
            index
        } else if (progressiveChannels.isNotEmpty()) {
            IptvLiveGuideIndex.buildFromChannels(
                channels = repoChannels,
                lightweight = true
            )
        } else {
            index
        }
        GuideContext(
            isLoading = isLoading,
            isSyncing = isProviderSyncing,
            isEnrichingFacets = liveChannelCount > 0 && index.uiChannels.isEmpty(),
            allChannels = progressiveChannels,
            categories = activeIndex.categories,
            languages = activeIndex.languages,
            countries = activeIndex.countries,
            iptvChannelById = activeIndex.iptvChannelById,
            languageById = activeIndex.languageById,
            countryById = activeIndex.countryById,
            sectionById = activeIndex.sectionById,
            syncWarnings = syncStates.values.flatMap { state ->
                listOfNotNull(
                    state.error?.takeIf(String::isNotBlank),
                    state.warning?.takeIf(String::isNotBlank)
                )
            }.distinct()
        )
    }

    private val debouncedSearchQuery = filtersState
        .map { it.searchQuery }
        .distinctUntilChanged()
        .let { if (TestEnvironment.isTest) it else it.debounce(150L) }

    // Debounce category selection so rapid D-pad scrolling through the sidebar
    // doesn't re-filter the entire channel list on every focus change.
    private val debouncedCategory = filtersState
        .map { it.selectedCategory }
        .distinctUntilChanged()
        .let { if (TestEnvironment.isTest) it else it.debounce(250L) }

    private val filterInputs = combine(
        filtersState,
        debouncedSearchQuery,
        debouncedCategory,
        memoryHints
    ) { state, debouncedQuery, debouncedCat, hints ->
        FilterInputs(
            searchQuery = debouncedQuery,
            selectedView = state.selectedView,
            selectedLanguage = state.selectedLanguage,
            selectedCountry = state.selectedCountry,
            sortMode = state.sortMode,
            selectedCategory = debouncedCat,
            memoryHints = hints,
            reloadToken = state.reloadToken
        )
    }.distinctUntilChanged()

    val uiState: StateFlow<LiveGuideUiState> = combine(
        guideContext,
        filterInputs
    ) { context, filters ->
        val categories = context.categories
        val activeCategory = if (filters.selectedCategory in categories) {
            filters.selectedCategory
        } else {
            "All"
        }
        val filtered = if (context.isLoading || context.allChannels.isEmpty()) {
            emptyList()
        } else {
            IptvLiveGuideFilters.filterAndSort(
                channels = context.allChannels,
                activeCategory = activeCategory,
                query = filters.searchQuery,
                selectedView = filters.selectedView,
                selectedLanguage = filters.selectedLanguage,
                selectedCountry = filters.selectedCountry,
                sortMode = filters.sortMode,
                iptvChannelById = context.iptvChannelById,
                languageById = context.languageById,
                countryById = context.countryById,
                sectionById = context.sectionById,
                favoriteKeys = filters.memoryHints.favoriteKeys,
                recentOrder = filters.memoryHints.recentOrder
            )
        }
        LiveGuideUiState(
            isLoading = context.isLoading,
            isSyncing = context.isSyncing,
            isEnrichingFacets = context.isEnrichingFacets,
            allChannels = context.allChannels,
            filteredChannels = filtered,
            categories = categories,
            languages = context.languages,
            countries = context.countries,
            iptvChannelById = context.iptvChannelById,
            languageById = context.languageById,
            countryById = context.countryById,
            sectionById = context.sectionById,
            searchQuery = filters.searchQuery,
            selectedView = filters.selectedView,
            selectedLanguage = filters.selectedLanguage,
            selectedCountry = filters.selectedCountry,
            sortMode = filters.sortMode,
            selectedCategory = activeCategory,
            syncWarnings = context.syncWarnings,
            reloadToken = filters.reloadToken
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = LiveGuideUiState()
    )

    private data class GuideContext(
        val isLoading: Boolean,
        val isSyncing: Boolean,
        val isEnrichingFacets: Boolean,
        val allChannels: List<Channel>,
        val categories: List<String>,
        val languages: List<String>,
        val countries: List<String>,
        val iptvChannelById: Map<String, com.example.calmsource.core.model.IPTVChannel>,
        val languageById: Map<String, String>,
        val countryById: Map<String, String>,
        val sectionById: Map<String, IptvContentSection>,
        val syncWarnings: List<String>
    )

    private data class FilterInputs(
        val searchQuery: String,
        val selectedView: IptvLiveGuideView,
        val selectedLanguage: String,
        val selectedCountry: String,
        val sortMode: IptvLiveGuideSort,
        val selectedCategory: String,
        val memoryHints: MemoryHints,
        val reloadToken: Int
    )
}

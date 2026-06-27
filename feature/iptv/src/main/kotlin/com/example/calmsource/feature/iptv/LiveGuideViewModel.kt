package com.example.calmsource.feature.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.ProviderSyncStatus
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

    private val memoryHints = MutableStateFlow(MemoryHints())
    private val searchQuery = MutableStateFlow("")
    private val selectedView = MutableStateFlow(IptvLiveGuideView.LIVE)
    private val selectedLanguage = MutableStateFlow(IptvLiveGuideFilters.ALL_LANGUAGES)
    private val selectedCountry = MutableStateFlow(IptvLiveGuideFilters.ALL_REGIONS)
    private val sortMode = MutableStateFlow(IptvLiveGuideSort.RECOMMENDED)
    private val selectedCategory = MutableStateFlow("All")
    private val reloadToken = MutableStateFlow(0)

    fun updateMemoryHints(favoriteKeys: Set<String>, recentOrder: Map<String, Int>) {
        memoryHints.value = MemoryHints(favoriteKeys, recentOrder)
    }

    fun setSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun setSelectedView(value: IptvLiveGuideView) {
        selectedView.value = value
    }

    fun setSelectedLanguage(value: String) {
        selectedLanguage.value = value
    }

    fun setSelectedCountry(value: String) {
        selectedCountry.value = value
    }

    fun setSortMode(value: IptvLiveGuideSort) {
        sortMode.value = value
    }

    fun setSelectedCategory(value: String) {
        selectedCategory.value = value
    }

    fun clearFilters() {
        searchQuery.value = ""
        selectedView.value = IptvLiveGuideView.LIVE
        selectedLanguage.value = IptvLiveGuideFilters.ALL_LANGUAGES
        selectedCountry.value = IptvLiveGuideFilters.ALL_REGIONS
        sortMode.value = IptvLiveGuideSort.RECOMMENDED
        selectedCategory.value = "All"
    }

    fun bumpReloadToken() {
        reloadToken.update { it + 1 }
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
        val progressiveChannels = if (index.uiChannels.isNotEmpty()) {
            index.uiChannels
        } else if (liveChannelCount > 0) {
            IptvLiveGuideIndex.lightweightUiChannels(
                if (index.liveChannels.isNotEmpty()) index.liveChannels else IPTVRepository.getLiveChannels()
            )
        } else {
            emptyList()
        }
        val activeIndex = if (index.uiChannels.isNotEmpty()) {
            index
        } else if (progressiveChannels.isNotEmpty()) {
            IptvLiveGuideIndex.buildFromChannels(
                channels = if (index.liveChannels.isNotEmpty()) {
                    index.liveChannels
                } else {
                    IPTVRepository.getLiveChannels()
                },
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
            syncWarnings = syncStates.values.mapNotNull { it.warning?.takeIf(String::isNotBlank) }.distinct()
        )
    }

    private val filterInputs = combine(
        combine(searchQuery, selectedView, selectedLanguage) { query, view, language ->
            Triple(query, view, language)
        },
        combine(selectedCountry, sortMode, selectedCategory) { country, sort, category ->
            Triple(country, sort, category)
        },
        combine(memoryHints, reloadToken) { hints, token ->
            hints to token
        }
    ) { queryViewLanguage, countrySortCategory, hintsToken ->
        FilterInputs(
            searchQuery = queryViewLanguage.first,
            selectedView = queryViewLanguage.second,
            selectedLanguage = queryViewLanguage.third,
            selectedCountry = countrySortCategory.first,
            sortMode = countrySortCategory.second,
            selectedCategory = countrySortCategory.third,
            memoryHints = hintsToken.first,
            reloadToken = hintsToken.second
        )
    }.debounce(150L).distinctUntilChanged()

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
    }.stateIn(
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

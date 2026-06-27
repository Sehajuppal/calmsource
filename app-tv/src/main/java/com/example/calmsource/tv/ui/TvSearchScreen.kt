package com.example.calmsource.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.MediaType
import com.example.calmsource.feature.search.SearchDisplayResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged

import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun TvSearchScreen(
    initialQuery: String = "",
    onInitialQueryConsumed: () -> Unit = {},
    onMediaClick: (MediaItem) -> Unit,
    onChannelClick: (String) -> Unit = {},
    viewModel: TvSearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val scrollPosition by viewModel.scrollPosition.collectAsState()
    val sections = remember(searchResults) { searchResults.toTvDiscoverySections() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPosition.first,
        initialFirstVisibleItemScrollOffset = scrollPosition.second
    )

    fun submitQuery() {
        val submittedQuery = query.trim()
        if (submittedQuery.isNotEmpty()) {
            viewModel.submitSearch(submittedQuery)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            if (initialQuery != query) {
                viewModel.submitSearch(initialQuery)
            }
            onInitialQueryConsumed()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                viewModel.updateScrollPosition(index, offset)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(24.dp)
    ) {
        Text(
            text = "Search",
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            color = TvColors.TextMain,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        TvTextField(
            value = query,
            onValueChange = viewModel::search,
            placeholder = { Text("Search movies, series, and live channels...", color = TvColors.TextSub) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitQuery() }),
            onSearchAction = { submitQuery() },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        TvSearchFilterBar(
            filters = filters,
            onSelectType = { viewModel.setFilter("type", it) },
            onSelectGenre = { viewModel.setFilter("genre", it) }
        )

        when {
            isSearching && searchResults.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TvColors.BorderFocused)
                }
            }
            searchResults.isEmpty() && query.isNotBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matches found in your catalogs or connected sources.",
                        color = TvColors.TextSub,
                        fontSize = 16.sp
                    )
                }
            }
            query.isBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Enter a search term to find movies, shows, and channels.",
                        color = TvColors.TextSub,
                        fontSize = 16.sp
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    sections.forEach { section ->
                        item(key = section.key) {
                            Text(
                                text = section.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TvColors.BorderFocused,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        itemsIndexed(
                            section.results,
                            key = { index, result -> tvSearchResultLazyKey(section.key, index, result) }
                        ) { _, result ->
                            TvDiscoverySearchResultItem(
                                result = result,
                                onClick = {
                                    if (result.type == "channel") {
                                        onChannelClick(result.id)
                                    } else {
                                        val selectedQuery = query.trim()
                                        if (selectedQuery.isNotEmpty()) {
                                            scope.launch {
                                                viewModel.recordSearchInterest(selectedQuery, result)
                                            }
                                        }
                                        onMediaClick(result.toTvMediaItem())
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private val TV_SEARCH_TYPE_FILTERS = listOf(
    "All" to null,
    "Movies" to "movie",
    "Shows" to "series",
    "Channels" to "channel"
)

private val TV_SEARCH_GENRE_FILTERS = listOf(
    "Action", "Comedy", "Drama", "Horror", "Sci-Fi", "Thriller", "Romance", "Animation", "Documentary"
)

@Composable
private fun TvSearchFilterBar(
    filters: Map<String, String>,
    onSelectType: (String?) -> Unit,
    onSelectGenre: (String?) -> Unit
) {
    val activeType = filters["type"]
    val activeGenre = filters["genre"]
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        TV_SEARCH_TYPE_FILTERS.forEach { (label, value) ->
            val selected = activeType == value || (value == null && activeType == null)
            TvFilterChip(label = label, selected = selected) { onSelectType(value) }
        }
        TV_SEARCH_GENRE_FILTERS.forEach { genre ->
            val selected = activeGenre.equals(genre, ignoreCase = true)
            TvFilterChip(label = genre, selected = selected) {
                onSelectGenre(if (selected) null else genre)
            }
        }
    }
}

@Composable
private fun TvFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TvFocusCard(onClick = onClick) { isFocused ->
        Text(
            text = label,
            color = when {
                selected -> TvColors.BorderFocused
                isFocused -> TvColors.TextMain
                else -> TvColors.TextSub
            },
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

private data class TvDiscoverySearchSection(
    val key: String,
    val title: String,
    val results: List<SearchDisplayResult>
)

private fun List<SearchDisplayResult>.toTvDiscoverySections(): List<TvDiscoverySearchSection> {
    val orderedTypes = listOf("movie", "series", "episode", "channel")
    val grouped = groupBy { it.type.lowercase() }
    return buildList {
        orderedTypes.forEach { type ->
            grouped[type]?.takeIf { it.isNotEmpty() }?.let { results ->
                add(TvDiscoverySearchSection(type, type.toTvSearchSectionTitle(), results))
            }
        }
        grouped
            .filterKeys { it !in orderedTypes }
            .toSortedMap()
            .forEach { (type, results) ->
                add(TvDiscoverySearchSection(type, type.toTvSearchSectionTitle(), results))
            }
    }
}

private fun String.toTvSearchSectionTitle(): String = when (this) {
    "movie" -> "Movies"
    "series" -> "Series"
    "episode" -> "Episodes"
    "channel" -> "Live Channels"
    else -> replaceFirstChar { it.uppercase() }
}

private fun tvSearchResultLazyKey(
    sectionKey: String,
    index: Int,
    result: SearchDisplayResult
): String = "$sectionKey-$index-${result.type}-${result.id}"

private fun String.toTvItemTypeLabel(): String = when (this) {
    "movie" -> "Movie"
    "series" -> "Series"
    "episode" -> "Episode"
    "channel" -> "Live Channel"
    else -> replaceFirstChar { it.uppercase() }
}

private fun SearchDisplayResult.toTvMediaItem(): MediaItem {
    return MediaItem(
        id = id,
        title = title,
        type = if (type == "movie") MediaType.MOVIE else MediaType.SHOW,
        overview = subtitle,
        posterUrl = posterUrl,
        externalIds = externalIds
    )
}

@Composable
fun TvDiscoverySearchResultItem(
    result: SearchDisplayResult,
    onClick: () -> Unit
) {
    TvFocusCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) { isFocused ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = result.posterUrl,
                contentDescription = "Artwork for ${result.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp, 75.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x1AFFFFFF))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TvColors.TextMain,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.subtitle ?: result.type.toTvItemTypeLabel(),
                    fontSize = 13.sp,
                    color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    TvSearchSourceChip(result.sourceLabel)
                    if (result.hasPlayableSource) {
                        TvSearchSourceChip("Streams ready")
                    }
                }

                AnimatedVisibility(visible = isFocused) {
                    Text(
                        text = if (result.type == "channel") {
                            "Press OK to watch this channel"
                        } else if (result.hasPlayableSource) {
                            "Playable source available"
                        } else {
                            "Press OK to open details"
                        },
                        fontSize = 12.sp,
                        color = TvColors.BorderFocused,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSearchSourceChip(label: String) {
    Text(
        text = label.ifBlank { "local" },
        color = TvColors.BorderFocused,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x1AFFFFFF))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

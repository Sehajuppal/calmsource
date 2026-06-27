package com.example.calmsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String = "",
    onInitialQueryConsumed: () -> Unit = {},
    onMediaClick: (MediaItem) -> Unit,
    onChannelClick: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val scrollPosition by viewModel.scrollPosition.collectAsState()
    val sections = remember(searchResults) { searchResults.toDiscoverySections() }
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
            .background(AppColors.Background)
            .padding(16.dp)
    ) {
        Text(
            text = "Search",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextMain,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        TextField(
            value = query,
            onValueChange = viewModel::search,
            placeholder = { Text("Search movies, shows, and live channels...", color = AppColors.TextSub) },
            trailingIcon = {
                IconButton(onClick = { submitQuery() }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search connected sources",
                        tint = AppColors.TextSub
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitQuery() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AppColors.Surface,
                unfocusedContainerColor = AppColors.Surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = AppColors.TextMain,
                unfocusedTextColor = AppColors.TextMain
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        SearchFilterBar(
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
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            }
            searchResults.isEmpty() && query.isNotBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No matches found in your catalogs or connected sources.", color = AppColors.TextSub)
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
                        text = "Search movies, shows, and channels across your catalogs.",
                        color = AppColors.TextSub
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    sections.forEach { section ->
                        item(key = section.key) {
                            Text(
                                text = section.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        itemsIndexed(
                            section.results,
                            key = { index, result -> searchResultLazyKey(section.key, index, result) }
                        ) { _, result ->
                            DiscoverySearchResultItem(
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
                                        onMediaClick(result.toMediaItem())
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

private val SEARCH_TYPE_FILTERS = listOf(
    "All" to null,
    "Movies" to "movie",
    "Shows" to "series",
    "Channels" to "channel"
)

private val SEARCH_GENRE_FILTERS = listOf(
    "Action", "Comedy", "Drama", "Horror", "Sci-Fi", "Thriller", "Romance", "Animation", "Documentary"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterBar(
    filters: Map<String, String>,
    onSelectType: (String?) -> Unit,
    onSelectGenre: (String?) -> Unit
) {
    val activeType = filters["type"]
    val activeGenre = filters["genre"]
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 12.dp)
    ) {
        SEARCH_TYPE_FILTERS.forEach { (label, value) ->
            val selected = activeType == value || (value == null && activeType == null)
            FilterChip(
                selected = selected,
                onClick = { onSelectType(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Primary,
                    selectedLabelColor = Color.White
                )
            )
        }
        SEARCH_GENRE_FILTERS.forEach { genre ->
            val selected = activeGenre.equals(genre, ignoreCase = true)
            FilterChip(
                selected = selected,
                onClick = { onSelectGenre(if (selected) null else genre) },
                label = { Text(genre) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Secondary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

private data class DiscoverySearchSection(
    val key: String,
    val title: String,
    val results: List<SearchDisplayResult>
)

private fun List<SearchDisplayResult>.toDiscoverySections(): List<DiscoverySearchSection> {
    val orderedTypes = listOf("movie", "series", "episode", "channel")
    val grouped = groupBy { it.type.lowercase() }
    return buildList {
        orderedTypes.forEach { type ->
            grouped[type]?.takeIf { it.isNotEmpty() }?.let { results ->
                add(DiscoverySearchSection(type, type.toSearchSectionTitle(), results))
            }
        }
        grouped
            .filterKeys { it !in orderedTypes }
            .toSortedMap()
            .forEach { (type, results) ->
                add(DiscoverySearchSection(type, type.toSearchSectionTitle(), results))
            }
    }
}

private fun String.toSearchSectionTitle(): String = when (this) {
    "movie" -> "Movies"
    "series" -> "Series"
    "episode" -> "Episodes"
    "channel" -> "Live Channels"
    else -> replaceFirstChar { it.uppercase() }
}

private fun searchResultLazyKey(
    sectionKey: String,
    index: Int,
    result: SearchDisplayResult
): String = "$sectionKey-$index-${result.type}-${result.id}"

private fun String.toSearchItemTypeLabel(): String = when (this) {
    "movie" -> "Movie"
    "series" -> "Series"
    "episode" -> "Episode"
    "channel" -> "Live Channel"
    else -> replaceFirstChar { it.uppercase() }
}

private fun SearchDisplayResult.toMediaItem(): MediaItem {
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
fun DiscoverySearchResultItem(
    result: SearchDisplayResult,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = result.posterUrl,
                contentDescription = "Artwork for ${result.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp, 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1AFFFFFF))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextMain,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.subtitle ?: result.type.toSearchItemTypeLabel(),
                    fontSize = 12.sp,
                    color = AppColors.TextSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    SearchSourceChip(result.sourceLabel)
                    if (result.hasPlayableSource) {
                        SearchSourceChip("Streams ready")
                    }
                }

                Text(
                    text = if (result.hasPlayableSource) "Playable source available" else "Catalog match",
                    fontSize = 11.sp,
                    color = AppColors.Secondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchSourceChip(label: String) {
    Text(
        text = label.ifBlank { "local" },
        color = AppColors.Primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x1AFFFFFF))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.MediaType
import com.example.calmsource.feature.search.SearchDisplayResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged

import androidx.compose.ui.res.stringResource
import com.example.calmsource.core.ui.R as CoreUiR
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.calmsource.core.ui.components.GlassSurface
import com.example.calmsource.core.ui.components.LumenCard
import com.example.calmsource.core.ui.components.ChipRow
import com.example.calmsource.core.ui.components.LumenSkeleton
import com.example.calmsource.core.ui.components.LumenEmptyState
import com.example.calmsource.core.ui.components.LumenErrorState
import com.example.calmsource.core.ui.components.PosterCard
import com.example.calmsource.core.ui.components.LumenHorizontalRowFade

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String = "",
    onInitialQueryConsumed: () -> Unit = {},
    onMediaClick: (MediaItem) -> Unit,
    onChannelClick: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val t = LocalLumenTokens.current
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val filters by viewModel.filters.collectAsState()
    // Read scroll position once for initial composition; snapshotFlow below
    // pushes updates to the ViewModel. Collecting it as State would create a
    // recomposition loop on every scroll pixel.
    val scrollPosition = remember { viewModel.scrollPosition.value }
    
    val titlesGroup = remember(searchResults) { searchResults.filter { it.type != "channel" } }
    val channelsGroup = remember(searchResults) { searchResults.filter { it.type == "channel" } }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyGridState(
        initialFirstVisibleItemIndex = scrollPosition.first,
        initialFirstVisibleItemScrollOffset = scrollPosition.second
    )
    val focusRequester = remember { FocusRequester() }
    var requestInitialFocus by remember { mutableStateOf(initialQuery.isNotBlank()) }

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

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            focusRequester.requestFocus()
            requestInitialFocus = false
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
            .background(t.colors.background)
            .statusBarsPadding()
            .padding(LumenTokens.Space.md)
    ) {
        Text(
            text = stringResource(CoreUiR.string.search_title),
            style = LumenType.H1.toTextStyle(),
            color = t.colors.foreground,
            modifier = Modifier.padding(bottom = LumenTokens.Space.md)
        )

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = LumenTokens.Space.s5),
            shape = LumenTokens.Shape.md,
        ) {
            TextField(
                value = query,
                onValueChange = viewModel::search,
                placeholder = { Text(stringResource(CoreUiR.string.search_placeholder), color = t.colors.mutedForeground) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = t.colors.mutedForeground,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = t.colors.mutedForeground,
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitQuery() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = t.colors.foreground,
                    unfocusedTextColor = t.colors.foreground,
                    cursorColor = t.colors.brand,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        if (query.isNotEmpty() || filters.isNotEmpty()) {
            SearchFilterBar(
                filters = filters,
                onSelectType = { viewModel.setFilter("type", it) },
                onSelectGenre = { viewModel.setFilter("genre", it) }
            )
        }

        when {
            isSearching && searchResults.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(LumenTokens.Space.md),
                    verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.md)
                ) {
                    repeat(3) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LumenSkeleton(modifier = Modifier.weight(1f).height(LumenLayout.epgMinBlockWidth))
                            LumenSkeleton(modifier = Modifier.weight(1f).height(LumenLayout.epgMinBlockWidth))
                        }
                    }
                }
            }
            searchResults.isEmpty() && (query.isNotEmpty() || filters.isNotEmpty()) && searchError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    LumenErrorState(
                        title = "Search failed",
                        body = searchError ?: "Check your connection and try again.",
                        onRetry = { viewModel.submitSearch(query) },
                    )
                }
            }
            searchResults.isEmpty() && (query.isNotEmpty() || filters.isNotEmpty()) -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    LumenEmptyState(
                        title = if (query.isNotEmpty()) "Nothing matched '$query'" else "Nothing matched the active filters",
                        body = "Try checking the spelling or look for different keywords.",
                        icon = Icons.Default.Search
                    )
                }
            }
            query.isEmpty() && filters.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LumenEmptyState(
                        title = "What are you in the mood for?",
                        body = "Try a title, genre, live channel, or a feeling like “funny and light.”",
                        icon = Icons.Default.Search,
                        modifier = Modifier.weight(1f)
                    )
                    val suggestedTags = listOf("thriller", "drama", "sci-fi", "comedy", "documentary", "news", "sports")
                    Text(
                        text = "Start with a mood",
                        fontSize = LumenType.size13,
                        fontWeight = FontWeight.Bold,
                        color = t.colors.mutedForeground,
                        modifier = Modifier.padding(horizontal = LumenTokens.Space.md, vertical = LumenTokens.Space.sm)
                    )
                    ChipRow(
                        items = suggestedTags,
                        selected = null,
                        onSelect = { tag ->
                            viewModel.search(tag)
                            viewModel.submitSearch(tag)
                        },
                        modifier = Modifier.padding(start = LumenTokens.Space.md, end = LumenTokens.Space.md, bottom = LumenTokens.Space.md)
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    state = listState,
                    columns = GridCells.Adaptive(minSize = LumenLayout.epgMinBlockWidth),
                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5),
                    verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = LumenTokens.Space.sectionGapMobile,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (titlesGroup.isNotEmpty()) {
                        item(key = "header-titles", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "TITLES · ${titlesGroup.size}",
                                fontSize = LumenType.size11,
                                fontWeight = FontWeight.Bold,
                                color = t.colors.mutedForeground,
                                letterSpacing = LumenType.line1_6,
                                modifier = Modifier.padding(top = LumenTokens.Space.md, bottom = LumenTokens.Space.sm)
                            )
                        }
                        gridItemsIndexed(
                            titlesGroup,
                            key = { index, result -> searchResultLazyKey("titles", index, result) }
                        ) { _, result ->
                            VisualSearchResultItem(
                                result = result,
                                onClick = {
                                    val selectedQuery = query.trim()
                                    if (selectedQuery.isNotEmpty()) {
                                        scope.launch {
                                            viewModel.recordSearchInterest(selectedQuery, result)
                                        }
                                    }
                                    onMediaClick(result.toMediaItem())
                                }
                            )
                        }
                    }
                    if (channelsGroup.isNotEmpty()) {
                        item(key = "header-channels", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "LIVE CHANNELS · ${channelsGroup.size}",
                                fontSize = LumenType.size11,
                                fontWeight = FontWeight.Bold,
                                color = t.colors.mutedForeground,
                                letterSpacing = LumenType.line1_6,
                                modifier = Modifier.padding(top = LumenTokens.Space.md, bottom = LumenTokens.Space.sm)
                            )
                        }
                        gridItemsIndexed(
                            channelsGroup,
                            key = { index, result -> searchResultLazyKey("channels", index, result) }
                        ) { _, result ->
                            VisualSearchResultItem(
                                result = result,
                                onClick = {
                                    onChannelClick(result.id)
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
    val t = LocalLumenTokens.current
    val activeType = filters["type"]
    val activeGenre = filters["genre"]
    Row(
        horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = LumenTokens.Space.s5)
    ) {
        SEARCH_TYPE_FILTERS.forEach { (label, value) ->
            val selected = activeType == value || (value == null && activeType == null)
            FilterChip(
                selected = selected,
                onClick = { onSelectType(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = t.colors.card,
                    selectedContainerColor = t.colors.brand,
                    labelColor = t.colors.foreground,
                    selectedLabelColor = t.colors.brandForeground
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
                    containerColor = t.colors.card,
                    selectedContainerColor = t.colors.brand,
                    labelColor = t.colors.foreground,
                    selectedLabelColor = t.colors.brandForeground
                )
            )
        }
    }
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
    val t = LocalLumenTokens.current
    LumenCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md)
        ) {
            AsyncImage(
                model = result.posterUrl,
                contentDescription = "Artwork for ${result.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(LumenLayout.avatarLg, LumenLayout.inputWidthXs)
                    .clip(LumenTokens.Shape.sm)
                    .background(LumenTokens.Color.surfaceMuted)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    fontSize = LumenType.size15,
                    fontWeight = FontWeight.Bold,
                    color = t.colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val meta = remember(result) {
                    buildString {
                        append(result.type.toSearchItemTypeLabel())
                        if (result.sourceLabel.isNotEmpty()) {
                            append(" · ")
                            append(result.sourceLabel)
                        }
                    }
                }
                
                Text(
                    text = meta,
                    fontSize = LumenType.size11_5,
                    fontWeight = FontWeight.SemiBold,
                    color = t.colors.mutedForeground.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = LumenTokens.Space.s1)
                )

                Text(
                    text = result.subtitle ?: "",
                    fontSize = LumenType.size12_5,
                    color = t.colors.mutedForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = LumenTokens.Space.xs)
                )
            }
        }
    }
}

@Composable
private fun VisualSearchResultItem(
    result: SearchDisplayResult,
    onClick: () -> Unit,
) {
    val t = LocalLumenTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenTokens.Shape.md)
            .clickable(onClick = onClick)
            .padding(LumenTokens.Space.s5)
            .semantics(mergeDescendants = true) {
                contentDescription = result.title
            }
    ) {
        PosterCard(
            imageUrl = result.posterUrl,
            contentLabel = result.title,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = result.title,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = t.colors.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = LumenTokens.Space.sm),
        )
        val meta = remember(result) {
            buildString {
                append(result.type.toSearchItemTypeLabel())
                if (result.sourceLabel.isNotEmpty()) {
                    append(" · ")
                    append(result.sourceLabel)
                }
            }
        }
        Text(
            text = meta,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = t.colors.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!result.subtitle.isNullOrBlank()) {
            Text(
                text = result.subtitle.orEmpty(),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = t.colors.mutedForeground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = LumenTokens.Space.xs),
            )
        }
    }
}

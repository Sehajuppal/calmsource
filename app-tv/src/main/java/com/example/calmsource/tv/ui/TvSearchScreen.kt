package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import coil.compose.AsyncImage
import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.MediaType
import com.example.calmsource.feature.search.SearchDisplayResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.calmsource.core.ui.tv.rememberTvFocusMemory
import com.example.calmsource.core.ui.tv.TvFocusScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.calmsource.core.ui.components.TvFocusable
import com.example.calmsource.core.ui.components.RowSection
import com.example.calmsource.core.ui.components.PosterCard
import com.example.calmsource.core.ui.components.PosterOrientation
import com.example.calmsource.core.ui.components.LumenSkeleton
import com.example.calmsource.core.ui.components.LumenEmptyState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search

@Composable
fun TvSearchScreen(
    initialQuery: String = "",
    onInitialQueryConsumed: () -> Unit = {},
    onMediaClick: (MediaItem) -> Unit,
    onChannelClick: (String) -> Unit = {},
    viewModel: TvSearchViewModel = hiltViewModel()
) {
    val t = LocalLumenTokens.current
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val scrollPosition by viewModel.scrollPosition.collectAsState()

    val titlesGroup = remember(searchResults) { searchResults.filter { it.type != "channel" } }
    val channelsGroup = remember(searchResults) { searchResults.filter { it.type == "channel" } }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPosition.first,
        initialFirstVisibleItemScrollOffset = scrollPosition.second
    )

    val focusMemory = rememberTvFocusMemory()
    val searchFieldFocusRequester = remember { FocusRequester() }

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

    LaunchedEffect(Unit) {
        val hasRowMemory = listOf("search/titles", "search/channels", "search/chips")
            .any { focusMemory.lastFocusedId(it) != null }
        if (!hasRowMemory) {
            searchFieldFocusRequester.requestFocus()
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
            .padding(LumenLegacySpace.xxl)
    ) {
        Text(
            text = "Search",
            fontSize = LumenType.size38,
            fontWeight = FontWeight.Bold,
            color = t.colors.foreground,
            modifier = Modifier.padding(bottom = LumenLegacySpace.lg)
        )

        TvTextField(
            value = query,
            onValueChange = viewModel::search,
            placeholder = { Text("Search movies, series, and live channels...", color = t.colors.mutedForeground) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitQuery() }),
            onSearchAction = { submitQuery() },
            shape = LumenTokens.Shape.sm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = LumenLegacySpace.lg)
                .focusRequester(searchFieldFocusRequester)
        )

        when {
            isSearching && searchResults.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(LumenLegacySpace.lg),
                    verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
                ) {
                    repeat(3) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LumenSkeleton(modifier = Modifier.weight(1f).height(LumenLayout.epgMinBlockWidth))
                            LumenSkeleton(modifier = Modifier.weight(1f).height(LumenLayout.epgMinBlockWidth))
                        }
                    }
                }
            }
            searchResults.isEmpty() && query.isNotEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    LumenEmptyState(
                        title = "Nothing matched '$query'",
                        body = "Try checking the spelling or look for different keywords.",
                        icon = androidx.compose.material.icons.Icons.Default.Search
                    )
                }
            }
            query.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LumenEmptyState(
                        title = "Search films, series, channels",
                        body = "Find movies, series, or live TV channels from all sources.",
                        icon = androidx.compose.material.icons.Icons.Default.Search,
                        modifier = Modifier.weight(1f)
                    )
                    val suggestedTags = listOf("thriller", "drama", "sci-fi", "comedy", "documentary", "news", "sports")
                    Text(
                        text = "Suggested Genres",
                        fontSize = LumenType.size14,
                        fontWeight = FontWeight.Bold,
                        color = t.colors.mutedForeground,
                        modifier = Modifier.padding(bottom = LumenLegacySpace.sm2),
                    )
                    TvFocusScope(
                        memory = focusMemory,
                        scopeId = "search/chips",
                        itemIds = suggestedTags,
                    ) { restorer ->
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = LumenLegacySpace.sm2),
                            horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = LumenLegacySpace.lg),
                        ) {
                            items(suggestedTags, key = { it }) { label ->
                                TvFocusable(
                                    onClick = {
                                        viewModel.search(label)
                                        viewModel.submitSearch(label)
                                    },
                                    cornerRadius = LumenLegacySpace.sm2,
                                    onFocused = { restorer.remember(label) },
                                    modifier = restorer.itemModifier(label),
                                ) {
                                    Text(
                                        text = label,
                                        color = t.colors.foreground,
                                        fontSize = LumenType.size14,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .background(t.colors.card)
                                            .padding(horizontal = LumenTokens.Radius.md, vertical = LumenLegacySpace.sm2),
                                    )
                                }
                            }
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Enter a search term to find movies, shows, and channels.",
                            color = t.colors.mutedForeground,
                            fontSize = LumenType.size16
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xxl),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (titlesGroup.isNotEmpty()) {
                        item(key = "titles-section") {
                            RowSection(title = "Titles") {
                                TvFocusScope(
                                    memory = focusMemory,
                                    scopeId = "search/titles",
                                    itemIds = titlesGroup.map { it.id },
                                ) { restorer ->
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = LumenLegacySpace.sm2),
                                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        itemsIndexed(titlesGroup, key = { index, result -> tvSearchResultLazyKey("titles", index, result) }) { _, result ->
                                            PosterCard(
                                                imageUrl = result.posterUrl,
                                                orientation = PosterOrientation.Portrait,
                                                onClick = {
                                                    val selectedQuery = query.trim()
                                                    if (selectedQuery.isNotEmpty()) {
                                                        scope.launch {
                                                            viewModel.recordSearchInterest(selectedQuery, result)
                                                        }
                                                    }
                                                    onMediaClick(result.toTvMediaItem())
                                                },
                                                modifier = restorer.itemModifier(result.id),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (channelsGroup.isNotEmpty()) {
                        item(key = "channels-section") {
                            RowSection(title = "Live Channels") {
                                TvFocusScope(
                                    memory = focusMemory,
                                    scopeId = "search/channels",
                                    itemIds = channelsGroup.map { it.id },
                                ) { restorer ->
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = LumenLegacySpace.sm2),
                                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        itemsIndexed(channelsGroup, key = { index, result -> tvSearchResultLazyKey("channels", index, result) }) { _, result ->
                                            TvLiveChannelCard(
                                                channelName = result.title,
                                                logoUrl = result.posterUrl,
                                                category = result.sourceLabel,
                                                onClick = { onChannelClick(result.id) },
                                                modifier = restorer.itemModifier(result.id),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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

private fun tvSearchResultLazyKey(
    sectionKey: String,
    index: Int,
    result: SearchDisplayResult
): String = "$sectionKey-$index-${result.type}-${result.id}"


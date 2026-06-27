package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.LumenTokens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.providers.FailureLogEntry
import com.example.calmsource.core.discoveryengine.providers.ProviderManager
import com.example.calmsource.core.discoveryengine.providers.ProviderStatusRow
import com.example.calmsource.core.discoveryengine.providers.ProviderType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TvDiscoveryProvidersScreen(onBack: () -> Unit) {
    val providerRows by ProviderManager.getProviderStatus().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val sortedRows = providerRows.sortedWith(compareBy<ProviderStatusRow> { it.priority }.thenBy { it.name })
    val privacyTypes = remember {
        listOf(
            ProviderType.METADATA,
            ProviderType.RATING,
            ProviderType.SIMILAR,
            ProviderType.AVAILABILITY
        )
    }
    var localOnlyMode by remember { mutableStateOf(ProviderManager.isLocalOnlyMode()) }
    var enrichmentEnabled by remember {
        mutableStateOf(privacyTypes.associateWith { ProviderManager.isEnrichmentAllowed(it) })
    }
    var failuresDialog by remember { mutableStateOf<Pair<String, List<FailureLogEntry>>?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var fuzzySearch by remember {
        mutableStateOf(com.example.calmsource.core.discoveryengine.database.DiscoverySearchFeatureFlags.enableFuzzyFallback)
    }

    LaunchedEffect(Unit) {
        localOnlyMode = DiscoveryEngine.isLocalOnlyMode()
        enrichmentEnabled = DiscoveryEngine.getProviderEnrichmentSettings(privacyTypes.toSet())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(LumenTokens.Space.xxl),
        verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.md)
    ) {
        item {
            TvFocusCard(onClick = onBack, modifier = Modifier.wrapContentSize().padding(bottom = LumenTokens.Space.lg)) {
                Text(text = "< Back", color = TvColors.TextMain)
            }
        }
        item {
            Text(
                text = "Discovery Providers",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TvColors.TextMain
            )
            Text(
                text = "Provider enrichment is cache-first and never replaces playback selection.",
                color = TvColors.TextSub,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = LumenTokens.Space.xs)
            )
        }

        item {
            Text("Privacy", color = TvColors.BorderFocused, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        item {
            TvProviderToggleRow(
                title = "Local-only mode",
                subtitle = "Use local cache and local packs only",
                enabled = localOnlyMode,
                onToggle = {
                    val next = !localOnlyMode
                    localOnlyMode = next
                    coroutineScope.launch { DiscoveryEngine.setLocalOnlyMode(next) }
                }
            )
        }
        itemsIndexed(privacyTypes, key = { _, type -> type.name }) { _, type ->
            val enabled = enrichmentEnabled[type] != false
            TvProviderToggleRow(
                title = tvProviderTypeLabel(type),
                subtitle = "Allow this enrichment category",
                enabled = enabled,
                onToggle = {
                    val next = !enabled
                    enrichmentEnabled = enrichmentEnabled + (type to next)
                    coroutineScope.launch { DiscoveryEngine.setProviderEnrichmentAllowed(type, next) }
                }
            )
        }

        item {
            Text("Search", color = TvColors.BorderFocused, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = LumenTokens.Space.sm2))
        }
        item {
            TvProviderToggleRow(
                title = "Typo-Tolerant Search",
                subtitle = "Match results even when the query has small spelling mistakes",
                enabled = fuzzySearch,
                onToggle = {
                    val next = !fuzzySearch
                    fuzzySearch = next
                    com.example.calmsource.core.discoveryengine.database.DiscoverySearchFeatureFlags
                        .setEnabledBestEffort(context, next)
                }
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = LumenTokens.Space.sm2)) {
                Text("Providers", color = TvColors.BorderFocused, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(LumenTokens.Space.md))
                Text("${sortedRows.size}", color = TvColors.TextSub, fontSize = 14.sp)
            }
        }
        if (sortedRows.isEmpty()) {
            item {
                Text("No Stremio discovery providers registered yet.", color = TvColors.TextSub, fontSize = 14.sp)
            }
        } else {
            itemsIndexed(sortedRows, key = { _, row -> row.providerId }) { index, row ->
                TvDiscoveryProviderItem(
                    row = row,
                    canMoveUp = index > 0,
                    canMoveDown = index < sortedRows.lastIndex,
                    onToggle = {
                        coroutineScope.launch(Dispatchers.IO) {
                            ProviderManager.setProviderEnabled(row.providerId, !row.isEnabled)
                        }
                    },
                    onMove = { direction ->
                        coroutineScope.launch(Dispatchers.IO) {
                            tvReorderProviders(sortedRows, row.providerId, direction)
                        }
                    },
                    onFailures = {
                        coroutineScope.launch {
                            val failures = withContext(Dispatchers.IO) {
                                ProviderManager.getTelemetryStore()
                                    ?.getFailuresForProvider(row.providerId, 25)
                                    .orEmpty()
                            }
                            failuresDialog = row.name to failures
                        }
                    }
                )
            }
        }

        item {
            TvFocusCard(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        ProviderManager.clearProviderCache()
                        withContext(Dispatchers.Main) {
                            statusMessage = "Provider cache cleared"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = LumenTokens.Space.sm2)
            ) { isFocused ->
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear Provider Cache", color = TvColors.TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(if (isFocused) "Press OK" else "Clear", color = TvColors.BorderFocused, fontSize = 13.sp)
                }
            }
            statusMessage?.let { message ->
                Text(message, color = TvColors.TextSub, fontSize = 13.sp, modifier = Modifier.padding(top = LumenTokens.Space.sm2))
            }
        }
    }

    failuresDialog?.let { (name, failures) ->
        TvProviderFailuresDialog(name, failures, onDismiss = { failuresDialog = null })
    }
}

@Composable
private fun TvProviderToggleRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    TvFocusCard(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth()
    ) { isFocused ->
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TvColors.TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = if (isFocused) TvColors.TextMain else TvColors.TextSub, fontSize = 12.sp)
            }
            Text(
                text = if (enabled) "Enabled" else "Disabled",
                color = if (enabled) TvColors.BorderFocused else TvColors.TextSub,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TvDiscoveryProviderItem(
    row: ProviderStatusRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
    onFailures: () -> Unit
) {
    val healthColor = when {
        row.failureCount >= 5 -> LumenTokens.Color.errorBright
        row.failureCount > 0 -> LumenTokens.Color.warning
        else -> LumenTokens.Color.statusHealthy
    }

    Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm2)) {
        TvFocusCard(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth()
        ) { isFocused ->
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.name, color = TvColors.TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${row.kind.name.replace("_", " ")} | Priority ${row.priority}",
                        color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                        fontSize = 12.sp
                    )
                    Text(
                        row.capabilities.sortedBy { it.name }.joinToString("  |  ") { tvProviderTypeShortLabel(it) },
                        color = TvColors.TextSub,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = LumenTokens.Space.xs)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (row.isEnabled) "Enabled" else "Disabled",
                        color = if (row.isEnabled) TvColors.BorderFocused else TvColors.TextSub,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Reliability ${(row.reliabilityScore * 100).toInt()}%",
                        color = healthColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = LumenTokens.Space.xs)
                    )
                    Text("${row.failureCount} failures", color = TvColors.TextSub, fontSize = 12.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm2), modifier = Modifier.fillMaxWidth()) {
            TvFocusCard(
                onClick = { if (canMoveUp) onMove(-1) },
                modifier = Modifier.weight(1f)
            ) { isFocused ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Move Up", color = if (canMoveUp && isFocused) TvColors.TextMain else TvColors.TextSub)
                }
            }
            TvFocusCard(
                onClick = { if (canMoveDown) onMove(1) },
                modifier = Modifier.weight(1f)
            ) { isFocused ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Move Down", color = if (canMoveDown && isFocused) TvColors.TextMain else TvColors.TextSub)
                }
            }
            TvFocusCard(
                onClick = onFailures,
                modifier = Modifier.weight(1f)
            ) { isFocused ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Failures", color = if (isFocused) TvColors.TextMain else TvColors.BorderFocused)
                }
            }
        }
    }
}

@Composable
private fun TvProviderFailuresDialog(
    providerName: String,
    failures: List<FailureLogEntry>,
    onDismiss: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(LumenTokens.Layout.heroHeightLg)
                .background(TvColors.Surface, LumenTokens.Shape.md)
                .padding(LumenTokens.Space.xxl)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm)) {
                Text("Provider Failures", color = TvColors.TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(providerName, color = TvColors.BorderFocused, fontSize = 15.sp)
                if (failures.isEmpty()) {
                    Text("No recent failures.", color = TvColors.TextSub, fontSize = 14.sp)
                } else {
                    failures.take(8).forEach { failure ->
                        Column {
                            Text(failure.errorCode, color = TvColors.TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(formatter.format(Date(failure.occurredAt)), color = TvColors.TextSub, fontSize = 12.sp)
                            failure.message?.let { Text(it, color = TvColors.TextSub, fontSize = 12.sp) }
                        }
                    }
                }
                TvFocusCard(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { isFocused ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Done", color = if (isFocused) TvColors.TextMain else TvColors.BorderFocused)
                    }
                }
            }
        }
    }
}

private suspend fun tvReorderProviders(rows: List<ProviderStatusRow>, providerId: String, direction: Int) {
    val currentIndex = rows.indexOfFirst { it.providerId == providerId }
    if (currentIndex == -1) return
    val targetIndex = (currentIndex + direction).coerceIn(0, rows.lastIndex)
    if (currentIndex == targetIndex) return
    val reordered = rows.toMutableList()
    val item = reordered.removeAt(currentIndex)
    reordered.add(targetIndex, item)
    reordered.forEachIndexed { index, row ->
        ProviderManager.setProviderPriority(row.providerId, index * 10)
    }
}

private fun tvProviderTypeLabel(type: ProviderType): String = when (type) {
    ProviderType.METADATA -> "Metadata enrichment"
    ProviderType.RATING -> "Ratings enrichment"
    ProviderType.SIMILAR -> "Similar-title enrichment"
    ProviderType.AVAILABILITY -> "Availability enrichment"
    ProviderType.SUBTITLE -> "Subtitle enrichment"
    ProviderType.STREAM -> "Stream descriptors"
    ProviderType.CATALOG -> "Catalog enrichment"
    ProviderType.ARTWORK -> "Artwork enrichment"
}

private fun tvProviderTypeShortLabel(type: ProviderType): String = when (type) {
    ProviderType.METADATA -> "Meta"
    ProviderType.RATING -> "Ratings"
    ProviderType.SIMILAR -> "Similar"
    ProviderType.SUBTITLE -> "Subs"
    ProviderType.STREAM -> "Streams"
    ProviderType.CATALOG -> "Catalog"
    ProviderType.ARTWORK -> "Art"
    ProviderType.AVAILABILITY -> "Available"
}

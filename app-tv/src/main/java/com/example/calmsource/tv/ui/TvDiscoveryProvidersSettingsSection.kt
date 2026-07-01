package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val t = LocalLumenTokens.current
    val stableFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            stableFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }
    val coroutineScope = rememberCoroutineScope()
    val providerRows by ProviderManager.getProviderStatus().collectAsState(initial = emptyList())
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(LumenLegacySpace.xxl)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
        ) {
            item {
                TvFocusCard(
                    onClick = onBack,
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(bottom = LumenLegacySpace.sm2)
                        .focusRequester(stableFocusRequester)
                ) {
                    Text(text = "< Back", color = t.colors.foreground)
                }
            }
            item {
                Text(
                    text = "Discovery Providers",
                    fontSize = LumenType.size28,
                    fontWeight = FontWeight.Bold,
                    color = t.colors.foreground
                )
                Text(
                    text = "Configure search providers and data privacy",
                    color = t.colors.mutedForeground,
                    style = lumenCaptionStyle(),
                    modifier = Modifier.padding(bottom = LumenLegacySpace.md)
                )
            }

            item {
                Text("Privacy", color = t.colors.brand, fontSize = LumenType.size18, fontWeight = FontWeight.Bold)
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
                Text("Search", color = t.colors.brand, fontSize = LumenType.size18, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = LumenLegacySpace.sm2))
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
        }

        Spacer(modifier = Modifier.width(LumenLegacySpace.xxl))

        LazyColumn(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Providers", color = t.colors.brand, fontSize = LumenType.size18, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(LumenLegacySpace.md))
                    Text("${sortedRows.size}", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                }
            }
            if (sortedRows.isEmpty()) {
                item {
                    Text("No Stremio discovery providers registered yet.", color = t.colors.mutedForeground, style = lumenCaptionStyle())
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
                    modifier = Modifier.fillMaxWidth().padding(top = LumenLegacySpace.sm2)
                ) { isFocused ->
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear Provider Cache", color = t.colors.foreground, style = lumenBodyStyle(), fontWeight = FontWeight.Bold)
                        Text(if (isFocused) "Press OK" else "Clear", color = t.colors.brand, style = lumenCaptionStyle())
                    }
                }
                statusMessage?.let { message ->
                    Text(message, color = t.colors.mutedForeground, style = lumenCaptionStyle(), modifier = Modifier.padding(top = LumenLegacySpace.sm2))
                }
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
    val t = LocalLumenTokens.current
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
                Text(title, color = t.colors.foreground, style = lumenBodyStyle(), fontWeight = FontWeight.Bold)
                Text(subtitle, color = if (isFocused) t.colors.foreground else t.colors.mutedForeground, style = lumenCaptionStyle())
            }
            Text(
                text = if (enabled) "Enabled" else "Disabled",
                color = if (enabled) t.colors.brand else t.colors.mutedForeground,
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
    val t = LocalLumenTokens.current
    val healthColor = when {
        row.failureCount >= 5 -> LumenExtendedColors.errorBright
        row.failureCount > 0 -> LumenTokens.Color.warning
        else -> LumenExtendedColors.statusHealthy
    }

    Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)) {
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
                    Text(row.name, color = t.colors.foreground, fontSize = LumenType.size18, fontWeight = FontWeight.Bold)
                    Text(
                        "${row.kind.name.replace("_", " ")} | Priority ${row.priority}",
                        color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
                        style = lumenCaptionStyle()
                    )
                    Text(
                        row.capabilities.sortedBy { it.name }.joinToString("  |  ") { tvProviderTypeShortLabel(it) },
                        color = t.colors.mutedForeground,
                        style = lumenCaptionStyle(),
                        modifier = Modifier.padding(top = LumenLegacySpace.xs)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (row.isEnabled) "Enabled" else "Disabled",
                        color = if (row.isEnabled) t.colors.brand else t.colors.mutedForeground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Reliability ${(row.reliabilityScore * 100).toInt()}%",
                        color = healthColor,
                        style = lumenCaptionStyle(),
                        modifier = Modifier.padding(top = LumenLegacySpace.xs)
                    )
                    Text("${row.failureCount} failures", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2), modifier = Modifier.fillMaxWidth()) {
            TvFocusCard(
                onClick = { if (canMoveUp) onMove(-1) },
                modifier = Modifier.weight(1f)
            ) { isFocused ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Move Up", color = if (canMoveUp && isFocused) t.colors.foreground else t.colors.mutedForeground)
                }
            }
            TvFocusCard(
                onClick = { if (canMoveDown) onMove(1) },
                modifier = Modifier.weight(1f)
            ) { isFocused ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Move Down", color = if (canMoveDown && isFocused) t.colors.foreground else t.colors.mutedForeground)
                }
            }
            TvFocusCard(
                onClick = onFailures,
                modifier = Modifier.weight(1f)
            ) { isFocused ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Failures", color = if (isFocused) t.colors.foreground else t.colors.brand)
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
    val t = LocalLumenTokens.current
    val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(LumenLayout.heroHeightLg)
                .background(t.colors.surface, LumenTokens.Shape.md)
                .padding(LumenLegacySpace.xxl)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm)) {
                Text("Provider Failures", color = t.colors.foreground, fontSize = LumenType.size22, fontWeight = FontWeight.Bold)
                Text(providerName, color = t.colors.brand, fontSize = LumenType.size15)
                if (failures.isEmpty()) {
                    Text("No recent failures.", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                } else {
                    failures.take(8).forEach { failure ->
                        Column {
                            Text(failure.errorCode, color = t.colors.foreground, fontSize = LumenType.size15, fontWeight = FontWeight.Bold)
                            Text(formatter.format(Date(failure.occurredAt)), color = t.colors.mutedForeground, style = lumenCaptionStyle())
                            failure.message?.let { Text(it, color = t.colors.mutedForeground, style = lumenCaptionStyle()) }
                        }
                    }
                }
                TvFocusCard(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { isFocused ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Done", color = if (isFocused) t.colors.foreground else t.colors.brand)
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

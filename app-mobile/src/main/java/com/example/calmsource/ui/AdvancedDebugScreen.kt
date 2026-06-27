package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.LumenTokens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.database.CoreDatabaseRuntimeStatus
import com.example.calmsource.core.database.SlowQueryLogger
import com.example.calmsource.core.discoveryengine.database.DiscoveryDatabaseRuntimeStatus
import com.example.calmsource.core.discoveryengine.database.DiscoverySearchFeatureFlags
import com.example.calmsource.core.discoveryengine.providers.ProviderManager
import com.example.calmsource.core.model.ResourceGovernor
import com.example.calmsource.core.playback.ImageCacheController
import com.example.calmsource.core.playback.TunnelingBlacklist
import com.example.calmsource.core.playback.diagnostics.PlaybackDiagnosticsFormatter
import com.example.calmsource.core.playback.diagnostics.PlaybackDiagnosticsRecorder

@Composable
fun AdvancedDebugScreen(onBack: () -> Unit) {
    val coreDb by CoreDatabaseRuntimeStatus.state.collectAsState()
    val discoveryDb by DiscoveryDatabaseRuntimeStatus.state.collectAsState()
    val resources by ResourceGovernor.snapshot.collectAsState()
    val imageCache by ImageCacheController.state.collectAsState()
    val breakers by ProviderManager.providerCircuitState.collectAsState()
    var fuzzyEnabled by remember {
        mutableStateOf(DiscoverySearchFeatureFlags.enableFuzzyFallback)
    }
    var refreshKey by remember { mutableIntStateOf(0) }
    val queueSize = remember(refreshKey, breakers) { ProviderManager.snapshotProviderQueueSize() }
    val slowQueryCount = remember(refreshKey) { SlowQueryLogger.snapshot().size }
    val tunnelingBlacklistCount = remember(refreshKey) { TunnelingBlacklist.snapshot().size }
    val playbackSnapshot = remember(refreshKey) { PlaybackDiagnosticsRecorder.lastSessionSnapshot }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(LumenTokens.Space.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.md)
    ) {
        SubScreenHeader(title = "Advanced Debug", onBack = onBack)
        DebugSection(
            title = "Databases",
            rows = listOf(
                "Core WAL" to coreDb.walEnabled.toString(),
                "Core busy timeout" to "${coreDb.busyTimeoutMs} ms",
                "Discovery WAL" to discoveryDb.walEnabled.toString(),
                "Discovery busy timeout" to "${discoveryDb.busyTimeoutMs} ms",
                "Search index" to discoveryDb.ftsMode,
                "Slow query entries" to slowQueryCount.toString()
            )
        )
        DebugSection(
            title = "Playback",
            rows = PlaybackDiagnosticsFormatter.snapshotRows(playbackSnapshot) + listOf(
                "Recent events" to playbackSnapshot.recentEvents.size.toString()
            )
        )
        if (playbackSnapshot.recentEvents.isNotEmpty()) {
            DebugSection(
                title = "Recovery timeline",
                rows = playbackSnapshot.recentEvents.takeLast(5).map { event ->
                    event.kind to event.detail
                }
            )
        }
        DebugSection(
            title = "Resources",
            rows = listOf(
                "Playback state" to resources.playbackState.name,
                "Low memory mode" to resources.lowMemoryMode.toString(),
                "Background work paused" to resources.shouldPauseBackgroundWork.toString(),
                "Provider queue" to queueSize.toString(),
                "Open provider circuits" to breakers.values.count { it.state.name == "OPEN" }.toString(),
                "Image prefetch paused" to imageCache.nonCriticalRequestsPaused.toString(),
                "Tunneling blacklist entries" to tunnelingBlacklistCount.toString()
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    fuzzyEnabled = !fuzzyEnabled
                    DiscoverySearchFeatureFlags.enableFuzzyFallback = fuzzyEnabled
                }
                .padding(vertical = LumenTokens.Space.sm2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = fuzzyEnabled,
                onCheckedChange = { enabled ->
                    fuzzyEnabled = enabled
                    DiscoverySearchFeatureFlags.enableFuzzyFallback = enabled
                }
            )
            Text(
                text = "Enable fuzzy search fallback",
                color = AppColors.TextMain,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Button(onClick = { refreshKey++ }) {
            Text("Refresh snapshot")
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        Button(
            onClick = {
                ProviderManager.resetAllProviderCircuitBreakers()
                refreshKey++
            }
        ) {
            Text("Reset provider circuits")
        }
        Button(
            onClick = {
                com.example.calmsource.core.playback.VlcFallbackHelper.resetInitFailed(context)
            }
        ) {
            Text("Reset VLC init failed state")
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    rows: List<Pair<String, String>>
) {
    Text(
        text = title,
        color = AppColors.Primary,
        style = MaterialTheme.typography.titleMedium
    )
    rows.forEach { (label, value) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = AppColors.TextSub)
            Text(value, color = AppColors.TextMain)
        }
    }
}

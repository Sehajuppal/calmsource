package com.example.calmsource.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun TvAdvancedDebugScreen(onBack: () -> Unit) {
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
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Advanced Debug",
                color = TvColors.TextMain,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            TvDebugSection(
                "Databases",
                listOf(
                    "Core WAL" to coreDb.walEnabled.toString(),
                    "Core timeout" to "${coreDb.busyTimeoutMs} ms",
                    "Discovery WAL" to discoveryDb.walEnabled.toString(),
                    "Discovery timeout" to "${discoveryDb.busyTimeoutMs} ms",
                    "Search index" to discoveryDb.ftsMode,
                    "Slow queries" to slowQueryCount.toString()
                )
            )
        }
        item {
            TvDebugSection(
                "Playback",
                PlaybackDiagnosticsFormatter.snapshotRows(playbackSnapshot).map { it.first to it.second } +
                    listOf("Recent events" to playbackSnapshot.recentEvents.size.toString())
            )
        }
        if (playbackSnapshot.recentEvents.isNotEmpty()) {
            item {
                TvDebugSection(
                    "Recovery timeline",
                    playbackSnapshot.recentEvents.takeLast(5).map { event ->
                        event.kind to event.detail
                    }
                )
            }
        }
        item {
            TvDebugSection(
                "Runtime",
                listOf(
                    "Playback" to resources.playbackState.name,
                    "Low memory" to resources.lowMemoryMode.toString(),
                    "Background paused" to resources.shouldPauseBackgroundWork.toString(),
                    "Provider queue" to queueSize.toString(),
                    "Open circuits" to breakers.values.count { it.state.name == "OPEN" }.toString(),
                    "Image prefetch paused" to imageCache.nonCriticalRequestsPaused.toString(),
                    "Tunneling blacklist" to tunnelingBlacklistCount.toString()
                )
            )
        }
        item {
            TvSettingsRow(
                title = "Fuzzy search fallback",
                description = if (fuzzyEnabled) "Enabled" else "Disabled",
                onClick = {
                    fuzzyEnabled = !fuzzyEnabled
                    DiscoverySearchFeatureFlags.enableFuzzyFallback = fuzzyEnabled
                }
            )
        }
        item {
            TvSettingsRow(
                title = "Refresh snapshot",
                description = "Update counters",
                onClick = { refreshKey++ }
            )
        }
        item {
            TvSettingsRow(
                title = "Reset provider circuits",
                description = "Close all provider circuit breakers",
                onClick = {
                    ProviderManager.resetAllProviderCircuitBreakers()
                    refreshKey++
                }
            )
        }
        item {
            TvSettingsRow(
                title = "Reset VLC init failed state",
                description = "Clear persisted VLC initialization failure flag",
                onClick = {
                    com.example.calmsource.core.playback.VlcFallbackHelper.resetInitFailed(context)
                }
            )
        }
        item {
            TvSettingsRow(title = "Back", description = "Return to settings", onClick = onBack)
        }
    }
}

@Composable
private fun TvDebugSection(
    title: String,
    rows: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = TvColors.BorderFocused, fontWeight = FontWeight.Bold)
        rows.forEach { (label, value) ->
            Text("$label: $value", color = TvColors.TextSub, fontSize = 14.sp)
        }
    }
}

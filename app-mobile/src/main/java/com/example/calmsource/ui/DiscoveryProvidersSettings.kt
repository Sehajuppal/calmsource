package com.example.calmsource.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.calmsource.core.ui.theme.*
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
fun DiscoveryProvidersScreen(onBack: () -> Unit) {
    val t = LocalLumenTokens.current
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
    var localOnlyMode by remember { mutableStateOf(false) }
    var enrichmentEnabled by remember {
        mutableStateOf(privacyTypes.associateWith { ProviderManager.isEnrichmentAllowed(it) })
    }
    var failuresDialog by remember { mutableStateOf<Pair<String, List<FailureLogEntry>>?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        localOnlyMode = DiscoveryEngine.isLocalOnlyMode()
        enrichmentEnabled = DiscoveryEngine.getProviderEnrichmentSettings(privacyTypes.toSet())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenHeader(title = "Discovery Providers", onBack = onBack)

        Text("Privacy", style = MaterialTheme.typography.titleMedium, color = t.colors.brand)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = t.colors.surface)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PreferenceSwitchRow("Local-only mode", localOnlyMode) { enabled ->
                    localOnlyMode = enabled
                    coroutineScope.launch { DiscoveryEngine.setLocalOnlyMode(enabled) }
                }
                privacyTypes.forEach { type ->
                    PreferenceSwitchRow(providerTypeLabel(type), enrichmentEnabled[type] != false) { enabled ->
                        enrichmentEnabled = enrichmentEnabled + (type to enabled)
                        coroutineScope.launch { DiscoveryEngine.setProviderEnrichmentAllowed(type, enabled) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Providers", style = MaterialTheme.typography.titleMedium, color = t.colors.brand)
        Spacer(modifier = Modifier.height(8.dp))

        if (sortedRows.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = t.colors.surface)
            ) {
                Text(
                    text = "No Stremio discovery providers registered yet.",
                    color = t.colors.mutedForeground,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            sortedRows.forEachIndexed { index, row ->
                DiscoveryProviderRow(
                    row = row,
                    canMoveUp = index > 0,
                    canMoveDown = index < sortedRows.lastIndex,
                    onToggle = { enabled ->
                        coroutineScope.launch(Dispatchers.IO) {
                            ProviderManager.setProviderEnabled(row.providerId, enabled)
                        }
                    },
                    onMove = { direction ->
                        coroutineScope.launch(Dispatchers.IO) {
                            reorderProviders(sortedRows, row.providerId, direction)
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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    ProviderManager.clearProviderCache()
                    withContext(Dispatchers.Main) {
                        statusMessage = "Provider cache cleared"
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = t.colors.brand),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear Provider Cache", color = Color.White)
        }

        statusMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = t.colors.mutedForeground, fontSize = 12.sp)
        }
    }

    failuresDialog?.let { (providerName, failures) ->
        ProviderFailuresDialog(
            providerName = providerName,
            failures = failures,
            onDismiss = { failuresDialog = null }
        )
    }
}

@Composable
private fun DiscoveryProviderRow(
    row: ProviderStatusRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onFailures: () -> Unit
) {
    val t = LocalLumenTokens.current
    val healthColor = when {
        row.failureCount >= 5 -> LumenExtendedColors.errorBright
        row.failureCount > 0 -> LumenExtendedColors.warning
        else -> LumenExtendedColors.statusHealthy
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = t.colors.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.name, color = t.colors.foreground, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${row.kind.name.replace("_", " ")}  |  Priority ${row.priority}",
                        color = t.colors.mutedForeground,
                        fontSize = 12.sp
                    )
                }
                Switch(checked = row.isEnabled, onCheckedChange = onToggle)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HealthBadge(
                    status = "Reliability ${(row.reliabilityScore * 100).toInt()}%",
                    color = healthColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${row.failureCount} failures",
                    color = t.colors.mutedForeground,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = row.capabilities.sortedBy { it.name }.joinToString("  |  ") { providerTypeShortLabel(it) },
                color = t.colors.mutedForeground,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", tint = t.colors.foreground)
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", tint = t.colors.foreground)
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onFailures) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null, tint = t.colors.brand, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View failures", color = t.colors.brand)
                }
            }
        }
    }
}

@Composable
private fun ProviderFailuresDialog(
    providerName: String,
    failures: List<FailureLogEntry>,
    onDismiss: () -> Unit
) {
    val t = LocalLumenTokens.current
    val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Provider Failures", color = t.colors.brand) },
        text = {
            Column {
                Text(providerName, color = t.colors.foreground, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (failures.isEmpty()) {
                    Text("No recent failures.", color = t.colors.mutedForeground)
                } else {
                    failures.take(8).forEach { failure ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(failure.errorCode, color = t.colors.foreground, fontWeight = FontWeight.SemiBold)
                            Text(
                                formatter.format(Date(failure.occurredAt)),
                                color = t.colors.mutedForeground,
                                fontSize = 12.sp
                            )
                            failure.message?.let {
                                Text(it, color = t.colors.mutedForeground, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = t.colors.brand)
            }
        },
        containerColor = t.colors.surface
    )
}

private suspend fun reorderProviders(rows: List<ProviderStatusRow>, providerId: String, direction: Int) {
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

private fun providerTypeLabel(type: ProviderType): String = when (type) {
    ProviderType.METADATA -> "Metadata enrichment"
    ProviderType.RATING -> "Ratings enrichment"
    ProviderType.SIMILAR -> "Similar-title enrichment"
    ProviderType.AVAILABILITY -> "Availability enrichment"
    ProviderType.SUBTITLE -> "Subtitle enrichment"
    ProviderType.STREAM -> "Stream descriptors"
    ProviderType.CATALOG -> "Catalog enrichment"
    ProviderType.ARTWORK -> "Artwork enrichment"
}

private fun providerTypeShortLabel(type: ProviderType): String = when (type) {
    ProviderType.METADATA -> "Meta"
    ProviderType.RATING -> "Ratings"
    ProviderType.SIMILAR -> "Similar"
    ProviderType.SUBTITLE -> "Subs"
    ProviderType.STREAM -> "Streams"
    ProviderType.CATALOG -> "Catalog"
    ProviderType.ARTWORK -> "Art"
    ProviderType.AVAILABILITY -> "Available"
}

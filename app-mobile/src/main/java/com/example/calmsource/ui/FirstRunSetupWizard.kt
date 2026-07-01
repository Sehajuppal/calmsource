package com.example.calmsource.ui

import android.content.Context
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.model.ProviderSyncStatus
import com.example.calmsource.core.model.userLabel
import com.example.calmsource.core.ui.R as CoreUiR
import com.example.calmsource.core.ui.components.AdaptiveButton
import com.example.calmsource.core.ui.components.LumenCard
import com.example.calmsource.core.ui.components.PrimaryButton
import com.example.calmsource.core.ui.components.SyncStatusPill
import com.example.calmsource.core.ui.theme.LumenExtendedColors
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.feature.extensions.ExtensionRepository
import com.example.calmsource.feature.extensions.RecommendedStremioAddons
import com.example.calmsource.feature.iptv.IPTVRepository
import kotlinx.coroutines.launch

private enum class SetupPath { Choose, Iptv, Extensions, Syncing, Done }

@Composable
fun FirstRunSetupWizard(
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalLumenTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(SetupPath.Choose) }
    var iptvMode by remember { mutableStateOf<String?>(null) }
    var m3uName by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var m3uError by remember { mutableStateOf<String?>(null) }
    var xtreamName by remember { mutableStateOf("") }
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamUsername by remember { mutableStateOf("") }
    var xtreamPassword by remember { mutableStateOf("") }
    var xtreamError by remember { mutableStateOf<String?>(null) }
    var isInstallingAddon by remember { mutableStateOf(false) }
    var addonError by remember { mutableStateOf<String?>(null) }

    val providers by IPTVRepository.providers.collectAsState()
    val extensions by ExtensionRepository.extensions.collectAsState()
    val syncStates by IPTVRepository.syncStates.collectAsState()
    val xtreamProgress by IPTVRepository.xtreamSyncProgress.collectAsState()
    val activeSync = syncStates.values.firstOrNull { it.status == ProviderSyncStatus.SYNCING }
    val syncStageLabel = xtreamProgress?.stage?.userLabel()
        ?: stringResource(CoreUiR.string.sync_iptv_catalog)

    LaunchedEffect(providers, extensions, activeSync) {
        if (step == SetupPath.Syncing && activeSync == null && (providers.isNotEmpty() || extensions.isNotEmpty())) {
            step = SetupPath.Done
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background.copy(alpha = 0.88f))
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        LumenCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LumenTokens.Space.md),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
            ) {
                when (step) {
                    SetupPath.Choose -> {
                        Text(
                            text = stringResource(CoreUiR.string.setup_welcome_title),
                            style = LumenType.H1.toTextStyle(),
                            color = t.colors.foreground,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(CoreUiR.string.setup_welcome_body),
                            color = t.colors.mutedForeground,
                            textAlign = TextAlign.Center,
                        )
                        PrimaryButton(
                            text = stringResource(CoreUiR.string.setup_add_iptv),
                            onClick = { step = SetupPath.Iptv },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AdaptiveButton(
                            text = stringResource(CoreUiR.string.setup_add_extensions),
                            onClick = { step = SetupPath.Extensions },
                            backdropLuminance = 0f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(CoreUiR.string.setup_skip_for_now))
                        }
                    }
                    SetupPath.Iptv -> {
                        Text(
                            text = stringResource(CoreUiR.string.setup_iptv_title),
                            style = LumenType.Title.toTextStyle(),
                            color = t.colors.foreground,
                        )
                        if (iptvMode == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5)) {
                                AdaptiveButton(
                                    text = stringResource(CoreUiR.string.setup_m3u),
                                    onClick = { iptvMode = "m3u" },
                                    backdropLuminance = 0f,
                                    modifier = Modifier.weight(1f),
                                )
                                AdaptiveButton(
                                    text = stringResource(CoreUiR.string.setup_xtream),
                                    onClick = { iptvMode = "xtream" },
                                    backdropLuminance = 0f,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else if (iptvMode == "m3u") {
                            OutlinedTextField(
                                value = m3uName,
                                onValueChange = { m3uName = it },
                                label = { Text(stringResource(CoreUiR.string.setup_provider_name)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = m3uUrl,
                                onValueChange = { m3uUrl = it },
                                label = { Text(stringResource(CoreUiR.string.setup_m3u_url)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            m3uError?.let { Text(it, color = LumenExtendedColors.errorBright) }
                            PrimaryButton(
                                text = stringResource(CoreUiR.string.setup_connect),
                                onClick = {
                                    val url = m3uUrl.trim()
                                    if (url.isEmpty()) {
                                        m3uError = context.getString(CoreUiR.string.setup_error_m3u_url)
                                        return@PrimaryButton
                                    }
                                    scope.launch {
                                        m3uError = null
                                        runCatching {
                                            val name = m3uName.trim().ifBlank { "M3U Playlist" }
                                            val provider = IPTVRepository.addM3uProvider(name, url)
                                            IPTVRepository.syncPlaylistFromUrl(provider.id)
                                            markSetupStarted(context)
                                            step = SetupPath.Syncing
                                        }.onFailure { error ->
                                            m3uError = error.localizedMessage
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            OutlinedTextField(
                                value = xtreamName,
                                onValueChange = { xtreamName = it },
                                label = { Text(stringResource(CoreUiR.string.setup_provider_name)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = xtreamServer,
                                onValueChange = { xtreamServer = it },
                                label = { Text(stringResource(CoreUiR.string.setup_xtream_server)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = xtreamUsername,
                                onValueChange = { xtreamUsername = it },
                                label = { Text(stringResource(CoreUiR.string.setup_xtream_username)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = xtreamPassword,
                                onValueChange = { xtreamPassword = it },
                                label = { Text(stringResource(CoreUiR.string.setup_xtream_password)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            xtreamError?.let { Text(it, color = LumenExtendedColors.errorBright) }
                            PrimaryButton(
                                text = stringResource(CoreUiR.string.setup_connect),
                                onClick = {
                                    if (xtreamServer.isBlank() || xtreamUsername.isBlank() || xtreamPassword.isBlank()) {
                                        xtreamError = context.getString(CoreUiR.string.setup_error_xtream_fields)
                                        return@PrimaryButton
                                    }
                                    scope.launch {
                                        xtreamError = null
                                        val name = xtreamName.trim().ifBlank { "Xtream TV" }
                                        val result = IPTVRepository.addXtreamProvider(
                                            name,
                                            xtreamServer.trim(),
                                            xtreamUsername.trim(),
                                            xtreamPassword.trim(),
                                        )
                                        if (result.isSuccess) {
                                            IPTVRepository.startXtreamProviderSync(result.getOrThrow().id)
                                            markSetupStarted(context)
                                            step = SetupPath.Syncing
                                        } else {
                                            xtreamError = result.exceptionOrNull()?.localizedMessage
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        TextButton(onClick = { step = SetupPath.Choose; iptvMode = null }) {
                            Text(stringResource(CoreUiR.string.cta_back))
                        }
                    }
                    SetupPath.Extensions -> {
                        Text(
                            text = stringResource(CoreUiR.string.setup_extensions_title),
                            style = LumenType.Title.toTextStyle(),
                            color = t.colors.foreground,
                        )
                        Text(
                            text = stringResource(CoreUiR.string.setup_extensions_body),
                            color = t.colors.mutedForeground,
                        )
                        addonError?.let { Text(it, color = LumenExtendedColors.errorBright) }
                        RecommendedStremioAddons.presets.forEach { preset ->
                            val installed = RecommendedStremioAddons.installedProvider(preset, extensions)
                            AdaptiveButton(
                                text = if (installed != null) {
                                    stringResource(CoreUiR.string.setup_addon_installed, preset.name)
                                } else {
                                    stringResource(CoreUiR.string.setup_install_addon, preset.name)
                                },
                                onClick = {
                                    if (installed != null || isInstallingAddon) return@AdaptiveButton
                                    isInstallingAddon = true
                                    addonError = null
                                    scope.launch {
                                        try {
                                            val preview = ExtensionRepository.previewExtension(preset.manifestUrl)
                                            val manifest = preview.manifest
                                            if (preview.isSuccess && manifest != null) {
                                                ExtensionRepository.confirmInstall(manifest, preset.manifestUrl, preview.warnings)
                                                markSetupStarted(context)
                                                if (providers.isEmpty()) {
                                                    step = SetupPath.Done
                                                } else {
                                                    step = SetupPath.Syncing
                                                }
                                            } else {
                                                addonError = context.getString(CoreUiR.string.setup_error_addon)
                                            }
                                        } catch (error: Exception) {
                                            addonError = error.localizedMessage
                                        } finally {
                                            isInstallingAddon = false
                                        }
                                    }
                                },
                                backdropLuminance = 0f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (isInstallingAddon) {
                            CircularProgressIndicator(color = t.colors.brand)
                        }
                        if (extensions.isNotEmpty()) {
                            PrimaryButton(
                                text = stringResource(CoreUiR.string.setup_continue),
                                onClick = {
                                    markSetupComplete(context)
                                    onComplete()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        TextButton(onClick = { step = SetupPath.Choose }) {
                            Text(stringResource(CoreUiR.string.cta_back))
                        }
                    }
                    SetupPath.Syncing -> {
                        SyncStatusPill(
                            title = syncStageLabel,
                            subtitle = activeSync?.let {
                                stringResource(CoreUiR.string.sync_progress, it.progressPercent)
                            } ?: stringResource(CoreUiR.string.setup_sync_hint),
                            dismissLabel = stringResource(CoreUiR.string.cta_browse_now),
                            onDismiss = {
                                markSetupComplete(context)
                                onComplete()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        activeSync?.let { sync ->
                            LinearProgressIndicator(
                                progress = { sync.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            text = stringResource(CoreUiR.string.setup_sync_hint),
                            style = LumenType.Body.toTextStyle(),
                            color = t.colors.mutedForeground,
                            textAlign = TextAlign.Center,
                        )
                        PrimaryButton(
                            text = stringResource(CoreUiR.string.cta_browse_now),
                            onClick = {
                                markSetupComplete(context)
                                onComplete()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SetupPath.Done -> {
                        Text(
                            text = stringResource(CoreUiR.string.setup_done_title),
                            style = LumenType.Title.toTextStyle(),
                            color = t.colors.foreground,
                        )
                        Text(
                            text = stringResource(CoreUiR.string.setup_done_body),
                            color = t.colors.mutedForeground,
                            textAlign = TextAlign.Center,
                        )
                        PrimaryButton(
                            text = stringResource(CoreUiR.string.setup_start_watching),
                            onClick = {
                                markSetupComplete(context)
                                onComplete()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private const val SETUP_PREFS = "first_run_setup"

private fun markSetupStarted(context: Context) {
    context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("started", true)
        .apply()
}

private fun markSetupComplete(context: Context) {
    context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("complete", true)
        .apply()
}

fun isFirstRunSetupComplete(context: Context): Boolean {
    return context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE)
        .getBoolean("complete", false)
}

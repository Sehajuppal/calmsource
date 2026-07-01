package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.calmsource.core.model.ExtensionHealth
import com.example.calmsource.core.model.ExtensionProvider
import com.example.calmsource.feature.extensions.ExtensionRepository
import com.example.calmsource.feature.extensions.RecommendedStremioAddons
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun TvExtensionsScreen(
    onBack: (() -> Unit)? = null,
    onInstallFlowActiveChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val stableFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            stableFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }
    val extensions by ExtensionRepository.extensions.collectAsState()
    val prefs by UserPreferencesRepository.preferences.collectAsState()
    var selectedExtensionId by rememberSaveable { mutableStateOf<String?>(null) }
    var isInstallingOrPreviewing by rememberSaveable { mutableStateOf(false) }
    
    val vaultRestoreErrors by ExtensionRepository.vaultRestoreErrors.collectAsState()
    LaunchedEffect(vaultRestoreErrors) {
        vaultRestoreErrors.firstOrNull()?.let { error ->
            android.util.Log.w("TvExtensionsScreen", "Extension restore failed: $error")
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var inputUrl by rememberSaveable { mutableStateOf("") }
    var isPreviewing by remember { mutableStateOf(false) }
    var previewManifest by remember { mutableStateOf<com.example.calmsource.core.model.ExtensionManifest?>(null) }
    var previewWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    // Guards a confirm-install in flight so rapid D-pad activations can't fire confirmInstall twice
    // (bug #18). Tracks the in-flight preview so a newer preview cancels a slower previous one and
    // results can't arrive out of order (bug #21).
    var isInstalling by remember { mutableStateOf(false) }
    var isSavingConfig by remember { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var extensionToRemove by remember { mutableStateOf<ExtensionProvider?>(null) }
    // Feedback for the per-extension "Save Configuration" action (bug #22).
    var configSaveMessage by remember(selectedExtensionId) { mutableStateOf<String?>(null) }
    var configSaveIsError by remember(selectedExtensionId) { mutableStateOf(false) }

    val inputUrlFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val confirmInstallFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    val inInstallFlow = isInstallingOrPreviewing || previewManifest != null
    val blocksSettingsNavigation = inInstallFlow || isSavingConfig

    LaunchedEffect(blocksSettingsNavigation) {
        onInstallFlowActiveChanged(blocksSettingsNavigation)
    }
    DisposableEffect(Unit) {
        onDispose { onInstallFlowActiveChanged(false) }
    }

    fun cancelPreviewJob() {
        previewJob?.cancel()
        previewJob = null
    }

    fun dismissInstallPreview() {
        cancelPreviewJob()
        previewManifest = null
        previewWarnings = emptyList()
        validationError = null
        isInstallingOrPreviewing = false
        isPreviewing = false
    }

    BackHandler(enabled = inInstallFlow) {
        when {
            previewManifest != null -> {
                previewManifest = null
                previewWarnings = emptyList()
                validationError = null
                cancelPreviewJob()
                isInstallingOrPreviewing = inputUrl.isNotBlank()
            }
            isInstallingOrPreviewing -> {
                isInstallingOrPreviewing = false
                inputUrl = ""
                validationError = null
                cancelPreviewJob()
            }
        }
    }

    LaunchedEffect(selectedExtensionId, isInstallingOrPreviewing) {
        if (selectedExtensionId == null && isInstallingOrPreviewing) {
            inputUrlFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(previewManifest) {
        if (previewManifest != null) {
            confirmInstallFocusRequester.requestFocus()
        }
    }

    // Ensure a default selection exists if possible
    LaunchedEffect(extensions, isInstallingOrPreviewing, previewManifest) {
        if (isInstallingOrPreviewing || previewManifest != null) return@LaunchedEffect
        if (selectedExtensionId != null && extensions.none { it.id == selectedExtensionId }) {
            selectedExtensionId = extensions.firstOrNull()?.id
        } else if (selectedExtensionId == null && extensions.isNotEmpty()) {
            selectedExtensionId = extensions.first().id
        }
    }

    val selectedExtension = extensions.find { it.id == selectedExtensionId }

    var showRawJson by remember(selectedExtensionId) { mutableStateOf(false) }

    val configs = remember(selectedExtensionId) {
        val ext = extensions.find { it.id == selectedExtensionId }
        ext?.manifest?.let { ExtensionRepository.getAddonConfigList(it) } ?: emptyList()
    }

    val configValues = remember(selectedExtensionId) {
        mutableStateMapOf<String, String>()
    }
    var configLoadedForExtensionId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedExtensionId, extensions) {
        val targetId = selectedExtensionId
        if (targetId == null) {
            configLoadedForExtensionId = null
            configValues.clear()
            return@LaunchedEffect
        }
        if (targetId == configLoadedForExtensionId) return@LaunchedEffect
        val ext = extensions.find { it.id == targetId } ?: return@LaunchedEffect
        val map = com.example.calmsource.core.network.StremioAddonClient
            .parseConfigFromUrl(ext.url)
            .toMutableMap()
        val configDefs = ext.manifest?.let { ExtensionRepository.getAddonConfigList(it) }.orEmpty()
        configDefs.forEach { config ->
            if (ExtensionRepository.isSecretConfigKey(config)) {
                com.example.calmsource.core.network.ExtensionSecrets
                    .readSecret(ext.id, config.key)
                    ?.let { map[config.key] = it }
            }
        }
        configValues.clear()
        configValues.putAll(map)
        configLoadedForExtensionId = targetId
    }

    val rawJson = remember(selectedExtension) {
        if (selectedExtension == null) ""
        else {
            val manifest = selectedExtension.manifest
            """
            {
              "id": "${manifest?.id}",
              "name": "${manifest?.name}",
              "description": "${manifest?.description}",
              "version": "${manifest?.version}",
              "resources": ${manifest?.resources?.toString()},
              "types": ${manifest?.types?.toString()},
              "catalogsCount": ${manifest?.catalogs?.size ?: 0}
            }
            """.trimIndent()
        }
    }

    val onPreviewUrl = { urlToPreview: String ->
        val trimmedUrl = urlToPreview.trim()
        inputUrl = trimmedUrl
        selectedExtensionId = null
        isInstallingOrPreviewing = true
        previewManifest = null
        previewWarnings = emptyList()
        validationError = null

        if (trimmedUrl.isBlank()) {
            validationError = "Manifest URL cannot be blank"
        } else {
            val isHttp = trimmedUrl.lowercase().startsWith("http://")
            if (isHttp && !prefs.allowCleartextUserSources) {
                validationError = "Unsafe schemes (HTTP) are rejected by your settings. Enable 'Allow Cleartext HTTP Sources' to use this."
            } else {
                isPreviewing = true

                previewJob?.cancel()
                previewJob = coroutineScope.launch {
                    try {
                        val result = ExtensionRepository.previewExtension(trimmedUrl)
                        isPreviewing = false

                        val httpsWarning = if (trimmedUrl.startsWith("http://", ignoreCase = true)) {
                            listOf("Non-HTTPS extension URLs are a security risk.")
                        } else emptyList()

                        if (result.isSuccess) {
                            previewManifest = result.manifest
                            previewWarnings = result.warnings + httpsWarning
                        } else {
                            previewManifest = null
                            validationError = result.error?.message ?: result.warnings.firstOrNull() ?: httpsWarning.firstOrNull() ?: "Failed to load manifest"
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        previewManifest = null
                        validationError = e.localizedMessage ?: "Failed to load manifest"
                    } finally {
                        isPreviewing = false
                    }
                }
            }
        }
    }

    val onConfirmInstall = {
        val manifest = previewManifest
        if (manifest != null && !isInstalling) {
            isInstalling = true
            coroutineScope.launch {
                try {
                    val result = ExtensionRepository.confirmInstall(manifest, inputUrl, previewWarnings)
                    if (result.isSuccess) {
                        selectedExtensionId = result.manifest?.id
                        configLoadedForExtensionId = null
                        runCatching { stableFocusRequester.requestFocus() }
                        isInstallingOrPreviewing = false
                        inputUrl = ""
                        previewManifest = null
                        previewWarnings = emptyList()
                        validationError = null
                    } else {
                        validationError = result.error?.message ?: result.warnings.firstOrNull() ?: "Failed to install"
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    validationError = e.localizedMessage ?: "Failed to install"
                } finally {
                    isInstalling = false
                }
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(LumenLegacySpace.xxl),
        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.xxl)
    ) {
        // Left Column: Extension List & registration triggers
        LazyColumn(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
        ) {
            item {
                if (onBack != null) {
                    TvFocusCard(
                        onClick = onBack,
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(bottom = LumenLegacySpace.sm2)
                            .focusRequester(stableFocusRequester)
                    ) {
                        Text(text = "← Back", color = t.colors.foreground)
                    }
                }
            }
            
            item {
                Column(
                    modifier = if (onBack == null) {
                        Modifier.focusRequester(stableFocusRequester).focusable()
                    } else {
                        Modifier
                    }
                ) {
                    Text(text = "Extensions", fontSize = LumenType.size28, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                    Text(text = "Configure catalog, search, and stream providers", style = lumenCaptionStyle(), color = t.colors.mutedForeground, modifier = Modifier.padding(bottom = LumenLegacySpace.md))
                }
            }

            items(RecommendedStremioAddons.presets, key = { "preset_${it.manifestId}" }) { preset ->
                val installed = RecommendedStremioAddons.installedProvider(preset, extensions)
                TvFocusCard(
                    onClick = {
                        if (installed == null) {
                            onPreviewUrl(preset.manifestUrl)
                        } else if (!inInstallFlow) {
                            selectedExtensionId = installed.id
                            isInstallingOrPreviewing = false
                            previewManifest = null
                            validationError = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { isFocused ->
                    Column(verticalArrangement = Arrangement.spacedBy(LumenExtendedColors.focusRingWidth)) {
                        Text(
                            text = if (installed == null) "+ Add ${preset.name}" else "${preset.name} Installed",
                            color = if (isFocused) t.colors.foreground else t.colors.brand,
                            fontWeight = FontWeight.Bold,
                            style = lumenCaptionStyle()
                        )
                        Text(
                            text = preset.description,
                            color = t.colors.mutedForeground,
                            style = LumenType.Meta.toTextStyle(lumenTextScale())
                        )
                    }
                }
            }

            // Action: Add Extension by URL
            item {
                TvFocusCard(
                    onClick = { 
                        selectedExtensionId = null 
                        isInstallingOrPreviewing = true
                        previewManifest = null
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { isFocused ->
                    Text(
                        text = "+ Install Custom Extension",
                        color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
                        fontWeight = FontWeight.Bold,
                        style = lumenCaptionStyle()
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))
            }

            if (extensions.isEmpty()) {
                item {
                    Text(text = "No extensions installed. Add Torrentio, AIOStreams, or paste a Stremio manifest URL.", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                }
            } else {
                items(extensions, key = { it.id }) { ext ->
                    val isSelected = ext.id == selectedExtensionId

                    TvFocusCard(
                        onClick = {
                            if (!inInstallFlow) {
                                selectedExtensionId = ext.id
                                isInstallingOrPreviewing = false
                                previewManifest = null
                                previewWarnings = emptyList()
                                validationError = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { isFocused ->
                        val healthColor = when (ext.health) {
                            ExtensionHealth.ACTIVE -> LumenExtendedColors.statusHealthy
                            ExtensionHealth.SLOW -> LumenTokens.Color.warning
                            ExtensionHealth.FAILED -> LumenExtendedColors.errorBright
                            ExtensionHealth.DISABLED -> LumenTokens.Color.textMuted
                            ExtensionHealth.INVALID_MANIFEST -> LumenExtendedColors.errorBright
                            ExtensionHealth.NEEDS_CONFIGURATION -> LumenExtendedColors.info
                            ExtensionHealth.UNKNOWN -> LumenTokens.Color.textMuted
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ext.name, 
                                    color = if (isSelected || isFocused) t.colors.foreground else t.colors.mutedForeground, 
                                    style = lumenBodyStyle(), 
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Priority: ${ext.priority}", 
                                    color = t.colors.mutedForeground, 
                                    style = lumenCaptionStyle(),
                                    modifier = Modifier.padding(top = LumenLegacySpace.xxs)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(LumenTokens.Shape.md)
                                    .background(healthColor.copy(alpha = 0.2f))
                                    .padding(horizontal = LumenLegacySpace.sm, vertical = LumenLegacySpace.xxs)
                            ) {
                                Text(
                                    text = ext.health.name,
                                    color = healthColor,
                                    style = LumenType.Eyebrow.toTextStyle(lumenTextScale()),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right Column: Extension Details & Control Panel
        LazyColumn(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight()
                .background(t.colors.surface.copy(alpha = 0.5f), LumenTokens.Shape.md)
                .padding(LumenLegacySpace.lg),
            verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
        ) {
            if (previewManifest != null) {
                val manifest = previewManifest!!
                // Show Preview Manifest UI
                item {
                    Text(text = "Preview Extension", fontSize = LumenType.size20, fontWeight = FontWeight.Bold, color = t.colors.mutedForeground)
                }
                item {
                    Text(text = manifest.name, fontSize = LumenType.size28, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                }
                item {
                    Text(text = "URL: ${com.example.calmsource.core.network.UrlRedactor.redactUrl(inputUrl)}", style = lumenCaptionStyle(), color = t.colors.mutedForeground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                item {
                    Text(text = manifest.description?.ifBlank { "No description provided." } ?: "No description provided.", style = lumenCaptionStyle(), color = t.colors.foreground)
                }

                if (previewWarnings.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(vertical = LumenLegacySpace.sm2), verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xs)) {
                            previewWarnings.forEach { warning ->
                                Text(text = "⚠ $warning", color = LumenTokens.Color.warning, style = lumenCaptionStyle())
                            }
                        }
                    }
                }

                // Capabilities listing
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xs)) {
                        Text(text = "Capabilities:", style = lumenCaptionStyle(), color = t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                        val resources = manifest.resources
                        if (resources.isEmpty()) {
                            Text(text = "• No capabilities declared", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                        } else {
                            resources.forEach { capability ->
                                Text(text = "✔ $capability", color = LumenExtendedColors.statusHealthy, style = lumenCaptionStyle())
                            }
                        }
                    }
                }

                if (validationError != null) {
                    item {
                        Text(text = validationError!!, color = LumenExtendedColors.errorBright, style = lumenCaptionStyle())
                    }
                }

                item {
                    TvFocusCard(
                        onClick = { if (!isInstalling) onConfirmInstall() },
                        modifier = Modifier.fillMaxWidth().focusRequester(confirmInstallFocusRequester)
                    ) { isFocused ->
                        Text(
                            text = if (isInstalling) "Installing…" else "Confirm & Install",
                            color = if (isFocused) LumenTokens.Color.textPrimary else LumenExtendedColors.statusHealthy,
                            fontWeight = FontWeight.Bold,
                            style = lumenCaptionStyle(),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                item {
                    TvFocusCard(
                        onClick = { dismissInstallPreview() },
                        modifier = Modifier.fillMaxWidth()
                    ) { isFocused ->
                        Text(
                            text = "Cancel",
                            color = if (isFocused) LumenTokens.Color.textPrimary else t.colors.mutedForeground,
                            fontWeight = FontWeight.Bold,
                            style = lumenCaptionStyle(),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

            } else if (selectedExtensionId == null) {
                // Install Form State
                item {
                    Text(text = "Install Extension", style = lumenTitleStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
                }
                
                // Text input for URL (simplified)
                item {
                    var isTextFieldFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(inputUrlFocusRequester)
                            .onFocusChanged { isTextFieldFocused = it.isFocused }
                            .border(if (isTextFieldFocused) LumenLegacySpace.xxs else 0.dp, if (isTextFieldFocused) t.colors.brand else Color.Transparent, LumenTokens.Shape.md)
                    )
                }

                if (validationError != null) {
                    item {
                        Text(text = validationError!!, color = LumenExtendedColors.errorBright, style = lumenCaptionStyle())
                    }
                }

                item {
                    TvFocusCard(
                        onClick = { if (!isPreviewing) onPreviewUrl(inputUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) { isFocused ->
                        Text(
                            text = if (isPreviewing) "Loading manifest..." else "Preview URL",
                            color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
                            fontWeight = FontWeight.Bold,
                            style = lumenCaptionStyle(),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(LumenLegacySpace.xl))
                }
                
                // QR Placeholder
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LumenLayout.skeletonTitleWidth)
                            .background(LumenTokens.Color.glass, LumenTokens.Shape.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Scan to push URL from phone (Coming soon)", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                    }
                }

            } else if (selectedExtension != null) {
                val ext = selectedExtension

                item {
                    Text(text = ext.name, style = lumenTitleStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
                }
                item {
                    Text(text = "URL: ${com.example.calmsource.core.network.UrlRedactor.redactUrl(ext.url)}", style = lumenCaptionStyle(), color = t.colors.mutedForeground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                item {
                    Text(text = ext.manifest?.description ?: "No description provided.", style = lumenCaptionStyle(), color = t.colors.foreground)
                }

                item {
                    val healthColor = when (ext.health) {
                        ExtensionHealth.ACTIVE -> LumenExtendedColors.statusHealthy
                        ExtensionHealth.SLOW -> LumenTokens.Color.warning
                        ExtensionHealth.FAILED -> LumenExtendedColors.errorBright
                        ExtensionHealth.DISABLED -> LumenTokens.Color.textMuted
                        ExtensionHealth.INVALID_MANIFEST -> LumenExtendedColors.errorBright
                        ExtensionHealth.NEEDS_CONFIGURATION -> LumenExtendedColors.info
                        ExtensionHealth.UNKNOWN -> LumenTokens.Color.textMuted
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)
                    ) {
                        Text(text = "Health Status:", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                        Box(
                            modifier = Modifier
                                .clip(LumenTokens.Shape.md)
                                .background(healthColor.copy(alpha = 0.2f))
                                .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xs)
                        ) {
                            Text(
                                text = ext.health.name,
                                color = healthColor,
                                style = LumenType.Meta.toTextStyle(lumenTextScale()),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (ext.health == ExtensionHealth.NEEDS_CONFIGURATION && configs.isEmpty()) {
                    item {
                        Text(
                            text = "This extension has no in-app configuration form. Configure it with the provider, then install the configured manifest URL.",
                            color = LumenTokens.Color.warning,
                            style = lumenCaptionStyle()
                        )
                    }
                }

                // Capabilities listing
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xs)) {
                        Text(text = "What this extension can provide:", style = lumenCaptionStyle(), color = t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                        val resources = ext.manifest?.resources ?: emptyList()
                        if (resources.isEmpty()) {
                            Text(text = "• No capabilities declared", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                        } else {
                            resources.forEach { capability ->
                                val readable = when (capability.lowercase()) {
                                    "catalog" -> "Provides movie/show catalogs"
                                    "search" -> "Integrates into Universal Search"
                                    "stream" -> "Provides stream watch options"
                                    "subtitles" -> "Provides subtitle tracks"
                                    else -> "Custom capability ($capability)"
                                }
                                Text(text = "✔ $readable", color = LumenExtendedColors.statusHealthy, style = lumenCaptionStyle())
                            }
                        }
                    }
                }

                // Permissions listing
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xs)) {
                        Text(text = "Granted Permissions:", style = lumenCaptionStyle(), color = t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                        if (ext.permissions.isEmpty()) {
                            Text(text = "• No permissions requested", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                        } else {
                            ext.permissions.forEach { perm ->
                                Text(text = "• ${perm.name.lowercase().replace('_', ' ')}", color = t.colors.foreground, style = lumenCaptionStyle())
                            }
                        }
                    }
                }

                // Configuration form
                if (configs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(LumenTokens.Radius.sm))
                        Text(text = "Configuration Settings:", style = lumenBodyStyle(), color = t.colors.foreground, fontWeight = FontWeight.Bold)
                    }
                    
                    configs.forEach { config ->
                        val currentVal = configValues[config.key] ?: ""
                        val isSecret = ExtensionRepository.isSecretConfigKey(config)

                        item(key = "config_${config.key}") {
                            when (config.type) {
                                "checkbox" -> {
                                    var isCheckboxFocused by remember { mutableStateOf(false) }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { isCheckboxFocused = it.isFocused }
                                            .border(if (isCheckboxFocused) 1.dp else 0.dp, if (isCheckboxFocused) t.colors.brand else Color.Transparent, LumenTokens.Shape.md)
                                            .clickable {
                                                val next = if (currentVal == "true") "false" else "true"
                                                configValues[config.key] = next
                                            }
                                            .focusable()
                                            .padding(LumenLegacySpace.xs)
                                    ) {
                                        androidx.compose.material3.Checkbox(
                                            checked = currentVal == "true",
                                            onCheckedChange = null
                                        )
                                        Spacer(modifier = Modifier.width(LumenLegacySpace.sm2))
                                        Text(text = config.title ?: config.key, color = t.colors.foreground, style = lumenCaptionStyle())
                                    }
                                }
                                "select" -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(text = config.title ?: config.key, color = t.colors.mutedForeground, style = lumenCaptionStyle())
                                        Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm), modifier = Modifier.padding(top = LumenLegacySpace.xs)) {
                                            config.options?.forEach { option ->
                                                val isSelected = currentVal == option || (currentVal.isEmpty() && config.default == option)
                                                var isOptFocused by remember { mutableStateOf(false) }
                                                Box(
                                                    modifier = Modifier
                                                        .background(if (isSelected) t.colors.brand else LumenTokens.Color.glass, LumenTokens.Shape.md)
                                                        .onFocusChanged { isOptFocused = it.isFocused }
                                                        .border(if (isOptFocused) 1.dp else 0.dp, if (isOptFocused) LumenTokens.Color.textPrimary else Color.Transparent, LumenTokens.Shape.md)
                                                        .clickable { configValues[config.key] = option }
                                                        .focusable()
                                                        .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xs)
                                                ) {
                                                    Text(
                                                        text = option,
                                                        color = if (isSelected) t.colors.background else t.colors.foreground,
                                                        style = lumenCaptionStyle(),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(text = config.title ?: config.key, color = t.colors.mutedForeground, style = lumenCaptionStyle())
                                        var isTextFocused by remember { mutableStateOf(false) }
                                        TvTextField(
                                            value = currentVal,
                                            onValueChange = { configValues[config.key] = it },
                                            singleLine = true,
                                            visualTransformation = if (isSecret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { isTextFocused = it.isFocused }
                                                .border(if (isTextFocused) 1.dp else 0.dp, if (isTextFocused) t.colors.brand else Color.Transparent, LumenTokens.Shape.md)
                                                .height(LumenLayout.epgRowHeight)
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "config_spacer_${config.key}") {
                            Spacer(modifier = Modifier.height(LumenLegacySpace.sm))
                        }
                    }

                    item {
                        TvFocusCard(
                            onClick = {
                                if (!isSavingConfig) {
                                    configSaveMessage = null
                                    isSavingConfig = true
                                    coroutineScope.launch {
                                        try {
                                            val result = ExtensionRepository.saveConfiguration(ext.id, configValues.toMap())
                                            configSaveIsError = !result.isSuccess
                                            configSaveMessage = if (result.isSuccess) {
                                                configLoadedForExtensionId = null
                                                "Configuration saved"
                                            } else {
                                                result.error?.message ?: "Failed to save configuration"
                                            }
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            configSaveIsError = true
                                            configSaveMessage = e.localizedMessage ?: "Failed to save configuration"
                                        } finally {
                                            isSavingConfig = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { isFocused ->
                            Text(
                                text = if (isSavingConfig) "Saving…" else "Save Configuration",
                                color = if (isFocused) t.colors.background else t.colors.foreground,
                                fontWeight = FontWeight.Bold,
                                style = lumenCaptionStyle(),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }

                    if (configSaveMessage != null) {
                        item {
                            Text(
                                text = configSaveMessage!!,
                                color = if (configSaveIsError) LumenExtendedColors.errorBright else LumenExtendedColors.statusHealthy,
                                style = lumenCaptionStyle(),
                                modifier = Modifier.padding(top = LumenLegacySpace.xs)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(LumenLegacySpace.xs))
                }

                // Actions area
                item {
                    Text(text = "Configure Action Panel", style = lumenCaptionStyle(), color = t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                }
                
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm)
                        ) {
                            // Action 1: Toggle Enable / Disable
                            TvFocusCard(
                                onClick = {
                                    ExtensionRepository.toggleExtension(
                                        ext.id,
                                        !ext.isEnabled
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { isFocused ->
                                Text(
                                    text = if (ext.isEnabled) "Disable" else "Enable",
                                    color = if (isFocused) t.colors.foreground else t.colors.brand,
                                    fontWeight = FontWeight.Bold,
                                    style = lumenCaptionStyle()
                                )
                            }

                            // Action 2: Show/Hide Json Manifest
                            TvFocusCard(
                                onClick = { showRawJson = !showRawJson },
                                modifier = Modifier.weight(1f)
                            ) { isFocused ->
                                Text(
                                    text = if (showRawJson) "Hide JSON" else "Advanced Details",
                                    color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
                                    fontWeight = FontWeight.Bold,
                                    style = lumenCaptionStyle()
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm)
                        ) {
                            // Action 3: Priority Up
                            TvFocusCard(
                                onClick = {
                                    ExtensionRepository.updatePriority(
                                        ext.id,
                                        (ext.priority - 10).coerceAtLeast(0)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { isFocused ->
                                Text(
                                    text = "Priority Up",
                                    color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
                                    style = lumenCaptionStyle()
                                )
                            }

                            // Action 4: Priority Down
                            TvFocusCard(
                                onClick = {
                                    ExtensionRepository.updatePriority(
                                        ext.id,
                                        ext.priority + 10
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { isFocused ->
                                Text(
                                    text = "Priority Down",
                                    color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
                                    style = lumenCaptionStyle()
                                )
                            }
                        }

                        // Action 5: Remove Extension
                        TvFocusCard(
                            onClick = { extensionToRemove = ext },
                            modifier = Modifier.fillMaxWidth()
                        ) { isFocused ->
                            Text(
                                text = "Remove Extension",
                                color = if (isFocused) LumenTokens.Color.textPrimary else LumenExtendedColors.errorBright,
                                fontWeight = FontWeight.Bold,
                                style = lumenCaptionStyle(),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                if (showRawJson) {
                    item {
                        Spacer(modifier = Modifier.height(LumenTokens.Radius.sm))
                        Text(text = "Raw Manifest Metadata:", style = lumenCaptionStyle(), color = t.colors.foreground, fontWeight = FontWeight.Bold)
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LumenTokens.Color.borderSubtle, LumenTokens.Shape.xs)
                                .padding(LumenLegacySpace.md)
                        ) {
                            Text(
                                text = rawJson,
                                color = t.colors.foreground,
                                style = LumenType.Meta.toTextStyle(lumenTextScale()),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No catalog extensions yet", color = t.colors.mutedForeground, style = lumenBodyStyle())
                    }
                }
            }
        }
    }

    extensionToRemove?.let { target ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { extensionToRemove = null }) {
            Box(
                modifier = Modifier
                    .width(LumenLayout.heroHeightLg)
                    .background(t.colors.surface, LumenTokens.Shape.md)
                    .padding(LumenLegacySpace.xxl)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm)) {
                    Text(
                        text = "Remove extension?",
                        color = t.colors.foreground,
                        fontSize = LumenType.size22,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Remove ${target.name}? This cannot be undone.",
                        color = t.colors.mutedForeground,
                        style = lumenCaptionStyle(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)) {
                        TvFocusCard(
                            onClick = {
                                ExtensionRepository.removeExtension(target.id)
                                if (selectedExtensionId == target.id) {
                                    selectedExtensionId = null
                                    configLoadedForExtensionId = null
                                }
                                extensionToRemove = null
                                runCatching { stableFocusRequester.requestFocus() }
                            },
                            modifier = Modifier.weight(1f),
                        ) { isFocused ->
                            Text(
                                text = "Remove",
                                color = if (isFocused) LumenTokens.Color.textPrimary else LumenExtendedColors.errorBright,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                        TvFocusCard(
                            onClick = { extensionToRemove = null },
                            modifier = Modifier.weight(1f),
                        ) { isFocused ->
                            Text(
                                text = "Cancel",
                                color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }
            }
        }
    }
}

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
import com.example.calmsource.feature.extensions.ExtensionRepository
import com.example.calmsource.feature.extensions.RecommendedStremioAddons
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun TvExtensionsScreen(onBack: () -> Unit) {
    val t = LocalLumenTokens.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val stableFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val extensions by ExtensionRepository.extensions.collectAsState()
    val prefs by UserPreferencesRepository.preferences.collectAsState()
    var selectedExtensionId by remember { mutableStateOf<String?>(null) }
    var isInstallingOrPreviewing by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    var inputUrl by remember { mutableStateOf("") }
    var isPreviewing by remember { mutableStateOf(false) }
    var previewManifest by remember { mutableStateOf<com.example.calmsource.core.model.ExtensionManifest?>(null) }
    var previewWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var validationError by remember { mutableStateOf<String?>(null) }
    // Guards a confirm-install in flight so rapid D-pad activations can't fire confirmInstall twice
    // (bug #18). Tracks the in-flight preview so a newer preview cancels a slower previous one and
    // results can't arrive out of order (bug #21).
    var isInstalling by remember { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Feedback for the per-extension "Save Configuration" action (bug #22).
    var configSaveMessage by remember(selectedExtensionId) { mutableStateOf<String?>(null) }
    var configSaveIsError by remember(selectedExtensionId) { mutableStateOf(false) }

    val inputUrlFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val confirmInstallFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

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
    LaunchedEffect(extensions, isInstallingOrPreviewing) {
        if (isInstallingOrPreviewing) return@LaunchedEffect
        if (selectedExtensionId != null && extensions.none { it.id == selectedExtensionId }) {
            selectedExtensionId = extensions.firstOrNull()?.id
        } else if (selectedExtensionId == null && extensions.isNotEmpty()) {
            selectedExtensionId = extensions.first().id
        }
    }

    val selectedExtension = extensions.find { it.id == selectedExtensionId }

    var showRawJson by remember(selectedExtensionId) { mutableStateOf(false) }

    val configs = remember(selectedExtension) {
        selectedExtension?.manifest?.let { ExtensionRepository.getAddonConfigList(it) } ?: emptyList()
    }

    val initialValues = remember(selectedExtension, configs) {
        if (selectedExtension == null) emptyMap()
        else {
            val map = com.example.calmsource.core.network.StremioAddonClient.parseConfigFromUrl(selectedExtension.url).toMutableMap()
            configs.forEach { config ->
                if (com.example.calmsource.feature.extensions.ExtensionRepository.isSecretConfigKey(config)) {
                    val sec = com.example.calmsource.core.network.ExtensionSecrets.readSecret(selectedExtension.id, config.key)
                    if (sec != null) {
                        map[config.key] = sec
                    }
                }
            }
            map
        }
    }

    val configValues = remember(selectedExtensionId) {
        mutableStateMapOf<String, String>()
    }
    LaunchedEffect(selectedExtensionId, initialValues) {
        configValues.clear()
        configValues.putAll(initialValues)
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

                        val httpsWarning = if (!trimmedUrl.startsWith("https://", ignoreCase = true)) {
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
                        isPreviewing = false
                        previewManifest = null
                        validationError = e.localizedMessage ?: "Failed to load manifest"
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
                        stableFocusRequester.requestFocus()
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
        modifier = Modifier
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
            
            item {
                Text(text = "Extensions", fontSize = LumenType.size28, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                Text(text = "Configure catalog, search, and stream providers", fontSize = LumenType.size12, color = t.colors.mutedForeground, modifier = Modifier.padding(bottom = LumenLegacySpace.md))
            }

            items(RecommendedStremioAddons.presets, key = { "preset_${it.manifestId}" }) { preset ->
                val installed = RecommendedStremioAddons.installedProvider(preset, extensions)
                TvFocusCard(
                    onClick = {
                        if (installed == null) {
                            onPreviewUrl(preset.manifestUrl)
                        } else {
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
                            fontSize = LumenType.size14
                        )
                        Text(
                            text = preset.description,
                            color = t.colors.mutedForeground,
                            fontSize = LumenType.size11
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
                        fontSize = LumenType.size14
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))
            }

            if (extensions.isEmpty()) {
                item {
                    Text(text = "No extensions installed. Add Torrentio, AIOStreams, or paste a Stremio manifest URL.", color = t.colors.mutedForeground, fontSize = LumenType.size14)
                }
            } else {
                items(extensions, key = { it.id }) { ext ->
                    val isSelected = ext.id == selectedExtensionId

                    TvFocusCard(
                        onClick = { 
                            selectedExtensionId = ext.id
                            isInstallingOrPreviewing = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { 
                                if (it.isFocused) {
                                    selectedExtensionId = ext.id
                                    isInstallingOrPreviewing = false
                                }
                            }
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
                                    fontSize = LumenType.size16, 
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Priority: ${ext.priority}", 
                                    color = t.colors.mutedForeground, 
                                    fontSize = LumenType.size12,
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
                                    fontSize = LumenType.size10,
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
                    Text(text = "URL: ${com.example.calmsource.core.network.UrlRedactor.redactUrl(inputUrl)}", fontSize = LumenType.size12, color = t.colors.mutedForeground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                item {
                    Text(text = manifest.description?.ifBlank { "No description provided." } ?: "No description provided.", fontSize = LumenType.size14, color = t.colors.foreground)
                }

                if (previewWarnings.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(vertical = LumenLegacySpace.sm2), verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xs)) {
                            previewWarnings.forEach { warning ->
                                Text(text = "⚠ $warning", color = LumenTokens.Color.warning, fontSize = LumenType.size12)
                            }
                        }
                    }
                }

                // Capabilities listing
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xs)) {
                        Text(text = "Capabilities:", fontSize = LumenType.size13, color = t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                        val resources = manifest.resources
                        if (resources.isEmpty()) {
                            Text(text = "• No capabilities declared", color = t.colors.mutedForeground, fontSize = LumenType.size12)
                        } else {
                            resources.forEach { capability ->
                                Text(text = "✔ $capability", color = LumenExtendedColors.statusHealthy, fontSize = LumenType.size13)
                            }
                        }
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
                            fontSize = LumenType.size14,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                item {
                    TvFocusCard(
                        onClick = {
                            stableFocusRequester.requestFocus()
                            previewManifest = null
                            isInstallingOrPreviewing = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { isFocused ->
                        Text(
                            text = "Cancel",
                            color = if (isFocused) LumenTokens.Color.textPrimary else t.colors.mutedForeground,
                            fontWeight = FontWeight.Bold,
                            fontSize = LumenType.size14,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

            } else if (selectedExtensionId == null) {
                // Install Form State
                item {
                    Text(text = "Install Extension", fontSize = LumenType.size24, fontWeight = FontWeight.Bold, color = t.colors.foreground)
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
                        Text(text = validationError!!, color = LumenExtendedColors.errorBright, fontSize = LumenType.size12)
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
                            fontSize = LumenType.size14,
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
                        Text(text = "Scan to push URL from phone (Coming soon)", color = t.colors.mutedForeground, fontSize = LumenType.size12)
                    }
                }

            } else if (selectedExtension != null) {
                val ext = selectedExtension

                item {
                    Text(text = ext.name, fontSize = LumenType.size24, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                }
                item {
                    Text(text = "URL: ${com.example.calmsource.core.network.UrlRedactor.redactUrl(ext.url)}", fontSize = LumenType.size12, color = t.colors.mutedForeground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                item {
                    Text(text = ext.manifest?.description ?: "No description provided.", fontSize = LumenType.size14, color = t.colors.foreground)
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
                        Text(text = "Health Status:", color = t.colors.mutedForeground, fontSize = LumenType.size13)
                        Box(
                            modifier = Modifier
                                .clip(LumenTokens.Shape.md)
                                .background(healthColor.copy(alpha = 0.2f))
                                .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xs)
                        ) {
                            Text(
                                text = ext.health.name,
                                color = healthColor,
                                fontSize = LumenType.size11,
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
                            fontSize = LumenType.size13
                        )
                    }
                }

                // Capabilities listing
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xs)) {
                        Text(text = "What this extension can provide:", fontSize = LumenType.size13, color = t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                        val resources = ext.manifest?.resources ?: emptyList()
                        if (resources.isEmpty()) {
                            Text(text = "• No capabilities declared", color = t.colors.mutedForeground, fontSize = LumenType.size12)
                        } else {
                            resources.forEach { capability ->
                                val readable = when (capability.lowercase()) {
                                    "catalog" -> "Provides movie/show catalogs"
                                    "search" -> "Integrates into Universal Search"
                                    "stream" -> "Provides stream watch options"
                                    "subtitles" -> "Provides subtitle tracks"
                                    else -> "Custom capability ($capability)"
                                }
                                Text(text = "✔ $readable", color = LumenExtendedColors.statusHealthy, fontSize = LumenType.size13)
                            }
                        }
                    }
                }

                // Permissions listing
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xs)) {
                        Text(text = "Granted Permissions:", fontSize = LumenType.size13, color = t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                        if (ext.permissions.isEmpty()) {
                            Text(text = "• No permissions requested", color = t.colors.mutedForeground, fontSize = LumenType.size12)
                        } else {
                            ext.permissions.forEach { perm ->
                                Text(text = "• ${perm.name.lowercase().replace('_', ' ')}", color = t.colors.foreground, fontSize = LumenType.size13)
                            }
                        }
                    }
                }

                // Configuration form
                if (configs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(LumenTokens.Radius.sm))
                        Text(text = "Configuration Settings:", fontSize = LumenType.size16, color = t.colors.foreground, fontWeight = FontWeight.Bold)
                    }
                    
                    configs.forEach { config ->
                        val currentVal = configValues[config.key] ?: ""
                        val isSecret = com.example.calmsource.feature.extensions.ExtensionRepository.isSecretConfigKey(config)

                        item {
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
                                        Text(text = config.title ?: config.key, color = t.colors.foreground, fontSize = LumenType.size13)
                                    }
                                }
                                "select" -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(text = config.title ?: config.key, color = t.colors.mutedForeground, fontSize = LumenType.size13)
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
                                                        fontSize = LumenType.size12,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(text = config.title ?: config.key, color = t.colors.mutedForeground, fontSize = LumenType.size12)
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
                        item {
                            Spacer(modifier = Modifier.height(LumenLegacySpace.sm))
                        }
                    }

                    item {
                        TvFocusCard(
                            onClick = {
                                configSaveMessage = null
                                coroutineScope.launch {
                                    val result = ExtensionRepository.saveConfiguration(ext.id, configValues.toMap())
                                    configSaveIsError = !result.isSuccess
                                    configSaveMessage = if (result.isSuccess) {
                                        "Configuration saved"
                                    } else {
                                        result.error?.message ?: "Failed to save configuration"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { isFocused ->
                            Text(
                                text = "Save Configuration",
                                color = if (isFocused) t.colors.background else t.colors.foreground,
                                fontWeight = FontWeight.Bold,
                                fontSize = LumenType.size14,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }

                    if (configSaveMessage != null) {
                        item {
                            Text(
                                text = configSaveMessage!!,
                                color = if (configSaveIsError) LumenExtendedColors.errorBright else LumenExtendedColors.statusHealthy,
                                fontSize = LumenType.size12,
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
                    Text(text = "Configure Action Panel", fontSize = LumenType.size14, color = t.colors.mutedForeground, fontWeight = FontWeight.Bold)
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
                                    fontSize = LumenType.size13
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
                                    fontSize = LumenType.size13
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
                                    fontSize = LumenType.size13
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
                                    fontSize = LumenType.size13
                                )
                            }
                        }

                        // Action 5: Remove Extension
                        TvFocusCard(
                            onClick = {
                                stableFocusRequester.requestFocus()
                                selectedExtensionId = null
                                ExtensionRepository.removeExtension(ext.id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { isFocused ->
                            Text(
                                text = "Remove Extension",
                                color = if (isFocused) LumenTokens.Color.textPrimary else LumenExtendedColors.errorBright,
                                fontWeight = FontWeight.Bold,
                                fontSize = LumenType.size13,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                if (showRawJson) {
                    item {
                        Spacer(modifier = Modifier.height(LumenTokens.Radius.sm))
                        Text(text = "Raw Manifest Metadata:", fontSize = LumenType.size14, color = t.colors.foreground, fontWeight = FontWeight.Bold)
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
                                fontSize = LumenType.size11,
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
                        Text(text = "No catalog extensions yet", color = t.colors.mutedForeground, fontSize = LumenType.size16)
                    }
                }
            }
        }
    }
}

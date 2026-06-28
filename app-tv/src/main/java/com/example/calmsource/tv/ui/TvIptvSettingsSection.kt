package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.calmsource.core.model.*
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.feature.iptv.IptvGroupMode
import com.example.calmsource.feature.iptv.IptvOptimizationPreferences
import com.example.calmsource.feature.iptv.IptvOptimizationStats
import com.example.calmsource.feature.iptv.XtreamRepository
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun TvIptvScreen(onBack: () -> Unit) {
    val t = LocalLumenTokens.current
    val stableFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val dialogCancelFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val providers by IPTVRepository.providers.collectAsState()
    val epgSources by IPTVRepository.epgSources.collectAsState()
    val syncStates by IPTVRepository.syncStates.collectAsState()
    val xtreamSyncProgress by XtreamRepository.syncProgress.collectAsState()
    val prefs by UserPreferencesRepository.preferences.collectAsState()
    val optimizationPreferences by IPTVRepository.optimizationPreferences.collectAsState()
    val optimizationStats by IPTVRepository.optimizationStats.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showM3uForm by remember { mutableStateOf(false) }
    var m3uName by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var m3uAddError by remember { mutableStateOf<String?>(null) }
    var isAddingM3u by remember { mutableStateOf(false) }
    var showXtreamForm by remember { mutableStateOf(false) }
    var xtreamName by remember { mutableStateOf("") }
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamUsername by remember { mutableStateOf("") }
    var xtreamPassword by remember { mutableStateOf("") }
    var xtreamAddError by remember { mutableStateOf<String?>(null) }
    var showHttpWarning by remember { mutableStateOf(false) }
    var isAddingXtream by remember { mutableStateOf(false) }
    var providerToDelete by remember { mutableStateOf<IPTVProvider?>(null) }
    var isDeletingProvider by remember { mutableStateOf(false) }
    var providerDeleteError by remember { mutableStateOf<String?>(null) }
    var showEpgForm by remember { mutableStateOf(false) }
    var epgName by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var epgProviderId by remember { mutableStateOf<String?>(null) }
    var epgAddError by remember { mutableStateOf<String?>(null) }
    var isAddingEpg by remember { mutableStateOf(false) }

    LaunchedEffect(providers) {
        if (epgProviderId == null && providers.isNotEmpty()) {
            epgProviderId = providers.first().id
        }
    }

    var xtreamAuthMessage by remember { mutableStateOf("Connecting and validating provider...") }
    LaunchedEffect(isAddingXtream) {
        if (isAddingXtream) {
            val startTime = System.currentTimeMillis()
            while (isAddingXtream) {
                val elapsed = System.currentTimeMillis() - startTime
                xtreamAuthMessage = when {
                    elapsed < 30_000L -> "Connecting and validating provider..."
                    elapsed < 60_000L -> "Still connecting... (Checking slow server catalog...)"
                    else -> "Authenticating... (Retrieving IPTV channels list...)"
                }
                delay(1000)
            }
        } else {
            xtreamAuthMessage = "Connecting and validating provider..."
        }
    }

    LaunchedEffect(providerToDelete, isDeletingProvider) {
        if (providerToDelete != null) {
            dialogCancelFocusRequester.requestFocus()
        }
    }

    fun connectAndSyncM3u() {
        if (isAddingM3u) return
        val url = m3uUrl.trim()
        val scheme = runCatching { java.net.URI(url).scheme?.lowercase() }.getOrNull()
        when {
            url.isBlank() -> {
                m3uAddError = "Playlist URL cannot be blank"
                return
            }
            scheme != "http" && scheme != "https" -> {
                m3uAddError = "Only HTTP and HTTPS playlist URLs are supported"
                return
            }
            scheme == "http" && !prefs.allowCleartextUserSources -> {
                m3uAddError = "HTTP playlist URLs are blocked by your cleartext setting."
                return
            }
        }

        isAddingM3u = true
        coroutineScope.launch {
            try {
                val provider = IPTVRepository.addM3uProvider(
                    name = m3uName.trim().ifBlank { "M3U Playlist" },
                    playlistUrl = url
                )
                m3uName = ""
                m3uUrl = ""
                m3uAddError = null
                stableFocusRequester.requestFocus()
                showM3uForm = false
                IPTVRepository.syncPlaylistFromUrl(provider.id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                m3uAddError = com.example.calmsource.core.network.UrlRedactor
                    .redactErrorMessage(e.message ?: "Could not add playlist")
            } finally {
                isAddingM3u = false
            }
        }
    }

    fun connectAndSyncXtream() {
        if (isAddingXtream) return
        isAddingXtream = true
        coroutineScope.launch {
            try {
                val name = if (xtreamName.isNotBlank()) xtreamName else "Xtream TV"
                val result = IPTVRepository.addXtreamProvider(
                    name,
                    xtreamServer,
                    xtreamUsername,
                    xtreamPassword
                )
                if (result.isSuccess) {
                    val provider = result.getOrThrow()
                    xtreamName = ""
                    xtreamServer = ""
                    xtreamUsername = ""
                    xtreamPassword = ""
                    xtreamAddError = null
                    stableFocusRequester.requestFocus()
                    showXtreamForm = false
                    IPTVRepository.startXtreamProviderSync(provider.id)
                } else {
                    val rawError = result.exceptionOrNull()?.message ?: "Connection failed"
                    xtreamAddError = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(rawError)
                }
            } finally {
                isAddingXtream = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(LumenLegacySpace.xxl),
        verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
    ) {
        item {
            TvFocusCard(
                onClick = onBack, 
                modifier = Modifier
                    .wrapContentSize()
                    .padding(bottom = LumenLegacySpace.lg)
                    .focusRequester(stableFocusRequester)
            ) {
                Text(text = "← Back", color = t.colors.foreground)
            }
        }

        if (!XtreamRepository.isEncryptedStorageAvailable()) {
            item {
                androidx.compose.material3.Surface(
                    color = LumenExtendedColors.errorSoft,
                    shape = LumenTokens.Shape.sm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = LumenLegacySpace.md)
                ) {
                    Column(modifier = Modifier.padding(LumenLegacySpace.lg)) {
                        Text(
                            text = "Warning: Secure Storage Unavailable",
                            fontWeight = FontWeight.Bold,
                            color = LumenTokens.Color.bg,
                            fontSize = LumenType.size16
                        )
                        Spacer(modifier = Modifier.height(LumenLegacySpace.xs))
                        Text(
                            text = "Encrypted storage is unavailable. Your credentials will only be saved in-memory and will be lost on app exit.",
                            color = LumenTokens.Color.bg,
                            fontSize = LumenType.size14
                        )
                    }
                }
            }
        }
        item {
            Text(text = "TV Sources (M3U Playlists)", fontSize = LumenType.size24, fontWeight = FontWeight.Bold, color = t.colors.foreground, modifier = Modifier.padding(bottom = LumenLegacySpace.md))
        }
        item {
            TvIptvOptimizationSettings(
                preferences = optimizationPreferences,
                stats = optimizationStats,
                onUpdate = IPTVRepository::updateOptimizationPreferences,
                onReset = IPTVRepository::resetOptimizationPreferences
            )
        }

        if (providers.isEmpty()) {
            item {
                Text(
                    text = "No IPTV providers yet. Add an M3U playlist or Xtream login below.",
                    color = t.colors.mutedForeground,
                    fontSize = LumenType.size14,
                    modifier = Modifier.padding(bottom = LumenLegacySpace.lg)
                )
            }
        } else {
            item {
                if (providerToDelete != null) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = {
                            // While deleting, dismiss is blocked — the in-dialog
                            // "Cancel" button is the only way out.
                            if (!isDeletingProvider) {
                                stableFocusRequester.requestFocus()
                                providerToDelete = null
                                providerDeleteError = null
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .width(LumenLayout.sheetMaxWidth)
                                .background(t.colors.surface, LumenTokens.Shape.md)
                                .padding(LumenLegacySpace.xxl)
                        ) {
                            Column {
                                Text("Delete Provider", color = t.colors.foreground, fontSize = LumenType.size20, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(LumenLegacySpace.md))
                                Text("Are you sure you want to delete '${providerToDelete?.name}'? This will remove all associated channels and data.", color = t.colors.mutedForeground, fontSize = LumenType.size16)
                                providerDeleteError?.let { error ->
                                    Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))
                                    Text(error, color = LumenExtendedColors.errorBright, fontSize = LumenType.size14)
                                }
                                if (isDeletingProvider) {
                                    Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            color = t.colors.brand,
                                            strokeWidth = LumenExtendedColors.focusRingWidth,
                                            modifier = Modifier.size(LumenLegacySpace.xxl)
                                        )
                                        Text(
                                            "Removing provider and channel data…",
                                            color = t.colors.mutedForeground,
                                            fontSize = LumenType.size14
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(LumenLegacySpace.xxl))
                                when (isDeletingProvider) {
                                    true -> {
                                        // Show cancel button while deleting
                                        TvFocusCard(
                                            onClick = {
                                                coroutineScope.launch {
                                                    // No way to cancel deleteProvider mid-execution,
                                                    // but dismiss the dialog for UX.
                                                    stableFocusRequester.requestFocus()
                                                    providerToDelete = null
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().focusRequester(dialogCancelFocusRequester)
                                        ) { isFocused ->
                                            Box(
                                                modifier = Modifier.padding(LumenLegacySpace.md),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Cancel", color = if (isFocused) t.colors.foreground else t.colors.mutedForeground)
                                            }
                                        }
                                    }
                                    false -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)) {
                                            TvFocusCard(
                                                onClick = {
                                                    stableFocusRequester.requestFocus()
                                                    providerToDelete = null
                                                    providerDeleteError = null
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .focusRequester(dialogCancelFocusRequester)
                                            ) { isFocused ->
                                                Box(modifier = Modifier.fillMaxWidth().padding(LumenLegacySpace.md), contentAlignment = Alignment.Center) {
                                                    Text("Cancel", color = if (isFocused) t.colors.foreground else t.colors.mutedForeground)
                                                }
                                            }
                                            TvFocusCard(
                                                onClick = {
                                                    val provider = providerToDelete
                                                    if (provider != null && !isDeletingProvider) {
                                                        dialogCancelFocusRequester.requestFocus()
                                                        isDeletingProvider = true
                                                        providerDeleteError = null
                                                        coroutineScope.launch {
                                                            try {
                                                                IPTVRepository.deleteProvider(provider.id)
                                                                stableFocusRequester.requestFocus()
                                                                providerToDelete = null
                                                            } catch (error: kotlinx.coroutines.CancellationException) {
                                                                throw error
                                                            } catch (_: Exception) {
                                                                providerDeleteError = "Could not delete this provider. Please try again."
                                                            } finally {
                                                                isDeletingProvider = false
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { isFocused ->
                                                Box(modifier = Modifier.fillMaxWidth().padding(LumenLegacySpace.md), contentAlignment = Alignment.Center) {
                                                    Text("Delete", color = if (isFocused) t.colors.foreground else Color.Red)
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
            items(providers, key = { it.id }) { provider ->
                val syncState = syncStates[provider.id] ?: ProviderSyncState(provider.id, ProviderSyncStatus.IDLE)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = LumenLegacySpace.xs)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (provider.type == IPTVProviderType.XTREAM) {
                            val providerProgress = xtreamSyncProgress?.takeIf { it.providerId == provider.id }
                            TvXtreamProviderItem(
                                provider = provider,
                                syncProgress = providerProgress,
                                onSync = {
                                    IPTVRepository.startXtreamProviderSync(provider.id)
                                }
                            )
                        } else {
                            TvIptvItem(provider = provider, syncState = syncState, onSync = {
                                coroutineScope.launch {
                                    IPTVRepository.syncPlaylistFromUrl(provider.id)
                                }
                            })
                        }
                    }
                    TvFocusCard(
                        onClick = {
                            coroutineScope.launch {
                                IPTVRepository.setProviderEnabled(provider.id, !provider.isEnabled)
                            }
                        },
                        modifier = Modifier.width(LumenLayout.inputWidthSm)
                    ) { isFocused ->
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = LumenLegacySpace.md), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (provider.isEnabled) "Disable" else "Enable",
                                color = if (isFocused) t.colors.foreground else t.colors.brand,
                                fontSize = LumenType.size13,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TvFocusCard(
                        onClick = {
                            providerDeleteError = null
                            providerToDelete = provider
                        },
                        modifier = Modifier.width(LumenLayout.inputWidthXs)
                    ) { isFocused ->
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = LumenLegacySpace.md), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Delete",
                                color = if (isFocused) LumenTokens.Color.textPrimary else LumenExtendedColors.errorBright,
                                fontSize = LumenType.size14,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md), modifier = Modifier.fillMaxWidth()) {
                TvFocusCard(
                    onClick = {
                        showM3uForm = true
                        showXtreamForm = false
                    },
                    modifier = Modifier.weight(1f)
                ) { isFocused ->
                    Text(
                        text = "+ Add M3U Playlist",
                        color = if (isFocused) t.colors.foreground else t.colors.brand,
                        fontWeight = FontWeight.Bold,
                        fontSize = LumenType.size15,
                        modifier = Modifier.padding(vertical = LumenLegacySpace.sm2, horizontal = LumenLegacySpace.md)
                    )
                }
                TvFocusCard(
                    onClick = {
                        showXtreamForm = true
                        showM3uForm = false
                    },
                    modifier = Modifier.weight(1f)
                ) { isFocused ->
                    Text(
                        text = "+ Add Xtream Login",
                        color = if (isFocused) t.colors.foreground else t.colors.brand,
                        fontWeight = FontWeight.Bold,
                        fontSize = LumenType.size15,
                        modifier = Modifier.padding(vertical = LumenLegacySpace.sm2, horizontal = LumenLegacySpace.md)
                    )
                }
            }
        }

        if (showM3uForm) {
            item {
                Text(text = "Add M3U Playlist", fontSize = LumenType.size20, fontWeight = FontWeight.Bold, color = t.colors.foreground, modifier = Modifier.padding(top = LumenLegacySpace.lg, bottom = LumenLegacySpace.sm2))
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm), modifier = Modifier.padding(bottom = LumenLegacySpace.lg)) {
                    var isNameFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = m3uName,
                        onValueChange = { m3uName = it; m3uAddError = null },
                        placeholder = { Text("Playlist Name (Optional)", fontSize = LumenType.size14) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = LumenType.size16),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isNameFocused = it.isFocused }
                            .border(if (isNameFocused) LumenLegacySpace.xxs else 0.dp, if (isNameFocused) t.colors.brand else Color.Transparent, LumenTokens.Shape.md)
                    )
                    var isUrlFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it; m3uAddError = null },
                        placeholder = { Text("Playlist URL", fontSize = LumenType.size14) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = LumenType.size16),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isUrlFocused = it.isFocused }
                            .border(if (isUrlFocused) LumenLegacySpace.xxs else 0.dp, if (isUrlFocused) t.colors.brand else Color.Transparent, LumenTokens.Shape.md)
                    )
                    m3uAddError?.let { error ->
                        Text(text = error, color = LumenExtendedColors.errorBright, fontSize = LumenType.size14)
                    }
                    if (isAddingM3u) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(LumenLayout.iconSm),
                                strokeWidth = LumenLegacySpace.xxs,
                                color = t.colors.brand
                            )
                            Text("Adding playlist and starting sync...", color = t.colors.mutedForeground, fontSize = LumenType.size14)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md), modifier = Modifier.fillMaxWidth()) {
                        TvFocusCard(
                            onClick = {
                                if (!isAddingM3u) {
                                    stableFocusRequester.requestFocus()
                                    showM3uForm = false
                                    m3uAddError = null
                                    m3uName = ""
                                    m3uUrl = ""
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { isFocused ->
                            Text(
                                text = "Cancel",
                                color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
                                fontWeight = FontWeight.Bold,
                                fontSize = LumenType.size15,
                                modifier = Modifier.padding(vertical = LumenLegacySpace.sm2, horizontal = LumenLegacySpace.md)
                            )
                        }
                        TvFocusCard(
                            onClick = {
                                if (!isAddingM3u) connectAndSyncM3u()
                            },
                            modifier = Modifier.weight(1f)
                        ) { isFocused ->
                            Text(
                                text = if (isAddingM3u) "Adding..." else "Add & Sync",
                                color = if (isFocused) t.colors.foreground else t.colors.brand,
                                fontWeight = FontWeight.Bold,
                                fontSize = LumenType.size15,
                                modifier = Modifier.padding(vertical = LumenLegacySpace.sm2, horizontal = LumenLegacySpace.md)
                            )
                        }
                    }
                }
            }
        }

        if (showXtreamForm || (providers.isEmpty() && !showM3uForm)) {
            item {
                Text(text = "Add Xtream Login", fontSize = LumenType.size20, fontWeight = FontWeight.Bold, color = t.colors.foreground, modifier = Modifier.padding(top = LumenLegacySpace.lg, bottom = LumenLegacySpace.sm2))
            }
            item {                if (showHttpWarning) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showHttpWarning = false }) {
                        Box(
                            modifier = Modifier
                                .width(LumenLayout.sheetMaxWidth)
                                .background(t.colors.surface, LumenTokens.Shape.md)
                                .padding(LumenLegacySpace.xxl)
                        ) {
                            Column {
                                Text("Insecure Connection", color = t.colors.foreground, fontSize = LumenType.size20, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(LumenLegacySpace.md))
                                Text("You are attempting to connect via an unencrypted HTTP URL. This connection is not secure. Are you sure you want to proceed?", color = t.colors.mutedForeground, fontSize = LumenType.size16)
                                Spacer(modifier = Modifier.height(LumenLegacySpace.xxl))
                                Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)) {
                                    TvFocusCard(
                                        onClick = {
                                            stableFocusRequester.requestFocus()
                                            showHttpWarning = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { isFocused ->
                                        Text("Cancel", color = if (isFocused) t.colors.foreground else t.colors.mutedForeground, modifier = Modifier.padding(LumenLegacySpace.md))
                                    }
                                    TvFocusCard(
                                        onClick = {
                                            stableFocusRequester.requestFocus()
                                            showHttpWarning = false
                                            connectAndSyncXtream()
                                        }, 
                                        modifier = Modifier.weight(1f)
                                    ) { isFocused ->
                                        Text("Proceed Anyway", color = if (isFocused) t.colors.foreground else t.colors.brand, modifier = Modifier.padding(LumenLegacySpace.md))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm), modifier = Modifier.padding(bottom = LumenLegacySpace.lg)) {
                    var isNameFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamName,
                        onValueChange = { xtreamName = it; xtreamAddError = null },
                        placeholder = { Text("Provider Name (Optional)", fontSize = LumenType.size14) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = LumenType.size16),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isNameFocused = it.isFocused }
                            .border(if (isNameFocused) LumenLegacySpace.xxs else 0.dp, if (isNameFocused) t.colors.brand else Color.Transparent, LumenTokens.Shape.md)
                    )
                    var isServerFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamServer,
                        onValueChange = { xtreamServer = it; xtreamAddError = null },
                        placeholder = { Text("http://host:port", fontSize = LumenType.size14) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = LumenType.size16),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isServerFocused = it.isFocused }
                            .border(if (isServerFocused) LumenLegacySpace.xxs else 0.dp, if (isServerFocused) t.colors.brand else Color.Transparent, LumenTokens.Shape.md)
                    )
                    var isUsernameFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamUsername,
                        onValueChange = { xtreamUsername = it; xtreamAddError = null },
                        placeholder = { Text("Username", fontSize = LumenType.size14) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = LumenType.size16),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isUsernameFocused = it.isFocused }
                            .border(if (isUsernameFocused) LumenLegacySpace.xxs else 0.dp, if (isUsernameFocused) t.colors.brand else Color.Transparent, LumenTokens.Shape.md)
                    )
                    var isPasswordFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamPassword,
                        onValueChange = { xtreamPassword = it; xtreamAddError = null },
                        placeholder = { Text("Password", fontSize = LumenType.size14) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = LumenType.size16),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isPasswordFocused = it.isFocused }
                            .border(if (isPasswordFocused) LumenLegacySpace.xxs else 0.dp, if (isPasswordFocused) t.colors.brand else Color.Transparent, LumenTokens.Shape.md)
                    )
                    if (xtreamAddError != null) {
                        Text(
                            text = xtreamAddError!!,
                            color = LumenExtendedColors.errorBright,
                            fontSize = LumenType.size14,
                            modifier = Modifier.padding(bottom = LumenLegacySpace.xs)
                        )
                    }
                    if (isAddingXtream) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(LumenLayout.iconSm),
                                strokeWidth = LumenLegacySpace.xxs,
                                color = t.colors.brand
                            )
                            Text(
                                text = xtreamAuthMessage,
                                color = t.colors.mutedForeground,
                                fontSize = LumenType.size14
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TvFocusCard(
                            onClick = {
                                if (!isAddingXtream) {
                                    stableFocusRequester.requestFocus()
                                    showXtreamForm = false
                                    xtreamAddError = null
                                    xtreamName = ""
                                    xtreamServer = ""
                                    xtreamUsername = ""
                                    xtreamPassword = ""
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { isFocused ->
                            Text(
                                text = "Cancel",
                                color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
                                fontWeight = FontWeight.Bold,
                                fontSize = LumenType.size15,
                                modifier = Modifier.padding(vertical = LumenLegacySpace.sm2, horizontal = LumenLegacySpace.md)
                            )
                        }
                        TvFocusCard(
                            onClick = {
                                if (!isAddingXtream) {
                                    if (xtreamServer.isNotBlank() && xtreamUsername.isNotBlank() && xtreamPassword.isNotBlank()) {
                                        xtreamAddError = null
                                        if (xtreamServer.trim().lowercase().startsWith("http://")) {
                                            showHttpWarning = true
                                        } else {
                                            connectAndSyncXtream()
                                        }
                                    } else {
                                        xtreamAddError = "Please fill in all required fields"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { isFocused ->
                            Text(
                                text = if (isAddingXtream) "Connecting..." else "Connect",
                                color = if (isFocused) t.colors.foreground else t.colors.brand,
                                fontWeight = FontWeight.Bold,
                                fontSize = LumenType.size15,
                                modifier = Modifier.padding(vertical = LumenLegacySpace.sm2, horizontal = LumenLegacySpace.md)
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(text = "Program Guides (EPG)", fontSize = LumenType.size24, fontWeight = FontWeight.Bold, color = t.colors.foreground, modifier = Modifier.padding(top = LumenLegacySpace.lg, bottom = LumenLegacySpace.md))
        }

        if (!showEpgForm) {
            item {
                TvFocusCard(
                    onClick = {
                        if (providers.isEmpty()) {
                            epgAddError = "Add an IPTV provider before attaching an EPG guide"
                        } else {
                            epgAddError = null
                            showEpgForm = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { isFocused ->
                    Text(
                        text = "+ Add XMLTV Guide",
                        color = if (isFocused) t.colors.foreground else t.colors.brand,
                        fontWeight = FontWeight.Bold,
                        fontSize = LumenType.size15,
                        modifier = Modifier.padding(vertical = LumenLegacySpace.sm2, horizontal = LumenLegacySpace.md)
                    )
                }
            }
            epgAddError?.takeIf { !showEpgForm }?.let { error ->
                item {
                    Text(error, color = LumenExtendedColors.errorBright, fontSize = LumenType.size13)
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm), modifier = Modifier.padding(bottom = LumenLegacySpace.md)) {
                    Text("Add XMLTV Guide", color = t.colors.foreground, fontSize = LumenType.size18, fontWeight = FontWeight.Bold)
                    if (providers.size > 1) {
                        providers.forEach { provider ->
                            TvFocusCard(
                                onClick = { epgProviderId = provider.id },
                                modifier = Modifier.fillMaxWidth()
                            ) { isFocused ->
                                Text(
                                    text = (if (epgProviderId == provider.id) "● " else "○ ") + provider.name,
                                    color = if (isFocused || epgProviderId == provider.id) t.colors.foreground else t.colors.mutedForeground,
                                    fontSize = LumenType.size14,
                                    modifier = Modifier.padding(LumenTokens.Radius.sm)
                                )
                            }
                        }
                    }
                    TvTextField(
                        value = epgName,
                        onValueChange = { epgName = it; epgAddError = null },
                        placeholder = { Text("Guide name", fontSize = LumenType.size14) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TvTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it; epgAddError = null },
                        placeholder = { Text("XMLTV URL (http://...)", fontSize = LumenType.size14) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    epgAddError?.let { error ->
                        Text(error, color = LumenExtendedColors.errorBright, fontSize = LumenType.size13)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)) {
                        TvFocusCard(
                            onClick = {
                                if (!isAddingEpg) {
                                    showEpgForm = false
                                    epgAddError = null
                                    epgName = ""
                                    epgUrl = ""
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { isFocused ->
                            Text("Cancel", color = if (isFocused) t.colors.foreground else t.colors.mutedForeground, modifier = Modifier.padding(LumenLegacySpace.md))
                        }
                        TvFocusCard(
                            onClick = {
                                if (isAddingEpg) return@TvFocusCard
                                val providerId = epgProviderId ?: providers.firstOrNull()?.id
                                val url = epgUrl.trim()
                                if (providerId == null) {
                                    epgAddError = "Add an IPTV provider first"
                                    return@TvFocusCard
                                }
                                if (url.isBlank()) {
                                    epgAddError = "XMLTV URL cannot be blank"
                                    return@TvFocusCard
                                }
                                isAddingEpg = true
                                coroutineScope.launch {
                                    try {
                                        val source = IPTVRepository.addEpgSource(
                                            providerId = providerId,
                                            name = epgName.trim().ifBlank { "EPG Guide" },
                                            url = url
                                        )
                                        IPTVRepository.syncEpgFromUrl(source.id)
                                        epgName = ""
                                        epgUrl = ""
                                        epgAddError = null
                                        showEpgForm = false
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: IllegalArgumentException) {
                                        epgAddError = e.message ?: "Invalid EPG URL"
                                    } catch (e: Exception) {
                                        epgAddError = com.example.calmsource.core.network.UrlRedactor
                                            .redactErrorMessage(e.message ?: "Could not add EPG guide")
                                    } finally {
                                        isAddingEpg = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { isFocused ->
                            Text(
                                if (isAddingEpg) "Adding..." else "Add & Sync",
                                color = if (isFocused) t.colors.foreground else t.colors.brand,
                                modifier = Modifier.padding(LumenLegacySpace.md)
                            )
                        }
                    }
                }
            }
        }

        if (epgSources.isEmpty() && !showEpgForm) {
            item {
                Text(text = "No custom XMLTV guides registered.", color = t.colors.mutedForeground, fontSize = LumenType.size14)
            }
        } else {
            items(epgSources, key = { it.id }) { source ->
                val formatter = remember { java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()) }
                val lastSyncText = if (source.lastSyncMs > 0) {
                    "Last synced at ${formatter.format(java.util.Date(source.lastSyncMs))}"
                } else {
                    "Never synced"
                }
                TvFocusCard(
                    onClick = {
                        coroutineScope.launch {
                            IPTVRepository.syncEpgFromUrl(source.id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { isFocused ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(text = source.name, color = t.colors.foreground, fontSize = LumenType.size16, fontWeight = FontWeight.Bold)
                            Text(text = com.example.calmsource.core.network.UrlRedactor.redactUrl(source.url), color = if (isFocused) t.colors.foreground else t.colors.mutedForeground, fontSize = LumenType.size12)
                            Text(text = lastSyncText, color = t.colors.brand, fontSize = LumenType.size12, modifier = Modifier.padding(top = LumenLegacySpace.xs))
                        }
                        Text(
                            text = if (isFocused) "Press OK to Sync ↻" else "Sync",
                            color = if (isFocused) t.colors.brand else t.colors.mutedForeground,
                            fontSize = LumenType.size13,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvIptvOptimizationSettings(
    preferences: IptvOptimizationPreferences,
    stats: IptvOptimizationStats,
    onUpdate: ((IptvOptimizationPreferences) -> IptvOptimizationPreferences) -> Unit,
    onReset: () -> Unit
) {
    val t = LocalLumenTokens.current
    var languagesText by remember { mutableStateOf(preferences.preferredLanguages.sorted().joinToString(", ")) }
    var countryText by remember { mutableStateOf(preferences.preferredCountry) }
    var languagesFocused by remember { mutableStateOf(false) }
    var countryFocused by remember { mutableStateOf(false) }

    LaunchedEffect(preferences.preferredLanguages) {
        languagesText = preferences.preferredLanguages.sorted().joinToString(", ")
    }
    LaunchedEffect(preferences.preferredCountry) {
        countryText = preferences.preferredCountry
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm),
        modifier = Modifier
            .fillMaxWidth()
            .background(t.colors.surface, LumenTokens.Shape.xs)
            .padding(LumenLegacySpace.lg)
    ) {
        Text("Channel Optimization", color = t.colors.foreground, fontSize = LumenType.size20, fontWeight = FontWeight.Bold)
        Text(
            text = "${stats.visibleCount} visible of ${stats.inputCount}; " +
                "${stats.duplicatesRemoved} duplicates and ${stats.unsupportedHidden} broken hidden",
            color = t.colors.mutedForeground,
            fontSize = LumenType.size13
        )
        TvTextField(
            value = languagesText,
            onValueChange = { value ->
                languagesText = value
                onUpdate { current ->
                    current.copy(
                        preferredLanguages = value.split(',')
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .toSet()
                    )
                }
            },
            placeholder = { Text("Preferred languages, comma-separated", fontSize = LumenType.size14) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { languagesFocused = it.isFocused }
                .border(
                    if (languagesFocused) LumenLegacySpace.xxs else 0.dp,
                    if (languagesFocused) t.colors.brand else Color.Transparent,
                    LumenTokens.Shape.md
                )
        )
        TvTextField(
            value = countryText,
            onValueChange = { value ->
                countryText = value
                onUpdate { it.copy(preferredCountry = value.trim()) }
            },
            placeholder = { Text("Country or region", fontSize = LumenType.size14) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { countryFocused = it.isFocused }
                .border(
                    if (countryFocused) LumenLegacySpace.xxs else 0.dp,
                    if (countryFocused) t.colors.brand else Color.Transparent,
                    LumenTokens.Shape.md
                )
        )

        Text("Favorite categories", color = t.colors.foreground, fontSize = LumenType.size14)
        Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)) {
            listOf("Sports", "Movies", "News", "Kids").forEach { category ->
                TvFocusCard(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onUpdate { current ->
                            val categories = current.favoriteCategories.toMutableSet()
                            if (!categories.add(category)) categories.remove(category)
                            current.copy(favoriteCategories = categories)
                        }
                    }
                ) { focused ->
                    Text(
                        text = if (category in preferences.favoriteCategories) "$category: On" else category,
                        color = if (focused || category in preferences.favoriteCategories) {
                            t.colors.foreground
                        } else {
                            t.colors.mutedForeground
                        },
                        fontSize = LumenType.size12,
                        maxLines = 1
                    )
                }
            }
        }

        TvOptimizationToggle("Hide adult content", preferences.hideAdult) {
            onUpdate { current -> current.copy(hideAdult = it) }
        }
        TvOptimizationToggle("Hide broken or unsupported", preferences.hideUnsupported) {
            onUpdate { current -> current.copy(hideUnsupported = it) }
        }
        TvOptimizationToggle("Prioritize HD, FHD, and 4K", preferences.preferHighQuality) {
            onUpdate { current -> current.copy(preferHighQuality = it) }
        }
        TvOptimizationToggle("Remove duplicate channels", preferences.removeDuplicates) {
            onUpdate { current -> current.copy(removeDuplicates = it) }
        }

        Text("Group channels by", color = t.colors.foreground, fontSize = LumenType.size14)
        Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)) {
            IptvGroupMode.entries.forEach { mode ->
                TvFocusCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onUpdate { it.copy(groupMode = mode) } }
                ) { focused ->
                    Text(
                        text = mode.name.lowercase().replaceFirstChar(Char::uppercase),
                        color = if (focused || preferences.groupMode == mode) {
                            t.colors.foreground
                        } else {
                            t.colors.mutedForeground
                        },
                        fontSize = LumenType.size12
                    )
                }
            }
        }
        TvFocusCard(onClick = onReset, modifier = Modifier.fillMaxWidth()) { focused ->
            Text(
                text = "Reset optimization",
                color = if (focused) t.colors.foreground else t.colors.brand,
                fontSize = LumenType.size13
            )
        }
    }
}

@Composable
private fun TvOptimizationToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val t = LocalLumenTokens.current
    TvFocusCard(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth()
    ) { focused ->
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, color = if (focused) t.colors.foreground else t.colors.mutedForeground, fontSize = LumenType.size13)
            Text(
                text = if (checked) "On" else "Off",
                color = if (checked || focused) t.colors.brand else t.colors.mutedForeground,
                fontSize = LumenType.size13,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TvIptvItem(
    provider: IPTVProvider,
    syncState: ProviderSyncState,
    onSync: () -> Unit
) {
    val t = LocalLumenTokens.current
    val statusColor = when (syncState.status) {
        ProviderSyncStatus.IDLE -> t.colors.mutedForeground
        ProviderSyncStatus.SYNCING -> t.colors.brand
        ProviderSyncStatus.SUCCESS -> LumenExtendedColors.statusHealthy
        ProviderSyncStatus.ERROR -> LumenExtendedColors.errorBright
    }

    var channelsCount by remember(provider.id, syncState) { mutableIntStateOf(0) }
    var matchedCount by remember(provider.id, syncState) { mutableIntStateOf(0) }
    var matchRate by remember(provider.id, syncState) { mutableIntStateOf(0) }

    LaunchedEffect(provider.id, syncState) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val fetched = IPTVRepository.getChannels().filter { it.providerId == provider.id }
            val matched = fetched.count { ch -> IPTVRepository.getMatchStatusForChannel(ch.id)?.epgId?.isNotEmpty() == true }
            channelsCount = fetched.size
            matchedCount = matched
            matchRate = if (fetched.isNotEmpty()) (matched * 100) / fetched.size else 0
        }
    }

    TvFocusCard(
        onClick = {
            if (syncState.status != ProviderSyncStatus.SYNCING) onSync()
        },
        modifier = Modifier.fillMaxWidth()
    ) { isFocused ->
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = provider.name, color = t.colors.foreground, fontSize = LumenType.size16, fontWeight = FontWeight.Bold)
                Text(text = com.example.calmsource.core.network.UrlRedactor.redactUrl(provider.playlistUrl), color = if (isFocused) t.colors.foreground else t.colors.mutedForeground, fontSize = LumenType.size12)
                if (provider.type == IPTVProviderType.XTREAM) {
                    Text(text = "Type: Xtream API", color = t.colors.mutedForeground, fontSize = LumenType.size12)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                    modifier = Modifier.padding(top = LumenLegacySpace.xs)
                ) {
                    Text(
                        text = "Sync: ${syncState.status.name}",
                        color = statusColor,
                        fontSize = LumenType.size12,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "•  $matchRate% EPG matched ($matchedCount/$channelsCount channels)",
                        color = t.colors.mutedForeground,
                        fontSize = LumenType.size12
                    )
                }

                if (syncState.status == ProviderSyncStatus.SYNCING) {
                    Box(
                        modifier = Modifier
                            .padding(top = LumenLegacySpace.sm)
                            .fillMaxWidth(0.5f)
                            .height(LumenLegacySpace.xs)
                            .clip(LumenTokens.Shape.md)
                            .background(LumenTokens.Color.glass)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(syncState.progressPercent.toFloat() / 100f)
                                .background(t.colors.brand)
                        )
                    }
                }

                val syncError = syncState.error
                if (syncError != null) {
                    val safeError = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(syncError)
                    Text(text = "Needs attention: $safeError", color = LumenExtendedColors.errorBright, fontSize = LumenType.size11, modifier = Modifier.padding(top = LumenLegacySpace.xxs))
                }
            }
            Text(
                text = if (isFocused) "Press OK to Sync ↻" else "Sync",
                color = if (isFocused) t.colors.brand else t.colors.mutedForeground,
                fontSize = LumenType.size13,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TvXtreamProviderItem(
    provider: IPTVProvider,
    syncProgress: XtreamSyncProgress?,
    onSync: () -> Unit
) {
    val t = LocalLumenTokens.current
    val healthColor = when (provider.health) {
        ProviderHealth.HEALTHY -> LumenExtendedColors.statusHealthy
        ProviderHealth.SLOW -> LumenTokens.Color.warning
        ProviderHealth.FAILED -> LumenExtendedColors.errorBright
    }

    val isSyncing = syncProgress != null &&
            syncProgress.stage != XtreamSyncStage.IDLE &&
            syncProgress.stage != XtreamSyncStage.COMPLETE &&
            syncProgress.stage != XtreamSyncStage.FAILED

    TvFocusCard(
        onClick = {
            if (!isSyncing) onSync()
        },
        modifier = Modifier.fillMaxWidth()
    ) { isFocused ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        color = t.colors.foreground,
                        fontSize = LumenType.size18,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Type: Xtream API",
                        color = t.colors.mutedForeground,
                        fontSize = LumenType.size14
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(LumenTokens.Shape.md)
                        .background(healthColor.copy(alpha = 0.2f))
                        .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xs)
                ) {
                    Text(
                        text = provider.health.name,
                        color = healthColor,
                        fontSize = LumenType.size12,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            syncProgress?.takeIf { isSyncing }?.let { progress ->
                Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))
                Text(
                    text = xtreamStageLabel(progress.stage),
                    color = t.colors.brand,
                    fontSize = LumenType.size14,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .padding(top = LumenLegacySpace.xs)
                        .fillMaxWidth()
                        .height(LumenLegacySpace.sm)
                        .clip(LumenTokens.Shape.xs)
                        .background(LumenTokens.Color.glass)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.progressPercent.toFloat() / 100f)
                            .background(t.colors.brand)
                    )
                }
            }

            if (syncProgress != null && syncProgress.stage == XtreamSyncStage.COMPLETE) {
                Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))
                Text(
                    text = "✓ Sync Complete",
                    color = LumenExtendedColors.statusHealthy,
                    fontSize = LumenType.size14,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                    modifier = Modifier.padding(top = LumenLegacySpace.xs)
                ) {
                    Text(
                        text = "Live: ${syncProgress.liveChannelCount}",
                        color = t.colors.foreground,
                        fontSize = LumenType.size14
                    )
                    Text(
                        text = "VOD: ${syncProgress.vodCount}",
                        color = t.colors.foreground,
                        fontSize = LumenType.size14
                    )
                    Text(
                        text = "Series: ${syncProgress.seriesCount}",
                        color = t.colors.foreground,
                        fontSize = LumenType.size14
                    )
                }
            }

            if (syncProgress != null && syncProgress.stage == XtreamSyncStage.FAILED) {
                Spacer(modifier = Modifier.height(LumenLegacySpace.sm))
                val safeError = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(syncProgress.error ?: "Sync failed")
                Text(
                    text = "Needs attention: $safeError",
                    color = LumenExtendedColors.errorBright,
                    fontSize = LumenType.size14
                )
            }

            val warning = syncProgress?.warning
            if (!warning.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LumenTokens.Shape.sm)
                        .background(LumenExtendedColors.warningSurface)
                        .padding(LumenLegacySpace.md)
                ) {
                    Text(
                        text = warning,
                        color = LumenExtendedColors.warningText,
                        fontSize = LumenType.size14,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!isSyncing) {
                Spacer(modifier = Modifier.height(LumenLegacySpace.sm))
                Text(
                    text = if (isFocused) "Press OK to Sync ↻" else "Sync",
                    color = if (isFocused) t.colors.brand else t.colors.mutedForeground,
                    fontSize = LumenType.size13,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun xtreamStageLabel(stage: XtreamSyncStage): String = when (stage) {
    XtreamSyncStage.IDLE -> "Ready"
    XtreamSyncStage.VALIDATING -> "Validating..."
    XtreamSyncStage.SYNCING_LIVE_CATEGORIES -> "Syncing Live Channels..."
    XtreamSyncStage.SYNCING_LIVE_STREAMS -> "Syncing Live Channels..."
    XtreamSyncStage.SYNCING_VOD_CATEGORIES -> "Syncing VOD..."
    XtreamSyncStage.SYNCING_VOD_STREAMS -> "Syncing VOD..."
    XtreamSyncStage.SYNCING_SERIES_CATEGORIES -> "Syncing Series..."
    XtreamSyncStage.SYNCING_SERIES -> "Syncing Series..."
    XtreamSyncStage.SYNCING_EPG -> "Syncing EPG..."
    XtreamSyncStage.COMPLETE -> "Complete!"
    XtreamSyncStage.FAILED -> "Failed"
}

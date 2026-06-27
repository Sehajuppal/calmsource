package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.LumenTokens

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
            .background(TvColors.Background)
            .padding(LumenTokens.Space.xxl),
        verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.md)
    ) {
        item {
            TvFocusCard(
                onClick = onBack, 
                modifier = Modifier
                    .wrapContentSize()
                    .padding(bottom = LumenTokens.Space.lg)
                    .focusRequester(stableFocusRequester)
            ) {
                Text(text = "← Back", color = TvColors.TextMain)
            }
        }

        if (!XtreamRepository.isEncryptedStorageAvailable()) {
            item {
                androidx.compose.material3.Surface(
                    color = LumenTokens.Color.errorSoft,
                    shape = LumenTokens.Shape.sm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = LumenTokens.Space.md)
                ) {
                    Column(modifier = Modifier.padding(LumenTokens.Space.lg)) {
                        Text(
                            text = "Warning: Secure Storage Unavailable",
                            fontWeight = FontWeight.Bold,
                            color = LumenTokens.Color.bg,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(LumenTokens.Space.xs))
                        Text(
                            text = "Encrypted storage is unavailable. Your credentials will only be saved in-memory and will be lost on app exit.",
                            color = LumenTokens.Color.bg,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        item {
            Text(text = "TV Sources (M3U Playlists)", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain, modifier = Modifier.padding(bottom = LumenTokens.Space.md))
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
                    color = TvColors.TextSub,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = LumenTokens.Space.lg)
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
                                .width(LumenTokens.Layout.sheetMaxWidth)
                                .background(TvColors.Surface, LumenTokens.Shape.md)
                                .padding(LumenTokens.Space.xxl)
                        ) {
                            Column {
                                Text("Delete Provider", color = TvColors.TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(LumenTokens.Space.md))
                                Text("Are you sure you want to delete '${providerToDelete?.name}'? This will remove all associated channels and data.", color = TvColors.TextSub, fontSize = 16.sp)
                                providerDeleteError?.let { error ->
                                    Spacer(modifier = Modifier.height(LumenTokens.Space.sm2))
                                    Text(error, color = LumenTokens.Color.errorBright, fontSize = 14.sp)
                                }
                                if (isDeletingProvider) {
                                    Spacer(modifier = Modifier.height(LumenTokens.Space.lg))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md)
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            color = TvColors.BorderFocused,
                                            strokeWidth = LumenTokens.Color.focusRingWidth,
                                            modifier = Modifier.size(LumenTokens.Space.xxl)
                                        )
                                        Text(
                                            "Removing provider and channel data…",
                                            color = TvColors.TextSub,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(LumenTokens.Space.xxl))
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
                                                modifier = Modifier.padding(LumenTokens.Space.md),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Cancel", color = if (isFocused) TvColors.TextMain else TvColors.TextSub)
                                            }
                                        }
                                    }
                                    false -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.lg)) {
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
                                                Box(modifier = Modifier.fillMaxWidth().padding(LumenTokens.Space.md), contentAlignment = Alignment.Center) {
                                                    Text("Cancel", color = if (isFocused) TvColors.TextMain else TvColors.TextSub)
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
                                                Box(modifier = Modifier.fillMaxWidth().padding(LumenTokens.Space.md), contentAlignment = Alignment.Center) {
                                                    Text("Delete", color = if (isFocused) TvColors.TextMain else Color.Red)
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
                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm2),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = LumenTokens.Space.xs)
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
                        modifier = Modifier.width(LumenTokens.Layout.inputWidthSm)
                    ) { isFocused ->
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = LumenTokens.Space.md), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (provider.isEnabled) "Disable" else "Enable",
                                color = if (isFocused) TvColors.TextMain else TvColors.BorderFocused,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TvFocusCard(
                        onClick = {
                            providerDeleteError = null
                            providerToDelete = provider
                        },
                        modifier = Modifier.width(LumenTokens.Layout.inputWidthXs)
                    ) { isFocused ->
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = LumenTokens.Space.md), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Delete",
                                color = if (isFocused) LumenTokens.Color.textPrimary else LumenTokens.Color.errorBright,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md), modifier = Modifier.fillMaxWidth()) {
                TvFocusCard(
                    onClick = {
                        showM3uForm = true
                        showXtreamForm = false
                    },
                    modifier = Modifier.weight(1f)
                ) { isFocused ->
                    Text(
                        text = "+ Add M3U Playlist",
                        color = if (isFocused) TvColors.TextMain else TvColors.BorderFocused,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = LumenTokens.Space.sm2, horizontal = LumenTokens.Space.md)
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
                        color = if (isFocused) TvColors.TextMain else TvColors.BorderFocused,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = LumenTokens.Space.sm2, horizontal = LumenTokens.Space.md)
                    )
                }
            }
        }

        if (showM3uForm) {
            item {
                Text(text = "Add M3U Playlist", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain, modifier = Modifier.padding(top = LumenTokens.Space.lg, bottom = LumenTokens.Space.sm2))
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm), modifier = Modifier.padding(bottom = LumenTokens.Space.lg)) {
                    var isNameFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = m3uName,
                        onValueChange = { m3uName = it; m3uAddError = null },
                        placeholder = { Text("Playlist Name (Optional)", fontSize = 14.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isNameFocused = it.isFocused }
                            .border(if (isNameFocused) LumenTokens.Space.xxs else 0.dp, if (isNameFocused) TvColors.BorderFocused else Color.Transparent, LumenTokens.Shape.md)
                    )
                    var isUrlFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it; m3uAddError = null },
                        placeholder = { Text("Playlist URL", fontSize = 14.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isUrlFocused = it.isFocused }
                            .border(if (isUrlFocused) LumenTokens.Space.xxs else 0.dp, if (isUrlFocused) TvColors.BorderFocused else Color.Transparent, LumenTokens.Shape.md)
                    )
                    m3uAddError?.let { error ->
                        Text(text = error, color = LumenTokens.Color.errorBright, fontSize = 14.sp)
                    }
                    if (isAddingM3u) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(LumenTokens.Layout.iconSm),
                                strokeWidth = LumenTokens.Space.xxs,
                                color = TvColors.BorderFocused
                            )
                            Text("Adding playlist and starting sync...", color = TvColors.TextSub, fontSize = 14.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md), modifier = Modifier.fillMaxWidth()) {
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
                                color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(vertical = LumenTokens.Space.sm2, horizontal = LumenTokens.Space.md)
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
                                color = if (isFocused) TvColors.TextMain else TvColors.BorderFocused,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(vertical = LumenTokens.Space.sm2, horizontal = LumenTokens.Space.md)
                            )
                        }
                    }
                }
            }
        }

        if (showXtreamForm || (providers.isEmpty() && !showM3uForm)) {
            item {
                Text(text = "Add Xtream Login", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain, modifier = Modifier.padding(top = LumenTokens.Space.lg, bottom = LumenTokens.Space.sm2))
            }
            item {                if (showHttpWarning) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showHttpWarning = false }) {
                        Box(
                            modifier = Modifier
                                .width(LumenTokens.Layout.sheetMaxWidth)
                                .background(TvColors.Surface, LumenTokens.Shape.md)
                                .padding(LumenTokens.Space.xxl)
                        ) {
                            Column {
                                Text("Insecure Connection", color = TvColors.TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(LumenTokens.Space.md))
                                Text("You are attempting to connect via an unencrypted HTTP URL. This connection is not secure. Are you sure you want to proceed?", color = TvColors.TextSub, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(LumenTokens.Space.xxl))
                                Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.lg)) {
                                    TvFocusCard(
                                        onClick = {
                                            stableFocusRequester.requestFocus()
                                            showHttpWarning = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { isFocused ->
                                        Text("Cancel", color = if (isFocused) TvColors.TextMain else TvColors.TextSub, modifier = Modifier.padding(LumenTokens.Space.md))
                                    }
                                    TvFocusCard(
                                        onClick = {
                                            stableFocusRequester.requestFocus()
                                            showHttpWarning = false
                                            connectAndSyncXtream()
                                        }, 
                                        modifier = Modifier.weight(1f)
                                    ) { isFocused ->
                                        Text("Proceed Anyway", color = if (isFocused) TvColors.TextMain else TvColors.BorderFocused, modifier = Modifier.padding(LumenTokens.Space.md))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm), modifier = Modifier.padding(bottom = LumenTokens.Space.lg)) {
                    var isNameFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamName,
                        onValueChange = { xtreamName = it; xtreamAddError = null },
                        placeholder = { Text("Provider Name (Optional)", fontSize = 14.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isNameFocused = it.isFocused }
                            .border(if (isNameFocused) LumenTokens.Space.xxs else 0.dp, if (isNameFocused) TvColors.BorderFocused else Color.Transparent, LumenTokens.Shape.md)
                    )
                    var isServerFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamServer,
                        onValueChange = { xtreamServer = it; xtreamAddError = null },
                        placeholder = { Text("http://host:port", fontSize = 14.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isServerFocused = it.isFocused }
                            .border(if (isServerFocused) LumenTokens.Space.xxs else 0.dp, if (isServerFocused) TvColors.BorderFocused else Color.Transparent, LumenTokens.Shape.md)
                    )
                    var isUsernameFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamUsername,
                        onValueChange = { xtreamUsername = it; xtreamAddError = null },
                        placeholder = { Text("Username", fontSize = 14.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isUsernameFocused = it.isFocused }
                            .border(if (isUsernameFocused) LumenTokens.Space.xxs else 0.dp, if (isUsernameFocused) TvColors.BorderFocused else Color.Transparent, LumenTokens.Shape.md)
                    )
                    var isPasswordFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamPassword,
                        onValueChange = { xtreamPassword = it; xtreamAddError = null },
                        placeholder = { Text("Password", fontSize = 14.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { isPasswordFocused = it.isFocused }
                            .border(if (isPasswordFocused) LumenTokens.Space.xxs else 0.dp, if (isPasswordFocused) TvColors.BorderFocused else Color.Transparent, LumenTokens.Shape.md)
                    )
                    if (xtreamAddError != null) {
                        Text(
                            text = xtreamAddError!!,
                            color = LumenTokens.Color.errorBright,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = LumenTokens.Space.xs)
                        )
                    }
                    if (isAddingXtream) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(LumenTokens.Layout.iconSm),
                                strokeWidth = LumenTokens.Space.xxs,
                                color = TvColors.BorderFocused
                            )
                            Text(
                                text = xtreamAuthMessage,
                                color = TvColors.TextSub,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
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
                                color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(vertical = LumenTokens.Space.sm2, horizontal = LumenTokens.Space.md)
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
                                color = if (isFocused) TvColors.TextMain else TvColors.BorderFocused,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(vertical = LumenTokens.Space.sm2, horizontal = LumenTokens.Space.md)
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(text = "Program Guides (EPG)", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain, modifier = Modifier.padding(top = LumenTokens.Space.lg, bottom = LumenTokens.Space.md))
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
                        color = if (isFocused) TvColors.TextMain else TvColors.BorderFocused,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = LumenTokens.Space.sm2, horizontal = LumenTokens.Space.md)
                    )
                }
            }
            epgAddError?.takeIf { !showEpgForm }?.let { error ->
                item {
                    Text(error, color = LumenTokens.Color.errorBright, fontSize = 13.sp)
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm), modifier = Modifier.padding(bottom = LumenTokens.Space.md)) {
                    Text("Add XMLTV Guide", color = TvColors.TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (providers.size > 1) {
                        providers.forEach { provider ->
                            TvFocusCard(
                                onClick = { epgProviderId = provider.id },
                                modifier = Modifier.fillMaxWidth()
                            ) { isFocused ->
                                Text(
                                    text = (if (epgProviderId == provider.id) "● " else "○ ") + provider.name,
                                    color = if (isFocused || epgProviderId == provider.id) TvColors.TextMain else TvColors.TextSub,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(LumenTokens.Radius.sm)
                                )
                            }
                        }
                    }
                    TvTextField(
                        value = epgName,
                        onValueChange = { epgName = it; epgAddError = null },
                        placeholder = { Text("Guide name", fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TvTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it; epgAddError = null },
                        placeholder = { Text("XMLTV URL (http://...)", fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    epgAddError?.let { error ->
                        Text(error, color = LumenTokens.Color.errorBright, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md)) {
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
                            Text("Cancel", color = if (isFocused) TvColors.TextMain else TvColors.TextSub, modifier = Modifier.padding(LumenTokens.Space.md))
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
                                color = if (isFocused) TvColors.TextMain else TvColors.BorderFocused,
                                modifier = Modifier.padding(LumenTokens.Space.md)
                            )
                        }
                    }
                }
            }
        }

        if (epgSources.isEmpty() && !showEpgForm) {
            item {
                Text(text = "No custom XMLTV guides registered.", color = TvColors.TextSub, fontSize = 14.sp)
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
                            Text(text = source.name, color = TvColors.TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = com.example.calmsource.core.network.UrlRedactor.redactUrl(source.url), color = if (isFocused) TvColors.TextMain else TvColors.TextSub, fontSize = 12.sp)
                            Text(text = lastSyncText, color = TvColors.BorderFocused, fontSize = 12.sp, modifier = Modifier.padding(top = LumenTokens.Space.xs))
                        }
                        Text(
                            text = if (isFocused) "Press OK to Sync ↻" else "Sync",
                            color = if (isFocused) TvColors.BorderFocused else TvColors.TextSub,
                            fontSize = 13.sp,
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
            .background(TvColors.Surface, LumenTokens.Shape.xs)
            .padding(LumenTokens.Space.lg)
    ) {
        Text("Channel Optimization", color = TvColors.TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "${stats.visibleCount} visible of ${stats.inputCount}; " +
                "${stats.duplicatesRemoved} duplicates and ${stats.unsupportedHidden} broken hidden",
            color = TvColors.TextSub,
            fontSize = 13.sp
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
            placeholder = { Text("Preferred languages, comma-separated", fontSize = 14.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { languagesFocused = it.isFocused }
                .border(
                    if (languagesFocused) LumenTokens.Space.xxs else 0.dp,
                    if (languagesFocused) TvColors.BorderFocused else Color.Transparent,
                    LumenTokens.Shape.md
                )
        )
        TvTextField(
            value = countryText,
            onValueChange = { value ->
                countryText = value
                onUpdate { it.copy(preferredCountry = value.trim()) }
            },
            placeholder = { Text("Country or region", fontSize = 14.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { countryFocused = it.isFocused }
                .border(
                    if (countryFocused) LumenTokens.Space.xxs else 0.dp,
                    if (countryFocused) TvColors.BorderFocused else Color.Transparent,
                    LumenTokens.Shape.md
                )
        )

        Text("Favorite categories", color = TvColors.TextMain, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm2)) {
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
                            TvColors.TextMain
                        } else {
                            TvColors.TextSub
                        },
                        fontSize = 12.sp,
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

        Text("Group channels by", color = TvColors.TextMain, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm2)) {
            IptvGroupMode.entries.forEach { mode ->
                TvFocusCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onUpdate { it.copy(groupMode = mode) } }
                ) { focused ->
                    Text(
                        text = mode.name.lowercase().replaceFirstChar(Char::uppercase),
                        color = if (focused || preferences.groupMode == mode) {
                            TvColors.TextMain
                        } else {
                            TvColors.TextSub
                        },
                        fontSize = 12.sp
                    )
                }
            }
        }
        TvFocusCard(onClick = onReset, modifier = Modifier.fillMaxWidth()) { focused ->
            Text(
                text = "Reset optimization",
                color = if (focused) TvColors.TextMain else TvColors.BorderFocused,
                fontSize = 13.sp
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
    TvFocusCard(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth()
    ) { focused ->
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, color = if (focused) TvColors.TextMain else TvColors.TextSub, fontSize = 13.sp)
            Text(
                text = if (checked) "On" else "Off",
                color = if (checked || focused) TvColors.BorderFocused else TvColors.TextSub,
                fontSize = 13.sp,
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
    val statusColor = when (syncState.status) {
        ProviderSyncStatus.IDLE -> TvColors.TextSub
        ProviderSyncStatus.SYNCING -> TvColors.BorderFocused
        ProviderSyncStatus.SUCCESS -> LumenTokens.Color.statusHealthy
        ProviderSyncStatus.ERROR -> LumenTokens.Color.errorBright
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
                Text(text = provider.name, color = TvColors.TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = com.example.calmsource.core.network.UrlRedactor.redactUrl(provider.playlistUrl), color = if (isFocused) TvColors.TextMain else TvColors.TextSub, fontSize = 12.sp)
                if (provider.type == IPTVProviderType.XTREAM) {
                    Text(text = "Type: Xtream API", color = TvColors.TextSub, fontSize = 12.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm2),
                    modifier = Modifier.padding(top = LumenTokens.Space.xs)
                ) {
                    Text(
                        text = "Sync: ${syncState.status.name}",
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "•  $matchRate% EPG matched ($matchedCount/$channelsCount channels)",
                        color = TvColors.TextSub,
                        fontSize = 12.sp
                    )
                }

                if (syncState.status == ProviderSyncStatus.SYNCING) {
                    Box(
                        modifier = Modifier
                            .padding(top = LumenTokens.Space.sm)
                            .fillMaxWidth(0.5f)
                            .height(LumenTokens.Space.xs)
                            .clip(LumenTokens.Shape.md)
                            .background(LumenTokens.Color.glassOverlay)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(syncState.progressPercent.toFloat() / 100f)
                                .background(TvColors.BorderFocused)
                        )
                    }
                }

                val syncError = syncState.error
                if (syncError != null) {
                    val safeError = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(syncError)
                    Text(text = "Needs attention: $safeError", color = LumenTokens.Color.errorBright, fontSize = 11.sp, modifier = Modifier.padding(top = LumenTokens.Space.xxs))
                }
            }
            Text(
                text = if (isFocused) "Press OK to Sync ↻" else "Sync",
                color = if (isFocused) TvColors.BorderFocused else TvColors.TextSub,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
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

@Composable
fun TvXtreamProviderItem(
    provider: IPTVProvider,
    syncProgress: XtreamSyncProgress?,
    onSync: () -> Unit
) {
    val healthColor = when (provider.health) {
        ProviderHealth.HEALTHY -> LumenTokens.Color.statusHealthy
        ProviderHealth.SLOW -> LumenTokens.Color.warning
        ProviderHealth.FAILED -> LumenTokens.Color.errorBright
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
                        color = TvColors.TextMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Type: Xtream API",
                        color = TvColors.TextSub,
                        fontSize = 14.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(LumenTokens.Shape.md)
                        .background(healthColor.copy(alpha = 0.2f))
                        .padding(horizontal = LumenTokens.Space.sm2, vertical = LumenTokens.Space.xs)
                ) {
                    Text(
                        text = provider.health.name,
                        color = healthColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            syncProgress?.takeIf { isSyncing }?.let { progress ->
                Spacer(modifier = Modifier.height(LumenTokens.Space.sm2))
                Text(
                    text = xtreamStageLabel(progress.stage),
                    color = TvColors.BorderFocused,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .padding(top = LumenTokens.Space.xs)
                        .fillMaxWidth()
                        .height(LumenTokens.Space.sm)
                        .clip(LumenTokens.Shape.xs)
                        .background(LumenTokens.Color.glassOverlay)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.progressPercent.toFloat() / 100f)
                            .background(TvColors.BorderFocused)
                    )
                }
            }

            if (syncProgress != null && syncProgress.stage == XtreamSyncStage.COMPLETE) {
                Spacer(modifier = Modifier.height(LumenTokens.Space.sm2))
                Text(
                    text = "✓ Sync Complete",
                    color = LumenTokens.Color.statusHealthy,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
                    modifier = Modifier.padding(top = LumenTokens.Space.xs)
                ) {
                    Text(
                        text = "Live: ${syncProgress.liveChannelCount}",
                        color = TvColors.TextMain,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "VOD: ${syncProgress.vodCount}",
                        color = TvColors.TextMain,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Series: ${syncProgress.seriesCount}",
                        color = TvColors.TextMain,
                        fontSize = 14.sp
                    )
                }
            }

            if (syncProgress != null && syncProgress.stage == XtreamSyncStage.FAILED) {
                Spacer(modifier = Modifier.height(LumenTokens.Space.sm))
                val safeError = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(syncProgress.error ?: "Sync failed")
                Text(
                    text = "Needs attention: $safeError",
                    color = LumenTokens.Color.errorBright,
                    fontSize = 14.sp
                )
            }

            val warning = syncProgress?.warning
            if (!warning.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(LumenTokens.Space.sm2))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LumenTokens.Shape.sm)
                        .background(LumenTokens.Color.warningSurface)
                        .padding(LumenTokens.Space.md)
                ) {
                    Text(
                        text = warning,
                        color = LumenTokens.Color.warningText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!isSyncing) {
                Spacer(modifier = Modifier.height(LumenTokens.Space.sm))
                Text(
                    text = if (isFocused) "Press OK to Sync ↻" else "Sync",
                    color = if (isFocused) TvColors.BorderFocused else TvColors.TextSub,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

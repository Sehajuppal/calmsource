package com.example.calmsource.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import android.util.Log
import com.example.calmsource.core.model.*
import com.example.calmsource.feature.debrid.DebridRepository
import com.example.calmsource.feature.extensions.ExtensionRepository
import com.example.calmsource.feature.extensions.RecommendedStremioAddons
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.feature.iptv.IptvGroupMode
import com.example.calmsource.feature.iptv.IptvOptimizationPreferences
import com.example.calmsource.feature.iptv.IptvOptimizationStats
import com.example.calmsource.feature.iptv.XtreamRepository
import com.example.calmsource.core.playback.FrameRateMatchingMode
import com.example.calmsource.core.playback.FrameRateMatchingPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

enum class SettingsSubScreen {
    IPTV, EXTENSIONS, DEBRID, PRIORITIES, DISCOVERY_PROVIDERS, SEARCH, PLAYBACK, DEBUG, GENERAL, ECOSYSTEM_SYNC, CLOUD_SYNC
}

private val SettingsSubScreenSaver = Saver<SettingsSubScreen?, String>(
    save = { it?.name ?: "" },
    restore = { name ->
        if (name.isBlank()) null else SettingsSubScreen.entries.firstOrNull { it.name == name }
    }
)

@Composable
fun SettingsScreens() {
    var currentSubScreen by rememberSaveable(stateSaver = SettingsSubScreenSaver) {
        mutableStateOf<SettingsSubScreen?>(null)
    }

    BackHandler(enabled = currentSubScreen != null) {
        currentSubScreen = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentSubScreen == null) {
            SettingsRootScreen(onNavigate = { currentSubScreen = it })
        } else {
            when (currentSubScreen) {
                SettingsSubScreen.IPTV -> IptvManagerScreen(onBack = { currentSubScreen = null })
                SettingsSubScreen.EXTENSIONS -> ExtensionsScreen(onBack = { currentSubScreen = null })
                SettingsSubScreen.DEBRID -> DebridAccountsScreen(onBack = { currentSubScreen = null })
                SettingsSubScreen.PRIORITIES -> SourcePriorityScreen(onBack = { currentSubScreen = null })
                SettingsSubScreen.DISCOVERY_PROVIDERS -> DiscoveryProvidersScreen(onBack = { currentSubScreen = null })
                SettingsSubScreen.SEARCH -> SearchSettingsScreen(onBack = { currentSubScreen = null })
                SettingsSubScreen.PLAYBACK -> PlaybackSettingsScreen(onBack = { currentSubScreen = null })
                SettingsSubScreen.DEBUG -> AdvancedDebugScreen(onBack = { currentSubScreen = null })
                SettingsSubScreen.GENERAL -> GeneralSettingsScreen(onBack = { currentSubScreen = null })
                SettingsSubScreen.ECOSYSTEM_SYNC -> EcosystemSyncScreen(onBack = { currentSubScreen = null })
                SettingsSubScreen.CLOUD_SYNC -> CloudAuthScreen(onBack = { currentSubScreen = null })
                else -> {}
            }
        }
    }
}

@Composable
fun SettingsRootScreen(onNavigate: (SettingsSubScreen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = AppColors.TextMain,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (!XtreamRepository.isEncryptedStorageAvailable()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Warning: Secure Storage Unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Encrypted storage is unavailable. Your credentials will only be saved in-memory and will be lost on app exit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        SettingsSection(title = "Content Sources") {
            SettingsRow(title = "Stremio Extensions", subtitle = "Add Torrentio, AIOStreams, or any manifest URL", onClick = { onNavigate(SettingsSubScreen.EXTENSIONS) })
            SettingsRow(title = "IPTV Playlists & Services", subtitle = "Add, edit, or configure M3U/XMLTV connections", onClick = { onNavigate(SettingsSubScreen.IPTV) })
            SettingsRow(title = "Debrid Accounts", subtitle = "Optional cached torrent accounts for Stremio addons", onClick = { onNavigate(SettingsSubScreen.DEBRID) })
            SettingsRow(title = "Ecosystem Sync", subtitle = "Sync credentials and extensions with TV", onClick = { onNavigate(SettingsSubScreen.ECOSYSTEM_SYNC) })
            SettingsRow(title = "Cloud Account (Log In / Register)", subtitle = "Sync settings, extensions, and playlists to the cloud", onClick = { onNavigate(SettingsSubScreen.CLOUD_SYNC) })
        }

        SettingsSection(title = "Preferences") {
            SettingsRow(title = "Source Priorities & Language", subtitle = "Set preferred audio language, resolution, debrid filters", onClick = { onNavigate(SettingsSubScreen.PRIORITIES) })
            SettingsRow(title = "Discovery Providers", subtitle = "Control enrichment sources, cache, privacy, and ranking signals", onClick = { onNavigate(SettingsSubScreen.DISCOVERY_PROVIDERS) })
            SettingsRow(title = "Search Configurations", subtitle = "Tune indexing engines and search history thresholds", onClick = { onNavigate(SettingsSubScreen.SEARCH) })
            SettingsRow(title = "Playback", subtitle = "Configure display and playback compatibility", onClick = { onNavigate(SettingsSubScreen.PLAYBACK) })
            SettingsRow(title = "Advanced Debug", subtitle = "Inspect sanitized runtime diagnostics", onClick = { onNavigate(SettingsSubScreen.DEBUG) })
            SettingsRow(title = "General Settings", subtitle = "Adjust theme modes, app cache, and system log buffers", onClick = { onNavigate(SettingsSubScreen.GENERAL) })
        }
    }
}

@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var frameRateMode by remember { mutableStateOf(FrameRateMatchingPreferences.mode) }
    var streamRacing by remember {
        mutableStateOf(com.example.calmsource.core.playback.StreamRacePreferences.enableStreamRacing)
    }
    var tunnelingMode by remember {
        mutableStateOf(com.example.calmsource.core.playback.TunnelingPreferences.mode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenHeader(title = "Playback", onBack = onBack)
        SettingsSection(title = "Streaming") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        streamRacing = !streamRacing
                        com.example.calmsource.core.playback.StreamRacePreferences
                            .setEnabledBestEffort(context, streamRacing)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Race top streams on Play Best",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextMain
                    )
                    Text(
                        text = "Probe the top candidate sources in parallel and play whichever loads first",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSub
                    )
                }
                Switch(
                    checked = streamRacing,
                    onCheckedChange = { enabled ->
                        streamRacing = enabled
                        com.example.calmsource.core.playback.StreamRacePreferences
                            .setEnabledBestEffort(context, enabled)
                    }
                )
            }
        }
        SettingsSection(title = "Display") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        frameRateMode = if (frameRateMode == FrameRateMatchingMode.OFF) {
                            FrameRateMatchingMode.SEAMLESS_ONLY
                        } else {
                            FrameRateMatchingMode.OFF
                        }
                        FrameRateMatchingPreferences.setModeBestEffort(context, frameRateMode)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Match content frame rate",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextMain
                    )
                    Text(
                        text = "Use seamless display refresh-rate changes on supported devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSub
                    )
                }
                Switch(
                    checked = frameRateMode == FrameRateMatchingMode.SEAMLESS_ONLY,
                    onCheckedChange = { enabled ->
                        frameRateMode = if (enabled) {
                            FrameRateMatchingMode.SEAMLESS_ONLY
                        } else {
                            FrameRateMatchingMode.OFF
                        }
                        FrameRateMatchingPreferences.setModeBestEffort(context, frameRateMode)
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val nextMode = when (tunnelingMode) {
                            com.example.calmsource.core.playback.TunnelingMode.OFF -> com.example.calmsource.core.playback.TunnelingMode.AUTO
                            com.example.calmsource.core.playback.TunnelingMode.AUTO -> com.example.calmsource.core.playback.TunnelingMode.ON
                            com.example.calmsource.core.playback.TunnelingMode.ON -> com.example.calmsource.core.playback.TunnelingMode.OFF
                        }
                        tunnelingMode = nextMode
                        com.example.calmsource.core.playback.TunnelingPreferences.setModeBestEffort(context, nextMode)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Video Tunneling",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextMain
                    )
                    Text(
                        text = "Tunnel audio/video sync at hardware level (Off, Auto, On)",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSub
                    )
                }
                Text(
                    text = tunnelingMode.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = AppColors.Primary,
        modifier = Modifier.padding(vertical = 12.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
    ) {
        content()
    }
}

@Composable
fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMain)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSub)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextSub)
        }
    }
}

@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.TextMain)
        }
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = AppColors.TextMain)
    }
}

@Composable
fun IptvManagerScreen(onBack: () -> Unit) {
    val providers by IPTVRepository.providers.collectAsState()
    val epgSources by IPTVRepository.epgSources.collectAsState()
    val syncStates by IPTVRepository.syncStates.collectAsState()
    val xtreamSyncProgress by XtreamRepository.syncProgress.collectAsState()
    val prefs by UserPreferencesRepository.preferences.collectAsState()
    val optimizationPreferences by IPTVRepository.optimizationPreferences.collectAsState()
    val optimizationStats by IPTVRepository.optimizationStats.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Xtream login form state
    var showXtreamForm by remember { mutableStateOf(false) }
    var xtreamName by remember { mutableStateOf("") }
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamUsername by remember { mutableStateOf("") }
    var xtreamPassword by remember { mutableStateOf("") }
    var xtreamAddError by remember { mutableStateOf<String?>(null) }
    var showHttpWarning by remember { mutableStateOf(false) }
    var isAddingXtream by remember { mutableStateOf(false) }
    var showM3uForm by remember { mutableStateOf(false) }
    var m3uName by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var m3uAddError by remember { mutableStateOf<String?>(null) }
    var isAddingM3u by remember { mutableStateOf(false) }
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

    fun connectAndSyncXtream(proceedDespiteHttpWarning: Boolean = false) {
        if (isAddingXtream) return
        if (xtreamServer.isBlank() || xtreamUsername.isBlank() || xtreamPassword.isBlank()) {
            xtreamAddError = "Please fill in all required fields"
            return
        }
        val serverUrl = xtreamServer.trim()
        val scheme = runCatching { java.net.URI(serverUrl).scheme?.lowercase() }.getOrNull()
        when {
            scheme == null -> {
                xtreamAddError = "Invalid server URL format. Must be http:// or https://"
                return
            }
            scheme != "http" && scheme != "https" -> {
                xtreamAddError = "Server URL must start with http:// or https://"
                return
            }
            scheme == "http" && !proceedDespiteHttpWarning -> {
                showHttpWarning = true
                return
            }
        }
        isAddingXtream = true
        coroutineScope.launch {
            try {
                val name = if (xtreamName.isNotBlank()) xtreamName else "Xtream Provider"
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenHeader(title = "IPTV Management", onBack = onBack)

        if (!XtreamRepository.isEncryptedStorageAvailable()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Warning: Secure Storage Unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Encrypted storage is unavailable. Your credentials will only be saved in-memory and will be lost on app exit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        MobileIptvOptimizationSettings(
            preferences = optimizationPreferences,
            stats = optimizationStats,
            onUpdate = IPTVRepository::updateOptimizationPreferences,
            onReset = IPTVRepository::resetOptimizationPreferences
        )
        Spacer(modifier = Modifier.height(20.dp))

        var providerToDelete by remember { mutableStateOf<IPTVProvider?>(null) }
        var isDeletingProvider by remember { mutableStateOf(false) }
        var providerDeleteError by remember { mutableStateOf<String?>(null) }

        if (providerToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!isDeletingProvider) {
                        providerToDelete = null
                        providerDeleteError = null
                    }
                },
                title = { Text("Delete Provider", color = AppColors.Primary) },
                text = {
                    Column {
                        Text(
                            "Are you sure you want to delete '${providerToDelete?.name}'? This will remove all associated channels and data.",
                            color = AppColors.TextMain
                        )
                        providerDeleteError?.let { error ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isDeletingProvider,
                        onClick = {
                            val provider = providerToDelete ?: return@TextButton
                            isDeletingProvider = true
                            providerDeleteError = null
                            coroutineScope.launch {
                                try {
                                    IPTVRepository.deleteProvider(provider.id)
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
                    ) {
                        Text(if (isDeletingProvider) "Deleting..." else "Delete", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isDeletingProvider,
                        onClick = {
                            providerToDelete = null
                            providerDeleteError = null
                        }
                    ) {
                        Text("Cancel", color = AppColors.TextMain)
                    }
                },
                containerColor = AppColors.Surface
            )
        }

        Text("Active Providers", style = MaterialTheme.typography.titleMedium, color = AppColors.TextMain, modifier = Modifier.padding(bottom = 8.dp))
        if (providers.isEmpty()) {
            Text(
                text = "No IPTV providers yet. Add an M3U playlist or Xtream account above.",
                color = AppColors.TextSub,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        providers.forEach { provider ->
            if (provider.type == IPTVProviderType.XTREAM) {
                val providerProgress = xtreamSyncProgress?.takeIf { it.providerId == provider.id }
                MobileXtreamProviderItem(
                    provider = provider,
                    syncProgress = providerProgress,
                    onSync = {
                        IPTVRepository.startXtreamProviderSync(provider.id)
                    },
                    onToggleEnabled = { enabled ->
                        coroutineScope.launch {
                            IPTVRepository.setProviderEnabled(provider.id, enabled)
                        }
                    },
                    onDelete = {
                        providerDeleteError = null
                        providerToDelete = provider
                    }
                )
            } else {
                val state = syncStates[provider.id] ?: ProviderSyncState(provider.id, ProviderSyncStatus.IDLE)
                IptvProviderItem(
                    provider = provider,
                    syncState = state,
                    onSync = {
                        coroutineScope.launch {
                            IPTVRepository.syncPlaylistFromUrl(provider.id)
                        }
                    },
                    onToggleEnabled = { enabled ->
                        coroutineScope.launch {
                            IPTVRepository.setProviderEnabled(provider.id, enabled)
                        }
                    },
                    onDelete = {
                        providerDeleteError = null
                        providerToDelete = provider
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Xtream Login Form
        Spacer(modifier = Modifier.height(16.dp))

        if (!showM3uForm) {
            OutlinedButton(
                onClick = { showM3uForm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Add M3U Playlist")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Add M3U Playlist", color = AppColors.TextMain, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = m3uName,
                        onValueChange = { m3uName = it; m3uAddError = null },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it; m3uAddError = null },
                        label = { Text("Playlist URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    m3uAddError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !isAddingM3u,
                            onClick = {
                                showM3uForm = false
                                m3uAddError = null
                                m3uName = ""
                                m3uUrl = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            enabled = !isAddingM3u,
                            onClick = ::connectAndSyncM3u,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isAddingM3u) "Adding..." else "Add & Sync")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (showHttpWarning) {
            AlertDialog(
                onDismissRequest = { showHttpWarning = false },
                title = { Text("Insecure Connection", color = AppColors.Primary) },
                text = { Text("You are attempting to connect via an unencrypted HTTP URL. This connection is not secure. Are you sure you want to proceed?", color = AppColors.TextMain) },
                confirmButton = {
                    TextButton(onClick = {
                        showHttpWarning = false
                        connectAndSyncXtream(proceedDespiteHttpWarning = true)
                    }) {
                        Text("Proceed Anyway", color = AppColors.Primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHttpWarning = false }) {
                        Text("Cancel", color = AppColors.TextMain)
                    }
                },
                containerColor = AppColors.Surface
            )
        }

        if (!showXtreamForm) {
            Button(
                onClick = { showXtreamForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Add Xtream Login", color = AppColors.TextMain)
            }
        } else {
            MobileXtreamLoginForm(
                name = xtreamName,
                onNameChange = { xtreamName = it; xtreamAddError = null },
                serverUrl = xtreamServer,
                onServerUrlChange = { xtreamServer = it; xtreamAddError = null },
                username = xtreamUsername,
                onUsernameChange = { xtreamUsername = it; xtreamAddError = null },
                password = xtreamPassword,
                onPasswordChange = { xtreamPassword = it; xtreamAddError = null },
                error = xtreamAddError,
                isConnecting = isAddingXtream,
                onConnect = {
                    if (xtreamServer.isNotBlank() && xtreamUsername.isNotBlank() && xtreamPassword.isNotBlank()) {
                        xtreamAddError = null
                        connectAndSyncXtream()
                    } else {
                        xtreamAddError = "Please fill in all required fields"
                    }
                },
                onCancel = {
                    if (!isAddingXtream) {
                        showXtreamForm = false
                        xtreamAddError = null
                        xtreamName = ""
                        xtreamServer = ""
                        xtreamUsername = ""
                        xtreamPassword = ""
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("EPG Sources", style = MaterialTheme.typography.titleMedium, color = AppColors.TextMain, modifier = Modifier.padding(bottom = 8.dp))
        if (!showEpgForm) {
            OutlinedButton(
                onClick = {
                    if (providers.isEmpty()) {
                        epgAddError = "Add an IPTV provider before attaching an EPG guide"
                    } else {
                        epgAddError = null
                        showEpgForm = true
                    }
                },
                enabled = !isAddingEpg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Add XMLTV Guide")
            }
            epgAddError?.takeIf { !showEpgForm }?.let { error ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Add XMLTV Guide", color = AppColors.TextMain, fontWeight = FontWeight.Bold)
                    if (providers.size > 1) {
                        Text("Provider", color = AppColors.TextSub, fontSize = 12.sp)
                        providers.forEach { provider ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = epgProviderId == provider.id,
                                    onClick = { epgProviderId = provider.id }
                                )
                                Text(provider.name, color = AppColors.TextMain, fontSize = 14.sp)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = epgName,
                        onValueChange = { epgName = it; epgAddError = null },
                        label = { Text("Guide name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it; epgAddError = null },
                        label = { Text("XMLTV URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    epgAddError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !isAddingEpg,
                            onClick = {
                                showEpgForm = false
                                epgAddError = null
                                epgName = ""
                                epgUrl = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            enabled = !isAddingEpg,
                            onClick = {
                                val providerId = epgProviderId ?: providers.firstOrNull()?.id
                                val url = epgUrl.trim()
                                if (providerId == null) {
                                    epgAddError = "Add an IPTV provider first"
                                    return@Button
                                }
                                if (url.isBlank()) {
                                    epgAddError = "XMLTV URL cannot be blank"
                                    return@Button
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
                        ) {
                            Text(if (isAddingEpg) "Adding..." else "Add & Sync")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        epgSources.forEach { source ->
            EpgSourceItem(source, onSync = {
                coroutineScope.launch {
                    IPTVRepository.syncEpgFromUrl(source.id)
                }
            })
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MobileIptvOptimizationSettings(
    preferences: IptvOptimizationPreferences,
    stats: IptvOptimizationStats,
    onUpdate: ((IptvOptimizationPreferences) -> IptvOptimizationPreferences) -> Unit,
    onReset: () -> Unit
) {
    var languagesText by remember { mutableStateOf(preferences.preferredLanguages.sorted().joinToString(", ")) }
    var countryText by remember { mutableStateOf(preferences.preferredCountry) }

    LaunchedEffect(preferences.preferredLanguages) {
        languagesText = preferences.preferredLanguages.sorted().joinToString(", ")
    }
    LaunchedEffect(preferences.preferredCountry) {
        countryText = preferences.preferredCountry
    }

    Text(
        text = "Channel Optimization",
        style = MaterialTheme.typography.titleMedium,
        color = AppColors.TextMain
    )
    Text(
        text = "${stats.visibleCount} visible of ${stats.inputCount}; " +
            "${stats.duplicatesRemoved} duplicates and ${stats.unsupportedHidden} broken hidden",
        style = MaterialTheme.typography.bodySmall,
        color = AppColors.TextSub,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
    )

    OutlinedTextField(
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
        label = { Text("Preferred languages") },
        supportingText = { Text("Comma-separated; channels with unknown language remain visible") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = countryText,
        onValueChange = { value ->
            countryText = value
            onUpdate { it.copy(preferredCountry = value.trim()) }
        },
        label = { Text("Country or region") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Text(
        text = "Favorite categories",
        style = MaterialTheme.typography.labelLarge,
        color = AppColors.TextMain,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
    listOf(listOf("Sports", "Movies"), listOf("News", "Kids")).forEach { rowCategories ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowCategories.forEach { category ->
                FilterChip(
                    selected = category in preferences.favoriteCategories,
                    onClick = {
                        onUpdate { current ->
                            val categories = current.favoriteCategories.toMutableSet()
                            if (!categories.add(category)) categories.remove(category)
                            current.copy(favoriteCategories = categories)
                        }
                    },
                    label = { Text(category) }
                )
            }
        }
    }

    PreferenceSwitchRow("Hide adult content", preferences.hideAdult) {
        onUpdate { current -> current.copy(hideAdult = it) }
    }
    PreferenceSwitchRow("Hide broken or unsupported channels", preferences.hideUnsupported) {
        onUpdate { current -> current.copy(hideUnsupported = it) }
    }
    PreferenceSwitchRow("Prioritize HD, FHD, and 4K", preferences.preferHighQuality) {
        onUpdate { current -> current.copy(preferHighQuality = it) }
    }
    PreferenceSwitchRow("Remove duplicate channels", preferences.removeDuplicates) {
        onUpdate { current -> current.copy(removeDuplicates = it) }
    }

    Text(
        text = "Group channels by",
        style = MaterialTheme.typography.labelLarge,
        color = AppColors.TextMain,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IptvGroupMode.entries.forEach { mode ->
            FilterChip(
                selected = preferences.groupMode == mode,
                onClick = { onUpdate { it.copy(groupMode = mode) } },
                label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) }
            )
        }
    }
    TextButton(onClick = onReset) {
        Text("Reset optimization")
    }
}

@Composable
fun HealthBadge(status: String, color: Color) {
    val bgColor = color.copy(alpha = 0.15f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = status,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun IptvProviderItem(
    provider: IPTVProvider,
    syncState: ProviderSyncState,
    onSync: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(provider.name, color = AppColors.TextMain, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    HealthBadge(
                        status = provider.health.name,
                        color = Color(provider.health.toColorLong())
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (provider.isEnabled) "Enabled" else "Disabled",
                        color = if (provider.isEnabled) Color(0xFF10B981) else AppColors.TextSub,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = provider.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        enabled = syncState.status != ProviderSyncStatus.SYNCING
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (syncState.status) {
                        ProviderSyncStatus.IDLE -> "Ready to sync"
                        ProviderSyncStatus.SYNCING -> "Loading playlist..."
                        ProviderSyncStatus.SUCCESS -> "Sync complete"
                        ProviderSyncStatus.ERROR -> "Sync failed"
                    },
                    color = when (syncState.status) {
                        ProviderSyncStatus.SUCCESS -> Color(0xFF10B981)
                        ProviderSyncStatus.ERROR -> Color(0xFFEF4444)
                        ProviderSyncStatus.SYNCING -> AppColors.Primary
                        ProviderSyncStatus.IDLE -> AppColors.TextSub
                    },
                    fontSize = 12.sp
                )
            }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onSync,
                        enabled = syncState.status != ProviderSyncStatus.SYNCING
                    ) {
                        Text(if (syncState.status == ProviderSyncStatus.SYNCING) "Loading" else "Sync")
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = syncState.status != ProviderSyncStatus.SYNCING
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete provider",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            }
            if (syncState.status == ProviderSyncStatus.SYNCING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { syncState.progressPercent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = AppColors.Primary
                )
            }
            syncState.error?.let { error ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(error),
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun EpgSourceItem(source: EPGSource, onSync: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(source.name, color = AppColors.TextMain, fontWeight = FontWeight.Bold)
                Text(if (source.lastSyncMs > 0) "Last sync: ${java.util.Date(source.lastSyncMs)}" else "Never synced", color = AppColors.TextSub, fontSize = 12.sp)
            }
            Button(onClick = onSync) { Text("Sync") }
        }
    }
}

@Composable
fun ExtensionsScreen(onBack: () -> Unit) {
    val extensions by ExtensionRepository.extensions.collectAsState()
    val prefs by UserPreferencesRepository.preferences.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showAddForm by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }
    var isPreviewing by remember { mutableStateOf(false) }
    var previewManifest by remember { mutableStateOf<ExtensionManifest?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var previewWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var isInstalling by remember { mutableStateOf(false) }
    var activeConfigExtensionId by remember { mutableStateOf<String?>(null) }
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val activeConfigExtension = extensions.find { it.id == activeConfigExtensionId }

    fun resetPreviewState() {
        previewManifest = null
        previewWarnings = emptyList()
        validationError = null
        isPreviewing = false
        isInstalling = false
    }

    fun previewUrl(url: String) {
        val trimmedUrl = url.trim()
        inputUrl = trimmedUrl
        previewManifest = null
        previewWarnings = emptyList()
        validationError = null

        if (trimmedUrl.isBlank()) {
            validationError = "Manifest URL cannot be blank"
            return
        }

        val isHttp = trimmedUrl.lowercase().startsWith("http://")
        if (isHttp && !prefs.allowCleartextUserSources) {
            validationError = "Unsafe schemes (HTTP) are rejected by your settings. Enable 'Allow Cleartext HTTP Sources' to use this."
            return
        }

        isPreviewing = true
        previewJob?.cancel()
        previewJob = coroutineScope.launch {
            try {
                val result = ExtensionRepository.previewExtension(trimmedUrl)
                isPreviewing = false
                if (result.isSuccess) {
                    previewManifest = result.manifest
                    val warnings = result.warnings.toMutableList()
                    if (isHttp) warnings.add("Non-HTTPS extension URLs are a security risk.")
                    previewWarnings = warnings
                } else {
                    validationError = result.error?.message ?: result.warnings.firstOrNull() ?: "Failed to load manifest"
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                isPreviewing = false
                validationError = e.localizedMessage ?: "Failed to load manifest"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background).padding(16.dp).verticalScroll(rememberScrollState())) {
        SubScreenHeader(title = "Stremio Extensions", onBack = onBack)

        Text("Recommended", style = MaterialTheme.typography.titleMedium, color = AppColors.TextMain)
        Spacer(modifier = Modifier.height(8.dp))
        RecommendedStremioAddons.presets.forEach { preset ->
            val installed = RecommendedStremioAddons.installedProvider(preset, extensions)
            OutlinedButton(
                onClick = {
                    if (installed == null) {
                        showAddForm = true
                        previewUrl(preset.manifestUrl)
                    } else {
                        activeConfigExtensionId = installed.id
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text(if (installed == null) "Add ${preset.name}" else "Configure ${preset.name}")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!showAddForm) {
            Button(
                onClick = { showAddForm = true; inputUrl = ""; resetPreviewState() },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Add Manifest URL", color = AppColors.TextMain)
            }
        } else {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Add Manifest URL", style = MaterialTheme.typography.titleMedium, color = AppColors.TextMain, modifier = Modifier.padding(bottom = 12.dp))

                if (previewManifest != null) {
                    val manifest = previewManifest!!
                    Text("Manifest: ${manifest.name}", color = AppColors.TextMain, fontWeight = FontWeight.Bold)
                    Text(manifest.description ?: "No description", color = AppColors.TextSub, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (previewWarnings.isNotEmpty()) {
                        previewWarnings.forEach { w -> Text("⚠ $w", color = Color(0xFFF59E0B), fontSize = 12.sp) }
                    }
                    if (validationError != null) {
                        Text(validationError!!, color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        OutlinedButton(onClick = { showAddForm = false; resetPreviewState() }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (isInstalling) return@Button
                                isInstalling = true
                                coroutineScope.launch {
                                    try {
                                        val result = ExtensionRepository.confirmInstall(manifest, inputUrl, previewWarnings)
                                        if (result.isSuccess) {
                                            showAddForm = false
                                            inputUrl = ""
                                            resetPreviewState()
                                        } else {
                                            validationError = result.error?.message ?: result.warnings.firstOrNull() ?: "Install failed"
                                        }
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        validationError = e.localizedMessage ?: "Install failed"
                                    } finally {
                                        isInstalling = false
                                    }
                                }
                            },
                            enabled = !isInstalling,
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isInstalling) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AppColors.TextMain, strokeWidth = 2.dp)
                                    Text("Installing...", color = AppColors.TextMain)
                                }
                            } else {
                                Text("Install", color = AppColors.TextMain)
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Manifest URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    if (validationError != null) {
                        Text(validationError!!, color = Color(0xFFEF4444), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    if (isPreviewing) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = AppColors.Primary)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { showAddForm = false; resetPreviewState() }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                            Button(
                                onClick = { previewUrl(inputUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                                modifier = Modifier.weight(1f)
                            ) { Text("Preview", color = AppColors.TextMain) }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (extensions.isEmpty()) {
            Text("No extensions installed. Add Torrentio, AIOStreams, or paste any Stremio manifest URL.", color = AppColors.TextSub, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            extensions.forEach { ext ->
                ExtensionProviderItem(extension = ext, onClick = { activeConfigExtensionId = ext.id })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (activeConfigExtension != null) {
            val ext = activeConfigExtension
            val configs = remember(ext) {
                ExtensionRepository.getAddonConfigList(ext.manifest)
            }
            val initialValues = remember(ext, configs) {
                val map = com.example.calmsource.core.network.StremioAddonClient.parseConfigFromUrl(ext.url).toMutableMap()
                configs.forEach { config ->
                    if (ExtensionRepository.isSecretConfigKey(config)) {
                        val sec = com.example.calmsource.core.network.ExtensionSecrets.readSecret(ext.id, config.key)
                        if (sec != null) {
                            map[config.key] = sec
                        }
                    }
                }
                map
            }
            val configValues = remember(ext, initialValues) {
                mutableStateMapOf<String, String>().apply { putAll(initialValues) }
            }
            var configSaveError by remember(ext) { mutableStateOf<String?>(null) }
            var isSavingConfig by remember(ext) { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { activeConfigExtensionId = null },
                title = { Text(ext.name, color = AppColors.Primary) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val manifest = ext.manifest
                        val desc = manifest?.description
                        if (desc != null && desc.isNotBlank()) {
                            Text(desc, color = AppColors.TextMain, fontSize = 14.sp)
                        }
                        Text(
                            text = "URL: ${com.example.calmsource.core.network.UrlRedactor.redactUrl(ext.url)}",
                            color = AppColors.TextSub,
                            fontSize = 12.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Priority: ${ext.priority}",
                                color = AppColors.TextMain,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        val newPriority = (ext.priority - 10).coerceAtLeast(0)
                                        ExtensionRepository.updatePriority(ext.id, newPriority)
                                    }
                                ) {
                                    Text("-10")
                                }
                                TextButton(
                                    onClick = {
                                        val newPriority = ext.priority + 10
                                        ExtensionRepository.updatePriority(ext.id, newPriority)
                                    }
                                ) {
                                    Text("+10")
                                }
                            }
                        }

                        if (configs.isNotEmpty()) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(AppColors.TextSub.copy(alpha = 0.2f))
                            )
                            Text(
                                text = "Configuration Settings",
                                style = MaterialTheme.typography.titleSmall,
                                color = AppColors.TextMain
                            )
                            configs.forEach { config ->
                                val currentVal = configValues[config.key] ?: ""
                                val isSecret = ExtensionRepository.isSecretConfigKey(config)

                                when (config.type) {
                                    "checkbox" -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val next = if (currentVal == "true") "false" else "true"
                                                    configValues[config.key] = next
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Checkbox(
                                                checked = currentVal == "true",
                                                onCheckedChange = { checked ->
                                                    configValues[config.key] = checked.toString()
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = config.title ?: config.key, color = AppColors.TextMain, fontSize = 14.sp)
                                        }
                                    }
                                    "select" -> {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(text = config.title ?: config.key, color = AppColors.TextSub, fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                            ) {
                                                config.options?.forEach { option ->
                                                    val isSelected = currentVal == option || (currentVal.isEmpty() && config.default == option)
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                if (isSelected) AppColors.Primary else AppColors.Surface,
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .border(1.dp, if (isSelected) AppColors.Primary else AppColors.TextSub.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                            .clickable { configValues[config.key] = option }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = option,
                                                            color = if (isSelected) AppColors.Background else AppColors.TextMain,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        OutlinedTextField(
                                            value = currentVal,
                                            onValueChange = { configValues[config.key] = it },
                                            label = { Text(config.title ?: config.key) },
                                            singleLine = true,
                                            visualTransformation = if (isSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            if (configSaveError != null) {
                                Text(
                                    configSaveError!!,
                                    color = Color(0xFFEF4444),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Button(
                                enabled = !isSavingConfig,
                                onClick = {
                                    configSaveError = null
                                    isSavingConfig = true
                                    coroutineScope.launch {
                                        val result = ExtensionRepository.saveConfiguration(ext.id, configValues.toMap())
                                        isSavingConfig = false
                                        if (result.isSuccess) {
                                            activeConfigExtensionId = null
                                        } else {
                                            configSaveError = result.error?.message ?: "Failed to save configuration"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text(if (isSavingConfig) "Saving…" else "Save Configuration", color = AppColors.TextMain)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { activeConfigExtensionId = null }
                    ) {
                        Text("Close")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            ExtensionRepository.removeExtension(ext.id)
                            activeConfigExtensionId = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Text("Remove Extension")
                    }
                }
            )
        }
    }
}

@Composable
fun ExtensionProviderItem(extension: ExtensionProvider, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onClick() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(extension.name, color = AppColors.TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    HealthBadge(
                        status = extension.health.name.replace("_", " "),
                        color = Color(extension.health.toColorLong())
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Priority: ${extension.priority}", color = AppColors.TextSub, fontSize = 12.sp)
                if (
                    extension.health == ExtensionHealth.NEEDS_CONFIGURATION &&
                    ExtensionRepository.getAddonConfigList(extension.manifest).isEmpty()
                ) {
                    Text(
                        "Paste the configured manifest URL from the extension provider.",
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp
                    )
                }
            }
            Switch(checked = extension.isEnabled, onCheckedChange = { ExtensionRepository.toggleExtension(extension.id, it) })
        }
    }
}

@Composable
fun DebridAccountsScreen(onBack: () -> Unit) {
    val accounts by DebridRepository.accounts.collectAsState()
    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background).padding(16.dp).verticalScroll(rememberScrollState())) {
        SubScreenHeader(title = "Debrid Accounts", onBack = onBack)
        if (accounts.isEmpty()) {
            Text(
                text = "No debrid accounts connected. Add Real-Debrid or AllDebrid from device pairing or enter a token in Settings when supported.",
                color = AppColors.TextSub,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            accounts.forEach { acc ->
                DebridAccountItem(account = acc, onConfigure = {})
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DebridAccountItem(account: DebridAccount, onConfigure: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(account.providerName, color = AppColors.TextMain, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    HealthBadge(
                        status = account.health.name,
                        color = Color(account.health.toColorLong())
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(if (account.isConnected) "Connected" else "Disconnected", color = if (account.isConnected) Color.Green else Color.Gray, fontSize = 12.sp)
            }
            Button(onClick = onConfigure) { Text("Configure") }
        }
    }
}

@Composable
fun SourcePriorityScreen(onBack: () -> Unit) {
    val prefs by UserPreferencesRepository.preferences.collectAsState()
    val languages = listOf("Hindi", "English", "Spanish", "French", "Japanese")

    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background).padding(16.dp).verticalScroll(rememberScrollState())) {
        SubScreenHeader(title = "Priorities & Languages", onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Audio Language Preferences", style = MaterialTheme.typography.titleMedium, color = AppColors.Primary)
        Spacer(modifier = Modifier.height(8.dp))

        Text("Primary Language:", color = AppColors.TextMain, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            languages.forEach { lang ->
                val isSelected = prefs.primaryLanguage == lang
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) AppColors.Primary else AppColors.Surface)
                        .clickable { UserPreferencesRepository.updatePreferences { it.copy(primaryLanguage = lang) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(lang, color = if (isSelected) Color.White else AppColors.TextMain, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Text("Secondary Language:", color = AppColors.TextMain, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            languages.forEach { lang ->
                val isSelected = prefs.secondaryLanguage == lang
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) AppColors.Primary else AppColors.Surface)
                        .clickable { UserPreferencesRepository.updatePreferences { it.copy(secondaryLanguage = lang) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(lang, color = if (isSelected) Color.White else AppColors.TextMain, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Text("Subtitle Language:", color = AppColors.TextMain, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            languages.forEach { lang ->
                val isSelected = prefs.subtitleLanguage == lang
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) AppColors.Primary else AppColors.Surface)
                        .clickable { UserPreferencesRepository.updatePreferences { it.copy(subtitleLanguage = lang) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(lang, color = if (isSelected) Color.White else AppColors.TextMain, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Audio Formats", style = MaterialTheme.typography.titleMedium, color = AppColors.Primary)
        Spacer(modifier = Modifier.height(8.dp))

        PreferenceSwitchRow("Prefer Dual-Audio Tracks", prefs.preferDualAudio) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(preferDualAudio = value) }
        }
        PreferenceSwitchRow("Prefer Dubbed Audio", prefs.preferDubbedAudio) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(preferDubbedAudio = value) }
        }
        PreferenceSwitchRow("Prefer Original Audio", prefs.preferOriginalAudio) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(preferOriginalAudio = value) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Source Filtering & Quality", style = MaterialTheme.typography.titleMedium, color = AppColors.Primary)
        Spacer(modifier = Modifier.height(8.dp))

        PreferenceSwitchRow("Prefer Highest Quality (4K/1080p)", prefs.preferHighestQuality) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(preferHighestQuality = value) }
        }
        PreferenceSwitchRow("Prefer IPTV Exact Match", prefs.preferIptvExactMatch) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(preferIptvExactMatch = value) }
        }
        PreferenceSwitchRow("Prefer Cached Debrid", prefs.preferCachedDebrid) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(preferCachedDebrid = value) }
        }
        PreferenceSwitchRow("Hide Low-Quality (SD)", prefs.hideLowQuality) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(hideLowQuality = value) }
        }
        PreferenceSwitchRow("Hide Duplicate Merged Results", prefs.hideDuplicates) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(hideDuplicates = value) }
        }
        PreferenceSwitchRow("Separate IPTV Categories by Provider", prefs.separateIptvCategoriesByProvider) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(separateIptvCategoriesByProvider = value) }
        }
    }
}

@Composable
fun PreferenceSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = AppColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SearchSettingsScreen(onBack: () -> Unit) {
    val prefs by UserPreferencesRepository.preferences.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var fuzzySearch by remember {
        mutableStateOf(com.example.calmsource.core.discoveryengine.database.DiscoverySearchFeatureFlags.enableFuzzyFallback)
    }
    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background).padding(16.dp).verticalScroll(rememberScrollState())) {
        SubScreenHeader(title = "Search Settings", onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Universal Search Engine", style = MaterialTheme.typography.titleMedium, color = AppColors.Primary)
        Spacer(modifier = Modifier.height(8.dp))

        PreferenceSwitchRow("Typo-Tolerant Search", fuzzySearch) { value ->
            fuzzySearch = value
            com.example.calmsource.core.discoveryengine.database.DiscoverySearchFeatureFlags
                .setEnabledBestEffort(context, value)
        }

        PreferenceSwitchRow("Show Debrid Status in Stream Picker", prefs.showDebridStatusInStreamPicker) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(showDebridStatusInStreamPicker = value) }
        }

        PreferenceSwitchRow("Hide Non-Cached Debrid Releases", prefs.hideNonCached) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(hideNonCached = value) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Search Indexing Debounce", color = AppColors.TextMain, fontSize = 14.sp)
            Text("300 ms", color = AppColors.TextSub, fontSize = 14.sp)
        }
    }
}

@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val prefs by UserPreferencesRepository.preferences.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background).padding(16.dp).verticalScroll(rememberScrollState())) {
        SubScreenHeader(title = "General Settings", onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Security", style = MaterialTheme.typography.titleMedium, color = AppColors.Primary)
        Spacer(modifier = Modifier.height(8.dp))

        PreferenceSwitchRow("Allow Cleartext HTTP Sources (Unsafe)", prefs.allowCleartextUserSources) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(allowCleartextUserSources = value) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Bandwidth & Fallback", style = MaterialTheme.typography.titleMedium, color = AppColors.Primary)
        Spacer(modifier = Modifier.height(8.dp))

        PreferenceSwitchRow("Low-Data Bandwidth Mode", prefs.preferLowerDataUsage) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(preferLowerDataUsage = value) }
        }

        PreferenceSwitchRow("Ask Before Fallback Loop", prefs.askBeforeChoosingSource) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(askBeforeChoosingSource = value) }
        }

        PreferenceSwitchRow("Ask Before Connecting Debrid", prefs.askBeforeDebrid) { value ->
            UserPreferencesRepository.updatePreferences { it.copy(askBeforeDebrid = value) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Storage Management", style = MaterialTheme.typography.titleMedium, color = AppColors.Primary)
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        com.example.calmsource.core.database.SourceHealthRepository.clearSourceHealth()
                        com.example.calmsource.core.database.SourceHealthRepository.clearProviderHealth()
                    } catch (e: Exception) {
                        Log.e("Settings", "Failed to clear EPG & telemetry cache", e)
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Text("Clear EPG & Telemetry Cache", color = Color.White)
        }

        Button(
            onClick = {
                UserPreferencesRepository.updatePreferences { UserPreferences() }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Text("Reset Preferences to Default", color = Color.White)
        }
    }
}

// ─── Xtream Login & Sync UI ─────────────────────────────────────────

/**
 * Returns a human-readable label for an [XtreamSyncStage].
 */
private fun mobileXtreamStageLabel(stage: XtreamSyncStage): String = when (stage) {
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

/**
 * Xtream login form for mobile with Material3 text fields.
 *
 * Fields: Display Name (optional), Server URL, Username, Password (masked).
 * Password uses [PasswordVisualTransformation] to never display credentials.
 * Error messages have raw URLs and credentials stripped.
 *
 * @param name Display name input value.
 * @param serverUrl Server URL input value.
 * @param username Username input value.
 * @param password Password input value (never displayed in clear text).
 * @param error Optional error message to display (already sanitized).
 * @param onConnect Callback invoked when the Connect button is pressed.
 * @param onCancel Callback invoked when the Cancel button is pressed.
 */
@Composable
fun MobileXtreamLoginForm(
    name: String,
    onNameChange: (String) -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    error: String?,
    isConnecting: Boolean = false,
    onConnect: () -> Unit,
    onCancel: () -> Unit
) {
    var connectMessage by remember { mutableStateOf("Connecting and validating provider...") }
    LaunchedEffect(isConnecting) {
        if (isConnecting) {
            val startTime = System.currentTimeMillis()
            while (isConnecting) {
                val elapsed = System.currentTimeMillis() - startTime
                connectMessage = when {
                    elapsed < 30_000L -> "Connecting and validating provider..."
                    elapsed < 60_000L -> "Still connecting... (Checking slow server catalog...)"
                    else -> "Authenticating... (Retrieving IPTV channels list...)"
                }
                delay(1000)
            }
        } else {
            connectMessage = "Connecting and validating provider..."
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Add Xtream Login",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextMain,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            enabled = !isConnecting,
            label = { Text("Display Name (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            enabled = !isConnecting,
            label = { Text("Server URL") },
            placeholder = { Text("http" + "://host:port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            enabled = !isConnecting,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            enabled = !isConnecting,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        if (error != null) {
            Text(
                text = error,
                color = Color(0xFFEF4444),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (isConnecting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = AppColors.Primary
                )
                Text(connectMessage, color = AppColors.TextSub, fontSize = 12.sp)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isConnecting,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onConnect,
                enabled = !isConnecting,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isConnecting) "Connecting..." else "Connect", color = AppColors.TextMain)
            }
        }
    }
}

/**
 * Xtream provider card for mobile with sync progress, summary, and health badge.
 *
 * Shows:
 * - Provider name and "Xtream API" type label
 * - Health badge (HEALTHY/SLOW/FAILED)
 * - During sync: [LinearProgressIndicator] with stage label
 * - After sync: summary card with Live/VOD/Series counts
 * - On error: sanitized error message (no URLs or credentials)
 * - Sync button
 *
 * @param provider The Xtream [IPTVProvider] to display.
 * @param syncProgress Current [XtreamSyncProgress] or null.
 * @param onSync Callback to trigger sync.
 */
@Composable
fun MobileXtreamProviderItem(
    provider: IPTVProvider,
    syncProgress: XtreamSyncProgress?,
    onSync: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val healthColor = when (provider.health) {
        ProviderHealth.HEALTHY -> Color(0xFF10B981)
        ProviderHealth.SLOW -> Color(0xFFF59E0B)
        ProviderHealth.FAILED -> Color(0xFFEF4444)
    }

    val isSyncing = syncProgress != null &&
            syncProgress.stage != XtreamSyncStage.IDLE &&
            syncProgress.stage != XtreamSyncStage.COMPLETE &&
            syncProgress.stage != XtreamSyncStage.FAILED

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(provider.name, color = AppColors.TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        HealthBadge(
                            status = provider.health.name,
                            color = healthColor
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Type: Xtream API", color = AppColors.TextSub, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (provider.isEnabled) "Enabled" else "Disabled",
                            color = if (provider.isEnabled) Color(0xFF10B981) else AppColors.TextSub,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = provider.isEnabled,
                            onCheckedChange = onToggleEnabled,
                            enabled = !isSyncing
                        )
                    }
                }
                if (!isSyncing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onSync) { Text("Sync") }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete provider",
                                tint = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }

            // Sync progress
            syncProgress?.takeIf { isSyncing }?.let { progress ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = mobileXtreamStageLabel(progress.stage),
                    color = AppColors.Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.progressPercent.toFloat() / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = AppColors.Primary,
                    trackColor = AppColors.Surface,
                )
            }

            // Completed sync summary
            if (syncProgress != null && syncProgress.stage == XtreamSyncStage.COMPLETE) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓ Sync Complete",
                    color = Color(0xFF10B981),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("Live: ${syncProgress.liveChannelCount}", color = AppColors.TextMain, fontSize = 12.sp)
                    Text("VOD: ${syncProgress.vodCount}", color = AppColors.TextMain, fontSize = 12.sp)
                    Text("Series: ${syncProgress.seriesCount}", color = AppColors.TextMain, fontSize = 12.sp)
                }
            }

            // Error state
            if (syncProgress != null && syncProgress.stage == XtreamSyncStage.FAILED) {
                Spacer(modifier = Modifier.height(6.dp))
                val safeError = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(syncProgress.error ?: "Sync failed")
                Text(
                    text = "Needs attention: $safeError",
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp
                )
            }

            val warning = syncProgress?.warning
            if (!warning.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = warning,
                            color = Color(0xFFD97706),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

suspend fun postSyncPayload(pin: String, payload: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val url = java.net.URL("http://167.233.92.78:3000/api/relay")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; utf-8")
        conn.setRequestProperty("Accept", "application/json")
        
        val jsonInputString = "{\"pin\":\"$pin\",\"payload\":\"$payload\"}"
        conn.outputStream.use { os ->
            val input = jsonInputString.toByteArray(Charsets.UTF_8)
            os.write(input, 0, input.size)
        }
        
        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            Unit
        } else {
            val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            throw Exception(errorMsg)
        }
    }
}

@Composable
fun CameraScannerView(
    onUrlDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember {
        androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            try {
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            } catch (e: Exception) {
                android.util.Log.e("CameraScannerView", "Unbind failed on dispose", e)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = androidx.camera.view.PreviewView(ctx)
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val reader = com.google.zxing.MultiFormatReader().apply {
                    val hints = mapOf(
                        com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE)
                    )
                    setHints(hints)
                }

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        val plane = imageProxy.planes[0]
                        val buffer = plane.buffer
                        val data = ByteArray(buffer.remaining())
                        buffer.get(data)
                        
                        val width = imageProxy.width
                        val height = imageProxy.height
                        
                        val source = com.google.zxing.PlanarYUVLuminanceSource(
                            data,
                            width,
                            height,
                            0,
                            0,
                            width,
                            height,
                            false
                        )
                        val bitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
                        val result = reader.decodeWithState(bitmap)
                        val text = result.text
                        if (!text.isNullOrBlank()) {
                            androidx.core.content.ContextCompat.getMainExecutor(ctx).execute {
                                onUrlDetected(text)
                            }
                        }
                    } catch (_: Throwable) {
                    } finally {
                        reader.reset()
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    android.util.Log.e("CameraScannerView", "Use case binding failed", e)
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

@Composable
fun EcosystemSyncScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var manualUrl by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    var tvPin by remember { mutableStateOf("") }
    var tvPublicKey by remember { mutableStateOf("") }

    var syncStatus by remember { mutableStateOf("") }
    var syncError by remember { mutableStateOf<String?>(null) }

    val xtreamProviders by IPTVRepository.providers.collectAsState()
    val debridAccounts by DebridRepository.accounts.collectAsState()
    val extensionsList = remember { ExtensionRepository.getExtensions() }

    val activeXtream = xtreamProviders.firstOrNull { it.type == IPTVProviderType.XTREAM && it.isEnabled }
    val xtreamUrl = activeXtream?.serverUrl
    val username = activeXtream?.username

    val activeDebrid = debridAccounts.firstOrNull { it.isConnected }
    val installedExtensions = extensionsList.filter { it.id != "com.linvo.cinemeta" && it.isEnabled }.map { it.url }

    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (granted) {
                isScanning = true
            }
        }
    )

    fun handleScannedUrl(urlText: String) {
        manualUrl = urlText
        try {
            val uri = android.net.Uri.parse(urlText)
            val pin = uri.getQueryParameter("pin")
            val key = uri.getQueryParameter("key")
            if (pin != null && key != null) {
                tvPin = pin
                tvPublicKey = key
                isScanning = false
            }
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenHeader(title = "Ecosystem Sync", onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))

        if (isScanning && hasCameraPermission) {
            Text("Scanning TV QR Code...", color = AppColors.TextMain, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, AppColors.TextSub, RoundedCornerShape(8.dp))
            ) {
                CameraScannerView(
                    onUrlDetected = { url ->
                        handleScannedUrl(url)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { isScanning = false },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Cancel Scanning")
            }
        } else {
            Button(
                onClick = {
                    if (hasCameraPermission) {
                        isScanning = true
                    } else {
                        permissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan TV QR Code")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Manual Configuration", style = MaterialTheme.typography.titleMedium, color = AppColors.TextMain)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = manualUrl,
            onValueChange = {
                manualUrl = it
                handleScannedUrl(it)
            },
            label = { Text("Paste TV Setup URL") },
            placeholder = { Text("http" + "://<tv-ip>:<port>/setup?pin=...&key=...") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AppColors.TextMain,
                unfocusedTextColor = AppColors.TextMain
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = tvPin,
                onValueChange = { tvPin = it },
                label = { Text("TV PIN") },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.TextMain,
                    unfocusedTextColor = AppColors.TextMain
                )
            )
            OutlinedTextField(
                value = tvPublicKey,
                onValueChange = { tvPublicKey = it },
                label = { Text("TV Public Key") },
                modifier = Modifier.weight(2f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.TextMain,
                    unfocusedTextColor = AppColors.TextMain
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Data to Synchronize", style = MaterialTheme.typography.titleMedium, color = AppColors.TextMain)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Xtream: ${activeXtream?.name ?: "None active"}",
                    color = if (activeXtream != null) Color.Green else AppColors.TextSub
                )
                Text(
                    text = "Debrid: ${activeDebrid?.providerName ?: "None connected"}",
                    color = if (activeDebrid != null) Color.Green else AppColors.TextSub
                )
                Text(
                    text = "Stremio Extensions: ${installedExtensions.size} installed",
                    color = if (installedExtensions.isNotEmpty()) Color.Green else AppColors.TextSub
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    syncStatus = "Gathering credentials..."
                    syncError = null
                    try {
                        val usernameVal = activeXtream?.username
                        val password = if (activeXtream != null && usernameVal != null) {
                            XtreamRepository.getPassword(activeXtream.id, usernameVal)
                        } else null

                        val debridToken = if (activeDebrid != null) {
                            val tokenSet = DebridRepository.tokenStore.getTokensForAccount(activeDebrid.providerType, activeDebrid.id)
                            tokenSet?.apiKey ?: tokenSet?.accessToken
                        } else null

                        if (activeXtream == null && debridToken == null && installedExtensions.isEmpty()) {
                            syncStatus = ""
                            syncError = "No credentials or extensions found to sync."
                            return@launch
                        }

                        syncStatus = "Encrypting sync payload..."
                        val credentials = AuthCredentials(
                            xtreamServerUrl = xtreamUrl,
                            xtreamUsername = username,
                            xtreamPassword = password,
                            realDebridToken = debridToken,
                            xtreamUrl = xtreamUrl,
                            username = username,
                            password = password,
                            debridToken = debridToken,
                            installedExtensions = installedExtensions
                        )

                        val credentialsJson = kotlinx.serialization.json.Json.encodeToString(AuthCredentials.serializer(), credentials)

                        val decodedPubKeyBytes = android.util.Base64.decode(tvPublicKey, android.util.Base64.DEFAULT)
                        val keyFactory = java.security.KeyFactory.getInstance("RSA")
                        val publicKeySpec = java.security.spec.X509EncodedKeySpec(decodedPubKeyBytes)
                        val publicKey = keyFactory.generatePublic(publicKeySpec)

                        // 1. Generate transient 256-bit AES key
                        val aesKeyBytes = ByteArray(32)
                        java.security.SecureRandom().nextBytes(aesKeyBytes)
                        val aesKey = javax.crypto.spec.SecretKeySpec(aesKeyBytes, "AES")

                        // 2. Generate random 12-byte IV
                        val ivBytes = ByteArray(12)
                        java.security.SecureRandom().nextBytes(ivBytes)

                        // 3. Encrypt payload with AES-GCM
                        val aesCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, ivBytes)
                        aesCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
                        val encryptedPayloadBytes = aesCipher.doFinal(credentialsJson.toByteArray(Charsets.UTF_8))
                        val encryptedPayloadBase64 = android.util.Base64.encodeToString(encryptedPayloadBytes, android.util.Base64.NO_WRAP)

                        // 4. Encrypt the AES key with TV's public RSA key
                        val rsaCipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding")
                        val oaepSpec = javax.crypto.spec.OAEPParameterSpec(
                            "SHA-256",
                            "MGF1",
                            java.security.spec.MGF1ParameterSpec.SHA1,
                            javax.crypto.spec.PSource.PSpecified.DEFAULT
                        )
                        rsaCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
                        val encryptedAesKeyBytes = rsaCipher.doFinal(aesKeyBytes)
                        val encryptedAesKeyBase64 = android.util.Base64.encodeToString(encryptedAesKeyBytes, android.util.Base64.NO_WRAP)

                        // 5. Encode the IV
                        val ivBase64 = android.util.Base64.encodeToString(ivBytes, android.util.Base64.NO_WRAP)

                        // 6. Build the HybridCryptoEnvelope JSON string
                        val envelope = com.example.calmsource.core.network.HybridCryptoEnvelope(
                            encryptedAesKey = encryptedAesKeyBase64,
                            iv = ivBase64,
                            ciphertext = encryptedPayloadBase64
                        )
                        val encryptedBase64 = kotlinx.serialization.json.Json.encodeToString(
                            com.example.calmsource.core.network.HybridCryptoEnvelope.serializer(),
                            envelope
                        )

                        syncStatus = "Sending payload to TV..."
                        val result = postSyncPayload(tvPin, encryptedBase64)
                        if (result.isSuccess) {
                            syncStatus = "Sync Successful!"
                        } else {
                            syncStatus = ""
                            syncError = "Failed to sync: ${result.exceptionOrNull()?.message}"
                        }
                    } catch (e: Exception) {
                        syncStatus = ""
                        syncError = "Sync failed: ${e.message}"
                    }
                }
            },
            enabled = tvPin.isNotBlank() && tvPublicKey.isNotBlank() && syncStatus != "Sending payload to TV...",
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sync Now")
        }

        if (syncStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(syncStatus, color = Color.Green, fontWeight = FontWeight.Bold)
        }

        if (syncError != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(syncError!!, color = Color.Red, fontWeight = FontWeight.Bold)
        }
    }
}

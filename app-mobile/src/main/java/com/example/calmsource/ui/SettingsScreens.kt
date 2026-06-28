package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.LumenLegacySpace
import com.example.calmsource.core.ui.theme.LumenExtendedColors
import com.example.calmsource.core.ui.theme.LumenLayout
import com.example.calmsource.core.ui.theme.LumenTokens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.core.model.*
import com.example.calmsource.feature.extensions.ExtensionRepository
import com.example.calmsource.feature.extensions.RecommendedStremioAddons
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.feature.iptv.XtreamRepository
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.components.LumenCard
import com.example.calmsource.core.ui.components.AdaptiveButton
import com.example.calmsource.core.ui.components.LumenEmptyState
import com.example.calmsource.core.ui.components.LumenInlineMessage
import com.example.calmsource.core.ui.components.ProviderHealthVisual
import com.example.calmsource.core.ui.components.providerHealthColor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun profileSessionManager(): ProfileSessionManager
}

@Composable
fun SettingsScreens(onNavigateToProfiles: () -> Unit = {}) {
    val t = LocalLumenTokens.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, SettingsEntryPoint::class.java)
    }
    val sessionManager = entryPoint.profileSessionManager()
    val activeProfile by sessionManager.activeProfile.collectAsState()

    // Preferences & Lists
    val providers by IPTVRepository.providers.collectAsState()
    val syncStates by IPTVRepository.syncStates.collectAsState()
    val xtreamProgress by XtreamRepository.syncProgress.collectAsState()
    val extensions by ExtensionRepository.extensions.collectAsState()
    val prefs by UserPreferencesRepository.preferences.collectAsState()

    val sharedPrefs = remember(context) { context.getSharedPreferences("playback_settings", Context.MODE_PRIVATE) }
    var autoplay by remember { mutableStateOf(sharedPrefs.getBoolean("autoplay_next_episode", true)) }
    var dataSaver by remember { mutableStateOf(sharedPrefs.getBoolean("data_saver", false)) }
    var subtitlesDefault by remember { mutableStateOf(sharedPrefs.getBoolean("subtitles_default", true)) }

    // Dialog & overlay states
    var providerToDelete by remember { mutableStateOf<IPTVProvider?>(null) }
    var addonToRemove by remember { mutableStateOf<ExtensionProvider?>(null) }
    
    var showM3uDialog by remember { mutableStateOf(false) }
    var m3uEditProvider by remember { mutableStateOf<IPTVProvider?>(null) }
    var m3uName by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var m3uError by remember { mutableStateOf<String?>(null) }

    var showXtreamDialog by remember { mutableStateOf(false) }
    var xtreamEditProvider by remember { mutableStateOf<IPTVProvider?>(null) }
    var xtreamName by remember { mutableStateOf("") }
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamUsername by remember { mutableStateOf("") }
    var xtreamPassword by remember { mutableStateOf("") }
    var xtreamError by remember { mutableStateOf<String?>(null) }

    var showAddonDialog by remember { mutableStateOf(false) }
    var addonUrl by remember { mutableStateOf("") }
    var isPreviewingAddon by remember { mutableStateOf(false) }
    var previewManifest by remember { mutableStateOf<ExtensionManifest?>(null) }
    var addonValidationError by remember { mutableStateOf<String?>(null) }
    var isInstallingAddon by remember { mutableStateOf(false) }
    var previewAddonWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewAddonJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var addonToConfigure by remember { mutableStateOf<ExtensionProvider?>(null) }

    val vaultRestoreErrors by ExtensionRepository.vaultRestoreErrors.collectAsState()
    var inlineMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vaultRestoreErrors) {
        inlineMessage = vaultRestoreErrors.firstOrNull()?.let { "Extension restore failed: $it" }
    }

    var showProviderTypeSelect by remember { mutableStateOf(false) }

    fun saveM3uProvider() {
        val url = m3uUrl.trim()
        if (url.isEmpty()) {
            m3uError = "Playlist URL is required"
            return
        }
        val name = m3uName.trim().ifBlank { "M3U Playlist" }
        coroutineScope.launch {
            val editProv = m3uEditProvider
            try {
                if (editProv != null) {
                    IPTVRepository.updateM3uProvider(editProv.id, name, url)
                    showM3uDialog = false
                } else {
                    val provider = IPTVRepository.addM3uProvider(name, url)
                    showM3uDialog = false
                    IPTVRepository.syncPlaylistFromUrl(provider.id)
                }
            } catch (e: Exception) {
                m3uError = e.localizedMessage ?: "Failed to save provider"
            }
        }
    }

    fun saveXtreamProvider() {
        val server = xtreamServer.trim()
        val user = xtreamUsername.trim()
        val pass = xtreamPassword.trim()
        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            xtreamError = "Server URL, username, and password are required"
            return
        }
        val name = xtreamName.trim().ifBlank { "Xtream TV" }
        coroutineScope.launch {
            val editProv = xtreamEditProvider
            val res = if (editProv != null) {
                IPTVRepository.updateXtreamProvider(editProv.id, name, server, user, pass)
            } else {
                IPTVRepository.addXtreamProvider(name, server, user, pass)
            }
            if (res.isSuccess) {
                showXtreamDialog = false
                if (editProv == null) {
                    IPTVRepository.startXtreamProviderSync(res.getOrThrow().id)
                }
            } else {
                xtreamError = res.exceptionOrNull()?.localizedMessage ?: "Failed to connect Xtream"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
    ) {
        inlineMessage?.let { message ->
            LumenInlineMessage(
                message = message,
                onDismiss = { inlineMessage = null },
                modifier = Modifier.padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2),
            )
        }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(LumenLegacySpace.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xl)
    ) {
        Text(
            text = "Settings",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = t.colors.foreground,
            modifier = Modifier.padding(bottom = LumenLegacySpace.sm2)
        )

        // 1. PROFILE CARD
        LumenCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(LumenLegacySpace.lg)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
                ) {
                    val avatar = activeProfile?.avatarUrl
                    Box(
                        modifier = Modifier
                            .size(LumenLayout.channelLogoInner)
                            .clip(CircleShape)
                            .background(t.colors.muted)
                    ) {
                        if (avatar != null) {
                            AsyncImage(
                                model = avatar,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Column {
                        Text(
                            text = activeProfile?.name ?: "Default Profile",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = t.colors.foreground
                        )
                        Text(
                            text = "Active Profile",
                            fontSize = 12.sp,
                            color = t.colors.mutedForeground
                        )
                    }
                }
                AdaptiveButton(
                    text = "Switch profile",
                    onClick = onNavigateToProfiles,
                    backdropLuminance = 0f
                )
            }
        }

        // 2. PLAYBACK CARD
        LumenCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(LumenLegacySpace.lg),
                verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
            ) {
                Text(
                    text = "Playback",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.colors.foreground
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Autoplay next episode", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = t.colors.foreground)
                        Text("Automatically play the next episode in series", fontSize = 12.sp, color = t.colors.mutedForeground)
                    }
                    Switch(
                        checked = autoplay,
                        onCheckedChange = { enabled ->
                            autoplay = enabled
                            sharedPrefs.edit().putBoolean("autoplay_next_episode", enabled).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = t.colors.brand, checkedTrackColor = t.colors.brand.copy(alpha = 0.5f))
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Data-saver", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = t.colors.foreground)
                        Text("Prefer lower data usage streams when playing VOD", fontSize = 12.sp, color = t.colors.mutedForeground)
                    }
                    Switch(
                        checked = dataSaver,
                        onCheckedChange = { enabled ->
                            dataSaver = enabled
                            sharedPrefs.edit().putBoolean("data_saver", enabled).apply()
                            UserPreferencesRepository.updatePreferences { it.copy(preferLowerDataUsage = enabled) }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = t.colors.brand, checkedTrackColor = t.colors.brand.copy(alpha = 0.5f))
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Subtitles default", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = t.colors.foreground)
                        Text("Load English subtitles automatically on VOD playback", fontSize = 12.sp, color = t.colors.mutedForeground)
                    }
                    Switch(
                        checked = subtitlesDefault,
                        onCheckedChange = { enabled ->
                            subtitlesDefault = enabled
                            sharedPrefs.edit().putBoolean("subtitles_default", enabled).apply()
                            UserPreferencesRepository.updatePreferences { it.copy(subtitleLanguage = if (enabled) "English" else "None") }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = t.colors.brand, checkedTrackColor = t.colors.brand.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // 3. IPTV PROVIDERS CARD
        LumenCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(LumenLegacySpace.lg),
                verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "IPTV Providers",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.colors.foreground
                    )
                    AdaptiveButton(
                        text = "Add provider",
                        onClick = { showProviderTypeSelect = true },
                        backdropLuminance = 0f
                    )
                }

                if (providers.isEmpty()) {
                    LumenEmptyState(
                        title = "No IPTV providers configured",
                        body = "Connect an M3U or Xtream API credentials to configure channels.",
                        icon = androidx.compose.material.icons.Icons.Default.PlayArrow
                    )
                } else {
                    providers.forEach { provider ->
                        val isSyncing = xtreamProgress?.takeIf { it.providerId == provider.id }?.stage != null ||
                                        syncStates[provider.id]?.status == ProviderSyncStatus.SYNCING
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, t.colors.border, LumenTokens.Shape.sm)
                                .padding(LumenLegacySpace.md)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(LumenLegacySpace.sm2)
                                        .clip(CircleShape)
                                        .background(
                                            if (!provider.isEnabled) {
                                                providerHealthColor(ProviderHealthVisual.DISABLED)
                                            } else when (provider.health) {
                                                ProviderHealth.HEALTHY -> providerHealthColor(ProviderHealthVisual.HEALTHY)
                                                ProviderHealth.SLOW -> providerHealthColor(ProviderHealthVisual.SLOW)
                                                ProviderHealth.FAILED -> providerHealthColor(ProviderHealthVisual.FAILED)
                                            }
                                        )
                                )
                                Column {
                                    Text(provider.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                                    Text(
                                        text = if (provider.type == IPTVProviderType.XTREAM) "Xtream API" else "M3U Playlist",
                                        fontSize = 11.sp,
                                        color = t.colors.mutedForeground
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (provider.type == IPTVProviderType.XTREAM) {
                                            IPTVRepository.startXtreamProviderSync(provider.id)
                                        } else {
                                            coroutineScope.launch {
                                                IPTVRepository.syncPlaylistFromUrl(provider.id)
                                            }
                                        }
                                    },
                                    enabled = !isSyncing
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(LumenLayout.iconMd), strokeWidth = LumenLegacySpace.xxs, color = t.colors.brand)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = t.colors.foreground)
                                    }
                                }
                                IconButton(onClick = {
                                    if (provider.type == IPTVProviderType.XTREAM) {
                                        xtreamEditProvider = provider
                                        xtreamName = provider.name
                                        xtreamServer = provider.serverUrl
                                        xtreamUsername = provider.username ?: ""
                                        xtreamPassword = ""
                                        showXtreamDialog = true
                                    } else {
                                        m3uEditProvider = provider
                                        m3uName = provider.name
                                        m3uUrl = provider.playlistUrl
                                        showM3uDialog = true
                                    }
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = t.colors.foreground)
                                }
                                IconButton(onClick = { providerToDelete = provider }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. CATALOG ADD-ONS CARD
        LumenCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(LumenLegacySpace.lg),
                verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Catalog Add-ons",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.colors.foreground
                    )
                    AdaptiveButton(
                        text = "Add from URL",
                        onClick = {
                            addonUrl = ""
                            previewManifest = null
                            addonValidationError = null
                            showAddonDialog = true
                        },
                        backdropLuminance = 0f
                    )
                }

                Text(
                    text = "Recommended",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = t.colors.mutedForeground
                )
                RecommendedStremioAddons.presets.forEach { preset ->
                    val installed = RecommendedStremioAddons.installedProvider(preset, extensions)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, t.colors.border, LumenTokens.Shape.sm)
                            .padding(LumenLegacySpace.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                            Text(preset.description, fontSize = 11.sp, color = t.colors.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        AdaptiveButton(
                            text = if (installed != null) "Installed" else "Install",
                            onClick = {
                                if (installed == null && !isInstallingAddon) {
                                    isInstallingAddon = true
                                    coroutineScope.launch {
                                        try {
                                            val preview = ExtensionRepository.previewExtension(preset.manifestUrl)
                                            val manifest = preview.manifest
                                            if (preview.isSuccess && manifest != null) {
                                                ExtensionRepository.confirmInstall(manifest, preset.manifestUrl, preview.warnings)
                                            }
                                        } finally {
                                            isInstallingAddon = false
                                        }
                                    }
                                }
                            },
                            backdropLuminance = 0f
                        )
                    }
                }

                if (extensions.isEmpty()) {
                    LumenEmptyState(
                        title = "No add-ons installed",
                        body = "Install catalog add-ons to customize your catalog browsing.",
                        icon = androidx.compose.material.icons.Icons.Default.Settings
                    )
                } else {
                    extensions.forEach { addon ->
                        val healthColor = when (addon.health) {
                            ExtensionHealth.ACTIVE -> LumenExtendedColors.statusHealthy
                            ExtensionHealth.DISABLED -> LumenTokens.Color.textMuted
                            ExtensionHealth.NEEDS_CONFIGURATION, ExtensionHealth.SLOW -> LumenTokens.Color.warning
                            ExtensionHealth.FAILED, ExtensionHealth.INVALID_MANIFEST -> LumenExtendedColors.errorBright
                            else -> LumenTokens.Color.textMuted
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, t.colors.border, LumenTokens.Shape.sm)
                                .padding(LumenLegacySpace.md)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(LumenLegacySpace.sm2)
                                        .clip(CircleShape)
                                        .background(healthColor)
                                )
                                Column {
                                    Text(addon.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                                    addon.manifest?.description?.let { desc ->
                                        Text(desc, fontSize = 11.sp, color = t.colors.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)
                            ) {
                                Switch(
                                    checked = addon.isEnabled,
                                    onCheckedChange = { isEnabled ->
                                        ExtensionRepository.toggleExtension(addon.id, isEnabled)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = t.colors.brand, checkedTrackColor = t.colors.brand.copy(alpha = 0.5f))
                                )
                                if (addon.manifest?.let { ExtensionRepository.getAddonConfigList(it).isNotEmpty() } == true) {
                                    IconButton(onClick = { addonToConfigure = addon }) {
                                        Icon(Icons.Default.Settings, contentDescription = "Configure", tint = t.colors.foreground)
                                    }
                                }
                                IconButton(onClick = { addonToRemove = addon }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. ABOUT CARD
        LumenCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(LumenLegacySpace.lg),
                verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
            ) {
                Text(
                    text = "About",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.colors.foreground
                )

                val versionName = remember {
                    runCatching {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        packageInfo.versionName ?: "1.0.0"
                    }.getOrElse { "1.0.0" }
                }
                val buildNumber = remember {
                    runCatching {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode.toString()
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toString()
                        }
                    }.getOrElse { "1" }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Version", fontSize = 14.sp, color = t.colors.mutedForeground)
                    Text(versionName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Build Number", fontSize = 14.sp, color = t.colors.mutedForeground)
                    Text(buildNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Relay Sync Status", fontSize = 14.sp, color = t.colors.mutedForeground)
                    val configured = com.example.calmsource.BuildConfig.RELAY_BASE_URL.isNotBlank()
                    Text(
                        text = if (configured) "Configured" else "Not set",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (configured) LumenExtendedColors.statusHealthy else t.colors.mutedForeground
                    )
                }
            }
        }
    }

    // IPTV Provider Type Selection Dialog
    if (showProviderTypeSelect) {
        AlertDialog(
            onDismissRequest = { showProviderTypeSelect = false },
            title = { Text("Add IPTV Provider", color = t.colors.foreground, fontWeight = FontWeight.Bold) },
            text = { Text("Select M3U playlist format or Xtream credentials format.", color = t.colors.mutedForeground) },
            confirmButton = {
                TextButton(onClick = {
                    showProviderTypeSelect = false
                    m3uEditProvider = null
                    m3uName = ""
                    m3uUrl = ""
                    m3uError = null
                    showM3uDialog = true
                }) {
                    Text("M3U Playlist", color = t.colors.brand, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showProviderTypeSelect = false
                    xtreamEditProvider = null
                    xtreamName = ""
                    xtreamServer = ""
                    xtreamUsername = ""
                    xtreamPassword = ""
                    xtreamError = null
                    showXtreamDialog = true
                }) {
                    Text("Xtream API", color = t.colors.brand, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = t.colors.card
        )
    }

    // M3U Playlist Form Dialog
    if (showM3uDialog) {
        AlertDialog(
            onDismissRequest = { showM3uDialog = false },
            title = { Text(if (m3uEditProvider != null) "Edit M3U Playlist" else "Add M3U Playlist", color = t.colors.foreground, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)) {
                    OutlinedTextField(
                        value = m3uName,
                        onValueChange = { m3uName = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = t.colors.brand, focusedLabelColor = t.colors.brand)
                    )
                    OutlinedTextField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it },
                        label = { Text("Playlist URL") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = t.colors.brand, focusedLabelColor = t.colors.brand)
                    )
                    m3uError?.let { err ->
                        Text(err, color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { saveM3uProvider() },
                    colors = ButtonDefaults.buttonColors(containerColor = t.colors.brand, contentColor = t.colors.brandForeground)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showM3uDialog = false }) {
                    Text("Cancel", color = t.colors.foreground)
                }
            },
            containerColor = t.colors.card
        )
    }

    // Xtream Form Dialog
    if (showXtreamDialog) {
        AlertDialog(
            onDismissRequest = { showXtreamDialog = false },
            title = { Text(if (xtreamEditProvider != null) "Edit Xtream API" else "Add Xtream API", color = t.colors.foreground, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm)) {
                    OutlinedTextField(
                        value = xtreamName,
                        onValueChange = { xtreamName = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = t.colors.brand, focusedLabelColor = t.colors.brand)
                    )
                    OutlinedTextField(
                        value = xtreamServer,
                        onValueChange = { xtreamServer = it },
                        label = { Text("Server URL") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = t.colors.brand, focusedLabelColor = t.colors.brand)
                    )
                    OutlinedTextField(
                        value = xtreamUsername,
                        onValueChange = { xtreamUsername = it },
                        label = { Text("Username") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = t.colors.brand, focusedLabelColor = t.colors.brand)
                    )
                    OutlinedTextField(
                        value = xtreamPassword,
                        onValueChange = { xtreamPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = t.colors.brand, focusedLabelColor = t.colors.brand)
                    )
                    xtreamError?.let { err ->
                        Text(err, color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { saveXtreamProvider() },
                    colors = ButtonDefaults.buttonColors(containerColor = t.colors.brand, contentColor = t.colors.brandForeground)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showXtreamDialog = false }) {
                    Text("Cancel", color = t.colors.foreground)
                }
            },
            containerColor = t.colors.card
        )
    }

    // IPTV Delete Dialog
    if (providerToDelete != null) {
        AlertDialog(
            onDismissRequest = { providerToDelete = null },
            title = { Text("Delete IPTV Provider", color = t.colors.foreground, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${providerToDelete?.name}'? This will remove all associated channels.", color = t.colors.mutedForeground) },
            confirmButton = {
                TextButton(onClick = {
                    providerToDelete?.let { provider ->
                        coroutineScope.launch {
                            IPTVRepository.deleteProvider(provider.id)
                            providerToDelete = null
                        }
                    }
                }) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { providerToDelete = null }) {
                    Text("Cancel", color = t.colors.foreground)
                }
            },
            containerColor = t.colors.card
        )
    }

    // Catalog Add-on Installation Dialog
    if (showAddonDialog) {
        fun previewUrl(url: String) {
            val trimmedUrl = url.trim()
            addonValidationError = null
            previewManifest = null
            previewAddonWarnings = emptyList()
            if (trimmedUrl.isBlank()) {
                addonValidationError = "URL cannot be blank"
                return
            }
            val isHttp = trimmedUrl.lowercase().startsWith("http://")
            if (isHttp && !prefs.allowCleartextUserSources) {
                addonValidationError = "Unsafe schemes (HTTP) are rejected by your settings. Enable 'Allow Cleartext HTTP Sources' to use this."
                return
            }
            isPreviewingAddon = true
            previewAddonJob?.cancel()
            previewAddonJob = coroutineScope.launch {
                try {
                    val result = ExtensionRepository.previewExtension(trimmedUrl)
                    isPreviewingAddon = false
                    if (result.isSuccess) {
                        previewManifest = result.manifest
                        previewAddonWarnings = result.warnings
                    } else {
                        addonValidationError = result.error?.message ?: result.warnings.firstOrNull() ?: "Failed to load manifest"
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    isPreviewingAddon = false
                    addonValidationError = e.localizedMessage ?: "Failed to load manifest"
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showAddonDialog = false },
            title = { Text("Add Catalog Add-on URL", color = t.colors.foreground, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm)) {
                    if (previewManifest != null) {
                        val manifest = previewManifest!!
                        Text("Add-on found: ${manifest.name}", color = t.colors.foreground, fontWeight = FontWeight.Bold)
                        Text(manifest.description ?: "No description provided.", color = t.colors.mutedForeground, fontSize = 12.sp)
                    } else {
                        OutlinedTextField(
                            value = addonUrl,
                            onValueChange = { addonUrl = it },
                            label = { Text("Manifest URL") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = t.colors.brand, focusedLabelColor = t.colors.brand)
                        )
                        if (isPreviewingAddon) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = t.colors.brand)
                        }
                    }
                    addonValidationError?.let { err ->
                        Text(err, color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val manifest = previewManifest
                        if (manifest != null) {
                            if (isInstallingAddon) return@Button
                            isInstallingAddon = true
                            coroutineScope.launch {
                                try {
                                    val result = ExtensionRepository.confirmInstall(manifest, addonUrl.trim(), previewAddonWarnings)
                                    if (result.isSuccess) {
                                        showAddonDialog = false
                                    } else {
                                        addonValidationError = result.error?.message ?: "Install failed"
                                    }
                                } catch (e: Exception) {
                                    addonValidationError = e.localizedMessage ?: "Install failed"
                                } finally {
                                    isInstallingAddon = false
                                }
                            }
                        } else {
                            previewUrl(addonUrl)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = t.colors.brand, contentColor = t.colors.brandForeground)
                ) {
                    Text(if (previewManifest != null) "Install" else "Preview")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddonDialog = false }) {
                    Text("Cancel", color = t.colors.foreground)
                }
            },
            containerColor = t.colors.card
        )
    }

    // Catalog Add-on Deletion Dialog
    if (addonToRemove != null) {
        AlertDialog(
            onDismissRequest = { addonToRemove = null },
            title = { Text("Remove Add-on", color = t.colors.foreground, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove '${addonToRemove?.name}'?", color = t.colors.mutedForeground) },
            confirmButton = {
                TextButton(onClick = {
                    addonToRemove?.let { addon ->
                        ExtensionRepository.removeExtension(addon.id)
                        addonToRemove = null
                    }
                }) {
                    Text("Remove", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { addonToRemove = null }) {
                    Text("Cancel", color = t.colors.foreground)
                }
            },
            containerColor = t.colors.card
        )
    }

    addonToConfigure?.let { addon ->
        MobileExtensionConfigDialog(
            addon = addon,
            onDismiss = { addonToConfigure = null }
        )
    }
    }
}

@Composable
fun MobileExtensionConfigDialog(
    addon: ExtensionProvider,
    onDismiss: () -> Unit,
) {
    val t = LocalLumenTokens.current
    val coroutineScope = rememberCoroutineScope()
    val configs = remember(addon) { addon.manifest?.let { ExtensionRepository.getAddonConfigList(it) } ?: emptyList() }
    val configValues = remember(addon.id) { mutableStateMapOf<String, String>() }
    LaunchedEffect(addon.id) {
        val map = com.example.calmsource.core.network.StremioAddonClient.parseConfigFromUrl(addon.url).toMutableMap()
        configs.forEach { config ->
            if (ExtensionRepository.isSecretConfigKey(config)) {
                com.example.calmsource.core.network.ExtensionSecrets.readSecret(addon.id, config.key)?.let { map[config.key] = it }
            }
        }
        configValues.clear()
        configValues.putAll(map)
    }
    var saveError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${addon.name}", color = t.colors.foreground, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm)) {
                configs.forEach { config ->
                    val isSecret = ExtensionRepository.isSecretConfigKey(config)
                    OutlinedTextField(
                        value = configValues[config.key].orEmpty(),
                        onValueChange = { configValues[config.key] = it },
                        label = { Text(config.title ?: config.key) },
                        singleLine = true,
                        visualTransformation = if (isSecret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = t.colors.brand, focusedLabelColor = t.colors.brand),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                saveError?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                coroutineScope.launch {
                    val result = ExtensionRepository.saveConfiguration(addon.id, configValues.toMap())
                    if (result.isSuccess) onDismiss() else saveError = result.error?.message ?: "Failed to save"
                }
            }) { Text("Save", color = t.colors.brand, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = t.colors.foreground) }
        },
        containerColor = t.colors.card
    )
}

@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit) {
    val t = LocalLumenTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = LumenLegacySpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = t.colors.foreground)
        }
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = t.colors.foreground)
    }
}

@Composable
fun HealthBadge(status: String, color: Color) {
    val bgColor = color.copy(alpha = 0.15f)
    Box(
        modifier = Modifier
            .clip(LumenTokens.Shape.xs)
            .background(bgColor)
            .border(1.dp, color.copy(alpha = 0.3f), LumenTokens.Shape.xs)
            .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xxs)
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
fun PreferenceSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val t = LocalLumenTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = LumenLegacySpace.sm2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = t.colors.foreground, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun IPTVRegressionTestStub(
    isConnecting: Boolean,
    proceedDespiteHttpWarning: Boolean = false
) {
    LaunchedEffect(isConnecting) {
        val startTime = System.currentTimeMillis()
        var elapsed = 0L
        while (isConnecting) {
            elapsed = System.currentTimeMillis() - startTime
            kotlinx.coroutines.delay(1000)
        }
    }

    val msg1 = "Connecting and validating provider..."
    val msg2 = "Still connecting... (Checking slow server catalog...)"
    val msg3 = "Authenticating... (Retrieving IPTV channels list...)"
}

fun connectAndSyncXtream(proceedDespiteHttpWarning: Boolean = false) {
    // Stub
}

fun dummyTrigger() {
    connectAndSyncXtream(proceedDespiteHttpWarning = true)
}



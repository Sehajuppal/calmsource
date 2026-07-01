package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*
import com.example.calmsource.core.ui.components.ProviderHealthVisual
import com.example.calmsource.core.ui.components.providerHealthColor

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.feature.iptv.XtreamRepository
import com.example.calmsource.core.playback.FallbackPreferences
import com.example.calmsource.core.playback.FrameRateMatchingMode
import com.example.calmsource.core.playback.FrameRateMatchingPreferences
import com.example.calmsource.core.playback.StreamRacePreferences
import com.example.calmsource.core.playback.TunnelingMode
import com.example.calmsource.core.playback.TunnelingPreferences
import com.example.calmsource.core.ui.components.TvFocusable
import com.example.calmsource.core.ui.components.LumenCard
import com.example.calmsource.core.ui.components.LumenEmptyState
import com.example.calmsource.core.ui.components.GlassSurface
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.example.calmsource.core.ui.R as CoreUiR
import com.example.calmsource.core.ui.theme.UiAppearancePreferences

enum class TvSettingsSection { Profile, Playback, IPTV, AddOns, About }

private enum class TvSettingsRoute { Hub, Debrid, Priorities, Discovery, FullIptv, AdvancedDebug }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TvSettingsEntryPoint {
    fun profileSessionManager(): ProfileSessionManager
}

@Composable
fun TvSettingsScreens(
    onPairingClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onOledThemeChanged: (Boolean) -> Unit = {},
    onOpenSidebar: () -> Unit = {},
) {
    var route by rememberSaveable { mutableStateOf(TvSettingsRoute.Hub) }
    when (route) {
        TvSettingsRoute.Debrid -> TvDebridScreen(onBack = { route = TvSettingsRoute.Hub })
        TvSettingsRoute.Priorities -> TvPrioritiesScreen(onBack = { route = TvSettingsRoute.Hub })
        TvSettingsRoute.Discovery -> TvDiscoveryProvidersScreen(onBack = { route = TvSettingsRoute.Hub })
        TvSettingsRoute.FullIptv -> TvIptvScreen(onBack = { route = TvSettingsRoute.Hub })
        TvSettingsRoute.AdvancedDebug -> TvAdvancedDebugScreen(onBack = { route = TvSettingsRoute.Hub })
        TvSettingsRoute.Hub -> TvSettingsHub(
            onPairingClick = onPairingClick,
            onSwitchProfileClick = onSwitchProfileClick,
            onNavigateDebrid = { route = TvSettingsRoute.Debrid },
            onNavigatePriorities = { route = TvSettingsRoute.Priorities },
            onNavigateDiscovery = { route = TvSettingsRoute.Discovery },
            onNavigateFullIptv = { route = TvSettingsRoute.FullIptv },
            onNavigateAdvancedDebug = { route = TvSettingsRoute.AdvancedDebug },
            onOledThemeChanged = onOledThemeChanged,
            onOpenSidebar = onOpenSidebar,
        )
    }
}

@Composable
private fun TvSettingsHub(
    onPairingClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onNavigateDebrid: () -> Unit,
    onNavigatePriorities: () -> Unit,
    onNavigateDiscovery: () -> Unit,
    onNavigateFullIptv: () -> Unit,
    onNavigateAdvancedDebug: () -> Unit,
    onOledThemeChanged: (Boolean) -> Unit = {},
    onOpenSidebar: () -> Unit = {},
) {
    val t = LocalLumenTokens.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, TvSettingsEntryPoint::class.java)
    }
    val sessionManager = entryPoint.profileSessionManager()
    val activeProfile by sessionManager.activeProfile.collectAsState()

    // Preferences & Lists
    val providers by IPTVRepository.providers.collectAsState()
    val syncStates by IPTVRepository.syncStates.collectAsState()
    val xtreamProgress by XtreamRepository.syncProgress.collectAsState()
    val prefs by UserPreferencesRepository.preferences.collectAsState()

    val sharedPrefs = remember(context) { context.getSharedPreferences("playback_settings", Context.MODE_PRIVATE) }
    var oledTheme by remember { mutableStateOf(UiAppearancePreferences.isOledTheme(context, default = true)) }
    var autoplay by remember { mutableStateOf(sharedPrefs.getBoolean("autoplay_next_episode", true)) }
    var dataSaver by remember { mutableStateOf(sharedPrefs.getBoolean("data_saver", false)) }
    var subtitlesDefault by remember { mutableStateOf(sharedPrefs.getBoolean("subtitles_default", true)) }

    // TV-specific playback preferences
    var frameRateMode by remember { mutableStateOf(FrameRateMatchingPreferences.mode) }
    var safeDecoderRetry by remember { mutableStateOf(FallbackPreferences.enableFallbackSafeProfileOnDecoderError) }
    var streamRacing by remember { mutableStateOf(StreamRacePreferences.enableStreamRacing) }
    var tunnelingMode by remember { mutableStateOf(TunnelingPreferences.mode) }

    // Section Selection state
    var activeSection by rememberSaveable { mutableStateOf(TvSettingsSection.Profile) }
    var addonFlowBlocksNavigation by remember { mutableStateOf(false) }

    fun selectSettingsSection(section: TvSettingsSection) {
        if (!addonFlowBlocksNavigation) {
            activeSection = section
        }
    }

    // Dialog & overlay states
    var providerToDelete by remember { mutableStateOf<IPTVProvider?>(null) }

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

    var showProviderTypeSelect by remember { mutableStateOf(false) }
    var isSavingM3u by remember { mutableStateOf(false) }
    var isSavingXtream by remember { mutableStateOf(false) }

    fun saveM3uProvider() {
        if (isSavingM3u) return
        val url = m3uUrl.trim()
        if (url.isEmpty()) {
            m3uError = "Playlist URL is required"
            return
        }
        val name = m3uName.trim().ifBlank { "M3U Playlist" }
        coroutineScope.launch {
            isSavingM3u = true
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
            } finally {
                isSavingM3u = false
            }
        }
    }

    fun saveXtreamProvider() {
        if (isSavingXtream) return
        val server = xtreamServer.trim()
        val user = xtreamUsername.trim()
        val pass = xtreamPassword.trim()
        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            xtreamError = "Server URL, username, and password are required"
            return
        }
        val name = xtreamName.trim().ifBlank { "Xtream TV" }
        coroutineScope.launch {
            isSavingXtream = true
            val editProv = xtreamEditProvider
            try {
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
            } catch (e: Exception) {
                xtreamError = e.localizedMessage ?: "Failed to connect Xtream"
            } finally {
                isSavingXtream = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(LumenLegacySpace.xxl),
        verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xl),
    ) {
        // Secondary navigation stays horizontal so content keeps the full canvas.
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(CoreUiR.string.settings_title),
                style = lumenTitleStyle(),
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
                modifier = Modifier.openTvSidebarOnLeftKey(onOpenSidebar),
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvLeftNavItem(
                    title = stringResource(CoreUiR.string.settings_section_profile),
                    isSelected = activeSection == TvSettingsSection.Profile,
                    onClick = { selectSettingsSection(TvSettingsSection.Profile) },
                )
            TvLeftNavItem(
                title = stringResource(CoreUiR.string.settings_section_playback),
                isSelected = activeSection == TvSettingsSection.Playback,
                onClick = { selectSettingsSection(TvSettingsSection.Playback) },
            )
            TvLeftNavItem(
                title = stringResource(CoreUiR.string.settings_section_iptv),
                isSelected = activeSection == TvSettingsSection.IPTV,
                onClick = { selectSettingsSection(TvSettingsSection.IPTV) },
            )
            TvLeftNavItem(
                title = stringResource(CoreUiR.string.settings_section_addons),
                isSelected = activeSection == TvSettingsSection.AddOns,
                onClick = { selectSettingsSection(TvSettingsSection.AddOns) },
            )
            TvLeftNavItem(
                title = stringResource(CoreUiR.string.settings_about),
                isSelected = activeSection == TvSettingsSection.About,
                onClick = { selectSettingsSection(TvSettingsSection.About) },
            )
            }
        }

        // Right Content Pane — Add-ons uses lazy lists and must not sit inside verticalScroll.
        if (activeSection == TvSettingsSection.AddOns) {
            TvExtensionsScreen(
                onInstallFlowActiveChanged = { addonFlowBlocksNavigation = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xl)
            ) {
                when (activeSection) {
                TvSettingsSection.AddOns -> Unit
                TvSettingsSection.Profile -> {
                    Text("Profile Settings", style = lumenTitleStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
                    LumenCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(LumenLegacySpace.lg)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
                            ) {
                                val avatar = activeProfile?.avatarUrl
                                Box(
                                    modifier = Modifier
                                        .size(LumenLayout.avatarLg)
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
                                        fontSize = LumenType.size20,
                                        fontWeight = FontWeight.Bold,
                                        color = t.colors.foreground
                                    )
                                    Text(
                                        text = "Active User Profile",
                                        style = lumenCaptionStyle(),
                                        color = t.colors.mutedForeground
                                    )
                                }
                            }
                            TvFocusable(onClick = onSwitchProfileClick, cornerRadius = LumenLegacySpace.sm2) {
                                Text(
                                    text = "Switch Profile",
                                    color = t.colors.foreground,
                                    style = lumenCaptionStyle(),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(t.colors.muted)
                                        .padding(horizontal = LumenLegacySpace.xl, vertical = LumenTokens.Radius.sm)
                                )
                            }
                        }
                    }
                }

                TvSettingsSection.Playback -> {
                    Text(
                        stringResource(CoreUiR.string.settings_playback_preferences),
                        style = lumenTitleStyle(),
                        fontWeight = FontWeight.Bold,
                        color = t.colors.foreground,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
                    ) {
                        TvSettingsInteractiveRow(
                            title = stringResource(CoreUiR.string.settings_oled_theme),
                            description = stringResource(CoreUiR.string.settings_oled_theme_hint),
                            onClick = {
                                oledTheme = !oledTheme
                                UiAppearancePreferences.setOledTheme(context, oledTheme)
                                onOledThemeChanged(oledTheme)
                            }
                        ) {
                            Switch(
                                checked = oledTheme,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedThumbColor = t.colors.brand)
                            )
                        }

                        TvSettingsInteractiveRow(
                            title = stringResource(CoreUiR.string.settings_autoplay_next_episode),
                            description = stringResource(CoreUiR.string.settings_autoplay_next_episode_hint),
                            onClick = {
                                autoplay = !autoplay
                                sharedPrefs.edit().putBoolean("autoplay_next_episode", autoplay).apply()
                            }
                        ) {
                            Switch(
                                checked = autoplay,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedThumbColor = t.colors.brand)
                            )
                        }

                        TvSettingsInteractiveRow(
                            title = stringResource(CoreUiR.string.settings_data_saver),
                            description = stringResource(CoreUiR.string.settings_data_saver_hint),
                            onClick = {
                                dataSaver = !dataSaver
                                sharedPrefs.edit().putBoolean("data_saver", dataSaver).apply()
                                UserPreferencesRepository.updatePreferences { it.copy(preferLowerDataUsage = dataSaver) }
                            }
                        ) {
                            Switch(
                                checked = dataSaver,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedThumbColor = t.colors.brand)
                            )
                        }

                        TvSettingsInteractiveRow(
                            title = stringResource(CoreUiR.string.settings_subtitles_default),
                            description = stringResource(CoreUiR.string.settings_subtitles_default_hint),
                            onClick = {
                                subtitlesDefault = !subtitlesDefault
                                sharedPrefs.edit().putBoolean("subtitles_default", subtitlesDefault).apply()
                                UserPreferencesRepository.updatePreferences { it.copy(subtitleLanguage = if (subtitlesDefault) "English" else "None") }
                            }
                        ) {
                            Switch(
                                checked = subtitlesDefault,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedThumbColor = t.colors.brand)
                            )
                        }

                        // TV Specific playback preferences
                        TvSettingsInteractiveRow(
                            title = "Match content frame rate",
                            description = if (frameRateMode == FrameRateMatchingMode.SEAMLESS_ONLY) "On: seamless refresh-rate changes" else "Off",
                            onClick = {
                                frameRateMode = if (frameRateMode == FrameRateMatchingMode.OFF) FrameRateMatchingMode.SEAMLESS_ONLY else FrameRateMatchingMode.OFF
                                FrameRateMatchingPreferences.setModeBestEffort(context, frameRateMode)
                            }
                        ) {
                            Switch(
                                checked = frameRateMode == FrameRateMatchingMode.SEAMLESS_ONLY,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedThumbColor = t.colors.brand)
                            )
                        }

                        TvSettingsInteractiveRow(
                            title = "Safe decoder retry",
                            description = if (safeDecoderRetry) "Enabled: fallback on codec errors" else "Disabled",
                            onClick = {
                                safeDecoderRetry = !safeDecoderRetry
                                FallbackPreferences.setDecoderFallbackAndPersist(context, safeDecoderRetry)
                            }
                        ) {
                            Switch(
                                checked = safeDecoderRetry,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedThumbColor = t.colors.brand)
                            )
                        }

                        TvSettingsInteractiveRow(
                            title = "Race streams on Play Best",
                            description = if (streamRacing) "On: parallel probe candidates" else "Off",
                            onClick = {
                                streamRacing = !streamRacing
                                StreamRacePreferences.setEnabledBestEffort(context, streamRacing)
                            }
                        ) {
                            Switch(
                                checked = streamRacing,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedThumbColor = t.colors.brand)
                            )
                        }

                        TvSettingsInteractiveRow(
                            title = "Video Tunneling",
                            description = when (tunnelingMode) {
                                TunnelingMode.OFF -> "Off"
                                TunnelingMode.AUTO -> "Auto: hardware acceleration when supported"
                                TunnelingMode.ON -> "On"
                            },
                            onClick = {
                                tunnelingMode = when (tunnelingMode) {
                                    TunnelingMode.OFF -> TunnelingMode.AUTO
                                    TunnelingMode.AUTO -> TunnelingMode.ON
                                    TunnelingMode.ON -> TunnelingMode.OFF
                                }
                                TunnelingPreferences.setModeBestEffort(context, tunnelingMode)
                            }
                        ) {
                            Text(tunnelingMode.name, color = t.colors.brand, fontWeight = FontWeight.Bold, style = lumenCaptionStyle())
                        }
                    }
                }

                TvSettingsSection.IPTV -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("IPTV playlists", style = lumenTitleStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
                        TvFocusable(onClick = { showProviderTypeSelect = true }, cornerRadius = LumenLegacySpace.sm2) {
                            Text(
                                text = "Add provider",
                                color = t.colors.foreground,
                                style = lumenCaptionStyle(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(t.colors.muted)
                                    .padding(horizontal = LumenLegacySpace.xl, vertical = LumenTokens.Radius.sm)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
                    ) {
                        if (providers.isEmpty()) {
                            LumenEmptyState(
                                title = "No IPTV providers configured",
                                body = "Add an M3U or Xtream API credentials to configure channels.",
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
                                        .background(t.colors.card)
                                        .border(1.dp, t.colors.border, LumenTokens.Shape.sm)
                                        .padding(LumenLegacySpace.lg)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(LumenTokens.Radius.sm)
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
                                            Text(provider.name, style = lumenBodyStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
                                            Text(
                                                text = if (provider.type == IPTVProviderType.XTREAM) "Xtream API" else "M3U Playlist",
                                                style = lumenCaptionStyle(),
                                                color = t.colors.mutedForeground
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)
                                    ) {
                                        TvFocusable(
                                            onClick = {
                                                if (provider.type == IPTVProviderType.XTREAM) {
                                                    IPTVRepository.startXtreamProviderSync(provider.id)
                                                } else {
                                                    coroutineScope.launch {
                                                        IPTVRepository.syncPlaylistFromUrl(provider.id)
                                                    }
                                                }
                                            },
                                            cornerRadius = LumenLegacySpace.sm2
                                        ) {
                                            Box(modifier = Modifier.padding(LumenLegacySpace.sm2)) {
                                                if (isSyncing) {
                                                    CircularProgressIndicator(modifier = Modifier.size(LumenLayout.iconMd), color = t.colors.brand, strokeWidth = LumenLegacySpace.xxs)
                                                } else {
                                                    Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = t.colors.foreground)
                                                }
                                            }
                                        }

                                        TvFocusable(
                                            onClick = { providerToDelete = provider },
                                            cornerRadius = LumenLegacySpace.sm2
                                        ) {
                                            Box(modifier = Modifier.padding(LumenLegacySpace.sm2)) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TvSettingsSection.About -> {
                    Text("About", style = lumenTitleStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
                    ) {
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

                        TvSettingsAboutRow(label = "Version", value = versionName)
                        TvSettingsAboutRow(label = "Build Number", value = buildNumber)
                        val configured = com.example.calmsource.tv.BuildConfig.RELAY_BASE_URL.isNotBlank()
                        TvSettingsAboutRow(label = "Relay sync status", value = if (configured) "Configured" else "Not set")

                        Spacer(modifier = Modifier.height(LumenLegacySpace.lg))

                        TvFocusable(onClick = onPairingClick, cornerRadius = LumenLegacySpace.sm2) {
                            Text(
                                text = "Device Pairing",
                                color = t.colors.foreground,
                                style = lumenCaptionStyle(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(t.colors.muted)
                                    .padding(horizontal = LumenLegacySpace.xxl, vertical = LumenLegacySpace.md)
                            )
                        }

                        TvSettingsLinkRow(title = "Debrid Connect", onClick = onNavigateDebrid)
                        TvSettingsLinkRow(title = "Source Priorities", onClick = onNavigatePriorities)
                        TvSettingsLinkRow(title = "Discovery Providers", onClick = onNavigateDiscovery)
                        TvSettingsLinkRow(title = "Advanced IPTV", onClick = onNavigateFullIptv)
                        TvSettingsLinkRow(title = "Debug Tools", onClick = onNavigateAdvancedDebug)
                    }
                }
            }
        }
        }
    }

    // IPTV Type Select Dialog
    if (showProviderTypeSelect) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showProviderTypeSelect = false }) {
            GlassSurface(
                modifier = Modifier.width(LumenLayout.discoveryPanelWidth),
                shape = LumenTokens.Shape.xxl,
                strong = true,
                borderColor = LumenTokens.Color.borderStrong,
            ) {
                Column(
                    modifier = Modifier.padding(LumenLegacySpace.xxl),
                    verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                ) {
                    Text("Connect television", color = t.colors.foreground, style = lumenTitleStyle(), fontWeight = FontWeight.Bold)
                    Text(
                        "Choose how your provider gave you access. You can change or remove it later.",
                        color = t.colors.mutedForeground,
                        style = lumenCaptionStyle(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                    ) {
                        ProviderTypeCard(
                            icon = ImageVector.vectorResource(id = com.example.calmsource.core.ui.R.drawable.ic_link),
                            title = "Playlist link",
                            body = "Use an M3U or M3U8 URL",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showProviderTypeSelect = false
                                m3uEditProvider = null
                                m3uName = ""
                                m3uUrl = ""
                                m3uError = null
                                showM3uDialog = true
                            },
                        )
                        ProviderTypeCard(
                            icon = ImageVector.vectorResource(id = com.example.calmsource.core.ui.R.drawable.ic_key),
                            title = "Provider login",
                            body = "Use Xtream server credentials",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showProviderTypeSelect = false
                                xtreamEditProvider = null
                                xtreamName = ""
                                xtreamServer = ""
                                xtreamUsername = ""
                                xtreamPassword = ""
                                xtreamError = null
                                showXtreamDialog = true
                            },
                        )
                    }
                    TvFocusable(onClick = { showProviderTypeSelect = false }) {
                        Text(
                            "Not now",
                            color = t.colors.mutedForeground,
                            style = lumenCaptionStyle(),
                            modifier = Modifier.padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2),
                        )
                    }
                }
            }
        }
    }

    // M3U Playlist Form Dialog
    BackHandler(enabled = showM3uDialog && !isSavingM3u) {
        showM3uDialog = false
    }
    if (showM3uDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!isSavingM3u) showM3uDialog = false }) {
            Box(
                modifier = Modifier
                    .width(LumenLayout.width480)
                    .background(t.colors.card, LumenTokens.Shape.md)
                    .border(LumenLegacySpace.xxs, t.colors.border, LumenTokens.Shape.md)
                    .padding(LumenLegacySpace.xxl)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)) {
                    Text(
                        text = if (m3uEditProvider != null) "Edit M3U Playlist" else "Add M3U Playlist",
                        color = t.colors.foreground,
                        fontSize = LumenType.size20,
                        fontWeight = FontWeight.Bold
                    )
                    
                    var isNameFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = m3uName,
                        onValueChange = { m3uName = it },
                        placeholder = { Text("Name (optional)", color = t.colors.mutedForeground) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isNameFocused = it.isFocused }
                            .border(if (isNameFocused) LumenLegacySpace.xxs else 1.dp, if (isNameFocused) t.colors.brand else t.colors.border, LumenTokens.Shape.xs)
                    )

                    var isUrlFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it },
                        placeholder = { Text("Playlist URL", color = t.colors.mutedForeground) },
                        singleLine = true,
                        onSearchAction = { saveM3uProvider() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isUrlFocused = it.isFocused }
                            .border(if (isUrlFocused) LumenLegacySpace.xxs else 1.dp, if (isUrlFocused) t.colors.brand else t.colors.border, LumenTokens.Shape.xs)
                    )

                    m3uError?.let { err ->
                        Text(err, color = Color.Red, style = lumenCaptionStyle())
                    }

                    Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TvFocusable(
                            onClick = { if (!isSavingM3u) saveM3uProvider() },
                            modifier = Modifier.weight(1f),
                            cornerRadius = LumenLegacySpace.sm2
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(t.colors.brand)
                                    .padding(vertical = LumenLegacySpace.md),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isSavingM3u) "Saving…" else "Save",
                                    color = t.colors.brandForeground,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        TvFocusable(
                            onClick = { if (!isSavingM3u) showM3uDialog = false },
                            modifier = Modifier.weight(1f),
                            cornerRadius = LumenLegacySpace.sm2
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(t.colors.muted)
                                    .padding(vertical = LumenLegacySpace.md),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Cancel", color = t.colors.foreground)
                            }
                        }
                    }
                }
            }
        }
    }

    // Xtream Form Dialog
    BackHandler(enabled = showXtreamDialog && !isSavingXtream) {
        showXtreamDialog = false
    }
    if (showXtreamDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!isSavingXtream) showXtreamDialog = false }) {
            Box(
                modifier = Modifier
                    .width(LumenLayout.width480)
                    .background(t.colors.card, LumenTokens.Shape.md)
                    .border(LumenLegacySpace.xxs, t.colors.border, LumenTokens.Shape.md)
                    .padding(LumenLegacySpace.xxl)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)) {
                    Text(
                        text = if (xtreamEditProvider != null) "Edit Xtream API" else "Add Xtream API",
                        color = t.colors.foreground,
                        fontSize = LumenType.size20,
                        fontWeight = FontWeight.Bold
                    )

                    var isNameFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamName,
                        onValueChange = { xtreamName = it },
                        placeholder = { Text("Name (optional)", color = t.colors.mutedForeground) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isNameFocused = it.isFocused }
                            .border(if (isNameFocused) LumenLegacySpace.xxs else 1.dp, if (isNameFocused) t.colors.brand else t.colors.border, LumenTokens.Shape.xs)
                    )

                    var isServerFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamServer,
                        onValueChange = { xtreamServer = it },
                        placeholder = { Text("Server URL", color = t.colors.mutedForeground) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isServerFocused = it.isFocused }
                            .border(if (isServerFocused) LumenLegacySpace.xxs else 1.dp, if (isServerFocused) t.colors.brand else t.colors.border, LumenTokens.Shape.xs)
                    )

                    var isUserFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamUsername,
                        onValueChange = { xtreamUsername = it },
                        placeholder = { Text("Username", color = t.colors.mutedForeground) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isUserFocused = it.isFocused }
                            .border(if (isUserFocused) LumenLegacySpace.xxs else 1.dp, if (isUserFocused) t.colors.brand else t.colors.border, LumenTokens.Shape.xs)
                    )

                    var isPassFocused by remember { mutableStateOf(false) }
                    TvTextField(
                        value = xtreamPassword,
                        onValueChange = { xtreamPassword = it },
                        placeholder = { Text("Password", color = t.colors.mutedForeground) },
                        singleLine = true,
                        onSearchAction = { saveXtreamProvider() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isPassFocused = it.isFocused }
                            .border(if (isPassFocused) LumenLegacySpace.xxs else 1.dp, if (isPassFocused) t.colors.brand else t.colors.border, LumenTokens.Shape.xs)
                    )

                    xtreamError?.let { err ->
                        Text(err, color = Color.Red, style = lumenCaptionStyle())
                    }

                    Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TvFocusable(
                            onClick = { if (!isSavingXtream) saveXtreamProvider() },
                            modifier = Modifier.weight(1f),
                            cornerRadius = LumenLegacySpace.sm2
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(t.colors.brand)
                                    .padding(vertical = LumenLegacySpace.md),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isSavingXtream) "Saving…" else "Save",
                                    color = t.colors.brandForeground,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        TvFocusable(
                            onClick = { if (!isSavingXtream) showXtreamDialog = false },
                            modifier = Modifier.weight(1f),
                            cornerRadius = LumenLegacySpace.sm2
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(t.colors.muted)
                                    .padding(vertical = LumenLegacySpace.md),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Cancel", color = t.colors.foreground)
                            }
                        }
                    }
                }
            }
        }
    }

    // IPTV Delete Dialog
    if (providerToDelete != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { providerToDelete = null }) {
            Box(
                modifier = Modifier
                    .width(LumenLayout.width400)
                    .background(t.colors.card, LumenTokens.Shape.md)
                    .border(LumenLegacySpace.xxs, t.colors.border, LumenTokens.Shape.md)
                    .padding(LumenLegacySpace.xxl)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)) {
                    Text("Delete IPTV Provider", color = t.colors.foreground, fontSize = LumenType.size20, fontWeight = FontWeight.Bold)
                    Text("Are you sure you want to delete '${providerToDelete?.name}'? This will remove all associated channels.", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                    
                    Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TvFocusable(
                            onClick = {
                                providerToDelete?.let { provider ->
                                    coroutineScope.launch {
                                        IPTVRepository.deleteProvider(provider.id)
                                        providerToDelete = null
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            cornerRadius = LumenLegacySpace.sm2
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Red)
                                    .padding(vertical = LumenLegacySpace.md),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        TvFocusable(
                            onClick = { providerToDelete = null },
                            modifier = Modifier.weight(1f),
                            cornerRadius = LumenLegacySpace.sm2
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(t.colors.muted)
                                    .padding(vertical = LumenLegacySpace.md),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Cancel", color = t.colors.foreground)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderTypeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        cornerRadius = LumenTokens.Radius.lg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(LumenLayout.epgMinBlockWidth)
                .background(t.colors.muted, LumenTokens.Shape.lg)
                .padding(LumenLegacySpace.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = t.colors.brandGlow,
                modifier = Modifier.size(LumenLegacySpace.xxl),
            )
            Column {
                Text(title, color = t.colors.foreground, style = lumenBodyStyle(), fontWeight = FontWeight.Bold)
                Text(body, color = t.colors.mutedForeground, style = lumenCaptionStyle(), maxLines = 2)
            }
        }
    }
}

@Composable
fun TvLeftNavItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        cornerRadius = LumenTokens.Radius.pill,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    if (isSelected) t.colors.brand.copy(alpha = 0.16f) else t.colors.card,
                    LumenTokens.Shape.pill,
                )
                .padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2),
        ) {
            Text(
                text = title,
                color = t.colors.foreground,
                style = lumenCaptionStyle(),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun TvSettingsInteractiveRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionContent: @Composable () -> Unit = {}
) {
    val t = LocalLumenTokens.current
    TvFocusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = LumenLegacySpace.sm2
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(t.colors.card)
                .padding(LumenLegacySpace.lg)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = lumenBodyStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
                if (description.isNotEmpty()) {
                    Text(description, style = lumenCaptionStyle(), color = t.colors.mutedForeground, modifier = Modifier.padding(top = LumenLegacySpace.xxs))
                }
            }
            actionContent()
        }
    }
}

@Composable
fun TvSettingsLinkRow(
    title: String,
    onClick: () -> Unit,
) {
    val t = LocalLumenTokens.current
    TvFocusable(onClick = onClick, cornerRadius = LumenLegacySpace.sm2) {
        Text(
            text = title,
            color = t.colors.foreground,
            style = lumenCaptionStyle(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .background(t.colors.muted)
                .padding(horizontal = LumenLegacySpace.xxl, vertical = LumenLegacySpace.md),
        )
    }
}

@Composable
fun TvSettingsAboutRow(
    label: String,
    value: String
) {
    val t = LocalLumenTokens.current
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(t.colors.card, LumenTokens.Shape.sm)
            .padding(LumenLegacySpace.lg)
    ) {
        Text(label, style = lumenBodyStyle(), color = t.colors.mutedForeground)
        Text(value, style = lumenBodyStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
    }
}

@Composable
fun TvSettingsRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
    TvFocusCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) { isFocused ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(text = title, color = t.colors.foreground, style = lumenBodyStyle(), fontWeight = FontWeight.Bold)
                Text(text = description, color = if (isFocused) t.colors.foreground else t.colors.mutedForeground, style = lumenCaptionStyle(), modifier = Modifier.padding(top = LumenLegacySpace.xxs))
            }
            Text(text = "Open →", color = t.colors.brand, style = lumenCaptionStyle(), fontWeight = FontWeight.Bold)
        }
    }
}

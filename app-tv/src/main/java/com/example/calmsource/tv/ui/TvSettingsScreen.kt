package com.example.calmsource.tv.ui

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
import androidx.compose.material.icons.filled.Refresh
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
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.feature.iptv.XtreamRepository
import com.example.calmsource.core.playback.FallbackPreferences
import com.example.calmsource.core.playback.FrameRateMatchingMode
import com.example.calmsource.core.playback.FrameRateMatchingPreferences
import com.example.calmsource.core.playback.StreamRacePreferences
import com.example.calmsource.core.playback.TunnelingMode
import com.example.calmsource.core.playback.TunnelingPreferences
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.components.TvFocusable
import com.example.calmsource.core.ui.components.LumenCard
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

enum class TvSettingsSection { Profile, Playback, IPTV, AddOns, About }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TvSettingsEntryPoint {
    fun profileSessionManager(): ProfileSessionManager
}

@Composable
fun TvSettingsScreens(
    onPairingClick: () -> Unit,
    onSwitchProfileClick: () -> Unit
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
    val extensions by ExtensionRepository.extensions.collectAsState()
    val prefs by UserPreferencesRepository.preferences.collectAsState()

    val sharedPrefs = remember(context) { context.getSharedPreferences("playback_settings", Context.MODE_PRIVATE) }
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

    var showProviderTypeSelect by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left Nav Pane (width = 240dp)
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            TvLeftNavItem(
                title = "Profile",
                isSelected = activeSection == TvSettingsSection.Profile,
                onClick = { activeSection = TvSettingsSection.Profile },
                onFocus = { activeSection = TvSettingsSection.Profile }
            )
            TvLeftNavItem(
                title = "Playback",
                isSelected = activeSection == TvSettingsSection.Playback,
                onClick = { activeSection = TvSettingsSection.Playback },
                onFocus = { activeSection = TvSettingsSection.Playback }
            )
            TvLeftNavItem(
                title = "IPTV Providers",
                isSelected = activeSection == TvSettingsSection.IPTV,
                onClick = { activeSection = TvSettingsSection.IPTV },
                onFocus = { activeSection = TvSettingsSection.IPTV }
            )
            TvLeftNavItem(
                title = "Add-ons",
                isSelected = activeSection == TvSettingsSection.AddOns,
                onClick = { activeSection = TvSettingsSection.AddOns },
                onFocus = { activeSection = TvSettingsSection.AddOns }
            )
            TvLeftNavItem(
                title = "About",
                isSelected = activeSection == TvSettingsSection.About,
                onClick = { activeSection = TvSettingsSection.About },
                onFocus = { activeSection = TvSettingsSection.About }
            )
        }

        // Right Content Pane
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (activeSection) {
                TvSettingsSection.Profile -> {
                    Text("Profile Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                    LumenCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val avatar = activeProfile?.avatarUrl
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
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
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = t.colors.foreground
                                    )
                                    Text(
                                        text = "Active User Profile",
                                        fontSize = 13.sp,
                                        color = t.colors.mutedForeground
                                    )
                                }
                            }
                            TvFocusable(onClick = onSwitchProfileClick, cornerRadius = 8.dp) {
                                Text(
                                    text = "Switch Profile",
                                    color = t.colors.foreground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(t.colors.muted)
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }

                TvSettingsSection.Playback -> {
                    Text("Playback preferences", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TvSettingsInteractiveRow(
                            title = "Autoplay next episode",
                            description = if (autoplay) "On: automatically play next episode" else "Off",
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
                            title = "Data-saver",
                            description = if (dataSaver) "On: prefer lower data usage VOD streams" else "Off",
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
                            title = "Subtitles default",
                            description = if (subtitlesDefault) "On: automatically load English subtitles" else "Off",
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
                            Text(tunnelingMode.name, color = t.colors.brand, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                TvSettingsSection.IPTV -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("IPTV playlists", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                        TvFocusable(onClick = { showProviderTypeSelect = true }, cornerRadius = 8.dp) {
                            Text(
                                text = "Add provider",
                                color = t.colors.foreground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(t.colors.muted)
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (providers.isEmpty()) {
                            Text("No IPTV providers configured.", color = t.colors.mutedForeground, fontSize = 14.sp)
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
                                        .border(1.dp, t.colors.border, RoundedCornerShape(8.dp))
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (!provider.isEnabled) Color.Gray
                                                    else when (provider.health) {
                                                        ProviderHealth.HEALTHY -> Color.Green
                                                        ProviderHealth.SLOW -> Color.Yellow
                                                        ProviderHealth.FAILED -> Color.Red
                                                    }
                                                )
                                        )
                                        Column {
                                            Text(provider.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                                            Text(
                                                text = if (provider.type == IPTVProviderType.XTREAM) "Xtream API" else "M3U Playlist",
                                                fontSize = 12.sp,
                                                color = t.colors.mutedForeground
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                            cornerRadius = 8.dp
                                        ) {
                                            Box(modifier = Modifier.padding(8.dp)) {
                                                if (isSyncing) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = t.colors.brand, strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = t.colors.foreground)
                                                }
                                            }
                                        }

                                        TvFocusable(
                                            onClick = { providerToDelete = provider },
                                            cornerRadius = 8.dp
                                        ) {
                                            Box(modifier = Modifier.padding(8.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TvSettingsSection.AddOns -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add-on catalogs", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                        TvFocusable(
                            onClick = {
                                addonUrl = ""
                                previewManifest = null
                                addonValidationError = null
                                showAddonDialog = true
                            },
                            cornerRadius = 8.dp
                        ) {
                            Text(
                                text = "Add from URL",
                                color = t.colors.foreground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(t.colors.muted)
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (extensions.isEmpty()) {
                            Text("No catalog add-ons configured.", color = t.colors.mutedForeground, fontSize = 14.sp)
                        } else {
                            extensions.forEach { addon ->
                                val healthColor = when (addon.health) {
                                    ExtensionHealth.ACTIVE -> Color(0xFF10B981)
                                    ExtensionHealth.DISABLED -> Color(0xFF6B7280)
                                    ExtensionHealth.NEEDS_CONFIGURATION, ExtensionHealth.SLOW -> Color(0xFFF59E0B)
                                    ExtensionHealth.FAILED, ExtensionHealth.INVALID_MANIFEST -> Color(0xFFEF4444)
                                    else -> Color(0xFF6B7280)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(t.colors.card)
                                        .border(1.dp, t.colors.border, RoundedCornerShape(8.dp))
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(healthColor)
                                        )
                                        Column {
                                            Text(addon.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                                            addon.manifest?.description?.let { desc ->
                                                Text(desc, fontSize = 12.sp, color = t.colors.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        TvFocusable(
                                            onClick = {
                                                ExtensionRepository.toggleExtension(addon.id, !addon.isEnabled)
                                            },
                                            cornerRadius = 8.dp
                                        ) {
                                            Text(
                                                text = if (addon.isEnabled) "Disable" else "Enable",
                                                color = t.colors.foreground,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }

                                        TvFocusable(
                                            onClick = { addonToRemove = addon },
                                            cornerRadius = 8.dp
                                        ) {
                                            Box(modifier = Modifier.padding(8.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TvSettingsSection.About -> {
                    Text("About", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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

                        Spacer(modifier = Modifier.height(16.dp))

                        TvFocusable(onClick = onPairingClick, cornerRadius = 8.dp) {
                            Text(
                                text = "Device Pairing",
                                color = t.colors.foreground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(t.colors.muted)
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // IPTV Type Select Dialog
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = m3uName,
                        onValueChange = { m3uName = it },
                        label = { Text("Name") },
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
                    onClick = {
                        val name = m3uName.trim()
                        val url = m3uUrl.trim()
                        if (name.isEmpty() || url.isEmpty()) {
                            m3uError = "Please fill in all fields"
                            return@Button
                        }
                        coroutineScope.launch {
                            val editProv = m3uEditProvider
                            if (editProv != null) {
                                IPTVRepository.deleteProvider(editProv.id)
                            }
                            try {
                                IPTVRepository.addM3uProvider(name, url)
                                showM3uDialog = false
                            } catch (e: Exception) {
                                m3uError = e.localizedMessage ?: "Failed to save provider"
                            }
                        }
                    },
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = xtreamName,
                        onValueChange = { xtreamName = it },
                        label = { Text("Name") },
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
                    onClick = {
                        val name = xtreamName.trim()
                        val server = xtreamServer.trim()
                        val user = xtreamUsername.trim()
                        val pass = xtreamPassword.trim()
                        if (name.isEmpty() || server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                            xtreamError = "Please fill in all fields"
                            return@Button
                        }
                        coroutineScope.launch {
                            val editProv = xtreamEditProvider
                            if (editProv != null) {
                                IPTVRepository.deleteProvider(editProv.id)
                            }
                            val res = IPTVRepository.addXtreamProvider(name, server, user, pass)
                            if (res.isSuccess) {
                                showXtreamDialog = false
                            } else {
                                xtreamError = res.exceptionOrNull()?.localizedMessage ?: "Failed to connect Xtream"
                            }
                        }
                    },
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
            coroutineScope.launch {
                try {
                    val result = ExtensionRepository.previewExtension(trimmedUrl)
                    isPreviewingAddon = false
                    if (result.isSuccess) {
                        previewManifest = result.manifest
                    } else {
                        addonValidationError = result.error?.message ?: result.warnings.firstOrNull() ?: "Failed to load manifest"
                    }
                } catch (e: Exception) {
                    isPreviewingAddon = false
                    addonValidationError = e.localizedMessage ?: "Failed to load manifest"
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showAddonDialog = false },
            title = { Text("Add Catalog Add-on URL", color = t.colors.foreground, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    val result = ExtensionRepository.confirmInstall(manifest, addonUrl.trim(), emptyList())
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
}

@Composable
fun TvLeftNavItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
    TvFocusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() },
        cornerRadius = 8.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) t.colors.brand.copy(alpha = 0.15f) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                color = if (isSelected) t.colors.brand else t.colors.foreground,
                fontSize = 16.sp,
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
        cornerRadius = 8.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(t.colors.card)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                if (description.isNotEmpty()) {
                    Text(description, fontSize = 12.sp, color = t.colors.mutedForeground, modifier = Modifier.padding(top = 2.dp))
                }
            }
            actionContent()
        }
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
            .background(t.colors.card, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(label, fontSize = 16.sp, color = t.colors.mutedForeground)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
    }
}

@Composable
fun TvSettingsRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Text(text = title, color = TvColors.TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = description, color = if (isFocused) TvColors.TextMain else TvColors.TextSub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text(text = "Open →", color = TvColors.BorderFocused, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}


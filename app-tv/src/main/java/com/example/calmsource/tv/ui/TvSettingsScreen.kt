package com.example.calmsource.tv.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.calmsource.core.playback.FallbackPreferences
import com.example.calmsource.feature.iptv.XtreamRepository
import com.example.calmsource.core.playback.FrameRateMatchingMode
import com.example.calmsource.core.playback.FrameRateMatchingPreferences
import com.example.calmsource.core.playback.StreamRacePreferences
import com.example.calmsource.core.playback.TunnelingMode
import com.example.calmsource.core.playback.TunnelingPreferences

sealed interface TvSettingsSubScreen {
    data object Root : TvSettingsSubScreen
    data object Iptv : TvSettingsSubScreen
    data object Extensions : TvSettingsSubScreen
    data object Debrid : TvSettingsSubScreen
    data object Priorities : TvSettingsSubScreen
    data object DiscoveryProviders : TvSettingsSubScreen
    data object Playback : TvSettingsSubScreen
    data object Debug : TvSettingsSubScreen
}

val TvSettingsSubScreenSaver = Saver<TvSettingsSubScreen, String>(
    save = { it::class.simpleName ?: "Root" },
    restore = { name ->
        when (name) {
            "Root" -> TvSettingsSubScreen.Root
            "Iptv" -> TvSettingsSubScreen.Iptv
            "Extensions" -> TvSettingsSubScreen.Extensions
            "Debrid" -> TvSettingsSubScreen.Debrid
            "Priorities" -> TvSettingsSubScreen.Priorities
            "DiscoveryProviders" -> TvSettingsSubScreen.DiscoveryProviders
            "Playback" -> TvSettingsSubScreen.Playback
            "Debug" -> TvSettingsSubScreen.Debug
            else -> TvSettingsSubScreen.Root
        }
    }
)

@Composable
fun TvSettingsScreens(
    onPairingClick: () -> Unit,
    onSwitchProfileClick: () -> Unit
) {
    var currentScreen by rememberSaveable(stateSaver = TvSettingsSubScreenSaver) {
        mutableStateOf<TvSettingsSubScreen>(TvSettingsSubScreen.Root)
    }

    val navigateTo: (TvSettingsSubScreen) -> Unit = remember {
        { target: TvSettingsSubScreen -> currentScreen = target }
    }

    androidx.activity.compose.BackHandler(enabled = currentScreen != TvSettingsSubScreen.Root) {
        currentScreen = TvSettingsSubScreen.Root
    }

    val focusRequesters = remember {
        mapOf(
            TvSettingsSubScreen.Iptv to FocusRequester(),
            TvSettingsSubScreen.Extensions to FocusRequester(),
            TvSettingsSubScreen.Debrid to FocusRequester(),
            TvSettingsSubScreen.Priorities to FocusRequester(),
            TvSettingsSubScreen.DiscoveryProviders to FocusRequester(),
            TvSettingsSubScreen.Playback to FocusRequester(),
            TvSettingsSubScreen.Debug to FocusRequester()
        )
    }

    var lastScreen by remember { mutableStateOf<TvSettingsSubScreen?>(null) }
    var initialRootFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(currentScreen) {
        if (currentScreen == TvSettingsSubScreen.Root) {
            kotlinx.coroutines.delay(150)
            val focusTarget = if (lastScreen != null) {
                val target = lastScreen!!
                lastScreen = null
                target
            } else if (!initialRootFocusDone) {
                initialRootFocusDone = true
                TvSettingsSubScreen.Iptv
            } else {
                null
            }
            if (focusTarget != null) {
                try {
                    focusRequesters[focusTarget]?.requestFocus()
                } catch (_: Exception) {
                    // Focus may fail before the list is attached.
                }
            }
        } else {
            lastScreen = currentScreen
        }
    }

    when (currentScreen) {
        TvSettingsSubScreen.Root -> TvSettingsRoot(
            onNavigate = navigateTo,
            focusRequesters = focusRequesters,
            onPairingClick = onPairingClick,
            onSwitchProfileClick = onSwitchProfileClick
        )
        TvSettingsSubScreen.Iptv -> TvIptvScreen(onBack = { navigateTo(TvSettingsSubScreen.Root) })
        TvSettingsSubScreen.Extensions -> TvExtensionsScreen(onBack = { navigateTo(TvSettingsSubScreen.Root) })
        TvSettingsSubScreen.Debrid -> TvDebridScreen(onBack = { navigateTo(TvSettingsSubScreen.Root) })
        TvSettingsSubScreen.Priorities -> TvPrioritiesScreen(onBack = { navigateTo(TvSettingsSubScreen.Root) })
        TvSettingsSubScreen.DiscoveryProviders -> TvDiscoveryProvidersScreen(onBack = { navigateTo(TvSettingsSubScreen.Root) })
        TvSettingsSubScreen.Playback -> TvPlaybackSettingsScreen(onBack = { navigateTo(TvSettingsSubScreen.Root) })
        TvSettingsSubScreen.Debug -> TvAdvancedDebugScreen(onBack = { navigateTo(TvSettingsSubScreen.Root) })
    }
}

@Composable
fun TvSettingsRoot(
    onNavigate: (TvSettingsSubScreen) -> Unit,
    focusRequesters: Map<TvSettingsSubScreen, FocusRequester>,
    onPairingClick: () -> Unit,
    onSwitchProfileClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "Settings", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain)
            Text(text = "System preferences and providers configuration", fontSize = 14.sp, color = TvColors.TextSub, modifier = Modifier.padding(bottom = 24.dp))
        }

        if (!XtreamRepository.isEncryptedStorageAvailable()) {
            item {
                androidx.compose.material3.Surface(
                    color = androidx.compose.ui.graphics.Color(0xFFE57373),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Warning: Secure Storage Unavailable",
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.Black,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Encrypted storage is unavailable. Your credentials will only be saved in-memory and will be lost on app exit.",
                            color = androidx.compose.ui.graphics.Color.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        item {
            TvSettingsRow(
                title = "IPTV Services & Playlists",
                description = "Edit custom playlist and EPG channels",
                onClick = { onNavigate(TvSettingsSubScreen.Iptv) },
                modifier = Modifier.focusRequester(focusRequesters[TvSettingsSubScreen.Iptv] ?: remember { FocusRequester() })
            )
        }
        item {
            TvSettingsRow(
                title = "Stremio Extensions",
                description = "Add Torrentio, AIOStreams, or any manifest URL",
                onClick = { onNavigate(TvSettingsSubScreen.Extensions) },
                modifier = Modifier.focusRequester(focusRequesters[TvSettingsSubScreen.Extensions] ?: remember { FocusRequester() })
            )
        }
        item {
            TvSettingsRow(
                title = "Debrid Accounts APIs",
                description = "Configure Real-Debrid and AllDebrid tokens",
                onClick = { onNavigate(TvSettingsSubScreen.Debrid) },
                modifier = Modifier.focusRequester(focusRequesters[TvSettingsSubScreen.Debrid] ?: remember { FocusRequester() })
            )
        }
        item {
            TvSettingsRow(
                title = "Device Pairing",
                description = "Pair with phone to transfer credentials",
                onClick = onPairingClick
            )
        }
        item {
            TvSettingsRow(
                title = "Switch Profile",
                description = "Change the active user profile",
                onClick = onSwitchProfileClick
            )
        }
        item {
            TvSettingsRow(
                title = "Source Priorities & Language Preferences",
                description = "Select audio priorities and resolution filters",
                onClick = { onNavigate(TvSettingsSubScreen.Priorities) },
                modifier = Modifier.focusRequester(focusRequesters[TvSettingsSubScreen.Priorities] ?: remember { FocusRequester() })
            )
        }
        item {
            TvSettingsRow(
                title = "Discovery Providers",
                description = "Manage enrichment, cache, privacy, and provider order",
                onClick = { onNavigate(TvSettingsSubScreen.DiscoveryProviders) },
                modifier = Modifier.focusRequester(focusRequesters[TvSettingsSubScreen.DiscoveryProviders] ?: remember { FocusRequester() })
            )
        }
        item {
            TvSettingsRow(
                title = "Playback",
                description = "Configure display and playback compatibility",
                onClick = { onNavigate(TvSettingsSubScreen.Playback) },
                modifier = Modifier.focusRequester(focusRequesters[TvSettingsSubScreen.Playback] ?: remember { FocusRequester() })
            )
        }
        item {
            TvSettingsRow(
                title = "Advanced Debug",
                description = "Inspect sanitized runtime diagnostics",
                onClick = { onNavigate(TvSettingsSubScreen.Debug) },
                modifier = Modifier.focusRequester(focusRequesters[TvSettingsSubScreen.Debug] ?: remember { FocusRequester() })
            )
        }
    }
}

@Composable
fun TvPlaybackSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var frameRateMode by remember { mutableStateOf(FrameRateMatchingPreferences.mode) }
    var safeDecoderRetry by remember { mutableStateOf(FallbackPreferences.enableFallbackSafeProfileOnDecoderError) }
    var streamRacing by remember { mutableStateOf(StreamRacePreferences.enableStreamRacing) }
    var tunnelingMode by remember { mutableStateOf(TunnelingPreferences.mode) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Playback",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = TvColors.TextMain
            )
            Text(
                text = "Display and playback compatibility",
                fontSize = 14.sp,
                color = TvColors.TextSub,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
        item {
            TvSettingsRow(
                title = "Match content frame rate",
                description = if (frameRateMode == FrameRateMatchingMode.SEAMLESS_ONLY) {
                    "On: seamless refresh-rate changes"
                } else {
                    "Off"
                },
                onClick = {
                    frameRateMode = if (frameRateMode == FrameRateMatchingMode.OFF) {
                        FrameRateMatchingMode.SEAMLESS_ONLY
                    } else {
                        FrameRateMatchingMode.OFF
                    }
                    FrameRateMatchingPreferences.setModeBestEffort(context, frameRateMode)
                }
            )
        }
        item {
            TvSettingsRow(
                title = "Safe decoder retry",
                description = if (safeDecoderRetry) "Enabled: retry safe profile on decoder error" else "Disabled",
                onClick = {
                    val next = !safeDecoderRetry
                    safeDecoderRetry = next
                    FallbackPreferences.setDecoderFallbackAndPersist(context, next)
                }
            )
        }
        item {
            TvSettingsRow(
                title = "Race top streams on Play Best",
                description = if (streamRacing) {
                    "On: probe top candidates before selecting"
                } else {
                    "Off"
                },
                onClick = {
                    streamRacing = !streamRacing
                    StreamRacePreferences.setEnabledBestEffort(context, streamRacing)
                }
            )
        }
        item {
            TvSettingsRow(
                title = "Tunneling Mode",
                description = when (tunnelingMode) {
                    TunnelingMode.OFF -> "Off"
                    TunnelingMode.AUTO -> "Auto: enable when device and codecs allow"
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
            )
        }
        item {
            TvSettingsRow(
                title = "Back",
                description = "Return to settings",
                onClick = onBack
            )
        }
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

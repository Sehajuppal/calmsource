package com.example.calmsource.tv

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.toMediaItem
import com.example.calmsource.core.model.CalmSourceDeepLink
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.tv.ui.TvColors
import com.example.calmsource.tv.ui.TvDetailsScreen
import com.example.calmsource.tv.ui.TvFocusCard
import com.example.calmsource.tv.ui.TvHomeScreen
import com.example.calmsource.tv.ui.TvLibraryScreen
import com.example.calmsource.tv.ui.TvLiveGuideScreen
import com.example.calmsource.tv.ui.TvPlayerScreen
import com.example.calmsource.tv.ui.TvSearchScreen
import com.example.calmsource.tv.ui.TvSettingsScreens
import com.example.calmsource.tv.ui.TvBootDestination
import com.example.calmsource.tv.ui.TvBootViewModel
import com.example.calmsource.tv.ui.TvOnboardingScreen
import com.example.calmsource.tv.ui.TvProfileSelectionScreen
import dagger.hilt.android.AndroidEntryPoint
import com.example.calmsource.core.model.ProviderSyncStatus
import com.example.calmsource.core.model.userLabel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color

sealed interface TvScreen {
    data object ProfileSelection : TvScreen
    data object Home : TvScreen
    data object Library : TvScreen
    data object Search : TvScreen
    data object LiveGuide : TvScreen
    data object Settings : TvScreen
    data object Onboarding : TvScreen
    data class Details(
        val mediaItem: MediaItem,
        val startPositionMs: Long = 0L
    ) : TvScreen
    data class Player(
        val request: PlaybackRequest,
        val fallbackSources: List<PlaybackSource> = emptyList(),
        val playBestIntent: Boolean = false,
        val parentScreen: TvScreen? = null
    ) : TvScreen
    data class Resume(
        val deepLink: String,
        val parentScreen: TvScreen
    ) : TvScreen
}

private fun saveTvScreen(screen: TvScreen): Bundle {
    return Bundle().apply {
        when (screen) {
            is TvScreen.ProfileSelection -> putString("type", "ProfileSelection")
            is TvScreen.Home -> putString("type", "Home")
            is TvScreen.LiveGuide -> putString("type", "LiveGuide")
            is TvScreen.Library -> putString("type", "Library")
            is TvScreen.Search -> putString("type", "Search")
            is TvScreen.Settings -> putString("type", "Settings")
            is TvScreen.Onboarding -> putString("type", "Onboarding")
            is TvScreen.Details -> {
                putString("type", "Details")
                putString("media_item_json", kotlinx.serialization.json.Json.encodeToString(com.example.calmsource.core.model.MediaItem.serializer(), screen.mediaItem))
                putLong("start_position_ms", screen.startPositionMs)
            }
            is TvScreen.Player -> {
                putString("type", "Player")
                if (screen.parentScreen != null) {
                    putBundle("parent_screen", saveTvScreen(screen.parentScreen))
                }
                // Persist a privacy-safe resume reference (never the resolved URL/credentials):
                // a deep link that the navigation layer re-resolves on restore. Live channels
                // rebuild directly into the player; VOD resumes via the details screen.
                resumeDeepLinkFor(screen.request)?.let { putString("resume_deeplink", it) }
            }
            is TvScreen.Resume -> {
                putString("type", "Player")
                putString("resume_deeplink", screen.deepLink)
                putBundle("parent_screen", saveTvScreen(screen.parentScreen))
            }
        }
    }
}

private fun resumeDeepLinkFor(request: PlaybackRequest): String? {
    val source = request.source
    return if (source.metadata?.isLive == true) {
        CalmSourceDeepLink.channelUri(source.id)
    } else {
        request.userMemoryReference?.let { reference ->
            CalmSourceDeepLink.detailsUri(reference, request.startPositionMs)
        }
    }
}

private fun restoreTvScreen(bundle: Bundle): TvScreen? {
    return when (val type = bundle.getString("type")) {
        "ProfileSelection" -> TvScreen.ProfileSelection
        "Home" -> TvScreen.Home
        "LiveGuide" -> TvScreen.LiveGuide
        "Library" -> TvScreen.Library
        "Search" -> TvScreen.Search
        "Settings" -> TvScreen.Settings
        "Onboarding" -> TvScreen.Onboarding
        "Details" -> {
            val json = bundle.getString("media_item_json")
            val mediaItem = json?.let { kotlinx.serialization.json.Json.decodeFromString(com.example.calmsource.core.model.MediaItem.serializer(), it) }
            if (mediaItem != null) {
                TvScreen.Details(mediaItem, bundle.getLong("start_position_ms"))
            } else {
                TvScreen.Home
            }
        }
        "Player" -> {
            val resumeDeepLink = bundle.getString("resume_deeplink")
            val parentBundle = bundle.getBundle("parent_screen")
            val parentScreen = if (parentBundle != null) {
                restoreTvScreen(parentBundle) ?: TvScreen.Home
            } else {
                TvScreen.Home
            }
            if (resumeDeepLink != null) {
                TvScreen.Resume(resumeDeepLink, parentScreen)
            } else {
                parentScreen
            }
        }
        else -> null
    }
}

val TvScreenSaver = Saver<TvScreen, Bundle>(
    save = { screen -> saveTvScreen(screen) },
    restore = { bundle -> restoreTvScreen(bundle) }
)

@AndroidEntryPoint
class TvMainActivity : ComponentActivity() {
    private val _pendingDeepLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            _pendingDeepLink.value = intent?.dataString
        }
        setContent {
            val pendingDeepLink by _pendingDeepLink.collectAsState()
            val appContext = LocalContext.current.applicationContext
            LaunchedEffect(Unit) {
                val isDebuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                if (isDebuggable) {
                    DebugAutoSetup.runIfNeeded(appContext)
                }
            }

            val bootViewModel: TvBootViewModel = hiltViewModel()
            val bootGate by bootViewModel.gateState.collectAsState()

            var isBootSettled by remember { mutableStateOf(false) }
            var isBootRouted by rememberSaveable { mutableStateOf(savedInstanceState != null) }

            LaunchedEffect(bootGate.destination) {
                if (bootGate.destination == TvBootDestination.Loading) {
                    isBootSettled = false
                } else if (!isBootSettled) {
                    kotlinx.coroutines.delay(150)
                    isBootSettled = true
                }
            }

            var activeTab by rememberSaveable { mutableStateOf(0) }
            val tabFocusRequesters = remember { List(5) { FocusRequester() } }
            var currentScreen by rememberSaveable(stateSaver = TvScreenSaver) {
                mutableStateOf<TvScreen>(TvScreen.Onboarding)
            }

            LaunchedEffect(isBootSettled, bootGate.destination, isBootRouted) {
                if (!isBootSettled || isBootRouted) return@LaunchedEffect
                bootViewModel.bootScreenForFirstRoute()?.let { route ->
                    currentScreen = route
                    isBootRouted = true
                }
            }

            LaunchedEffect(isBootSettled, bootGate.destination, currentScreen) {
                if (!isBootSettled) return@LaunchedEffect

                val redirected = bootViewModel.redirectScreenIfBlocked(currentScreen)
                if (redirected != currentScreen) {
                    currentScreen = redirected
                    if (bootGate.destination == TvBootDestination.Onboarding) {
                        isBootRouted = false
                    }
                }
            }

            var searchSeed by remember { mutableStateOf("") }
            val tvNavigationScope = rememberCoroutineScope()

            fun navigateToChannel(channelId: String, parent: TvScreen) {
                tvNavigationScope.launch {
                    val channel = IPTVRepository.findChannelForPlayback(channelId)
                    if (channel != null) {
                        currentScreen = TvScreen.Player(
                            IPTVRepository.buildLivePlaybackRequest(channel),
                            parentScreen = parent
                        )
                    } else {
                        android.widget.Toast.makeText(
                            this@TvMainActivity,
                            "Channel unavailable — its provider may be disabled. Try re-syncing.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            LaunchedEffect(currentScreen) {
                val screen = currentScreen
                if (screen is TvScreen.Resume) {
                    _pendingDeepLink.value = screen.deepLink
                    currentScreen = screen.parentScreen
                }
            }

            fun tabScreen(): TvScreen = when (activeTab) {
                0 -> TvScreen.Home
                1 -> TvScreen.LiveGuide
                2 -> TvScreen.Library
                3 -> TvScreen.Search
                else -> TvScreen.Settings
            }

            LaunchedEffect(pendingDeepLink, isBootSettled, bootGate.destination) {
                val link = pendingDeepLink ?: return@LaunchedEffect
                if (!isBootSettled) return@LaunchedEffect
                if (bootGate.destination != TvBootDestination.Home) return@LaunchedEffect

                when (val route = CalmSourceDeepLink.parse(link)) {
                    is CalmSourceDeepLink.Details -> {
                        currentScreen = TvScreen.Details(route.mediaItem, route.startPositionMs)
                    }
                    is CalmSourceDeepLink.Channel -> {
                        val channel = IPTVRepository.findChannelForPlayback(route.channelId)
                        if (channel != null) {
                            currentScreen = TvScreen.Player(
                                request = IPTVRepository.buildLivePlaybackRequest(channel),
                                parentScreen = TvScreen.Home
                            )
                        } else {
                            android.widget.Toast.makeText(this@TvMainActivity, "Channel not found", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    is CalmSourceDeepLink.Search -> {
                        searchSeed = route.query
                        activeTab = 3
                        currentScreen = TvScreen.Search
                    }
                    null -> Unit
                }
                _pendingDeepLink.value = null
            }

            BackHandler(
                enabled = currentScreen !is TvScreen.Home &&
                    currentScreen !is TvScreen.ProfileSelection &&
                    currentScreen !is TvScreen.Onboarding &&
                    currentScreen !is TvScreen.Settings &&
                    currentScreen !is TvScreen.Player
            ) {
                currentScreen = when (val screen = currentScreen) {
                    is TvScreen.Details -> tabScreen()
                    is TvScreen.Player -> screen.parentScreen ?: tabScreen()
                    is TvScreen.Resume -> screen.parentScreen
                    else -> {
                        activeTab = 0
                        TvScreen.Home
                    }
                }
            }

            val syncStates by IPTVRepository.syncStates.collectAsState()
            val activeSync = syncStates.values.firstOrNull { it.status == ProviderSyncStatus.SYNCING }
            val xtreamProgress by IPTVRepository.xtreamSyncProgress.collectAsState()
            val liveGuideIndex by IPTVRepository.liveGuideIndex.collectAsState()
            val liveChannelCount = liveGuideIndex.liveChannels.size
            var syncOverlayDismissed by remember { mutableStateOf(false) }
            LaunchedEffect(activeSync?.providerId) {
                syncOverlayDismissed = false
            }
            val showSyncOverlay = activeSync != null &&
                liveChannelCount == 0 &&
                !syncOverlayDismissed &&
                currentScreen !is TvScreen.Settings
            val showSyncBanner = activeSync != null && liveChannelCount > 0 && !syncOverlayDismissed
            val syncStageLabel = xtreamProgress?.stage?.userLabel()
                ?: "Syncing IPTV catalog…"

            BackHandler(enabled = showSyncOverlay) {
                syncOverlayDismissed = true
            }

            val topLevel = currentScreen is TvScreen.Home ||
                currentScreen is TvScreen.Library ||
                currentScreen is TvScreen.Search ||
                currentScreen is TvScreen.LiveGuide ||
                currentScreen is TvScreen.Settings

            if (!isBootSettled || !isBootRouted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TvColors.Background),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Color(0xFF22C55E)
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TvColors.Background)
                        .then(
                            if (showSyncOverlay) {
                                Modifier.focusProperties { canFocus = false }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (topLevel) {
                        Column(
                            modifier = Modifier
                                .width(110.dp)
                                .fillMaxHeight()
                                .background(TvColors.Surface)
                                .padding(vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "CS",
                                color = TvColors.BorderFocused,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TvNavRailItem("Home", activeTab == 0, modifier = Modifier.focusRequester(tabFocusRequesters[0])) {
                                activeTab = 0
                                currentScreen = TvScreen.Home
                            }
                            TvNavRailItem("Live", activeTab == 1, modifier = Modifier.focusRequester(tabFocusRequesters[1])) {
                                activeTab = 1
                                currentScreen = TvScreen.LiveGuide
                            }
                            TvNavRailItem("Library", activeTab == 2, modifier = Modifier.focusRequester(tabFocusRequesters[2])) {
                                activeTab = 2
                                currentScreen = TvScreen.Library
                            }
                            TvNavRailItem("Search", activeTab == 3, modifier = Modifier.focusRequester(tabFocusRequesters[3])) {
                                activeTab = 3
                                currentScreen = TvScreen.Search
                            }
                            TvNavRailItem("Setup", activeTab == 4, modifier = Modifier.focusRequester(tabFocusRequesters[4])) {
                                activeTab = 4
                                currentScreen = TvScreen.Settings
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusProperties {
                                left = tabFocusRequesters[activeTab]
                            }
                    ) {
                        when (val screen = currentScreen) {
                            TvScreen.ProfileSelection -> TvProfileSelectionScreen(
                                onProfileSelected = {
                                    activeTab = 0
                                    currentScreen = TvScreen.Home
                                },
                                onOpenSetup = {
                                    activeTab = 4
                                    currentScreen = TvScreen.Settings
                                }
                            )
                            TvScreen.Home -> TvHomeScreen(
                                onMediaClick = { currentScreen = TvScreen.Details(it) },
                                onChannelClick = { channelId -> navigateToChannel(channelId, TvScreen.Home) }
                            )
                            TvScreen.Library -> TvLibraryScreen(
                                onOpenMedia = { reference, progress ->
                                    currentScreen = TvScreen.Details(reference.toMediaItem(), progress)
                                },
                                onOpenChannel = { reference ->
                                    reference.sourceId?.let { navigateToChannel(it, TvScreen.Library) }
                                        ?: android.widget.Toast.makeText(
                                            this@TvMainActivity,
                                            "Channel unavailable — its provider may be disabled. Try re-syncing.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                },
                                onSearch = { query ->
                                    searchSeed = query
                                    activeTab = 3
                                    currentScreen = TvScreen.Search
                                }
                            )
                            TvScreen.Search -> TvSearchScreen(
                                initialQuery = searchSeed,
                                onInitialQueryConsumed = { searchSeed = "" },
                                onMediaClick = { currentScreen = TvScreen.Details(it) },
                                onChannelClick = { channelId -> navigateToChannel(channelId, TvScreen.Search) }
                            )
                            TvScreen.LiveGuide -> TvLiveGuideScreen(
                                onChannelSelect = { channel, program ->
                                    tvNavigationScope.launch {
                                        val iptvChannel = IPTVRepository.findChannelForPlayback(channel.id)
                                        if (iptvChannel != null) {
                                            currentScreen = TvScreen.Player(
                                                IPTVRepository.buildLivePlaybackRequest(
                                                    channel = iptvChannel,
                                                    programTitle = program?.title,
                                                    programDescription = program?.description,
                                                    programDurationMs = program?.let { it.endTimeMs - it.startTimeMs }
                                                ),
                                                parentScreen = TvScreen.LiveGuide
                                            )
                                        } else {
                                            android.widget.Toast.makeText(
                                                this@TvMainActivity,
                                                "Channel unavailable — its provider may be disabled. Try re-syncing.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onOpenSetup = {
                                    activeTab = 4
                                    currentScreen = TvScreen.Settings
                                }
                            )
                            TvScreen.Settings -> TvSettingsScreens(
                                onPairingClick = { currentScreen = TvScreen.Onboarding },
                                onSwitchProfileClick = {
                                    activeTab = 0
                                    currentScreen = TvScreen.ProfileSelection
                                }
                            )
                            TvScreen.Onboarding -> TvOnboardingScreen(
                                onComplete = {
                                    currentScreen = TvScreen.ProfileSelection
                                }
                            )
                            is TvScreen.Details -> TvDetailsScreen(
                                mediaItem = screen.mediaItem,
                                startPositionMs = screen.startPositionMs,
                                onBack = { currentScreen = tabScreen() },
                                onPlayOption = { request, candidates, playBestIntent ->
                                    currentScreen = TvScreen.Player(request, candidates, playBestIntent, tabScreen())
                                },
                                onOpenMedia = { currentScreen = TvScreen.Details(it) }
                            )
                            is TvScreen.Player -> TvPlayerScreen(
                                request = screen.request,
                                fallbackSources = screen.fallbackSources,
                                playBestIntent = screen.playBestIntent,
                                onBack = { currentScreen = screen.parentScreen ?: tabScreen() }
                            )
                            is TvScreen.Resume -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        color = Color(0xFF22C55E)
                                    )
                                }
                            }
                        }
                    }
                }

                if (showSyncOverlay) {
                    val syncing = activeSync!!
                    val buttonFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(100)
                        try {
                            buttonFocusRequester.requestFocus()
                        } catch (_: Exception) {
                            // Focus requesting might fail if node is not yet attached/focusable
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusable()
                            .background(Color.Black.copy(alpha = 0.9f))
                            .pointerInput(Unit) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color(0xFF22C55E)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = syncStageLabel,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Progress: ${syncing.progressPercent}%",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sync continues in the background. Press Back to browse.",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            TvFocusCard(
                                modifier = Modifier.focusRequester(buttonFocusRequester),
                                onClick = { syncOverlayDismissed = true }
                            ) { isFocused ->
                                Text(
                                    text = "Browse now",
                                    color = if (isFocused) TvColors.Background else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }

                if (showSyncBanner) {
                    val syncing = activeSync!!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111827).copy(alpha = 0.92f))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = syncStageLabel,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Progress: ${syncing.progressPercent}% — Live channels are ready to browse",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }
                            TvFocusCard(onClick = { syncOverlayDismissed = true }) { isFocused ->
                                Text(
                                    text = "Dismiss",
                                    color = if (isFocused) TvColors.Background else Color(0xFF22C55E),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            }
        }


    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _pendingDeepLink.value = intent.dataString
    }
}

@Composable
fun TvNavRailItem(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TvFocusCard(onClick = onClick, modifier = modifier.width(88.dp)) { isFocused ->
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected || isFocused) TvColors.BorderFocused else TvColors.TextSub,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

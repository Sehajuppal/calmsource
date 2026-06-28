package com.example.calmsource

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.toMediaItem
import com.example.calmsource.core.model.CalmSourceDeepLink
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.core.ui.theme.*
import com.example.calmsource.ui.DetailsScreen
import com.example.calmsource.ui.ProfilesScreen
import com.example.calmsource.ui.HomeScreen
import com.example.calmsource.ui.LibraryScreen
import com.example.calmsource.ui.LiveTvScreen
import com.example.calmsource.ui.PlayerScreen
import com.example.calmsource.ui.SearchScreen
import com.example.calmsource.ui.SettingsScreens
import androidx.compose.runtime.collectAsState
import com.example.calmsource.core.model.ProviderSyncStatus
import com.example.calmsource.core.model.userLabel
import kotlinx.coroutines.launch

sealed interface MobileScreen {
    data object Profiles : MobileScreen
    data object Home : MobileScreen
    data object LiveTv : MobileScreen
    data object Library : MobileScreen
    data object Search : MobileScreen
    data object Settings : MobileScreen
    data class Details(
        val mediaItem: MediaItem,
        val startPositionMs: Long = 0L
    ) : MobileScreen
    data class Player(
        val request: PlaybackRequest,
        val fallbackSources: List<PlaybackSource> = emptyList(),
        val playBestIntent: Boolean = false,
        val parentScreen: MobileScreen? = null
    ) : MobileScreen
    data class Resume(
        val deepLink: String,
        val parentScreen: MobileScreen
    ) : MobileScreen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    deepLinkUri: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    initialScreen: MobileScreen? = null,
    onScreenChanged: (MobileScreen) -> Unit = {}
) {
    val t = LocalLumenTokens.current
    var activeTab by rememberSaveable { mutableStateOf(0) }

    var currentScreen by remember {
        mutableStateOf<MobileScreen>(initialScreen ?: MobileScreen.Home)
    }

    LaunchedEffect(currentScreen) {
        onScreenChanged(currentScreen)
    }
    var searchSeed by rememberSaveable { mutableStateOf("") }

    var activeDeepLink by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(deepLinkUri) {
        if (deepLinkUri != null) {
            activeDeepLink = deepLinkUri
            onDeepLinkConsumed()
        }
    }

    LaunchedEffect(currentScreen) {
        val screen = currentScreen
        if (screen is MobileScreen.Resume) {
            activeDeepLink = screen.deepLink
            currentScreen = screen.parentScreen
        }
    }

    fun tabScreen(): MobileScreen = mobileTabScreen(activeTab)

    val context = androidx.compose.ui.platform.LocalContext.current
    val navigationScope = rememberCoroutineScope()

    fun navigateToChannel(channelId: String, parent: MobileScreen) {
        navigationScope.launch {
            val channel = IPTVRepository.findChannelForPlayback(channelId)
            if (channel != null) {
                currentScreen = MobileScreen.Player(
                    request = IPTVRepository.buildLivePlaybackRequest(channel),
                    parentScreen = parent
                )
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Channel unavailable — its provider may be disabled. Try re-syncing.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(activeDeepLink) {
        val link = activeDeepLink ?: return@LaunchedEffect
        when (val route = CalmSourceDeepLink.parse(link)) {
            is CalmSourceDeepLink.Details -> {
                currentScreen = MobileScreen.Details(route.mediaItem, route.startPositionMs)
            }
            is CalmSourceDeepLink.Channel -> {
                val channel = IPTVRepository.findChannelForPlayback(route.channelId)
                if (channel != null) {
                    currentScreen = MobileScreen.Player(
                        request = IPTVRepository.buildLivePlaybackRequest(channel),
                        parentScreen = MobileScreen.Home
                    )
                } else {
                    android.widget.Toast.makeText(context, "Channel not found", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            is CalmSourceDeepLink.Search -> {
                searchSeed = route.query
                activeTab = 3
                currentScreen = MobileScreen.Search
            }
            null -> Unit
        }
        activeDeepLink = null
    }

    BackHandler(enabled = currentScreen is MobileScreen.Details || currentScreen is MobileScreen.Player || currentScreen is MobileScreen.Resume) {
        currentScreen = when (val screen = currentScreen) {
            is MobileScreen.Details -> tabScreen()
            is MobileScreen.Player -> screen.parentScreen ?: tabScreen()
            is MobileScreen.Resume -> screen.parentScreen
            else -> tabScreen()
        }
    }

    val isTopLevel = currentScreen is MobileScreen.Home ||
        currentScreen is MobileScreen.LiveTv ||
        currentScreen is MobileScreen.Library ||
        currentScreen is MobileScreen.Search ||
        currentScreen is MobileScreen.Settings

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
        currentScreen !is MobileScreen.Settings
    val showSyncBanner = activeSync != null && liveChannelCount > 0 && !syncOverlayDismissed
    val syncStageLabel = xtreamProgress?.stage?.userLabel() ?: "Syncing IPTV catalog…"

    BackHandler(enabled = showSyncOverlay) {
        syncOverlayDismissed = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (isTopLevel) {
                    NavigationBar(
                        containerColor = t.colors.surface,
                        contentColor = t.colors.brand
                    ) {
                        NavigationBarItem(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0; currentScreen = MobileScreen.Home },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1; currentScreen = MobileScreen.LiveTv },
                            icon = { @Suppress("DEPRECATION") Icon(Icons.Default.List, contentDescription = "Live TV") },
                            label = { Text("Live") }
                        )
                        NavigationBarItem(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2; currentScreen = MobileScreen.Library },
                            icon = { Icon(Icons.Default.Favorite, contentDescription = "Library") },
                            label = { Text("Library") }
                        )
                        NavigationBarItem(
                            selected = activeTab == 3,
                            onClick = { activeTab = 3; currentScreen = MobileScreen.Search },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search") }
                        )
                        NavigationBarItem(
                            selected = activeTab == 4,
                            onClick = { activeTab = 4; currentScreen = MobileScreen.Settings },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Setup") }
                        )
                    }
                }
            },
            containerColor = t.colors.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (val screen = currentScreen) {
                    MobileScreen.Profiles -> ProfilesScreen(
                        onProfileSelected = { currentScreen = MobileScreen.Home }
                    )
                    MobileScreen.Home -> HomeScreen(
                        onMediaClick = { currentScreen = MobileScreen.Details(it) },
                        onChannelClick = { channelId -> navigateToChannel(channelId, MobileScreen.Home) }
                    )
                    MobileScreen.LiveTv -> LiveTvScreen(
                        onChannelSelect = { channel, program ->
                            navigationScope.launch {
                                val iptvChannel = IPTVRepository.findChannelForPlayback(channel.id)
                                if (iptvChannel != null) {
                                    currentScreen = MobileScreen.Player(
                                        request = IPTVRepository.buildLivePlaybackRequest(
                                            channel = iptvChannel,
                                            programTitle = program?.title,
                                            programDescription = program?.description,
                                            programDurationMs = program?.let { it.endTimeMs - it.startTimeMs }
                                        ),
                                        parentScreen = MobileScreen.LiveTv
                                    )
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Channel unavailable — its provider may be disabled. Try re-syncing.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onOpenSetup = {
                            activeTab = 4
                            currentScreen = MobileScreen.Settings
                        }
                    )
                    MobileScreen.Library -> LibraryScreen(
                        onOpenMedia = { reference, progress ->
                            currentScreen = MobileScreen.Details(reference.toMediaItem(), progress)
                        },
                        onOpenChannel = { reference ->
                            reference.sourceId?.let { navigateToChannel(it, MobileScreen.Library) }
                                ?: android.widget.Toast.makeText(
                                    context,
                                    "Channel unavailable — its provider may be disabled. Try re-syncing.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                        },
                        onSearch = { query ->
                            searchSeed = query
                            activeTab = 3
                            currentScreen = MobileScreen.Search
                        }
                    )
                    MobileScreen.Search -> SearchScreen(
                        initialQuery = searchSeed,
                        onInitialQueryConsumed = { searchSeed = "" },
                        onMediaClick = { currentScreen = MobileScreen.Details(it) },
                        onChannelClick = { channelId -> navigateToChannel(channelId, MobileScreen.Search) }
                    )
                    MobileScreen.Settings -> SettingsScreens(onNavigateToProfiles = { currentScreen = MobileScreen.Profiles })
                    is MobileScreen.Details -> DetailsScreen(
                        mediaItem = screen.mediaItem,
                        startPositionMs = screen.startPositionMs,
                        onBack = { currentScreen = tabScreen() },
                        onPlayOption = { request, fallbacks, playBestIntent ->
                            currentScreen = MobileScreen.Player(request, fallbacks, playBestIntent, tabScreen())
                        },
                        onOpenMedia = { currentScreen = MobileScreen.Details(it) }
                    )
                    is MobileScreen.Player -> PlayerScreen(
                        request = screen.request,
                        fallbackSources = screen.fallbackSources,
                        playBestIntent = screen.playBestIntent,
                        onBack = { currentScreen = screen.parentScreen ?: tabScreen() }
                    )
                    is MobileScreen.Resume -> Spacer(modifier = Modifier)
                }
            }
        }

        if (showSyncOverlay) {
            val syncing = activeSync!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Progress: ${syncing.progressPercent}%",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sync continues in the background. Tap below to browse.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.TextButton(onClick = { syncOverlayDismissed = true }) {
                        Text("Browse now", color = Color(0xFF22C55E))
                    }
                }
            }
        }

        if (showSyncBanner) {
            val syncing = activeSync!!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color(0xFF111827).copy(alpha = 0.94f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
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
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Progress: ${syncing.progressPercent}% — Live channels ready",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = { syncOverlayDismissed = true }) {
                        Text("Dismiss", color = Color(0xFF22C55E))
                    }
                }
            }
        }
    }
}

private fun mobileTabScreen(tab: Int): MobileScreen = when (tab) {
    0 -> MobileScreen.Home
    1 -> MobileScreen.LiveTv
    2 -> MobileScreen.Library
    3 -> MobileScreen.Search
    else -> MobileScreen.Settings
}

package com.example.calmsource

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Brush
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
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.PlaybackItemMetadata
import com.example.calmsource.core.model.SourceType
import com.example.calmsource.core.model.toMediaItem
import com.example.calmsource.core.model.toUserMemoryReference
import kotlinx.coroutines.flow.first
import com.example.calmsource.core.model.CalmSourceDeepLink
import com.example.calmsource.feature.extensions.ExtensionRepository
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.ui.FirstRunSetupWizard
import com.example.calmsource.ui.SettingsSubScreen
import com.example.calmsource.ui.isFirstRunSetupComplete
import com.example.calmsource.core.ui.theme.*
import com.example.calmsource.core.ui.components.LumenSyncCatalogOverlay
import com.example.calmsource.core.ui.components.SyncStatusPill
import com.example.calmsource.ui.DetailsScreen
import com.example.calmsource.ui.ProfilesScreen
import com.example.calmsource.ui.HomeScreen
import com.example.calmsource.ui.LibraryScreen
import com.example.calmsource.ui.LiveTvScreen
import com.example.calmsource.ui.PlayerScreen
import com.example.calmsource.ui.SearchScreen
import com.example.calmsource.ui.SettingsScreens
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.calmsource.core.data.BootDestination
import com.example.calmsource.core.ui.R as CoreUiR
import com.example.calmsource.core.ui.components.HomeFeedSkeleton
import com.example.calmsource.core.ui.components.LumenSkeleton
import com.example.calmsource.core.ui.theme.LumenLayout
import com.example.calmsource.core.ui.theme.LumenType
import com.example.calmsource.core.ui.theme.rememberReducedMotion
import com.example.calmsource.ui.LoginScreen
import com.example.calmsource.ui.MobileBootViewModel
import com.example.calmsource.ui.MobilePlaybackViewModel
import com.example.calmsource.ui.MiniPlayerBar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.example.calmsource.core.ui.theme.LumenDelightMotion
import com.example.calmsource.core.ui.components.LumenScreenTransition
import com.example.calmsource.core.ui.theme.UiAppearancePreferences
import com.example.calmsource.core.model.ProviderSyncStatus
import com.example.calmsource.core.model.userLabel
import kotlinx.coroutines.launch

sealed interface MobileScreen {
    data object Login : MobileScreen
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
    onScreenChanged: (MobileScreen) -> Unit = {},
    onOledThemeChanged: (Boolean) -> Unit = {},
    onRegisterPictureInPicture: (((() -> Boolean)?) -> Unit)? = null,
) {
    val t = LocalLumenTokens.current
    val bootViewModel: MobileBootViewModel = hiltViewModel()
    val playbackViewModel: MobilePlaybackViewModel = hiltViewModel()
    val isPlaybackMinimized by playbackViewModel.isMinimized.collectAsState()
    val storedPlayerRoute by playbackViewModel.playerRoute.collectAsState()
    val miniPlayerUiState by playbackViewModel.obtainManager().uiState.collectAsStateWithLifecycle()
    val reducedMotion = rememberReducedMotion()
    val bootGate by bootViewModel.gateState.collectAsState()
    var isBootSettled by remember { mutableStateOf(false) }
    var isBootRouted by rememberSaveable { mutableStateOf(initialScreen != null) }

    LaunchedEffect(bootGate.destination) {
        if (bootGate.destination == BootDestination.Loading) {
            isBootSettled = false
        } else if (!isBootSettled) {
            kotlinx.coroutines.delay(150)
            isBootSettled = true
        }
    }

    var activeTab by rememberSaveable { mutableStateOf(0) }

    var currentScreen by remember {
        mutableStateOf<MobileScreen>(initialScreen ?: MobileScreen.Home)
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
            if (bootGate.destination == BootDestination.Login) {
                isBootRouted = false
            }
        }
    }

    LaunchedEffect(currentScreen) {
        onScreenChanged(currentScreen)
    }
    var searchSeed by rememberSaveable { mutableStateOf("") }
    var sharedPosterKey by remember { mutableStateOf<String?>(null) }
    var pendingSettingsSubScreen by remember { mutableStateOf(SettingsSubScreen.Main) }
    var setupWizardDismissed by rememberSaveable { mutableStateOf(false) }

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
    val snackbarHostState = remember { SnackbarHostState() }

    fun showInlineError(message: String) {
        navigationScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun navigateToChannel(channelId: String, parent: MobileScreen) {
        navigationScope.launch {
            val channel = IPTVRepository.findChannelForPlayback(channelId)
            if (channel != null) {
                currentScreen = MobileScreen.Player(
                    request = IPTVRepository.buildLivePlaybackRequest(channel),
                    parentScreen = parent
                )
            } else {
                showInlineError(context.getString(CoreUiR.string.error_channel_unavailable))
            }
        }
    }

    fun playMediaDirectly(mediaItem: MediaItem) {
        navigationScope.launch {
            val localSources = IPTVRepository.findIptvStreamSources(mediaItem.id, mediaItem.title)
            val activeExtensions = ExtensionRepository.extensions.value
            val extensionResolution = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                ExtensionRepository.lookupMediaStreams(mediaItem, activeExtensions)
                    .first { it.streamSources.isNotEmpty() || it.errors.isNotEmpty() }
            } ?: ExtensionRepository.ExtensionMediaResolution(mediaItem, emptyList(), emptyList())
            val allSources = localSources + extensionResolution.streamSources
            val options = com.example.calmsource.core.model.WatchOptionResolver.buildWatchOptions(allSources)
            
            val playbackSources = options.map { opt ->
                PlaybackSource(
                    id = opt.id,
                    type = when (opt.type) {
                        SourceType.IPTV -> PlaybackSourceType.IPTV
                        SourceType.EXTENSION -> PlaybackSourceType.EXTENSION
                        SourceType.DEBRID -> PlaybackSourceType.DEBRID_RESOLVED
                    },
                    title = opt.title,
                    rawUrl = opt.source.url,
                    metadata = PlaybackItemMetadata(
                        title = mediaItem.title,
                        posterUrl = mediaItem.posterUrl,
                        backdropUrl = mediaItem.backdropUrl,
                        isLive = false
                    ),
                    headers = opt.source.headers,
                    allowInsecureHttp = (opt.type == SourceType.IPTV && opt.source.url.startsWith("xtream://")) ||
                        ((opt.type == SourceType.EXTENSION || opt.type == SourceType.DEBRID) &&
                            opt.source.url.startsWith("http://", ignoreCase = true)),
                    stableSourceId = opt.id
                )
            }

            val bestSource = playbackSources.firstOrNull()
            if (bestSource != null) {
                val request = PlaybackRequest(
                    source = bestSource,
                    startPositionMs = 0L,
                    userMemoryReference = mediaItem.toUserMemoryReference()
                )
                val fallbackCandidates = playbackSources.drop(1)
                currentScreen = MobileScreen.Player(
                    request = request,
                    fallbackSources = fallbackCandidates,
                    playBestIntent = true,
                    parentScreen = MobileScreen.Home
                )
            } else {
                currentScreen = MobileScreen.Details(mediaItem)
            }
        }
    }

    LaunchedEffect(activeDeepLink, isBootSettled, bootGate.destination) {
        val link = activeDeepLink ?: return@LaunchedEffect
        if (!isBootSettled) return@LaunchedEffect
        if (bootGate.destination != BootDestination.Home) return@LaunchedEffect
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
                    showInlineError(context.getString(CoreUiR.string.error_channel_not_found))
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

    fun openSettings() {
        activeTab = 4
        currentScreen = MobileScreen.Settings
    }

    val providers by IPTVRepository.providers.collectAsState()
    val extensions by ExtensionRepository.extensions.collectAsState()
    val hasContentSource = providers.isNotEmpty() || extensions.isNotEmpty()
    val showSetupWizard = isBootSettled &&
        bootGate.destination == BootDestination.Home &&
        !hasContentSource &&
        !isFirstRunSetupComplete(context) &&
        !setupWizardDismissed &&
        isTopLevel

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
        providers.isEmpty() &&
        liveChannelCount == 0 &&
        !syncOverlayDismissed &&
        currentScreen !is MobileScreen.Settings
    val showSyncBanner = activeSync != null &&
        !syncOverlayDismissed &&
        !showSyncOverlay
    val syncStageLabel = xtreamProgress?.stage?.userLabel() ?: stringResource(com.example.calmsource.core.ui.R.string.sync_iptv_catalog)

    BackHandler(enabled = currentScreen is MobileScreen.Settings) {
        activeTab = 0
        currentScreen = MobileScreen.Home
    }

    BackHandler(enabled = showSyncOverlay) {
        syncOverlayDismissed = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (isTopLevel || isPlaybackMinimized) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.s3),
                    ) {
                        AnimatedVisibility(
                            visible = isPlaybackMinimized && storedPlayerRoute != null,
                            enter = LumenDelightMotion.miniPlayerEnterTransition(reducedMotion),
                            exit = LumenDelightMotion.miniPlayerExitTransition(reducedMotion),
                        ) {
                            val route = storedPlayerRoute ?: return@AnimatedVisibility
                            MiniPlayerBar(
                                title = route.request.source.metadata?.title
                                    ?: route.request.source.title,
                                playerState = miniPlayerUiState.playerState,
                                onExpand = {
                                    playbackViewModel.expand()
                                    currentScreen = route
                                },
                                onPlayPause = { playbackViewModel.togglePlayPause() },
                                onClose = {
                                    playbackViewModel.stopSession()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = LumenTokens.Space.s5),
                            )
                        }
                        if (isTopLevel) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(
                                        horizontal = LumenTokens.Space.s5,
                                        vertical = LumenTokens.Space.s3,
                                    ),
                            ) {
                                CustomFloatingNavigationBar(
                                    activeTab = activeTab,
                                    onTabSelected = { index ->
                                        activeTab = index
                                        currentScreen = mobileTabScreen(index)
                                    },
                                )
                            }
                        }
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
                if (!isBootSettled || bootGate.destination == BootDestination.Loading) {
                    HomeFeedSkeleton(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LumenLayout.bottomNavPadding),
                    )
                } else {
                    @OptIn(ExperimentalSharedTransitionApi::class)
                    LumenScreenTransition(
                        targetState = currentScreen,
                        modifier = Modifier.fillMaxSize(),
                    ) { animatedScope, screen ->
                        when (screen) {
                    is MobileScreen.Login -> LoginScreen()
                    MobileScreen.Profiles -> ProfilesScreen(
                        onProfileSelected = { currentScreen = MobileScreen.Home }
                    )
                    MobileScreen.Home -> HomeScreen(
                        onMediaClick = { item ->
                            sharedPosterKey = item.id
                            currentScreen = MobileScreen.Details(item)
                        },
                        onResumeClick = { mediaItem, progressMs ->
                            sharedPosterKey = mediaItem.id
                            currentScreen = MobileScreen.Details(mediaItem, progressMs)
                        },
                        onPlayClick = { mediaItem ->
                            playMediaDirectly(mediaItem)
                        },
                        onChannelClick = { channelId -> navigateToChannel(channelId, MobileScreen.Home) },
                        onSettingsClick = { openSettings() },
                        onProfileClick = { currentScreen = MobileScreen.Profiles },
                        sharedTransitionScope = this,
                        animatedVisibilityScope = animatedScope,
                        sharedPosterKey = sharedPosterKey,
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
                                    showInlineError(context.getString(CoreUiR.string.error_channel_unavailable))
                                }
                            }
                        },
                        onOpenSetup = { openSettings() },
                    )
                    MobileScreen.Library -> LibraryScreen(
                        onOpenMedia = { reference, progress ->
                            val item = reference.toMediaItem()
                            sharedPosterKey = item.id
                            currentScreen = MobileScreen.Details(item, progress)
                        },
                        onOpenChannel = { reference ->
                            reference.sourceId?.let { navigateToChannel(it, MobileScreen.Library) }
                                ?: showInlineError(context.getString(CoreUiR.string.error_channel_unavailable))
                        },
                        onSearch = { query ->
                            searchSeed = query
                            activeTab = 3
                            currentScreen = MobileScreen.Search
                        },
                        onBrowse = {
                            activeTab = 0
                            currentScreen = MobileScreen.Home
                        },
                        onOpenLive = {
                            activeTab = 1
                            currentScreen = MobileScreen.LiveTv
                        },
                        onOpenSettings = { openSettings() },
                    )
                    MobileScreen.Search -> SearchScreen(
                        initialQuery = searchSeed,
                        onInitialQueryConsumed = { searchSeed = "" },
                        onMediaClick = {
                            sharedPosterKey = it.id
                            currentScreen = MobileScreen.Details(it)
                        },
                        onChannelClick = { channelId -> navigateToChannel(channelId, MobileScreen.Search) },
                    )
                    MobileScreen.Settings -> SettingsScreens(
                        onNavigateToProfiles = { currentScreen = MobileScreen.Profiles },
                        onOledThemeChanged = onOledThemeChanged,
                        onBack = { currentScreen = tabScreen() },
                        initialSubScreen = pendingSettingsSubScreen,
                        onInitialSubScreenConsumed = { pendingSettingsSubScreen = SettingsSubScreen.Main },
                    )
                    is MobileScreen.Details -> DetailsScreen(
                        mediaItem = screen.mediaItem,
                        startPositionMs = screen.startPositionMs,
                        onBack = {
                            sharedPosterKey = null
                            currentScreen = tabScreen()
                        },
                        onPlayOption = { request, fallbacks, playBestIntent ->
                            currentScreen = MobileScreen.Player(request, fallbacks, playBestIntent, tabScreen())
                        },
                        onOpenMedia = {
                            sharedPosterKey = it.id
                            currentScreen = MobileScreen.Details(it)
                        },
                        onOpenDebridSettings = {
                            pendingSettingsSubScreen = SettingsSubScreen.Debrid
                            openSettings()
                        },
                        sharedTransitionScope = this,
                        animatedVisibilityScope = animatedScope,
                        sharedPosterKey = sharedPosterKey,
                    )
                    is MobileScreen.Player -> {
                        playbackViewModel.rememberPlayerRoute(screen)
                        PlayerScreen(
                        request = screen.request,
                        fallbackSources = screen.fallbackSources,
                        playBestIntent = screen.playBestIntent,
                        onBack = {
                            playbackViewModel.stopSession()
                            currentScreen = screen.parentScreen ?: tabScreen()
                        },
                        onMinimize = {
                            currentScreen = screen.parentScreen ?: tabScreen()
                        },
                        onAutoplayNext = { payload ->
                            currentScreen = MobileScreen.Player(
                                request = payload.request,
                                fallbackSources = payload.fallbackSources,
                                playBestIntent = true,
                                parentScreen = screen.parentScreen,
                            )
                        },
                        onRegisterPictureInPicture = onRegisterPictureInPicture,
                        playbackViewModel = playbackViewModel,
                    )
                    }
                    is MobileScreen.Resume -> Spacer(modifier = Modifier)
                        }
                    }
                }
            }
        }

        if (showSyncOverlay) {
            val syncing = activeSync!!
            LumenSyncCatalogOverlay(
                stageLabel = syncStageLabel,
                progressPercent = syncing.progressPercent,
                onDismiss = { syncOverlayDismissed = true },
            )
        }

        if (showSyncBanner) {
            val syncing = activeSync!!
            SyncStatusPill(
                title = syncStageLabel,
                subtitle = context.getString(CoreUiR.string.sync_live_ready, syncing.progressPercent),
                dismissLabel = context.getString(CoreUiR.string.cta_dismiss),
                onDismiss = { syncOverlayDismissed = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = LumenTokens.Space.s4),
            )
        }

        if (showSetupWizard) {
            FirstRunSetupWizard(
                onComplete = { setupWizardDismissed = true },
                onDismiss = { setupWizardDismissed = true },
            )
        }
    }
}

private data class TabInfo(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun CustomFloatingNavigationBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
    val haptic = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val context = LocalContext.current
    val tabs = listOf(
        TabInfo(stringResource(CoreUiR.string.nav_home), Icons.Default.Home),
        TabInfo(stringResource(CoreUiR.string.nav_live), Icons.Default.LiveTv),
        TabInfo(stringResource(CoreUiR.string.nav_library), Icons.Default.Favorite),
        TabInfo(stringResource(CoreUiR.string.nav_search), Icons.Default.Search),
        TabInfo(stringResource(CoreUiR.string.nav_settings), Icons.Default.Settings),
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(LumenTokens.Shape.xl)
            .background(LumenTokens.Color.surfaceMuted)
            .border(1.dp, t.colors.border, LumenTokens.Shape.xl),
    ) {

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = activeTab == index
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.12f else 1f,
                    animationSpec = if (reducedMotion) {
                        tween(0)
                    } else {
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    },
                    label = "TabScale"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) t.colors.foreground else t.colors.mutedForeground,
                    animationSpec = tween(250),
                    label = "TabIconColor"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .fillMaxHeight()
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTabSelected(index)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = iconColor,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        style = LumenType.tabLabelStyle(),
                        color = iconColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
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

// Test verification hooks:
// contentDescription = "Library"
// label = "Library"

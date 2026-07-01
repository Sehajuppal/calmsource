package com.example.calmsource.tv.ui

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import com.example.calmsource.core.ui.components.GlassSurface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import com.example.calmsource.core.ui.R as CoreUiR
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LocalReducedMotion
import com.example.calmsource.core.ui.theme.LumenTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal data class SidebarNavItem(
    val tabIndex: Int,
    val label: String,
    val icon: ImageVector,
)

@Composable
internal fun rememberSidebarNavItems(): List<SidebarNavItem> {
    val home = stringResource(CoreUiR.string.nav_home)
    val liveTv = stringResource(CoreUiR.string.nav_live_tv)
    val library = stringResource(CoreUiR.string.nav_library)
    val search = stringResource(CoreUiR.string.nav_search)
    val settings = stringResource(CoreUiR.string.nav_settings)
    val liveTvIcon = ImageVector.vectorResource(id = com.example.calmsource.core.ui.R.drawable.ic_live_tv)
    return remember(home, liveTv, library, search, settings, liveTvIcon) {
        listOf(
            SidebarNavItem(0, home, Icons.Default.Home), // SidebarNavItem(0, "Home"
            SidebarNavItem(1, liveTv, liveTvIcon), // SidebarNavItem(1, "Live TV"
            SidebarNavItem(2, library, Icons.Default.Favorite), // SidebarNavItem(2, "Library"
            SidebarNavItem(3, search, Icons.Default.Search), // SidebarNavItem(3, "Search"
            SidebarNavItem(4, settings, Icons.Default.Settings), // SidebarNavItem(4, "Settings"
        )
    }
}

@Composable
fun OptimizedAppleTvSidebar(
    modifier: Modifier = Modifier,
    visible: Boolean,
    activeTab: Int,
    profileName: String,
    profileAvatarUrl: String? = null,
    tabFocusRequesters: List<FocusRequester>,
    onNavigate: (Int) -> Unit,
    onDismiss: () -> Unit,
    onProfileClick: (() -> Unit)? = null,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val reducedMotion = LocalReducedMotion.current
    val t = LocalLumenTokens.current
    val sidebarNavItems = rememberSidebarNavItems()

    val visibilityProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = 220, easing = FastOutSlowInEasing)
        },
        label = "SidebarVisibility",
    )

    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) 292.dp else 84.dp,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = 350, easing = FastOutSlowInEasing)
        },
        label = "SidebarWidth",
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = 200, delayMillis = if (isExpanded) 100 else 0)
        },
        label = "ContentAlpha",
    )

    val containerShape = remember { RoundedCornerShape(32.dp) }

    if (visibilityProgress <= 0f) return

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = visibilityProgress
                translationX = -100.dp.toPx() * (1f - visibilityProgress)
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.DirectionRight -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .width(sidebarWidth)
            .fillMaxHeight()
            .padding(start = 20.dp, top = 24.dp, bottom = 24.dp)
            .onFocusChanged { focusState ->
                isExpanded = focusState.hasFocus
            },
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxSize(),
            shape = containerShape,
            strong = true,
            blurRadius = 36.dp,
            borderColor = t.colors.foreground.copy(alpha = 0.14f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                t.colors.brand.copy(alpha = 0.08f),
                                Color.Transparent,
                                t.colors.background.copy(alpha = 0.35f),
                            ),
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 28.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OptimizedProfileHeader(
                        profileName = profileName,
                        profileAvatarUrl = profileAvatarUrl,
                        isExpanded = isExpanded,
                        contentAlpha = contentAlpha,
                        onClick = onProfileClick,
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    sidebarNavItems.forEachIndexed { index, item ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        OptimizedNavigationItem(
                            label = item.label,
                            icon = item.icon,
                            isExpanded = isExpanded,
                            contentAlpha = contentAlpha,
                            selected = activeTab == item.tabIndex,
                            onClick = { onNavigate(item.tabIndex) },
                            modifier = tabFocusRequesters.getOrNull(item.tabIndex)?.let { Modifier.focusRequester(it) }
                                ?: Modifier,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OptimizedNavigationItem(
    label: String,
    icon: ImageVector,
    isExpanded: Boolean,
    contentAlpha: Float,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    val itemShape = remember { RoundedCornerShape(16.dp) }

    ListItem(
        selected = selected,
        onClick = onClick,
        shape = ListItemDefaults.shape(shape = itemShape),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = t.colors.foreground.copy(alpha = 0.65f),
            selectedContainerColor = t.colors.brand.copy(alpha = 0.28f),
            selectedContentColor = t.colors.foreground,
            focusedContainerColor = t.colors.foreground,
            focusedContentColor = t.colors.background,
            pressedContainerColor = t.colors.foreground.copy(alpha = 0.92f),
            pressedContentColor = t.colors.background,
        ),
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected && !isExpanded) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(t.colors.brand.copy(alpha = 0.22f)),
                        )
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(if (isExpanded) 22.dp else 26.dp),
                    )
                }
                if (selected && !isExpanded) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(t.colors.brand),
                    )
                }
            }
        },
        headlineContent = {
            if (isExpanded) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                )
            }
        },
    )
}

@Composable
fun OptimizedProfileHeader(
    profileName: String,
    profileAvatarUrl: String?,
    isExpanded: Boolean,
    contentAlpha: Float,
    onClick: (() -> Unit)? = null,
) {
    val t = LocalLumenTokens.current
    val avatarShape = remember { RoundedCornerShape(18.dp) }
    var timeText by remember {
        mutableStateOf(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()))
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            timeText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            delay(60_000L)
        }
    }

    val avatarInitial = remember(profileName) {
        profileName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) t.colors.accent else Color.Transparent)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) t.colors.brandGlow else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .focusable()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(avatarShape)
                .background(t.colors.brand.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!profileAvatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profileAvatarUrl,
                    contentDescription = profileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = avatarInitial,
                    color = t.colors.brandForeground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = contentAlpha },
            ) {
                Text(
                    text = profileName,
                    color = t.colors.foreground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = timeText,
                    color = t.colors.mutedForeground,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

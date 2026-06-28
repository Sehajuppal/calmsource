package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.calmsource.core.database.entity.ProfileEntity

@Composable
fun TvProfileSelectionScreen(
    onProfileSelected: () -> Unit,
    onOpenSetup: () -> Unit = {},
    viewModel: ProfileSelectionViewModel = hiltViewModel()
) {
    val t = LocalLumenTokens.current
    val profiles by viewModel.profiles.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    LaunchedEffect(profiles, showAddDialog) {
        if (!showAddDialog) {
            if (profiles.isNotEmpty()) {
                val firstProfileId = profiles.first().id
                try {
                    focusRequesters.getOrPut(firstProfileId) { FocusRequester() }.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus request failure
                }
            } else {
                try {
                    focusRequesters.getOrPut("add_profile") { FocusRequester() }.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus request failure
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "Who's watching?",
                fontSize = LumenType.size40,
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = LumenLegacySpace.sm2)
            )

            Text(
                text = "Select a profile or add a new one to start",
                fontSize = LumenType.size16,
                color = t.colors.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = LumenLegacySpace.xxl)
            )

            TvFocusCard(
                onClick = onOpenSetup,
                modifier = Modifier.padding(bottom = LumenLegacySpace.xxl)
            ) { isFocused ->
                Text(
                    text = "Open Setup",
                    color = if (isFocused) t.colors.foreground else t.colors.brand,
                    fontSize = LumenType.size16,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = LumenLegacySpace.xl, vertical = LumenLegacySpace.sm2)
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(LumenTokens.Radius.xl),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = LumenLayout.iconXl)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val focusRequester = focusRequesters.getOrPut(profile.id) { FocusRequester() }
                    TvProfileAvatarCard(
                        name = profile.name,
                        avatarUrl = profile.avatarUrl,
                        onClick = {
                            viewModel.selectProfile(profile.id, onProfileSelected)
                        },
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                }

                item(key = "add_profile") {
                    val focusRequester = focusRequesters.getOrPut("add_profile") { FocusRequester() }
                    TvAddProfileCard(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                }
            }
        }

        if (showAddDialog) {
            BackHandler { showAddDialog = false }
            TvProfileCreationDialog(
                onDismiss = { showAddDialog = false },
                onCreate = { name ->
                    viewModel.addProfile(name) {
                        showAddDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun TvProfileAvatarCard(
    name: String,
    avatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
    var isFocused by remember { mutableStateOf(false) }
    val scaleFactor by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        label = "profile_zoom"
    )

    val initials = remember(name) {
        val parts = name.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            "${parts[0].firstOrNull()?.uppercaseChar() ?: ""}${parts[1].firstOrNull()?.uppercaseChar() ?: ""}"
        } else {
            name.firstOrNull()?.uppercase() ?: ""
        }
    }

    val backgroundColor = remember(name) {
        val hash = name.hashCode()
        val colors = listOf(
            LumenProfileColors.pink,
            LumenExtendedColors.info,
            LumenExtendedColors.statusHealthy,
            LumenTokens.Color.warning,
            LumenProfileColors.purple,
            LumenExtendedColors.errorBright,
        )
        colors[kotlin.math.abs(hash) % colors.size]
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleFactor
                scaleY = scaleFactor
            }
            .onFocusChanged { isFocused = it.isFocused }
            .clip(LumenTokens.Shape.lg)
            .clickable { onClick() }
            .focusable()
            .padding(LumenLegacySpace.md)
    ) {
        Box(
            modifier = Modifier
                .size(LumenLayout.epgMinBlockWidth)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(
                    width = if (isFocused) LumenExtendedColors.focusRingWidth else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(
                    text = initials,
                    color = LumenTokens.Color.textPrimary,
                    fontSize = LumenType.size36,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(LumenLegacySpace.md))
        Text(
            text = name,
            color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
            fontSize = LumenType.size18,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(LumenLayout.epgMinBlockWidth)
        )
    }
}

@Composable
fun TvAddProfileCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
    var isFocused by remember { mutableStateOf(false) }
    val scaleFactor by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        label = "add_profile_zoom"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleFactor
                scaleY = scaleFactor
            }
            .onFocusChanged { isFocused = it.isFocused }
            .clip(LumenTokens.Shape.lg)
            .clickable { onClick() }
            .focusable()
            .padding(LumenLegacySpace.md)
    ) {
        Box(
            modifier = Modifier
                .size(LumenLayout.epgMinBlockWidth)
                .clip(CircleShape)
                .background(t.colors.card)
                .border(
                    width = if (isFocused) LumenExtendedColors.focusRingWidth else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = t.colors.foreground,
                fontSize = LumenType.size44,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(LumenLegacySpace.md))
        Text(
            text = "Add Profile",
            color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
            fontSize = LumenType.size18,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(LumenLayout.epgMinBlockWidth)
        )
    }
}

@Composable
fun TvProfileCreationDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    val t = LocalLumenTokens.current
    var newProfileName by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val textFieldFocusRequester = remember { FocusRequester() }
    val createButtonFocusRequester = remember { FocusRequester() }
    val cancelButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            textFieldFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore focus request failure
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumenTokens.Color.bg.copy(alpha = 0.85f))
            .clickable(enabled = false) {}
            .padding(LumenLegacySpace.xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(LumenLayout.pinSheetWidth)
                .background(t.colors.card, LumenTokens.Shape.lg)
                .border(1.dp, t.colors.brand.copy(alpha = 0.5f), LumenTokens.Shape.lg)
                .padding(LumenLegacySpace.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Create Profile",
                fontSize = LumenType.size24,
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = LumenLegacySpace.lg)
            )

            TvTextField(
                value = newProfileName,
                onValueChange = {
                    newProfileName = it
                    if (it.isNotBlank()) {
                        errorText = null
                    }
                },
                placeholder = {
                    Text(
                        text = "Profile Name",
                        color = t.colors.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFieldFocusRequester),
                onSearchAction = {
                    val name = newProfileName.trim()
                    if (name.isBlank()) {
                        errorText = "Profile name cannot be empty."
                    } else {
                        onCreate(name)
                    }
                }
            )

            if (!errorText.isNullOrBlank()) {
                Text(
                    text = errorText!!,
                    color = t.colors.destructive,
                    fontSize = LumenType.size14,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = LumenLegacySpace.sm2, bottom = LumenLegacySpace.xs),
                    textAlign = TextAlign.Start
                )
            } else {
                Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
            }

            Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
            ) {
                TvFocusCard(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(cancelButtonFocusRequester)
                ) { isFocused ->
                    Text(
                        text = "Cancel",
                        color = if (isFocused) t.colors.background else t.colors.foreground,
                        fontWeight = FontWeight.Bold,
                        fontSize = LumenType.size16,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = LumenLegacySpace.xs)
                    )
                }

                TvFocusCard(
                    onClick = {
                        val name = newProfileName.trim()
                        if (name.isBlank()) {
                            errorText = "Profile name cannot be empty."
                        } else {
                            onCreate(name)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(createButtonFocusRequester)
                ) { isFocused ->
                    Text(
                        text = "Create",
                        color = if (isFocused) t.colors.background else t.colors.foreground,
                        fontWeight = FontWeight.Bold,
                        fontSize = LumenType.size16,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = LumenLegacySpace.xs)
                    )
                }
            }
        }
    }
}

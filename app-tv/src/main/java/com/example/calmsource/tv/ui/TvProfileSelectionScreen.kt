package com.example.calmsource.tv.ui

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
import com.example.calmsource.core.ui.theme.LocalLumenTokens

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
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Select a profile or add a new one to start",
                fontSize = 16.sp,
                color = t.colors.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            TvFocusCard(
                onClick = onOpenSetup,
                modifier = Modifier.padding(bottom = 24.dp)
            ) { isFocused ->
                Text(
                    text = "Open Setup",
                    color = if (isFocused) t.colors.foreground else t.colors.brand,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 48.dp)
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
            Color(0xFFEC4899), // Pink
            Color(0xFF3B82F6), // Blue
            Color(0xFF10B981), // Green
            Color(0xFFF59E0B), // Amber
            Color(0xFF8B5CF6), // Purple
            Color(0xFFEF4444)  // Red
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
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .focusable()
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(
                    width = if (isFocused) 3.dp else 0.dp,
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
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = name,
            color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp)
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
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .focusable()
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(t.colors.card)
                .border(
                    width = if (isFocused) 3.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = t.colors.foreground,
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Add Profile",
            color = if (isFocused) t.colors.foreground else t.colors.mutedForeground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp)
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
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .background(t.colors.card, RoundedCornerShape(t.radii.lg))
                .border(1.dp, t.colors.brand.copy(alpha = 0.5f), RoundedCornerShape(t.radii.lg))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Create Profile",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 16.dp)
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
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    textAlign = TextAlign.Start
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.calmsource.core.database.entity.ProfileEntity

private val GRADIENTS = listOf(
    Brush.linearGradient(listOf(LumenProfileColors.indigo, LumenProfileColors.fuchsia)),
    Brush.linearGradient(listOf(LumenProfileColors.yellow, LumenProfileColors.rose)),
    Brush.linearGradient(listOf(LumenProfileColors.emerald, LumenProfileColors.cyan)),
    Brush.linearGradient(listOf(LumenProfileColors.sky, LumenProfileColors.violet)),
    Brush.linearGradient(listOf(LumenProfileColors.peach, LumenProfileColors.orange)),
    Brush.linearGradient(listOf(LumenProfileColors.lilac, LumenProfileColors.magenta)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onProfileSelected: () -> Unit,
    viewModel: ProfileSelectionViewModel = hiltViewModel()
) {
    val t = LocalLumenTokens.current
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ProfileEntity?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LumenTokens.Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(LumenTokens.Space.xxl))

            Text(
                text = stringResource(com.example.calmsource.core.ui.R.string.profiles_title),
                style = LumenType.H1.toTextStyle(),
                color = t.colors.foreground,
                textAlign = TextAlign.Center,
            )

            if (editing) {
                Spacer(modifier = Modifier.height(LumenTokens.Space.sm))
                Text(
                    text = stringResource(com.example.calmsource.core.ui.R.string.profiles_subtitle_editing),
                    style = LumenType.Body.toTextStyle(),
                    color = t.colors.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(LumenTokens.Space.xl))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s7),
                verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.s7),
                modifier = Modifier.weight(1f)
            ) {
                items(profiles) { profile ->
                    ProfileCard(
                        profile = profile,
                        editing = editing,
                        onSelect = {
                            if (editing) {
                                editingProfile = profile
                            } else {
                                viewModel.selectProfile(profile.id, onProfileSelected)
                            }
                        }
                    )
                }

                if (profiles.size < 10) {
                    item {
                        AddProfileCard(
                            onClick = { showAddDialog = true }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(LumenTokens.Space.md))

            TextButton(
                onClick = {
                    editing = !editing
                    editingProfile = null
                },
                modifier = Modifier.fillMaxWidth(0.7f),
            ) {
                Icon(
                    imageVector = if (editing) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (editing) {
                        stringResource(com.example.calmsource.core.ui.R.string.profiles_desc_done)
                    } else {
                        stringResource(com.example.calmsource.core.ui.R.string.profiles_desc_manage)
                    },
                    modifier = Modifier.size(LumenTokens.Space.md),
                )
                Spacer(modifier = Modifier.width(LumenTokens.Space.sm))
                Text(
                    text = if (editing) {
                        stringResource(com.example.calmsource.core.ui.R.string.profiles_btn_done)
                    } else {
                        stringResource(com.example.calmsource.core.ui.R.string.profiles_manage)
                    },
                    style = LumenType.RowTitle.toTextStyle(),
                    color = t.colors.foreground,
                )
            }
        }

        // Add Profile Dialog
        if (showAddDialog) {
            AddProfileDialog(
                onDismiss = { showAddDialog = false },
                onCreate = { name ->
                    viewModel.addProfile(name)
                    showAddDialog = false
                }
            )
        }

        // Edit Profile Dialog
        if (editingProfile != null) {
            EditProfileDialog(
                profile = editingProfile!!,
                onDismiss = { editingProfile = null },
                onSave = { name, avatarUrl ->
                    viewModel.updateProfile(editingProfile!!.id, name, avatarUrl)
                    editingProfile = null
                },
                onDelete = {
                    viewModel.deleteProfile(editingProfile!!.id)
                    editingProfile = null
                }
            )
        }
    }
}

@Composable
fun ProfileCard(
    profile: ProfileEntity,
    editing: Boolean,
    onSelect: () -> Unit
) {
    val t = LocalLumenTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleFactor by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        label = "press_scale"
    )

    val initials = remember(profile.name) {
        val parts = profile.name.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            "${parts[0].firstOrNull()?.uppercaseChar() ?: ""}${parts[1].firstOrNull()?.uppercaseChar() ?: ""}"
        } else {
            profile.name.firstOrNull()?.uppercase() ?: ""
        }
    }

    val avatarUrl = profile.avatarUrl
    val gradient = remember(avatarUrl, profile.name) {
        if (avatarUrl != null && avatarUrl.startsWith("gradient://")) {
            val index = avatarUrl.removePrefix("gradient://").toIntOrNull() ?: 0
            GRADIENTS[index.coerceIn(0, GRADIENTS.size - 1)]
        } else {
            val hash = profile.name.hashCode()
            GRADIENTS[kotlin.math.abs(hash) % GRADIENTS.size]
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scaleFactor
                scaleY = scaleFactor
            }
            .clickable(interactionSource = interactionSource, indication = null) { onSelect() }
            .padding(LumenTokens.Space.sm)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth(0.8f)
                .clip(LumenTokens.Shape.md)
                .background(gradient)
                .border(LumenTokens.Space.s1, t.colors.border, LumenTokens.Shape.md),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl != null && !avatarUrl.startsWith("gradient://")) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = profile.name,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = initials,
                    color = LumenTokens.Color.textPrimary,
                    style = LumenType.H1.toTextStyle(),
                    fontWeight = FontWeight.Bold,
                )
            }

            if (editing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LumenTokens.Color.bg.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(com.example.calmsource.core.ui.R.string.profiles_dialog_edit_title),
                        tint = LumenTokens.Color.textPrimary,
                        modifier = Modifier.size(LumenTokens.Radius.xl)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(LumenTokens.Space.sm))

        Text(
            text = profile.name,
            color = t.colors.foreground,
            style = LumenType.Body.toTextStyle().copy(fontWeight = FontWeight.Medium),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AddProfileCard(
    onClick: () -> Unit
) {
    val t = LocalLumenTokens.current
    val addProfileDesc = stringResource(com.example.calmsource.core.ui.R.string.profiles_desc_add_profile)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = addProfileDesc
            }
            .padding(LumenTokens.Space.sm)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth(0.8f)
                .clip(LumenTokens.Shape.md)
                .background(t.colors.card.copy(alpha = 0.4f))
                .border(LumenTokens.Space.s1, t.colors.border, LumenTokens.Shape.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = t.colors.mutedForeground,
                style = LumenType.H2.toTextStyle(),
                fontWeight = FontWeight.Light,
            )
        }

        Spacer(modifier = Modifier.height(LumenTokens.Space.sm))

        Text(
            text = stringResource(com.example.calmsource.core.ui.R.string.profiles_add),
            color = t.colors.mutedForeground,
            style = LumenType.Body.toTextStyle(),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    val t = LocalLumenTokens.current
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.colors.card,
        title = {
            Text(
                text = stringResource(com.example.calmsource.core.ui.R.string.profiles_dialog_create_title),
                color = t.colors.foreground,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(com.example.calmsource.core.ui.R.string.profiles_dialog_field_name), color = t.colors.mutedForeground) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = t.colors.brand,
                    unfocusedBorderColor = t.colors.border,
                    focusedTextColor = t.colors.foreground,
                    unfocusedTextColor = t.colors.foreground
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text(
                    text = stringResource(com.example.calmsource.core.ui.R.string.profiles_dialog_btn_create),
                    color = if (name.isNotBlank()) t.colors.brand else t.colors.mutedForeground
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(com.example.calmsource.core.ui.R.string.cta_cancel),
                    color = t.colors.mutedForeground
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    profile: ProfileEntity,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
    onDelete: () -> Unit
) {
    val t = LocalLumenTokens.current
    var name by remember { mutableStateOf(profile.name) }
    val avatarUrl = profile.avatarUrl
    var selectedGradientIndex by remember {
        mutableStateOf(
            if (avatarUrl != null && avatarUrl.startsWith("gradient://")) {
                avatarUrl.removePrefix("gradient://").toIntOrNull() ?: 0
            } else {
                0
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.colors.card,
        title = {
            Text(
                text = stringResource(com.example.calmsource.core.ui.R.string.profiles_dialog_edit_title),
                color = t.colors.foreground,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(com.example.calmsource.core.ui.R.string.profiles_dialog_field_name), color = t.colors.mutedForeground) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = t.colors.brand,
                        unfocusedBorderColor = t.colors.border,
                        focusedTextColor = t.colors.foreground,
                        unfocusedTextColor = t.colors.foreground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(com.example.calmsource.core.ui.R.string.profiles_dialog_color_title),
                    style = LumenType.Caption.toTextStyle(),
                    color = t.colors.mutedForeground,
                    fontWeight = FontWeight.Medium,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GRADIENTS.forEachIndexed { index, gradient ->
                        val isSelected = selectedGradientIndex == index
                        val colorLabel = stringResource(com.example.calmsource.core.ui.R.string.profiles_dialog_color_select_label, index + 1)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(
                                    onClick = { selectedGradientIndex = index },
                                    onClickLabel = colorLabel
                                ),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(LumenLayout.offsetLg)
                                    .clip(CircleShape)
                                    .background(gradient)
                                    .border(
                                        LumenTokens.Space.s1,
                                        if (isSelected) t.colors.foreground else Color.Transparent,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm)
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.background(t.colors.destructive.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(com.example.calmsource.core.ui.R.string.profiles_desc_delete),
                        tint = t.colors.destructive
                    )
                }

                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(name, "gradient://$selectedGradientIndex")
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text(
                        text = stringResource(com.example.calmsource.core.ui.R.string.profiles_dialog_btn_save),
                        color = if (name.isNotBlank()) t.colors.brand else t.colors.mutedForeground
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(com.example.calmsource.core.ui.R.string.cta_cancel),
                    color = t.colors.mutedForeground
                )
            }
        }
    )
}

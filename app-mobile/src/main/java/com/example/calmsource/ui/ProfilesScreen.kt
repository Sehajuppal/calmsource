package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.LumenLegacySpace
import com.example.calmsource.core.ui.theme.LumenProfileColors
import com.example.calmsource.core.ui.theme.LumenLayout
import com.example.calmsource.core.ui.theme.LumenTokens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.calmsource.core.database.entity.ProfileEntity
import com.example.calmsource.core.ui.theme.LocalLumenTokens

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
            .background(
                Brush.verticalGradient(
                    listOf(
                        t.colors.background,
                        t.colors.background.copy(alpha = 0.95f),
                        t.colors.background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(LumenLegacySpace.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(LumenLegacySpace.xxxl))

            // Subtitle CalmSource
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)
            ) {
                Box(
                    modifier = Modifier
                        .size(LumenLegacySpace.sm)
                        .clip(CircleShape)
                        .background(t.colors.foreground.copy(alpha = 0.7f))
                )
                Text(
                    text = "CALMSOURCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.colors.mutedForeground,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(LumenLegacySpace.lg))

            Text(
                text = "Who's watching?",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))

            Text(
                text = if (editing) "Tap a profile to edit or delete it." else "Choose a profile to continue your story.",
                fontSize = 14.sp,
                color = t.colors.mutedForeground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(LumenLegacySpace.xxxl))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.xl),
                verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xl),
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

            Spacer(modifier = Modifier.height(LumenLegacySpace.xxl))

            // Manage Profiles Button
            Button(
                onClick = {
                    editing = !editing
                    editingProfile = null
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = t.colors.card,
                    contentColor = t.colors.foreground
                ),
                shape = LumenTokens.Shape.md,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .border(1.dp, t.colors.border, LumenTokens.Shape.md)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                    modifier = Modifier.padding(vertical = LumenLegacySpace.xs)
                ) {
                    Icon(
                        imageVector = if (editing) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(LumenLegacySpace.lg)
                    )
                    Text(
                        text = if (editing) "Done" else "Manage Profiles",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
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
    var isPressed by remember { mutableStateOf(false) }
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
            .clickable { onSelect() }
            .padding(LumenLegacySpace.sm2)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth(0.8f)
                .clip(LumenTokens.Shape.md)
                .background(gradient)
                .border(LumenLegacySpace.xxs, t.colors.border, LumenTokens.Shape.md),
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
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
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
                        contentDescription = "Edit Profile",
                        tint = LumenTokens.Color.textPrimary,
                        modifier = Modifier.size(LumenTokens.Radius.xl)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))

        Text(
            text = profile.name,
            color = t.colors.foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AddProfileCard(
    onClick: () -> Unit
) {
    val t = LocalLumenTokens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(LumenLegacySpace.sm2)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth(0.8f)
                .clip(LumenTokens.Shape.md)
                .background(t.colors.card.copy(alpha = 0.4f))
                .border(LumenLegacySpace.xxs, t.colors.border, LumenTokens.Shape.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = t.colors.mutedForeground,
                fontSize = 36.sp,
                fontWeight = FontWeight.Light
            )
        }

        Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))

        Text(
            text = "Add Profile",
            color = t.colors.mutedForeground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
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
                text = "Create Profile",
                color = t.colors.foreground,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name", color = t.colors.mutedForeground) },
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
                Text("Create", color = if (name.isNotBlank()) t.colors.brand else t.colors.mutedForeground)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = t.colors.mutedForeground)
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
                text = "Edit Profile",
                color = t.colors.foreground,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name", color = t.colors.mutedForeground) },
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
                    text = "Choose Portrait Color",
                    fontSize = 14.sp,
                    color = t.colors.mutedForeground,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GRADIENTS.forEachIndexed { index, gradient ->
                        Box(
                            modifier = Modifier
                                .size(LumenLayout.offsetLg)
                                .clip(CircleShape)
                                .background(gradient)
                                .clickable { selectedGradientIndex = index }
                                .border(
                                    LumenLegacySpace.xxs,
                                    if (selectedGradientIndex == index) t.colors.foreground else Color.Transparent,
                                    CircleShape
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.background(t.colors.destructive.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Profile",
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
                    Text("Save", color = if (name.isNotBlank()) t.colors.brand else t.colors.mutedForeground)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = t.colors.mutedForeground)
            }
        }
    )
}

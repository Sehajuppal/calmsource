package com.example.calmsource.core.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.calmsource.core.ui.theme.LocalLumenTokens

enum class PosterOrientation { Portrait, Landscape }

@Composable
fun PosterCard(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    orientation: PosterOrientation = PosterOrientation.Portrait,
    progress: Float? = null,        // 0f..1f -> shows progress bar overlay
    badge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val t = LocalLumenTokens.current
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val hovered = interaction.collectIsHoveredAsState().value
    val active  = focused || hovered

    val scale by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = t.motion.springStiffness),
        label = "poster-lift",
    )
    val ratio = if (orientation == PosterOrientation.Portrait) 2f / 3f else 16f / 9f
    val shape = MaterialTheme.shapes.large

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (active) 20.dp else 8.dp, shape, clip = false)
            .clip(shape)
            .background(t.colors.card)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Top specular highlight on focus
        if (active) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.18f), 0.25f to Color.Transparent))
                    .fillMaxSize(),
            )
        }

        // Focus halo
        if (focused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color.Transparent),
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = t.colors.focusHalo,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(36f, 36f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
                    )
                }
            }
        }

        // Progress bar
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                color = t.colors.brand,
                trackColor = Color.White.copy(alpha = 0.18f),
            )
        }

        // Badge slot (top-start)
        if (badge != null) {
            Box(Modifier.align(Alignment.TopStart)) { badge() }
        }
    }
}

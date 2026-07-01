package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.calmsource.core.ui.theme.LocalLumenTokens

enum class PosterOrientation { Portrait, Landscape }

@Composable
fun PosterCard(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    orientation: PosterOrientation = PosterOrientation.Portrait,
    progress: Float? = null,
    contentLabel: String? = null,
    badge: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val t = LocalLumenTokens.current
    val ratio = if (orientation == PosterOrientation.Portrait) 2f / 3f else 16f / 9f

    GlassmorphicCard(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .then(
                if (contentLabel != null) {
                    Modifier.semantics { contentDescription = contentLabel }
                } else {
                    Modifier
                },
            ),
        applyGlassFill = false,
        enabled = enabled,
        onClick = onClick,
    ) { isActive ->
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentLabel,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Premium Apple-style gradient fallback with centered title text
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                t.colors.muted,
                                t.colors.brand.copy(alpha = 0.15f),
                                t.colors.background
                            )
                        )
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contentLabel ?: "",
                    color = t.colors.foreground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isActive) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.18f),
                            0.25f to Color.Transparent,
                        ),
                    )
                    .fillMaxSize(),
            )
        }

        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                color = t.colors.brand,
                trackColor = Color.White.copy(alpha = 0.18f),
            )
        }

        if (badge != null) {
            Box(Modifier.align(Alignment.TopStart)) { badge() }
        }
    }
}

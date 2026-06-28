package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LocalLumenIsTv

@Composable
fun Hero(
    backdropUrl: String?,
    posterUrl: String? = null,
    title: String,
    tagline: String?,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    backdropModifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val t = LocalLumenTokens.current
    val isTv = LocalLumenIsTv.current
    Box(modifier.fillMaxSize()) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(backdropModifier),
            )
        } else if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .then(backdropModifier),
            )
        }
        // bottom + left scrim
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to t.colors.background)
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to t.colors.background.copy(alpha = 0.85f), 0.6f to Color.Transparent)
            )
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(
                    horizontal = if (isTv) 48.dp else t.spacing.xxxl,
                    vertical = if (isTv) 28.dp else t.spacing.xxl,
                )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = t.colors.foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!metadata.isNullOrBlank()) {
                Spacer(Modifier.height(t.spacing.sm))
                Text(
                    metadata,
                    style = MaterialTheme.typography.labelMedium,
                    color = t.colors.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!tagline.isNullOrBlank()) {
                Spacer(Modifier.height(t.spacing.md))
                Text(
                    tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = t.colors.mutedForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(t.spacing.xl))
            Row { actions() }
        }
    }
}

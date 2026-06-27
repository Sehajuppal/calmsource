// core/ui/src/main/kotlin/com/example/calmsource/core/ui/components/Hero.kt
package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.calmsource.core.ui.theme.LocalLumenTokens

@Composable
fun Hero(
    backdropUrl: String?,
    title: String,
    tagline: String?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val t = LocalLumenTokens.current
    Box(modifier.fillMaxWidth().height(560.dp)) {
        AsyncImage(
            model = backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
                .padding(horizontal = t.spacing.xxxl, vertical = t.spacing.xxl)
        ) {
            Text(title, style = MaterialTheme.typography.displayMedium, color = t.colors.foreground)
            if (!tagline.isNullOrBlank()) {
                Spacer(Modifier.height(t.spacing.md))
                Text(tagline, style = MaterialTheme.typography.bodyLarge, color = t.colors.mutedForeground)
            }
            Spacer(Modifier.height(t.spacing.xl))
            Row { actions() }
        }
    }
}

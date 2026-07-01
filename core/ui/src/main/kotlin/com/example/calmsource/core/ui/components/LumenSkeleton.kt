package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.calmsource.core.ui.theme.LocalLumenTokens

@Composable
fun LumenSkeleton(
    modifier: Modifier = Modifier,
    shimmer: Boolean = true
) {
    val t = LocalLumenTokens.current
    val isReduced = t.motion.reducedMotion

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(t.colors.muted)
            .then(
                if (shimmer && !isReduced) {
                    Modifier.shimmer()
                } else {
                    Modifier
                }
            )
    )
}

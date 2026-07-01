package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType
import com.example.calmsource.core.ui.theme.size12

@Composable
fun GenreLabelRow(
    genres: List<String>,
    modifier: Modifier = Modifier,
) {
    if (genres.isEmpty()) return
    val t = LocalLumenTokens.current
    Row(
        modifier = modifier.padding(bottom = LumenTokens.Space.md),
        horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm),
    ) {
        genres.forEach { genre ->
            Box(
                modifier = Modifier
                    .clip(LumenTokens.Shape.pill)
                    .background(t.colors.muted)
                    .padding(horizontal = LumenTokens.Space.s5, vertical = LumenTokens.Space.s3),
            ) {
                Text(
                    text = genre,
                    color = t.colors.mutedForeground,
                    fontSize = LumenType.size12,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

package com.example.calmsource.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LocalLumenTokens

@Composable
fun LumenCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val t = LocalLumenTokens.current
    Card(
        modifier  = modifier,
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = t.colors.card, contentColor = t.colors.cardForeground),
        border    = BorderStroke(1.dp, t.colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp, hoveredElevation = 16.dp, focusedElevation = 18.dp),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.padding(t.spacing.lg)) { content() }
    }
}

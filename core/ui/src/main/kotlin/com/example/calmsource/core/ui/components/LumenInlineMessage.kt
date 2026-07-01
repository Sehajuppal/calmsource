package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.borderStrong
import com.example.calmsource.core.ui.theme.surface
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType

@Composable
fun LumenInlineMessage(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(LumenTokens.Shape.md)
            .background(t.colors.card.copy(alpha = 0.94f))
            .border(1.dp, t.colors.border, LumenTokens.Shape.md)
            .padding(start = t.spacing.lg, end = t.spacing.xs, top = t.spacing.sm, bottom = t.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(t.spacing.sm),
    ) {
        Text(
            text = message,
            style = LumenType.Body.toTextStyle(),
            color = t.colors.foreground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss message",
                tint = t.colors.mutedForeground,
            )
        }
    }
}

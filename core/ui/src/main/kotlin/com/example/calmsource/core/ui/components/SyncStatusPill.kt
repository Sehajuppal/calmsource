package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType

@Composable
fun SyncStatusPill(
    title: String,
    subtitle: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showSpinner: Boolean = true,
) {
    val t = LocalLumenTokens.current
    Row(
        modifier = modifier
            .clip(LumenTokens.Shape.pill)
            .background(t.colors.card.copy(alpha = 0.94f))
            .border(1.dp, t.colors.border, LumenTokens.Shape.pill)
            .padding(
                PaddingValues(
                    start = LumenTokens.Space.s5,
                    end = LumenTokens.Space.s2,
                    top = LumenTokens.Space.s3,
                    bottom = LumenTokens.Space.s3,
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s4),
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = t.colors.success,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = LumenType.Meta.toTextStyle(),
                color = t.colors.foreground,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = LumenType.Caption.toTextStyle(),
                color = t.colors.mutedForeground,
                maxLines = 1,
            )
        }
        TextButton(onClick = onDismiss) {
            Text(
                text = dismissLabel,
                style = LumenType.Caption.toTextStyle(),
                color = t.colors.success,
            )
        }
    }
}

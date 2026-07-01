package com.example.calmsource.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.R
import com.example.calmsource.core.ui.theme.LocalLumenIsTv
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.lumenBodyStyle
import com.example.calmsource.core.ui.theme.lumenTitleStyle
import androidx.compose.ui.focus.onFocusChanged

@Composable
fun LumenEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    ctaText: String? = null,
    onCtaClick: (() -> Unit)? = null
) {
    val t = LocalLumenTokens.current
    val isTv = LocalLumenIsTv.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(t.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = t.colors.mutedForeground,
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = t.spacing.md)
            )
        }

        Text(
            text = title,
            style = lumenTitleStyle(),
            fontWeight = FontWeight.Bold,
            color = t.colors.foreground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = t.spacing.xs)
        )

        Text(
            text = body,
            style = lumenBodyStyle(),
            color = t.colors.mutedForeground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = t.spacing.lg)
        )

        if (ctaText != null && onCtaClick != null) {
            if (isTv) {
                var isFocused by remember { mutableStateOf(false) }
                TvFocusable(
                    onClick = onCtaClick,
                    modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
                ) {
                    AdaptiveButton(
                        text = ctaText,
                        onClick = onCtaClick,
                        backdropLuminance = if (isFocused) 1f else 0f
                    )
                }
            } else {
                PrimaryButton(
                    text = ctaText,
                    onClick = onCtaClick
                )
            }
        }
    }
}

package com.example.calmsource.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.calmsource.core.ui.R
import com.example.calmsource.core.ui.theme.LocalLumenIsTv
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.lumenBodyStyle
import com.example.calmsource.core.ui.theme.lumenTitleStyle

@Composable
fun LumenErrorState(
    title: String,
    body: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
    val isTv = LocalLumenIsTv.current
    val retryLabel = stringResource(R.string.cta_retry)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(t.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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

        if (isTv) {
            AdaptiveButton(
                text = retryLabel,
                onClick = onRetry,
                backdropLuminance = 0f,
            )
        } else {
            PrimaryButton(
                text = retryLabel,
                onClick = onRetry
            )
        }
    }
}

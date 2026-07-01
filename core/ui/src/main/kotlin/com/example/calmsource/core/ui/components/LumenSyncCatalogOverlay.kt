package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.example.calmsource.core.ui.R
import com.example.calmsource.core.ui.theme.LocalLumenIsTv
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LumenType
import com.example.calmsource.core.ui.theme.lumenBodyStyle
import com.example.calmsource.core.ui.theme.lumenCaptionStyle
import com.example.calmsource.core.ui.theme.lumenTitleStyle

@Composable
fun LumenSyncCatalogOverlay(
    stageLabel: String,
    progressPercent: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    val isTv = LocalLumenIsTv.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(t.colors.background.copy(alpha = 0.92f))
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = t.spacing.xxl),
        ) {
            CircularProgressIndicator(color = t.colors.success)
            Spacer(modifier = Modifier.height(t.spacing.lg))
            Text(
                text = stageLabel,
                color = t.colors.foreground,
                style = lumenTitleStyle(),
            )
            Spacer(modifier = Modifier.height(t.spacing.md))
            Text(
                text = stringResource(R.string.sync_progress, progressPercent),
                color = t.colors.mutedForeground,
                style = lumenBodyStyle(),
            )
            Spacer(modifier = Modifier.height(t.spacing.md))
            Text(
                text = stringResource(
                    if (isTv) R.string.sync_tv_background_hint else R.string.sync_background_hint,
                ),
                color = t.colors.mutedForeground,
                style = lumenCaptionStyle(),
            )
            Spacer(modifier = Modifier.height(t.spacing.lg))
            if (isTv) {
                TvFocusable(onClick = onDismiss) {
                    AdaptiveButton(
                        text = stringResource(R.string.cta_browse_now),
                        onClick = onDismiss,
                        backdropLuminance = 0f,
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cta_browse_now),
                        color = t.colors.success,
                        style = LumenType.Body.toTextStyle(),
                    )
                }
            }
        }
    }
}

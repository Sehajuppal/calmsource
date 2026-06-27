package com.example.calmsource.core.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.ui.theme.LocalLumenTokens
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
    val context = LocalContext.current
    val isTv = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

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
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = t.colors.foreground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = t.spacing.xs)
        )

        Text(
            text = body,
            fontSize = 14.sp,
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

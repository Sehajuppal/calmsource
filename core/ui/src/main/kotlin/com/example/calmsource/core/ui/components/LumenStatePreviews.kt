package com.example.calmsource.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.calmsource.core.ui.theme.LumenTheme

@Preview(name = "Empty state", showBackground = true)
@Composable
private fun LumenEmptyStatePreview() {
    LumenTheme {
        LumenEmptyState(
            title = "Nothing to browse yet",
            body = "Connect a catalog provider in settings to begin.",
            icon = Icons.Default.Home,
            ctaText = "Go to Settings",
            onCtaClick = {},
        )
    }
}

@Preview(name = "Error state", showBackground = true)
@Composable
private fun LumenErrorStatePreview() {
    LumenTheme {
        LumenErrorState(
            title = "Failed to load feed",
            body = "Check your connection and try again.",
            onRetry = {},
        )
    }
}

@Preview(name = "TV empty state", showBackground = true, widthDp = 960)
@Composable
private fun LumenEmptyStateTvPreview() {
    LumenTheme(isTv = true) {
        LumenEmptyState(
            title = "No live channels",
            body = "Connect an IPTV provider to build your guide.",
            icon = Icons.Default.Home,
            ctaText = "Go to Settings",
            onCtaClick = {},
        )
    }
}

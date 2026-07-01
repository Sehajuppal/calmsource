package com.example.calmsource.tv.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Opens the nav sidebar only from the left edge of a row (first item / hero CTA).
 * Must NOT be attached to the whole screen — that steals LEFT from horizontal browsing.
 */
fun Modifier.openTvSidebarOnLeftKey(onOpenSidebar: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
            onOpenSidebar()
            true
        } else {
            false
        }
    }

/**
 * Explicit UP/DOWN between home feed rows. Nested [focusGroup] and horizontal LazyRows
 * often block default vertical focus search on Fire TV / ring remotes.
 */
fun Modifier.tvHomeVerticalRowNav(
    onMoveDown: () -> Boolean,
    onMoveUp: () -> Boolean,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionDown -> onMoveDown()
        Key.DirectionUp -> onMoveUp()
        else -> false
    }
}

fun Modifier.tvHomeHeroVerticalNav(
    firstRowRequester: FocusRequester?,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown) return@onPreviewKeyEvent false
    firstRowRequester?.let { requester ->
        runCatching { requester.requestFocus() }.isSuccess
    } ?: false
}

fun sanitizeHomeBlurb(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val trimmed = text.trim()
    if (trimmed.startsWith("poster:", ignoreCase = true)) return null
    if (trimmed.startsWith("Resume watching", ignoreCase = true)) return null
    if (trimmed.length < 4) return null
    return trimmed
}

/**
 * Shared UI components and design system for the CalmSource Android TV app.
 *
 * Contains reusable composable building blocks and the color palette used
 * across all TV screens:
 * - [TvColors] — Color palette for the TV dark theme
 * - [TvFocusCard] — D-pad-focusable card with animated scale and border highlight
 * - [TvSourceBadge] — Colored badge showing source type (IPTV / Extension / Debrid)
 *
 * All TV screens import these components for consistent visual styling
 * and D-pad navigation behavior.
 */
package com.example.calmsource.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.model.SourceType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.LocalTextStyle

/**
 * Color palette for the CalmSource Android TV dark theme.
 *
 * Provides consistent dark-mode tokens: background, surface, focus border
 * (purple accent), and text colors (main, sub).
 */
object TvColors {
    val Background = Color(0xFF0F0E17)
    val Surface = Color(0x1F2A283E)
    val BorderFocused = Color(0xFF8B5CF6)
    val TextMain = Color(0xFFFFFEFE)
    val TextSub = Color(0xFFA7A9BE)
}

/**
 * D-pad-focusable card container with animated scale and border highlight.
 *
 * Core building block for all TV-optimized interactive elements. When the card
 * receives D-pad focus, it scales up by 1.08× via [animateFloatAsState] and
 * displays a purple [TvColors.BorderFocused] border.
 *
 * @param modifier Modifier applied to the outer card container.
 * @param onClick Callback invoked when the user presses D-pad Center/Enter.
 * @param content Column-scoped content block that receives a [Boolean] indicating
 *   whether the card is currently focused, enabling focus-dependent styling.
 */
@Composable
fun TvFocusCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.(Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scaleFactor by animateFloatAsState(if (isFocused) 1.08f else 1.0f, label = "card_zoom")

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scaleFactor; scaleY = scaleFactor }
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged?.invoke(it.isFocused)
            }
            .clip(RoundedCornerShape(12.dp))
            .background(TvColors.Surface)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) TvColors.BorderFocused else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .focusable()
            .padding(12.dp)
    ) {
        content(isFocused)
    }
}

/**
 * Colored badge chip indicating the source type of a watch option (TV variant).
 *
 * Background and text colors are determined by the [SourceType]:
 * - [SourceType.IPTV] → green
 * - [SourceType.EXTENSION] → blue
 * - [SourceType.DEBRID] → amber/yellow
 *
 * @param type The [SourceType] to render.
 */
@Composable
fun TvSourceBadge(type: SourceType) {
    val bgColor = when (type) {
        SourceType.IPTV -> Color(0x3D10B981)
        SourceType.EXTENSION -> Color(0x3D3B82F6)
        SourceType.DEBRID -> Color(0x3DF59E0B)
    }
    val textColor = when (type) {
        SourceType.IPTV -> Color(0xFF34D399)
        SourceType.EXTENSION -> Color(0xFF60A5FA)
        SourceType.DEBRID -> Color(0xFFFBBF24)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = type.name,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * TV-optimized text field that intercepts D-pad up/down events to prevent focus traps.
 *
 * Starts in read-only mode so the soft keyboard does NOT auto-open when the field
 * receives D-pad focus. The keyboard only appears after the user presses Enter/OK
 * (DPAD_CENTER), giving a "click-to-edit" behavior that matches TV UX conventions.
 * When focus is lost, the field returns to read-only mode.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onSearchAction: (() -> Unit)? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Start read-only so the keyboard doesn't pop up on D-pad focus
    var isEditing by remember { mutableStateOf(false) }

    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        singleLine = singleLine,
        readOnly = !isEditing,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = textStyle,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = TvColors.Surface,
            unfocusedContainerColor = TvColors.Surface,
            focusedIndicatorColor = TvColors.BorderFocused,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = TvColors.TextMain,
            unfocusedTextColor = TvColors.TextMain,
            cursorColor = TvColors.BorderFocused
        ),
        modifier = modifier
            .onFocusChanged { focusState ->
                if (!focusState.isFocused && isEditing) {
                    // Lost focus → exit editing mode, hide keyboard
                    isEditing = false
                    keyboardController?.hide()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key.nativeKeyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER -> {
                            if (!isEditing) {
                                // Enter editing mode and show keyboard
                                isEditing = true
                                keyboardController?.show()
                                true
                            } else if (onSearchAction != null) {
                                onSearchAction()
                                true
                            } else {
                                false
                            }
                        }
                        android.view.KeyEvent.KEYCODE_BACK,
                        android.view.KeyEvent.KEYCODE_ESCAPE -> {
                            if (isEditing) {
                                isEditing = false
                                keyboardController?.hide()
                                true
                            } else {
                                false
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!isEditing) {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!isEditing) {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!isEditing) {
                                focusManager.moveFocus(FocusDirection.Left)
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!isEditing) {
                                focusManager.moveFocus(FocusDirection.Right)
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_SEARCH -> {
                            if (onSearchAction != null) {
                                onSearchAction()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else false
            }
    )
}

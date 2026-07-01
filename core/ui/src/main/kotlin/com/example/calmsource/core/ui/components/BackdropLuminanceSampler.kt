package com.example.calmsource.core.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Samples backdrop luminance from a hidden 1dp image load.
 * Prefer passing [onLuminance] from [Hero] when the hero already loads the same URL.
 */
@Composable
fun BackdropLuminanceSampler(
    imageUrl: String?,
    onLuminance: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (imageUrl.isNullOrBlank()) return
    val scope = rememberCoroutineScope()
    val onLuminanceState = rememberUpdatedState(onLuminance)
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(1.dp),
        onSuccess = { state ->
            val drawable = state.result.drawable
            if (drawable !is BitmapDrawable) return@AsyncImage
            Palette.from(drawable.bitmap).generate { palette ->
                val dominantColor = palette?.getDominantColor(0xFF000000.toInt()) ?: 0xFF000000.toInt()
                val r = android.graphics.Color.red(dominantColor) / 255f
                val g = android.graphics.Color.green(dominantColor) / 255f
                val b = android.graphics.Color.blue(dominantColor) / 255f
                val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
                scope.launch {
                    onLuminanceState.value(luminance)
                }
            }
        },
    )
}

fun dominantColorLuminance(color: Int): Float {
    val r = android.graphics.Color.red(color) / 255f
    val g = android.graphics.Color.green(color) / 255f
    val b = android.graphics.Color.blue(color) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

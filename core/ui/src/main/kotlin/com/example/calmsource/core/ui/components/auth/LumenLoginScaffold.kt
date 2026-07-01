package com.example.calmsource.core.ui.components.auth

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType
import com.example.calmsource.core.ui.theme.borderStrong
import com.example.calmsource.core.ui.theme.size4
import com.example.calmsource.core.ui.theme.surface
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun LumenLoginScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLumenTokens.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        t.colors.surface.copy(alpha = 0.35f),
                        t.colors.background,
                        Color.Black,
                    ),
                    radius = 1200f,
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LumenTokens.Space.lg, vertical = LumenTokens.Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LumenLoginBrand()
            Spacer(modifier = Modifier.height(LumenTokens.Space.xl))
            Text(
                text = title,
                style = LumenType.H1.toTextStyle(),
                color = t.colors.foreground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(LumenTokens.Space.s3))
            Text(
                text = subtitle,
                style = LumenType.Body.toTextStyle(),
                color = t.colors.mutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = LumenTokens.Space.md),
            )
            Spacer(modifier = Modifier.height(LumenTokens.Space.lg))
            content()
        }
    }
}

@Composable
fun LumenLoginBrand(modifier: Modifier = Modifier) {
    val t = LocalLumenTokens.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            t.colors.foreground.copy(alpha = 0.92f),
                            t.colors.brand.copy(alpha = 0.55f),
                        )
                    )
                )
                .border(1.dp, t.colors.borderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(t.colors.background.copy(alpha = 0.85f))
            )
        }
        Spacer(modifier = Modifier.height(LumenTokens.Space.s3))
        Text(
            text = "CALMSOURCE",
            style = LumenType.Eyebrow.toTextStyle(),
            color = t.colors.mutedForeground,
        )
    }
}

@Composable
fun LumenLoginCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLumenTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LumenTokens.Shape.lg)
            .background(t.colors.surface.copy(alpha = 0.72f))
            .border(1.dp, t.colors.border, LumenTokens.Shape.lg)
            .padding(LumenTokens.Space.s7),
        content = content,
    )
}

@Composable
fun LumenLoginModeRow(
    modes: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s3),
    ) {
        modes.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(t.radii.pill))
                    .background(if (selected) t.colors.foreground else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (selected) t.colors.foreground else t.colors.border,
                        shape = RoundedCornerShape(t.radii.pill),
                    )
                    .clickable { onSelected(index) }
                    .padding(vertical = LumenTokens.Space.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = LumenType.Meta.toTextStyle().copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) t.colors.background else t.colors.mutedForeground,
                )
            }
        }
    }
}

@Composable
fun LumenAuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
) {
    val t = LocalLumenTokens.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (isPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        modifier = modifier.fillMaxWidth(),
        shape = LumenTokens.Shape.md,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = t.colors.foreground.copy(alpha = 0.45f),
            unfocusedBorderColor = t.colors.border,
            focusedLabelColor = t.colors.mutedForeground,
            unfocusedLabelColor = t.colors.mutedForeground,
            cursorColor = t.colors.brand,
            focusedTextColor = t.colors.foreground,
            unfocusedTextColor = t.colors.foreground,
        ),
    )
}

@Composable
fun LumenQrCodeFrame(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    contentDescription: String = "QR code",
) {
    val t = LocalLumenTokens.current
    val bitmap = remember(content, size) {
        generateQrCodeBitmap(content, (size.value * 2.5f).toInt())
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(LumenTokens.Shape.lg)
            .background(Color.White)
            .border(1.dp, t.colors.borderStrong, LumenTokens.Shape.lg)
            .padding(LumenTokens.Space.s5),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "QR unavailable",
                style = LumenType.Caption.toTextStyle(),
                color = Color.Black.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
fun LumenPinDisplay(
    pin: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    Text(
        text = pin,
        style = LumenType.Display.toTextStyle().copy(fontWeight = FontWeight.Light),
        color = t.colors.foreground,
        letterSpacing = LumenType.size4,
        modifier = modifier,
    )
}

fun generateQrCodeBitmap(content: String, pixelSize: Int = 512): ImageBitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, pixelSize, pixelSize)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

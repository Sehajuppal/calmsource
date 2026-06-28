package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text as TvText
import com.example.calmsource.core.network.BuildConfig
import io.ktor.http.Url
import java.net.URLEncoder

private fun pairingSetupPageUrl(pin: String, publicKey: String): String {
    val wsUrl = Url(BuildConfig.WS_AUTH_URL)
    val scheme = if (wsUrl.protocol.name == "wss") "https" else "http"
    val port = wsUrl.port.takeIf { it > 0 } ?: wsUrl.protocol.defaultPort
    val encodedPin = URLEncoder.encode(pin, Charsets.UTF_8.name())
    val encodedKey = URLEncoder.encode(publicKey, Charsets.UTF_8.name())
    return "$scheme://${wsUrl.host}:$port/setup?pin=$encodedPin&key=$encodedKey"
}

fun generateQrCode(content: String, size: Int = 512): ImageBitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

@Composable
fun TvOnboardingScreen(
    onComplete: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val t = LocalLumenTokens.current
    val state by viewModel.state.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelPairing()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startPairing()
    }

    LaunchedEffect(state) {
        if (state is PairingState.Success) {
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(LumenLayout.iconXl),
        contentAlignment = Alignment.Center
    ) {
        when (val currentState = state) {
            is PairingState.Idle, is PairingState.Connecting -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = t.colors.brand)
                    Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
                    Text(
                        text = "Connecting to pairing server...",
                        color = t.colors.foreground,
                        fontSize = LumenType.size18
                    )
                }
            }
            is PairingState.Decrypting -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = t.colors.brand)
                    Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
                    Text(
                        text = "Decrypting and saving credentials...",
                        color = t.colors.foreground,
                        fontSize = LumenType.size18
                    )
                }
            }
            is PairingState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Pairing Error",
                        color = Color.Red,
                        fontSize = LumenType.size24,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(LumenLegacySpace.md))
                    Text(
                        text = currentState.message,
                        color = t.colors.mutedForeground,
                        fontSize = LumenType.size16
                    )
                    Spacer(modifier = Modifier.height(LumenLegacySpace.xxl))
                    Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)) {
                        TvFocusCard(onClick = { viewModel.startPairing() }) {
                            Text("Retry", color = t.colors.foreground, fontSize = LumenType.size16)
                        }
                        Button(
                            onClick = { viewModel.skipAuthentication() },
                            colors = ButtonDefaults.colors(
                                containerColor = t.colors.surface,
                                contentColor = t.colors.mutedForeground,
                                focusedContainerColor = t.colors.brand,
                                focusedContentColor = t.colors.foreground
                            )
                        ) {
                            TvText(
                                text = "Skip for now",
                                fontSize = LumenType.size16,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            is PairingState.ShowPin -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Instructions & Back button
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = LumenLegacySpace.xxxl),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Pair with your device",
                            color = t.colors.foreground,
                            fontSize = LumenType.size32,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
                        Text(
                            text = "Scan the QR code on your phone, or open calmsource.tv/setup on your device, and enter the PIN code displayed on the right.",
                            color = t.colors.mutedForeground,
                            fontSize = LumenType.size18,
                            lineHeight = LumenType.size26
                        )
                    }

                    // Right Column: QR code & PIN
                    Column(
                        modifier = Modifier
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val qrCodeUrl = remember(currentState.pin, currentState.publicKey) {
                            pairingSetupPageUrl(currentState.pin, currentState.publicKey)
                        }
                        val qrBitmap = remember(qrCodeUrl) {
                            generateQrCode(qrCodeUrl)
                        }

                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(LumenLayout.detailsContentTop)
                                    .background(LumenTokens.Color.textPrimary, shape = LumenTokens.Shape.sm)
                                    .padding(LumenLegacySpace.md)
                            ) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "Pairing QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(LumenLayout.detailsContentTop)
                                    .background(Color.Gray, shape = LumenTokens.Shape.sm),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Failed to generate QR Code", color = LumenTokens.Color.textPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(LumenLegacySpace.xxl))

                        Text(
                            text = "PIN CODE",
                            color = t.colors.mutedForeground,
                            fontSize = LumenType.size14,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))
                        Text(
                            text = currentState.pin,
                            color = t.colors.foreground,
                            fontSize = LumenType.size48,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = LumenType.size4
                        )

                        Spacer(modifier = Modifier.height(LumenLegacySpace.xxxl))

                        Button(
                            onClick = { viewModel.skipAuthentication() },
                            colors = ButtonDefaults.colors(
                                containerColor = t.colors.surface,
                                contentColor = t.colors.mutedForeground,
                                focusedContainerColor = t.colors.brand,
                                focusedContentColor = t.colors.foreground
                            )
                        ) {
                            TvText(
                                text = "Skip for now",
                                fontSize = LumenType.size16,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            PairingState.Success -> {
                // Success: Transition handled by LaunchedEffect
            }
        }
    }
}

package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text as TvText
import com.example.calmsource.core.network.BuildConfig
import com.example.calmsource.core.network.PairingSetupUrl
import com.example.calmsource.core.ui.components.auth.LumenLoginBrand
import com.example.calmsource.core.ui.components.auth.LumenLoginCard
import com.example.calmsource.core.ui.components.auth.LumenLoginScaffold
import com.example.calmsource.core.ui.components.auth.LumenPinDisplay
import com.example.calmsource.core.ui.components.auth.LumenQrCodeFrame

@Composable
fun TvOnboardingScreen(
    onComplete: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val t = LocalLumenTokens.current
    val state by viewModel.state.collectAsState()

    DisposableEffect(Unit) {
        onDispose { viewModel.cancelPairing() }
    }

    LaunchedEffect(Unit) {
        viewModel.startPairing()
    }

    LaunchedEffect(state) {
        if (state is PairingState.Success) {
            onComplete()
        }
    }

    when (val currentState = state) {
        is PairingState.Idle, is PairingState.Connecting -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LumenLayout.iconXl),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = t.colors.brand)
                    Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
                    Text(
                        text = "Preparing secure sign-in…",
                        color = t.colors.foreground,
                        style = LumenType.Body.toTextStyle(),
                    )
                }
            }
        }
        is PairingState.Decrypting -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = t.colors.brand)
                    Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
                    Text(
                        text = "Securing your sources…",
                        color = t.colors.foreground,
                        style = LumenType.Body.toTextStyle(),
                    )
                }
            }
        }
        is PairingState.Error -> {
            LumenLoginScaffold(
                title = "Sign-in interrupted",
                subtitle = currentState.message,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                ) {
                    TvFocusCard(onClick = { viewModel.startPairing() }) {
                        Text("Try again", color = t.colors.foreground, style = LumenType.Body.toTextStyle())
                    }
                    Button(
                        onClick = { viewModel.skipAuthentication() },
                        colors = ButtonDefaults.colors(
                            containerColor = t.colors.surface,
                            contentColor = t.colors.mutedForeground,
                            focusedContainerColor = t.colors.foreground,
                            focusedContentColor = t.colors.background,
                        ),
                    ) {
                        TvText(text = "Continue without signing in", style = LumenType.Body.toTextStyle())
                    }
                }
            }
        }
        is PairingState.ShowPin -> {
            val qrCodeUrl = remember(currentState.pin, currentState.publicKey) {
                PairingSetupUrl.build(currentState.pin, currentState.publicKey, BuildConfig.WS_AUTH_URL)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = LumenTokens.Space.sidePaddingTv, vertical = LumenLegacySpace.xxxl),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.xxxl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        LumenLoginBrand()
                        Spacer(modifier = Modifier.height(LumenLegacySpace.xxl))
                        Text(
                            text = "Sign in to CalmSource",
                            style = LumenType.H1.toTextStyle(),
                            color = t.colors.foreground,
                        )
                        Spacer(modifier = Modifier.height(LumenLegacySpace.md))
                        Text(
                            text = "Scan the code with CalmSource on your phone to send your sources securely, or enter the PIN manually in the mobile app under TV Pair.",
                            style = LumenType.Body.toTextStyle(),
                            color = t.colors.mutedForeground,
                        )
                        Spacer(modifier = Modifier.height(LumenLegacySpace.xxxl))
                        Button(
                            onClick = { viewModel.skipAuthentication() },
                            colors = ButtonDefaults.colors(
                                containerColor = t.colors.surface,
                                contentColor = t.colors.mutedForeground,
                                focusedContainerColor = t.colors.foreground,
                                focusedContentColor = t.colors.background,
                            ),
                        ) {
                            TvText(text = "Continue without signing in", style = LumenType.Body.toTextStyle())
                        }
                    }
                    LumenLoginCard(
                        modifier = Modifier
                            .weight(0.9f)
                            .widthIn(max = 420.dp),
                    ) {
                        Text(
                            text = "MOBILE SIGN-IN",
                            style = LumenType.Eyebrow.toTextStyle(),
                            color = t.colors.mutedForeground,
                        )
                        Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            LumenQrCodeFrame(
                                content = qrCodeUrl,
                                size = 240.dp,
                                contentDescription = "TV sign-in QR code",
                            )
                        }
                        Spacer(modifier = Modifier.height(LumenLegacySpace.xl))
                        Text(
                            text = "PIN",
                            style = LumenType.Eyebrow.toTextStyle(),
                            color = t.colors.mutedForeground,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(LumenLegacySpace.sm))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            LumenPinDisplay(pin = currentState.pin)
                        }
                    }
                }
            }
        }
        PairingState.Success -> Unit
    }
}

package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.focusRequester
import com.example.calmsource.core.model.DebridAccount
import com.example.calmsource.core.model.DebridProviderType
import com.example.calmsource.core.model.DebridAccountHealth
import com.example.calmsource.feature.debrid.DebridRepository
import kotlinx.coroutines.launch

@Composable
fun TvDebridScreen(onBack: () -> Unit) {
    val t = LocalLumenTokens.current
    val stableFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            stableFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }
    val accounts by DebridRepository.accounts.collectAsState()
    var selectedAccountForConfig by remember { mutableStateOf<DebridAccount?>(null) }
    var selectedProviderForConnect by remember { mutableStateOf<DebridProviderType?>(null) }

    androidx.activity.compose.BackHandler(enabled = selectedProviderForConnect != null || selectedAccountForConfig != null) {
        if (selectedProviderForConnect != null) {
            selectedProviderForConnect = null
            runCatching { stableFocusRequester.requestFocus() }
        } else if (selectedAccountForConfig != null) {
            selectedAccountForConfig = null
            runCatching { stableFocusRequester.requestFocus() }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(LumenLegacySpace.xxl),
        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.xxl)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
        ) {
            item {
                TvFocusCard(
                    onClick = onBack, 
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(bottom = LumenLegacySpace.sm2)
                        .focusRequester(stableFocusRequester)
                ) {
                    Text(text = "← Back", color = t.colors.foreground)
                }
            }
            item {
                Text(text = "Debrid Connect", style = lumenH2Style(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
                Text(text = "Connect premium debrid servers for source availability", style = lumenCaptionStyle(), color = t.colors.mutedForeground, modifier = Modifier.padding(bottom = LumenLegacySpace.md))
            }

            item {
                Text(text = "Connect Provider", style = lumenBodyStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
            }
            items(DebridProviderType.values().filter { it != DebridProviderType.FAKE_DEMO }) { type ->
                val name = when (type) {
                    DebridProviderType.REAL_DEBRID -> "Real-Debrid"
                    DebridProviderType.ALL_DEBRID -> "AllDebrid"
                    DebridProviderType.PREMIUMIZE -> "Premiumize"
                    else -> type.name
                }
                TvFocusCard(
                    onClick = {
                        selectedAccountForConfig = null
                        selectedProviderForConnect = type
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { isFocused ->
                    Text(
                        text = "Connect $name",
                        color = if (isFocused) t.colors.background else t.colors.brand,
                        fontWeight = FontWeight.Bold,
                        style = lumenCaptionStyle()
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
            }

            item {
                Text(text = "Connected Accounts", style = lumenBodyStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
            }
            val connectedAccounts = accounts.filter { it.isConnected }
            if (connectedAccounts.isEmpty()) {
                item {
                    Text(text = "No accounts connected.", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                }
            } else {
                items(connectedAccounts, key = { it.id }) { acc ->
                    val isSelected = selectedAccountForConfig?.id == acc.id
                    TvFocusCard(
                        onClick = {
                            selectedProviderForConnect = null
                            selectedAccountForConfig = acc
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { isFocused ->
                        val healthColor = when (acc.health) {
                            DebridAccountHealth.HEALTHY -> LumenExtendedColors.statusHealthy
                            DebridAccountHealth.SLOW -> LumenTokens.Color.warning
                            DebridAccountHealth.FAILED -> LumenExtendedColors.errorBright
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = acc.providerName,
                                    color = if (isSelected || isFocused) t.colors.foreground else t.colors.mutedForeground,
                                    style = lumenBodyStyle(),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Status: Ready",
                                    color = LumenExtendedColors.statusHealthy,
                                    style = lumenCaptionStyle(),
                                    modifier = Modifier.padding(top = LumenLegacySpace.xxs)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(LumenTokens.Shape.md)
                                    .background(healthColor.copy(alpha = 0.2f))
                                    .padding(horizontal = LumenLegacySpace.sm, vertical = LumenLegacySpace.xxs)
                            ) {
                                Text(
                                    text = acc.health.name,
                                    color = healthColor,
                                    style = LumenType.Eyebrow.toTextStyle(lumenTextScale()),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight()
                .background(t.colors.surface.copy(alpha = 0.5f), LumenTokens.Shape.md)
                .padding(LumenLegacySpace.lg),
            verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
        ) {
            if (selectedProviderForConnect != null) {
                item {
                    TvDebridConnectFlow(
                        providerType = selectedProviderForConnect!!,
                        stableFocusRequester = stableFocusRequester,
                        onConnected = {
                            stableFocusRequester.requestFocus()
                            selectedProviderForConnect = null
                        }
                    )
                }
            } else if (selectedAccountForConfig != null) {
                item {
                    TvDebridAccountDetails(
                        account = selectedAccountForConfig!!,
                        onDisconnect = {
                            stableFocusRequester.requestFocus()
                            DebridRepository.disconnectAccount(selectedAccountForConfig!!.id)
                            selectedAccountForConfig = null
                        }
                    )
                }
            } else {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Select a provider to connect or account to configure", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                    }
                }
            }
        }
    }
}

@Composable
fun TvDebridConnectFlow(
    providerType: DebridProviderType,
    stableFocusRequester: androidx.compose.ui.focus.FocusRequester,
    onConnected: () -> Unit
) {
    val t = LocalLumenTokens.current
    val coroutineScope = rememberCoroutineScope()
    var authSession by remember { mutableStateOf<com.example.calmsource.core.model.DebridAuthSession?>(null) }
    var apiKeyInput by remember { mutableStateOf("") }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var isPolling by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableStateOf(0) }

    val debridActionFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(authSession) {
        if (authSession != null) {
            debridActionFocusRequester.requestFocus()
        }
    }

    val providerName = when (providerType) {
        DebridProviderType.REAL_DEBRID -> "Real-Debrid"
        DebridProviderType.ALL_DEBRID -> "AllDebrid"
        DebridProviderType.PREMIUMIZE -> "Premiumize"
        else -> "Demo"
    }

    LaunchedEffect(providerType, retryTrigger) {
        if (providerType != DebridProviderType.PREMIUMIZE) {
            try {
                authSession = DebridRepository.startConnectionFlow(providerType)
            } catch (e: Exception) {
                connectionError = (e.message ?: "Failed to initiate authentication session")
                    .replace(Regex("(token|key|secret|code|pin|password|apikey|api_key)\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE), "$1=***")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)) {
        Text(text = "Connect to $providerName", style = lumenTitleStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)

        if (connectionError != null) {
            Text(text = "Connection failed: $connectionError", color = LumenExtendedColors.errorBright, style = lumenCaptionStyle())
            val retryFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(connectionError) {
                retryFocusRequester.requestFocus()
            }
            TvFocusCard(
                onClick = {
                    stableFocusRequester.requestFocus()
                    connectionError = null
                    authSession = null
                    retryTrigger++
                },
                modifier = Modifier.fillMaxWidth().focusRequester(retryFocusRequester)
            ) { isFocused ->
                Text(
                    text = "Retry",
                    color = if (isFocused) t.colors.background else t.colors.foreground,
                    fontWeight = FontWeight.Bold,
                    style = lumenCaptionStyle(),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        } else if (providerType == DebridProviderType.PREMIUMIZE) {
            Text(text = "Please connect via API Key (Premiumize API Key flow to be implemented).", color = t.colors.mutedForeground, style = lumenCaptionStyle())
        } else if (authSession != null) {
            when (val session = authSession!!) {
                is com.example.calmsource.core.model.DebridAuthSession.DeviceCode -> {
                    Text(text = "Please authorize using the code below from your couch:", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LumenTokens.Color.glass, LumenTokens.Shape.sm)
                            .padding(LumenLegacySpace.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = session.details.userCode,
                            fontSize = LumenType.size36,
                            fontWeight = FontWeight.Bold,
                            color = t.colors.brand
                        )
                    }

                    Text(text = "1. Scan QR code or open: ${session.details.verificationUrl}", color = t.colors.foreground, style = lumenCaptionStyle())
                    Text(text = "2. Enter the large code shown above.", color = t.colors.foreground, style = lumenCaptionStyle())

                    Box(
                        modifier = Modifier
                            .size(LumenLayout.epgMinBlockWidth)
                            .background(LumenTokens.Color.textPrimary, LumenTokens.Shape.md)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "[QR Code]", color = LumenTokens.Color.bg, style = lumenCaptionStyle(), fontWeight = FontWeight.Bold)
                    }

                    TvFocusCard(
                        onClick = {
                            if (!isPolling) {
                                isPolling = true
                                coroutineScope.launch {
                                    try {
                                        DebridRepository.completeConnectionFlow(providerType, authSession!!)
                                        onConnected()
                                    } catch (e: Exception) {
                                        connectionError = (e.message ?: "Authentication polling timed out or expired")
                                            .replace(Regex("(token|key|secret|code|pin|password|apikey|api_key)\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE), "$1=***")
                                        isPolling = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(debridActionFocusRequester)
                    ) { isFocused ->
                        Text(
                            text = if (isPolling) "Connecting..." else "I have authorized on browser",
                            color = if (isFocused) t.colors.background else t.colors.foreground,
                            fontWeight = FontWeight.Bold,
                            style = lumenCaptionStyle(),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
                is com.example.calmsource.core.model.DebridAuthSession.Pin -> {
                    Text(text = "Please enter this PIN code at AllDebrid authorization page:", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LumenTokens.Color.glass, LumenTokens.Shape.sm)
                            .padding(LumenLegacySpace.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = session.details.pinCode,
                            fontSize = LumenType.size36,
                            fontWeight = FontWeight.Bold,
                            color = t.colors.brand
                        )
                    }
                    Text(text = "Enter pin code at: ${session.details.pinUrl}", color = t.colors.foreground, style = lumenCaptionStyle())

                    TvFocusCard(
                        onClick = {
                            if (!isPolling) {
                                isPolling = true
                                coroutineScope.launch {
                                    try {
                                        DebridRepository.completeConnectionFlow(providerType, authSession!!)
                                        onConnected()
                                    } catch (e: Exception) {
                                        connectionError = (e.message ?: "PIN authorization timed out")
                                            .replace(Regex("(token|key|secret|code|pin|password|apikey|api_key)\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE), "$1=***")
                                        isPolling = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(debridActionFocusRequester)
                    ) { isFocused ->
                        Text(
                            text = if (isPolling) "Connecting..." else "I have authorized PIN",
                            color = if (isFocused) t.colors.background else t.colors.foreground,
                            fontWeight = FontWeight.Bold,
                            style = lumenCaptionStyle(),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
                is com.example.calmsource.core.model.DebridAuthSession.ApiKey -> {
                    Text(text = "Connect with API Key.", color = t.colors.mutedForeground, style = lumenCaptionStyle())
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Initializing auth session...", color = t.colors.mutedForeground)
            }
        }
    }
}

@Composable
fun TvDebridAccountDetails(
    account: DebridAccount,
    onDisconnect: () -> Unit
) {
    val t = LocalLumenTokens.current
    var showRawJson by remember { mutableStateOf(false) }
    val status = account.status
    val healthColor = when (account.health) {
        DebridAccountHealth.HEALTHY -> LumenExtendedColors.statusHealthy
        DebridAccountHealth.SLOW -> LumenTokens.Color.warning
        DebridAccountHealth.FAILED -> LumenExtendedColors.errorBright
    }

    val maskedToken = remember(account) {
        val raw = account.tokenSet?.accessToken ?: account.tokenSet?.apiKey ?: ""
        if (raw.length > 8) raw.take(4) + "..." + raw.takeLast(4) else "••••••••"
    }

    Text(text = account.providerName, style = lumenTitleStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground)
    Text(text = "Status: Ready", style = lumenCaptionStyle(), color = LumenExtendedColors.statusHealthy)
    
    Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Account Health:", color = t.colors.mutedForeground, style = lumenCaptionStyle())
        Box(
            modifier = Modifier
                .clip(LumenTokens.Shape.md)
                .background(healthColor.copy(alpha = 0.2f))
                .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xs)
        ) {
            Text(
                text = account.health.name,
                color = healthColor,
                style = LumenType.Meta.toTextStyle(lumenTextScale()),
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (status != null) {
        Text(text = "Username: ${status.username}", color = t.colors.foreground, style = lumenCaptionStyle())
        Text(text = "Email: ${status.email}", color = t.colors.foreground, style = lumenCaptionStyle())
        Text(text = "Premium Days: ${status.premiumDaysRemaining} remaining", color = t.colors.brand, style = lumenCaptionStyle(), fontWeight = FontWeight.Bold)
    }

    Text(text = "Auth Key/Token: $maskedToken", color = t.colors.mutedForeground, style = lumenCaptionStyle())

    TvFocusCard(
        onClick = { showRawJson = !showRawJson },
        modifier = Modifier.fillMaxWidth()
    ) { isFocused ->
        Text(
            text = if (showRawJson) "Hide raw response" else "Advanced details",
            color = if (isFocused) t.colors.background else t.colors.foreground,
            style = lumenCaptionStyle(),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    if (showRawJson) {
        val rawJson = """
        {
          "provider": "${account.providerType.name}",
          "isConnected": ${account.isConnected},
          "health": "${account.health.name}",
          "rawResponse": {
            "username": "${status?.username ?: "N/A"}",
            "email": "${status?.email ?: "N/A"}",
            "premium": ${status?.isPremium ?: false},
            "days": ${status?.premiumDaysRemaining ?: 0}
          }
        }
        """.trimIndent()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(LumenExtendedColors.debridPanel, LumenTokens.Shape.sm)
                .padding(LumenLegacySpace.sm2)
        ) {
            Text(
                text = rawJson,
                color = LumenTokens.Color.success,
                style = LumenType.Meta.toTextStyle(lumenTextScale()),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }

    TvFocusCard(
        onClick = onDisconnect,
        modifier = Modifier.fillMaxWidth()
    ) { isFocused ->
        Text(
            text = "Disconnect Account",
            color = if (isFocused) LumenTokens.Color.textPrimary else LumenExtendedColors.errorBright,
            fontWeight = FontWeight.Bold,
            style = lumenCaptionStyle(),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

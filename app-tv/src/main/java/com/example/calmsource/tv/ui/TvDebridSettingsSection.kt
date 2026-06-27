package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.LumenTokens

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
    val stableFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val accounts by DebridRepository.accounts.collectAsState()
    var selectedAccountForConfig by remember { mutableStateOf<DebridAccount?>(null) }
    var selectedProviderForConnect by remember { mutableStateOf<DebridProviderType?>(null) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(LumenTokens.Space.xxl),
        horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.xxl)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.md)
        ) {
            item {
                TvFocusCard(
                    onClick = onBack, 
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(bottom = LumenTokens.Space.sm2)
                        .focusRequester(stableFocusRequester)
                ) {
                    Text(text = "← Back", color = TvColors.TextMain)
                }
            }
            item {
                Text(text = "Debrid Connect", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain)
                Text(text = "Connect premium debrid servers for source availability", fontSize = 12.sp, color = TvColors.TextSub, modifier = Modifier.padding(bottom = LumenTokens.Space.md))
            }

            item {
                Text(text = "Connect Provider", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain)
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
                        color = if (isFocused) TvColors.Background else TvColors.BorderFocused,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(LumenTokens.Space.lg))
            }

            item {
                Text(text = "Connected Accounts", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain)
            }
            val connectedAccounts = accounts.filter { it.isConnected }
            if (connectedAccounts.isEmpty()) {
                item {
                    Text(text = "No accounts connected.", color = TvColors.TextSub, fontSize = 14.sp)
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
                            DebridAccountHealth.HEALTHY -> LumenTokens.Color.statusHealthy
                            DebridAccountHealth.SLOW -> LumenTokens.Color.warning
                            DebridAccountHealth.FAILED -> LumenTokens.Color.errorBright
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = acc.providerName,
                                    color = if (isSelected || isFocused) TvColors.TextMain else TvColors.TextSub,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Status: Ready",
                                    color = LumenTokens.Color.statusHealthy,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = LumenTokens.Space.xxs)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(LumenTokens.Shape.md)
                                    .background(healthColor.copy(alpha = 0.2f))
                                    .padding(horizontal = LumenTokens.Space.sm, vertical = LumenTokens.Space.xxs)
                            ) {
                                Text(
                                    text = acc.health.name,
                                    color = healthColor,
                                    fontSize = 10.sp,
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
                .background(TvColors.Surface.copy(alpha = 0.5f), LumenTokens.Shape.md)
                .padding(LumenTokens.Space.lg),
            verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.lg)
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
                        Text(text = "Select a provider to connect or account to configure", color = TvColors.TextSub, fontSize = 14.sp)
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

    Column(verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.lg)) {
        Text(text = "Connect to $providerName", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain)

        if (connectionError != null) {
            Text(text = "Connection failed: $connectionError", color = LumenTokens.Color.errorBright, fontSize = 14.sp)
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
                    color = if (isFocused) TvColors.Background else TvColors.TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        } else if (providerType == DebridProviderType.PREMIUMIZE) {
            Text(text = "Please connect via API Key (Premiumize API Key flow to be implemented).", color = TvColors.TextSub, fontSize = 14.sp)
        } else if (authSession != null) {
            when (val session = authSession!!) {
                is com.example.calmsource.core.model.DebridAuthSession.DeviceCode -> {
                    Text(text = "Please authorize using the code below from your couch:", color = TvColors.TextSub, fontSize = 14.sp)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LumenTokens.Color.glassOverlay, LumenTokens.Shape.sm)
                            .padding(LumenTokens.Space.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = session.details.userCode,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = TvColors.BorderFocused
                        )
                    }

                    Text(text = "1. Scan QR code or open: ${session.details.verificationUrl}", color = TvColors.TextMain, fontSize = 14.sp)
                    Text(text = "2. Enter the large code shown above.", color = TvColors.TextMain, fontSize = 14.sp)

                    Box(
                        modifier = Modifier
                            .size(LumenTokens.Layout.epgMinBlockWidth)
                            .background(LumenTokens.Color.textPrimary, LumenTokens.Shape.md)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "[QR Code]", color = LumenTokens.Color.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            color = if (isFocused) TvColors.Background else TvColors.TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
                is com.example.calmsource.core.model.DebridAuthSession.Pin -> {
                    Text(text = "Please enter this PIN code at AllDebrid authorization page:", color = TvColors.TextSub, fontSize = 14.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LumenTokens.Color.glassOverlay, LumenTokens.Shape.sm)
                            .padding(LumenTokens.Space.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = session.details.pinCode,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = TvColors.BorderFocused
                        )
                    }
                    Text(text = "Enter pin code at: ${session.details.pinUrl}", color = TvColors.TextMain, fontSize = 14.sp)

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
                            color = if (isFocused) TvColors.Background else TvColors.TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
                is com.example.calmsource.core.model.DebridAuthSession.ApiKey -> {
                    Text(text = "Connect with API Key.", color = TvColors.TextSub, fontSize = 14.sp)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusable(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Initializing auth session...", color = TvColors.TextSub)
            }
        }
    }
}

@Composable
fun TvDebridAccountDetails(
    account: DebridAccount,
    onDisconnect: () -> Unit
) {
    var showRawJson by remember { mutableStateOf(false) }
    val status = account.status
    val healthColor = when (account.health) {
        DebridAccountHealth.HEALTHY -> LumenTokens.Color.statusHealthy
        DebridAccountHealth.SLOW -> LumenTokens.Color.warning
        DebridAccountHealth.FAILED -> LumenTokens.Color.errorBright
    }

    val maskedToken = remember(account) {
        val raw = account.tokenSet?.accessToken ?: account.tokenSet?.apiKey ?: ""
        if (raw.length > 8) raw.take(4) + "..." + raw.takeLast(4) else "••••••••"
    }

    Text(text = account.providerName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain)
    Text(text = "Status: Ready", fontSize = 14.sp, color = LumenTokens.Color.statusHealthy)
    
    Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm2), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Account Health:", color = TvColors.TextSub, fontSize = 13.sp)
        Box(
            modifier = Modifier
                .clip(LumenTokens.Shape.md)
                .background(healthColor.copy(alpha = 0.2f))
                .padding(horizontal = LumenTokens.Space.sm2, vertical = LumenTokens.Space.xs)
        ) {
            Text(
                text = account.health.name,
                color = healthColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (status != null) {
        Text(text = "Username: ${status.username}", color = TvColors.TextMain, fontSize = 14.sp)
        Text(text = "Email: ${status.email}", color = TvColors.TextMain, fontSize = 14.sp)
        Text(text = "Premium Days: ${status.premiumDaysRemaining} remaining", color = TvColors.BorderFocused, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }

    Text(text = "Auth Key/Token: $maskedToken", color = TvColors.TextSub, fontSize = 13.sp)

    TvFocusCard(
        onClick = { showRawJson = !showRawJson },
        modifier = Modifier.fillMaxWidth()
    ) { isFocused ->
        Text(
            text = if (showRawJson) "Hide raw response" else "Advanced details",
            color = if (isFocused) TvColors.Background else TvColors.TextMain,
            fontSize = 13.sp,
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
                .background(LumenTokens.Color.debridPanel, LumenTokens.Shape.sm)
                .padding(LumenTokens.Space.sm2)
        ) {
            Text(
                text = rawJson,
                color = LumenTokens.Color.success,
                fontSize = 11.sp,
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
            color = if (isFocused) LumenTokens.Color.textPrimary else LumenTokens.Color.errorBright,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

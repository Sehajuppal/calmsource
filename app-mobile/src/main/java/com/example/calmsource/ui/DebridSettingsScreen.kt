package com.example.calmsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.model.DebridAccount
import com.example.calmsource.core.model.DebridAccountHealth
import com.example.calmsource.core.model.DebridAuthSession
import com.example.calmsource.core.model.DebridProviderType
import com.example.calmsource.core.ui.R as CoreUiR
import com.example.calmsource.core.ui.components.AdaptiveButton
import com.example.calmsource.core.ui.components.LumenCard
import com.example.calmsource.core.ui.components.PrimaryButton
import com.example.calmsource.core.ui.theme.LumenExtendedColors
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.feature.debrid.DebridRepository
import kotlinx.coroutines.launch

@Composable
fun DebridSettingsScreen(onBack: () -> Unit) {
    val t = LocalLumenTokens.current
    val accounts by DebridRepository.accounts.collectAsState()
    var selectedProvider by remember { mutableStateOf<DebridProviderType?>(null) }
    var selectedAccount by remember { mutableStateOf<DebridAccount?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .statusBarsPadding()
            .padding(LumenTokens.Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiR.string.cta_back))
            }
            Text(
                text = stringResource(CoreUiR.string.settings_debrid_title),
                style = LumenType.H1.toTextStyle(),
                color = t.colors.foreground,
            )
        }
        Text(
            text = stringResource(CoreUiR.string.settings_debrid_subtitle),
            color = t.colors.mutedForeground,
            modifier = Modifier.padding(bottom = LumenTokens.Space.md),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
        ) {
            Text(
                text = stringResource(CoreUiR.string.settings_debrid_connect_providers),
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
            )
            DebridProviderType.values()
                .filter { it != DebridProviderType.FAKE_DEMO }
                .forEach { type ->
                    val label = debridProviderLabel(type)
                    AdaptiveButton(
                        text = stringResource(CoreUiR.string.settings_debrid_connect, label),
                        onClick = {
                            selectedAccount = null
                            selectedProvider = type
                        },
                        backdropLuminance = 0f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

            Spacer(modifier = Modifier.height(LumenTokens.Space.s3))

            Text(
                text = stringResource(CoreUiR.string.settings_debrid_connected),
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
            )
            val connected = accounts.filter { it.isConnected }
            if (connected.isEmpty()) {
                Text(
                    text = stringResource(CoreUiR.string.settings_debrid_none),
                    color = t.colors.mutedForeground,
                )
            } else {
                connected.forEach { account ->
                    LumenCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedProvider = null
                                selectedAccount = account
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(LumenTokens.Space.s5),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(account.providerName, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                                Text(
                                    text = account.health.name,
                                    color = when (account.health) {
                                        DebridAccountHealth.HEALTHY -> LumenExtendedColors.statusHealthy
                                        DebridAccountHealth.SLOW -> LumenTokens.Color.warning
                                        DebridAccountHealth.FAILED -> LumenExtendedColors.errorBright
                                    },
                                    fontSize = 12.sp,
                                )
                            }
                            Text(stringResource(CoreUiR.string.settings_debrid_manage), color = t.colors.brand)
                        }
                    }
                }
            }

            selectedProvider?.let { provider ->
                MobileDebridConnectPanel(
                    providerType = provider,
                    onConnected = { selectedProvider = null },
                    onCancel = { selectedProvider = null },
                )
            }

            selectedAccount?.let { account ->
                MobileDebridAccountPanel(
                    account = account,
                    onDisconnect = {
                        DebridRepository.disconnectAccount(account.id)
                        selectedAccount = null
                    },
                    onClose = { selectedAccount = null },
                )
            }
        }
    }
}

@Composable
private fun MobileDebridConnectPanel(
    providerType: DebridProviderType,
    onConnected: () -> Unit,
    onCancel: () -> Unit,
) {
    val t = LocalLumenTokens.current
    val scope = rememberCoroutineScope()
    var authSession by remember(providerType) { mutableStateOf<DebridAuthSession?>(null) }
    var connectionError by remember(providerType) { mutableStateOf<String?>(null) }
    var isPolling by remember(providerType) { mutableStateOf(false) }

    LaunchedEffect(providerType) {
        connectionError = null
        authSession = null
        if (providerType != DebridProviderType.PREMIUMIZE) {
            runCatching {
                authSession = DebridRepository.startConnectionFlow(providerType)
            }.onFailure { error ->
                connectionError = error.message ?: "Failed to start connection"
            }
        }
    }

    LumenCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(LumenTokens.Space.md),
            verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5),
        ) {
            Text(
                text = stringResource(CoreUiR.string.settings_debrid_connect, debridProviderLabel(providerType)),
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
            )
            when {
                connectionError != null -> {
                    Text(connectionError!!, color = LumenExtendedColors.errorBright)
                    PrimaryButton(
                        text = stringResource(CoreUiR.string.cta_retry),
                        onClick = {
                            connectionError = null
                            scope.launch {
                                runCatching {
                                    authSession = DebridRepository.startConnectionFlow(providerType)
                                }.onFailure { error ->
                                    connectionError = error.message
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                providerType == DebridProviderType.PREMIUMIZE -> {
                    var apiKey by remember(providerType) { mutableStateOf("") }
                    Text(
                        text = stringResource(CoreUiR.string.settings_debrid_premiumize_hint),
                        color = t.colors.mutedForeground,
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(CoreUiR.string.settings_debrid_api_key)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    PrimaryButton(
                        text = if (isPolling) {
                            stringResource(CoreUiR.string.settings_debrid_connecting)
                        } else {
                            stringResource(CoreUiR.string.settings_debrid_connect, debridProviderLabel(providerType))
                        },
                        onClick = {
                            val trimmedKey = apiKey.trim()
                            if (trimmedKey.isBlank() || isPolling) return@PrimaryButton
                            isPolling = true
                            scope.launch {
                                runCatching {
                                    DebridRepository.addAccountWithApiKey(
                                        providerType = providerType,
                                        username = "premiumize",
                                        email = "",
                                        apiKeyOrToken = trimmedKey,
                                    )
                                    onConnected()
                                }.onFailure { error ->
                                    connectionError = error.message
                                    isPolling = false
                                }
                            }
                        },
                        enabled = apiKey.isNotBlank() && !isPolling,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                authSession is DebridAuthSession.DeviceCode -> {
                    val session = authSession as DebridAuthSession.DeviceCode
                    Text(stringResource(CoreUiR.string.settings_debrid_device_code_hint), color = t.colors.mutedForeground)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LumenTokens.Color.glass, LumenTokens.Shape.sm)
                            .padding(LumenTokens.Space.s7),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(session.details.userCode, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = t.colors.brand)
                    }
                    PrimaryButton(
                        text = if (isPolling) {
                            stringResource(CoreUiR.string.settings_debrid_connecting)
                        } else {
                            stringResource(CoreUiR.string.settings_debrid_authorized)
                        },
                        onClick = {
                            if (!isPolling) {
                                isPolling = true
                                scope.launch {
                                    runCatching {
                                        DebridRepository.completeConnectionFlow(providerType, authSession!!)
                                        onConnected()
                                    }.onFailure { error ->
                                        connectionError = error.message
                                        isPolling = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                authSession is DebridAuthSession.Pin -> {
                    val session = authSession as DebridAuthSession.Pin
                    Text(stringResource(CoreUiR.string.settings_debrid_pin_hint), color = t.colors.mutedForeground)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LumenTokens.Color.glass, LumenTokens.Shape.sm)
                            .padding(LumenTokens.Space.s7),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(session.details.pinCode, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = t.colors.brand)
                    }
                    PrimaryButton(
                        text = if (isPolling) {
                            stringResource(CoreUiR.string.settings_debrid_connecting)
                        } else {
                            stringResource(CoreUiR.string.settings_debrid_authorized)
                        },
                        onClick = {
                            if (!isPolling) {
                                isPolling = true
                                scope.launch {
                                    runCatching {
                                        DebridRepository.completeConnectionFlow(providerType, authSession!!)
                                        onConnected()
                                    }.onFailure { error ->
                                        connectionError = error.message
                                        isPolling = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = t.colors.brand)
                    }
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(CoreUiR.string.cta_cancel))
            }
        }
    }
}

@Composable
private fun MobileDebridAccountPanel(
    account: DebridAccount,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalLumenTokens.current
    LumenCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(LumenTokens.Space.md),
            verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.s3),
        ) {
            Text(account.providerName, fontWeight = FontWeight.Bold, color = t.colors.foreground)
            account.status?.let { status ->
                Text(stringResource(CoreUiR.string.settings_debrid_username, status.username), color = t.colors.foreground)
                Text(
                    stringResource(CoreUiR.string.settings_debrid_premium_days, status.premiumDaysRemaining),
                    color = t.colors.brand,
                )
            }
            PrimaryButton(
                text = stringResource(CoreUiR.string.settings_debrid_disconnect),
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(CoreUiR.string.cta_dismiss))
            }
        }
    }
}

private fun debridProviderLabel(type: DebridProviderType): String = when (type) {
    DebridProviderType.REAL_DEBRID -> "Real-Debrid"
    DebridProviderType.ALL_DEBRID -> "AllDebrid"
    DebridProviderType.PREMIUMIZE -> "Premiumize"
    else -> type.name
}

package com.example.calmsource.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.calmsource.core.ui.components.GhostButton
import com.example.calmsource.core.ui.components.PrimaryButton
import com.example.calmsource.core.ui.components.auth.LumenAuthTextField
import com.example.calmsource.core.ui.components.auth.LumenLoginCard
import com.example.calmsource.core.ui.components.auth.LumenLoginModeRow
import com.example.calmsource.core.ui.components.auth.LumenLoginScaffold
import androidx.compose.ui.res.stringResource
import com.example.calmsource.core.ui.R as CoreUiR
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LumenExtendedColors
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val t = LocalLumenTokens.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val accountState by viewModel.accountState.collectAsState()
    val tvPairState by viewModel.tvPairState.collectAsState()
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        QrScannerOverlay(
            onResult = { raw ->
                showScanner = false
                viewModel.onQrScanned(raw)
            },
            onDismiss = { showScanner = false },
        )
        return
    }

    LumenLoginScaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        title = stringResource(CoreUiR.string.login_welcome_title),
        subtitle = stringResource(CoreUiR.string.login_welcome_subtitle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
        ) {
            LumenLoginModeRow(
                modes = listOf(
                    stringResource(CoreUiR.string.login_mode_account),
                    stringResource(CoreUiR.string.login_mode_tv_pair),
                ),
                selectedIndex = selectedTab,
                onSelected = viewModel::selectTab,
            )

            when (selectedTab) {
                0 -> AccountLoginPanel(
                    state = accountState,
                    onEmailChange = viewModel::updateEmail,
                    onPasswordChange = viewModel::updatePassword,
                    onModeChange = viewModel::setAccountMode,
                    onSubmit = viewModel::signIn,
                )
                else -> TvPairLoginPanel(
                    state = tvPairState,
                    onScan = { showScanner = true },
                    onFieldChange = viewModel::updateTvPairField,
                    onSend = viewModel::sendCredentialsToTv,
                    onRescan = viewModel::resetTvPairScan,
                    onFinished = viewModel::skipSignIn,
                )
            }

            TextButton(
                onClick = { viewModel.skipSignIn() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = stringResource(CoreUiR.string.login_continue_without),
                    style = LumenType.Body.toTextStyle(),
                    color = t.colors.mutedForeground,
                )
            }
        }
    }
}

@Composable
private fun AccountLoginPanel(
    state: LoginUiState.Account,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onModeChange: (AccountMode) -> Unit,
    onSubmit: () -> Unit,
) {
    val t = LocalLumenTokens.current
    LumenLoginCard {
        LumenLoginModeRow(
            modes = listOf(
                stringResource(CoreUiR.string.login_sign_in),
                stringResource(CoreUiR.string.login_create_account),
            ),
            selectedIndex = if (state.mode == AccountMode.SignIn) 0 else 1,
            onSelected = { index ->
                onModeChange(if (index == 0) AccountMode.SignIn else AccountMode.CreateAccount)
            },
        )
        Spacer(modifier = Modifier.height(LumenTokens.Space.md))
        state.error?.let { message ->
            Text(
                text = message,
                style = LumenType.Caption.toTextStyle(),
                color = LumenExtendedColors.errorBright,
                modifier = Modifier.padding(bottom = LumenTokens.Space.s3),
            )
        }
        LumenAuthTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(CoreUiR.string.login_email),
            enabled = !state.loading,
        )
        Spacer(modifier = Modifier.height(LumenTokens.Space.s5))
        LumenAuthTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(CoreUiR.string.login_password),
            enabled = !state.loading,
            isPassword = true,
        )
        Spacer(modifier = Modifier.height(LumenTokens.Space.lg))
        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = t.colors.brand)
            }
        } else {
            PrimaryButton(
                text = if (state.mode == AccountMode.SignIn) {
                    stringResource(CoreUiR.string.login_sign_in)
                } else {
                    stringResource(CoreUiR.string.login_create_account)
                },
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TvPairLoginPanel(
    state: LoginUiState.TvPair,
    onScan: () -> Unit,
    onFieldChange: (TvPairField, String) -> Unit,
    onSend: () -> Unit,
    onRescan: () -> Unit,
    onFinished: () -> Unit,
) {
    val t = LocalLumenTokens.current
    LumenLoginCard {
        when (state.step) {
            TvPairStep.Scan -> {
                Text(
                    text = stringResource(CoreUiR.string.login_tv_scan_hint),
                    style = LumenType.Body.toTextStyle(),
                    color = t.colors.mutedForeground,
                )
                Spacer(modifier = Modifier.height(LumenTokens.Space.md))
                state.error?.let {
                    Text(text = it, color = LumenExtendedColors.errorBright, style = LumenType.Caption.toTextStyle())
                    Spacer(modifier = Modifier.height(LumenTokens.Space.s3))
                }
                PrimaryButton(text = stringResource(CoreUiR.string.login_scan_qr), onClick = onScan, modifier = Modifier.fillMaxWidth())
            }
            TvPairStep.Confirm -> {
                Text(
                    text = stringResource(CoreUiR.string.login_tv_connected, state.pairing?.pin.orEmpty()),
                    style = LumenType.Meta.toTextStyle(),
                    color = t.colors.mutedForeground,
                )
                Spacer(modifier = Modifier.height(LumenTokens.Space.s5))
                state.error?.let {
                    Text(text = it, color = LumenExtendedColors.errorBright, style = LumenType.Caption.toTextStyle())
                    Spacer(modifier = Modifier.height(LumenTokens.Space.s3))
                }
                LumenAuthTextField(
                    value = state.xtreamUrl,
                    onValueChange = { onFieldChange(TvPairField.Url, it) },
                    label = stringResource(CoreUiR.string.login_xtream_url),
                    enabled = !state.loading,
                )
                Spacer(modifier = Modifier.height(LumenTokens.Space.s3))
                LumenAuthTextField(
                    value = state.xtreamUsername,
                    onValueChange = { onFieldChange(TvPairField.Username, it) },
                    label = stringResource(CoreUiR.string.login_username),
                    enabled = !state.loading,
                )
                Spacer(modifier = Modifier.height(LumenTokens.Space.s3))
                LumenAuthTextField(
                    value = state.xtreamPassword,
                    onValueChange = { onFieldChange(TvPairField.Password, it) },
                    label = stringResource(CoreUiR.string.login_password),
                    enabled = !state.loading,
                    isPassword = true,
                )
                Spacer(modifier = Modifier.height(LumenTokens.Space.s3))
                LumenAuthTextField(
                    value = state.debridToken,
                    onValueChange = { onFieldChange(TvPairField.Debrid, it) },
                    label = stringResource(CoreUiR.string.login_debrid_token),
                    enabled = !state.loading,
                    isPassword = true,
                )
                Spacer(modifier = Modifier.height(LumenTokens.Space.md))
                if (state.loading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = t.colors.brand)
                    }
                } else {
                    PrimaryButton(text = stringResource(CoreUiR.string.login_send_to_tv), onClick = onSend, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(LumenTokens.Space.s3))
                    GhostButton(text = stringResource(CoreUiR.string.login_scan_again), onClick = onRescan, modifier = Modifier.fillMaxWidth())
                }
            }
            TvPairStep.Done -> {
                Text(
                    text = stringResource(CoreUiR.string.login_tv_ready_title),
                    style = LumenType.Title.toTextStyle(),
                    color = t.colors.foreground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(LumenTokens.Space.s3))
                Text(
                    text = stringResource(CoreUiR.string.login_tv_ready_body),
                    style = LumenType.Body.toTextStyle(),
                    color = t.colors.mutedForeground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(LumenTokens.Space.md))
                PrimaryButton(
                    text = stringResource(CoreUiR.string.login_continue),
                    onClick = onFinished,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

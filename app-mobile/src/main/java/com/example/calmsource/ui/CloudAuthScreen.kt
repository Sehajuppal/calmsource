package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CloudAuthScreen(
    onBack: () -> Unit,
    viewModel: CloudSyncViewModel = hiltViewModel()
) {
    val t = LocalLumenTokens.current
    val uiState by viewModel.uiState.collectAsState()
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearErrorAndSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(LumenLegacySpace.lg)
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenHeader(title = "Cloud Sync", onBack = onBack)

        Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))

        if (uiState.error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = LumenExtendedColors.dangerContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = LumenLegacySpace.lg),
                shape = LumenTokens.Shape.sm
            ) {
                Row(
                    modifier = Modifier.padding(LumenLegacySpace.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LumenTokens.Color.danger,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (uiState.successMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = LumenExtendedColors.successContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = LumenLegacySpace.lg),
                shape = LumenTokens.Shape.sm
            ) {
                Row(
                    modifier = Modifier.padding(LumenLegacySpace.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.successMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LumenExtendedColors.statusHealthy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (!uiState.authStatus) {
            UnauthenticatedForm(viewModel = viewModel, loading = uiState.loading)
        } else {
            val token = viewModel.getToken()
            val emailFromToken = getEmailFromToken(token)
            AuthenticatedSyncControls(
                email = emailFromToken,
                viewModel = viewModel,
                loading = uiState.loading
            )
        }
    }
}

@Composable
fun UnauthenticatedForm(
    viewModel: CloudSyncViewModel,
    loading: Boolean
) {
    val t = LocalLumenTokens.current
    var activeTab by remember { mutableStateOf(0) } // 0 = Login, 1 = Register
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = LumenLegacySpace.xl),
            horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { 
                        if (!loading) {
                            activeTab = 0
                            viewModel.clearErrorAndSuccess()
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Login",
                    color = if (activeTab == 0) t.colors.brand else t.colors.mutedForeground,
                    style = LumenType.RowTitle.toTextStyle()
                )
                Spacer(modifier = Modifier.height(LumenLegacySpace.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LumenLegacySpace.xxs)
                        .background(if (activeTab == 0) t.colors.brand else Color.Transparent)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { 
                        if (!loading) {
                            activeTab = 1
                            viewModel.clearErrorAndSuccess()
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Register",
                    color = if (activeTab == 1) t.colors.brand else t.colors.mutedForeground,
                    style = LumenType.RowTitle.toTextStyle()
                )
                Spacer(modifier = Modifier.height(LumenLegacySpace.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LumenLegacySpace.xxs)
                        .background(if (activeTab == 1) t.colors.brand else Color.Transparent)
                )
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            singleLine = true,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(LumenLegacySpace.lg))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !loading,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(LumenLegacySpace.xxl))

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LumenLayout.buttonHeight),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = t.colors.brand)
            }
        } else {
            PremiumButton(
                text = if (activeTab == 0) "Login" else "Register",
                onClick = {
                    if (activeTab == 0) {
                        viewModel.login(email, password)
                    } else {
                        viewModel.register(email, password)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AuthenticatedSyncControls(
    email: String,
    viewModel: CloudSyncViewModel,
    loading: Boolean
) {
    val t = LocalLumenTokens.current
    var backupPassword by remember { mutableStateOf("") }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CalmSource Central Account",
            color = t.colors.brand,
            style = LumenType.Title.toTextStyle(),
            modifier = Modifier.padding(bottom = LumenLegacySpace.sm2)
        )
        
        Text(
            text = "Connected as:",
            color = t.colors.mutedForeground,
            style = LumenType.Meta.toTextStyle()
        )
        Text(
            text = email,
            color = t.colors.foreground,
            style = LumenType.Body.toTextStyle().copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = LumenLegacySpace.xl)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(t.colors.border)
                .padding(bottom = LumenLegacySpace.xl)
        )
        
        Spacer(modifier = Modifier.height(LumenLegacySpace.lg))

        Text(
            text = "Vault Protection",
            color = t.colors.foreground,
            style = LumenType.Body.toTextStyle().copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = LumenLegacySpace.sm)
        )
        Text(
            text = "Enter a password to encrypt your credentials before backing up or to decrypt when restoring.",
            color = t.colors.mutedForeground,
            style = LumenType.Meta.toTextStyle(),
            modifier = Modifier.padding(bottom = LumenLegacySpace.md)
        )

        OutlinedTextField(
            value = backupPassword,
            onValueChange = { backupPassword = it },
            label = { Text("Vault Password") },
            singleLine = true,
            enabled = !loading,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(LumenLegacySpace.xxl))

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LumenLayout.buttonHeight),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = t.colors.brand)
            }
        } else {
            PremiumButton(
                text = "Backup to Cloud",
                onClick = { viewModel.backup(backupPassword) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(LumenLegacySpace.md))

            OutlinedButton(
                onClick = { viewModel.restore(backupPassword) },
                modifier = Modifier.fillMaxWidth(),
                shape = LumenTokens.Shape.md,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = t.colors.brand)
            ) {
                Text(
                    text = "Restore from Cloud",
                    style = LumenType.RowTitle.toTextStyle(),
                    color = t.colors.foreground
                )
            }
        }

        Spacer(modifier = Modifier.height(LumenLegacySpace.xxl))

        TextButton(
            onClick = { viewModel.logout() },
            enabled = !loading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Logout", color = LumenExtendedColors.errorBright, fontWeight = FontWeight.Bold)
        }
    }
}

private fun getEmailFromToken(token: String?): String {
    if (token.isNullOrBlank()) return "Authenticated User"
    val parts = token.split(".")
    if (parts.size >= 2) {
        try {
            val payloadBytes = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            val payload = String(payloadBytes, Charsets.UTF_8)
            val emailRegex = """"email"\s*:\s*"([^"]+)"""".toRegex()
            val subRegex = """"sub"\s*:\s*"([^"]+)"""".toRegex()
            val emailMatch = emailRegex.find(payload)?.groupValues?.get(1)
            val subMatch = subRegex.find(payload)?.groupValues?.get(1)
            return emailMatch ?: subMatch ?: "Authenticated User"
        } catch (e: Exception) {
            // Fallback
        }
    }
    return "Authenticated User"
}

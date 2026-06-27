package com.example.calmsource.ui

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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CloudAuthScreen(
    onBack: () -> Unit,
    viewModel: CloudSyncViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearErrorAndSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenHeader(title = "Cloud Sync", onBack = onBack)

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (uiState.successMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1F10B981)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.successMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF10B981),
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
    var activeTab by remember { mutableStateOf(0) } // 0 = Login, 1 = Register
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                    color = if (activeTab == 0) AppColors.Primary else AppColors.TextSub,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (activeTab == 0) AppColors.Primary else Color.Transparent)
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
                    color = if (activeTab == 1) AppColors.Primary else AppColors.TextSub,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (activeTab == 1) AppColors.Primary else Color.Transparent)
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

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !loading,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.Primary)
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
    var backupPassword by remember { mutableStateOf("") }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CalmSource Central Account",
            color = AppColors.Primary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Connected as:",
            color = AppColors.TextSub,
            fontSize = 12.sp
        )
        Text(
            text = email,
            color = AppColors.TextMain,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColors.Border)
                .padding(bottom = 20.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Vault Protection",
            color = AppColors.TextMain,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Enter a password to encrypt your credentials before backing up or to decrypt when restoring.",
            color = AppColors.TextSub,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
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

        Spacer(modifier = Modifier.height(24.dp))

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        } else {
            PremiumButton(
                text = "Backup to Cloud",
                onClick = { viewModel.backup(backupPassword) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { viewModel.restore(backupPassword) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)
            ) {
                Text(
                    text = "Restore from Cloud",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AppColors.TextMain
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(
            onClick = { viewModel.logout() },
            enabled = !loading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Logout", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
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

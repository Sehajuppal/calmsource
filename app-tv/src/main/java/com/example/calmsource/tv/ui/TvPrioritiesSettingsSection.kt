package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import com.example.calmsource.core.database.repository.UserPreferencesRepository

@Composable
fun TvPrioritiesScreen(onBack: () -> Unit) {
    val t = LocalLumenTokens.current
    val prefs by UserPreferencesRepository.preferences.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(LumenLegacySpace.xxl),
        verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
    ) {
        item {
            TvFocusCard(onClick = onBack, modifier = Modifier.wrapContentSize().padding(bottom = LumenLegacySpace.lg)) {
                Text(text = "← Back", color = t.colors.foreground)
            }
        }
        item {
            Text(text = "Priorities Configuration", style = lumenTitleStyle(), fontWeight = FontWeight.Bold, color = t.colors.foreground, modifier = Modifier.padding(bottom = LumenLegacySpace.md))
        }

        item {
            TvFocusCard(
                onClick = {
                    val newLang = if (prefs.primaryLanguage == "Hindi") "English" else "Hindi"
                    UserPreferencesRepository.updatePreferences { it.copy(primaryLanguage = newLang) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Primary Language Priority", color = t.colors.foreground)
                    Text(text = prefs.primaryLanguage, color = t.colors.brand, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            TvFocusCard(
                onClick = {
                    UserPreferencesRepository.updatePreferences { it.copy(preferCachedDebrid = !prefs.preferCachedDebrid) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Debrid Auto-Pick Cache", color = t.colors.foreground)
                    Text(text = if (prefs.preferCachedDebrid) "Enabled" else "Disabled", color = t.colors.brand, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            TvFocusCard(
                onClick = {
                    UserPreferencesRepository.updatePreferences { it.copy(preferHighestQuality = !prefs.preferHighestQuality) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Prefer Highest Quality (4K/1080p)", color = t.colors.foreground)
                    Text(text = if (prefs.preferHighestQuality) "Enabled" else "Disabled", color = if (prefs.preferHighestQuality) t.colors.brand else t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            TvFocusCard(
                onClick = {
                    UserPreferencesRepository.updatePreferences { it.copy(hideDuplicates = !prefs.hideDuplicates) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Hide Duplicates", color = t.colors.foreground)
                    Text(text = if (prefs.hideDuplicates) "Enabled" else "Disabled", color = if (prefs.hideDuplicates) t.colors.brand else t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            TvFocusCard(
                onClick = {
                    UserPreferencesRepository.updatePreferences { it.copy(allowCleartextUserSources = !prefs.allowCleartextUserSources) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Allow Cleartext HTTP Sources (Unsafe)", color = t.colors.foreground)
                    Text(text = if (prefs.allowCleartextUserSources) "Enabled" else "Disabled", color = if (prefs.allowCleartextUserSources) t.colors.brand else t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            TvFocusCard(
                onClick = {
                    UserPreferencesRepository.updatePreferences { it.copy(preferLowerDataUsage = !prefs.preferLowerDataUsage) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm), modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Low-Data Bandwidth Mode", color = t.colors.foreground, modifier = Modifier.weight(1f))
                    Text(text = if (prefs.preferLowerDataUsage) "Enabled" else "Disabled", color = if (prefs.preferLowerDataUsage) t.colors.brand else t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            TvFocusCard(
                onClick = {
                    UserPreferencesRepository.updatePreferences { it.copy(separateIptvCategoriesByProvider = !prefs.separateIptvCategoriesByProvider) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm), modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Separate IPTV Categories by Provider", color = t.colors.foreground, modifier = Modifier.weight(1f))
                    Text(text = if (prefs.separateIptvCategoriesByProvider) "Enabled" else "Disabled", color = if (prefs.separateIptvCategoriesByProvider) t.colors.brand else t.colors.mutedForeground, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

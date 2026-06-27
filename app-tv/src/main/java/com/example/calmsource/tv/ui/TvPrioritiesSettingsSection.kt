package com.example.calmsource.tv.ui

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
    val prefs by UserPreferencesRepository.preferences.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TvFocusCard(onClick = onBack, modifier = Modifier.wrapContentSize().padding(bottom = 16.dp)) {
                Text(text = "← Back", color = TvColors.TextMain)
            }
        }
        item {
            Text(text = "Priorities Configuration", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain, modifier = Modifier.padding(bottom = 12.dp))
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
                    Text(text = "Primary Language Priority", color = TvColors.TextMain)
                    Text(text = prefs.primaryLanguage, color = TvColors.BorderFocused, fontWeight = FontWeight.Bold)
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
                    Text(text = "Debrid Auto-Pick Cache", color = TvColors.TextMain)
                    Text(text = if (prefs.preferCachedDebrid) "Enabled" else "Disabled", color = TvColors.BorderFocused, fontWeight = FontWeight.Bold)
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
                    Text(text = "Prefer Highest Quality (4K/1080p)", color = TvColors.TextMain)
                    Text(text = if (prefs.preferHighestQuality) "Enabled" else "Disabled", color = if (prefs.preferHighestQuality) TvColors.BorderFocused else TvColors.TextSub, fontWeight = FontWeight.Bold)
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
                    Text(text = "Hide Duplicates", color = TvColors.TextMain)
                    Text(text = if (prefs.hideDuplicates) "Enabled" else "Disabled", color = if (prefs.hideDuplicates) TvColors.BorderFocused else TvColors.TextSub, fontWeight = FontWeight.Bold)
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
                    Text(text = "Allow Cleartext HTTP Sources (Unsafe)", color = TvColors.TextMain)
                    Text(text = if (prefs.allowCleartextUserSources) "Enabled" else "Disabled", color = if (prefs.allowCleartextUserSources) TvColors.BorderFocused else TvColors.TextSub, fontWeight = FontWeight.Bold)
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Low-Data Bandwidth Mode", color = TvColors.TextMain, modifier = Modifier.weight(1f))
                    Text(text = if (prefs.preferLowerDataUsage) "Enabled" else "Disabled", color = if (prefs.preferLowerDataUsage) TvColors.BorderFocused else TvColors.TextSub, fontWeight = FontWeight.Bold)
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Separate IPTV Categories by Provider", color = TvColors.TextMain, modifier = Modifier.weight(1f))
                    Text(text = if (prefs.separateIptvCategoriesByProvider) "Enabled" else "Disabled", color = if (prefs.separateIptvCategoriesByProvider) TvColors.BorderFocused else TvColors.TextSub, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

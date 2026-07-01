package com.example.calmsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.core.ui.R as CoreUiR
import com.example.calmsource.core.ui.components.LumenCard
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType

@Composable
fun SourcePreferencesScreen(onBack: () -> Unit) {
    val t = LocalLumenTokens.current
    val prefs by UserPreferencesRepository.preferences.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(LumenTokens.Space.md),
        verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(CoreUiR.string.cta_back),
                    tint = t.colors.foreground,
                )
            }
            Text(
                text = stringResource(CoreUiR.string.settings_source_priorities_title),
                style = LumenType.Title.toTextStyle(),
                color = t.colors.foreground,
            )
        }

        Text(
            text = stringResource(CoreUiR.string.settings_source_priorities_subtitle),
            style = LumenType.Body.toTextStyle(),
            color = t.colors.mutedForeground,
        )

        PreferenceToggleRow(
            title = stringResource(CoreUiR.string.settings_pref_primary_language),
            value = prefs.primaryLanguage,
            onClick = {
                val next = if (prefs.primaryLanguage == "Hindi") "English" else "Hindi"
                UserPreferencesRepository.updatePreferences { it.copy(primaryLanguage = next) }
            },
        )
        PreferenceToggleRow(
            title = stringResource(CoreUiR.string.settings_pref_debrid_cache),
            value = if (prefs.preferCachedDebrid) {
                stringResource(CoreUiR.string.settings_enabled)
            } else {
                stringResource(CoreUiR.string.settings_disabled)
            },
            onClick = {
                UserPreferencesRepository.updatePreferences { it.copy(preferCachedDebrid = !prefs.preferCachedDebrid) }
            },
        )
        PreferenceToggleRow(
            title = stringResource(CoreUiR.string.settings_pref_highest_quality),
            value = if (prefs.preferHighestQuality) {
                stringResource(CoreUiR.string.settings_enabled)
            } else {
                stringResource(CoreUiR.string.settings_disabled)
            },
            onClick = {
                UserPreferencesRepository.updatePreferences { it.copy(preferHighestQuality = !prefs.preferHighestQuality) }
            },
        )
        PreferenceToggleRow(
            title = stringResource(CoreUiR.string.settings_pref_hide_duplicates),
            value = if (prefs.hideDuplicates) {
                stringResource(CoreUiR.string.settings_enabled)
            } else {
                stringResource(CoreUiR.string.settings_disabled)
            },
            onClick = {
                UserPreferencesRepository.updatePreferences { it.copy(hideDuplicates = !prefs.hideDuplicates) }
            },
        )
        PreferenceToggleRow(
            title = stringResource(CoreUiR.string.settings_pref_cleartext),
            value = if (prefs.allowCleartextUserSources) {
                stringResource(CoreUiR.string.settings_enabled)
            } else {
                stringResource(CoreUiR.string.settings_disabled)
            },
            onClick = {
                UserPreferencesRepository.updatePreferences {
                    it.copy(allowCleartextUserSources = !prefs.allowCleartextUserSources)
                }
            },
        )
        PreferenceToggleRow(
            title = stringResource(CoreUiR.string.settings_pref_low_data),
            value = if (prefs.preferLowerDataUsage) {
                stringResource(CoreUiR.string.settings_enabled)
            } else {
                stringResource(CoreUiR.string.settings_disabled)
            },
            onClick = {
                UserPreferencesRepository.updatePreferences { it.copy(preferLowerDataUsage = !prefs.preferLowerDataUsage) }
            },
        )
        PreferenceToggleRow(
            title = stringResource(CoreUiR.string.settings_pref_separate_iptv),
            value = if (prefs.separateIptvCategoriesByProvider) {
                stringResource(CoreUiR.string.settings_enabled)
            } else {
                stringResource(CoreUiR.string.settings_disabled)
            },
            onClick = {
                UserPreferencesRepository.updatePreferences {
                    it.copy(separateIptvCategoriesByProvider = !prefs.separateIptvCategoriesByProvider)
                }
            },
        )
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val t = LocalLumenTokens.current
    LumenCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LumenTokens.Space.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.colors.foreground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = t.colors.brand,
            )
        }
    }
}

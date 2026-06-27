package com.example.calmsource.core.database.mapper

import com.example.calmsource.core.database.entity.*
import com.example.calmsource.core.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val mapperJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun IPTVProviderEntity.toDomain(): IPTVProvider {
    return IPTVProvider(
        id = this.id,
        name = this.name,
        playlistUrl = this.playlistUrl,
        isEnabled = this.isEnabled,
        health = runCatching { ProviderHealth.valueOf(this.health) }.getOrDefault(ProviderHealth.HEALTHY),
        type = runCatching { IPTVProviderType.valueOf(this.type) }.getOrDefault(IPTVProviderType.M3U),
        serverUrl = this.serverUrl,
        username = this.username
    )
}

fun IPTVProvider.toEntity(): IPTVProviderEntity {
    return IPTVProviderEntity().apply {
        id = this@toEntity.id
        name = this@toEntity.name
        playlistUrl = this@toEntity.playlistUrl
        isEnabled = this@toEntity.isEnabled
        health = this@toEntity.health.name
        type = this@toEntity.type.name
        serverUrl = this@toEntity.serverUrl
        username = this@toEntity.username
    }
}

fun IPTVChannelEntity.toDomain(): IPTVChannel {
    val attrs: Map<String, String> = try {
        if (!rawAttributesJson.isNullOrBlank() && rawAttributesJson != "{}") {
            val jsonObj = mapperJson.parseToJsonElement(rawAttributesJson).jsonObject
            jsonObj.mapValues { (_, v) -> v.jsonPrimitive.content }
        } else emptyMap()
    } catch (e: Exception) { emptyMap() }

    return IPTVChannel(
        id = this.id,
        tvgId = this.tvgId,
        tvgName = this.tvgName,
        tvgLogo = this.tvgLogo,
        groupTitle = this.groupTitle,
        name = this.name,
        streamUrl = this.streamUrl,
        providerId = this.providerId,
        rawAttributes = attrs,
        language = this.language,
        country = this.country
    )
}

fun IPTVChannel.toEntity(): IPTVChannelEntity {
    return IPTVChannelEntity().apply {
        id = this@toEntity.id
        tvgId = this@toEntity.tvgId
        tvgName = this@toEntity.tvgName
        tvgLogo = this@toEntity.tvgLogo
        groupTitle = this@toEntity.groupTitle
        name = this@toEntity.name
        streamUrl = this@toEntity.streamUrl
        providerId = this@toEntity.providerId
        rawAttributesJson = if (this@toEntity.rawAttributes.isNotEmpty()) {
            mapperJson.encodeToString(JsonObject(this@toEntity.rawAttributes.mapValues { (_, v) -> JsonPrimitive(v) }))
        } else "{}"
        language = this@toEntity.language
        country = this@toEntity.country
    }
}

fun EPGSourceEntity.toDomain(): EPGSource {
    return EPGSource(
        id = this.id,
        providerId = this.providerId,
        name = this.name,
        url = this.url,
        lastSyncMs = this.lastSyncMs
    )
}

fun EPGSource.toEntity(): EPGSourceEntity {
    return EPGSourceEntity().apply {
        id = this@toEntity.id
        providerId = this@toEntity.providerId
        name = this@toEntity.name
        url = this@toEntity.url
        lastSyncMs = this@toEntity.lastSyncMs
    }
}

fun EPGProgramEntity.toDomain(): EPGProgram {
    return EPGProgram(
        id = this.id,
        channelId = this.channelId,
        title = this.title,
        description = this.description,
        startTimeMs = this.startTimeMs,
        endTimeMs = this.endTimeMs,
        subtitle = this.subtitle,
        category = this.category,
        language = this.language,
        episodeNum = this.episodeNum
    )
}

fun EPGProgram.toEntity(): EPGProgramEntity {
    return EPGProgramEntity().apply {
        id = this@toEntity.id
        channelId = this@toEntity.channelId
        title = this@toEntity.title
        description = this@toEntity.description
        startTimeMs = this@toEntity.startTimeMs
        endTimeMs = this@toEntity.endTimeMs
        subtitle = this@toEntity.subtitle
        category = this@toEntity.category
        language = this@toEntity.language
        episodeNum = this@toEntity.episodeNum
    }
}

fun ExtensionProviderEntity.toDomain(): ExtensionProvider {
    val hasManifestJson = !manifestJson.isNullOrBlank() && manifestJson != "{}"
    val parsedManifest = if (hasManifestJson) {
        try {
            mapperJson.decodeFromString<ExtensionManifest>(manifestJson)
        } catch (e: Exception) {
            // Lenient fallback: try a more permissive parser before giving up.
            // This prevents app updates (with new manifest fields) from silently
            // marking all existing extensions as INVALID_MANIFEST.
            try {
                val lenient = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                    explicitNulls = false
                }
                lenient.decodeFromString<ExtensionManifest>(manifestJson)
            } catch (_: Exception) {
                null
            }
        }
    } else {
        null
    }
    val storedHealth = runCatching { ExtensionHealth.valueOf(this.health) }.getOrDefault(ExtensionHealth.UNKNOWN)
    // Don't immediately mark as INVALID_MANIFEST on parse failure — the manifest
    // URL is still stored, so the auto-recovery job can re-fetch it. Preserve the
    // previous health state so the extension isn't permanently locked out.
    val resolvedHealth = when {
        storedHealth == ExtensionHealth.DISABLED -> ExtensionHealth.DISABLED
        hasManifestJson && parsedManifest == null && storedHealth == ExtensionHealth.UNKNOWN -> ExtensionHealth.INVALID_MANIFEST
        hasManifestJson && parsedManifest == null -> storedHealth
        else -> storedHealth
    }

    return ExtensionProvider(
        id = this.id,
        name = this.name,
        url = this.url,
        isEnabled = this.isEnabled,
        health = resolvedHealth,
        priority = this.priority,
        manifest = parsedManifest,
        permissions = this.permissionsCsv.split(",").mapNotNull { 
            runCatching { ExtensionPermission.valueOf(it.trim()) }.getOrNull() 
        },
        capabilities = parsedManifest?.detectCapabilities() ?: emptySet(),
        supportedTypes = parsedManifest?.detectContentTypes() ?: emptySet()
    )
}

fun ExtensionProvider.toEntity(): ExtensionProviderEntity {
    return ExtensionProviderEntity().apply {
        id = this@toEntity.id
        name = this@toEntity.name
        url = this@toEntity.url
        isEnabled = this@toEntity.isEnabled
        health = this@toEntity.health.name
        priority = this@toEntity.priority
        manifestJson = this@toEntity.manifest?.let { m ->
            try {
                mapperJson.encodeToString(m)
            } catch (e: Exception) { null }
        } ?: "{}"
        permissionsCsv = this@toEntity.permissions.joinToString(",") { it.name }
    }
}

fun DebridAccountEntity.toDomain(): DebridAccount {
    return DebridAccount(
        id = this.id,
        providerType = runCatching { DebridProviderType.valueOf(this.providerType) }.getOrDefault(DebridProviderType.FAKE_DEMO),
        providerName = this.providerName,
        isConnected = this.isConnected,
        email = this.email,
        username = this.username,
        health = runCatching { DebridAccountHealth.valueOf(this.health) }.getOrDefault(DebridAccountHealth.HEALTHY)
    )
}

fun DebridAccount.toEntity(): DebridAccountEntity {
    return DebridAccountEntity().apply {
        id = this@toEntity.id
        providerType = this@toEntity.providerType.name
        providerName = this@toEntity.providerName
        isConnected = this@toEntity.isConnected
        email = this@toEntity.email
        username = this@toEntity.username
        health = this@toEntity.health.name
    }
}

fun UserPreferencesEntity.toDomain(): UserPreferences {
    return UserPreferences(
        primaryLanguage = this.primaryLanguage,
        secondaryLanguage = this.secondaryLanguage,
        subtitleLanguage = this.subtitleLanguage,
        sourcePriority = this.sourcePriority,
        preferCachedDebrid = this.preferCachedDebrid,
        preferIptvExactMatch = this.preferIptvExactMatch,
        preferFhdOrBetter = this.preferFhdOrBetter,
        hideLowQuality = this.hideLowQuality,
        hideDuplicates = this.hideDuplicates,
        preferOriginalAudio = this.preferOriginalAudio,
        preferDubbedAudio = this.preferDubbedAudio,
        preferDualAudio = this.preferDualAudio,
        preferHighestQuality = this.preferHighestQuality,
        preferLowerDataUsage = this.preferLowerDataUsage,
        askBeforeChoosingSource = this.askBeforeChoosingSource,
        askBeforeDebrid = this.askBeforeDebrid,
        hideNonCached = this.hideNonCached,
        showDebridStatusInStreamPicker = this.showDebridStatusInStreamPicker,
        primaryDebridProvider = this.primaryDebridProvider,
        allowCleartextUserSources = this.allowCleartextUserSources,
        separateIptvCategoriesByProvider = this.separateIptvCategoriesByProvider
    )
}

fun UserPreferences.toEntity(): UserPreferencesEntity {
    return UserPreferencesEntity().apply {
        id = 1
        primaryLanguage = this@toEntity.primaryLanguage
        secondaryLanguage = this@toEntity.secondaryLanguage
        subtitleLanguage = this@toEntity.subtitleLanguage
        sourcePriority = this@toEntity.sourcePriority
        preferCachedDebrid = this@toEntity.preferCachedDebrid
        preferIptvExactMatch = this@toEntity.preferIptvExactMatch
        preferFhdOrBetter = this@toEntity.preferFhdOrBetter
        hideLowQuality = this@toEntity.hideLowQuality
        hideDuplicates = this@toEntity.hideDuplicates
        preferOriginalAudio = this@toEntity.preferOriginalAudio
        preferDubbedAudio = this@toEntity.preferDubbedAudio
        preferDualAudio = this@toEntity.preferDualAudio
        preferHighestQuality = this@toEntity.preferHighestQuality
        preferLowerDataUsage = this@toEntity.preferLowerDataUsage
        askBeforeChoosingSource = this@toEntity.askBeforeChoosingSource
        askBeforeDebrid = this@toEntity.askBeforeDebrid
        hideNonCached = this@toEntity.hideNonCached
        showDebridStatusInStreamPicker = this@toEntity.showDebridStatusInStreamPicker
        primaryDebridProvider = this@toEntity.primaryDebridProvider
        allowCleartextUserSources = this@toEntity.allowCleartextUserSources
        separateIptvCategoriesByProvider = this@toEntity.separateIptvCategoriesByProvider
    }
}

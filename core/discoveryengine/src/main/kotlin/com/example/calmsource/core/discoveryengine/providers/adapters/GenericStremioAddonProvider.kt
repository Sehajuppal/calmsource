package com.example.calmsource.core.discoveryengine.providers.adapters

import com.example.calmsource.core.database.entity.ExtensionProviderEntity
import com.example.calmsource.core.discoveryengine.providers.ArtworkEntry
import com.example.calmsource.core.discoveryengine.providers.ArtworkProvider
import com.example.calmsource.core.discoveryengine.providers.AvailabilityEntry
import com.example.calmsource.core.discoveryengine.providers.AvailabilityProvider
import com.example.calmsource.core.discoveryengine.providers.CatalogItemPreview
import com.example.calmsource.core.discoveryengine.providers.CatalogProvider
import com.example.calmsource.core.discoveryengine.providers.EnrichedMetadata
import com.example.calmsource.core.discoveryengine.providers.ExternalIdSet
import com.example.calmsource.core.discoveryengine.providers.MetadataProvider
import com.example.calmsource.core.discoveryengine.providers.ProviderResult
import com.example.calmsource.core.discoveryengine.providers.ProviderType
import com.example.calmsource.core.discoveryengine.providers.RatingEntry
import com.example.calmsource.core.discoveryengine.providers.RatingProvider
import com.example.calmsource.core.discoveryengine.providers.SimilarEntry
import com.example.calmsource.core.discoveryengine.providers.SimilarProvider
import com.example.calmsource.core.discoveryengine.providers.StreamDescriptor
import com.example.calmsource.core.discoveryengine.providers.StreamProvider
import com.example.calmsource.core.discoveryengine.providers.SubtitleEntry
import com.example.calmsource.core.discoveryengine.providers.SubtitleProvider
import com.example.calmsource.core.model.ExtensionError
import com.example.calmsource.core.model.ExtensionManifest
import com.example.calmsource.core.model.StremioManifest
import com.example.calmsource.core.model.StremioMeta
import com.example.calmsource.core.model.StremioMetaPreview
import com.example.calmsource.core.model.StremioStream
import com.example.calmsource.core.model.isResourceSupported
import com.example.calmsource.core.network.StremioAddonClient
import com.example.calmsource.core.network.StremioResult
import java.net.URLEncoder
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class GenericStremioAddonProvider(
    private val addonEntity: ExtensionProviderEntity,
    private val timeoutMs: Long = 15_000L
) : MetadataProvider,
    RatingProvider,
    SimilarProvider,
    SubtitleProvider,
    AvailabilityProvider,
    StreamProvider,
    ArtworkProvider,
    CatalogProvider {

    override val providerId: String = addonEntity.id

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Volatile private var extensionManifest: ExtensionManifest? = parseExtensionManifest(addonEntity.manifestJson)
    @Volatile private var stremioManifest: StremioManifest? = parseStremioManifest(addonEntity.manifestJson)

    override suspend fun fetchMetadata(mediaId: String, ids: ExternalIdSet): ProviderResult<EnrichedMetadata> {
        val type = requestType(ids)
        if (!supports("meta", type)) return ProviderResult.Skipped("addon does not support meta")
        val response = StremioAddonClient.getMeta(resolvedBaseUrl(), type, requestId(mediaId, ids), providerId, timeoutMs)
        return response.map(
            onSuccess = { body ->
                val meta = body.meta ?: return@map ProviderResult.Success(
                    EnrichedMetadata(
                        title = null,
                        originalTitle = null,
                        overview = null,
                        director = null,
                        runtimeMinutes = null,
                        language = null,
                        country = null,
                        posterUrl = null,
                        backdropUrl = null
                    )
                )
                ProviderResult.Success(meta.toEnrichedMetadata())
            }
        )
    }

    override suspend fun fetchRatings(mediaId: String, ids: ExternalIdSet): ProviderResult<List<RatingEntry>> {
        val type = requestType(ids)
        if (!supports("meta", type)) return ProviderResult.Skipped("addon does not support meta ratings")
        val response = StremioAddonClient.getMeta(resolvedBaseUrl(), type, requestId(mediaId, ids), providerId, timeoutMs)
        return response.map(
            onSuccess = { body ->
                val rating = body.meta?.imdbRating?.toDoubleOrNull()
                ProviderResult.Success(
                    if (rating == null) emptyList() else listOf(RatingEntry(value = rating, scale = 10.0))
                )
            }
        )
    }

    override suspend fun fetchSimilar(
        mediaId: String,
        ids: ExternalIdSet,
        limit: Int
    ): ProviderResult<List<SimilarEntry>> {
        return ProviderResult.Skipped("Stremio addons do not expose a similar resource")
    }

    override suspend fun fetchSubtitles(
        mediaId: String,
        ids: ExternalIdSet,
        languageHints: List<String>
    ): ProviderResult<List<SubtitleEntry>> {
        val type = requestType(ids)
        if (!supports("subtitles", type)) return ProviderResult.Skipped("addon does not support subtitles")
        val response = StremioAddonClient.getSubtitles(resolvedBaseUrl(), type, requestId(mediaId, ids), providerId, timeoutMs)
        return response.map(
            onSuccess = { body ->
                val subtitles = (body.subtitles ?: emptyList()).map { subtitle ->
                    SubtitleEntry(
                        id = subtitle.id.ifBlank { "${providerId}:${subtitle.lang}:${subtitle.url.hashCode()}" },
                        language = subtitle.lang,
                        url = subtitle.url,
                        format = subtitle.url.substringAfterLast('.', missingDelimiterValue = "").ifBlank { null }
                    )
                }
                ProviderResult.Success(subtitles)
            }
        )
    }

    override suspend fun checkAvailability(
        mediaId: String,
        ids: ExternalIdSet,
        addonIds: List<String>
    ): ProviderResult<List<AvailabilityEntry>> {
        val streams = describeStreams(mediaId, ids, providerId)
        return when (streams) {
            is ProviderResult.Success -> {
                ProviderResult.Success(
                    listOf(
                        AvailabilityEntry(
                            addonId = providerId,
                            streamCount = streams.value.size,
                            bestQuality = streams.value.mapNotNull { it.quality }.maxByOrNull { qualityRank(it) },
                            hasSubtitles = streams.value.any { it.hasSubtitles },
                            languages = streams.value.mapNotNull { it.language }.distinct()
                        )
                    )
                )
            }
            is ProviderResult.Timeout -> ProviderResult.Timeout
            is ProviderResult.Failure -> streams
            is ProviderResult.Skipped -> streams
            is ProviderResult.CacheOnly -> ProviderResult.CacheOnly()
            is ProviderResult.Disabled -> ProviderResult.Disabled
            is ProviderResult.LocalOnly -> ProviderResult.LocalOnly
        }
    }

    override suspend fun describeStreams(
        mediaId: String,
        ids: ExternalIdSet,
        addonId: String?
    ): ProviderResult<List<StreamDescriptor>> {
        val type = requestType(ids)
        if (!supports("stream", type)) return ProviderResult.Skipped("addon does not support stream")
        val response = StremioAddonClient.getStreams(resolvedBaseUrl(), type, requestId(mediaId, ids), providerId, timeoutMs)
        return response.map(
            onSuccess = { body ->
                val streams = (body.streams ?: emptyList()).map { it.toDescriptor() }
                ProviderResult.Success(streams)
            }
        )
    }

    override suspend fun fetchArtwork(mediaId: String, ids: ExternalIdSet): ProviderResult<ArtworkEntry> {
        val metadata = fetchMetadata(mediaId, ids)
        return when (metadata) {
            is ProviderResult.Success -> ProviderResult.Success(
                ArtworkEntry(
                    posterUrl = metadata.value.posterUrl,
                    backdropUrl = metadata.value.backdropUrl,
                    logoUrl = null,
                    thumbnailUrl = metadata.value.posterUrl
                )
            )
            is ProviderResult.Timeout -> ProviderResult.Timeout
            is ProviderResult.Failure -> metadata
            is ProviderResult.Skipped -> metadata
            is ProviderResult.CacheOnly -> ProviderResult.CacheOnly()
            is ProviderResult.Disabled -> ProviderResult.Disabled
            is ProviderResult.LocalOnly -> ProviderResult.LocalOnly
        }
    }

    override suspend fun fetchCatalog(
        catalogId: String,
        type: String,
        extra: Map<String, String>
    ): ProviderResult<List<CatalogItemPreview>> {
        if (!supports("catalog", type)) return ProviderResult.Skipped("addon does not support catalog")
        val extraArgs = extra.toExtraArgs()
        val response = StremioAddonClient.getCatalog(resolvedBaseUrl(), type, catalogId, extraArgs, providerId, timeoutMs)
        return response.map(
            onSuccess = { body ->
                ProviderResult.Success((body.metas ?: emptyList()).map { it.toPreview() })
            }
        )
    }

    private suspend fun supports(resource: String, type: String): Boolean {
        val extension = extensionManifest
        if (extension != null) return extension.isResourceSupported(resource, type)
        val stremio = ensureStremioManifest()
        return stremio?.isResourceSupported(resource, type) == true
    }

    private suspend fun ensureStremioManifest(): StremioManifest? {
        stremioManifest?.let { return it }
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            val result = StremioAddonClient.getManifest(addonEntity.url, providerId, timeoutMs)
            when (result) {
                is StremioResult.Success -> {
                    stremioManifest = result.data
                    return result.data
                }
                is StremioResult.Failure -> {
                    val error = result.error
                    val isRetryable = error is com.example.calmsource.core.model.ExtensionError.Timeout ||
                        error is com.example.calmsource.core.model.ExtensionError.NetworkError
                    if (isRetryable && attempt < maxAttempts) {
                        delay((300L * attempt).coerceAtMost(2000L))
                        continue
                    }
                    return null
                }
            }
        }
        return null
    }

    private fun requestId(mediaId: String, ids: ExternalIdSet): String {
        return ids.imdbId
            ?: ids.custom["imdb"]
            ?: ids.custom["imdb_id"]
            ?: ids.tmdbId?.let { "tmdb:$it" }
            ?: ids.custom["stremio"]
            ?: ids.custom["stremioId"]
            ?: mediaId
    }

    private fun requestType(ids: ExternalIdSet): String {
        val explicit = ids.custom["type"]
            ?: ids.custom["stremioType"]
            ?: ids.custom["mediaType"]
        if (!explicit.isNullOrBlank()) return normalizeType(explicit)
        val types = extensionManifest?.types?.ifEmpty { null }
            ?: stremioManifest?.types?.ifEmpty { null }
        return types?.firstOrNull()?.let(::normalizeType) ?: "movie"
    }

    private fun normalizeType(type: String): String = when (type.lowercase()) {
        "show", "tv" -> "series"
        else -> type.lowercase()
    }

    private fun resolvedBaseUrl(): String {
        return StremioAddonClient.resolveUrl(addonEntity.url, providerId)
            .trimEnd('/')
            .removeSuffix("/manifest.json")
    }

    private fun StremioMeta.toEnrichedMetadata(): EnrichedMetadata {
        return EnrichedMetadata(
            title = name,
            originalTitle = null,
            overview = description,
            genres = genres ?: emptyList(),
            director = null,
            runtimeMinutes = runtime.toMinutes(),
            language = null,
            country = null,
            posterUrl = poster,
            backdropUrl = background,
            externalIds = buildMap {
                imdbId?.let { put("imdb", it) }
                if (id.startsWith("tt")) put("imdb", id)
                put("stremio", id)
            }
        )
    }

    private fun StremioMetaPreview.toPreview(): CatalogItemPreview {
        return CatalogItemPreview(
            id = id,
            type = type,
            name = name,
            posterUrl = poster,
            backgroundUrl = background,
            imdbId = imdbId ?: id.takeIf { it.startsWith("tt") }
        )
    }

    private fun StremioStream.toDescriptor(): StreamDescriptor {
        val streamTitle = listOfNotNull(name, title).joinToString(" ").ifBlank { null }
        return StreamDescriptor(
            title = streamTitle,
            url = url,
            infoHash = infoHash,
            quality = inferQuality(streamTitle),
            language = inferLanguage(streamTitle),
            hasSubtitles = streamTitle?.contains("sub", ignoreCase = true) == true
        )
    }

    private fun inferQuality(title: String?): String? {
        val value = title?.lowercase() ?: return null
        return when {
            value.contains("2160") || value.contains("4k") || value.contains("uhd") -> "2160p"
            value.contains("1080") || value.contains("fhd") -> "1080p"
            value.contains("720") || value.contains(" hd") -> "720p"
            value.contains("480") -> "480p"
            else -> null
        }
    }

    private fun inferLanguage(title: String?): String? {
        val value = title?.lowercase() ?: return null
        return when {
            value.contains("hindi") || value.contains(" hin ") -> "hi"
            value.contains("punjabi") || value.contains(" pan ") -> "pa"
            value.contains("tamil") -> "ta"
            value.contains("telugu") -> "te"
            value.contains("spanish") -> "es"
            value.contains("french") -> "fr"
            value.contains("english") || value.contains(" eng ") -> "en"
            else -> null
        }
    }

    private fun String?.toMinutes(): Int? {
        val value = this?.lowercase() ?: return null
        return Regex("(\\d+)").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun Map<String, String>.toExtraArgs(): String? {
        if (isEmpty()) return null
        return entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private fun qualityRank(quality: String): Int = when (quality.lowercase()) {
        "4k", "2160p" -> 4
        "1080p" -> 3
        "720p" -> 2
        "480p" -> 1
        else -> 0
    }

    private fun <T, R> StremioResult<T>.map(onSuccess: (T) -> ProviderResult<R>): ProviderResult<R> {
        return when (this) {
            is StremioResult.Success -> onSuccess(data)
            is StremioResult.Failure -> error.toProviderResult()
        }
    }

    private fun ExtensionError.toProviderResult(): ProviderResult<Nothing> {
        return when (this) {
            is ExtensionError.Timeout -> ProviderResult.Timeout
            else -> ProviderResult.Failure(
                errorCode = this::class.simpleName?.lowercase() ?: "extension_error",
                message = message
            )
        }
    }

    private fun parseExtensionManifest(value: String): ExtensionManifest? {
        if (value.isBlank() || value == "{}") return null
        return runCatching { json.decodeFromString<ExtensionManifest>(value) }.getOrNull()
    }

    private fun parseStremioManifest(value: String): StremioManifest? {
        if (value.isBlank() || value == "{}") return null
        return runCatching { json.decodeFromString<StremioManifest>(value) }.getOrNull()
    }

    companion object {
        private val capabilityParser = Json { ignoreUnknownKeys = true; isLenient = true }

        fun capabilitiesFor(addonEntity: ExtensionProviderEntity): Set<ProviderType> {
            val extensionManifest = runCatching {
                if (addonEntity.manifestJson.isBlank() || addonEntity.manifestJson == "{}") {
                    null
                } else {
                    capabilityParser.decodeFromString<ExtensionManifest>(addonEntity.manifestJson)
                }
            }.getOrNull()

            if (extensionManifest == null) {
                return setOf(
                    ProviderType.CATALOG,
                    ProviderType.METADATA,
                    ProviderType.RATING,
                    ProviderType.ARTWORK,
                    ProviderType.STREAM,
                    ProviderType.AVAILABILITY,
                    ProviderType.SUBTITLE
                )
            }

            val types = extensionManifest.types.ifEmpty {
                extensionManifest.catalogs.map { it.type }.ifEmpty { listOf("movie", "series") }
            }
            return buildSet {
                if (types.any { extensionManifest.isResourceSupported("catalog", it) }) add(ProviderType.CATALOG)
                if (types.any { extensionManifest.isResourceSupported("meta", it) }) {
                    add(ProviderType.METADATA)
                    add(ProviderType.RATING)
                    add(ProviderType.ARTWORK)
                }
                if (types.any { extensionManifest.isResourceSupported("stream", it) }) {
                    add(ProviderType.STREAM)
                    add(ProviderType.AVAILABILITY)
                }
                if (types.any { extensionManifest.isResourceSupported("subtitles", it) }) add(ProviderType.SUBTITLE)
            }
        }
    }
}
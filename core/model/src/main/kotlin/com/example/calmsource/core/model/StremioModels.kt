package com.example.calmsource.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class StremioCatalog(
    val type: String,
    val id: String,
    val name: String,
    val extra: List<StremioCatalogExtra>? = null
)

@Serializable
data class StremioCatalogExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String>? = null
)

@Serializable
data class StremioAddonConfig(
    val key: String,
    val type: String, // "text", "number", "password", "checkbox", "select"
    val title: String? = null,
    val options: List<String>? = null,
    val required: Boolean? = null,
    val default: String? = null
)

@Serializable
data class StremioBehaviorHints(
    val configurable: Boolean? = null,
    val configurationRequired: Boolean? = null,
    val adult: Boolean? = null,
    val p2p: Boolean? = null
)

@Serializable
data class StremioManifest(
    val id: String,
    val name: String,
    val description: String? = null,
    val version: String? = null,
    val logo: String? = null,
    val resources: List<JsonElement> = emptyList(),
    val types: List<String> = emptyList(),
    val catalogs: List<StremioCatalog> = emptyList(),
    val behaviorHints: StremioBehaviorHints? = null,
    val config: List<StremioAddonConfig>? = null
)

@Serializable
data class StremioMetaPreview(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null
)

@Serializable
data class StremioCatalogResponse(
    val metas: List<StremioMetaPreview>? = emptyList()
)

@Serializable
data class StremioMeta(
    val id: String,
    val type: String,
    val name: String,
    val genres: List<String>? = null,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    val videos: List<StremioVideo>? = null,
    val rtRating: String? = null,
    val metascore: String? = null,
    val simklRating: String? = null,
    val malRating: String? = null,
    val studios: List<String>? = null
)

@Serializable
data class StremioSkipTime(
    val interval: StremioSkipInterval,
    @SerialName("skip_type") val skipType: String
)

@Serializable
data class StremioSkipInterval(
    @SerialName("start_time") val startTime: Double,
    @SerialName("end_time") val endTime: Double
)

@Serializable
data class StremioVideo(
    val id: String? = null,
    val title: String? = null,
    @SerialName("name")
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val released: String? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
    val skipTimes: List<StremioSkipTime>? = null
)

fun StremioVideo.resolvedTitle(): String? =
    title?.trim()?.takeIf { it.isNotBlank() }
        ?: name?.trim()?.takeIf { it.isNotBlank() }

fun StremioVideo.episodeDisplayLabel(seasonFallback: Int = 1): String {
    val seasonNum = season ?: seasonFallback
    val episodeNum = episode
    val code = if (episodeNum != null) "S${seasonNum}E$episodeNum" else "S$seasonNum"
    val episodeName = resolvedTitle() ?: episodeNum?.let { "Episode $it" } ?: "Episode"
    return "$code: $episodeName"
}

fun StremioVideo.displayImageUrl(fallback: String? = null): String? =
    thumbnail?.takeIf { it.isNotBlank() } ?: fallback

@Serializable
data class StremioMetaResponse(
    val meta: StremioMeta? = null
)

@Serializable
data class StremioStream(
    val name: String? = null,
    val title: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val behaviorHints: Map<String, JsonElement>? = null
)

@Serializable
data class StremioStreamResponse(
    val streams: List<StremioStream>? = emptyList()
)

@Serializable
data class StremioSubtitle(
    val id: String = "",
    val lang: String = "",
    val url: String = ""
)

@Serializable
data class StremioSubtitleResponse(
    val subtitles: List<StremioSubtitle>? = emptyList()
)

fun StremioManifest.isResourceSupported(resourceName: String, requestType: String): Boolean {
    if (resources.isEmpty()) return false
    return resources.any { res ->
        when (res) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                res.content == resourceName && (types.isEmpty() || types.contains(requestType))
            }
            is kotlinx.serialization.json.JsonObject -> {
                val name = (res["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val types = (res["types"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                name == resourceName && (types?.contains(requestType) ?: (this.types.isEmpty() || this.types.contains(requestType)))
            }
            else -> false
        }
    }
}

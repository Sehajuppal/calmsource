package com.example.calmsource.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackendRating(
    val source: String,
    val value: String
)

@Serializable
data class BackendEnrichment(
    val simklRating: String? = null,
    val malRating: String? = null,
    val studios: List<String>? = null
)

@Serializable
data class BackendMeta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val rtRating: String? = null,
    val metascore: String? = null,
    val ratings: List<BackendRating>? = null,
    val enrichment: BackendEnrichment? = null,
    val videos: List<BackendVideo>? = null
)

@Serializable
data class BackendVideo(
    val id: String? = null,
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val released: String? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
    val skipTimes: List<BackendSkipTime>? = null
)

@Serializable
data class BackendSkipTime(
    val interval: BackendSkipInterval,
    val skip_type: String
)

@Serializable
data class BackendSkipInterval(
    val start_time: Double,
    val end_time: Double
)

@Serializable
data class BackendMetaResponse(
    val meta: BackendMeta? = null
)

@Serializable
data class BackendSearchResult(
    val id: String,
    val type: String,
    val name: String,
    val releaseInfo: String? = null,
    val genres: List<String>? = null
)

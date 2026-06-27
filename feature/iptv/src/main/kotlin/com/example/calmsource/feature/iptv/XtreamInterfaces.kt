package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.XtreamProviderConfig
import com.example.calmsource.core.model.XtreamCategory
import com.example.calmsource.core.model.XtreamLiveChannel
import com.example.calmsource.core.model.XtreamVodItem
import com.example.calmsource.core.model.XtreamSeriesItem
import com.example.calmsource.core.model.XtreamSeriesEpisode
import com.example.calmsource.core.model.XtreamShortEpgProgram

/** Client for interacting with Xtream-Codes API */
interface XtreamApiClient {
    suspend fun authenticate(config: XtreamProviderConfig, password: String): Boolean
    suspend fun getLiveCategories(config: XtreamProviderConfig, password: String): List<XtreamCategory>
    suspend fun getLiveStreams(config: XtreamProviderConfig, password: String, categoryId: String? = null): List<XtreamLiveChannel>
    suspend fun getVodCategories(config: XtreamProviderConfig, password: String): List<XtreamCategory>
    suspend fun getVodStreams(config: XtreamProviderConfig, password: String, categoryId: String? = null): List<XtreamVodItem>
    suspend fun getSeriesCategories(config: XtreamProviderConfig, password: String): List<XtreamCategory>
    suspend fun getSeries(config: XtreamProviderConfig, password: String, categoryId: String? = null): List<XtreamSeriesItem>
    suspend fun getSeriesEpisodes(config: XtreamProviderConfig, password: String, seriesId: String): List<XtreamSeriesEpisode>
    suspend fun getShortEpg(config: XtreamProviderConfig, password: String, streamId: String): List<XtreamShortEpgProgram>
}

/** Service to authenticate */
interface XtreamAuthValidator {
    suspend fun validate(serverUrl: String, username: String, password: String): Result<XtreamProviderConfig>
}

/** Background sync service for Xtream */
interface XtreamSyncService {
    suspend fun syncProvider(providerId: String)
}

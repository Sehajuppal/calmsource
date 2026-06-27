package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import com.example.calmsource.feature.extensions.ExtensionRepository
import com.example.calmsource.core.network.StremioAddonClient
import com.example.calmsource.core.network.StremioResult
import com.example.calmsource.core.network.UrlRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

import com.example.calmsource.core.model.TestEnvironment
import android.util.Log

class ExtensionSearchProviderImpl : ExtensionSearchProvider {
    override val id = "prov-extensions"
    override val name = "Extensions Indexer"
    override val priority = 70

    companion object {
        private const val EXTENSION_SEARCH_TIMEOUT_MS = 15_000L
        private const val MAX_SEARCH_CATALOGS_PER_PROVIDER = 4
        private const val MAX_SEARCH_RESULTS_PER_PROVIDER = 40
        private val isTest: Boolean get() = TestEnvironment.isTest

        init {
            setupStremioAddonClientDelegate()
        }

        fun setupStremioAddonClientDelegate() {
            StremioAddonClient.recordSignalDelegate = { providerId, url, isTimeout, errorMsg ->
                val targetProviderId = if (providerId.isNotEmpty()) {
                    providerId
                } else {
                    findProviderIdForUrl(url)
                }

                if (targetProviderId != null) {
                    val signal = if (isTimeout) {
                        SourceHealthSignal.PLAYBACK_TIMEOUT
                    } else {
                        SourceHealthSignal.PLAYBACK_FAILURE
                    }

                    // Record to SourceHealthRepository
                    com.example.calmsource.core.database.SourceHealthRepository.recordSignal(
                        sourceId = targetProviderId,
                        providerId = targetProviderId,
                        sourceType = PlaybackSourceType.EXTENSION,
                        signal = signal,
                        errorCategory = errorMsg
                    )

                    // Get updated score and update health in ExtensionRepository
                    val updatedScore = com.example.calmsource.core.database.SourceHealthRepository.getProviderHealth(targetProviderId)
                    if (updatedScore != null) {
                        val newHealth = when {
                            updatedScore.healthScore >= 70 -> ExtensionHealth.ACTIVE
                            updatedScore.healthScore in 40..69 -> ExtensionHealth.SLOW
                            else -> ExtensionHealth.FAILED
                        }
                        com.example.calmsource.feature.extensions.ExtensionRepository.updateHealth(targetProviderId, newHealth)
                    }
                }
            }
        }

        private fun findProviderIdForUrl(url: String): String? {
            val extensions = com.example.calmsource.feature.extensions.ExtensionRepository.getExtensions()
            val targetUrlClean = url.substringBefore("?").substringBefore("#")
            
            var bestMatchProvider: ExtensionProvider? = null
            var bestMatchLength = 0
            
            for (provider in extensions) {
                val resolved = StremioAddonClient.resolveUrl(provider.url, provider.id)
                val resolvedBase = resolved.substringBefore("/manifest.json")
                if (targetUrlClean.startsWith(resolvedBase) && resolvedBase.length > bestMatchLength) {
                    bestMatchProvider = provider
                    bestMatchLength = resolvedBase.length
                }
            }
            
            if (bestMatchProvider != null) {
                return bestMatchProvider.id
            }
            
            try {
                val targetUri = java.net.URI(url)
                val targetHost = targetUri.host?.lowercase()
                if (targetHost != null) {
                    val hostMatch = extensions.firstOrNull {
                        val providerUri = java.net.URI(StremioAddonClient.resolveUrl(it.url, it.id))
                        providerUri.host?.lowercase() == targetHost
                    }
                    if (hostMatch != null) return hostMatch.id
                }
            } catch (e: Exception) {
                Log.w(
                    "ExtensionSearch",
                    "Failed to resolve extension host for url=${UrlRedactor.redactUrl(url)}"
                )
            }

            return null
        }

        internal fun searchableCatalogsForQuery(provider: ExtensionProvider): List<ExtensionCatalog> {
            if (!provider.capabilities.contains(ExtensionCapability.CatalogProvider)) return emptyList()
            return provider.manifest
                ?.catalogs
                .orEmpty()
                .asSequence()
                .filter { catalog -> catalog.extra.orEmpty().any { it.name == "search" } }
                .distinctBy { "${it.type}:${it.id}" }
                .take(MAX_SEARCH_CATALOGS_PER_PROVIDER)
                .toList()
        }
    }

    override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = channelFlow {
        if (query.query.isBlank()) {
            send(SearchProviderResult(id, name, query))
            return@channelFlow
        }

        val activeExtensions = com.example.calmsource.feature.extensions.ExtensionRepository
            .awaitExtensions()
            .filter {
                it.isEnabled &&
                    it.health != ExtensionHealth.NEEDS_CONFIGURATION &&
                    it.health != ExtensionHealth.INVALID_MANIFEST &&
                    it.health != ExtensionHealth.FAILED &&
                    it.health != ExtensionHealth.SLOW
            }
        val mediaItems = mutableListOf<MediaItem>()
        val sources = mutableListOf<StreamSource>()

        if (activeExtensions.isEmpty()) {
            send(SearchProviderResult(id, name, query))
            return@channelFlow
        }

        val mutex = kotlinx.coroutines.sync.Mutex()
        val timeoutMs = EXTENSION_SEARCH_TIMEOUT_MS

        val jobs = supervisorScope {
            activeExtensions.map { provider ->
                launch(Dispatchers.IO) {
                    try {
                        kotlinx.coroutines.withTimeout(timeoutMs) {
                            val isDemo = isTest

                            if (isDemo) {
                                // Demo/mock fallback logic
                                val queryNorm = query.query.normalizeForSearch()
                                if (queryNorm.contains("slow") || provider.health == ExtensionHealth.SLOW) {
                                    delay(1200) // Exceeds search timeout
                                }
                                if (queryNorm.contains("fail") || provider.health == ExtensionHealth.FAILED) {
                                    throw RuntimeException("Extension indexer failed unexpectedly")
                                }

                                val matched = (FakeData.movies + FakeData.shows).filter {
                                    it.title.normalizeForSearch().contains(queryNorm)
                                }
                                val matchedSources = mutableListOf<StreamSource>()
                                if (provider.capabilities.contains(ExtensionCapability.StreamProvider)) {
                                    matched.forEach { item ->
                                        val extSources = FakeData.getSourcesForMedia(item.id).filter {
                                            it.extensionId == provider.id
                                        }
                                        matchedSources.addAll(extSources)
                                    }
                                }
                                mutex.withLock {
                                    if (provider.capabilities.contains(ExtensionCapability.SearchCatalogProvider) || provider.capabilities.contains(ExtensionCapability.CatalogProvider)) {
                                        mediaItems.addAll(matched)
                                    }
                                    sources.addAll(matchedSources)
                                    send(SearchProviderResult(
                                        providerId = id,
                                        providerName = name,
                                        query = query,
                                        mediaItems = mediaItems.distinctBy { it.id },
                                        streamSources = sources.distinctBy { it.id }
                                    ))
                                }
                            } else {
                                // Real Stremio catalog search querying
                                val searchableCatalogs = searchableCatalogsForQuery(provider)
                                if (searchableCatalogs.isNotEmpty()) {
                                    val resolvedBase = StremioAddonClient.resolveUrl(provider.url, provider.id).removeSuffix("/manifest.json").trimEnd('/')
                                    val queryEncoded = java.net.URLEncoder.encode(query.query, "UTF-8").replace("+", "%20")

                                    val results = supervisorScope {
                                        searchableCatalogs.map { catalog ->
                                            async(Dispatchers.IO) {
                                                val res = StremioAddonClient.getCatalog(
                                                    resolvedBase,
                                                    catalog.type,
                                                    catalog.id,
                                                    "search=$queryEncoded",
                                                    provider.id,
                                                    timeoutMs
                                                )
                                                if (res is StremioResult.Success) {
                                                    res.data.metas.orEmpty().filter { meta ->
                                                        meta.id.isNotBlank() && meta.name.isNotBlank()
                                                    }.map { meta ->
                                                        MediaItem(
                                                            id = meta.id,
                                                            title = meta.name,
                                                            type = if (meta.type.ifBlank { catalog.type } == "series") {
                                                                MediaType.SHOW
                                                            } else {
                                                                MediaType.MOVIE
                                                            },
                                                            overview = meta.description ?: "Stremio catalog result",
                                                            posterUrl = meta.poster,
                                                            externalIds = buildMap {
                                                                meta.imdbId?.takeIf { it.isNotBlank() }?.let { put("imdb", it) }
                                                                if (meta.id.startsWith("tt") && meta.id.drop(2).all(Char::isDigit)) {
                                                                    put("imdb", meta.id)
                                                                }
                                                                put("stremio", meta.id)
                                                            }
                                                        )
                                                    }
                                                } else {
                                                    emptyList()
                                                }
                                            }
                                        }.awaitAll().flatten()
                                    }

                                    mutex.withLock {
                                        mediaItems.addAll(results.take(MAX_SEARCH_RESULTS_PER_PROVIDER))
                                        send(SearchProviderResult(
                                            providerId = id,
                                            providerName = name,
                                            query = query,
                                            mediaItems = mediaItems.distinctBy { it.id },
                                            streamSources = sources.distinctBy { it.id }
                                        ))
                                    }
                                }
                            }
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        StremioAddonClient.recordSignalDelegate?.invoke(provider.id, provider.url, true, "Timeout of ${timeoutMs}ms exceeded")
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        val redacted = UrlRedactor.redactErrorMessage(e.localizedMessage ?: e.javaClass.simpleName)
                        StremioAddonClient.recordSignalDelegate?.invoke(provider.id, provider.url, false, redacted)
                    }
                }
            }
        }
        jobs.forEach { it.join() }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Extension Hub health details on ExtensionProvider
// ═══════════════════════════════════════════════════════════════════

val ExtensionProvider.healthy: Boolean
    get() = isEnabled && health == ExtensionHealth.ACTIVE

val ExtensionProvider.slow: Boolean
    get() = isEnabled && health == ExtensionHealth.SLOW

val ExtensionProvider.failedRecently: Boolean
    get() = isEnabled && health == ExtensionHealth.FAILED

val ExtensionProvider.needsAttention: Boolean
    get() = isEnabled && (health == ExtensionHealth.NEEDS_CONFIGURATION || 
            health == ExtensionHealth.INVALID_MANIFEST || 
            health == ExtensionHealth.FAILED)

suspend fun ExtensionProvider.refreshHealth() {
    val resolved = StremioAddonClient.resolveUrl(this.url, this.id)
    val result = StremioAddonClient.getManifest(resolved, this.id)
    if (result is StremioResult.Success) {
        com.example.calmsource.core.database.SourceHealthRepository.recordSuccess(
            sourceId = this.id,
            providerId = this.id,
            sourceType = PlaybackSourceType.EXTENSION
        )
        val checkedHealth = com.example.calmsource.feature.extensions.ExtensionRepository.checkAddonHealth(this)
        com.example.calmsource.feature.extensions.ExtensionRepository.updateHealth(this.id, checkedHealth)
    } else if (result is StremioResult.Failure) {
        val signal = when (result.error) {
            is ExtensionError.Timeout -> SourceHealthSignal.PLAYBACK_TIMEOUT
            else -> SourceHealthSignal.PLAYBACK_FAILURE
        }
        com.example.calmsource.core.database.SourceHealthRepository.recordSignal(
            sourceId = this.id,
            providerId = this.id,
            sourceType = PlaybackSourceType.EXTENSION,
            signal = signal,
            errorCategory = result.error.message
        )
        com.example.calmsource.feature.extensions.ExtensionRepository.updateHealth(this.id, ExtensionHealth.FAILED)
    }
}

fun ExtensionProvider.disable() {
    com.example.calmsource.feature.extensions.ExtensionRepository.toggleExtension(this.id, false)
}

fun ExtensionProvider.enable() {
    com.example.calmsource.feature.extensions.ExtensionRepository.toggleExtension(this.id, true)
}

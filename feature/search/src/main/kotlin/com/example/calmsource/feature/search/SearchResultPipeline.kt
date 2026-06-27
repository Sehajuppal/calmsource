/**
 * Search result pipeline: scoring, deduplication, and grouping.
 *
 * This file contains the three core pipeline stages that transform raw
 * [SearchProviderResult]s into ranked, grouped [SearchResultGroup]s ready
 * for UI consumption:
 *
 * 1. **[SearchResultRanker]** — scores individual [StreamSource]s and applies
 *    post-merge boosts (title match, favorites, history).
 * 2. **[SearchResultDeduplicator]** — collapses duplicate media items into a
 *    single result with all watch options merged.
 * 3. **[SearchResultMerger]** — normalizes heterogeneous provider results
 *    (channels, programs, media items) into [NormalizedSearchResult]s,
 *    orchestrates dedup → rank, and splits results into classified groups.
 *
 * All scoring magic numbers are defined in [ScoringConstants] for easy tuning.
 *
 * @see ScoringConstants for the named constant definitions
 * @see UniversalSearchEngineImpl for the concurrent provider orchestration
 */
package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import com.example.calmsource.feature.extensions.ExtensionRepository
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.sourceintelligence.models.toRawSourceInput

private data class HealthLabelEntry(val label: String, val timestamp: Long)

// SEARCH-BUG-3: Bounded LRU cache with 30-min TTL and synchronization
private val healthLabelCache = object : java.util.LinkedHashMap<String, HealthLabelEntry>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HealthLabelEntry>?): Boolean = size > 2000
}

private fun StreamSource.isIptvSource(): Boolean {
    return extensionId.startsWith("iptv-") ||
            extensionId.startsWith("xtream-") ||
            url.startsWith("xtream://")
}

val WatchOption.healthLabel: String?
    get() = synchronized(healthLabelCache) {
        val entry = healthLabelCache[this.source.id]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp > 30 * 60 * 1000L) {
                healthLabelCache.remove(this.source.id)
                null
            } else {
                entry.label
            }
        } else {
            null
        }
    }

val NormalizedSearchResult.healthLabel: String?
    get() = this.bestMatchOption?.healthLabel

/**
 * Scores and ranks [NormalizedSearchResult]s.
 *
 * Two-phase scoring:
 * - **Phase 1** ([calculateSourceScore]): per-source scoring based on language,
 *   audio, source type, resolution, seeds, and provider health.
 * - **Phase 2** ([rankResults]): per-result boosts for title match, favorites,
 *   and watch history, then sorts descending by score.
 */
object SearchResultRanker {
    
    private val yearRegex = Regex("\\(\\d{4}\\)")
    private val bracketResRegex = Regex("\\[(?:HD|4K|FHD|SD|UHD|1080p|720p|480p)\\]", RegexOption.IGNORE_CASE)
    private val parenResRegex = Regex("\\((?:HD|4K|FHD|SD|UHD|1080p|720p|480p)\\)", RegexOption.IGNORE_CASE)
    private val languageRegex = Regex("\\s+(Hindi|English|Tamil|Telugu|Spanish|French|German)\\s*$", RegexOption.IGNORE_CASE)
    private val whitespaceRegex = Regex("\\s+")

    /**
     * Calculates a numeric quality score for a single [StreamSource].
     *
     * The score is the sum of independent bonuses and penalties across six
     * categories evaluated in order. See [ScoringConstants] for the exact
     * weight of each signal.
     *
     * @param source the stream source to evaluate
     * @param prefs the current user preferences that influence scoring
     * @return an integer score — higher is better, can be negative
     */
    suspend fun calculateSourceScore(
        source: StreamSource,
        prefs: UserPreferences,
        debridCacheMap: Map<String, Boolean> = emptyMap()
    ): Int {
        var score = 0

        // Fetch Intelligence Features
        val intelligence = com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(
            source.toRawSourceInput(),
            lowDataModeEnabled = prefs.preferLowerDataUsage
        )
        val rankingFeatures = intelligence.rankingFeatures

        // 1. Language matching
        if (source.language.equals(prefs.primaryLanguage, ignoreCase = true)) {
            score += ScoringConstants.PRIMARY_LANGUAGE_BONUS
        } else if (source.language.equals(prefs.secondaryLanguage, ignoreCase = true)) {
            score += ScoringConstants.SECONDARY_LANGUAGE_BONUS
        } else {
            score += ScoringConstants.FOREIGN_LANGUAGE_PENALTY
        }

        // 2. Dual Audio & dubbing preferences
        if (source.isDualAudio && prefs.preferDualAudio) {
            score += ScoringConstants.DUAL_AUDIO_BONUS
        }
        if (source.isDubbed && prefs.preferDubbedAudio) {
            score += ScoringConstants.DUBBED_AUDIO_BONUS
        }
        if (source.isSubbed && source.language.equals(prefs.subtitleLanguage, ignoreCase = true)) {
            score += ScoringConstants.SUBTITLE_MATCH_BONUS
        }

        // 3. Source types preferences
        val isDebrid = source.extensionId.startsWith("deb-")
        val isIptv = source.isIptvSource()

        if (isIptv && prefs.preferIptvExactMatch) {
            score += ScoringConstants.IPTV_EXACT_MATCH_BONUS
        }
        if (isDebrid) {
            // Query debrid cache availability via the repository.
            // Extracts the real torrent info-hash from a magnet URL when available;
            // skips the cache check entirely if no real hash can be determined.
            val isCached = try {
                val hash = if (source.url.startsWith("magnet:")) {
                    source.url.lowercase().substringAfter("btih:").substringBefore("&").takeIf { it.isNotEmpty() }
                } else {
                    null
                }
                if (hash != null) {
                    val precomputed = debridCacheMap[hash]
                    if (precomputed != null) {
                        precomputed
                    } else {
                        val accounts = com.example.calmsource.feature.debrid.DebridRepository.listAccounts()
                        val connectedAccount = accounts.firstOrNull { it.isConnected }
                        if (connectedAccount != null) {
                            val cacheResult = com.example.calmsource.feature.debrid.DebridRepository.checkCachedAvailability(
                                connectedAccount.providerType,
                                listOf(hash)
                            )
                            cacheResult[hash]?.isCached ?: false
                        } else {
                            false
                        }
                    }
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }

            if (isCached) {
                score += ScoringConstants.DEBRID_CACHED_BONUS
            }
            if (prefs.preferCachedDebrid && isCached) {
                score += ScoringConstants.DEBRID_PREFERRED_CACHED_BONUS
            }
        }


        // 4. Resolution scaling
        val resHeight = if (rankingFeatures.resolutionHeight > 0) {
            rankingFeatures.resolutionHeight
        } else {
            when (source.resolution.uppercase()) {
                "8K", "4320P" -> 4320
                "4K", "2160P" -> 2160
                "1440P" -> 1440
                "1080P" -> 1080
                "720P" -> 720
                "SD", "480P", "576P" -> 480
                else -> 0
            }
        }

        when {
            resHeight >= 2160 -> score += ScoringConstants.RESOLUTION_4K
            resHeight >= 1080 -> score += ScoringConstants.RESOLUTION_1080P
            resHeight >= 720 -> score += ScoringConstants.RESOLUTION_720P
            resHeight > 0 -> {
                if (prefs.hideLowQuality) score += ScoringConstants.LOW_QUALITY_PENALTY else score += ScoringConstants.RESOLUTION_SD
            }
            else -> score += ScoringConstants.RESOLUTION_UNKNOWN
        }

        if (prefs.preferHighestQuality && resHeight >= 1080) {
            score += ScoringConstants.HIGH_QUALITY_PREFERENCE_BONUS
        }

        if (rankingFeatures.isHugeSize && !prefs.preferHighestQuality) {
            // "Do not let huge file size automatically beat a balanced 1080p source."
            score -= 20
        }

        // Add practicalScore impact (-50 to +50)
        score += rankingFeatures.practicalScore - 50

        // 5. Seeds / Quality signals
        source.seeds?.let {
            score += (it / ScoringConstants.SEED_DIVISOR).coerceAtMost(ScoringConstants.MAX_SEEDS_BONUS)
        }

        // 6. Provider Health adjustments
        val health = getProviderHealth(source.extensionId)
        when (health) {
            ProviderHealth.HEALTHY -> score += ScoringConstants.PROVIDER_HEALTHY_BONUS
            ProviderHealth.SLOW -> score += ScoringConstants.PROVIDER_SLOW_PENALTY
            ProviderHealth.FAILED -> score += ScoringConstants.PROVIDER_FAILED_PENALTY
        }

        // 7. Provider Priority adjustments
        val priority = getProviderPriority(source.extensionId)
        if (priority != null) {
            score += (100 - priority).coerceAtLeast(0)
        }

        // 8. Source and Provider health persistence lookup & scoring
        val sourceHealth = SourceHealthRepository.getSourceHealth(source.id, readonly = true)
        var label: String? = null
        if (sourceHealth != null) {
            when (sourceHealth.reliabilityTier) {
                SourceReliabilityTier.BLOCKED -> score += ScoringConstants.SOURCE_BLOCKED_PENALTY
                SourceReliabilityTier.POOR -> score += ScoringConstants.SOURCE_POOR_PENALTY
                SourceReliabilityTier.UNSTABLE -> score += ScoringConstants.SOURCE_UNSTABLE_PENALTY
                SourceReliabilityTier.EXCELLENT -> score += ScoringConstants.SOURCE_EXCELLENT_BOOST
                else -> { /* Good, Unknown do not get penalty/boost */ }
            }
            
            // lastSuccessTime within 24 hours: +100
            val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            if (sourceHealth.lastSuccessTime > oneDayAgo) {
                score += ScoringConstants.SOURCE_RECENT_SUCCESS_BOOST
            }

            label = when {
                sourceHealth.userHidden || sourceHealth.reliabilityTier == SourceReliabilityTier.BLOCKED -> "Failed recently"
                sourceHealth.reliabilityTier == SourceReliabilityTier.POOR -> "Failed recently"
                sourceHealth.reliabilityTier == SourceReliabilityTier.UNSTABLE -> "Unstable"
                sourceHealth.lastSuccessTime > oneDayAgo -> "Recently worked"
                sourceHealth.reliabilityTier == SourceReliabilityTier.EXCELLENT || sourceHealth.reliabilityTier == SourceReliabilityTier.GOOD -> "Reliable"
                else -> null
            }
        }

        val providerHealthDb = SourceHealthRepository.getProviderHealth(source.extensionId, readonly = true)
        if (providerHealthDb != null && providerHealthDb.healthScore < 40) {
            score += ScoringConstants.PROVIDER_UNHEALTHY_PENALTY
            if (label == null) {
                label = "Unstable"
            }
        }

        synchronized(healthLabelCache) {
            if (label != null) {
                healthLabelCache[source.id] = HealthLabelEntry(label, System.currentTimeMillis())
            } else {
                healthLabelCache.remove(source.id)
            }
        }

        return score
    }

    // SEARCH-BUG-5: Build lookup maps once instead of O(N×M) linear scans per call
    private var extensionMapCache: Map<String, com.example.calmsource.core.model.ExtensionProvider>? = null
    private var iptvProviderMapCache: Map<String, com.example.calmsource.core.model.IPTVProvider>? = null

    internal fun invalidateLookupCaches() {
        extensionMapCache = null
        iptvProviderMapCache = null
    }

    private fun getExtensionMap(): Map<String, com.example.calmsource.core.model.ExtensionProvider> {
        return extensionMapCache ?: com.example.calmsource.feature.extensions.ExtensionRepository.getExtensions()
            .associateBy { it.id }
            .also { extensionMapCache = it }
    }

    private fun getIptvProviderMap(): Map<String, com.example.calmsource.core.model.IPTVProvider> {
        return iptvProviderMapCache ?: com.example.calmsource.feature.iptv.IPTVRepository.providers.value
            .associateBy { it.id }
            .also { iptvProviderMapCache = it }
    }

    private fun getProviderPriority(extensionId: String): Int? {
        return getExtensionMap()[extensionId]?.priority
    }

    private fun getProviderHealth(extensionId: String): ProviderHealth {
        val iptvMatch = getIptvProviderMap()[extensionId]
        if (iptvMatch != null) return iptvMatch.health

        val extMatch = getExtensionMap()[extensionId]
        if (extMatch != null) {
            return when (extMatch.health) {
                ExtensionHealth.ACTIVE -> ProviderHealth.HEALTHY
                ExtensionHealth.SLOW -> ProviderHealth.SLOW
                ExtensionHealth.FAILED -> ProviderHealth.FAILED
                ExtensionHealth.DISABLED -> ProviderHealth.FAILED
                ExtensionHealth.INVALID_MANIFEST -> ProviderHealth.FAILED
                else -> ProviderHealth.HEALTHY
            }
        }

        return ProviderHealth.HEALTHY
    }

    /**
     * Applies post-merge boosts to a list of [NormalizedSearchResult]s and
     * returns them sorted by descending score.
     *
     * Boosts applied:
     * - [ScoringConstants.EXACT_TITLE_MATCH_BONUS] for exact query match
     * - [ScoringConstants.FAVORITES_BONUS] for favorited items
     * - [ScoringConstants.HISTORY_BONUS] for items in watch history
     *
     * @param results the pre-scored results to re-rank
     * @param query the original search query string
     * @param prefs the current user preferences
     * @param favorites list of media item IDs the user has favorited
     * @param history list of media item IDs in the user's watch history
     * @return results sorted by descending final score
     */
    fun rankResults(
        results: List<NormalizedSearchResult>,
        query: String,
        prefs: UserPreferences,
        favorites: List<String> = emptyList(),
        history: List<String> = emptyList(),
        recentQueries: List<String> = emptyList()
    ): List<NormalizedSearchResult> {
        val normalizedRecentQueries = recentQueries
            .map { normalizeForTitleMatch(it).lowercase() }
            .filter { it.isNotBlank() }
        return results.map { result ->
            var finalScore = result.score
            
            // 1. Exact title match — normalize both sides for comparison:
            //    Strip colons, dashes, year suffixes like "(2017)", resolution tags
            //    like "[HD]" or "(4K)", and language suffixes.
            val titleClean = normalizeForTitleMatch(result.mediaItem.title)
            val queryClean = normalizeForTitleMatch(query)
            val compactTitle = titleClean.replace(" ", "")
            val compactQuery = queryClean.replace(" ", "")
            if (
                titleClean.equals(queryClean, ignoreCase = true) ||
                compactTitle.equals(compactQuery, ignoreCase = true)
            ) {
                finalScore += ScoringConstants.EXACT_TITLE_MATCH_BONUS
            } else {
                val distance = editDistance(compactTitle.lowercase(), compactQuery.lowercase())
                val maxDistance = if (compactQuery.length < 6) 1 else 2
                if (distance <= maxDistance) {
                    finalScore += ScoringConstants.EXACT_TITLE_MATCH_BONUS / 2
                }
            }
            
            // 2. Favorites / History
            if (favorites.contains(result.mediaItem.id)) {
                finalScore += ScoringConstants.FAVORITES_BONUS
            }
            if (history.contains(result.mediaItem.id)) {
                finalScore += ScoringConstants.HISTORY_BONUS
            }

            // 3. Recent-search affinity — nudge up titles related to the user's recent queries.
            if (normalizedRecentQueries.isNotEmpty()) {
                val titleNorm = normalizeForTitleMatch(result.mediaItem.title).lowercase()
                if (titleNorm.isNotBlank() && normalizedRecentQueries.any { rq ->
                        titleNorm.contains(rq) || rq.contains(titleNorm)
                    }) {
                    finalScore += ScoringConstants.RECENT_QUERY_BONUS
                }
            }

            result.copy(score = finalScore)
        }.sortedWith(compareByDescending<NormalizedSearchResult> { it.score }.thenBy { it.mediaItem.id })
    }

    /**
     * Normalizes a title or query string for exact-match comparison.
     *
     * Strips: colons, dashes, year suffixes like "(2017)", resolution tags
     * like "[HD]" or "(4K)", and common language suffixes.
     */
    internal fun normalizeForTitleMatch(input: String): String {
        var s = input
            .replace(":", "")
            .replace("-", "")
        s = s.replace(yearRegex, "")
        // Strip resolution in brackets: "[HD]", "[4K]", "[FHD]"
        s = s.replace(bracketResRegex, "")
        // Strip resolution in parentheses: "(4K)", "(HD)"
        s = s.replace(parenResRegex, "")
        // Strip trailing language suffixes: "Hindi", "English", etc.
        s = s.replace(languageRegex, "")
        return s.replace(whitespaceRegex, " ").trim()
    }

    private fun editDistance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1
                )
            }
            previous = current
        }
        return previous[right.length]
    }
}

/**
 * Collapses duplicate [NormalizedSearchResult]s that share the same media item ID.
 *
 * When duplicates are found, their watch options, languages, and source types
 * are merged into a single result. The best watch option is selected by
 * re-scoring each option via [SearchResultRanker.calculateSourceScore].
 */
object SearchResultDeduplicator {
    
    /**
     * Deduplicates a list of [NormalizedSearchResult]s by media item ID.
     *
     * @param results the input results (may contain duplicates)
     * @param hideDuplicates if `false`, returns [results] unchanged
     * @param prefs user preferences used to pick the best watch option
     * @return deduplicated results with merged watch options
     */
    suspend fun deduplicate(
        results: List<NormalizedSearchResult>,
        hideDuplicates: Boolean,
        prefs: UserPreferences,
        precomputedScores: Map<StreamSource, Int> = emptyMap(),
        debridCacheMap: Map<String, Boolean> = emptyMap()
    ): List<NormalizedSearchResult> {
        if (!hideDuplicates) return results
        
        val grouped = results.groupBy { it.mediaItem.id }
        
        return grouped.map { (_, resultList) ->
            if (resultList.size == 1) {
                resultList.first()
            } else {
                val allOptions = resultList.flatMap { it.watchOptions }.distinctBy { option ->
                    val url = option.source.url
                    if (url.startsWith("magnet:")) {
                        url.lowercase().substringAfter("btih:").substringBefore("&")
                    } else if (url.isNotEmpty()) {
                        url.lowercase()
                    } else {
                        option.id
                    }
                }
                val allTypes = resultList.flatMap { it.availableFrom }.distinct().sortedBy { it.ordinal }
                val allLanguages = resultList.flatMap { it.languages }.distinct()
                val isDual = resultList.any { it.isDualAudio }
                val scores = allOptions.associateWith { option ->
                    precomputedScores[option.source] ?: SearchResultRanker.calculateSourceScore(option.source, prefs, debridCacheMap)
                }
                
                val sortedOptions = allOptions.sortedWith(
                    compareByDescending<WatchOption> { option ->
                        scores[option] ?: 0
                    }.thenBy { it.id }
                )
                val bestOption = sortedOptions.firstOrNull()
                
                val primary = resultList.maxByOrNull { it.score } ?: resultList.first()
                // Merge externalIds from ALL results so metadata-enriched IDs aren't lost
                val mergedExternalIds = buildMap {
                    resultList.forEach { result -> putAll(result.mediaItem.externalIds) }
                }
                val mergedMediaItem = primary.mediaItem.copy(externalIds = mergedExternalIds)
                primary.copy(
                    mediaItem = mergedMediaItem,
                    availableFrom = allTypes,
                    languages = allLanguages,
                    watchOptions = sortedOptions,
                    bestMatchOption = bestOption ?: primary.bestMatchOption,
                    isDualAudio = isDual,
                    score = resultList.maxOf { it.score }
                )
            }
        }
    }
}

/**
 * Normalizes heterogeneous [SearchProviderResult]s into [NormalizedSearchResult]s,
 * orchestrates deduplication and ranking, and splits the final list into
 * classified [SearchResultGroup]s (Top Results, Movies, Shows, Live Channels, etc.).
 */
object SearchResultMerger {

    private val alphaNumRegex = Regex("[^a-z0-9]")

    private data class SourceContribution(
        val source: StreamSource,
        val owner: SearchProviderResult
    )

    private fun sourceType(contribution: SourceContribution): SourceType {
        val source = contribution.source
        val providerId = contribution.owner.providerId
        return when {
            source.isIptvSource() ||
                providerId == "prov-vod" ||
                providerId.startsWith("prov-iptv") -> SourceType.IPTV
            source.extensionId.startsWith("deb-") ||
                providerId == "prov-debrid" -> SourceType.DEBRID
            else -> SourceType.EXTENSION
        }
    }

    private fun sourceMatchesMedia(
        contribution: SourceContribution,
        mediaItem: MediaItem
    ): Boolean {
        val source = contribution.source
        if (source.id == mediaItem.id) return true

        val ownerItems = contribution.owner.mediaItems.distinctBy { it.id }
        if (ownerItems.isNotEmpty() && ownerItems.none { it.id == mediaItem.id }) {
            return false
        }

        val sourceNorm = source.name.lowercase().replace(alphaNumRegex, "").replace("iptv", "")
        val mediaNorm = mediaItem.title.lowercase().replace(alphaNumRegex, "").replace("iptv", "")
        if (sourceNorm.isNotEmpty() && mediaNorm.isNotEmpty() &&
            (sourceNorm.contains(mediaNorm) || mediaNorm.contains(sourceNorm))
        ) {
            return true
        }

        return ownerItems.size == 1 && ownerItems.first().id == mediaItem.id
    }

    private fun mediaProvenance(providerResult: SearchProviderResult): List<SourceType> {
        return if (providerResult.providerId == "prov-extensions") {
            listOf(SourceType.EXTENSION)
        } else {
            emptyList()
        }
    }

    /**
     * Infers language from channel/program name or category using keyword heuristics.
     * Returns "Unknown" if no language keyword is detected — avoids hardcoding a default.
     */
    private fun inferLanguageFromName(name: String, extra: String? = null): String {
        val combined = "$name ${extra ?: ""}".lowercase()
        return when {
            combined.contains("hindi") -> "Hindi"
            combined.contains("tamil") -> "Tamil"
            combined.contains("telugu") -> "Telugu"
            combined.contains("spanish") || combined.contains("castellano") || combined.contains("español") -> "Spanish"
            combined.contains("french") || combined.contains("français") -> "French"
            combined.contains("german") || combined.contains("deutsch") -> "German"
            combined.contains("english") || combined.contains("espn") || combined.contains("hbo") -> "English"
            else -> "Unknown"
        }
    }

    /**
     * Merges raw provider results into grouped, ranked search output.
     *
     * Pipeline: normalize → deduplicate → rank → classify into groups.
     *
     * @param providerResults raw results from all search providers
     * @param query the original search query
     * @param prefs user preferences for scoring and filtering
     * @param favorites media item IDs the user has favorited
     * @param history media item IDs in the user's watch history
     * @return classified groups ready for UI rendering
     */
    suspend fun merge(
        providerResults: List<SearchProviderResult>,
        query: String,
        prefs: UserPreferences,
        favorites: List<String> = emptyList(),
        history: List<String> = emptyList(),
        recentQueries: List<String> = emptyList()
    ): List<SearchResultGroup> {
        SearchResultRanker.invalidateLookupCaches()
        val normalizedResults = mutableListOf<NormalizedSearchResult>()
        val settingsRoutes = mutableListOf<String>()
        val sourceContributions = providerResults.flatMap { providerResult ->
            providerResult.streamSources.map { source ->
                SourceContribution(source, providerResult)
            }
        }

        // Precompute / batch debrid cache availability
        val allSources = providerResults.flatMap { it.streamSources }
        val uniqueHashes = allSources.mapNotNull { source ->
            if (source.extensionId.startsWith("deb-") && source.url.startsWith("magnet:")) {
                source.url.lowercase().substringAfter("btih:").substringBefore("&").takeIf { it.isNotEmpty() }
            } else {
                null
            }
        }.distinct()
        val debridCacheMap = try {
            val accounts = com.example.calmsource.feature.debrid.DebridRepository.listAccounts()
            val connectedAccount = accounts.firstOrNull { it.isConnected }
            if (connectedAccount != null && uniqueHashes.isNotEmpty()) {
                val cacheResult = com.example.calmsource.feature.debrid.DebridRepository.checkCachedAvailability(
                    connectedAccount.providerType,
                    uniqueHashes
                )
                uniqueHashes.associateWith { hash ->
                    cacheResult[hash]?.isCached ?: false
                }
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }

        val sourceScores = mutableMapOf<StreamSource, Int>()

        providerResults.forEach { provResult ->
            settingsRoutes.addAll(provResult.settingsRoutes)

            // Convert raw channels to NormalizedSearchResults
            provResult.channels.forEach { channel ->
                // Derive language from channel category/name heuristic — avoid hardcoding
                val channelLang = inferLanguageFromName(channel.name, channel.category)
                val pseudoSource = StreamSource(
                    id = channel.id,
                    name = channel.name,
                    url = channel.streamUrl,
                    extensionId = "iptv-live",
                    resolution = "Live",
                    language = channelLang
                )
                val watchOption = WatchOption(
                    id = channel.id,
                    title = channel.name,
                    source = pseudoSource,
                    type = SourceType.IPTV,
                    languageLabel = "Live IPTV Stream"
                )
                val mediaItem = MediaItem(
                    id = channel.id,
                    title = channel.name,
                    type = MediaType.MOVIE, // Live channels mapped under Channels group
                    overview = "Live Channel in ${channel.category ?: "General"}",
                    posterUrl = channel.logoUrl
                )
                normalizedResults.add(
                    NormalizedSearchResult(
                        mediaItem = mediaItem,
                        availableFrom = listOf(SourceType.IPTV),
                        languages = listOf(channelLang),
                        watchOptions = listOf(watchOption),
                        bestMatchOption = watchOption,
                        isDualAudio = false,
                        score = ScoringConstants.LIVE_CHANNEL_DEFAULT_SCORE
                    )
                )
            }

            // Convert raw programs to NormalizedSearchResults
            provResult.programs.forEach { program ->
                val programLang = inferLanguageFromName(program.title, program.description)
                val pseudoSource = StreamSource(
                    id = program.channelId,
                    name = "Live Guide: ${program.title}",
                    url = "",
                    extensionId = "epg-live",
                    resolution = "Live",
                    language = programLang
                )
                val watchOption = WatchOption(
                    id = program.channelId,
                    title = program.title,
                    source = pseudoSource,
                    type = SourceType.IPTV,
                    languageLabel = "Live Program Guide"
                )
                val mediaItem = MediaItem(
                    id = program.channelId,
                    title = program.title,
                    type = MediaType.MOVIE,
                    overview = program.description ?: "EPG Live Broadcast"
                )
                normalizedResults.add(
                    NormalizedSearchResult(
                        mediaItem = mediaItem,
                        availableFrom = listOf(SourceType.IPTV),
                        languages = listOf(programLang),
                        watchOptions = listOf(watchOption),
                        bestMatchOption = watchOption,
                        isDualAudio = false,
                        score = ScoringConstants.EPG_PROGRAM_DEFAULT_SCORE
                    )
                )
            }

            // Convert raw mediaItems and streamSources to NormalizedSearchResults
            provResult.mediaItems.forEach { mediaItem ->
                val matchingSources = sourceContributions
                    .filter { sourceMatchesMedia(it, mediaItem) }
                    .distinctBy { it.source.id }

                // Pre-compute scores once per source to avoid N+1 scoring calls
                matchingSources.forEach { contribution ->
                    val source = contribution.source
                    if (source !in sourceScores) {
                        sourceScores[source] = SearchResultRanker.calculateSourceScore(source, prefs, debridCacheMap)
                    }
                }

                val watchOptions = matchingSources.map { contribution ->
                    val source = contribution.source
                    val label = when {
                        source.isDualAudio -> "${source.resolution} Dual Audio"
                        source.isDubbed -> "${source.resolution} ${source.language} Dubbed"
                        source.isSubbed -> "${source.resolution} ${source.language} Sub"
                        else -> "${source.resolution} ${source.language}"
                    }
                    WatchOption(
                        id = source.id,
                        title = source.name,
                        source = source,
                        type = sourceType(contribution),
                        languageLabel = label
                    )
                }.sortedWith(
                    compareByDescending<WatchOption> { option ->
                        sourceScores[option.source] ?: 0
                    }.thenBy { it.id }
                )

                val bestMatch = watchOptions.firstOrNull()
                val availableFrom = (watchOptions.map { it.type } + mediaProvenance(provResult))
                    .distinct()
                    .sortedBy { it.ordinal }
                val languages = watchOptions.map { it.source.language }.distinct()
                val isDual = watchOptions.any { it.source.isDualAudio }
                val score = bestMatch?.let { sourceScores[it.source] ?: 0 } ?: 0

                normalizedResults.add(
                    NormalizedSearchResult(
                        mediaItem = mediaItem,
                        availableFrom = availableFrom,
                        languages = languages,
                        watchOptions = watchOptions,
                        bestMatchOption = bestMatch,
                        isDualAudio = isDual,
                        score = score
                    )
                )
            }
        }

        // Deduplicate and Rank results
        val deduplicated = SearchResultDeduplicator.deduplicate(normalizedResults, prefs.hideDuplicates, prefs, sourceScores, debridCacheMap)
        val ranked = SearchResultRanker.rankResults(deduplicated, query, prefs, favorites, history, recentQueries)

        // Split into classified groups — mutually exclusive to prevent duplicates
        // SEARCH-BUG-1: Track classified IDs so a result only appears in one group
        val groups = mutableListOf<SearchResultGroup>()
        val classifiedIds = mutableSetOf<String>()

        // 1. Top Results (highest scoring result — always shown, not exclusive)
        if (ranked.isNotEmpty()) {
            groups.add(
                SearchResultGroup(
                    groupType = SearchGroupType.TOP_RESULTS,
                    title = "Top Results",
                    results = listOf(ranked.first())
                )
            )
        }

        // 2. Live Channels (first priority for IPTV live sources)
        val liveChannels = ranked.filter {
            it.mediaItem.id !in classifiedIds &&
                it.watchOptions.any { option -> option.source.extensionId == "iptv-live" }
        }
        if (liveChannels.isNotEmpty()) {
            classifiedIds.addAll(liveChannels.map { it.mediaItem.id })
            groups.add(
                SearchResultGroup(
                    groupType = SearchGroupType.LIVE_CHANNELS,
                    title = "Live Channels",
                    results = liveChannels
                )
            )
        }

        // 3. Live Programs (EPG Guide)
        val livePrograms = ranked.filter {
            it.mediaItem.id !in classifiedIds &&
                it.watchOptions.any { option -> option.source.extensionId == "epg-live" }
        }
        if (livePrograms.isNotEmpty()) {
            classifiedIds.addAll(livePrograms.map { it.mediaItem.id })
            groups.add(
                SearchResultGroup(
                    groupType = SearchGroupType.LIVE_PROGRAMS,
                    title = "Live Programs",
                    results = livePrograms
                )
            )
        }

        // 4. IPTV Catalogs (VOD from IPTV providers, excluding live)
        val iptvCatalog = ranked.filter {
            it.mediaItem.id !in classifiedIds &&
                it.availableFrom.contains(SourceType.IPTV) &&
                (it.mediaItem.id.startsWith("iptv-vod-") || it.mediaItem.id.startsWith("xtream-"))
        }
        if (iptvCatalog.isNotEmpty()) {
            classifiedIds.addAll(iptvCatalog.map { it.mediaItem.id })
            groups.add(
                SearchResultGroup(
                    groupType = SearchGroupType.IPTV_VOD,
                    title = "IPTV Catalogs",
                    results = iptvCatalog
                )
            )
        }

        // 5. Movies
        val movies = ranked.filter {
            it.mediaItem.id !in classifiedIds &&
                it.mediaItem.type == MediaType.MOVIE
        }
        if (movies.isNotEmpty()) {
            classifiedIds.addAll(movies.map { it.mediaItem.id })
            groups.add(
                SearchResultGroup(
                    groupType = SearchGroupType.MOVIES,
                    title = "Movies",
                    results = movies
                )
            )
        }

        // 6. Shows
        val shows = ranked.filter {
            it.mediaItem.id !in classifiedIds &&
                it.mediaItem.type == MediaType.SHOW
        }
        if (shows.isNotEmpty()) {
            classifiedIds.addAll(shows.map { it.mediaItem.id })
            groups.add(
                SearchResultGroup(
                    groupType = SearchGroupType.SHOWS,
                    title = "Shows",
                    results = shows
                )
            )
        }

        // 7. Extension Results (catch-all for remaining unclassified results)
        val extResults = ranked.filter {
            it.mediaItem.id !in classifiedIds &&
                (it.availableFrom.contains(SourceType.EXTENSION) || it.availableFrom.contains(SourceType.DEBRID))
        }
        if (extResults.isNotEmpty()) {
            classifiedIds.addAll(extResults.map { it.mediaItem.id })
            groups.add(
                SearchResultGroup(
                    groupType = SearchGroupType.EXTENSION_RESULTS,
                    title = "Extension Results",
                    results = extResults
                )
            )
        }

        // 8. Settings Matches
        if (settingsRoutes.isNotEmpty()) {
            val settingsResults = settingsRoutes.map { route ->
                val routeMediaItem = MediaItem(
                    id = "settings-$route",
                    title = route,
                    type = MediaType.MOVIE,
                    overview = "System Settings Configuration Shortcut"
                )
                NormalizedSearchResult(
                    mediaItem = routeMediaItem,
                    availableFrom = emptyList(),
                    languages = emptyList(),
                    watchOptions = emptyList()
                )
            }
            groups.add(
                SearchResultGroup(
                    groupType = SearchGroupType.SETTINGS,
                    title = "Settings Shortcuts",
                    results = settingsResults
                )
            )
        }

        return groups
    }
}

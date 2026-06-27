package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.ChannelEntity
import com.example.calmsource.core.discoveryengine.database.EpgProgramEntity
import com.example.calmsource.core.discoveryengine.database.UserChannelStateEntity
import com.example.calmsource.core.discoveryengine.database.WatchEventEntity
import com.example.calmsource.core.discoveryengine.models.RecommendationItem
import com.example.calmsource.core.discoveryengine.models.ScoreBreakdown
import com.example.calmsource.core.discoveryengine.models.TasteProfile
import java.util.Calendar
import kotlin.math.abs

object LiveTvRecommender {
    private val categorySplitRegex = Regex("[/\\s&\\-|,;:]+")
    private val genericCategoryTokens = setOf(
        "live",
        "show",
        "special",
        "program",
        "movie",
        "movies",
        "series"
    )

    /**
     * Scores and ranks Live TV channels using EPG categories, historical watch patterns,
     * time-of-day affinity, and user favorites.
     */
    fun recommend(
        tasteProfile: TasteProfile,
        channels: List<ChannelEntity>,
        userChannelStates: Map<String, UserChannelStateEntity>,
        watchEvents: List<WatchEventEntity>,
        currentEpg: Map<String, EpgProgramEntity>,
        currentTimeMs: Long = System.currentTimeMillis(),
        limit: Int = 15
    ): List<RecommendationItem> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTimeMs
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        return channels.mapNotNull { channel ->
            val state = userChannelStates[channel.id]
            
            // Filter out hidden channels
            if (state != null && state.isHidden) return@mapNotNull null

            val reasons = mutableListOf<String>()

            // 1. Base Score
            var baseScore = 5.0
            
            // 2. Favorite boost
            var favoriteBoost = 0.0
            if (state != null && state.isFavorite) {
                favoriteBoost += 30.0
                reasons.add("Favorite channel")
            }

            // 3. Watch frequency boost
            var watchFreqBoost = 0.0
            if (state != null && state.watchCount > 0) {
                watchFreqBoost += 10.0 + (state.watchCount * 2.0).coerceAtMost(15.0)
                reasons.add("Frequently watched channel")
            }

            // 4. Time-of-day affinity boost
            var timeOfDayBoost = 0.0
            val channelEvents = watchEvents.filter { it.itemId == channel.id && it.itemType == "channel" }
            var matchCount = 0
            channelEvents.forEach { event ->
                val eventCalendar = Calendar.getInstance()
                eventCalendar.timeInMillis = event.timestamp
                val eventHour = eventCalendar.get(Calendar.HOUR_OF_DAY)
                val diff = abs(eventHour - currentHour)
                if (diff <= 2 || diff >= 22) {
                    matchCount++
                }
            }
            if (matchCount > 0) {
                timeOfDayBoost = (matchCount * 2.0).coerceAtMost(10.0)
                reasons.add("Watched around this time of day")
            }

            // 5. EPG Category boost
            var epgCategoryBoost = 0.0
            val epg = currentEpg[channel.id]
            val reasonText = if (epg != null) {
                val category = epg.category?.trim()?.lowercase().orEmpty()
                val affinity = bestCategoryAffinity(category, tasteProfile)
                if (affinity > 0.0) {
                    epgCategoryBoost = affinity * 15.0
                    reasons.add("Live now: ${epg.title} (Matches your genre preference: $category)")
                } else {
                    reasons.add("Live now: ${epg.title}")
                }
                "Live now: ${epg.title}"
            } else {
                "Live channel"
            }

            val totalScore = baseScore + favoriteBoost + watchFreqBoost + timeOfDayBoost + epgCategoryBoost

            val breakdown = ScoreBreakdown(
                ftsScore = 0.0,
                exactPrefixBoost = 0.0,
                aliasBoost = 0.0,
                qualityBoost = timeOfDayBoost,
                profileBoost = favoriteBoost + watchFreqBoost + epgCategoryBoost,
                liveNowBoost = baseScore,
                penalty = 0.0,
                reasons = reasons
            )

            RecommendationItem(
                id = channel.id,
                type = "channel",
                title = channel.name,
                score = totalScore,
                reason = reasonText,
                scoreBreakdown = breakdown
            )
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun bestCategoryAffinity(
        rawCategory: String,
        tasteProfile: TasteProfile
    ): Double {
        if (rawCategory.isBlank()) return 0.0

        val exactAffinity = rawCategory
            .takeIf { it !in genericCategoryTokens }
            ?.let { tasteProfile.genreAffinities[it] }
            ?: 0.0

        val tokenAffinity = rawCategory
            .split(categorySplitRegex)
            .asSequence()
            .map { it.trim() }
            .filter { it.length > 2 && it !in genericCategoryTokens }
            .map { tasteProfile.genreAffinities[it] ?: 0.0 }
            .maxOrNull()
            ?: 0.0

        return maxOf(exactAffinity, tokenAffinity)
    }
}

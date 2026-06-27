package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel

enum class IptvContentSection(val label: String) {
    SPORTS("Sports"),
    MOVIES("Movies"),
    NEWS("News"),
    KIDS("Kids"),
    ENTERTAINMENT("Entertainment"),
    OTHER("Other")
}

object IptvChannelFacets {
    private val NORMALIZE_REGEX = Regex("[^a-z0-9]+")

    private val sportsTerms = setOf(
        "sport", "sports", "espn", "football", "soccer", "cricket", "nba", "nfl",
        "nhl", "mlb", "tennis", "golf", "racing", "ufc", "wwe"
    )
    private val movieTerms = setOf(
        "movie", "movies", "cinema", "film", "films", "ppv"
    )
    private val newsTerms = setOf(
        "news", "noticias", "actualites", "breaking", "cnn", "msnbc", "bbc news",
        "fox news", "sky news"
    )
    private val kidsTerms = setOf(
        "kids", "children", "cartoon", "family", "disney", "nickelodeon", "nick jr",
        "boomerang"
    )
    private val entertainmentTerms = setOf(
        "entertainment", "general", "comedy", "drama", "music", "lifestyle", "reality"
    )

    private val sportsRegexes = sportsTerms.map { term -> Regex("(^| )${Regex.escape(normalize(term))}( |$)") }
    private val newsRegexes = newsTerms.map { term -> Regex("(^| )${Regex.escape(normalize(term))}( |$)") }
    private val kidsRegexes = kidsTerms.map { term -> Regex("(^| )${Regex.escape(normalize(term))}( |$)") }
    private val movieRegexes = movieTerms.map { term -> Regex("(^| )${Regex.escape(normalize(term))}( |$)") }
    private val entertainmentRegexes = entertainmentTerms.map { term -> Regex("(^| )${Regex.escape(normalize(term))}( |$)") }

    fun contentSection(channel: IPTVChannel): IptvContentSection {
        val searchable = normalize("${channel.groupTitle.orEmpty()} ${channel.name}")
        return when {
            containsTerm(searchable, sportsRegexes) -> IptvContentSection.SPORTS
            containsTerm(searchable, newsRegexes) -> IptvContentSection.NEWS
            containsTerm(searchable, kidsRegexes) -> IptvContentSection.KIDS
            containsTerm(searchable, movieRegexes) -> IptvContentSection.MOVIES
            containsTerm(searchable, entertainmentRegexes) -> IptvContentSection.ENTERTAINMENT
            else -> IptvContentSection.OTHER
        }
    }

    fun languages(channels: List<IPTVChannel>): List<String> {
        return channels
            .map(IptvChannelOrganizer::detectLanguage)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    fun countries(channels: List<IPTVChannel>): List<String> {
        return channels
            .map(IptvChannelOrganizer::detectCountry)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    private fun containsTerm(searchable: String, regexes: List<Regex>): Boolean {
        return regexes.any { regex ->
            regex.containsMatchIn(searchable)
        }
    }

    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(NORMALIZE_REGEX, " ")
            .trim()
    }
}

package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.ChannelMapper
import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.SourceHealth

enum class IptvGroupMode {
    CATEGORY,
    LANGUAGE,
    COUNTRY
}

data class IptvOptimizationPreferences(
    val preferredLanguages: Set<String> = emptySet(),
    val preferredCountry: String = "",
    val favoriteCategories: Set<String> = emptySet(),
    val hideAdult: Boolean = true,
    val hideUnsupported: Boolean = true,
    val preferHighQuality: Boolean = true,
    val removeDuplicates: Boolean = true,
    val groupMode: IptvGroupMode = IptvGroupMode.CATEGORY
)

data class IptvOptimizationStats(
    val inputCount: Int = 0,
    val visibleCount: Int = 0,
    val adultHidden: Int = 0,
    val languageHidden: Int = 0,
    val countryHidden: Int = 0,
    val unsupportedHidden: Int = 0,
    val duplicatesRemoved: Int = 0
)

data class IptvOrganizationResult(
    val channels: List<IPTVChannel>,
    val stats: IptvOptimizationStats
)

object IptvChannelOrganizer {
    private val adultTerms = setOf(
        "adult", "xxx", "18+", "18 plus", "playboy", "porn", "erotic"
    )
    private val languageAliases = linkedMapOf(
        "English" to setOf("english", "eng", "en"),
        "Spanish" to setOf("spanish", "espanol", "esp", "es"),
        "French" to setOf("french", "francais", "fra", "fre", "fr"),
        "German" to setOf("german", "deutsch", "deu", "ger", "de"),
        "Hindi" to setOf("hindi", "hin"),
        "Punjabi" to setOf("punjabi", "pan", "pa"),
        "Tamil" to setOf("tamil", "tam"),
        "Telugu" to setOf("telugu", "tel"),
        "Arabic" to setOf("arabic", "ara", "ar"),
        "Portuguese" to setOf("portuguese", "portugues", "por", "pt"),
        "Italian" to setOf("italian", "ita", "it"),
        "Japanese" to setOf("japanese", "jpn", "ja"),
        "Korean" to setOf("korean", "kor", "ko")
    )
    private val countryAliases = linkedMapOf(
        "United States" to setOf("united states", "usa"),
        "United Kingdom" to setOf("united kingdom", "britain", "british"),
        "Canada" to setOf("canada"),
        "India" to setOf("india"),
        "Spain" to setOf("spain"),
        "France" to setOf("france"),
        "Germany" to setOf("germany"),
        "Italy" to setOf("italy"),
        "Portugal" to setOf("portugal"),
        "Brazil" to setOf("brazil"),
        "Mexico" to setOf("mexico"),
        "Australia" to setOf("australia"),
        "New Zealand" to setOf("new zealand"),
        "Japan" to setOf("japan"),
        "South Korea" to setOf("south korea")
    )
    private val countryCodes = linkedMapOf(
        "US" to "United States",
        "USA" to "United States",
        "UK" to "United Kingdom",
        "GB" to "United Kingdom",
        "CA" to "Canada",
        "IN" to "India",
        "ES" to "Spain",
        "FR" to "France",
        "DE" to "Germany",
        "IT" to "Italy",
        "PT" to "Portugal",
        "BR" to "Brazil",
        "MX" to "Mexico",
        "AU" to "Australia",
        "NZ" to "New Zealand",
        "JP" to "Japan",
        "KR" to "South Korea"
    )

    private val nonAlphaNumericRegex = Regex("[^a-z0-9]+")

    private val duplicateKeyRegex = Regex("\\b(4k|uhd|fhd|hd|sd|backup|mirror|feed)\\b")

    private val implicitShortLanguageCodes = setOf(
        "en", "es", "fr", "pa", "ar", "pt", "it", "ja", "ko"
    )

    private val shortLanguageCodeRegexes: List<Pair<String, Regex>> = languageAliases.flatMap { (language, aliases) ->
        aliases
            .filter { it.length <= 2 && it in implicitShortLanguageCodes }
            .map { alias ->
                language to Regex(
                    "(^|[|\\[\\]():_/-])\\s*${Regex.escape(alias)}(?=\\s*($|[|\\[\\]():_/-]))",
                    RegexOption.IGNORE_CASE
                )
            }
    }

    private val languageRegexes: Map<String, List<Regex>> = languageAliases.mapValues { (_, aliases) ->
        aliases
            .filter { it.length > 2 }
            .map { alias ->
                Regex("(^| )${Regex.escape(alias)}( |$)", RegexOption.IGNORE_CASE)
            }
    }

    private val countryCodeRegexes: List<Pair<String, Regex>> = countryCodes.map { (code, country) ->
        country to Regex("(^|[|\\[\\]():_/-])\\s*${Regex.escape(code)}(?=\\s|$|[|\\[\\]():_/-])", RegexOption.IGNORE_CASE)
    }

    private val countryAliasRegexes: Map<String, List<Regex>> = countryAliases.mapValues { (_, aliases) ->
        aliases.map { alias ->
            Regex("(^| )${Regex.escape(normalize(alias))}( |$)", RegexOption.IGNORE_CASE)
        }
    }

    private data class SortableChannel(
        val channel: IPTVChannel,
        val isFavorite: Boolean,
        val matchesLanguage: Boolean,
        val isHighQuality: Boolean,
        val normalizedGroupTitle: String,
        val normalizedName: String,
        val detectedLanguage: String,
        val detectedCountry: String
    )

    fun organize(
        channels: List<IPTVChannel>,
        preferences: IptvOptimizationPreferences,
        healthBySourceId: Map<String, SourceHealth> = emptyMap()
    ): IptvOrganizationResult {
        var adultHidden = 0
        var languageHidden = 0
        var countryHidden = 0
        var unsupportedHidden = 0

        val preferredLanguages = preferences.preferredLanguages
            .map(::normalize)
            .filter(String::isNotEmpty)
            .toSet()
        val preferredCountry = normalize(preferences.preferredCountry)

        fun getLanguage(channel: IPTVChannel): String =
            channel.language.orEmpty().ifEmpty { detectLanguage(channel) }

        fun getCountry(channel: IPTVChannel): String =
            channel.country.orEmpty().ifEmpty { detectCountry(channel) }

        val filtered = channels.filter { channel ->
            when {
                preferences.hideAdult && isAdult(channel) -> {
                    adultHidden++
                    false
                }
                preferences.hideUnsupported && isUnsupported(channel, healthBySourceId) -> {
                    unsupportedHidden++
                    false
                }
                preferredLanguages.isNotEmpty() &&
                    getLanguage(channel).isNotEmpty() &&
                    normalize(getLanguage(channel)) !in preferredLanguages -> {
                    languageHidden++
                    false
                }
                preferredCountry.isNotEmpty() &&
                    getCountry(channel).isNotEmpty() &&
                    normalize(getCountry(channel)) != preferredCountry -> {
                    countryHidden++
                    false
                }
                else -> true
            }
        }

        val deduplicated = if (preferences.removeDuplicates) {
            filtered
                .groupBy { duplicateKey(it, getLanguage(it), getCountry(it)) }
                .values
                .map { matches ->
                    matches.maxWithOrNull(
                        compareBy<IPTVChannel> {
                            healthBySourceId[it.safeSourceId]?.healthScore ?: 50
                        }.thenBy { qualityScore(it) }
                    ) ?: matches.first()
                }
        } else {
            filtered
        }

        val favoriteCategories = preferences.favoriteCategories.map(::normalize).toSet()
        val ordered = deduplicated
            .map { channel ->
                val category = normalize(channel.groupTitle.orEmpty())
                val smartSection = normalize(IptvChannelFacets.contentSection(channel).label)
                val isFavorite = favoriteCategories.any { favorite ->
                    favorite == smartSection || category.contains(favorite)
                }
                
                // Only run expensive language/country detection once per output channel
                val lang = getLanguage(channel)
                val country = getCountry(channel)
                
                val matchesLanguage = if (preferredLanguages.isNotEmpty()) {
                    lang.isNotEmpty() && normalize(lang) in preferredLanguages
                } else {
                    false
                }
                val isHighQuality = preferences.preferHighQuality && qualityScore(channel) > 0
                SortableChannel(
                    channel = channel,
                    isFavorite = isFavorite,
                    matchesLanguage = matchesLanguage,
                    isHighQuality = isHighQuality,
                    normalizedGroupTitle = channel.groupTitle.orEmpty().lowercase(),
                    normalizedName = channel.name.lowercase(),
                    detectedLanguage = lang,
                    detectedCountry = country
                )
            }
            .sortedWith(
                compareByDescending<SortableChannel> { it.isFavorite }
                    .thenByDescending { it.matchesLanguage }
                    .thenByDescending { it.isHighQuality }
                    .thenBy { it.normalizedGroupTitle }
                    .thenBy { it.normalizedName }
            )
            .map { applyGrouping(it.channel, preferences.groupMode, it.detectedLanguage, it.detectedCountry) }

        return IptvOrganizationResult(
            channels = ordered,
            stats = IptvOptimizationStats(
                inputCount = channels.size,
                visibleCount = ordered.size,
                adultHidden = adultHidden,
                languageHidden = languageHidden,
                countryHidden = countryHidden,
                unsupportedHidden = unsupportedHidden,
                duplicatesRemoved = filtered.size - deduplicated.size
            )
        )
    }

    fun detectLanguage(channel: IPTVChannel): String {
        val explicit = listOf("tvg-language", "language", "lang")
            .firstNotNullOfOrNull { key -> channel.rawAttributes[key]?.trim()?.takeIf(String::isNotEmpty) }
        if (explicit != null) {
            return canonicalLanguage(explicit)
        }

        val original = "${channel.groupTitle.orEmpty()} ${channel.name}"
        shortLanguageCodeRegexes.firstOrNull { (_, regex) ->
            regex.containsMatchIn(original)
        }?.let { (language, _) ->
            return language
        }

        val haystack = normalize(original)
        return languageRegexes.entries.firstOrNull { (_, regexes) ->
            regexes.any { regex -> regex.containsMatchIn(haystack) }
        }?.key.orEmpty()
    }

    fun detectCountry(channel: IPTVChannel): String {
        val explicit = listOf("tvg-country", "country", "region")
            .firstNotNullOfOrNull { key -> channel.rawAttributes[key]?.trim()?.takeIf(String::isNotEmpty) }
        if (explicit != null) {
            return canonicalCountry(explicit)
        }

        val original = "${channel.groupTitle.orEmpty()} ${channel.name}"
        countryCodeRegexes.forEach { (country, codePattern) ->
            if (codePattern.containsMatchIn(original)) return country
        }

        val normalized = normalize(original)
        return countryAliasRegexes.entries.firstOrNull { (_, regexes) ->
            regexes.any { regex -> regex.containsMatchIn(normalized) }
        }?.key.orEmpty()
    }

    private fun canonicalLanguage(value: String): String {
        val normalized = normalize(value.substringBefore(',').substringBefore('|'))
        return languageAliases.entries.firstOrNull { (name, aliases) ->
            normalized == normalize(name) || normalized in aliases
        }?.key ?: value.trim()
    }

    private fun canonicalCountry(value: String): String {
        val firstValue = value.substringBefore(',').substringBefore('|').trim()
        countryCodes[firstValue.uppercase()]?.let { return it }
        val normalized = normalize(firstValue)
        return countryAliases.entries.firstOrNull { (country, aliases) ->
            normalized == normalize(country) || aliases.any { normalize(it) == normalized }
        }?.key ?: firstValue
    }

    private fun isAdult(channel: IPTVChannel): Boolean {
        val haystack = normalize("${channel.groupTitle.orEmpty()} ${channel.name}")
        return adultTerms.any { term -> haystack.contains(normalize(term)) }
    }

    private fun isUnsupported(
        channel: IPTVChannel,
        healthBySourceId: Map<String, SourceHealth>
    ): Boolean {
        if (healthBySourceId[channel.safeSourceId]?.healthScore == 0) return true
        return channel.rawAttributes["supported"]?.equals("false", ignoreCase = true) == true ||
            channel.rawAttributes["status"]?.equals("unsupported", ignoreCase = true) == true
    }

    private fun duplicateKey(channel: IPTVChannel, language: String, country: String): String {
        val normalizedName = normalize(channel.name)
            .replace(duplicateKeyRegex, "")
            .replace(" ", "")
        return listOf(
            normalizedName,
            normalize(language),
            normalize(country),
            channel.isVod.toString()
        ).joinToString("|")
    }

    private fun qualityScore(channel: IPTVChannel): Int {
        return when (ChannelMapper.extractResolution(channel)) {
            "4K" -> 4
            "1080p" -> 3
            "720p" -> 2
            "SD" -> 1
            else -> 0
        }
    }

    private fun applyGrouping(
        channel: IPTVChannel,
        groupMode: IptvGroupMode,
        detectedLanguage: String,
        detectedCountry: String
    ): IPTVChannel {
        val groupTitle = when (groupMode) {
            IptvGroupMode.CATEGORY -> channel.groupTitle
            IptvGroupMode.LANGUAGE -> detectedLanguage.ifEmpty { "Other languages" }
            IptvGroupMode.COUNTRY -> detectedCountry.ifEmpty { "Other regions" }
        }
        return channel.copy(groupTitle = groupTitle)
    }

    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(nonAlphaNumericRegex, " ")
            .trim()
    }
}

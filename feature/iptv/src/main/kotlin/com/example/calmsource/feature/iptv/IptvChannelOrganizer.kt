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

    // Compiled Combined Regexes for 10x Performance
    private val SHORT_LANG_REGEX = Regex(
        "(?:^|[|\\\\\\[\\]():_/-])\\s*(en|es|fr|pa|ar|pt|it|ja|ko)(?=\\s*(?:$|[|\\\\\\[\\]():_/-]))",
        RegexOption.IGNORE_CASE
    )

    private val LONG_LANG_REGEX = Regex(
        "\\b(english|eng|spanish|espanol|esp|french|francais|fra|fre|german|deutsch|deu|ger|hindi|hin|punjabi|pan|tamil|tam|telugu|tel|arabic|ara|portuguese|portugues|por|italian|ita|japanese|jpn|korean|kor)\\b",
        RegexOption.IGNORE_CASE
    )

    private val COUNTRY_CODE_REGEX = Regex(
        "(?:^|[|\\\\\\[\\]():_/-])\\s*(US|USA|UK|GB|CA|IN|ES|FR|DE|IT|PT|BR|MX|AU|NZ|JP|KR)(?=\\s|$|[|\\\\\\[\\]():_/-])",
        RegexOption.IGNORE_CASE
    )

    private val COUNTRY_ALIAS_REGEX = Regex(
        "\\b(united states|usa|united kingdom|britain|british|canada|india|spain|france|germany|italy|portugal|brazil|mexico|australia|new zealand|japan|south korea)\\b",
        RegexOption.IGNORE_CASE
    )

    private val aliasToLanguageMap: Map<String, String> = languageAliases.flatMap { (lang, aliases) ->
        aliases.map { it.lowercase() to lang }
    }.toMap()

    private val aliasToCountryMap: Map<String, String> = countryAliases.flatMap { (country, aliases) ->
        aliases.map { it.lowercase() to country }
    }.toMap()

    private class CachedChannel(
        val channel: IPTVChannel,
        val organizer: IptvChannelOrganizer
    ) {
        val language: String by lazy {
            channel.language.orEmpty().ifEmpty { organizer.detectLanguage(channel) }
        }
        val country: String by lazy {
            channel.country.orEmpty().ifEmpty { organizer.detectCountry(channel) }
        }
        val isAdult: Boolean by lazy {
            organizer.isAdult(channel)
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

        // Wrap in CachedChannel to lazily evaluate and cache language/country/adult checks
        val cachedChannels = channels.map { CachedChannel(it, this) }

        val filtered = cachedChannels.filter { cached ->
            val channel = cached.channel
            when {
                preferences.hideAdult && cached.isAdult -> {
                    adultHidden++
                    false
                }
                preferences.hideUnsupported && isUnsupported(channel, healthBySourceId) -> {
                    unsupportedHidden++
                    false
                }
                preferredLanguages.isNotEmpty() &&
                    cached.language.isNotEmpty() &&
                    normalize(cached.language) !in preferredLanguages -> {
                    languageHidden++
                    false
                }
                preferredCountry.isNotEmpty() &&
                    cached.country.isNotEmpty() &&
                    normalize(cached.country) != preferredCountry -> {
                    countryHidden++
                    false
                }
                else -> true
            }
        }

        val deduplicated = if (preferences.removeDuplicates) {
            filtered
                .groupBy { cached -> duplicateKey(cached.channel, cached.language, cached.country) }
                .values
                .map { matches ->
                    matches.maxWithOrNull(
                        compareBy<CachedChannel> {
                            healthBySourceId[it.channel.safeSourceId]?.healthScore ?: 50
                        }.thenBy { qualityScore(it.channel) }
                    ) ?: matches.first()
                }
        } else {
            filtered
        }

        val favoriteCategories = preferences.favoriteCategories.map(::normalize).toSet()
        val ordered = deduplicated
            .map { cached ->
                val channel = cached.channel
                val category = normalize(channel.groupTitle.orEmpty())
                val smartSection = normalize(IptvChannelFacets.contentSection(channel).label)
                val isFavorite = favoriteCategories.any { favorite ->
                    favorite == smartSection || category.contains(favorite)
                }
                
                val lang = cached.language
                val country = cached.country
                
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
                    .thenBy { if (it.normalizedGroupTitle.isEmpty()) "\uFFFF" else it.normalizedGroupTitle }
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
        
        // 1. Combined Short Language Match
        val shortMatch = SHORT_LANG_REGEX.find(original)
        if (shortMatch != null) {
            val alias = shortMatch.groupValues[1].lowercase()
            aliasToLanguageMap[alias]?.let { return it }
        }

        // 2. Combined Long Language Match
        val haystack = normalize(original)
        val longMatch = LONG_LANG_REGEX.find(haystack)
        if (longMatch != null) {
            val alias = longMatch.groupValues[1].lowercase()
            aliasToLanguageMap[alias]?.let { return it }
        }

        return ""
    }

    fun detectCountry(channel: IPTVChannel): String {
        val explicit = listOf("tvg-country", "country", "region")
            .firstNotNullOfOrNull { key -> channel.rawAttributes[key]?.trim()?.takeIf(String::isNotEmpty) }
        if (explicit != null) {
            return canonicalCountry(explicit)
        }

        val original = "${channel.groupTitle.orEmpty()} ${channel.name}"
        
        // 1. Combined Country Code Match
        val codeMatch = COUNTRY_CODE_REGEX.find(original)
        if (codeMatch != null) {
            val code = codeMatch.groupValues[1].uppercase()
            countryCodes[code]?.let { return it }
        }

        // 2. Combined Country Alias Match
        val normalized = normalize(original)
        val aliasMatch = COUNTRY_ALIAS_REGEX.find(normalized)
        if (aliasMatch != null) {
            val alias = aliasMatch.groupValues[1].lowercase()
            aliasToCountryMap[alias]?.let { return it }
        }

        return ""
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
        return adultTerms.any { term -> haystack.contains(term) }
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
        return "${normalizedName}|${normalize(language)}|${normalize(country)}|${channel.isVod}"
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
            IptvGroupMode.CATEGORY -> channel.groupTitle.orEmpty()
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

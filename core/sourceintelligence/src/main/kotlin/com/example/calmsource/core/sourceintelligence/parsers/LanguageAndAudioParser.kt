package com.example.calmsource.core.sourceintelligence.parsers

import com.example.calmsource.core.sourceintelligence.models.SourceAudioChannelLayout
import com.example.calmsource.core.sourceintelligence.models.SourceAudioFormat
import com.example.calmsource.core.sourceintelligence.models.SourceLanguageInfo
import com.example.calmsource.core.sourceintelligence.models.SourceSubtitleInfo

object LanguageAndAudioParser {

    // Pre-compiled regexes hoisted from per-call loops. This parser is called for
    // every source during scoring, so allocating dozens of Regex objects per call
    // created significant GC pressure on low-end devices.
    private val DUB_WORD = Regex("\\bdub\\b")
    private val DUB_BRACKETED = Regex("[\\[\\.\\-\\_\\(]dub[\\]\\.\\-\\_\\)]")
    private val DTS_WORD = Regex("\\bdts\\b")
    private val DTS_BRACKETED = Regex("[\\[\\.\\-\\_\\(]dts[\\]\\.\\-\\_\\)]")
    private val DD_WORD = Regex("\\bdd\\b")
    private val DD_BRACKETED = Regex("[\\[\\.\\-\\_\\(]dd[\\]\\.\\-\\_\\)]")
    private val DUAL_AUDIO_WORD = Regex("\\bdual[\\s\\.\\-\\_]*(audio|dub)\\b")
    private val MULTI_AUDIO_WORD = Regex("\\bmulti[\\s\\.\\-\\_]*(audio|dub)\\b")

    private val SUB_WORD = Regex("\\b(sub|subs|subbed|subtitles)\\b")
    private val SUB_BRACKETED = Regex("[\\[\\.\\-\\_\\(]sub(?:s)?[\\]\\.\\-\\_\\)]")
    private val HC_WORD = Regex("\\b(hc|hardsub|hcsub)\\b")
    private val HC_BRACKETED = Regex("[\\[\\.\\-\\_\\(](hc|hardsub|hcsub)[\\]\\.\\-\\_\\)]")
    private val CC_WORD = Regex("\\b(cc)\\b")
    private val CC_BRACKETED = Regex("[\\[\\.\\-\\_\\(]cc[\\]\\.\\-\\_\\)]")
    private val FORCED_WORD = Regex("\\b(forced)\\b")
    private val FORCED_BRACKETED = Regex("[\\[\\.\\-\\_\\(]forced[\\]\\.\\-\\_\\)]")

    private fun highRiskLanguageRegex(token: String): Regex {
        val escaped = Regex.escape(token)
        return Regex(
            "(?:[\\(\\[]$escaped[\\)\\]])|" +
            "(?:\\b(?:19\\d{2}|20\\d{2}|480p|576p|720p|1080p|2160p|4k|hdr|bluray|web-?rip|web-?dl|h264|x264|h265|x265|hevc|dd\\+?\\d?\\.?\\d?|dts)[\\s\\.\\-\\_]+$escaped(?=$|[\\s\\.\\-\\_\\)\\]]))",
            RegexOption.IGNORE_CASE
        )
    }

    fun getLanguageFullName(code: String?): String? {
        if (code == null) return null
        return when (code.lowercase()) {
            "en" -> "english"
            "pa" -> "punjabi"
            "hi" -> "hindi"
            "es" -> "spanish"
            "fr" -> "french"
            "de" -> "german"
            "it" -> "italian"
            "ta" -> "tamil"
            "te" -> "telugu"
            "ml" -> "malayalam"
            "kn" -> "kannada"
            "pt" -> "portuguese"
            "ru" -> "russian"
            else -> code
        }
    }

    /**
     * Per-language code patterns. Built once at class load. The original
     * implementation re-compiled 28+ regexes per parseLanguage call.
     */
    private val LANGUAGE_PATTERNS: List<Pair<String, List<Regex>>> = listOf(
        "English" to listOf("english", "eng"),
        "Hindi" to listOf("hindi", "hin", "hi"),
        "Tamil" to listOf("tamil", "tam"),
        "Telugu" to listOf("telugu", "tel"),
        "Malayalam" to listOf("malayalam", "mal", "ml"),
        "Kannada" to listOf("kannada", "kan", "kn"),
        "Spanish" to listOf("spanish", "spa"),
        "French" to listOf("french", "fre", "fr"),
        "German" to listOf("german", "ger"),
        "Japanese" to listOf("japanese", "jap"),
        "Korean" to listOf("korean", "kor"),
        "Italian" to listOf("italian", "ita", "it"),
        "Portuguese" to listOf("portuguese", "por", "pt"),
        "Russian" to listOf("russian", "rus", "ru")
    ).map { (lang, patterns) ->
        lang to patterns.map { token ->
            if (token.length <= 2) {
                highRiskLanguageRegex(token)
            } else {
                separatorTokenRegex(token)
            }
        }
    }

    private fun isLanguagePresent(input: String, regexes: List<Regex>): Boolean {
        for (regex in regexes) {
            if (regex.containsMatchIn(input)) return true
        }
        return false
    }

    fun parseLanguage(filename: String?, title: String? = null): SourceLanguageInfo {
        if (filename == null && title == null) return SourceLanguageInfo.UNKNOWN

        val input = ((filename ?: "") + " " + (title ?: "")).lowercase()

        val languages = mutableListOf<String>()

        for ((lang, regexes) in LANGUAGE_PATTERNS) {
            if (isLanguagePresent(input, regexes)) {
                languages.add(lang)
            }
        }

        val isDubbed = input.contains("dubbed") || DUB_WORD.containsMatchIn(input) || DUB_BRACKETED.containsMatchIn(input)
        val isDualAudio = DUAL_AUDIO_WORD.containsMatchIn(input)
        val isMultiAudio = MULTI_AUDIO_WORD.containsMatchIn(input)

        if (languages.isEmpty() && !isDubbed && !isDualAudio && !isMultiAudio) {
            return SourceLanguageInfo.UNKNOWN
        }

        return SourceLanguageInfo(
            languages = languages.distinct(),
            isDubbed = isDubbed,
            isDualAudio = isDualAudio,
            isMultiAudio = isMultiAudio
        )
    }

    fun parseSubtitle(filename: String?, title: String? = null): SourceSubtitleInfo {
        if (filename == null && title == null) return SourceSubtitleInfo.UNKNOWN

        val input = ((filename ?: "") + " " + (title ?: "")).lowercase()

        val hasSub = SUB_WORD.containsMatchIn(input) || SUB_BRACKETED.containsMatchIn(input)
        val isHardcoded = HC_WORD.containsMatchIn(input) || HC_BRACKETED.containsMatchIn(input)
        val isCc = CC_WORD.containsMatchIn(input) || CC_BRACKETED.containsMatchIn(input)
        val isForced = FORCED_WORD.containsMatchIn(input) || FORCED_BRACKETED.containsMatchIn(input)

        if (!hasSub && !isHardcoded && !isCc && !isForced) {
            return SourceSubtitleInfo.UNKNOWN
        }

        return SourceSubtitleInfo(
            isAvailable = hasSub || isHardcoded || isCc || isForced,
            languages = emptyList(), // Can be enhanced later
            isHardcoded = isHardcoded,
            isForced = isForced
        )
    }

    fun parseAudioFormat(filename: String?, title: String? = null): SourceAudioFormat {
        if (filename == null && title == null) return SourceAudioFormat.UNKNOWN

        val input = ((filename ?: "") + " " + (title ?: "")).lowercase()

        return when {
            input.contains("truehd") -> SourceAudioFormat.TRUEHD
            input.contains("dts-hd") || input.contains("dtshd") || input.contains("dts.hd") || input.contains("dts_hd") -> SourceAudioFormat.DTS_HD
            input.contains("dts-x") || input.contains("dtsx") || input.contains("dts.x") -> SourceAudioFormat.DTS_X
            DTS_WORD.containsMatchIn(input) || DTS_BRACKETED.containsMatchIn(input) -> SourceAudioFormat.DTS
            input.contains("eac3") || input.contains("dd+") || input.contains("ddp") || input.contains("dd.plus") -> SourceAudioFormat.EAC3
            input.contains("ac3") || DD_WORD.containsMatchIn(input) || DD_BRACKETED.containsMatchIn(input) -> SourceAudioFormat.AC3
            input.contains("aac") -> SourceAudioFormat.AAC
            input.contains("flac") -> SourceAudioFormat.FLAC
            input.contains("mp3") -> SourceAudioFormat.MP3
            input.contains("opus") -> SourceAudioFormat.OPUS
            else -> SourceAudioFormat.UNKNOWN
        }
    }

    fun parseAudioChannelLayout(filename: String?, title: String? = null): SourceAudioChannelLayout {
        if (filename == null && title == null) return SourceAudioChannelLayout.UNKNOWN

        val input = ((filename ?: "") + " " + (title ?: "")).lowercase()

        return when {
            input.contains("atmos") -> SourceAudioChannelLayout.ATMOS
            input.contains("7.1") || input.contains("7 1") || input.contains("7ch") -> SourceAudioChannelLayout.SURROUND_7_1
            input.contains("5.1") || input.contains("5 1") || input.contains("6ch") -> SourceAudioChannelLayout.SURROUND_5_1
            input.contains("2.0") || input.contains("2 0") || input.contains("stereo") || input.contains("2ch") -> SourceAudioChannelLayout.STEREO
            input.contains("1.0") || input.contains("1 0") || input.contains("mono") || input.contains("1ch") -> SourceAudioChannelLayout.MONO
            else -> SourceAudioChannelLayout.UNKNOWN
        }
    }

    private fun separatorTokenRegex(token: String): Regex {
        val escaped = Regex.escape(token)
        return Regex("(?:(?<=^)|(?<=[\\s\\.\\-\\_\\[\\(]))$escaped(?=$|[\\s\\.\\-\\_\\]\\)])")
    }
}

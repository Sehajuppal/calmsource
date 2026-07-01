/**
 * Parser for XMLTV electronic program guide (EPG) data.
 *
 * ## XMLTV Format Overview
 * XMLTV is an XML-based format for TV listings. Each `<programme>` element contains
 * attributes for start time, stop time, and channel ID, plus child elements for
 * title, description, subtitle, category, language, and episode numbering.
 *
 * Date/time values use the format `"YYYYMMDDHHmmss +ZZZZ"` (with optional timezone)
 * or `"YYYYMMDDHHmmss"` (without timezone, assumed UTC).
 *
 * ## Design Decision: Regex vs XmlPullParser
 * This parser uses regex-based extraction rather than Android's XmlPullParser for
 * several reasons:
 * - **Simplicity**: XMLTV files have a flat, predictable structure with no deep nesting
 * - **Fault tolerance**: Regex gracefully handles malformed XML that would crash a
 *   strict XML parser (common in real-world IPTV EPG feeds)
 * - **Performance**: For the limited set of fields we extract, regex avoids the
 *   overhead of building a full DOM or SAX state machine
 * - **Testability**: Pure string operations with no Android framework dependencies
 *
 * The tradeoff is that this approach won't handle edge cases like CDATA sections or
 * XML entities beyond the basics. A production implementation may want to upgrade
 * to XmlPullParser with error recovery if these become an issue.
 *
 * ## Performance Notes
 * - Pre-compiled regex patterns are hoisted to companion object (PERF: XMLTV-2)
 * - Date parsing uses globally shared, thread-safe java.time.format.DateTimeFormatter (PERF: XMLTV-3)
 * - Read uses a character buffer (char array) to stream and chunk contents safely, preventing crashes
 *   on single-line XMLTV files and avoiding readLine() overhead.
 *
 * @see EPGImportResult for the output structure
 * @see EPGProgram for the parsed program model
 */
package com.example.calmsource.core.parser

import com.example.calmsource.core.model.EPGProgram
import com.example.calmsource.core.model.EPGImportResult
import com.example.calmsource.core.model.EPGParseStats
import com.example.calmsource.core.model.generateSafeSourceId
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import androidx.annotation.RequiresApi
import android.os.Build

class XMLTVParser {

    companion object {
        const val DEFAULT_PAST_WINDOW_MS = 6L * 60L * 60L * 1000L
        const val DEFAULT_FUTURE_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L

        /** Normalized XMLTV date format with timezone offset, e.g. `"20260605100000 +0000"`. */
        private const val DATE_FORMAT_NORMALIZED = "yyyyMMddHHmmss Z"

        /**
         * Pre-compiled regex for extracting programme elements.
         * Hoisted to companion level to avoid re-compilation per chunk (PERF: XMLTV-2).
         */
        private val PROGRAMME_REGEX = Regex("""<programme\s+([^>]+)>(.*?)</programme>""", RegexOption.DOT_MATCHES_ALL)

        /** Pre-compiled attribute extraction regexes (PERF: XMLTV-2). */
        private val START_ATTR_REGEX = Regex("""start\s*=\s*['"]([^'"]*)['"]""")
        private val STOP_ATTR_REGEX = Regex("""stop\s*=\s*['"]([^'"]*)['"]""")
        private val CHANNEL_ATTR_REGEX = Regex("""channel\s*=\s*['"]([^'"]*)['"]""")

        /** Pre-compiled tag extraction regexes (PERF: XMLTV-2). */
        private val TITLE_TAG_REGEX = Regex("""<title(?:\s+[^>]*)?>([^<]*)</title>""", RegexOption.DOT_MATCHES_ALL)
        private val DESC_TAG_REGEX = Regex("""<desc(?:\s+[^>]*)?>([^<]*)</desc>""", RegexOption.DOT_MATCHES_ALL)
        private val DESCRIPTION_TAG_REGEX = Regex("""<description(?:\s+[^>]*)?>([^<]*)</description>""", RegexOption.DOT_MATCHES_ALL)
        private val SUBTITLE_TAG_REGEX = Regex("""<sub-title(?:\s+[^>]*)?>([^<]*)</sub-title>""", RegexOption.DOT_MATCHES_ALL)
        private val CATEGORY_TAG_REGEX = Regex("""<category(?:\s+[^>]*)?>([^<]*)</category>""", RegexOption.DOT_MATCHES_ALL)
        private val LANGUAGE_TAG_REGEX = Regex("""<language(?:\s+[^>]*)?>([^<]*)</language>""", RegexOption.DOT_MATCHES_ALL)
        private val EPISODE_NUM_TAG_REGEX = Regex("""<episode-num(?:\s+[^>]*)?>([^<]*)</episode-num>""", RegexOption.DOT_MATCHES_ALL)

        /** Pre-compiled regex for numeric character references in unescapeXml (PERF: XMLTV-2). */
        private val NUMERIC_CHAR_REF_REGEX = Regex("&#(x?)([0-9a-fA-F]+);")

        private val AMP_REGEX = Regex("&amp;", RegexOption.LITERAL)
        private val LT_REGEX = Regex("&lt;", RegexOption.LITERAL)
        private val GT_REGEX = Regex("&gt;", RegexOption.LITERAL)
        private val QUOT_REGEX = Regex("&quot;", RegexOption.LITERAL)
        private val APOS_REGEX = Regex("&apos;", RegexOption.LITERAL)

        private data class MutableEpgStats(
            var totalProgramElements: Int = 0,
            var parsedPrograms: Int = 0,
            var skippedPrograms: Int = 0,
            var malformedPrograms: Int = 0,
            var missingChannelPrograms: Int = 0,
            var invalidTimePrograms: Int = 0,
            var massiveProgramElements: Int = 0,
            var outsideWindowPrograms: Int = 0
        ) {
            fun toStats(durationMs: Long): EPGParseStats = EPGParseStats(
                totalProgramElements = totalProgramElements,
                parsedPrograms = parsedPrograms,
                skippedPrograms = skippedPrograms,
                malformedPrograms = malformedPrograms,
                missingChannelPrograms = missingChannelPrograms,
                invalidTimePrograms = invalidTimePrograms,
                massiveProgramElements = massiveProgramElements,
                outsideWindowPrograms = outsideWindowPrograms,
                durationMs = durationMs
            )
        }

        private val DATE_TZ_REGEX = Regex("""^(\d{8,20})\s*(?:Z|UTC|GMT|([+-]\d{2})(?::?(\d{2}))?)?$""", RegexOption.IGNORE_CASE)

        @RequiresApi(Build.VERSION_CODES.O)
        private object Api26Formatter {
            private val formatter = DateTimeFormatter.ofPattern(DATE_FORMAT_NORMALIZED, Locale.ROOT)

            fun parse(clean: String): Long {
                return try {
                    OffsetDateTime.parse(clean, formatter).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    0L
                }
            }
        }

        private fun parseDateLegacy(clean: String): Long {
            return try {
                val sdf = java.text.SimpleDateFormat(DATE_FORMAT_NORMALIZED, Locale.ROOT).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                sdf.parse(clean)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        /**
         * Thread-safe date parsing helper that normalizes timezone offsets.
         */
        private fun parseDate(dateStr: String?): Long {
            if (dateStr == null) return 0L
            val clean = dateStr.trim()
            if (clean.isEmpty()) return 0L
            val match = DATE_TZ_REGEX.matchEntire(clean) ?: return 0L
            var dateTimePart = match.groupValues[1]
            val tzHours = match.groupValues[2]
            val tzMinutes = match.groupValues[3]

            if (dateTimePart.length > 14) {
                dateTimePart = dateTimePart.substring(0, 14)
            } else if (dateTimePart.length < 14) {
                dateTimePart = dateTimePart.padEnd(14, '0')
            }

            val tzOffset = when {
                tzHours.isNotEmpty() -> {
                    val minutes = if (tzMinutes.isNotEmpty()) tzMinutes else "00"
                    "$tzHours$minutes"
                }
                else -> "+0000"
            }

            val normalized = "$dateTimePart $tzOffset"
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Api26Formatter.parse(normalized)
            } else {
                parseDateLegacy(normalized)
            }
        }

        private fun indexOfIgnoreCase(src: CharSequence, target: String, startIndex: Int = 0): Int {
            val targetLen = target.length
            if (targetLen == 0) return 0
            val max = src.length - targetLen
            for (i in startIndex..max) {
                var matches = true
                for (j in 0 until targetLen) {
                    val c1 = src[i + j]
                    val c2 = target[j]
                    if (c1 != c2 && c1.lowercaseChar() != c2.lowercaseChar()) {
                        matches = false
                        break
                    }
                }
                if (matches) return i
            }
            return -1
        }

        /**
         * Parses XMLTV EPG content into a list of [EPGProgram] entries.
         *
         * @param inputStream Raw XMLTV XML content stream
         * @param timeWindow Time window to filter programs
         * @param onProgramParsed Optional streaming callback invoked for each parsed program
         * @return [EPGImportResult] with parsed programs and any warnings
         */
        suspend fun parse(
            inputStream: java.io.InputStream,
            timeWindow: XMLTVTimeWindow? = null,
            maxPrograms: Int = 50_000,
            onProgramParsed: ((EPGProgram) -> Unit)? = null
        ): EPGImportResult = withContext(Dispatchers.IO) {
            require(maxPrograms > 0) { "maxPrograms must be positive" }
            val startedAt = System.currentTimeMillis()
            val programs = mutableListOf<EPGProgram>()
            val channelIds = linkedSetOf<String>()
            val warnings = mutableListOf<String>()
            val stats = MutableEpgStats()

            fun addWarning(message: String) {
                if (warnings.size < 100) {
                    warnings.add(message)
                }
            }

            try {
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    val charBuffer = CharArray(16384)
                    val buffer = java.lang.StringBuilder()
                    var numRead: Int
                    var programCount = 0

                    readInput@ while (reader.read(charBuffer).also { numRead = it } != -1) {
                        buffer.append(charBuffer, 0, numRead)

                        var scanIndex = 0
                        while (true) {
                            val startIndex = indexOfIgnoreCase(buffer, "<programme", scanIndex)
                            if (startIndex == -1) {
                                val keepFrom = (buffer.length - 20).coerceAtLeast(0)
                                if (keepFrom > 0) {
                                    buffer.delete(0, keepFrom)
                                }
                                break
                            }

                            val endIndex = indexOfIgnoreCase(buffer, "</programme>", startIndex)
                            if (endIndex == -1) {
                                val currentProgrammeLength = buffer.length - startIndex
                                if (currentProgrammeLength >= 2 * 1024 * 1024) {
                                    stats.totalProgramElements++
                                    stats.massiveProgramElements++
                                    stats.skippedPrograms++
                                    addWarning("Skipped massive <programme> tag to prevent OOM")
                                    buffer.delete(0, startIndex + 10)
                                }
                                if (startIndex > 0) {
                                    buffer.delete(0, startIndex)
                                }
                                break
                            }

                            val programXml = buffer.substring(startIndex, endIndex + "</programme>".length)
                            parseProgrammeElement(
                                programmeContent = programXml,
                                programs = programs,
                                channelIds = channelIds,
                                stats = stats,
                                addWarning = ::addWarning,
                                timeWindow = timeWindow,
                                onProgramParsed = onProgramParsed
                            )
                            
                            programCount++
                            if (stats.parsedPrograms >= maxPrograms) {
                                addWarning("EPG was limited to $maxPrograms programs")
                                buffer.clear()
                                break@readInput
                            }
                            if (programCount % 100 == 0) {
                                yield()
                            }

                            buffer.delete(0, endIndex + "</programme>".length)
                            scanIndex = 0
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                return@withContext EPGImportResult(
                    isSuccess = false,
                    warnings = listOf("XMLTV parser failure: ${e.message}"),
                    stats = stats.toStats(System.currentTimeMillis() - startedAt)
                )
            }

            return@withContext EPGImportResult(
                isSuccess = stats.parsedPrograms > 0,
                programs = programs,
                channelIds = channelIds,
                warnings = warnings,
                stats = stats.toStats(System.currentTimeMillis() - startedAt)
            )
        }

        private fun parseProgrammeElement(
            programmeContent: String,
            programs: MutableList<EPGProgram>,
            channelIds: MutableSet<String>,
            stats: MutableEpgStats,
            addWarning: (String) -> Unit,
            timeWindow: XMLTVTimeWindow?,
            onProgramParsed: ((EPGProgram) -> Unit)?
        ) {
            stats.totalProgramElements++
            val matchResult = PROGRAMME_REGEX.find(programmeContent)
            if (matchResult == null) {
                stats.malformedPrograms++
                stats.skippedPrograms++
                addWarning("Skipped malformed programme element")
                return
            }
            val attributesStr = matchResult.groupValues[1]
            val innerContent = matchResult.groupValues[2]

            try {
                val startAttr = START_ATTR_REGEX.find(attributesStr)?.groupValues?.get(1)
                val stopAttr = STOP_ATTR_REGEX.find(attributesStr)?.groupValues?.get(1)
                val channelAttr = CHANNEL_ATTR_REGEX.find(attributesStr)?.groupValues?.get(1)
                    ?.take(512)

                if (channelAttr == null) {
                    stats.missingChannelPrograms++
                    stats.skippedPrograms++
                    addWarning("Skipped programme: Missing channel ID attribute")
                    return
                }
                channelIds.add(channelAttr)

                val startTimeMs = parseDate(startAttr)
                val endTimeMs = parseDate(stopAttr)
                if (startTimeMs <= 0L || endTimeMs <= startTimeMs) {
                    stats.invalidTimePrograms++
                    stats.skippedPrograms++
                    addWarning("Skipped programme: Invalid start/stop time")
                    return
                }
                if (timeWindow != null && !timeWindow.overlaps(startTimeMs, endTimeMs)) {
                    stats.outsideWindowPrograms++
                    stats.skippedPrograms++
                    return
                }

                val title = extractTagValue(innerContent, TITLE_TAG_REGEX, 512) ?: "Untitled Show"
                val desc = extractTagValue(innerContent, DESC_TAG_REGEX, 4_096)
                    ?: extractTagValue(innerContent, DESCRIPTION_TAG_REGEX, 4_096)
                val subtitle = extractTagValue(innerContent, SUBTITLE_TAG_REGEX, 512)
                val category = extractTagValue(innerContent, CATEGORY_TAG_REGEX, 256)
                val language = extractTagValue(innerContent, LANGUAGE_TAG_REGEX, 64)
                val episodeNum = extractTagValue(innerContent, EPISODE_NUM_TAG_REGEX, 128)

                val id = generateSafeSourceId("$channelAttr-$startTimeMs-$endTimeMs-$title")

                val program = EPGProgram(
                    id = id,
                    channelId = channelAttr,
                    title = title,
                    description = desc,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    subtitle = subtitle,
                    category = category,
                    language = language,
                    episodeNum = episodeNum
                )

                if (onProgramParsed != null) {
                    onProgramParsed(program)
                } else {
                    programs.add(program)
                }
                stats.parsedPrograms++
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                stats.malformedPrograms++
                stats.skippedPrograms++
                addWarning("Failed to parse programme element: ${e.message}")
            }
        }

        private fun unescapeXml(value: String): String {
            if (!value.contains('&')) return value
            var result = value
            result = AMP_REGEX.replace(result, "&")
            result = LT_REGEX.replace(result, "<")
            result = GT_REGEX.replace(result, ">")
            result = QUOT_REGEX.replace(result, "\"")
            result = APOS_REGEX.replace(result, "'")
            // Decode numeric character references: &#160; (decimal) and &#xA0; (hex)
            result = NUMERIC_CHAR_REF_REGEX.replace(result) { match ->
                val isHex = match.groupValues[1] == "x"
                val code = try {
                    if (isHex) match.groupValues[2].toInt(16)
                    else match.groupValues[2].toInt()
                } catch (_: NumberFormatException) {
                    return@replace match.value
                }
                String(Character.toChars(code))
            }
            return result
        }

        /**
         * Extracts the text content of an XML tag using a pre-compiled regex.
         */
        private fun extractTagValue(
            innerContent: String,
            regex: Regex,
            maxLength: Int
        ): String? {
            val rawValue = regex.find(innerContent)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
            return rawValue?.let { unescapeXml(it).take(maxLength) }
        }
    }
}

data class XMLTVTimeWindow(
    val startTimeMs: Long,
    val endTimeMs: Long
) {
    init {
        require(endTimeMs >= startTimeMs) { "XMLTV time window end must not precede start" }
    }

    fun overlaps(programStartTimeMs: Long, programEndTimeMs: Long): Boolean {
        if (programStartTimeMs <= 0L || programEndTimeMs <= programStartTimeMs) return false
        return programEndTimeMs >= startTimeMs && programStartTimeMs <= endTimeMs
    }

    companion object {
        fun forSync(
            nowMs: Long = System.currentTimeMillis(),
            pastWindowMs: Long = XMLTVParser.DEFAULT_PAST_WINDOW_MS,
            futureWindowMs: Long = XMLTVParser.DEFAULT_FUTURE_WINDOW_MS
        ): XMLTVTimeWindow {
            return XMLTVTimeWindow(
                startTimeMs = nowMs - pastWindowMs.coerceAtLeast(0L),
                endTimeMs = nowMs + futureWindowMs.coerceAtLeast(0L)
            )
        }
    }
}

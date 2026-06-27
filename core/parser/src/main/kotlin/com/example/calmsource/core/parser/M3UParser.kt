package com.example.calmsource.core.parser

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.PlaylistImportResult
import com.example.calmsource.core.model.PlaylistParseStats
import com.example.calmsource.core.model.generateSafeSourceId
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.BitSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Optimized, memory-efficient, and stable M3U/M3U8 playlist parser.
 */
class M3UParser {

    companion object {
        private const val TAG_EXTM3U = "#EXTM3U"
        private const val TAG_EXTINF = "#EXTINF:"
        private const val MAX_LINE_CHARS = 64 * 1024

        private data class BoundedLine(val text: String?, val wasTruncated: Boolean)

        private fun readBoundedLine(reader: BufferedReader): BoundedLine {
            val line = StringBuilder(256)
            var truncated = false
            while (true) {
                val next = reader.read()
                if (next == -1) {
                    return if (line.isEmpty() && !truncated) BoundedLine(null, false)
                    else BoundedLine(line.toString(), truncated)
                }
                val char = next.toChar()
                if (char == '\n') return BoundedLine(line.toString(), truncated)
                if (char == '\r') continue
                if (line.length < MAX_LINE_CHARS) line.append(char) else truncated = true
            }
        }

        // Hoisted set to avoid O(C) allocations during scheme checking
        private val SUPPORTED_SCHEMES = setOf(
            "http", "https", "rtmp", "rtsp", "rtp", "udp", "mms",
            "xtream", "acestream", "sop", "magnet", "file", "plugin", "pipe"
        )

        /**
         * Parses M3U playlist content into a list of [IPTVChannel] entries.
         *
         * @param inputStream InputStream of M3U/M3U8 playlist content
         * @param providerId ID of the IPTV provider this playlist belongs to
         * @return [PlaylistImportResult] with parsed channels and any warnings
         */
        suspend fun parse(
            inputStream: InputStream,
            providerId: String,
            maxChannels: Int = 100_000,
            parsingContext: CoroutineContext = Dispatchers.IO,
            onChannelBatchParsed: suspend (List<IPTVChannel>) -> Unit = {}
        ): PlaylistImportResult = withContext(parsingContext) {
            require(maxChannels > 0) { "maxChannels must be positive" }
            val startedAt = System.currentTimeMillis()
            var channelCount = 0
            val warnings = mutableListOf<String>()
            val chunk = mutableListOf<IPTVChannel>()
            val CHUNK_SIZE = 500
            var lineNumber = 0
            var extInfLines = 0
            var malformedEntries = 0
            var duplicateChannels = 0
            var missingName = 0
            var missingUrl = 0
            var invalidUrl = 0

            fun buildStats(): PlaylistParseStats {
                return PlaylistParseStats(
                    totalLines = lineNumber,
                    extInfLines = extInfLines,
                    parsedChannels = channelCount,
                    skippedChannels = malformedEntries + duplicateChannels + missingUrl + invalidUrl,
                    duplicateChannels = duplicateChannels,
                    malformedEntries = malformedEntries,
                    missingName = missingName,
                    missingUrl = missingUrl,
                    invalidUrl = invalidUrl,
                    durationMs = System.currentTimeMillis() - startedAt
                )
            }

            try {
                // Robust resource protection: use() on the raw inputStream first
                inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                        var isFirstLine = true
                        var currentExtInf: String? = null
                        var currentExtInfLine = 0
                        
                        // Memory optimization: Primitive open-addressed hash set avoids boxed java.lang.Long & Node allocations
                        val seenUrlHashes = PrimitiveLongSet(initialCapacity = 5000)

                        parseLines@ while (true) {
                            val boundedLine = readBoundedLine(reader)
                            val rawLine = boundedLine.text ?: break
                            lineNumber++
                            if (boundedLine.wasTruncated) {
                                malformedEntries++
                                currentExtInf = null
                                if (warnings.size < 100) {
                                    warnings.add("Line $lineNumber: Skipped oversized playlist line")
                                }
                                continue
                            }
                            
                            // Allocation optimization: Skip blank lines without calling trim()
                            if (rawLine.isBlank()) continue

                            // Performance optimization: Avoid trim() allocation for start check
                            var trimmed = if (rawLine.startsWith(" ") || rawLine.endsWith(" ") || rawLine.endsWith("\r")) {
                                rawLine.trim()
                            } else {
                                rawLine
                            }

                            if (trimmed.isEmpty()) continue

                            // Strip BOM (U+FEFF) from the first line
                            if (isFirstLine) {
                                trimmed = trimmed.removePrefix("\uFEFF")
                                isFirstLine = false
                            }

                            if (trimmed.startsWith(TAG_EXTM3U)) {
                                continue
                            }

                            if (trimmed.startsWith(TAG_EXTINF)) {
                                extInfLines++
                                currentExtInf = trimmed
                                currentExtInfLine = lineNumber
                            } else if (!trimmed.startsWith("#")) {
                                // This is a stream URL line
                                val url = trimmed
                                if (currentExtInf != null) {
                                    try {
                                        // Performance optimization: Check validity and duplicates BEFORE parsing metadata/creating objects
                                        if (!isSupportedStreamReference(url)) {
                                            invalidUrl++
                                            if (warnings.size < 100) {
                                                warnings.add("Line $lineNumber: Skipped invalid stream reference")
                                            }
                                        } else {
                                            val urlHash = get64BitHash(url)
                                            if (seenUrlHashes.contains(urlHash)) {
                                                duplicateChannels++
                                                if (warnings.size < 100) {
                                                    warnings.add("Line $lineNumber: Skipped duplicate channel stream URL")
                                                }
                                            } else {
                                                val channel = parseChannel(currentExtInf, url, providerId)
                                                if (channel.name == "Channel") {
                                                    missingName++
                                                }
                                                chunk.add(channel)
                                                seenUrlHashes.add(urlHash)
                                                channelCount++

                                                if (chunk.size >= CHUNK_SIZE) {
                                                    onChannelBatchParsed(chunk.toList())
                                                    chunk.clear()
                                                }
                                                if (channelCount >= maxChannels) {
                                                    if (chunk.isNotEmpty()) {
                                                        onChannelBatchParsed(chunk.toList())
                                                        chunk.clear()
                                                    }
                                                    warnings.add("Playlist was limited to $maxChannels channels")
                                                    currentExtInf = null
                                                    break@parseLines
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e // Propagate coroutines cancellation
                                        malformedEntries++
                                        if (warnings.size < 100) {
                                            warnings.add("Line $lineNumber: Failed to parse channel entry: ${e.message}")
                                        }
                                    }
                                    currentExtInf = null
                                } else {
                                    malformedEntries++
                                    if (warnings.size < 100) {
                                        warnings.add("Line $lineNumber: Stream URL without preceding #EXTINF info")
                                    }
                                }
                            }
                        }

                        if (chunk.isNotEmpty()) {
                            onChannelBatchParsed(chunk.toList())
                            chunk.clear()
                        }

                        if (currentExtInf != null) {
                            missingUrl++
                            if (warnings.size < 100) {
                                warnings.add("Line $currentExtInfLine: #EXTINF entry missing stream URL")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e // Propagate coroutines cancellation
                return@withContext PlaylistImportResult(
                    isSuccess = false,
                    warnings = listOf("Parser failed: ${e.message}"),
                    stats = buildStats()
                )
            }

            return@withContext PlaylistImportResult(
                isSuccess = channelCount > 0,
                channelCount = channelCount,
                warnings = warnings,
                stats = buildStats()
            )
        }

        private fun findSeparatorCommaIndex(extInf: String): Int {
            var inQuotes = false
            for (i in extInf.indices) {
                val char = extInf[i]
                if (char == '"') {
                    inQuotes = !inQuotes
                } else if (char == ',' && !inQuotes) {
                    return i
                }
            }
            return -1
        }

        private fun parseChannel(extInf: String, url: String, providerId: String): IPTVChannel {
            // Find the index of comma separating attributes and display name
            val commaIndex = findSeparatorCommaIndex(extInf)
            val name: String
            val attributesPart: String
            if (commaIndex == -1) {
                val afterTag = extInf.removePrefix(TAG_EXTINF).trim()
                // Performance optimization: Avoid regex replace for duration stripping
                name = stripDurationPrefix(afterTag).ifEmpty { "Channel" }
                attributesPart = afterTag
            } else {
                attributesPart = extInf.substring(0, commaIndex)
                name = extInf.substring(commaIndex + 1).trim()
            }

            // Performance optimization: Use high-speed manual index scanner instead of Regex.findAll
            val rawAttributes = parseAttributes(attributesPart)

            val tvgId = rawAttributes["tvg-id"]
            val tvgName = rawAttributes["tvg-name"]
            val tvgLogoRaw = rawAttributes["tvg-logo"]
            val groupTitle = rawAttributes["group-title"]

            val tvgLogo = tvgLogoRaw?.trim()?.ifEmpty { null }

            // Performance / Product optimization: Reuse deterministic safe hash instead of blocking random UUID generator.
            // Maintains stable channel IDs across imports to preserve favorites/EPG history.
            val id = generateSafeSourceId("$providerId:$url")

            return IPTVChannel(
                id = id,
                tvgId = tvgId,
                tvgName = tvgName,
                tvgLogo = tvgLogo,
                groupTitle = groupTitle,
                name = name.ifEmpty { tvgName ?: "Channel" },
                streamUrl = url,
                providerId = providerId,
                rawAttributes = rawAttributes
            )
        }

        /**
         * Parses key="value" attributes using an allocation-free manual char scanner.
         */
        private fun parseAttributes(attributesPart: String): Map<String, String> {
            val map = LinkedHashMap<String, String>()
            val len = attributesPart.length
            var i = 0
            while (i < len) {
                val eqIndex = attributesPart.indexOf('=', i)
                if (eqIndex == -1) break

                // Scan key backwards
                var keyEnd = eqIndex
                while (keyEnd > i && attributesPart[keyEnd - 1].isWhitespace()) {
                    keyEnd--
                }
                var keyStart = keyEnd
                while (keyStart > i && (attributesPart[keyStart - 1].isLetterOrDigit() || attributesPart[keyStart - 1] == '-' || attributesPart[keyStart - 1] == '_')) {
                    keyStart--
                }

                if (keyStart < keyEnd) {
                    val key = attributesPart.substring(keyStart, keyEnd)

                    // Scan value forwards
                    var valStart = eqIndex + 1
                    while (valStart < len && attributesPart[valStart].isWhitespace()) {
                        valStart++
                    }
                    val quote = if (
                        valStart < len &&
                        (attributesPart[valStart] == '"' || attributesPart[valStart] == '\'')
                    ) attributesPart[valStart] else null
                    if (quote != null) {
                        valStart++ // Skip opening quote
                        var valEnd = valStart
                        while (valEnd < len && attributesPart[valEnd] != quote) {
                            valEnd++
                        }
                        val value = attributesPart.substring(valStart, valEnd)
                        map[key] = value
                        i = if (valEnd < len) valEnd + 1 else len
                    } else {
                        // Real-world playlists often omit quotes for simple values.
                        var valEnd = valStart
                        while (valEnd < len && !attributesPart[valEnd].isWhitespace()) {
                            valEnd++
                        }
                        if (valEnd > valStart) {
                            map[key] = attributesPart.substring(valStart, valEnd)
                        }
                        i = if (valEnd > eqIndex) valEnd else eqIndex + 1
                    }
                } else {
                    // Skip the '=' to avoid infinite loop if key was empty/invalid
                    i = eqIndex + 1
                }
            }
            return map
        }

        /**
         * Replaces DURATION_PREFIX_REGEX without regex allocations.
         */
        private fun stripDurationPrefix(afterTag: String): String {
            var i = 0
            val len = afterTag.length
            if (i < len && afterTag[i] == '-') {
                i++
            }
            val digitStart = i
            while (i < len && afterTag[i].isDigit()) {
                i++
            }
            if (i > digitStart) {
                while (i < len && afterTag[i].isWhitespace()) {
                    i++
                }
                return afterTag.substring(i)
            }
            return afterTag
        }

        private fun isSupportedStreamReference(url: String): Boolean {
            val candidate = url.trim()
            if (candidate.isBlank()) return false
            if (candidate.startsWith("<") || candidate.startsWith("{")) return false
            if (candidate.any { it.isISOControl() || it.isWhitespace() }) return false

            val schemeEnd = candidate.indexOf(':')
            if (schemeEnd <= 0) {
                return candidate.startsWith("/") ||
                    candidate.startsWith("./") ||
                    candidate.startsWith("../")
            }

            val scheme = candidate.substring(0, schemeEnd).lowercase()
            if (scheme !in SUPPORTED_SCHEMES) {
                return false
            }

            return when (scheme) {
                "http", "https", "rtmp", "rtsp", "mms" -> runCatching {
                    val uri = URI(candidate)
                    !uri.host.isNullOrBlank()
                }.getOrDefault(false)
                "magnet" -> candidate.startsWith("magnet:?xt=", ignoreCase = true)
                "xtream" -> candidate.startsWith("xtream://stream_id/", ignoreCase = true)
                else -> true
            }
        }

        private fun get64BitHash(value: String): Long {
            var hash = 1125899906842597L
            val len = value.length
            for (i in 0 until len) {
                hash = 31L * hash + value[i].code.toLong()
            }
            return hash
        }
    }

    /**
     * Highly optimized, zero-allocation primitive long hash set utilizing open addressing.
     */
    private class PrimitiveLongSet(initialCapacity: Int = 1000) {
        private var capacity = getPowerOfTwo(initialCapacity * 2)
        private var mask = capacity - 1
        private var keys = LongArray(capacity)
        private var filled = BitSet(capacity)
        private var size = 0

        fun contains(value: Long): Boolean {
            var idx = (value xor (value ushr 32)).toInt() and mask
            while (filled.get(idx)) {
                if (keys[idx] == value) return true
                idx = (idx + 1) and mask
            }
            return false
        }

        fun add(value: Long): Boolean {
            if (size >= capacity / 2) {
                resize()
            }
            var idx = (value xor (value ushr 32)).toInt() and mask
            while (filled.get(idx)) {
                if (keys[idx] == value) return false
                idx = (idx + 1) and mask
            }
            keys[idx] = value
            filled.set(idx)
            size++
            return true
        }

        private fun resize() {
            val oldCapacity = capacity
            val oldKeys = keys
            val oldFilled = filled

            capacity *= 2
            mask = capacity - 1
            keys = LongArray(capacity)
            filled = BitSet(capacity)
            size = 0

            for (i in 0 until oldCapacity) {
                if (oldFilled.get(i)) {
                    add(oldKeys[i])
                }
            }
        }

        private fun getPowerOfTwo(value: Int): Int {
            var v = value - 1
            v = v or (v shr 1)
            v = v or (v shr 2)
            v = v or (v shr 4)
            v = v or (v shr 8)
            v = v or (v shr 16)
            return if (v < 0) 1 else v + 1
        }
    }
}

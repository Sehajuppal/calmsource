package com.example.calmsource.core.discoveryengine.ingestion

import com.example.calmsource.core.discoveryengine.models.EpgProgram
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

/**
 * Memory-efficient, streaming XMLTV (EPG) parser using standard SAX.
 * Parses large XML streams in chunks to prevent memory allocation spikes.
 */
object XmlTvParser {

    /**
     * Parses the EPG XML stream and emits programs in chunks.
     *
     * @param inputStream The XMLTV data input stream.
     * @param chunkSize The maximum size of each chunk before calling the callback.
     * @param onChunkParsed Callback invoked when a chunk of programs is ready.
     */
    fun parseEpg(
        inputStream: InputStream,
        chunkSize: Int = 500,
        onChunkParsed: (List<EpgProgram>) -> Unit
    ) {
        val parserFactory = SAXParserFactory.newInstance()
        val parser = parserFactory.newSAXParser()
        val handler = XmlTvHandler(chunkSize, onChunkParsed)
        parser.parse(inputStream, handler)
    }

    private class XmlTvHandler(
        private val chunkSize: Int,
        private val onChunkParsed: (List<EpgProgram>) -> Unit
    ) : DefaultHandler() {

        private val currentChunk = mutableListOf<EpgProgram>()
        private var currentElement = ""
        private var currentProgramId = ""
        
        private var channelId = ""
        private var startTimeStr = ""
        private var endTimeStr = ""
        
        private val titleBuilder = StringBuilder()
        private val descBuilder = StringBuilder()
        private val categoryBuilder = StringBuilder()
        
        private var titleLang = ""
        private var descLang = ""

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String,
            attributes: Attributes
        ) {
            currentElement = qName
            when (qName) {
                "programme" -> {
                    channelId = attributes.getValue("channel") ?: ""
                    startTimeStr = attributes.getValue("start") ?: ""
                    endTimeStr = attributes.getValue("stop") ?: ""
                    // Create a deterministic unique ID for the EPG program
                    currentProgramId = "epg-${channelId}-${startTimeStr}-${endTimeStr}".replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    
                    titleBuilder.setLength(0)
                    descBuilder.setLength(0)
                    categoryBuilder.setLength(0)
                    titleLang = ""
                    descLang = ""
                }
                "title" -> {
                    titleLang = attributes.getValue("lang") ?: ""
                    titleBuilder.setLength(0)
                }
                "desc" -> {
                    descLang = attributes.getValue("lang") ?: ""
                    descBuilder.setLength(0)
                }
                "category" -> {
                    categoryBuilder.setLength(0)
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            when (currentElement) {
                "title" -> titleBuilder.append(ch, start, length)
                "desc" -> descBuilder.append(ch, start, length)
                "category" -> categoryBuilder.append(ch, start, length)
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            // Reset qName matching tracker
            currentElement = ""
            
            if (qName == "programme") {
                val startMs = parseXmlTvDate(startTimeStr)
                val endMs = parseXmlTvDate(endTimeStr)
                
                // DE-BUG-7: Skip programs with unparseable dates instead of inserting garbage epoch-0 rows
                if (startMs <= 0L) return
                
                val program = EpgProgram(
                    id = currentProgramId,
                    channelId = channelId,
                    title = titleBuilder.toString().trim(),
                    description = descBuilder.toString().trim().takeIf { it.isNotEmpty() },
                    category = categoryBuilder.toString().trim().takeIf { it.isNotEmpty() },
                    startTimeMs = startMs,
                    endTimeMs = if (endMs > 0L) endMs else startMs,
                    language = titleLang.takeIf { it.isNotEmpty() } ?: descLang.takeIf { it.isNotEmpty() }
                )
                
                currentChunk.add(program)
                if (currentChunk.size >= chunkSize) {
                    onChunkParsed(currentChunk.toList())
                    currentChunk.clear()
                }
            }
        }

        override fun endDocument() {
            if (currentChunk.isNotEmpty()) {
                onChunkParsed(currentChunk.toList())
                currentChunk.clear()
            }
        }

        private companion object {
            private val DATE_FORMAT_STRINGS = listOf(
                "yyyyMMddHHmmss Z",
                "yyyyMMddHHmmss",
                "yyyyMMddHHmm Z",
                "yyyyMMddHHmm",
                "yyyyMMdd Z",
                "yyyyMMdd"
            )

            private val threadLocalFormats = ThreadLocal.withInitial {
                DATE_FORMAT_STRINGS.map { java.text.SimpleDateFormat(it, java.util.Locale.US) }
            }
        }

        private fun parseXmlTvDate(dateStr: String?): Long {
            if (dateStr == null || dateStr.isBlank()) return 0L
            val cleanDate = dateStr.trim()
            val formats = threadLocalFormats.get() ?: return 0L
            for (sdf in formats) {
                try {
                    return sdf.parse(cleanDate)?.time ?: 0L
                } catch (e: Exception) {
                    // Try next pattern
                }
            }
            return 0L
        }
    }
}

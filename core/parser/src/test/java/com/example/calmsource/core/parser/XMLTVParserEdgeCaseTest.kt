package com.example.calmsource.core.parser

import com.example.calmsource.core.model.EPGProgram
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale

import kotlinx.coroutines.runBlocking

/**
 * Comprehensive edge-case tests for [XMLTVParser].
 *
 * Covers:
 * - Standard XMLTV parsing with all fields
 * - Missing programme elements
 * - Empty descriptions
 * - Unusual date formats (no timezone, short format)
 * - Missing channel ID attribute
 * - Missing title
 * - Empty XMLTV data
 * - Large preamble (channel section) handling
 * - Multiple programmes for same channel
 * - XMLTV with only channel elements (no programmes)
 */
class XMLTVParserEdgeCaseTest {

    private fun parse(content: String) = runBlocking {
        XMLTVParser.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))
    }

    private fun utcMillis(value: String): Long {
        return SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT)
            .parse("$value +0000")!!
            .time
    }

    @Test
    fun `standard XMLTV with all fields parses correctly`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="CNN.us">
                <display-name>CNN</display-name>
              </channel>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="CNN.us">
                <title>Breaking News</title>
                <desc>Latest news coverage</desc>
                <sub-title>Live Edition</sub-title>
                <category>News</category>
                <language>English</language>
                <episode-num system="xmltv_ns">0.1.0</episode-num>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertEquals(1, result.programs.size)

        val prog = result.programs[0]
        assertEquals("CNN.us", prog.channelId)
        assertEquals("Breaking News", prog.title)
        assertEquals("Latest news coverage", prog.description)
        assertEquals("Live Edition", prog.subtitle)
        assertEquals("News", prog.category)
        assertEquals("English", prog.language)
        assertEquals("0.1.0", prog.episodeNum)
        assertTrue(prog.startTimeMs > 0)
        assertTrue(prog.endTimeMs > prog.startTimeMs)
    }

    @Test
    fun `XMLTV with no programme elements returns failure`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="CNN.us">
                <display-name>CNN</display-name>
              </channel>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `time window retains overlapping programs and skips distant entries`() {
        val xmltv = """
            <tv>
              <programme start="20260605080000 +0000" stop="20260605090000 +0000" channel="old">
                <title>Old</title>
              </programme>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="current">
                <title>Current</title>
              </programme>
              <programme start="20260606080000 +0000" stop="20260606090000 +0000" channel="future">
                <title>Future</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = runBlocking {
            XMLTVParser.parse(
                ByteArrayInputStream(xmltv.toByteArray(Charsets.UTF_8)),
                XMLTVTimeWindow(
                    startTimeMs = utcMillis("20260605093000"),
                    endTimeMs = utcMillis("20260605120000")
                )
            )
        }

        assertTrue(result.isSuccess)
        assertEquals(listOf("Current"), result.programs.map { it.title })
        assertEquals(setOf("old", "current", "future"), result.channelIds)
        assertEquals(2, result.stats.outsideWindowPrograms)
        assertEquals(2, result.stats.skippedPrograms)
    }

    @Test
    fun `unfiltered parser remains backward compatible`() {
        val xmltv = """
            <tv>
              <programme start="20200101000000 +0000" stop="20200101010000 +0000" channel="archive">
                <title>Archive</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)

        assertTrue(result.isSuccess)
        assertEquals(1, result.programs.size)
        assertEquals(0, result.stats.outsideWindowPrograms)
    }

    @Test
    fun `empty description is handled`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="CNN.us">
                <title>Show Title</title>
                <desc></desc>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        // Empty desc should be treated as null
        assertNull(result.programs[0].description)
    }

    @Test
    fun `missing description is handled`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="CNN.us">
                <title>Show Title</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertNull(result.programs[0].description)
    }

    @Test
    fun `date format without timezone is parsed`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000" stop="20260605110000" channel="TEST.ch">
                <title>No TZ Show</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertTrue("Start time should be parsed", result.programs[0].startTimeMs > 0)
        assertTrue("End time should be parsed", result.programs[0].endTimeMs > 0)
    }

    @Test
    fun `date format with positive timezone offset is parsed`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0530" stop="20260605110000 +0530" channel="TEST.ch">
                <title>IST Show</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertTrue(result.programs[0].startTimeMs > 0)
    }

    @Test
    fun `date format with negative timezone offset is parsed`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 -0500" stop="20260605110000 -0500" channel="TEST.ch">
                <title>EST Show</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertTrue(result.programs[0].startTimeMs > 0)
    }

    @Test
    fun `missing channel ID produces warning and skips programme`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000">
                <title>No Channel</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertFalse(result.isSuccess)
        assertTrue(result.warnings.any { it.contains("Missing channel ID") })
        assertEquals(1, result.stats.missingChannelPrograms)
        assertEquals(1, result.stats.skippedPrograms)
    }

    @Test
    fun `missing title defaults to Untitled Show`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="TEST.ch">
                <desc>Some description</desc>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertEquals("Untitled Show", result.programs[0].title)
    }

    @Test
    fun `empty input returns failure`() {
        val result = parse("")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `large preamble with many channels is handled`() {
        val channels = (1..100).joinToString("\n") { i ->
            """<channel id="ch$i"><display-name>Channel $i</display-name></channel>"""
        }
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              $channels
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="ch1">
                <title>First Show</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertEquals(1, result.programs.size)
        assertEquals("ch1", result.programs[0].channelId)
    }

    @Test
    fun `multiple programmes for same channel are parsed`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="CNN.us">
                <title>Morning Show</title>
              </programme>
              <programme start="20260605110000 +0000" stop="20260605120000 +0000" channel="CNN.us">
                <title>Noon Report</title>
              </programme>
              <programme start="20260605120000 +0000" stop="20260605130000 +0000" channel="CNN.us">
                <title>Afternoon Update</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertEquals(3, result.programs.size)
        assertEquals(3, result.stats.parsedPrograms)
        assertEquals("Morning Show", result.programs[0].title)
        assertEquals("Noon Report", result.programs[1].title)
        assertEquals("Afternoon Update", result.programs[2].title)
    }

    @Test
    fun `programmes across multiple channels are parsed`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="CNN.us">
                <title>CNN Show</title>
              </programme>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="BBC.uk">
                <title>BBC Show</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertEquals(2, result.programs.size)
        val channelIds = result.programs.map { it.channelId }.toSet()
        assertTrue(channelIds.contains("CNN.us"))
        assertTrue(channelIds.contains("BBC.uk"))
    }

    @Test
    fun `all programmes get unique IDs`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="ch1">
                <title>Show 1</title>
              </programme>
              <programme start="20260605110000 +0000" stop="20260605120000 +0000" channel="ch1">
                <title>Show 2</title>
              </programme>
              <programme start="20260605120000 +0000" stop="20260605130000 +0000" channel="ch2">
                <title>Show 3</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        val ids = result.programs.map { it.id }.toSet()
        assertEquals("All programme IDs should be unique", 3, ids.size)
    }

    @Test
    fun `description tag alternative spelling is supported`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="ch1">
                <title>Show</title>
                <description>Full description here</description>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertEquals("Full description here", result.programs[0].description)
    }

    @Test
    fun `missing start and stop times are rejected`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme channel="ch1">
                <title>No Times</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertFalse(result.isSuccess)
        assertTrue(result.programs.isEmpty())
        assertEquals(1, result.stats.invalidTimePrograms)
    }

    // ── Regression tests for XMLTV-LEAK-1 (resource leak fix) ────────

    @Test
    fun `InputStream is closed when no programme elements exist`() {
        // Regression for XMLTV-LEAK-1: early return path leaked BufferedReader
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="CNN.us">
                <display-name>CNN</display-name>
              </channel>
            </tv>
        """.trimIndent()

        var closed = false
        val trackingStream = object : java.io.ByteArrayInputStream(xmltv.toByteArray(Charsets.UTF_8)) {
            override fun close() {
                closed = true
                super.close()
            }
        }

        val result = runBlocking { XMLTVParser.parse(trackingStream) }
        assertFalse(result.isSuccess)
        assertTrue("InputStream should be closed on early return path", closed)
    }

    @Test
    fun `InputStream is closed after successful parse`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="ch1">
                <title>Test Show</title>
              </programme>
            </tv>
        """.trimIndent()

        var closed = false
        val trackingStream = object : java.io.ByteArrayInputStream(xmltv.toByteArray(Charsets.UTF_8)) {
            override fun close() {
                closed = true
                super.close()
            }
        }

        val result = runBlocking { XMLTVParser.parse(trackingStream) }
        assertTrue(result.isSuccess)
        assertTrue("InputStream should be closed after successful parse", closed)
    }

    @Test
    fun `XMLTV parser handles single quotes for XML attributes`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start='20260605100000 +0000' stop='20260605110000 +0000' channel='CNN.us'>
                <title>Single Quote Show</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertEquals(1, result.programs.size)
        assertEquals("CNN.us", result.programs[0].channelId)
    }

    @Test
    fun `XMLTV parser handles colons in timezone offsets`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +00:00" stop="20260605110000 +00:00" channel="CNN.us">
                <title>Colon TZ Show</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertEquals(1, result.programs.size)
        assertTrue(result.programs[0].startTimeMs > 0)
    }

    @Test
    fun `XMLTV parser unescapes XML and HTML entities in titles and descriptions`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="CNN.us">
                <title>News &amp; Sports &quot;Special&quot; &apos;Tonight&apos;</title>
                <desc>Preview &lt;and&gt; show details</desc>
              </programme>
            </tv>
        """.trimIndent()

        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertEquals(1, result.programs.size)
        assertEquals("News & Sports \"Special\" 'Tonight'", result.programs[0].title)
        assertEquals("Preview <and> show details", result.programs[0].description)
    }

    @Test
    fun `XMLTV parser streaming callback is invoked and does not accumulate`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="CNN.us">
                <title>Breaking News</title>
              </programme>
              <programme start="20260605110000 +0000" stop="20260605120000 +0000" channel="CNN.us">
                <title>Noon Report</title>
              </programme>
            </tv>
        """.trimIndent()

        val parsedList = mutableListOf<EPGProgram>()
        val result = runBlocking {
            XMLTVParser.parse(
                java.io.ByteArrayInputStream(xmltv.toByteArray(Charsets.UTF_8)),
                onProgramParsed = { parsedList.add(it) }
            )
        }

        assertTrue(result.isSuccess)
        assertTrue("Programs should NOT be accumulated in EPGImportResult when callback is set", result.programs.isEmpty())
        assertEquals(2, parsedList.size)
        assertEquals("Breaking News", parsedList[0].title)
        assertEquals("Noon Report", parsedList[1].title)
    }

    @Test
    fun `XMLTV parser handles single line XMLTV file correctly`() {
        val xmltv = """<tv><programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="CNN.us"><title>Single Line Show</title></programme></tv>"""
        val result = parse(xmltv)
        assertTrue(result.isSuccess)
        assertEquals(1, result.programs.size)
        assertEquals("Single Line Show", result.programs[0].title)
    }
}

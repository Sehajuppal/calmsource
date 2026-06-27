package com.example.calmsource.core.parser

import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mission27XmlTvWindowStressTest {
    @Test
    fun `large guide discards out of window programs before retention`() = kotlinx.coroutines.runBlocking {
        val xml = buildString {
            appendLine("<tv>")
            repeat(4_997) { index ->
                appendLine(
                    """<programme start="20200101000000 +0000" stop="20200101010000 +0000" channel="old-$index"><title>Old $index</title></programme>"""
                )
            }
            repeat(3) { index ->
                appendLine(
                    """<programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="current-$index"><title>Current $index</title></programme>"""
                )
            }
            appendLine("</tv>")
        }
        val parser = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT)
        val result = XMLTVParser.parse(
            ByteArrayInputStream(xml.toByteArray()),
            XMLTVTimeWindow(
                startTimeMs = parser.parse("20260605090000 +0000")!!.time,
                endTimeMs = parser.parse("20260605120000 +0000")!!.time
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(3, result.programs.size)
        assertEquals(4_997, result.stats.outsideWindowPrograms)
        assertEquals(5_000, result.channelIds.size)
    }
}

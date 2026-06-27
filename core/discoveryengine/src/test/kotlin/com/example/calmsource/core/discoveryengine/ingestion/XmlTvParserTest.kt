package com.example.calmsource.core.discoveryengine.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream

class XmlTvParserTest {

    @Test
    fun testParseEpgChunked() {
        val mockXmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv generator-info-name="MockGenerator">
                <channel id="hbo-east">
                    <display-name>HBO East</display-name>
                </channel>
                <programme start="20260609120000 -0700" stop="20260609140000 -0700" channel="hbo-east">
                    <title lang="en">Dune: Part Two</title>
                    <desc lang="en">Paul Atreides unites with Chani and the Fremen.</desc>
                    <category>Movie</category>
                </programme>
                <programme start="20260609140000 -0700" stop="20260609153000 -0700" channel="hbo-east">
                    <title lang="en">Succession</title>
                    <desc lang="en">The Roy family struggles for control of the empire.</desc>
                    <category>Drama</category>
                </programme>
                <programme start="20260609153000 -0700" stop="20260609170000 -0700" channel="hbo-east">
                    <title lang="en">House of the Dragon</title>
                    <desc lang="en">Targaryen family civil war begins.</desc>
                    <category>Fantasy</category>
                </programme>
            </tv>
        """.trimIndent()

        val inputStream = ByteArrayInputStream(mockXmltv.toByteArray())
        val chunks = mutableListOf<List<com.example.calmsource.core.discoveryengine.models.EpgProgram>>()

        XmlTvParser.parseEpg(inputStream, chunkSize = 2) { chunk ->
            chunks.add(chunk)
        }

        // We expect 2 chunks: first has 2 programs (Dune, Succession), second has 1 program (House of the Dragon)
        assertEquals(2, chunks.size)
        assertEquals(2, chunks[0].size)
        assertEquals(1, chunks[1].size)

        // Verify content
        val firstProgram = chunks[0][0]
        assertEquals("hbo-east", firstProgram.channelId)
        assertEquals("Dune: Part Two", firstProgram.title)
        assertEquals("Movie", firstProgram.category)
        assertEquals("en", firstProgram.language)
        assertNotNull(firstProgram.id)

        // Verify dates parsing (e.g. 2026-06-09 12:00:00 -0700)
        // 20260609120000 -0700 -> timestamp conversion
        // start date is 2026-06-09T12:00:00 -0700, which in UTC is 19:00:00.
        // Let's verify start timestamp is greater than 0
        assertEquals(true, firstProgram.startTimeMs > 0)
        assertEquals(true, firstProgram.endTimeMs > firstProgram.startTimeMs)

        val thirdProgram = chunks[1][0]
        assertEquals("House of the Dragon", thirdProgram.title)
        assertEquals("Fantasy", thirdProgram.category)
    }
}

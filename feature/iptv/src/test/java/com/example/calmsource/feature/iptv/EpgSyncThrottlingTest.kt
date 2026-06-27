package com.example.calmsource.feature.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

class EpgSyncThrottlingTest {

    @Test
    fun `syncEPG does not overlap dangerously and processes sequentially`() = runBlocking {
        val activeParses = AtomicInteger(0)
        val maxConcurrentParses = AtomicInteger(0)

        class SlowInputStream(content: String) : InputStream() {
            private val stream = ByteArrayInputStream(content.toByteArray())
            
            override fun read(): Int {
                val currentActive = activeParses.incrementAndGet()
                synchronized(maxConcurrentParses) {
                    if (currentActive > maxConcurrentParses.get()) {
                        maxConcurrentParses.set(currentActive)
                    }
                }
                
                Thread.sleep(100)
                
                val result = stream.read()
                activeParses.decrementAndGet()
                return result
            }
            
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val currentActive = activeParses.incrementAndGet()
                synchronized(maxConcurrentParses) {
                    if (currentActive > maxConcurrentParses.get()) {
                        maxConcurrentParses.set(currentActive)
                    }
                }
                
                Thread.sleep(50)
                
                val result = stream.read(b, off, len)
                activeParses.decrementAndGet()
                return result
            }
        }

        val epgXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="test-channel-1">
                <title>Test Program 1</title>
              </programme>
            </tv>
        """.trimIndent()

        val provider = IPTVRepository.addProvider("Test Provider", "http://fake.com")
        val source1 = IPTVRepository.addEpgSource(provider.id, "source1", "http://fake.com/xmltv")
        val source2 = IPTVRepository.addEpgSource(provider.id, "source2", "http://fake.com/xmltv")

        val job1 = async(Dispatchers.IO) {
            IPTVRepository.syncEPG(source1.id, SlowInputStream(epgXml))
        }
        val job2 = async(Dispatchers.IO) {
            IPTVRepository.syncEPG(source2.id, SlowInputStream(epgXml))
        }

        job1.await()
        job2.await()

        assertEquals("EPG Sync operations should run sequentially due to Mutex, but they overlapped", 1, maxConcurrentParses.get())
    }

    @Test
    fun `syncEPG ingests EPG programs into Discovery Engine test hook`() = runBlocking {
        val now = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val start1 = sdf.format(java.util.Date(now - 1000 * 60 * 30)) // 30 mins ago
        val end1 = sdf.format(java.util.Date(now + 1000 * 60 * 30)) // 30 mins from now
        val start2 = sdf.format(java.util.Date(now + 1000 * 60 * 60)) // 1 hour from now
        val end2 = sdf.format(java.util.Date(now + 1000 * 60 * 90)) // 1.5 hours from now

        val epgXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="$start1" stop="$end1" channel="channel-1">
                <title>Show 1</title>
                <desc>Description 1</desc>
                <category>Comedy</category>
              </programme>
              <programme start="$start2" stop="$end2" channel="channel-1">
                <title>Show 2</title>
                <desc>Description 2</desc>
                <category>Drama</category>
              </programme>
            </tv>
        """.trimIndent()

        val ingestedPrograms = mutableListOf<com.example.calmsource.core.model.EPGProgram>()
        IPTVRepository.epgProgramsIngestedInTest = ingestedPrograms

        val provider = IPTVRepository.addProvider("Test Provider 2", "http://fake.com")
        val source = IPTVRepository.addEpgSource(provider.id, "test-source-id", "http://fake.com/xmltv")

        try {
            IPTVRepository.syncEPG(source.id, java.io.ByteArrayInputStream(epgXml.toByteArray()))

            assertEquals(2, ingestedPrograms.size)
            assertEquals("Show 1", ingestedPrograms[0].title)
            assertEquals("Comedy", ingestedPrograms[0].category)
            assertEquals("Show 2", ingestedPrograms[1].title)
            assertEquals("Drama", ingestedPrograms[1].category)
        } finally {
            IPTVRepository.epgProgramsIngestedInTest = null
        }
    }
}

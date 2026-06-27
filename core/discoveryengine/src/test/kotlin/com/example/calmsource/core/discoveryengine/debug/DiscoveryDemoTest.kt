package com.example.calmsource.core.discoveryengine.debug

import com.example.calmsource.core.discoveryengine.normalization.MetadataNormalizer
import org.junit.Test

class DiscoveryDemoTest {

    @Test
    fun runDiscoveryEngineDemo() {
        println("\n==================================================")
        println("       DISCOVERY ENGINE DEMO RUNNER V1            ")
        println("==================================================")

        // 1. Generate Fake Data
        println("\n--- [1] Mock Data Generation ---")
        val profiles = FakeDataGenerator.generateProfiles()
        val mediaItems = FakeDataGenerator.generateMediaItems()
        val streams = FakeDataGenerator.generateMediaStreams()
        val channels = FakeDataGenerator.generateIptvChannels()
        val programs = FakeDataGenerator.generateEpgPrograms()
        val watchEvents = FakeDataGenerator.generateWatchEvents()
        val searchEvents = FakeDataGenerator.generateSearchEvents()

        println("- Generated ${profiles.size} User Profiles:")
        profiles.forEach { p -> println("  * Profile ID: ${p.id}, Name: ${p.name}") }

        println("- Generated ${mediaItems.size} Media Items:")
        mediaItems.forEach { m -> println("  * Item ID: ${m.id}, Type: ${m.type}, Title: '${m.title}', Genres: ${m.genres}") }

        println("- Generated ${streams.size} Media Streams:")
        streams.forEach { s -> println("  * Stream ID: ${s.id}, Quality: ${s.resolution ?: "N/A"}, Size: ${s.sizeInBytes ?: 0} bytes") }

        println("- Generated ${channels.size} IPTV Channels:")
        channels.forEach { c -> println("  * Channel ID: ${c.id}, Name: '${c.name}'") }

        println("- Generated ${programs.size} EPG Programs:")
        programs.forEach { e -> println("  * Program ID: ${e.id}, Title: '${e.title}', Category: ${e.category}") }

        println("- Generated ${watchEvents.size} Watch Events & ${searchEvents.size} Search Events")

        // 2. Demonstrate Metadata Normalization & Aliases
        println("\n--- [2] Metadata Normalization Pipeline ---")
        println("a) Title Normalization:")
        val titlesToTest = listOf(
            "Inception (2010)",
            "Spider-Man: Into the Spider-Verse",
            "The Lord... of the Rings!!!",
            "Café"
        )
        titlesToTest.forEach { title ->
            val norm = MetadataNormalizer.normalizeTitle(title)
            val aliases = MetadataNormalizer.generateTitleAliases(title)
            println("   Original: '$title'")
            println("   Normalized: '$norm'")
            println("   Aliases: $aliases")
            println("   ----------------------------------------")
        }

        println("b) IPTV Channel Name Normalization:")
        val channelsToTest = listOf(
            "US: HBO HD [BACKUP]",
            "FR | CANAL+ SPORT FHD",
            "[ES] Action 1080p",
            "uk- sky news raw"
        )
        channelsToTest.forEach { chan ->
            val norm = MetadataNormalizer.normalizeChannelName(chan)
            val aliases = MetadataNormalizer.generateChannelAliases(chan)
            println("   Original: '$chan'")
            println("   Normalized: '$norm'")
            println("   Aliases: $aliases")
            println("   ----------------------------------------")
        }

        println("c) Stremio Stream Title Noise Stripping:")
        val streamsToTest = listOf(
            "[RD] Inception.2010.2160p.BluRay.x265.HDR.Atmos.7.1-FGT",
            "Inception.2010.1080p.BluRay.x264.DTS-5.1-YIFY",
            "Spider-Man Into the Spider-Verse (2018) Multi-Audio Dual 1080p HEVC x265"
        )
        streamsToTest.forEach { stream ->
            val cleaned = MetadataNormalizer.removeStreamNoise(stream)
            println("   Original: '$stream'")
            println("   Cleaned Title: '$cleaned'")
            println("   ----------------------------------------")
        }

        println("\n==================================================")
        println("     DEMO RUNNER COMPLETED SUCCESSFULLY           ")
        println("==================================================")
    }
}

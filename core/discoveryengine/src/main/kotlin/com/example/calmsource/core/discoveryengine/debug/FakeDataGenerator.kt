package com.example.calmsource.core.discoveryengine.debug

import com.example.calmsource.core.discoveryengine.models.*

object FakeDataGenerator {

    fun generateProfiles(): List<LocalProfile> {
        val now = System.currentTimeMillis()
        return listOf(
            LocalProfile("p-adult", "Main User", "https://example.com/avatar1.png", now - 1000000),
            LocalProfile("p-kids", "Kids Mode", "https://example.com/avatar2.png", now - 500000),
            LocalProfile("p-sports", "Sports Addict", null, now - 10000)
        )
    }

    fun generateMediaItems(): List<MediaItem> {
        return listOf(
            // Movies
            MediaItem(
                id = "m-inception",
                type = "movie",
                title = "Inception",
                overview = "A thief who steals corporate secrets through the use of dream-sharing technology.",
                posterUrl = "https://image.tmdb.org/t/p/w500/inceptions_poster.jpg",
                rating = 8.8,
                releaseYear = 2010,
                genres = listOf("Sci-Fi", "Action", "Adventure"),
                cast = listOf("Leonardo DiCaprio", "Joseph Gordon-Levitt", "Elliot Page"),
                director = "Christopher Nolan",
                language = "en",
                durationMs = 8880000, // 148 mins
                externalIds = mapOf("imdb" to "tt1375666", "tmdb" to "27205"),
                source = "stremio-addon"
            ),
            MediaItem(
                id = "m-dark-knight",
                type = "movie",
                title = "The Dark Knight",
                overview = "When the menace known as the Joker wreaks havoc and chaos on the people of Gotham.",
                posterUrl = "https://image.tmdb.org/t/p/w500/dark_knight_poster.jpg",
                rating = 9.0,
                releaseYear = 2008,
                genres = listOf("Action", "Crime", "Drama"),
                cast = listOf("Christian Bale", "Heath Ledger", "Aaron Eckhart"),
                director = "Christopher Nolan",
                language = "en",
                durationMs = 9120000, // 152 mins
                externalIds = mapOf("imdb" to "tt0468569", "tmdb" to "155"),
                source = "stremio-addon"
            ),
            MediaItem(
                id = "m-spiderman-verse",
                type = "movie",
                title = "Spider-Man: Into the Spider-Verse",
                overview = "Teen Miles Morales becomes the Spider-Man of his universe and must join with five spider-powered individuals.",
                posterUrl = "https://image.tmdb.org/t/p/w500/spiderman_poster.jpg",
                rating = 8.4,
                releaseYear = 2018,
                genres = listOf("Animation", "Action", "Adventure"),
                cast = listOf("Shameik Moore", "Jake Johnson", "Hailee Steinfeld"),
                director = "Bob Persichetti",
                language = "en",
                durationMs = 7020000, // 117 mins
                externalIds = mapOf("imdb" to "tt4633694", "tmdb" to "324857"),
                source = "stremio-addon"
            ),
            // Series & Episodes
            MediaItem(
                id = "s-breaking-bad",
                type = "series",
                title = "Breaking Bad",
                overview = "A chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing and selling methamphetamine.",
                posterUrl = "https://image.tmdb.org/t/p/w500/breaking_bad_poster.jpg",
                rating = 9.5,
                releaseYear = 2008,
                genres = listOf("Crime", "Drama", "Thriller"),
                cast = listOf("Bryan Cranston", "Aaron Paul", "Anna Gunn"),
                director = "Vince Gilligan",
                language = "en",
                externalIds = mapOf("imdb" to "tt0903747", "tmdb" to "1396"),
                source = "stremio-addon"
            ),
            MediaItem(
                id = "e-bb-s1e1",
                type = "episode",
                title = "Pilot",
                overview = "Walter White begins his descent into the criminal underworld.",
                releaseYear = 2008,
                durationMs = 3480000,
                seriesId = "s-breaking-bad",
                seasonNumber = 1,
                episodeNumber = 1,
                source = "stremio-addon"
            )
        )
    }

    fun generateMediaStreams(): List<MediaStream> {
        return listOf(
            MediaStream(
                id = "str-inception-4k",
                mediaItemId = "m-inception",
                title = "[RD] Inception.2010.2160p.BluRay.x265.HDR.Atmos.7.1-FGT",
                url = "https://debrid-proxy.com/stream/inception-4k",
                resolution = "2160p",
                codec = "hevc",
                quality = "BluRay",
                sizeInBytes = 48512390123L,
                language = "en",
                source = "aio-streams"
            ),
            MediaStream(
                id = "str-inception-1080p",
                mediaItemId = "m-inception",
                title = "Inception.2010.1080p.BluRay.x264.DTS-5.1-YIFY",
                url = "https://debrid-proxy.com/stream/inception-1080p",
                resolution = "1080p",
                codec = "h264",
                quality = "BluRay",
                sizeInBytes = 2451239012L,
                language = "en",
                source = "aio-streams"
            ),
            MediaStream(
                id = "str-spiderman-1080p",
                mediaItemId = "m-spiderman-verse",
                title = "Spider-Man.Into.the.Spider-Verse.2018.1080p.BluRay.x264-SPARKS",
                url = "https://torrent-direct.com/spider-verse",
                resolution = "1080p",
                codec = "h264",
                quality = "BluRay",
                sizeInBytes = 8512390123L,
                language = "en",
                source = "stremio-catalog"
            )
        )
    }

    fun generateIptvChannels(): List<IptvChannel> {
        return listOf(
            IptvChannel(
                id = "chan-hbo-us",
                name = "US: HBO HD [BACKUP]",
                logoUrl = "https://example.com/hbo.png",
                streamUrl = "http://iptvprovider.xyz/live/hbo.m3u8",
                category = "Movies & Series",
                providerId = "prov-premium",
                tvgId = "HBO.us"
            ),
            IptvChannel(
                id = "chan-canal-fr",
                name = "FR | CANAL+ SPORT FHD",
                logoUrl = "https://example.com/canal_sport.png",
                streamUrl = "http://iptvprovider.xyz/live/canal_sport.m3u8",
                category = "Sports",
                providerId = "prov-premium",
                tvgId = "CanalSport.fr"
            ),
            IptvChannel(
                id = "chan-bbc-one",
                name = "UK: BBC One HD",
                logoUrl = "https://example.com/bbc_one.png",
                streamUrl = "http://iptvprovider.xyz/live/bbc_one.m3u8",
                category = "General",
                providerId = "prov-premium",
                tvgId = "BBCOne.uk"
            )
        )
    }

    fun generateEpgPrograms(): List<EpgProgram> {
        val now = System.currentTimeMillis()
        val oneHour = 3600000L
        return listOf(
            EpgProgram(
                id = "epg-hbo-prog1",
                channelId = "chan-hbo-us",
                title = "Dune: Part Two",
                description = "Paul Atreides unites with Chani and the Fremen while seeking revenge.",
                category = "Movie",
                startTimeMs = now - oneHour,
                endTimeMs = now + (oneHour * 2),
                language = "en"
            ),
            EpgProgram(
                id = "epg-canal-prog1",
                channelId = "chan-canal-fr",
                title = "Monaco F1 Grand Prix Live",
                description = "Live coverage of the qualifying sessions at Monte Carlo.",
                category = "Sports",
                startTimeMs = now - (oneHour / 2),
                endTimeMs = now + (oneHour * 3 / 2),
                language = "fr"
            )
        )
    }

    fun generateWatchEvents(): List<WatchEvent> {
        val now = System.currentTimeMillis()
        return listOf(
            WatchEvent(
                profileId = "p-adult",
                itemId = "m-inception",
                itemType = "movie",
                timestamp = now - 7200000,
                progressMs = 4500000,
                durationMs = 8880000,
                eventType = "progress"
            ),
            WatchEvent(
                profileId = "p-adult",
                itemId = "e-bb-s1e1",
                itemType = "episode",
                timestamp = now - 3600000,
                progressMs = 3480000,
                durationMs = 3480000,
                eventType = "completed"
            )
        )
    }

    fun generateSearchEvents(): List<SearchEvent> {
        val now = System.currentTimeMillis()
        return listOf(
            SearchEvent("p-adult", "Incept", now - 7500000, "m-inception"),
            SearchEvent("p-kids", "Spider-man", now - 200000)
        )
    }
}

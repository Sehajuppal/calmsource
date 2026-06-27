/**
 * Demo and test fixture data for the CalmSource application.
 *
 * This singleton provides fake media items, channels, stream sources, and account data
 * used when no real providers are configured. It serves as:
 * - Default content shown on first app launch
 * - Test fixture data for unit and integration tests
 * - Demo data for development and UI prototyping
 *
 * **Important**: This is mutable shared state. Fields marked @Volatile are written
 * from repository singletons (e.g., [ExtensionRepository], [DebridRepository]).
 * In production, this would be replaced by Room database queries.
 *
 * @see IPTVRepository for IPTV channel/program management
 * @see DebridRepository for debrid account management
 * @see ExtensionRepository for extension provider management
 */
package com.example.calmsource.core.model

object FakeData {

    val defaultPreferences = UserPreferences(
        primaryLanguage = "Hindi",
        secondaryLanguage = "English",
        subtitleLanguage = "English",
        sourcePriority = "Auto-pick best source",
        preferCachedDebrid = true,
        preferIptvExactMatch = true,
        preferFhdOrBetter = true,
        hideLowQuality = false, // Keep false so we can view all options in manual list
        hideDuplicates = true,
        preferOriginalAudio = false,
        preferDubbedAudio = false,
        preferDualAudio = true,
        preferHighestQuality = true,
        preferLowerDataUsage = false,
        askBeforeChoosingSource = false
    )

    @Volatile
    var activePreferences = defaultPreferences

    val languages = listOf(
        LanguageOption("hi", "Hindi"),
        LanguageOption("en", "English"),
        LanguageOption("ta", "Tamil"),
        LanguageOption("es", "Spanish")
    )

    val iptvProviders = listOf(
        IPTVProvider("iptv-1", "Premium IPTV USA", "http://example.com/playlist1.m3u", isEnabled = true, ProviderHealth.HEALTHY),
        IPTVProvider("iptv-2", "Global TV Live", "http://example.com/playlist2.m3u", isEnabled = true, ProviderHealth.HEALTHY),
        IPTVProvider("iptv-3", "Backup M3U Playlist", "http://example.com/playlist3.m3u", isEnabled = false, ProviderHealth.FAILED)
    )

    private val isTest: Boolean get() = TestEnvironment.isTest

    @Volatile
    var extensionProviders: List<ExtensionProvider> = if (isTest) listOf(
        ExtensionProvider("ext-legal-demo", "Public Domain Movies", "https://legal-demo.com/manifest.json", isEnabled = true, ExtensionHealth.ACTIVE, 1, ExtensionManifest(id = "ext-legal-demo", name = "Public Domain Movies", description = "Legal public domain content provider", version = "1.0.0", resources = listOf("catalog", "search", "stream"), types = listOf("movie", "series"))),
        ExtensionProvider("ext-torrentio", "Torrentio Addon", "https://torrentio.strem.io/manifest.json", isEnabled = true, ExtensionHealth.ACTIVE, 10, ExtensionManifest(id = "ext-torrentio", name = "Torrentio Addon", description = "Torrent stream provider", version = "1.0.0", resources = listOf("stream"), types = listOf("movie", "series"))),
        ExtensionProvider("ext-aiostreams", "AIOStreams Aggregator", "https://aiostreams.net/manifest.json", isEnabled = true, ExtensionHealth.ACTIVE, 20, ExtensionManifest(id = "ext-aiostreams", name = "AIOStreams Aggregator", description = "Stream aggregator provider", version = "1.0.0", resources = listOf("stream"), types = listOf("movie", "series"))),
        ExtensionProvider("ext-slow", "Slow Catalog Addon", "https://slowaddon.org/manifest.json", isEnabled = true, ExtensionHealth.SLOW, 30, ExtensionManifest(id = "ext-slow", name = "Slow Catalog Addon", description = "Slow catalog provider", version = "1.0.0", resources = listOf("catalog", "stream"), types = listOf("movie", "series"))),
        ExtensionProvider("ext-failed", "Failed Scraper Engine", "https://failedaddon.com/manifest.json", isEnabled = true, ExtensionHealth.FAILED, 40, ExtensionManifest(id = "ext-failed", name = "Failed Scraper Engine", description = "Failed scraper provider", version = "1.0.0", resources = listOf("stream"), types = listOf("movie", "series")))
    ) else emptyList()

    @Volatile
    var debridAccounts: List<DebridAccount> = if (isTest) listOf(
        DebridAccount(
            id = "deb-rd",
            providerType = DebridProviderType.REAL_DEBRID,
            providerName = "Real-Debrid",
            isConnected = false,
            email = null,
            username = null,
            tokenSet = null,
            status = null,
            health = DebridAccountHealth.HEALTHY
        ),
        DebridAccount(
            id = "deb-ad",
            providerType = DebridProviderType.ALL_DEBRID,
            providerName = "AllDebrid",
            isConnected = false,
            email = null,
            username = null,
            tokenSet = null,
            status = null,
            health = DebridAccountHealth.HEALTHY
        ),
        DebridAccount(
            id = "deb-pm",
            providerType = DebridProviderType.PREMIUMIZE,
            providerName = "Premiumize",
            isConnected = false,
            email = null,
            username = null,
            tokenSet = null,
            status = null,
            health = DebridAccountHealth.HEALTHY
        )
    ) else emptyList()

    // Movies
    val movieSpiderman = MediaItem(
        id = "movie-spiderman",
        title = "Spider-Man: Homecoming",
        type = MediaType.MOVIE,
        overview = "Thrilled by his experience with the Avengers, Peter Parker returns home, where he lives with his Aunt May, under the watchful eye of his new mentor Tony Stark.",
        posterUrl = "https://images.unsplash.com/photo-1635805737707-575885ab0820?auto=format&fit=crop&q=80&w=300", // placeholder spiderman style artwork
        backdropUrl = "https://images.unsplash.com/photo-1608889175123-8ec330b86f84?auto=format&fit=crop&q=80&w=800",
        releaseDate = "2017-07-07",
        rating = 7.9
    )

    val movieInception = MediaItem(
        id = "movie-inception",
        title = "Inception",
        type = MediaType.MOVIE,
        overview = "Cobb, a skilled thief who steals valuable secrets from deep within the subconscious during the dream state, is given a chance at redemption: enter a target's mind and plant an idea.",
        posterUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?auto=format&fit=crop&q=80&w=300",
        backdropUrl = "https://images.unsplash.com/photo-1492446845049-9c50cc313f00?auto=format&fit=crop&q=80&w=800",
        releaseDate = "2010-07-16",
        rating = 8.8
    )

    val movieInterstellar = MediaItem(
        id = "movie-interstellar",
        title = "Interstellar",
        type = MediaType.MOVIE,
        overview = "The adventures of a group of explorers who make use of a newly discovered wormhole to surpass the limitations on human space travel.",
        posterUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&q=80&w=300",
        backdropUrl = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?auto=format&fit=crop&q=80&w=800",
        releaseDate = "2014-11-07",
        rating = 8.6
    )

    val movies = listOf(movieSpiderman, movieInception, movieInterstellar)

    // Shows
    val showBreakingBad = MediaItem(
        id = "show-breakingbad",
        title = "Breaking Bad",
        type = MediaType.SHOW,
        overview = "A high school chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing and selling methamphetamine with a former student in order to secure his family's future.",
        posterUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?auto=format&fit=crop&q=80&w=300",
        backdropUrl = "https://images.unsplash.com/photo-1560169897-fc0cdbdfa4d5?auto=format&fit=crop&q=80&w=800",
        releaseDate = "2008-01-20",
        rating = 9.5
    )

    val showStrangerThings = MediaItem(
        id = "show-strangerthings",
        title = "Stranger Things",
        type = MediaType.SHOW,
        overview = "When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces and one strange little girl.",
        posterUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&q=80&w=300",
        backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&q=80&w=800",
        releaseDate = "2016-07-15",
        rating = 8.7
    )

    val shows = listOf(showBreakingBad, showStrangerThings)

    // Live Channels
    val channelHbo = Channel("chan-hbo", "HBO East HD", "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?auto=format&fit=crop&q=80&w=100", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", "Movies")
    val channelStarSports = Channel("chan-starsports", "Star Sports 1 Hindi", "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&q=80&w=100", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", "Sports")
    val channelEspn = Channel("chan-espn", "ESPN HD", "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&q=80&w=100", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", "Sports")
    val channelCanal = Channel("chan-canal", "Canal+ Cinema", "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&q=80&w=100", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", "Movies")
    val liveChannels = listOf(channelHbo, channelStarSports, channelEspn, channelCanal)

    // Programs (EPG)
    val now = System.currentTimeMillis()
    val oneHour = 3600000L

    val epgPrograms = listOf(
        Program("prog-hbo-1", "chan-hbo", "Dune: Part Two", "The mythical journey of Paul Atreides continues.", now - oneHour * 2, now + oneHour),
        Program("prog-hbo-2", "chan-hbo", "House of the Dragon", "Predecessor story to Game of Thrones.", now + oneHour, now + oneHour * 3),
        
        Program("prog-star-1", "chan-starsports", "Live IPL: MI vs CSK", "Live cricket coverage from Mumbai.", now - oneHour, now + oneHour * 2),
        Program("prog-star-2", "chan-starsports", "IPL Post-Match Show", "Analysis of the Mumbai vs Chennai match.", now + oneHour * 2, now + oneHour * 3),
        
        Program("prog-espn-1", "chan-espn", "NBA Today", "Comprehensive news and opinions around the NBA.", now - oneHour / 2, now + oneHour),
        Program("prog-espn-2", "chan-espn", "MLS Soccer: LA Galaxy vs Inter Miami", "Live major league soccer coverage.", now + oneHour, now + oneHour * 4),

        Program("prog-canal-1", "chan-canal", "Anatomy of a Fall", "French legal drama thriller film.", now - oneHour * 3, now),
        Program("prog-canal-2", "chan-canal", "Oppenheimer", "The life of J. Robert Oppenheimer.", now, now + oneHour * 3)
    )

    // Stream Sources for Spider-Man: Homecoming (Target Search Result Test Case)
    val spidermanSources = listOf(
        // IPTV VOD Stream
        StreamSource(
            id = "src-spiderman-iptv",
            name = "Spider-Man: Homecoming [IPTV VOD] Dual-Audio (Hindi/English)",
            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny1.mp4",
            extensionId = "iptv-1",
            resolution = "1080p",
            videoCodec = "AVC",
            audioCodec = "AAC",
            sizeBytes = 2_800_000_000,
            seeds = null,
            language = "Hindi",
            isDubbed = true,
            isDualAudio = true
        ),
        // Debrid Cached 4K Stream
        StreamSource(
            id = "src-spiderman-debrid-4k",
            name = "Spider-Man.Homecoming.2017.2160p.UHD.HDR.BluRay.x265.REMUX-RealDebrid.mkv",
            url = "magnet:?xt=urn:btih:spiderman-4k-hash&dn=Spider-Man.Homecoming.2017.2160p",
            extensionId = "deb-rd",
            resolution = "4K",
            videoCodec = "HEVC",
            audioCodec = "DTS-HD",
            sizeBytes = 55_400_000_000,
            seeds = 184,
            language = "English",
            isSubbed = true
        ),
        // Extension Torrent English 1080p
        StreamSource(
            id = "src-spiderman-ext-1080p-en",
            name = "Spider-Man.Homecoming.2017.1080p.BluRay.DD5.1.x264-Torrentio.mkv",
            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny3.mp4",
            extensionId = "ext-torrentio",
            resolution = "1080p",
            videoCodec = "AVC",
            audioCodec = "E-AC3",
            sizeBytes = 8_200_000_000,
            seeds = 1204,
            language = "English"
        ),
        // Extension Torrent Hindi Dubbed 1080p
        StreamSource(
            id = "src-spiderman-ext-1080p-hi",
            name = "Spider-Man Homecoming (2017) 1080p Bluray x264 [Hindi DD 5.1 + English]",
            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny4.mp4",
            extensionId = "ext-torrentio",
            resolution = "1080p",
            videoCodec = "AVC",
            audioCodec = "AC3",
            sizeBytes = 4_500_000_000,
            seeds = 412,
            language = "Hindi",
            isDubbed = true,
            isDualAudio = true
        ),
        // Slow Extension Provider 720p (Tamil)
        StreamSource(
            id = "src-spiderman-slow-720p-ta",
            name = "Spider-Man.Homecoming.2017.720p.BRRip.x264.Tamil.mkv",
            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny5.mp4",
            extensionId = "ext-slow",
            resolution = "720p",
            videoCodec = "AVC",
            audioCodec = "AAC",
            sizeBytes = 1_400_000_000,
            seeds = 32,
            language = "Tamil",
            isDubbed = true
        ),
        // Low Quality SD Stream (Spanish)
        StreamSource(
            id = "src-spiderman-ext-sd-es",
            name = "Spider-Man.Homecoming.2017.BDRip.x264.Castellano.mp4",
            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            extensionId = "ext-torrentio",
            resolution = "SD",
            videoCodec = "AVC",
            audioCodec = "AAC",
            sizeBytes = 750_000_000,
            seeds = 8,
            language = "Spanish",
            isDubbed = true
        )
    )

    // Stream Sources for Inception
    val inceptionSources = listOf(
        StreamSource(
            id = "src-inception-debrid-1080p",
            name = "Inception.2010.1080p.BluRay.x264-RealDebrid.mkv",
            url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            extensionId = "deb-rd",
            resolution = "1080p",
            videoCodec = "AVC",
            audioCodec = "DTS",
            sizeBytes = 14_000_000_000,
            seeds = 422,
            language = "English"
        ),
        StreamSource(
            id = "src-inception-ext-1080p-hi",
            name = "Inception (2010) [Hindi Dubbed] 1080p BRRip x264",
            url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            extensionId = "ext-torrentio",
            resolution = "1080p",
            videoCodec = "AVC",
            audioCodec = "AAC",
            sizeBytes = 2_100_000_000,
            seeds = 94,
            language = "Hindi",
            isDubbed = true
        )
    )

    // Stream Sources for Interstellar
    val interstellarSources = listOf(
        StreamSource(
            id = "src-interstellar-debrid-4k",
            name = "Interstellar.2014.2160p.UHD.BluRay.x265-RealDebrid.mkv",
            url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            extensionId = "deb-rd",
            resolution = "4K",
            videoCodec = "HEVC",
            audioCodec = "AC3",
            sizeBytes = 42_000_000_000,
            seeds = 91,
            language = "English"
        )
    )

    // Map media items to their sources
    val mediaSourcesMap = mapOf(
        "movie-spiderman" to spidermanSources,
        "movie-inception" to inceptionSources,
        "movie-interstellar" to interstellarSources
    )

    fun getSourcesForMedia(mediaId: String): List<StreamSource> {
        return mediaSourcesMap[mediaId] ?: emptyList()
    }
}

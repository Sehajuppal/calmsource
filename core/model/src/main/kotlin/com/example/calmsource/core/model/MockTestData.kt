package com.example.calmsource.core.model

/**
 * Mock/demo data strings for testing IPTV and EPG import flows.
 *
 * These are used in both mobile and TV settings screens to provide
 * a one-tap demo import without requiring a real URL. They contain
 * legal, fictional channel data only.
 *
 * @see IPTVRepository.syncPlaylist for M3U import
 * @see IPTVRepository.syncEPG for XMLTV import
 */
object MockTestData {

    /** Sample M3U playlist with 3 fictional channels for demo import. */
    const val SAMPLE_M3U = """#EXTM3U
#EXTINF:-1 tvg-id="chan-demosports" tvg-name="Demo Sports" tvg-logo="https://picsum.photos/100" group-title="Sports",Demo Sports Channel
http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
#EXTINF:-1 tvg-id="chan-demonews" tvg-name="Demo News" tvg-logo="https://picsum.photos/101" group-title="News",Demo News Channel
http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
#EXTINF:-1 tvg-id="chan-demomusic" tvg-name="Demo Music" tvg-logo="https://picsum.photos/102" group-title="Music",Demo Music
http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"""

    /** Sample XMLTV EPG data with programs for the demo channels. */
    val SAMPLE_XMLTV: String
        get() {
            val now = System.currentTimeMillis()
            val hour = 3_600_000L
            val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.ROOT)
            return """<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="chan-demosports"><display-name>Demo Sports</display-name></channel>
  <channel id="chan-demonews"><display-name>Demo News</display-name></channel>
  <programme start="${fmt.format(now - hour)}" stop="${fmt.format(now + hour)}" channel="chan-demosports">
    <title>Demo Sports Live</title>
    <desc>Live sports demo broadcast</desc>
  </programme>
  <programme start="${fmt.format(now - hour)}" stop="${fmt.format(now + hour)}" channel="chan-demonews">
    <title>Demo Breaking News</title>
    <desc>Latest demo news coverage</desc>
  </programme>
</tv>"""
        }
}

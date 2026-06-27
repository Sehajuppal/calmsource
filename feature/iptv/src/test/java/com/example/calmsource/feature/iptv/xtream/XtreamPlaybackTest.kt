package com.example.calmsource.feature.iptv.xtream

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.IPTVProvider
import com.example.calmsource.core.model.IPTVProviderType
import com.example.calmsource.feature.iptv.FakeInMemoryIptvSecureTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XtreamPlaybackTest {

    private lateinit var secureTokenStore: FakeInMemoryIptvSecureTokenStore

    private val testProviderId = "xtream-provider-1"
    private val testUsername = "testuser"
    private val testPassword = "testpass123"
    private val testServerUrl = "http://example.com:8080"
    private val testStreamId = "12345"

    private val xtreamProvider = IPTVProvider(
        id = testProviderId,
        name = "Test Xtream Provider",
        playlistUrl = "http://example.com:8080/get.php?username=testuser&password=testpass123&type=m3u_plus",
        isEnabled = true,
        type = IPTVProviderType.XTREAM,
        serverUrl = testServerUrl,
        username = testUsername
    )

    private val m3uProvider = IPTVProvider(
        id = "m3u-provider-1",
        name = "Test M3U Provider",
        playlistUrl = "http://example.com/playlist.m3u",
        isEnabled = true,
        type = IPTVProviderType.M3U
    )

    private val xtreamChannel = createChannel(
        id = "ch-xtream-1",
        streamUrl = "xtream://stream_id/$testStreamId",
        providerId = testProviderId
    )

    private val m3uChannel = createChannel(
        id = "ch-m3u-1",
        streamUrl = "http://example.com/live/stream.ts",
        providerId = "m3u-provider-1"
    )

    @Before
    fun setUp() {
        secureTokenStore = FakeInMemoryIptvSecureTokenStore()
        secureTokenStore.savePassword(testProviderId, testUsername, testPassword)
    }

    // ── resolveLivePlaybackUrl: resolved URL contains stream_id ─────────

    @Test
    fun `resolved live URL contains stream_id`() {
        val url = XtreamPlaybackHelper.resolveLivePlaybackUrl(
            channel = xtreamChannel,
            secureTokenStore = secureTokenStore,
            providers = listOf(xtreamProvider)
        )
        assertNotNull(url)
        assertTrue("URL should contain the stream ID", url!!.contains(testStreamId))
    }

    @Test
    fun `resolved URL matches expected live format`() {
        val url = XtreamPlaybackHelper.resolveLivePlaybackUrl(
            channel = xtreamChannel,
            secureTokenStore = secureTokenStore,
            providers = listOf(xtreamProvider)
        )
        val expected = "$testServerUrl/live/$testUsername/$testPassword/$testStreamId.ts"
        assertEquals(expected, url)
    }

    @Test
    fun `resolved URL format matches live username password streamId pattern`() {
        val url = XtreamPlaybackHelper.resolveLivePlaybackUrl(
            channel = xtreamChannel,
            secureTokenStore = secureTokenStore,
            providers = listOf(xtreamProvider)
        )
        assertNotNull(url)
        // Verify the URL matches /live/{username}/{password}/{streamId}.ts
        val regex = Regex(""".*/live/[^/]+/[^/]+/\d+\.ts$""")
        assertTrue(
            "URL '$url' should match /live/username/password/streamId.ts pattern",
            regex.matches(url!!)
        )
    }

    // ── resolveLivePlaybackUrl: non-Xtream channels ─────────────────────

    @Test
    fun `non-Xtream channel returns null`() {
        val url = XtreamPlaybackHelper.resolveLivePlaybackUrl(
            channel = m3uChannel,
            secureTokenStore = secureTokenStore,
            providers = listOf(m3uProvider)
        )
        assertNull(url)
    }

    @Test
    fun `Xtream channel with M3U provider type returns null`() {
        val mismatchedProvider = xtreamProvider.copy(type = IPTVProviderType.M3U)
        val url = XtreamPlaybackHelper.resolveLivePlaybackUrl(
            channel = xtreamChannel,
            secureTokenStore = secureTokenStore,
            providers = listOf(mismatchedProvider)
        )
        assertNull(url)
    }

    // ── resolveLivePlaybackUrl: missing credentials ─────────────────────

    @Test
    fun `missing password returns null`() {
        val emptyStore = FakeInMemoryIptvSecureTokenStore()
        // Do NOT save password
        val url = XtreamPlaybackHelper.resolveLivePlaybackUrl(
            channel = xtreamChannel,
            secureTokenStore = emptyStore,
            providers = listOf(xtreamProvider)
        )
        assertNull(url)
    }

    @Test
    fun `provider without username returns null`() {
        val noUsernameProvider = xtreamProvider.copy(username = null)
        val url = XtreamPlaybackHelper.resolveLivePlaybackUrl(
            channel = xtreamChannel,
            secureTokenStore = secureTokenStore,
            providers = listOf(noUsernameProvider)
        )
        assertNull(url)
    }

    @Test
    fun `unknown provider ID returns null`() {
        val channelWithBadProvider = xtreamChannel.copy(providerId = "nonexistent-provider")
        val url = XtreamPlaybackHelper.resolveLivePlaybackUrl(
            channel = channelWithBadProvider,
            secureTokenStore = secureTokenStore,
            providers = listOf(xtreamProvider)
        )
        assertNull(url)
    }

    // ── resolveLivePlaybackUrl: serverUrl fallback ──────────────────────

    @Test
    fun `falls back to playlistUrl when serverUrl is empty`() {
        val providerNoServerUrl = xtreamProvider.copy(serverUrl = "")
        val url = XtreamPlaybackHelper.resolveLivePlaybackUrl(
            channel = xtreamChannel,
            secureTokenStore = secureTokenStore,
            providers = listOf(providerNoServerUrl)
        )
        assertNotNull(url)
        // extractBaseUrl strips playlistUrl to scheme+host+port: "http://example.com:8080"
        assertTrue("URL should start with extracted base URL", url!!.startsWith("http://example.com:8080/live/"))
    }

    // ── isXtreamChannel ─────────────────────────────────────────────────

    @Test
    fun `isXtreamChannel returns true for xtream pseudo-URL`() {
        assertTrue(XtreamPlaybackHelper.isXtreamChannel(xtreamChannel))
    }

    @Test
    fun `isXtreamChannel returns false for M3U channel`() {
        assertTrue(!XtreamPlaybackHelper.isXtreamChannel(m3uChannel))
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun createChannel(
        id: String,
        streamUrl: String,
        providerId: String
    ): IPTVChannel {
        return IPTVChannel(
            id = id,
            tvgId = null,
            tvgName = null,
            tvgLogo = null,
            groupTitle = null,
            name = "Test Channel $id",
            streamUrl = streamUrl,
            providerId = providerId,
            rawAttributes = emptyMap()
        )
    }

    private fun IPTVChannel.copy(providerId: String): IPTVChannel {
        return IPTVChannel(
            id = this.id,
            tvgId = this.tvgId,
            tvgName = this.tvgName,
            tvgLogo = this.tvgLogo,
            groupTitle = this.groupTitle,
            name = this.name,
            streamUrl = this.streamUrl,
            providerId = providerId,
            rawAttributes = this.rawAttributes
        )
    }
}

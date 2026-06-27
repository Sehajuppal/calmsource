package com.example.calmsource.core.discoveryengine.providers

import com.example.calmsource.core.discoveryengine.providers.fakes.FakeProviderCacheDao
import com.example.calmsource.core.discoveryengine.providers.fakes.FakeProviderRegistryDao
import com.example.calmsource.core.discoveryengine.providers.fakes.FakeProviderTelemetryDao
import com.example.calmsource.core.model.ResourceGovernor
import com.example.calmsource.core.model.ResourcePlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnrichmentQueueTest {

    private lateinit var cacheStore: ProviderCacheStore

    @Before
    fun setUp() {
        cacheStore = ProviderCacheStore(FakeProviderCacheDao())
        ProviderManager.initForTest(
            ProviderRegistryStore(FakeProviderRegistryDao()),
            cacheStore,
            ProviderTelemetryStore(FakeProviderTelemetryDao())
        )
        ProviderManager.setLocalOnlyMode(false)
        ProviderManager.setLowMemoryMode(false)
        ProviderManager.setPlaybackActive(false)
        ProviderManager.setPlaybackState(ResourcePlaybackState.IDLE)
        ProviderType.entries.forEach { ProviderManager.setEnrichmentAllowed(it, true) }
    }

    @Test
    fun queueDedupesTasksAndWritesSuccessfulMetadataToCache() = runBlocking {
        val provider = FakeMetadataProvider("queue-provider")
        ProviderManager.registerAddonProvider(
            addonId = provider.providerId,
            addonName = "Queue Provider",
            endpointUrl = "https://example.test/manifest.json",
            capabilities = setOf(ProviderType.METADATA),
            isEnabled = true,
            priority = 1,
            createProvider = { provider }
        )
        val queue = EnrichmentQueue(
            providerManager = ProviderManager,
            cacheStore = cacheStore,
            isLowMemoryMode = { false },
            isPlaybackActive = { false },
            isLocalOnlyMode = { false }
        )
        val task = EnrichmentTask.FetchMetadata("m-queue", "profile", ExternalIdSet(imdbId = "tt1"))

        assertTrue(queue.enqueue(task))
        repeat(99) {
            assertFalse(queue.enqueue(task))
        }
        queue.start()

        withTimeout(2_000) {
            while (cacheStore.getMetadata("m-queue").isEmpty()) {
                delay(25)
            }
        }

        assertEquals(1, provider.callCount)
        assertEquals("Queue Title", cacheStore.getMetadata("m-queue").single().title)
    }

    @Test
    fun taskDedupeKeysIncludeProfileAndLanguage() {
        val ids = ExternalIdSet(imdbId = "tt1")
        val english = EnrichmentTask.FetchSubtitles(
            mediaId = "m-subtitle",
            profileId = "adult",
            externalIds = ids,
            languageHints = listOf("en")
        )
        val spanish = english.copy(languageHints = listOf("es"))
        val kidsProfile = english.copy(profileId = "kids")

        assertTrue(english.dedupeKey != spanish.dedupeKey)
        assertTrue(english.dedupeKey != kidsProfile.dedupeKey)
    }

    @Test
    fun queueWaitsWhilePlaybackIsActive() = runBlocking {
        val provider = FakeMetadataProvider("playback-provider")
        ProviderManager.registerAddonProvider(
            addonId = provider.providerId,
            addonName = "Playback Provider",
            endpointUrl = "https://example.test/playback.json",
            capabilities = setOf(ProviderType.METADATA),
            isEnabled = true,
            priority = 1,
            createProvider = { provider }
        )
        var playbackActive = true
        val queue = EnrichmentQueue(
            providerManager = ProviderManager,
            cacheStore = cacheStore,
            isLowMemoryMode = { false },
            isPlaybackActive = { playbackActive },
            isLocalOnlyMode = { false }
        )

        assertTrue(queue.enqueue(EnrichmentTask.FetchMetadata("m-playback", "profile", ExternalIdSet(imdbId = "tt2"))))
        queue.start()

        delay(200)
        assertEquals(0, provider.callCount)

        playbackActive = false
        withTimeout(2_000) {
            while (cacheStore.getMetadata("m-playback").isEmpty()) {
                delay(25)
            }
        }

        assertEquals(1, provider.callCount)
    }

    @Test
    fun queueWaitsWhileResourceGovernorPausesBackgroundWork() = runBlocking {
        val provider = FakeMetadataProvider("governor-provider")
        ProviderManager.registerAddonProvider(
            addonId = provider.providerId,
            addonName = "Governor Provider",
            endpointUrl = "https://example.test/governor.json",
            capabilities = setOf(ProviderType.METADATA),
            isEnabled = true,
            priority = 1,
            createProvider = { provider }
        )
        val queue = EnrichmentQueue(
            providerManager = ProviderManager,
            cacheStore = cacheStore,
            isLowMemoryMode = { false },
            isPlaybackActive = { false },
            isLocalOnlyMode = { false }
        )

        ResourceGovernor.setPlaybackState(ResourcePlaybackState.READY_PLAYING)
        assertTrue(queue.enqueue(EnrichmentTask.FetchMetadata("m-governor", "profile", ExternalIdSet(imdbId = "tt3"))))
        queue.start()

        delay(200)
        assertEquals(0, provider.callCount)

        ResourceGovernor.setPlaybackState(ResourcePlaybackState.ENDED)
        withTimeout(2_000) {
            while (cacheStore.getMetadata("m-governor").isEmpty()) {
                delay(25)
            }
        }

        assertEquals(1, provider.callCount)
    }

    @Test
    fun queueDropsOldestDeferredWorkWhenCapacityIsReached() = runBlocking {
        val provider = FakeMetadataProvider("capacity-provider")
        ProviderManager.registerAddonProvider(
            addonId = provider.providerId,
            addonName = "Capacity Provider",
            endpointUrl = "https://example.test/capacity.json",
            capabilities = setOf(ProviderType.METADATA),
            isEnabled = true,
            priority = 1,
            createProvider = { provider }
        )
        val queue = EnrichmentQueue(
            providerManager = ProviderManager,
            cacheStore = cacheStore,
            isLowMemoryMode = { false },
            isPlaybackActive = { false },
            isLocalOnlyMode = { false },
            capacity = 2
        )

        assertTrue(queue.enqueue(EnrichmentTask.FetchMetadata("m-cap-1", "profile", ExternalIdSet(imdbId = "tt1"))))
        assertTrue(queue.enqueue(EnrichmentTask.FetchMetadata("m-cap-2", "profile", ExternalIdSet(imdbId = "tt2"))))
        assertTrue(queue.enqueue(EnrichmentTask.FetchMetadata("m-cap-3", "profile", ExternalIdSet(imdbId = "tt3"))))
        assertEquals(2, queue.snapshotQueuedCount())

        queue.start()
        withTimeout(2_000) {
            while (
                cacheStore.getMetadata("m-cap-2").isEmpty() ||
                cacheStore.getMetadata("m-cap-3").isEmpty()
            ) {
                delay(25)
            }
        }

        assertTrue(cacheStore.getMetadata("m-cap-1").isEmpty())
        assertEquals("Queue Title", cacheStore.getMetadata("m-cap-2").single().title)
        assertEquals("Queue Title", cacheStore.getMetadata("m-cap-3").single().title)
    }

    private class FakeMetadataProvider(
        override val providerId: String
    ) : MetadataProvider {
        var callCount = 0

        override suspend fun fetchMetadata(mediaId: String, ids: ExternalIdSet): ProviderResult<EnrichedMetadata> {
            callCount += 1
            return ProviderResult.Success(
                EnrichedMetadata(
                    title = "Queue Title",
                    originalTitle = null,
                    overview = null,
                    director = null,
                    runtimeMinutes = null,
                    language = null,
                    country = null,
                    posterUrl = null,
                    backdropUrl = null
                )
            )
        }
    }
}

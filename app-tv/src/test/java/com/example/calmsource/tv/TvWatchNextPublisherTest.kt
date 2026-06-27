package com.example.calmsource.tv

import androidx.tvprovider.media.tv.TvContractCompat
import com.example.calmsource.core.model.ContinueWatchingItem
import com.example.calmsource.core.model.UserMemoryContentType
import com.example.calmsource.core.model.UserMemoryReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TvWatchNextPublisherTest {
    @Test
    fun `watch next program contains progress and privacy safe deep link`() {
        val spec = TvWatchNextPublisher.buildSpec(
            ContinueWatchingItem(
                reference = UserMemoryReference(
                    itemKey = "media-safe",
                    contentType = UserMemoryContentType.MOVIE,
                    title = "Movie",
                    sourceId = "movie-id"
                ),
                progressMs = 120_000L,
                durationMs = 7_200_000L,
                updatedAt = 123L
            )
        )

        assertEquals(
            TvContractCompat.WatchNextPrograms.TYPE_MOVIE,
            spec.type
        )
        assertEquals(120_000, spec.progressMs)
        assertFalse(spec.intentUri.contains("http://"))
        assertFalse(spec.intentUri.contains("https://"))
        assertFalse(spec.intentUri.contains("token"))
    }
}

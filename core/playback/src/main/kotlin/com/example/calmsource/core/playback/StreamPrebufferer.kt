package com.example.calmsource.core.playback

import android.content.Context
import android.util.Log
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.network.NetworkClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.use
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object StreamPrebufferer {
    private val stateLock = Any()
    private var prebufferJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cachedMediaId: String? = null
    private var cachedStreamUrl: String? = null

    /**
     * Starts pre-buffering the top-ranked stream for the given media ID.
     * This warms up the DNS, TCP/TLS connection pool, and pre-caches the first 2MB of the stream.
     */
    fun preBufferStream(context: Context, profileId: String, mediaId: String) {
        synchronized(stateLock) {
            if (cachedMediaId == mediaId) return
            prebufferJob?.cancel()
            cachedMediaId = mediaId
            cachedStreamUrl = null
        }

        prebufferJob = scope.launch {
            try {
                val streams = try {
                    DiscoveryEngine.rankStreams(profileId, mediaId)
                } catch (e: Exception) {
                    Log.w("StreamPrebufferer", "Failed to rank streams for $mediaId", e)
                    emptyList()
                }

                if (streams.isEmpty()) {
                    Log.i("StreamPrebufferer", "No streams found to pre-buffer for $mediaId")
                    return@launch
                }

                val bestStream = streams.first()
                val url = bestStream.url ?: return@launch
                if (url.isBlank() || !url.startsWith("http")) return@launch

                synchronized(stateLock) { cachedStreamUrl = url }
                Log.i(
                    "StreamPrebufferer",
                    "Pre-buffering stream for $mediaId: ${PlaybackSource.redactUrl(url)}",
                )

                val client = NetworkClient.client
                client.get(url) {
                    headers {
                        append("Range", "bytes=0-2097151")
                    }
                }.use { response ->
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(65_536)
                        if (packet.isEmpty) break
                        packet.release()
                    }
                }
                Log.i("StreamPrebufferer", "Pre-buffering completed for $mediaId")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("StreamPrebufferer", "Pre-buffering failed for $mediaId: ${e.message}")
            }
        }
    }

    /**
     * Returns the pre-buffered URL if the media ID matches.
     */
    fun getPrebufferedUrl(mediaId: String): String? {
        synchronized(stateLock) {
            return if (cachedMediaId == mediaId) cachedStreamUrl else null
        }
    }

    /**
     * Cancels any active pre-buffering job.
     */
    fun cancel() {
        synchronized(stateLock) {
            prebufferJob?.cancel()
            prebufferJob = null
            cachedMediaId = null
            cachedStreamUrl = null
        }
    }
}

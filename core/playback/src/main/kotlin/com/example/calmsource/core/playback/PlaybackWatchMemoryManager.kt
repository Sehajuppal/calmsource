package com.example.calmsource.core.playback

import android.content.Context
import com.example.calmsource.core.database.repository.UserMemoryRepository
import com.example.calmsource.core.model.PlaybackRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

internal class PlaybackWatchMemoryManager(
    private val context: Context,
    private val userMemoryRepository: UserMemoryRepository
) {
    // Process-lifetime scope: intentionally not cancelable so in-flight writes complete on exit
    private val memoryPersistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryTracker = PlaybackMemoryTracker()
    private val memoryWriteLock = Any()

    private var memoryWriteJob: Job? = null
    private var pendingVodCheckpoint: PlaybackMemoryWrite.VodCheckpoint? = null
    private var debounceJob: Job? = null

    fun begin(request: PlaybackRequest) {
        memoryTracker.begin(request)
    }

    fun onPlaying(positionMs: Long, durationMs: Long) {
        enqueueMemoryWrites(memoryTracker.onPlaying(positionMs, durationMs))
    }

    fun onPeriodicCheckpoint(positionMs: Long, durationMs: Long) {
        enqueueMemoryWrites(memoryTracker.onPeriodicCheckpoint(positionMs, durationMs), debounce = true)
    }

    fun onPause(positionMs: Long, durationMs: Long) {
        enqueueMemoryWrites(memoryTracker.onPause(positionMs, durationMs))
    }

    fun onEnded(positionMs: Long, durationMs: Long) {
        enqueueMemoryWrites(memoryTracker.onEnded(positionMs, durationMs))
    }

    fun onRelease(positionMs: Long, durationMs: Long) {
        enqueueMemoryWrites(memoryTracker.onRelease(positionMs, durationMs))
    }

    private fun flushPendingCheckpoint() {
        val repo = userMemoryRepository
        synchronized(memoryWriteLock) {
            val checkpoint = pendingVodCheckpoint
            pendingVodCheckpoint = null
            debounceJob = null
            if (checkpoint != null) {
                memoryPersistenceScope.launch {
                    try {
                        persistMemoryWriteLocal(repo, checkpoint)
                    } catch (_: Exception) {
                        // Playback remains usable even if memory persistence fails
                    }
                }
            }
        }
    }

    private fun enqueueMemoryWrites(writes: List<PlaybackMemoryWrite>, debounce: Boolean = false) {
        if (writes.isEmpty()) return
        val repo = userMemoryRepository

        // If any write requires immediate recording (e.g. record history or completion),
        // override the debounce flag.
        val shouldDebounce = debounce && writes.none {
            it is PlaybackMemoryWrite.VodCheckpoint && (it.recordHistory || it.completed)
        }

        synchronized(memoryWriteLock) {
            if (shouldDebounce) {
                // Buffer the latest checkpoint
                val checkpoint = writes.firstOrNull { it is PlaybackMemoryWrite.VodCheckpoint } as? PlaybackMemoryWrite.VodCheckpoint
                if (checkpoint != null) {
                    pendingVodCheckpoint = checkpoint
                }

                if (debounceJob == null || debounceJob?.isActive == false) {
                    debounceJob = memoryPersistenceScope.launch {
                        delay(20000L) // Debounce watch progress updates for 20 seconds
                        flushPendingCheckpoint()
                    }
                }
            } else {
                // Immediate write: cancel pending debounce job and flush any pending checkpoint first
                debounceJob?.cancel()
                debounceJob = null

                val currentPending = pendingVodCheckpoint
                pendingVodCheckpoint = null

                val allWrites = if (currentPending != null) {
                    listOf(currentPending) + writes
                } else {
                    writes
                }

                val previousWrite = memoryWriteJob
                previousWrite?.cancel()
                memoryWriteJob = memoryPersistenceScope.launch {
                    runCatching { previousWrite?.join() }
                    allWrites.forEach { write ->
                        try {
                            persistMemoryWriteLocal(repo, write)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            // Playback remains usable even if memory persistence fails
                        }
                    }
                }
            }
        }
    }

    private suspend fun persistMemoryWriteLocal(repo: UserMemoryRepository, write: PlaybackMemoryWrite) {
        when (write) {
            is PlaybackMemoryWrite.LiveStarted -> {
                repo.recordRecentChannel(
                    reference = write.reference,
                    watchedAt = write.watchedAt
                )
            }
            is PlaybackMemoryWrite.VodCheckpoint -> {
                if (write.recordHistory) {
                    repo.recordWatchHistory(
                        reference = write.reference,
                        progressMs = write.progressMs,
                        durationMs = write.durationMs,
                        watchedAt = write.watchedAt
                    )
                }
                if (write.completed) {
                    repo.removeContinueWatching(write.reference.itemKey)
                } else if (write.progressMs > 0L) {
                    repo.upsertContinueWatching(
                        reference = write.reference,
                        progressMs = write.progressMs,
                        durationMs = write.durationMs,
                        updatedAt = write.watchedAt
                    )
                }
            }
        }
    }
}

package com.example.calmsource.core.playback

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class PrefetchPlan(
    val urls: List<String>,
    val skippedBecausePaused: Boolean,
    val droppedCount: Int
)

object PrefetchPlanner {
    fun plan(
        urls: Iterable<String?>,
        activeUrls: Set<String>,
        allowNonCriticalRequests: Boolean,
        maxRequests: Int
    ): PrefetchPlan {
        val candidates = urls
            .asSequence()
            .mapNotNull { it?.trim()?.takeIf(::isSupportedImageUrl) }
            .distinct()
            .filterNot(activeUrls::contains)
            .toList()

        if (!allowNonCriticalRequests || maxRequests <= 0) {
            return PrefetchPlan(
                urls = emptyList(),
                skippedBecausePaused = !allowNonCriticalRequests,
                droppedCount = candidates.size
            )
        }

        return PrefetchPlan(
            urls = candidates.take(maxRequests),
            skippedBecausePaused = false,
            droppedCount = (candidates.size - maxRequests).coerceAtLeast(0)
        )
    }

    private fun isSupportedImageUrl(value: String): Boolean {
        return value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
    }
}

class PrefetchCoordinator(
    context: Context,
    private val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    private val imageLoader: ImageLoader = Coil.imageLoader(context.applicationContext ?: context)
) : Closeable {
    private val appContext = context.applicationContext ?: context
    // Dispatchers.Default so URL flattening, deduplication, and dispatcher
    // bookkeeping never block the main thread. The actual Coil image fetch
    // runs on Coil's own IO pool; we just need to ensure the call site
    // (TvHomeScreen's LaunchedEffect) can return immediately.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val activeRequests = linkedMapOf<String, Disposable>()
    private val pendingRequests = linkedSetOf<String>()
    private val stateJob: Job

    init {
        stateJob = scope.launch {
            ImageCacheController.state.collectLatest { state ->
                if (state.nonCriticalRequestsPaused) {
                    cancelAll()
                }
            }
        }
    }

    fun prefetch(urls: Iterable<String?>): PrefetchPlan {
        val activeSnapshot = synchronized(lock) {
            activeRequests.keys + pendingRequests
        }
        val availableSlots = (maxRequests - activeSnapshot.size).coerceAtLeast(0)
        val plan = PrefetchPlanner.plan(
            urls = urls,
            activeUrls = activeSnapshot,
            allowNonCriticalRequests = ImageCacheController.shouldAllowNonCriticalRequests(),
            maxRequests = availableSlots
        )
        if (plan.skippedBecausePaused) {
            cancelAll()
            return plan
        }

        plan.urls.forEach { url ->
            synchronized(lock) {
                pendingRequests.add(url)
            }
            var succeeded = false
            val request = ImageRequest.Builder(appContext)
                .data(url)
                .listener(
                    onCancel = { complete(url) },
                    onError = { _, _ -> complete(url) },
                    onSuccess = { _, _ ->
                        synchronized(lock) { succeeded = true }
                        complete(url)
                    }
                )
                .build()
            val disposable = imageLoader.enqueue(request)
            val completedBeforeRegistration = synchronized(lock) {
                if (pendingRequests.remove(url)) {
                    activeRequests[url] = disposable
                    false
                } else {
                    true
                }
            }
            if (completedBeforeRegistration && !synchronized(lock) { succeeded }) {
                disposable.dispose()
            }
        }
        return plan
    }

    fun cancelAll() {
        val requests = synchronized(lock) {
            activeRequests.values.toList().also {
                activeRequests.clear()
                pendingRequests.clear()
            }
        }
        requests.forEach(Disposable::dispose)
    }

    fun activeRequestCount(): Int = synchronized(lock) {
        activeRequests.size + pendingRequests.size
    }

    override fun close() {
        stateJob.cancel()
        cancelAll()
    }

    private fun complete(url: String) {
        synchronized(lock) {
            pendingRequests.remove(url)
            activeRequests.remove(url)
        }
    }

    companion object {
        const val DEFAULT_MAX_REQUESTS = 12
    }
}

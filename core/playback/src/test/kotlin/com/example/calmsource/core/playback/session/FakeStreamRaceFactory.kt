package com.example.calmsource.core.playback.session

import android.content.Context
import com.example.calmsource.core.playback.StreamRaceRequest
import com.example.calmsource.core.playback.StreamRaceResult
import kotlinx.coroutines.delay

/**
 * Test double for [StreamRaceFactory] with a controllable delay and result.
 */
class FakeStreamRaceFactory(
    private val delayMs: Long = 0L,
    private val resultProvider: (StreamRaceRequest) -> StreamRaceResult,
) : StreamRaceFactory {

    override suspend fun race(context: Context, request: StreamRaceRequest): StreamRaceResult {
        if (delayMs > 0) {
            delay(delayMs)
        }
        return resultProvider(request)
    }
}

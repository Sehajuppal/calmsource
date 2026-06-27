package com.example.calmsource.core.playback.session

import android.content.Context
import com.example.calmsource.core.playback.Media3StreamProbe
import com.example.calmsource.core.playback.StreamRaceManager
import com.example.calmsource.core.playback.StreamRaceRequest
import com.example.calmsource.core.playback.StreamRaceResult

fun interface StreamRaceFactory {
    suspend fun race(context: Context, request: StreamRaceRequest): StreamRaceResult
}

object DefaultStreamRaceFactory : StreamRaceFactory {
    override suspend fun race(context: Context, request: StreamRaceRequest): StreamRaceResult {
        return StreamRaceManager(Media3StreamProbe(context)).race(request)
    }
}

package com.example.calmsource.core.playback

import androidx.media3.common.C
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.ktor.client.plugins.ResponseException

internal class CustomLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        return 3
    }

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception
        val isKtorResponseException = exception.javaClass.name == "io.ktor.client.plugins.ResponseException"
        val statusCode = when {
            exception is HttpDataSource.InvalidResponseCodeException -> exception.responseCode
            isKtorResponseException -> {
                runCatching {
                    val getResponse = exception.javaClass.getMethod("getResponse")
                    val response = getResponse.invoke(exception)
                    val getStatus = response.javaClass.getMethod("getStatus")
                    val status = getStatus.invoke(response)
                    val getValue = status.javaClass.getMethod("getValue")
                    getValue.invoke(status) as Int
                }.getOrDefault(-1)
            }
            else -> {
                val cause = exception.cause
                when (cause) {
                    is HttpDataSource.InvalidResponseCodeException -> cause.responseCode
                    is ResponseException -> cause.response.status.value
                    else -> -1
                }
            }
        }
        if (statusCode in setOf(400, 401, 403, 404, 405, 410, 451, 500)) {
            return C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }
}

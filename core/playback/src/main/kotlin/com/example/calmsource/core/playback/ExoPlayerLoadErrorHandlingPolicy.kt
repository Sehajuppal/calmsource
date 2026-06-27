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
        val statusCode = extractHttpStatusCode(exception)
        if (statusCode in setOf(400, 401, 403, 404, 405, 410, 451, 500)) {
            return C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    private fun extractHttpStatusCode(exception: java.io.IOException): Int {
        if (exception is HttpDataSource.InvalidResponseCodeException) {
            return exception.responseCode
        }
        val innerCause = exception.cause
        if (innerCause is HttpDataSource.InvalidResponseCodeException) {
            return innerCause.responseCode
        }
        if (innerCause is ResponseException) {
            return innerCause.response.status.value
        }
        if (exception.javaClass.name == "io.ktor.client.plugins.ResponseException") {
            val code = extractKtorStatusCodeReflective(exception)
            if (code != null) return code
        }
        if (innerCause != null && innerCause.javaClass.name == "io.ktor.client.plugins.ResponseException") {
            val code = extractKtorStatusCodeReflective(innerCause)
            if (code != null) return code
        }
        return -1
    }

    private fun extractKtorStatusCodeReflective(exception: Throwable): Int? {
        return try {
            val responseMethod = exception.javaClass.getMethod("getResponse")
            val response = responseMethod.invoke(exception)
            val statusMethod = response.javaClass.getMethod("getStatus")
            val status = statusMethod.invoke(response)
            val valueMethod = status.javaClass.getMethod("getValue")
            valueMethod.invoke(status) as? Int
        } catch (_: Exception) {
            null
        }
    }
}

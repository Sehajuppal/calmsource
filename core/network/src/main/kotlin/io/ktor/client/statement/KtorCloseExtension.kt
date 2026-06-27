package io.ktor.client.statement

import kotlinx.coroutines.cancel

/**
 * Extension function to close HttpResponse in Ktor versions where it is not AutoCloseable.
 * Cancels the coroutine scope of the HttpResponse to abort the connection and release resources.
 */
public fun HttpResponse.close() {
    try {
        this.cancel()
    } catch (_: Throwable) {
        // Ignore
    }
}

/**
 * Extension function to use and automatically close HttpResponse.
 */
public suspend inline fun <R> HttpResponse.use(block: (HttpResponse) -> R): R {
    try {
        return block(this)
    } finally {
        this.close()
    }
}

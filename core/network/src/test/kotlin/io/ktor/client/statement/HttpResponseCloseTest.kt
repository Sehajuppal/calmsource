package io.ktor.client.statement

import io.ktor.client.call.HttpClientCall
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class HttpResponseCloseTest {

    @Test
    fun testHttpResponseCloseCancelsJob() {
        val job = Job()
        val response = mock<HttpResponse> {
            on { coroutineContext } doReturn job
        }

        assertTrue("Coroutine job should be active initially", job.isActive)

        response.close()

        assertFalse("Coroutine job should be cancelled after close()", job.isActive)
    }

    @Test
    fun testHttpResponseUseExecutesAndCloses() = runBlocking {
        val job = Job()
        val response = mock<HttpResponse> {
            on { coroutineContext } doReturn job
        }

        var blockExecuted = false
        val result = response.use { res ->
            blockExecuted = true
            "test_result"
        }

        assertTrue("Block should have executed", blockExecuted)
        assertEquals("Result should be returned from block", "test_result", result)
        assertFalse("Coroutine job should be cancelled after use block", job.isActive)
    }

    @Test
    fun testHttpResponseUseClosesOnFailure() = runBlocking {
        val job = Job()
        val response = mock<HttpResponse> {
            on { coroutineContext } doReturn job
        }

        var exceptionThrown = false
        try {
            response.use { res ->
                throw RuntimeException("Simulated error")
            }
        } catch (e: RuntimeException) {
            if (e.message == "Simulated error") {
                exceptionThrown = true
            }
        }

        assertTrue("Exception should have been thrown", exceptionThrown)
        assertFalse("Coroutine job should be cancelled even if block throws an exception", job.isActive)
    }
}

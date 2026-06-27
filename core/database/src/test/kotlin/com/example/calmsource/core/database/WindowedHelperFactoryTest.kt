package com.example.calmsource.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WindowedHelperFactoryTest {

    @Test
    fun `default window size is 8 MB`() {
        assertEquals(8L * 1024 * 1024, WindowedHelperFactory.DEFAULT_CURSOR_WINDOW_BYTES)
    }

    @Test
    fun `factory construction is non-null`() {
        // Custom size is accepted and the factory is created.
        val factory = WindowedHelperFactory(16L * 1024 * 1024)
        assertNotNull(factory)
    }
}

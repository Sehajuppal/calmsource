package com.example.calmsource.core.discoveryengine.normalization

import org.junit.Assert.assertEquals
import org.junit.Test

class LevenshteinTest {

    @Test
    fun testDistance() {
        assertEquals(0, Levenshtein.distance("hello", "hello"))
        assertEquals(1, Levenshtein.distance("hello", "helo"))
        assertEquals(1, Levenshtein.distance("hello", "hellos"))
        assertEquals(1, Levenshtein.distance("hello", "hallo"))
        assertEquals(4, Levenshtein.distance("spiderman", "spidremna"))
        assertEquals(3, Levenshtein.distance("kitten", "sitting"))
        assertEquals(5, Levenshtein.distance("", "hello"))
        assertEquals(5, Levenshtein.distance("hello", ""))
    }
}

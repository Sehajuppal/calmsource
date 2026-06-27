package com.example.calmsource.core.discoveryengine.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.*

class FtsIndexManagerTest {

    private fun anyArray(): Array<Any?> {
        any(Array::class.java)
        return emptyArray()
    }

    private fun eqString(value: String): String {
        eq(value)
        return value
    }

    private fun containsString(value: String): String {
        contains(value)
        return value
    }

    @Test
    fun testFts5DetectionSuccess() {
        val mockDb = mock(SupportSQLiteDatabase::class.java)
        
        FtsIndexManager.initialize(mockDb)

        assertEquals(FtsIndexManager.FtsMode.FTS5, FtsIndexManager.activeMode)
        verify(mockDb).execSQL(eqString(
            "CREATE VIRTUAL TABLE IF NOT EXISTS fts_search_index USING fts5(" +
                    "id, type, title, normalized_title, overview, genres, cast_director, aliases" +
                    ")"
        ))
    }

    @Test
    fun testFts4FallbackSuccess() {
        val mockDb = mock(SupportSQLiteDatabase::class.java)
        
        // Throw exception on FTS5 creation to simulate lack of FTS5 support, but succeed on FTS4
        doThrow(RuntimeException("FTS5 check failed")).`when`(mockDb).execSQL(containsString("temp_fts5_check"))
        
        FtsIndexManager.initialize(mockDb)

        assertEquals(FtsIndexManager.FtsMode.FTS4, FtsIndexManager.activeMode)
        verify(mockDb).execSQL(eqString(
            "CREATE VIRTUAL TABLE IF NOT EXISTS fts_search_index USING fts4(" +
                    "id, type, title, normalized_title, overview, genres, cast_director, aliases" +
                    ")"
        ))
    }

    @Test
    fun testStandardFallbackSuccess() {
        val mockDb = mock(SupportSQLiteDatabase::class.java)
        
        // Throw exception on both FTS5 and FTS4 checks to simulate standard SQLite only
        doThrow(RuntimeException("FTS5 check failed")).`when`(mockDb).execSQL(containsString("temp_fts5_check"))
        doThrow(RuntimeException("FTS4 check failed")).`when`(mockDb).execSQL(containsString("temp_fts4_check"))
        
        FtsIndexManager.initialize(mockDb)

        assertEquals(FtsIndexManager.FtsMode.STANDARD, FtsIndexManager.activeMode)
        verify(mockDb).execSQL(containsString("CREATE TABLE IF NOT EXISTS fts_search_index"))
        verify(mockDb).execSQL(containsString("CREATE INDEX IF NOT EXISTS idx_fts_norm_title"))
    }

    @Test
    fun testUpsertIndexEntry() {
        val mockDb = mock(SupportSQLiteDatabase::class.java)
        
        FtsIndexManager.upsertIndexEntry(
            db = mockDb,
            id = "m-123",
            type = "movie",
            title = "Inception",
            normalizedTitle = "inception",
            overview = "A dream within a dream",
            genres = "Action,Sci-Fi",
            castDirector = "Leonardo DiCaprio,Christopher Nolan",
            aliases = "inception dream"
        )

        // Verify deletion run first
        verify(mockDb).execSQL(eqString("DELETE FROM fts_search_index WHERE id = ?"), anyArray())
        
        // Verify insert query
        verify(mockDb).execSQL(
            eqString("INSERT INTO fts_search_index(id, type, title, normalized_title, overview, genres, cast_director, aliases) VALUES(?, ?, ?, ?, ?, ?, ?, ?)"),
            anyArray()
        )
    }

    @Test
    fun testFormatFtsQuery() {
        assertEquals("inception*", FtsIndexManager.formatFtsQuery("Inception"))
        assertEquals("spider* man*", FtsIndexManager.formatFtsQuery("Spider-Man"))
        assertEquals("lord* rings*", FtsIndexManager.formatFtsQuery("Lord... rings"))
        assertEquals("", FtsIndexManager.formatFtsQuery("   "))
    }

    @Test
    fun fuzzyFallbackDefaultsOff() {
        assertEquals(false, DiscoverySearchFeatureFlags.enableFuzzyFallback)
    }
}

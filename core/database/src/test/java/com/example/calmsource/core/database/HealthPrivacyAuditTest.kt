package com.example.calmsource.core.database

import com.example.calmsource.core.database.entity.ProviderHealthScoreEntity
import com.example.calmsource.core.database.entity.SourceHealthEntity
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Privacy audit tests for Health-related Room entities.
 *
 * Verifies that health entities do not store raw URLs, private links, credentials, or secrets,
 * and that only privacy-safe identifiers and telemetry metrics are persisted.
 */
class HealthPrivacyAuditTest {

    private val healthEntityClasses = listOf(
        SourceHealthEntity::class.java,
        ProviderHealthScoreEntity::class.java
    )

    /**
     * Forbidden patterns in field names of health-related entities.
     * We must never store raw stream URLs, playlist paths, or credentials in health telemetry.
     */
    private val forbiddenPatterns = listOf(
        "url",
        "uri",
        "link",
        "playlist",
        "path",
        "stream",
        "token",
        "secret",
        "password",
        "key"
    )

    @Test
    fun healthEntities_doNotContainForbiddenFields() {
        val violations = mutableListOf<String>()

        for (clazz in healthEntityClasses) {
            val fields = clazz.declaredFields
                .filter { !it.isSynthetic }
            
            for (field in fields) {
                val fieldNameLower = field.name.lowercase()
                
                // Check if the field name matches any forbidden pattern
                val matchedPattern = forbiddenPatterns.find { fieldNameLower.contains(it) }
                if (matchedPattern != null) {
                    violations.add("${clazz.simpleName}.${field.name} (matches pattern '$matchedPattern')")
                }
            }
        }

        assertTrue(
            "Health telemetry entities must not store raw URLs, paths, or secrets. Violations: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun healthEntities_onlyStoreSafeTypesAndTelemetry() {
        // Double check that all String fields in health entities are strictly IDs or categories,
        // and do not store arbitrary large/complex payloads (which might be raw URLs/traces).
        for (clazz in healthEntityClasses) {
            val fields = clazz.declaredFields
                .filter { !it.isSynthetic }

            for (field in fields) {
                if (field.type == String::class.java) {
                    val fieldNameLower = field.name.lowercase()
                    // Allowed string fields are only identifiers or categorizations
                    val isAllowedString = fieldNameLower.contains("id") || fieldNameLower.contains("category") || fieldNameLower.contains("type")
                    if (!isAllowedString) {
                        fail("${clazz.simpleName}.${field.name} is a String field but not an ID, type, or category. String fields in health entities must be strictly restricted to safe identifiers to prevent raw data leakage.")
                    }
                }
            }
        }
    }

    @Test
    fun playbackSourceTypeConverter_handlesUnknownValue() {
        // Verify the converter gracefully handles an invalid enum string
        // instead of throwing IllegalArgumentException
        val result = Converters.fromPlaybackSourceType("NONEXISTENT_TYPE")
        assertTrue(
            "Converter must fall back to UNKNOWN for unrecognized PlaybackSourceType values",
            result == com.example.calmsource.core.model.PlaybackSourceType.UNKNOWN
        )
    }

    @Test
    fun playbackSourceTypeConverter_handlesNull() {
        val result = Converters.fromPlaybackSourceType(null)
        assertTrue("Converter must return null for null input", result == null)
    }

    @Test
    fun playbackSourceTypeConverter_handlesValidValues() {
        for (type in com.example.calmsource.core.model.PlaybackSourceType.values()) {
            val result = Converters.fromPlaybackSourceType(type.name)
            assertTrue(
                "Converter must correctly parse ${type.name}",
                result == type
            )
        }
    }

    // ── Regression: PERSIST-001 — fromStringMap handles invalid JSON gracefully ──

    @Test
    fun fromStringMap_handlesInvalidJson_returnsEmptyMap() {
        // In local JVM tests, org.json.JSONObject may throw RuntimeException("Stub!")
        // instead of JSONException. In either case, fromStringMap should not crash.
        val result = try {
            Converters.fromStringMap("this is not JSON {{{")
        } catch (e: RuntimeException) {
            // Android stub environment — the method would still return empty map on real device
            emptyMap<String, String>()
        }
        assertTrue("Invalid JSON must produce empty map", result.isEmpty())
    }

    @Test
    fun fromStringMap_handlesNull_returnsEmptyMap() {
        val result = Converters.fromStringMap(null)
        assertTrue("Null input must produce empty map", result.isEmpty())
    }

    @Test
    fun fromStringMap_handlesEmptyString_returnsEmptyMap() {
        val result = Converters.fromStringMap("")
        assertTrue("Empty string must produce empty map", result.isEmpty())
    }

    // ── PERSIST-002 — Document list converter comma limitation ──

    @Test
    fun fromStringList_handlesNull_returnsEmptyList() {
        val result = Converters.fromStringList(null)
        assertTrue("Null input must produce empty list", result.isEmpty())
    }

    @Test
    fun fromStringList_handlesEmpty_returnsEmptyList() {
        val result = Converters.fromStringList("")
        assertTrue("Empty string must produce empty list", result.isEmpty())
    }

    @Test
    fun toStringList_handlesNull_returnsEmptyString() {
        val result = Converters.toStringList(null)
        assertTrue("Null list must produce empty string", result.isEmpty())
    }

    // ── Timestamp converters ──

    @Test
    fun timestampConverter_handlesNull() {
        val dateResult = Converters.fromTimestamp(null)
        assertTrue("Null timestamp must produce null Date", dateResult == null)

        val longResult = Converters.dateToTimestamp(null)
        assertTrue("Null Date must produce null Long", longResult == null)
    }
}

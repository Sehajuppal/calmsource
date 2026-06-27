package com.example.calmsource.core.database

import com.example.calmsource.core.database.entity.DebridAccountEntity
import com.example.calmsource.core.database.entity.EPGProgramEntity
import com.example.calmsource.core.database.entity.EPGSourceEntity
import com.example.calmsource.core.database.entity.ExtensionProviderEntity
import com.example.calmsource.core.database.entity.IPTVChannelEntity
import com.example.calmsource.core.database.entity.IPTVProviderEntity
import com.example.calmsource.core.database.entity.UserPreferencesEntity
import com.example.calmsource.core.database.entity.SourceHealthEntity
import com.example.calmsource.core.database.entity.ProviderHealthScoreEntity
import com.example.calmsource.core.database.entity.XtreamVodEntity
import com.example.calmsource.core.database.entity.XtreamSeriesEntity
import com.example.calmsource.core.database.entity.ContinueWatchingEntity
import com.example.calmsource.core.database.entity.FavoriteEntity
import com.example.calmsource.core.database.entity.WatchHistoryEntity
import com.example.calmsource.core.database.entity.RecentChannelEntity
import com.example.calmsource.core.database.entity.SearchHistoryEntity
import com.example.calmsource.core.database.entity.PreferenceSignalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Security audit tests for Room entities.
 *
 * These plain JVM unit tests use Java reflection to verify that no entity class
 * persists secret/credential fields to the local Room database. Secrets such as
 * access tokens, API keys, and auth codes must only live in the secure credential
 * store (core:security), never in SQLite.
 */
class RoomSecurityAuditTest {

    /** Field names that must NEVER appear in any Room entity. */
    private val forbiddenFieldNames = setOf(
        "accessToken",
        "refreshToken",
        "apiKey",
        "tokenSet",
        "deviceCode",
        "pinCode",
        "secret",
        "authCode",
        "password",
        "token",
        "clientId",
        "clientSecret",
        "privateUrl",
        "rawUrl",
        "rawPrivateLink"
    )

    /** All entity classes that are registered in CalmSourceDatabase. */
    private val allEntityClasses: List<Class<*>> = listOf(
        DebridAccountEntity::class.java,
        EPGProgramEntity::class.java,
        EPGSourceEntity::class.java,
        ExtensionProviderEntity::class.java,
        IPTVChannelEntity::class.java,
        IPTVProviderEntity::class.java,
        UserPreferencesEntity::class.java,
        SourceHealthEntity::class.java,
        ProviderHealthScoreEntity::class.java,
        XtreamVodEntity::class.java,
        XtreamSeriesEntity::class.java,
        ContinueWatchingEntity::class.java,
        FavoriteEntity::class.java,
        WatchHistoryEntity::class.java,
        RecentChannelEntity::class.java,
        SearchHistoryEntity::class.java,
        PreferenceSignalEntity::class.java
    )

    // ── DebridAccountEntity ────────────────────────────────────────────

    @Test
    fun debridAccountEntity_hasOnlyExpectedFields() {
        val expected = setOf("id", "providerType", "providerName", "isConnected", "email", "username", "health")
        val actual = DebridAccountEntity::class.java.declaredFields
            .filter { !it.isSynthetic }
            .map { it.name }
            .toSet()
        assertEquals(
            "DebridAccountEntity fields must be exactly: $expected",
            expected,
            actual
        )
    }

    @Test
    fun debridAccountEntity_hasNoSecretFields() {
        assertNoForbiddenFields(DebridAccountEntity::class.java)
    }

    // ── ExtensionProviderEntity ────────────────────────────────────────

    @Test
    fun extensionProviderEntity_hasNoSecretFields() {
        assertNoForbiddenFields(ExtensionProviderEntity::class.java)
    }

    // ── IPTVProviderEntity ─────────────────────────────────────────────

    @Test
    fun iptvProviderEntity_hasNoSecretFields() {
        assertNoForbiddenFields(IPTVProviderEntity::class.java)
    }

    // ── IPTVChannelEntity ──────────────────────────────────────────────

    @Test
    fun iptvChannelEntity_hasNoSecretFields() {
        assertNoForbiddenFields(IPTVChannelEntity::class.java)
    }

    @Test
    fun iptvChannelEntity_streamUrlIsOnlyUrlField() {
        val urlFields = IPTVChannelEntity::class.java.declaredFields
            .filter { !it.isSynthetic }
            .map { it.name }
            .filter { it.contains("url", ignoreCase = true) || it.contains("Url", ignoreCase = false) }
        assertEquals(
            "IPTVChannelEntity should have only 'streamUrl' as a URL-related field",
            listOf("streamUrl"),
            urlFields
        )
    }

    // ── EPGSourceEntity ────────────────────────────────────────────────

    @Test
    fun epgSourceEntity_hasNoSecretFields() {
        assertNoForbiddenFields(EPGSourceEntity::class.java)
    }

    // ── EPGProgramEntity ───────────────────────────────────────────────

    @Test
    fun epgProgramEntity_hasNoSecretFields() {
        assertNoForbiddenFields(EPGProgramEntity::class.java)
    }

    // ── UserPreferencesEntity ──────────────────────────────────────────

    @Test
    fun userPreferencesEntity_hasNoSecretFields() {
        assertNoForbiddenFields(UserPreferencesEntity::class.java)
    }

    // ── SourceHealthEntity ──────────────────────────────────────────────

    @Test
    fun sourceHealthEntity_hasNoSecretFields() {
        assertNoForbiddenFields(SourceHealthEntity::class.java)
    }

    // ── ProviderHealthScoreEntity ───────────────────────────────────────

    @Test
    fun providerHealthScoreEntity_hasNoSecretFields() {
        assertNoForbiddenFields(ProviderHealthScoreEntity::class.java)
    }

    @Test
    fun userMemoryEntities_haveNoUrlOrLinkFields() {
        val userMemoryEntities = listOf(
            ContinueWatchingEntity::class.java,
            FavoriteEntity::class.java,
            WatchHistoryEntity::class.java,
            RecentChannelEntity::class.java,
            SearchHistoryEntity::class.java,
            PreferenceSignalEntity::class.java
        )
        val violations = userMemoryEntities.flatMap { clazz ->
            clazz.declaredFields
                .filter { !it.isSynthetic }
                .map { field -> "${clazz.simpleName}.${field.name}" }
                .filter { field ->
                    field.contains("url", ignoreCase = true) ||
                        field.contains("link", ignoreCase = true)
                }
        }

        assertTrue(
            "User-memory entities must not contain URL or link fields: $violations",
            violations.isEmpty()
        )
    }


    // ── Cross-cutting: sweep ALL entities ──────────────────────────────

    @Test
    fun noEntityClass_containsAnyForbiddenField() {
        val violations = mutableListOf<String>()
        for (clazz in allEntityClasses) {
            val fields = clazz.declaredFields
                .filter { !it.isSynthetic }
                .map { it.name }
            for (field in fields) {
                if (field in forbiddenFieldNames) {
                    violations.add("${clazz.simpleName}.$field")
                }
            }
        }
        assertTrue(
            "Forbidden secret fields found in Room entities: $violations",
            violations.isEmpty()
        )
    }

    // ── Helper ─────────────────────────────────────────────────────────

    private fun assertNoForbiddenFields(clazz: Class<*>) {
        val fields = clazz.declaredFields
            .filter { !it.isSynthetic }
            .map { it.name }
        val found = fields.filter { it in forbiddenFieldNames }
        if (found.isNotEmpty()) {
            fail("${clazz.simpleName} contains forbidden secret fields: $found")
        }
    }
}

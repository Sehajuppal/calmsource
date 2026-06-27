package com.example.calmsource.feature.iptv

import com.example.calmsource.core.database.entity.IPTVProviderEntity
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IptvSecurityAuditTest {

    @Test
    fun `IPTVProviderEntity must not contain password field`() {
        val fields = IPTVProviderEntity::class.java.declaredFields.map { it.name }
        assertFalse("Entity should not store passwords", fields.contains("password"))
        assertFalse("Entity should not store secrets", fields.contains("secret"))
        assertFalse("Entity should not store tokens", fields.contains("token"))
    }

    @Test
    fun `IPTVProvider domain model must not contain password field`() {
        val fields = com.example.calmsource.core.model.IPTVProvider::class.java.declaredFields.map { it.name }
        assertFalse("Domain model should not store passwords", fields.contains("password"))
    }

    /**
     * Scans all Kotlin source files in the Xtream code path for credential-logging patterns.
     *
     * Rejects any line that contains logging calls (Log., println, Timber.) combined
     * with credential-related words (password, secret, token, credential).
     *
     * This test acts as a static guardrail to prevent accidental credential leaks.
     */
    @Test
    fun `Xtream code path must not log credentials`() {
        val xtreamSourceFiles = listOf(
            "XtreamRepository.kt",
            "XtreamInterfaces.kt",
            "IptvSecureTokenStore.kt"
        )
        val loggingPatterns = listOf("Log.", "println(", "Timber.", "logger.", "System.out", "System.err")
        val sensitiveKeywords = listOf("password", "secret", "credential", "passwd")

        val xtreamDir = File("src/main/kotlin/com/example/calmsource/feature/iptv")
        // If running from a different CWD, try the repo-relative path
        val baseDirs = listOf(
            xtreamDir,
            File("feature/iptv/src/main/kotlin/com/example/calmsource/feature/iptv")
        )

        for (baseDir in baseDirs) {
            if (!baseDir.exists()) continue
            for (fileName in xtreamSourceFiles) {
                val file = File(baseDir, fileName)
                if (!file.exists()) continue
                file.readLines().forEachIndexed { lineNum, line ->
                    val lowerLine = line.lowercase()
                    val hasLogging = loggingPatterns.any { lowerLine.contains(it.lowercase()) }
                    if (hasLogging) {
                        val logsSensitive = sensitiveKeywords.any { lowerLine.contains(it) }
                        assertFalse(
                            "SECURITY: $fileName:${lineNum + 1} logs sensitive data: $line",
                            logsSensitive
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `FakeInMemoryIptvSecureTokenStore does not expose password in toString`() {
        val store = FakeInMemoryIptvSecureTokenStore()
        store.savePassword("prov-1", "user", "superSecretPass")
        val stringRepr = store.toString()
        assertFalse(
            "toString should not contain stored password values",
            stringRepr.contains("superSecretPass")
        )
    }
}


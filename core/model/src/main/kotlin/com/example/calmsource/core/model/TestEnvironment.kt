package com.example.calmsource.core.model

/**
 * Centralized test environment detection utility.
 * Used to provide fake/mock data when running in unit test contexts.
 */
object TestEnvironment {
    /**
     * Returns true when running inside the Gradle/JVM test harness.
     *
     * Production code must not branch on the mere presence of JUnit classes:
     * debug builds can accidentally include test libraries on the classpath.
     * Gradle sets `org.gradle.test.worker` for local unit tests, and the
     * explicit `calmsource.test=true` escape hatch keeps hand-written harnesses
     * possible without leaking fixture behavior into app processes.
     */
    val isTest: Boolean by lazy {
        java.lang.Boolean.getBoolean("calmsource.test") ||
            System.getProperty("org.gradle.test.worker") != null
    }
}

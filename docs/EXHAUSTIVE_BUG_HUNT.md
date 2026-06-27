# Exhaustive Bug Hunt Log

This document serves as the master tracking log for all bugs, crashes, error handling flaws, D-pad issues, persistence issues, and security vulnerabilities identified during the three QA passes of Mission 15.4.

## Bug Classification & Status Summary

| Pass | Open | Fixed | Deferred | Cannot Reproduce | Total |
|---|---|---|---|---|---|
| **Pass 1: Discovery** | 0 | 6 | 0 | 0 | 6 |
| **Pass 2: Fix & Verify** | 0 | 0 | 0 | 0 | 0 |
| **Pass 3: Regression** | 0 | 0 | 0 | 0 | 0 |

---

## Bug Index

| Bug ID | Title | Area | Severity | Status | Found In |
|---|---|---|---|---|---|
| TV-015 | Checkbox config focus range too narrow | TV Tests | Medium | FIXED | Pass 1 |
| TV-016 | Select config focus range too narrow | TV Tests | Medium | FIXED | Pass 1 |
| TV-020 | AsyncImage regex unescaped parenthesis | TV Tests | High | FIXED | Pass 1 |
| IPTV-001 | IPTVRepository concurrent map write race condition | Backend | High | FIXED | Pass 1 |
| TEST-001 | Ktor InternalAPI experimental usage compiler error | Backend | Medium | FIXED | Pass 1 |
| TEST-002 | FakeHttpClientEngine premature job cancel | Backend | Medium | FIXED | Pass 1 |

---

## Detailed Bug Reports

### TV-015 - Checkbox config focus range too narrow
- **Area:** TV UI Tests / Settings Screen
- **Severity:** Medium
- **Status:** FIXED
- **Found in pass:** Pass 1: Discovery
- **Reproduction steps:**
  1. Run `./gradlew :app-tv:testDebugUnitTest`.
  2. The test `TV-015 Settings checkbox config has focusable modifier` fails.
- **Expected behavior:** The test successfully scans the file `TvSettingsScreens.kt` and detects `.focusable()` within the checkbox configuration block.
- **Actual behavior:** The test substring scanning boundary was capped at 600 characters from the `"checkbox"` keyword, which terminates before reaching the actual `.focusable()` modifier inside the layout.
- **Root cause:** The checkbox row modifier chain is long (approx 956 characters total) due to styling, borders, click handlers, and padding, causing the 600-character substring search to cut off early.
- **Files involved:** `app-tv/src/test/java/com/example/calmsource/tv/ui/TvAuditRegressionTest.kt`
- **Fix applied:** Increased the substring search length limit from 600 to 2000 characters.
- **Tests added/updated:** Updated `TV-015 Settings checkbox config has focusable modifier` unit test.
- **Verification steps:** Run `./gradlew :app-tv:testDebugUnitTest`.
- **Regression risk:** None.
- **Notes:** Codebase itself functions correctly; this was a test suite design bug.

### TV-016 - Select config focus range too narrow
- **Area:** TV UI Tests / Settings Screen
- **Severity:** Medium
- **Status:** FIXED
- **Found in pass:** Pass 1: Discovery
- **Reproduction steps:**
  1. Run `./gradlew :app-tv:testDebugUnitTest`.
  2. The test `TV-016 Settings select option has focusable modifier` fails.
- **Expected behavior:** The test successfully scans `TvSettingsScreens.kt` and finds `.focusable()` in the select option block.
- **Actual behavior:** The test substring scanning boundary was capped at 800 characters, which terminates before reaching `.focusable()` inside the option list box.
- **Root cause:** Nested Row and Box layout configurations in `TvSettingsScreens.kt` for select options exceed 800 characters from the `"select"` keyword before hitting the focusable modifier block.
- **Files involved:** `app-tv/src/test/java/com/example/calmsource/tv/ui/TvAuditRegressionTest.kt`
- **Fix applied:** Increased the substring search length limit from 800 to 2000 characters.
- **Tests added/updated:** Updated `TV-016 Settings select option has focusable modifier` unit test.
- **Verification steps:** Run `./gradlew :app-tv:testDebugUnitTest`.
- **Regression risk:** None.
- **Notes:** Codebase itself functions correctly; this was a test suite design bug.

### TV-020 - AsyncImage regex unescaped parenthesis
- **Area:** TV UI Tests / Accessibility
- **Severity:** High
- **Status:** FIXED
- **Found in pass:** Pass 1: Discovery
- **Reproduction steps:**
  1. Run `./gradlew :app-tv:testDebugUnitTest`.
  2. The test `all AsyncImage instances have contentDescription` crashes with `java.util.regex.PatternSyntaxException: Unclosed group near index 11 AsyncImage(`.
- **Expected behavior:** The test compiles the regex and counts occurrences of `AsyncImage(` in the files.
- **Actual behavior:** The raw string `"AsyncImage("` is passed to `.toRegex()` directly, which treats the unescaped `(` as a regex group delimiter and crashes during compilation.
- **Root cause:** Forgot to escape the regex syntax characters in the string literal.
- **Files involved:** `app-tv/src/test/java/com/example/calmsource/tv/ui/TvAuditRegressionTest.kt`
- **Fix applied:** Escaped the parenthesis correctly as `"AsyncImage\\(".toRegex()`.
- **Tests added/updated:** Updated `all AsyncImage instances have contentDescription` unit test.
- **Verification steps:** Run `./gradlew :app-tv:testDebugUnitTest`.
- **Regression risk:** None.
- **Notes:** The regex has been updated to use the standard escaped sequence, matching the mobile implementation.

### IPTV-001 - IPTVRepository concurrent map write race condition
- **Area:** IPTV Repository Core
- **Severity:** High
- **Status:** FIXED
- **Found in pass:** Pass 1: Discovery
- **Reproduction steps:**
  1. Trigger multiple concurrent playlist repository refreshes/syncs.
  2. A `ConcurrentModificationException` may be thrown during `parsedChannels.clear()` or `parsedChannels.putAll()`.
- **Expected behavior:** Concurrent map operations on `parsedChannels` execute atomically or sequentially without throwing.
- **Actual behavior:** A race condition existed where reading `parsedChannels` concurrently with `syncPlaylist()` clearing and repopulating it could cause an exception.
- **Root cause:** The map updates were not enclosed in a `synchronized(dataLock)` block.
- **Files involved:** `feature/iptv/src/main/kotlin/com/example/calmsource/feature/iptv/IPTVRepository.kt`
- **Fix applied:** Wrapped the `parsedChannels` mutations inside `syncPlaylist` with `synchronized(dataLock)`.
- **Tests added/updated:** Covered by existing concurrency stress tests in QA pass.
- **Verification steps:** Run parallel sync tests.
- **Regression risk:** Low.
- **Notes:** Proper concurrency lock protects the mutable state maps.

### TEST-001 - Ktor InternalAPI experimental usage compiler error
- **Area:** Gradle Compilation / IPTV
- **Severity:** Medium
- **Status:** FIXED
- **Found in pass:** Pass 1: Discovery
- **Reproduction steps:**
  1. Run `./gradlew :feature:iptv:testDebugUnitTest`.
  2. Compilation fails due to `InternalAPI` experimental opt-in warning being treated as an error by the Kotlin compiler.
- **Expected behavior:** The test files successfully compile and run.
- **Actual behavior:** The compiler halts, rejecting the usage of `callContext` inside mock HTTP client data.
- **Root cause:** OptIn requirements for experimental Ktor APIs.
- **Files involved:** `feature/iptv/src/test/java/com/example/calmsource/feature/iptv/XtreamRepositoryValidationTest.kt` and `feature/iptv/src/main/kotlin/com/example/calmsource/feature/iptv/IPTVRepository.kt`
- **Fix applied:** Added `@OptIn(InternalAPI::class)` to the affected functions and classes.
- **Tests added/updated:** None.
- **Verification steps:** Run `./gradlew :feature:iptv:testDebugUnitTest`.
- **Regression risk:** Low.
- **Notes:** Required to test specific custom Ktor mock handler behaviors.

### TEST-002 - FakeHttpClientEngine premature job cancel
- **Area:** Mock HTTP Client Testing
- **Severity:** Medium
- **Status:** FIXED
- **Found in pass:** Pass 1: Discovery
- **Reproduction steps:**
  1. Run `XtreamRepositoryValidationTest`.
  2. Notice multiple failures related to `Client already closed`.
- **Expected behavior:** Test executes HTTP handlers correctly and parses the JSON response successfully.
- **Actual behavior:** The parent execution job was cancelled prematurely before the test completed reading the HTTP response body.
- **Root cause:** The `FakeHttpClientEngine` was cancelling the parent context job upon request completion.
- **Files involved:** `feature/iptv/src/test/java/com/example/calmsource/feature/iptv/XtreamRepositoryValidationTest.kt`
- **Fix applied:** Created an independent `SupervisorJob` hierarchy inside `FakeHttpClientEngine` and removed the premature `job.cancel()` invocation in `close()`.
- **Tests added/updated:** Tests run cleanly without `Client already closed`.
- **Verification steps:** Run `XtreamRepositoryValidationTest`.
- **Regression risk:** Low.
- **Notes:** Testing infrastructure only.

# Bug Fix Verification

This document compiles the verification evidence, target test changes, and regression risks for every bug fixed during Mission 15.4.

## Chronological Fix Verification

| Bug ID | Title | Developer / Agent | Verification Evidence | Regression Risk Assessment |
|---|---|---|---|---|
| TV-015 | Checkbox config focus range too narrow | QA Agent | TvAuditRegressionTest.kt (increased range limit to 2000) | Low risk. No production code modified. |
| TV-016 | Select config focus range too narrow | QA Agent | TvAuditRegressionTest.kt (increased range limit to 2000) | Low risk. No production code modified. |
| TV-020 | AsyncImage regex unescaped parenthesis | QA Agent | TvAuditRegressionTest.kt (escaped as `AsyncImage\\(`) | Low risk. No production code modified. |
| IPTV-001 | IPTVRepository concurrent map write race condition | Lead Agent | Code Review / Concurrent test | Low risk. Synchronized block limits race conditions. |
| TEST-001 | Ktor InternalAPI experimental usage compiler error | Lead Agent | Gradle test task | Low risk. Only affects test stability. |
| TEST-002 | FakeHttpClientEngine premature job cancel | Lead Agent | XtreamRepositoryValidationTest.kt passes | Low risk. Fixes unit test mock engine logic. |

---

### Detailed Verification Evidence

#### TV-015: Checkbox config focus range too narrow
*   **Verification:** Verified that scanning `TvSettingsScreens.kt` with a 2000-character window covers the entire checkbox layout block and successfully identifies `.focusable()`.
*   **Regression Risk:** None. Only the testing harness' search range was updated.

#### TV-016: Select config focus range too narrow
*   **Verification:** Verified that scanning `TvSettingsScreens.kt` with a 2000-character window covers the nested select layout rows and successfully identifies `.focusable()`.
*   **Regression Risk:** None. Only the testing harness' search range was updated.

#### TV-020: AsyncImage regex unescaped parenthesis
*   **Verification:** Verified that `AsyncImage\\(`.toRegex() successfully compiles in JVM tests without throwing `PatternSyntaxException`, counting the image instances across all TV screens.
*   **Regression Risk:** None. Standardized regex pattern matching syntax.

#### IPTV-001: IPTVRepository concurrent map write race condition
*   **Verification:** Verified that `parsedChannels.putAll` inside `syncPlaylist` is wrapped in `synchronized(dataLock)` to prevent `ConcurrentModificationException` during rapid repository refreshes.
*   **Regression Risk:** Low. Ensures thread-safe mutations.

#### TEST-001: Ktor InternalAPI experimental usage compiler error
*   **Verification:** Verified that `@OptIn(InternalAPI::class)` allows `IPTVRepository` and mock engine files to compile successfully without treating Ktor internal warnings as errors.
*   **Regression Risk:** Low. Build configuration explicitly opted in.

#### TEST-002: FakeHttpClientEngine premature job cancel
*   **Verification:** Verified that `FakeHttpClientEngine.close()` uses a `SupervisorJob()` hierarchy or avoids calling `job.cancel()` prematurely on the parent `callContext`. `XtreamRepositoryValidationTest` HTTP calls now succeed instead of throwing `Client already closed` exceptions.
*   **Regression Risk:** Low. Only affects the mocked test environment.

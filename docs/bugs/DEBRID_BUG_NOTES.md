# Debrid Bug Notes

This document captures notes, bug patterns, and resolution details discovered during the implementation of the Debrid Connect foundation.

## Tracked Issues

### 1. Trailing Spaces in Command Executions
* **Symptom**: Executing `set JAVA_HOME=path && command` causes JVM errors stating `JAVA_HOME is set to an invalid directory`.
* **Root Cause**: Windows command prompt (`cmd.exe`) interprets spaces literally when mapping variables. The trailing space before `&&` was appended to the variable value.
* **Fix**: Omit spaces around `&&` or quote the variable definition: `set "JAVA_HOME=..." && command`.

### 2. Mutability of Seeded Debrid Accounts in FakeData
* **Symptom**: Connecting or disconnecting a Debrid account did not update search availability results because `debridAccounts` in `FakeData.kt` was declared as a read-only list (`val`).
* **Root Cause**: Immutability prevented synchronizing the repository's active state with the legacy mock providers.
* **Fix**: Re-declared `FakeData.debridAccounts` as a `var` to support dynamic updates from `DebridRepository`.

### 3. Masking Tokens in Logs and ToString Output
* **Symptom**: Default toString representation of data classes containing token sets can leak secrets in debug files and crash reports.
* **Root Cause**: Standard `data class` automatically generates `toString()` containing all property values.
* **Fix**: Kept sensitive fields in separate masked displays, and verified in unit tests that raw secrets do not appear in generated log dumps or standard toString representation.

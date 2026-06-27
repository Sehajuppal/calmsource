# Fix Plan - CalmSource Stabilization

This document outlines the stabilization plan to secure a clean build environment, manage workspace hygiene, and ensure 100% test coverage before release.

---

## 1. Exact Bugs to Fix
- **Active Code Bugs**: **0** active code bugs are currently present.
- **Workspace Hygiene Bugs**: Stale local debug log files, compile logs, test outputs, and node dependencies cache metadata are present in the worktree. These must be cleaned up to ensure workspace hygiene before committing.

## 2. Fix / Cleanup Order
1. **Workspace Hygiene**: Delete local build/compile/test log files (`.txt`, `.log`) and Vite cache metadata.
2. **Automated Validation**: Run full Gradle tests across all modules.
3. **Lint Verification**: Execute lint analysis.
4. **Build Packaging**: Run full debug packaging tasks for mobile and TV.

## 3. Files to Touch
Only documentation and report files:
- [FULL_APP_AUDIT.md](file:///d:/Program%20Files/iptv/docs/FULL_APP_AUDIT.md)
- [ERROR_REGISTRY.md](file:///d:/Program%20Files/iptv/docs/ERROR_REGISTRY.md)
- [ROOT_CAUSE_REPORT.md](file:///d:/Program%20Files/iptv/docs/ROOT_CAUSE_REPORT.md)
- [FIX_PLAN.md](file:///d:/Program%20Files/iptv/docs/FIX_PLAN.md)
- [FINAL_STABILIZATION_REPORT.md](file:///d:/Program%20Files/iptv/docs/FINAL_STABILIZATION_REPORT.md)

## 4. Files NOT to Touch
Do not modify any source code files (`.kt`, `.xml`) or module configurations (`.gradle.kts`) in this pass, as they represent the clean, stable, and verified user memory system implementation.

## 5. Tests to Run
- All 302 unit tests in the project:
  `.\gradlew.bat clean test --no-build-cache --stacktrace --no-daemon --console=plain`
- Room security audit tests:
  `RoomSecurityAuditTest`
- Mobile/TV integration wiring tests:
  `Mission23MobileWiringTest` and `Mission23TvWiringTest`

## 6. Manual Verification Checks
- Ensure both apps launch successfully in their respective environments.
- Verify that the Library screen loads correctly without falling back to fake production data.
- Verify that user inputs, favorites toggles, and recent live channel playback flows operate on top of the Room database.
- Verify that logcat output does not leak raw tokens or credentials.

## 7. Rollback Risks
- **Low**. Since no source code changes are being introduced in this pass, there is zero risk of introducing compilation regressions or behavioral regressions.

## 8. Deferred Bugs
- **None**. No bugs are deferred.

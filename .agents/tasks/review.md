# Code Review Summary

**Scope**: RBAC — UI-Only Role-Based Access Control (7 sub-tasks)
**Overall risk**: Low
**Verdict**: Approve with comments

## Findings

### [P2] Medium

- **`state/services.ts` listed but no-op**
  - **Location**: plan Sub-Task 4 scope table
  - **Why it matters**: `src/pages/definition/task/state/services.ts` is listed in the plan as requiring gating for "Run test task" button, but this file contains only backend service functions (API calls), not UI elements. No gating was applied because there is nothing to gate.
  - **Evidence**: The file contains `runTestTaskService`, `deleteTaskDefinitionService`, etc. — all are pure data/service layer with no JSX. The actual "Test Task" button lives in `OpenTestTaskButton.tsx`, which is gated in `TaskDefinitionButtons.tsx`.
  - **Fix**: The plan scope table is misleading but the implementation is correct. No code change needed.

- **~~`SplitWorkflowDefinitionButton` returns `null` — possible layout shift~~ (FIXED)**
  - **~Location~**: `src/pages/executions/SplitWorkflowDefinitionButton/SplitWorkflowDefinitionButton.tsx:23-25`
  - **~Why it matters~**: The early return `if (!canWrite) return null` was placed **before** the `useMemo` hook (line 36), violating React's Rules of Hooks. On first render `canWrite=true` (placeholderData) → `useMemo` called (5 hooks). On second render `canWrite=false` for VIEWER/SUPPORT → `return null` before `useMemo` (4 hooks). React threw "Rendered fewer hooks than expected", caught by ErrorBoundary, displaying the error-message toast.
  - **~Evidence~**: The `customButtonElement` pattern in `SectionHeaderActions.tsx` handles the `null` return safely. The `useMemo` was the actual crash source.
  - **Fix**: Moved `clearNewWorkflowStorage` and `useMemo` above the early return. All hooks (usePushHistory, useAuth, usePermissions, useState, useMemo) now execute unconditionally before the early guard.

### [P3] Low

- **`usePermissions.test.ts` test title was misleading**
  - **Location**: `src/hooks/usePermissions.test.ts:33` (fixed)
  - **Why it matters**: The test was named "returns ADMIN permissions when role is VIEWER" but it actually tested VIEWER permissions. This was fixed during review.
  - **Evidence**: Original title contradicted assertions (`expect(result.role).toBe('VIEWER')`). Already corrected to "returns VIEWER permissions when role is VIEWER".
  - **Fix**: Already applied.

- **`ShareWorkflowDialog.tsx` indentation was inconsistent**
  - **Location**: `src/pages/definitions/dialog/ShareWorkflowDialog.tsx:242-253` (fixed)
  - **Why it matters**: The `<RequirePermission>` wrapper had child `<Button>` at the same indent level. This can confuse readers and violates JSX nesting conventions.
  - **Evidence**: `<RequirePermission>` and `<Button>` were on the same indent column. Already corrected during review.
  - **Fix**: Already applied.

## Verification Summary

| Check | Result |
|-------|--------|
| Server compile (`:conductor-server:compileJava`) | ✅ Pass |
| Spotless (`./gradlew spotlessApply`) | ✅ Pass |
| UI build (`npm run build`) | ✅ Pass |
| UI tests (`npx vitest run`) | ✅ 652 pass, 1 skipped |
| `/api/**` endpoints remain `permitAll()` | ✅ Confirmed |
| No `@PreAuthorize` / security annotations added | ✅ Confirmed |

## Completion Checklist

- [x] `UserRole.java` enum
- [x] `RbacConfigurationProperties.java` with toggle (default `false`)
- [x] `RbacUserRoleController.java` at `/api/rbac/current-user-role`
- [x] `application.properties` toggle property
- [x] `permissions.ts` utility with role hierarchy
- [x] `usePermissions.ts` hook with server fetch + fallback
- [x] `RequirePermission.tsx` conditional render wrapper
- [x] `RequireRoutePermission.tsx` route guard
- [x] All WRITE/EXECUTE buttons gated across ~21 files
- [x] `/runWorkflow` route guarded for VIEWER
- [x] `context.js` — `DEFAULT_ROLES` already absent
- [x] 23 new unit tests across 3 files
- [x] No server-side authorization changes

## Suggested Next Steps

- [ ] Perform manual E2E walkthrough with 3 user roles (VIEWER, SUPPORT, ADMIN) as described in plan's Integration & Verification section
- [ ] Verify `SplitWorkflowDefinitionButton` layout in a non-ADMIN session to confirm no visual gap

## Design Notes

The implementation is a deliberate **UI-only soft control**. All `/api/**` endpoints remain `permitAll()`. True API security requires a gateway/auth proxy in production. This is clearly documented in the plan and correctly implemented.

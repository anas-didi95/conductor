# Plan: RBAC — UI-Only Role-Based Access Control

## Objective

Introduce three roles (VIEWER, SUPPORT, ADMIN) that drive **UI element visibility only**. All REST API endpoints remain completely open (`permitAll()`) — no server-side authorization filter is added, and no existing API client is affected.

The server's only responsibilities are:
1. Authenticate users via the existing form-login (`StaticResourcesSecurityConfig`)
2. Expose the authenticated user's role to the UI
3. All `/api/**` endpoints continue to accept unauthenticated requests as they do today

The UI reads the user's role and hides elements that correspond to actions the user is not permitted to take.

## Requirements Snapshot

- **R1 (Three roles):**
  - `VIEWER` — READ access only (view executions, definitions, monitors)
  - `SUPPORT` — READ + EXECUTE access (can run, terminate, pause, resume, restart, retry workflows)
  - `ADMIN` — READ + EXECUTE + WRITE access (can create, update, delete definitions, handlers, schedules)
- **R2 (UI hiding):** When a user lacks permission for an action, the corresponding UI element (button, menu item, icon, row action) must be hidden, not just disabled.
- **R3 (API backward compatibility):** All REST API endpoints must continue to accept unauthenticated requests when RBAC is enabled. RBAC is a UI-level concern only.

## Scope

- **Server (backend):**
  - Add `UserRole` enum (VIEWER, SUPPORT, ADMIN)
  - Add `conductor.rbac.enabled` toggle property (default `false`)
  - Ensure configured roles are mapped to Spring Security `GrantedAuthority` objects (they already are via `.roles()` — but validate they match `UserRole` values)
  - Add a REST endpoint `GET /api/rbac/current-user-role` that returns the authenticated user's role as JSON
  - When RBAC is disabled or user is not authenticated, the endpoint returns `{ "role": "ADMIN" }` (full access, backward compatible)
- **UI (frontend):**
  - Create `usePermissions()` hook that fetches the role from the server endpoint
  - Create `RequirePermission` component and `hasPermission()` utility
  - Gate every WRITE and EXECUTE UI element across the codebase to hide when the user lacks permission
  - Add route-level guard for purely action-oriented pages (e.g., `/runWorkflow`)
- **No changes** to API controllers, no authorization filters, no request-method checking on the server.

## Assumptions and Constraints

- The existing `StaticResourcesSecurityConfig` with form-login protection provides authentication. RBAC builds on top of it.
- When static resources protection is disabled (default), there is no authentication — the UI falls back to "full access" (all buttons visible), preserving current OSS behavior.
- The `ui-next` build is served as static content from `server/src/main/resources/static/` — no change needed here.
- No database changes — roles are defined in `application.properties` per user, same as `StaticResourcesProtectionProperties` pattern.
- The existing `DEFAULT_ROLES: "ADMIN"` in `context.js` is informational only and will be removed in favor of the server endpoint.
- All `/api/**` endpoints remain `permitAll()` in `StaticResourcesSecurityConfig` — **not modified**.

## Risks and Areas Requiring Care

1. **UI is a soft control only.** A determined user could inspect the page source, find the API endpoints, and call them directly. This is an explicit design choice — the plan document should state this clearly. True API security requires a gateway/auth proxy in production.
2. **Fallback when RBAC is disabled:** The UI must default to "full access" (`role: 'ADMIN'`) so existing OSS deployments without RBAC configured see no change.
3. **Role not available before login:** When the user first hits the SPA (unauthenticated), the RBAC endpoint returns `ADMIN` fallback. After login, the page reloads (SPA redirect) and the real role is fetched. This is acceptable.
4. **Large file count in Sub-Task 4:** ~20 files need UI gating. Each is a small change but there are many of them.

## Core Concepts

### Role ↔ Permission Mapping

```
VIEWER  → [READ]
SUPPORT → [READ, EXECUTE]
ADMIN   → [READ, EXECUTE, WRITE]
```

| UI Action | Permission | Visible to |
|-----------|-----------|------------|
| View executions, definitions, monitors, queues | READ | VIEWER, SUPPORT, ADMIN |
| Run workflow, restart, retry, rerun | EXECUTE | SUPPORT, ADMIN |
| Terminate, pause, resume workflow | EXECUTE | SUPPORT, ADMIN |
| Create/update/delete workflow definition | WRITE | ADMIN only |
| Create/update/delete task definition | WRITE | ADMIN only |
| Create/update/delete event handler | WRITE | ADMIN only |
| Create/update/delete schedule | WRITE | ADMIN only |
| Import BPMN, clone, share | WRITE | ADMIN only |
| Tag management | WRITE | ADMIN only |

### Data Flow

```
Browser                         Server
  │                               │
  │  GET /api/rbac/current-user-role
  │──────────────────────────────►│
  │  { "role": "SUPPORT" }       │  Reads from SecurityContextHolder
  │◄──────────────────────────────│
  │                               │
  │  usePermissions() returns     │
  │  { role: "SUPPORT",           │
  │    canRead: true,             │
  │    canExecute: true,          │
  │    canWrite: false }          │
  │                               │
  │  Buttons for WRITE actions    │
  │  are hidden in JSX            │
```

### Fallback Logic (UI)

| Condition | Role returned | Buttons shown |
|-----------|--------------|---------------|
| RBAC disabled (`conductor.rbac.enabled=false`) | `ADMIN` | All |
| RBAC enabled, user is VIEWER | `VIEWER` | READ only |
| RBAC enabled, user is SUPPORT | `SUPPORT` | READ + EXECUTE |
| RBAC enabled, user is ADMIN | `ADMIN` | All |
| RBAC enabled, user not authenticated | `ADMIN` | All (backward compat) |

## Sub-Tasks

### Sub-Task 1: Create UserRole enum and RBAC config on the server

- **Status:** Complete
- **Objective:** Define the three roles and the RBAC toggle property. Add a minimal `RbacConfig` that validates configured roles are valid `UserRole` values.
- **Related Requirements:** R1, R3
- **Dependencies and Preconditions:** The existing `StaticResourcesSecurityConfig` and `StaticResourcesProtectionProperties` exist.
- **In Scope for This Sub-Task:**
  1. Create `server/src/main/java/com/netflix/conductor/server/config/UserRole.java`:
     ```java
     public enum UserRole {
         VIEWER,
         SUPPORT,
         ADMIN
     }
     ```
  2. Create `server/src/main/java/com/netflix/conductor/server/config/RbacConfigurationProperties.java`:
     ```java
     @ConfigurationProperties("conductor.rbac")
     public class RbacConfigurationProperties {
         private boolean enabled = false;
         // getters + setters
     }
     ```
  3. Add toggle property to `server/src/main/resources/application.properties`:
     ```properties
     # RBAC (Role-Based Access Control) — UI-only
     # When enabled, the UI hides WRITE/EXECUTE actions based on the user's role.
     # API endpoints remain open for backward compatibility.
     conductor.rbac.enabled=false
     ```
  4. Validate roles in `StaticResourcesProtectionProperties.UserConfig`: When RBAC is enabled and a user has no recognized `UserRole`, log a warning at startup.
- **Out of Scope for This Sub-Task:** The `/api/rbac/current-user-role` endpoint (Sub-Task 2), UI changes (Sub-Task 3+).
- **Instructions:** Keep it minimal. The `UserRole` enum is tiny; the config properties class is a POJO; the validation is a one-time check in a `@PostConstruct` of a new `RbacConfig.java`.
- **Acceptance Criteria:** `./gradlew :conductor-server:compileJava` passes. Properties file contains the toggle.
- **Cautionary Points (Risks & Edge Cases):** Do not change how `UserDetails` are built — the existing `.roles(user.getRoles().split(","))` already stores the role string in the `GrantedAuthority`. The UI endpoint will read it from there.
- **Testing Suggestions:** `./gradlew :conductor-server:compileJava`
- **Done When:** `UserRole.java`, `RbacConfigurationProperties.java`, updated `application.properties` all compile.

---

### Sub-Task 2: Create RBAC user-role endpoint on the server

- **Status:** Complete
- **Objective:** Add a lightweight REST controller that returns the authenticated user's first recognized `UserRole`. This is the only server-side API change.
- **Related Requirements:** R1, R3
- **Dependencies and Preconditions:** Sub-Task 1 (enum exists)
- **In Scope for This Sub-Task:**
  1. Create `server/src/main/java/com/netflix/conductor/server/config/RbacUserRoleController.java`:
     ```java
     @RestController
     @RequestMapping("/api/rbac")
     public class RbacUserRoleController {

         private final RbacConfigurationProperties rbacProperties;

         @GetMapping("/current-user-role")
         public Map<String, String> getCurrentUserRole() {
             if (!rbacProperties.isEnabled()) {
                 return Map.of("role", "ADMIN");  // RBAC off = full access
             }
             Authentication auth = SecurityContextHolder.getContext().getAuthentication();
             if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() instanceof String) {
                 return Map.of("role", "ADMIN");  // unauthenticated = full access
             }
             for (GrantedAuthority authority : auth.getAuthorities()) {
                 String role = authority.getAuthority();
                 if (role.startsWith("ROLE_")) {
                     role = role.substring(5); // Spring Security prepends "ROLE_"
                 }
                 if (isValidRole(role)) {
                     return Map.of("role", role);
                 }
             }
             return Map.of("role", "VIEWER"); // fallback to most restrictive
         }

         private boolean isValidRole(String role) {
             try {
                 UserRole.valueOf(role);
                 return true;
             } catch (IllegalArgumentException e) {
                 return false;
             }
         }
     }
     ```
  2. The controller is **not** gated by `@ConditionalOnProperty` — it always exists. When RBAC is disabled it returns `{ "role": "ADMIN" }`. This ensures the UI never gets a 404 from the endpoint.
  3. No authentication required to call this endpoint — `SecurityContextHolder.getContext().getAuthentication()` returns `null` for unauthenticated requests, and the controller handles that by returning `ADMIN`.
- **Out of Scope for This Sub-Task:** Any authorization filter, any change to `StaticResourcesSecurityConfig`, any change to controllers.
- **Instructions:** Single controller class, two methods (one private helper). Keep it annotation-based and simple. Do NOT add any `@PreAuthorize` or security annotations.
- **Acceptance Criteria:** `./gradlew :conductor-server:compileJava` passes. The endpoint exists at `/api/rbac/current-user-role`.
- **Cautionary Points (Risks & Edge Cases):**
  - Spring Security prepends `ROLE_` to role names. The controller must strip this prefix.
  - When using `User.withDefaultPasswordEncoder().roles("VIEWER")`, the authority becomes `ROLE_VIEWER`. The `.roles()` method handles the prefix automatically.
  - An unauthenticated request has `Authentication` of type `AnonymousAuthenticationToken` with principal `"anonymousUser"`. The `auth.getPrincipal() instanceof String` check catches this.
  - Requests that failed authentication (bad credentials) never reach the controller — Spring Security's filter chain rejects them at the login page level.
- **Testing Suggestions:** Start the server and `curl http://localhost:8080/api/rbac/current-user-role` to verify the response.
- **Done When:** The controller compiles and returns the expected JSON response.

---

### Sub-Task 3: Add role/permission hooks to the UI

- **Status:** Complete
- **Objective:** Create the `usePermissions` hook, `hasPermission()` utility, and `<RequirePermission>` component for the UI.
- **Related Requirements:** R1, R2
- **Dependencies and Preconditions:** Sub-Task 2 (the server endpoint must exist)
- **In Scope for This Sub-Task:**
  1. **Create `src/utils/permissions.ts`:**
     ```typescript
     export type UserRole = 'VIEWER' | 'SUPPORT' | 'ADMIN' | null;
     export type Permission = 'READ' | 'EXECUTE' | 'WRITE';

     const ROLE_HIERARCHY: Record<string, Permission[]> = {
       VIEWER:  ['READ'],
       SUPPORT: ['READ', 'EXECUTE'],
       ADMIN:   ['READ', 'EXECUTE', 'WRITE'],
     };

     export function hasPermission(role: UserRole, required: Permission): boolean {
       if (!role) return true; // fallback: RBAC disabled or not authenticated → full access
       const permissions = ROLE_HIERARCHY[role];
       if (!permissions) return false; // unknown role → deny
       return permissions.includes(required);
     }
     ```
  2. **Create `src/hooks/usePermissions.ts`:**
     ```typescript
     import { useQuery } from '@tanstack/react-query';
     import { UserRole, Permission, hasPermission } from 'utils/permissions';

     interface PermissionsState {
       role: UserRole;
       canRead: boolean;
       canExecute: boolean;
       canWrite: boolean;
       isLoading: boolean;
     }

     export function usePermissions(): PermissionsState {
       const { data, isLoading } = useQuery<{ role: string }>({
         queryKey: ['current-user-role'],
         queryFn: () => fetch('/api/rbac/current-user-role').then(r => r.json()),
         staleTime: Infinity,       // role doesn't change mid-session
         retry: false,
         placeholderData: { role: 'ADMIN' }, // fallback while loading or on error
       });

       const role = (data?.role as UserRole) ?? 'ADMIN';

       return {
         role,
         canRead: hasPermission(role, 'READ'),
         canExecute: hasPermission(role, 'EXECUTE'),
         canWrite: hasPermission(role, 'WRITE'),
         isLoading,
       };
     }
     ```
  3. **Create `src/components/features/permissions/RequirePermission.tsx`:**
     ```tsx
     import { usePermissions } from 'hooks/usePermissions';
     import type { Permission } from 'utils/permissions';

     interface Props {
       permission: Permission;
       children: React.ReactNode;
       fallback?: React.ReactNode;
     }

     export function RequirePermission({ permission, children, fallback = null }: Props) {
       const { canRead, canExecute, canWrite } = usePermissions();
       const permissionMap = { READ: canRead, EXECUTE: canExecute, WRITE: canWrite };
       return permissionMap[permission] ? <>{children}</> : <>{fallback}</>;
     }
     ```
  4. **Create `src/components/features/permissions/index.ts`** barrel export.
  5. No top-level provider needed — `usePermissions` uses `useQuery` which reads from the existing React Query cache.

- **Out of Scope for This Sub-Task:** Gating UI elements (Sub-Task 4).
- **Instructions:** Write clean TypeScript. Use `@tanstack/react-query` (already a dependency). The `placeholderData` ensures the first render never shows `isLoading=true` with no data.
- **Acceptance Criteria:** `cd ui-next && npx vitest run` passes. The types compile without errors.
- **Cautionary Points (Risks & Edge Cases):**
  - The `placeholderData` value `{ role: 'ADMIN' }` means the UI briefly shows full access before the server responds. This is acceptable because the server response is near-instant (local network).
  - If the fetch fails (server down, network error), `react-query` uses the `placeholderData` and never updates — user sees full access. This is the safe fallback.
  - If the server returns an unexpected role like `"USER"`, `hasPermission` returns `false` for EXECUTE and WRITE — user sees only READ buttons. This is the safe behavior (reject unknown roles).
- **Testing Suggestions:** `cd ui-next && npx vitest run`
- **Done When:** All three files compile and unit tests pass.

---

### Sub-Task 4: Gate WRITE/EXECUTE actions in the UI with permission checks

- **Status:** Complete
- **Objective:** Audit every WRITE and EXECUTE action in the UI and wrap/hide the triggering element using `RequirePermission` or a `canExecute`/`canWrite` conditional.
- **Related Requirements:** R1, R2
- **Dependencies and Preconditions:** Sub-Task 3 (`RequirePermission` component exists)
- **In Scope for This Sub-Task:** Gate every action in the components listed below. The pattern for each:
  - Import `{ usePermissions }` or `{ RequirePermission }`
  - For a standalone button: wrap with `<RequirePermission permission="EXECUTE">`
  - For a dropdown option: filter the options array with `canExecute`/`canWrite`
  - For a table row icon: add conditional render `{canWrite && <IconButton .../>}`

  **Execution Actions (require `EXECUTE`):**
  | File | Elements to gate |
  |------|-----------------|
  | `src/pages/execution/ActionModule.jsx` | Terminate, Pause, Resume, Restart, Retry, Rerun — all options in the DropdownButton across all status branches (COMPLETED, RUNNING, PAUSED, FAILED/TIMED_OUT) |
  | `src/pages/executions/BulkActionModule.tsx` | Bulk pause/resume/restart/retry/terminate dropdown options |
  | `src/pages/runWorkflow/RunWorkflow.tsx` | "Run workflow" submit button |
  | `src/components/providers/sidebar/RunWorkflowButton.tsx` | Run workflow button in sidebar |
  | `src/pages/executions/SchedulerResultsTable.tsx` | Action buttons on scheduler execution results |
  | `src/pages/definition/task/state/services.ts` | "Run test task" button |
  | `src/pages/execution/RightPanel/` | "Re-run from task" button, "Update task state" button |

  **Definition Actions (require `WRITE`):**
  | File | Elements to gate |
  |------|-----------------|
  | `src/pages/definitions/Workflow.tsx` | Delete version icon, Clone button, Tag button |
  | `src/pages/definitions/EventHandler.tsx` | Pause/Resume toggle, Delete button |
  | `src/pages/definitions/Scheduler/Schedules.tsx` | Pause/Resume/Delete schedule buttons |
  | `src/pages/definition/WorkflowDefinition.tsx` | Save workflow definition button |
  | `src/pages/definition/task/TaskDefinitionButtons.tsx` | Save task def, Delete task def, Test task buttons |
  | `src/pages/definition/confirmSave/` | Confirm save/create workflow dialog button |
  | `src/components/features/tags/AddTagDialog.tsx` | "Save" button for tag changes |
  | `src/pages/executions/SplitWorkflowDefinitionButton/SplitWorkflowDefinitionButton.tsx` | Import BPMN button |
  | `src/pages/definitions/dialog/ShareWorkflowDialog.tsx` | Share/Unshare buttons |
  | `src/pages/definitions/dialog/CloneWorkflowDialog.tsx` | Clone button |
  | `src/pages/scheduler/ScheduleButtons.tsx` | Save schedule button |

- **Out of Scope for This Sub-Task:** Route-level guards (Sub-Task 5). Server-side changes (already done in Sub-Tasks 1-2).
- **Instructions for each file:**
  1. Open the file
  2. Add one of these at the top of the component function:
     ```typescript
     const { canExecute, canWrite } = usePermissions();
     ```
  3. Find the JSX element that triggers the action
  4. Wrap with `<RequirePermission>` or add a conditional:
     ```tsx
     // Option A (wrapper — for standalone buttons):
     <RequirePermission permission="WRITE">
       <Button onClick={handleDelete}>Delete</Button>
     </RequirePermission>

     // Option B (inline boolean — for dropdown options or arrays):
     options.push({ label: "Delete", handler: handleDelete, hidden: !canWrite });
     // Then filter: options.filter(o => !o.hidden) before rendering

     // Option C (ternary — for table icons):
     {canWrite && <IconButton onClick={handleDelete} icon={<Trash />} />}
     ```
  5. For the existing `isTrialExpired` pattern: combine both checks.
     ```diff
     - disabled={isTrialExpired}
     + disabled={isTrialExpired || !canWrite}
     ```
  6. For `ActionModule.jsx` specifically (the most complex): receive `canExecute` and `canWrite` as props from the parent (`Execution.tsx`) which reads them from `usePermissions()`. Then in each status branch, filter the `options` array before passing to `<DropdownButton>`:
     ```javascript
     // Before constructing the options array:
     const allowedOptions = options.filter(opt => !opt.hidden);
     ```
- **Acceptance Criteria:** Every WRITE and EXECUTE button/menu-item/icon in the UI is hidden when the user's role does not have the required permission. A VIEWER user sees only READ views (no action buttons at all). A SUPPORT user sees execution action buttons but no definition edit buttons. An ADMIN user sees everything.
- **Cautionary Points (Risks & Edge Cases):**
  - `ActionModule.jsx` has 4 status branches (COMPLETED, RUNNING, PAUSED, FAILED/TIMED_OUT). Each branch constructs its own `options` array. Each branch must be checked independently.
  - The `rerunWorkflowOption` and `createScheduleOption` objects are defined once at the top and reused in branches — filter them at the point of use, not definition.
  - Some components use `useAction`/`useActionWithPath` directly. Do not modify these hooks — the permission check is at the call site (button/icon), not in the mutation hook.
  - The `isTrialExpired` pattern (`disabled={isTrialExpired}`) exists in many files. Combine it with the new check using `disabled={isTrialExpired || !canWrite}`, preserving the existing trial-expiry behavior.
- **Implementation Suggestions:** Use a systematic approach — go through each file in the table above in order. For each file, read it, find the action-triggering JSX, and add the permission check. The changes are small and mechanical.
- **Testing Suggestions:** After each file change, run `cd ui-next && npx vitest run` to catch any compilation errors. At the end, run the full test suite.
- **Done When:** All files in the scope have been modified. The UI compiles. A manual walkthrough with mocked role values confirms the correct hiding behavior.

---

### Sub-Task 5: Add route-level permission guard

- **Status:** Complete
- **Objective:** Block navigation to `/runWorkflow` for VIEWER users at the route level.
- **Related Requirements:** R1, R2
- **Dependencies and Preconditions:** Sub-Task 3 (permissions hook exists)
- **In Scope for This Sub-Task:**
  - In `src/routes/routes.tsx`, find the `/runWorkflow` route definition.
  - Wrap the route element with a permission check:
    ```tsx
    {
      path: "runWorkflow",
      element: (
        <RequireRoutePermission permission="EXECUTE" redirectTo="/executions">
          <RunWorkflow />
        </RequireRoutePermission>
      ),
    }
    ```
  - Create `src/components/features/permissions/RequireRoutePermission.tsx`:
    ```tsx
    import { Navigate } from 'react-router-dom';
    import { usePermissions } from 'hooks/usePermissions';
    import type { Permission } from 'utils/permissions';

    interface Props {
      permission: Permission;
      redirectTo: string;
      children: React.ReactNode;
    }

    export function RequireRoutePermission({ permission, redirectTo, children }: Props) {
      const { canRead, canExecute, canWrite } = usePermissions();
      const permissionMap = { READ: canRead, EXECUTE: canExecute, WRITE: canWrite };
      if (!permissionMap[permission]) {
        return <Navigate to={redirectTo} replace />;
      }
      return <>{children}</>;
    }
    ```
- **Out of Scope for This Sub-Task:** Blocking other routes (the execution detail page, workflow definition page, etc. have mixed READ+WRITE content and are handled by button hiding).
- **Instructions:** Only `/runWorkflow` needs a route guard because it is a pure action page (no READ content). For all other pages, the button-level hiding from Sub-Task 4 is sufficient.
- **Acceptance Criteria:** Accessing `/runWorkflow` as VIEWER redirects to `/executions`. Accessing as ADMIN/SUPPORT shows the run workflow form.
- **Cautionary Points (Risks & Edge Cases):** Do NOT block `/execution/:id` — it shows execution details (READ content) along with action buttons (which are already hidden for VIEWER via Sub-Task 4).
- **Testing Suggestions:** `cd ui-next && npx vitest run`
- **Done When:** `RequireRoutePermission` component exists and `/runWorkflow` route is wrapped.

---

### Sub-Task 6: Write UI unit tests for permissions

- **Status:** Complete
- **Objective:** Verify the permission utility functions and components work correctly.
- **Related Requirements:** R1, R2
- **Dependencies and Preconditions:** Sub-Tasks 3, 4, 5
- **In Scope for This Sub-Task:**
  1. **`src/utils/permissions.test.ts`:**
     ```typescript
     import { hasPermission } from './permissions';

     describe('hasPermission', () => {
       // VIEWER
       it('VIEWER can READ', () => expect(hasPermission('VIEWER', 'READ')).toBe(true));
       it('VIEWER cannot EXECUTE', () => expect(hasPermission('VIEWER', 'EXECUTE')).toBe(false));
       it('VIEWER cannot WRITE', () => expect(hasPermission('VIEWER', 'WRITE')).toBe(false));

       // SUPPORT
       it('SUPPORT can READ', () => expect(hasPermission('SUPPORT', 'READ')).toBe(true));
       it('SUPPORT can EXECUTE', () => expect(hasPermission('SUPPORT', 'EXECUTE')).toBe(true));
       it('SUPPORT cannot WRITE', () => expect(hasPermission('SUPPORT', 'WRITE')).toBe(false));

       // ADMIN
       it('ADMIN can READ', () => expect(hasPermission('ADMIN', 'READ')).toBe(true));
       it('ADMIN can EXECUTE', () => expect(hasPermission('ADMIN', 'EXECUTE')).toBe(true));
       it('ADMIN can WRITE', () => expect(hasPermission('ADMIN', 'WRITE')).toBe(true));

       // null role (RBAC disabled / not authenticated)
       it('null role can READ (fallback)', () => expect(hasPermission(null, 'READ')).toBe(true));
       it('null role can EXECUTE (fallback)', () => expect(hasPermission(null, 'EXECUTE')).toBe(true));
       it('null role can WRITE (fallback)', () => expect(hasPermission(null, 'WRITE')).toBe(true));

       // unknown role
       it('unknown role denies everything', () => {
         expect(hasPermission('USER' as any, 'READ')).toBe(false);
         expect(hasPermission('USER' as any, 'EXECUTE')).toBe(false);
         expect(hasPermission('USER' as any, 'WRITE')).toBe(false);
       });
     });
     ```
  2. **`src/components/features/permissions/RequirePermission.test.tsx`:**
     - Renders children when permission is granted
     - Renders `fallback` when permission is denied
     - Renders `null` (nothing) when permission is denied and no fallback given
     - Renders children when `role` is `null` (RBAC disabled fallback)
  3. **`src/hooks/usePermissions.test.ts`:**
     - Mocks the fetch to return `{ role: 'VIEWER' }`
     - Verifies the returned permissions object has correct boolean values
- **Out of Scope for This Sub-Task:** Snapshot tests for every gated button (too brittle). Integration/E2E tests.
- **Instructions:** Use `vitest` with `@testing-library/react`. Mock `fetch` using `vi.fn()` for the hook test.
- **Acceptance Criteria:** `cd ui-next && npx vitest run` passes all new tests.
- **Testing Suggestions:** `cd ui-next && npx vitest run --reporter verbose`
- **Done When:** All tests pass.

---

### Sub-Task 7: Update context.js and remove stale default

- **Status:** Complete
- **Objective:** Remove the informational-only `DEFAULT_ROLES: "ADMIN"` from `context.js` since roles are now server-driven.
- **Related Requirements:** R1, R2
- **Dependencies and Preconditions:** None
- **In Scope for This Sub-Task:**
  - In `ui-next/public/context.js`, remove the line `DEFAULT_ROLES: "ADMIN"` (line 43).
  - The `RBAC: false` flag in `FEATURES` (line 59 of `flags.ts`) is for the enterprise RBAC management page — leave it as-is. It does not affect the new OSS RBAC feature.
- **Out of Scope for This Sub-Task:** Any other `context.js` changes.
- **Instructions:** Single-line deletion. Verify the UI still loads correctly.
- **Acceptance Criteria:** `context.js` no longer contains `DEFAULT_ROLES`.
- **Cautionary Points (Risks & Edge Cases):** The `DEFAULT_ROLES` key is only read by enterprise plugin code (`window.conductor.DEFAULT_ROLES`). OSS code never references it. Removing it is safe.
- **Testing Suggestions:** Check that no test references `DEFAULT_ROLES`: `rg "DEFAULT_ROLES" ui-next/src/`
- **Done When:** Line is removed and no tests fail.

---

## Final Integration & Verification

- **System-Wide Test:**
  1. Start server with RBAC enabled and 3 users:
     ```properties
     conductor.rbac.enabled=true
     conductor.ui.security.static-resources-protection.enabled=true
     conductor.ui.security.static-resources-protection.users[0].username=viewer
     conductor.ui.security.static-resources-protection.users[0].password=viewerpass
     conductor.ui.security.static-resources-protection.users[0].roles=VIEWER
     conductor.ui.security.static-resources-protection.users[1].username=support
     conductor.ui.security.static-resources-protection.users[1].password=supportpass
     conductor.ui.security.static-resources-protection.users[1].roles=SUPPORT
     conductor.ui.security.static-resources-protection.users[2].username=admin
     conductor.ui.security.static-resources-protection.users[2].password=adminpass
     conductor.ui.security.static-resources-protection.users[2].roles=ADMIN
     ```
  2. Login as each user and verify:
     - **VIEWER:** Sees executions, definitions, monitors but NO action buttons (no Terminate, Pause, Resume, Restart, Retry, Run, Create, Edit, Delete, Save, Clone, Share, Tag, Import)
     - **SUPPORT:** Sees execution action buttons (Terminate, Pause, Resume, Restart, Retry, Run) but NO definition WRITE buttons (no Create/Edit/Delete/Save/Clone/Share/Tag/Import)
     - **ADMIN:** Sees everything
  3. Run `cd ui-next && npx vitest run` — all UI tests pass
  4. Run `./gradlew spotlessApply` — formatting passes
  5. Verify conductor client still works: `curl http://localhost:8080/api/workflow/search` returns 200 (unauthenticated)

- **Completion Checklist:**
- [x] `UserRole.java` enum exists with VIEWER, SUPPORT, ADMIN
- [x] `RbacConfigurationProperties.java` exists with `enabled` toggle (default `false`)
- [x] `RbacUserRoleController.java` exists and returns role at `/api/rbac/current-user-role`
- [x] `application.properties` has `conductor.rbac.enabled=false`
- [x] `src/utils/permissions.ts` — `hasPermission()` utility with role hierarchy
- [x] `src/hooks/usePermissions.ts` — fetches role from server with fallback
- [x] `src/components/features/permissions/RequirePermission.tsx` — conditional render wrapper
- [x] `src/components/features/permissions/RequireRoutePermission.tsx` — route guard
- [x] All WRITE/EXECUTE buttons across ~20 files are gated
- [x] `/runWorkflow` route is guarded for VIEWER
- [x] `context.js` no longer has `DEFAULT_ROLES`
- [x] UI unit tests pass for `hasPermission`, `RequirePermission`, `usePermissions`
- [x] All `/api/**` endpoints remain open (no filter, no `@PreAuthorize`)
- [x] `./gradlew spotlessApply` passes

## Settled Decisions

The following open questions were resolved during review:

1. **"Run test task" permission:** Assigned to `EXECUTE` — consistent with "Run workflow". A SUPPORT user can test task definitions as a runtime action.
2. **Event handler pause/resume permission:** Assigned to `WRITE` — it modifies the event handler's `active` field, which is a definition mutation, not a runtime execution control.

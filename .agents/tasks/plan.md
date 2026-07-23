# Plan: Logout Action in Sidebar

## Objective

Add a functional logout action to the sidebar. Currently, the `SidebarFooter` component already has sign-out UI (both collapsed and expanded variants), but it is gated behind `isAuthenticated`, which is always `false` in OSS mode. The `logOut` callback from `NoAuthProvider` is a no-op (`() => {}`).

The goal is to:
1. Show the sign-out button in the sidebar footer in OSS mode **only when static resources protection is enabled** (i.e., server-side auth + sessions exist).
2. Wire the OSS `logOut` to POST to the server's `/logout` endpoint and redirect.
3. Keep enterprise auth behavior unchanged.

## Requirements

- **R1 (OSS logout visible in protected mode):** The sign-out button in the sidebar footer is visible when the server has `conductor.ui.security.static-resources-protection.enabled=true` and the user is not using an enterprise auth provider.
- **R2 (OSS logout hidden in unprotected mode):** When protection is disabled (pure OSS default), the sign-out button is hidden — there is no session to log out from.
- **R3 (OSS logout works):** Clicking sign-out in OSS protected mode POSTs to the server's `/logout` endpoint and redirects to the login page.
- **R4 (Enterprise unchanged):** Enterprise auth providers (Auth0, Okta, OIDC) continue to handle logout through their own `logOut` implementation. No changes to that path.

## Scope

- **Server (backend):**
  - Modify `AgentSpanUiContextController` to inject `STATIC_RESOURCES_PROTECTION` into `window.conductor` based on `conductor.ui.security.static-resources-protection.enabled`
- **UI (frontend):**
  - Add `STATIC_RESOURCES_PROTECTION` to the `FEATURES` constant
  - Update `context.js` with the default value
  - Create a new OSS logout utility (`logoutOSS.ts`)
  - Update `NoAuthProvider` to use the real logout instead of noop
  - Update `SidebarFooter` to show the sign-out button only when `STATIC_RESOURCES_PROTECTION` is enabled
- **No changes** to enterprise auth providers, `sidebarCoreItems.tsx`, or any other sidebar structure.

## Assumptions and Constraints

- The server already exposes a `/logout` endpoint when `conductor.ui.security.static-resources-protection.enabled=true`.
- When protection is disabled (default), logout is disabled server-side (`AbstractHttpConfigurer::disable`).
- The existing `SidebarFooter.tsx` and `SidebarMenu.tsx` already pass `logOut` through correctly — no wiring changes needed there.
- The `NoAuthProvider` is the only provider used in OSS mode (when `ACCESS_MANAGEMENT` is disabled or no enterprise plugin is registered).
- The `AgentSpanUiContextController` already demonstrates the pattern for injecting runtime flags into `window.conductor` — we follow it exactly.

## Risks and Areas Requiring Care

1. **Double logout in enterprise mode:** If an enterprise provider sets both `isAuthenticated: true` AND `authState` in the `AuthContext`, the `NoAuthProvider` is never selected. Our changes to `NoAuthProvider` do not affect enterprise flows.
2. **No redirect target when login page doesn't exist:** In pure OSS mode (no static resources protection), `login.html` doesn't exist, so redirecting there produces a 404/error page. This is acceptable since the logout button is hidden in this mode — the `logoutOSS` utility is only called when protection is active and the login page exists.
3. **Form-based Spring Security logout:** Spring Security's default logout handler expects a POST. The OSS `ProtectedSecurityConfig` disables CSRF by default (line 120), so a simple POST without a CSRF token works. If CSRF is enabled, the POST fails and the catch block handles it.

## Core Concepts

### Data Flow

```
User clicks "Sign out" in sidebar footer
        │
        ▼
logOut() from useAuth() is called
        │
        ├── Enterprise mode: enterprise logOut (e.g., Auth0 logout redirect)
        │
        └── OSS mode (STATIC_RESOURCES_PROTECTION=true): logoutOSS()
                │
                ▼
        POST /logout (empty form, credentials: "include")
                │
        ┌───────┴───────┐
        ▼               ▼
   200 OK              Error
        │               │
        ▼               ▼
  redirect to        redirect to
  /login.html?logout  /login.html
```

### Feature Flag Mechanism

```
Server (StaticResourcesSecurityConfig)
  │  conductor.ui.security.static-resources-protection.enabled=true
  ▼
AgentSpanUiContextController
  │  injects window.conductor.STATIC_RESOURCES_PROTECTION = true
  ▼
Browser ui-next reads via featureFlags.isEnabled(FEATURES.STATIC_RESOURCES_PROTECTION)
  │
  ├── true  → show sign-out button, wire logoutOSS
  └── false → hide sign-out button (no auth to log out from)
```

### OSS Logout Visibility Matrix

| Server Config | `STATIC_RESOURCES_PROTECTION` | Sign-out shown? | `/logout` exists? |
|---|---|---|---|
| `protection.enabled=true` | `true` (from server) | Yes | Yes (POST) |
| `protection.enabled=false` (default) | `false` (from `context.js`) | No | No (disabled) |
| `enable.ui.serving=false` | `false` (default) | No | No (disabled) |

## Sub-Tasks

### Sub-Task 1: Inject `STATIC_RESOURCES_PROTECTION` flag from server

- **Status:** Pending
- **Objective:** The server's `AgentSpanUiContextController` already injects runtime flags into `window.conductor`. Add `STATIC_RESOURCES_PROTECTION` to tell the UI whether static resources protection (server-side auth) is active.
- **Related Requirements:** R1, R2
- **Dependencies and Preconditions:** The `conductor.ui.security.static-resources-protection.enabled` property already exists and is used by `ProtectedSecurityConfig`.
- **In Scope for This Sub-Task:**
  1. Edit `server/src/main/java/com/netflix/conductor/server/AgentSpanUiContextController.java`:
     - Add a second `@Value` injection for the protection property:
       ```java
       private final boolean staticResourcesProtectionEnabled;

       public AgentSpanUiContextController(
               @Value("${conductor.integrations.ai.enabled:false}") boolean agentSpanEnabled,
               @Value("${conductor.ui.security.static-resources-protection.enabled:false}") boolean staticResourcesProtectionEnabled) {
           this.agentSpanEnabled = agentSpanEnabled;
           this.staticResourcesProtectionEnabled = staticResourcesProtectionEnabled;
       }
       ```
     - Append the runtime override after the `AGENTSPAN_ENABLED` line (line 77):
       ```java
       js.append("window.conductor.STATIC_RESOURCES_PROTECTION = ")
         .append(staticResourcesProtectionEnabled)
         .append(";\n");
       ```
  2. No new controller or class needed — the existing `AgentSpanUiContextController` already serves `/context.js` and appends runtime overrides.
- **Out of Scope for This Sub-Task:** Any other server config injection.
- **Instructions:** Two additions to the existing controller: constructor parameter + append line. Follow the exact pattern of `AGENTSPAN_ENABLED`.
- **Acceptance Criteria:** `./gradlew :conductor-server:compileJava` passes. `curl http://localhost:8080/context.js` returns `window.conductor.STATIC_RESOURCES_PROTECTION = true|false`.
- **Cautionary Points (Risks & Edge Cases):**
  - The controller is gated by `@ConditionalOnProperty(name = "conductor.enable.ui.serving", havingValue = "true", matchIfMissing = true)`. When UI serving is disabled, the flag is never injected. In that case, the UI falls back to the `context.js` default (`false`), which is correct — no logout button.
  - The `@Value` default `:false` ensures the flag is always set even if the property is absent.
- **Testing Suggestions:** `./gradlew :conductor-server:compileJava`
- **Done When:** The flag is injected into `window.conductor` at runtime.

---

### Sub-Task 2: Add `STATIC_RESOURCES_PROTECTION` feature flag to the UI

- **Status:** Pending
- **Objective:** Register the new flag so it can be read via `featureFlags.isEnabled(FEATURES.STATIC_RESOURCES_PROTECTION)`.
- **Related Requirements:** R1, R2
- **Dependencies and Preconditions:** None.
- **In Scope for This Sub-Task:**
  1. In `ui-next/src/utils/flags.ts`, add to the `FEATURES` object (before the closing `}`):
     ```typescript
     // Driven by the server's conductor.ui.security.static-resources-protection.enabled
     // (injected via /context.js). Gates the OSS logout button in the sidebar footer.
     STATIC_RESOURCES_PROTECTION: "STATIC_RESOURCES_PROTECTION",
     ```
  2. In `ui-next/public/context.js`, add the default value (in the OSS Core Features section, after line 14):
     ```javascript
     // Static resources protection (server-side login) — disabled for default OSS
     STATIC_RESOURCES_PROTECTION: false,
     ```
- **Out of Scope for This Sub-Task:** Any other feature flag additions.
- **Instructions:** Two additions — one to the enum, one to the runtime config. Both default to `false`.
- **Acceptance Criteria:** `cd ui-next && npx tsc --noEmit` passes.
- **Cautionary Points (Risks & Edge Cases):**
  - The `featureFlags.isEnabled` function defaults to `false` when a flag is missing (line 128: `defaultValue = false`). So if the server never injects the flag (e.g., old version without this change), the UI safely hides the logout button.
- **Testing Suggestions:** `cd ui-next && npx tsc --noEmit`
- **Done When:** The flag is defined and defaults to `false`.

---

### Sub-Task 3: Create OSS logout utility

- **Status:** Pending
- **Objective:** Create a utility function that performs the OSS logout action: POST to `/logout` and redirect.
- **Related Requirements:** R3
- **Dependencies and Preconditions:** None (pure utility, no imports from other modules needed).
- **In Scope for This Sub-Task:**
  1. Create `ui-next/src/components/features/auth/logoutOSS.ts`:
     ```typescript
     /**
      * OSS logout: POST to the server's /logout endpoint (Spring Security default).
      * Called only when static resources protection is enabled.
      */
     export async function logoutOSS(): Promise<void> {
       try {
         await fetch("/logout", {
           method: "POST",
           credentials: "include",
           redirect: "follow",
         });
       } catch {
         // /logout not available — fall through to redirect
       }
       // Always redirect — either to login page (protected mode) or home
       window.location.href = "/login.html";
     }
     ```
  2. No CSRF handling needed — CSRF is disabled by default in `ProtectedSecurityConfig`. If a deployment enables CSRF, the fetch fails, the catch block runs, and the redirect still happens.
- **Out of Scope for This Sub-Task:** Enterprise logout logic, token cleanup (tokenManagerJotai is already all no-ops in OSS).
- **Instructions:** Single file, single exported async function. Keep it simple.
- **Acceptance Criteria:** `cd ui-next && npx tsc --noEmit` passes.
- **Cautionary Points (Risks & Edge Cases):**
  - `credentials: "include"` ensures the JSESSIONID cookie is sent with the request (needed for Spring Security to identify the session).
  - `redirect: "follow"` lets Spring Security's default logout handler redirect. The explicit `window.location.href` after the try/catch ensures we always navigate even if the fetch itself doesn't redirect.
- **Testing Suggestions:** `cd ui-next && npx tsc --noEmit`
- **Done When:** The file exists and compiles.

---

### Sub-Task 4: Wire real `logOut` into NoAuthProvider

- **Status:** Pending
- **Objective:** Replace the noop `logOut` in `NoAuthProvider` with the real `logoutOSS` function.
- **Related Requirements:** R3
- **Dependencies and Preconditions:** Sub-Task 3 (logoutOSS.ts exists)
- **In Scope for This Sub-Task:**
  1. In `ui-next/src/components/features/auth/NoAuthProvider/NoAuthProvider.tsx`:
     - Add import: `import { logoutOSS } from "../logoutOSS";`
     - In the `useMemo` block (line 29-32), replace `logOut: noop` with `logOut: logoutOSS`:
       ```typescript
       const authState = React.useMemo(
         () => ({
           ...defaultAuthState,
           authService: service,
           logOut: logoutOSS,
         }),
         [service],
       );
       ```
  2. The `isAuthenticated` field remains `false` — that is correct. The sidebar footer will render the OSS sign-out button only when the `STATIC_RESOURCES_PROTECTION` flag is `true`.
- **Out of Scope for This Sub-Task:** Enterprise auth providers, `useAuth.ts`, `types.ts`, `context.ts`.
- **Instructions:** Two-line change: add import + replace one value in the useMemo.
- **Acceptance Criteria:** `cd ui-next && npx tsc --noEmit` passes.
- **Cautionary Points (Risks & Edge Cases):**
  - Enterprise providers that set `authState` in `AuthContext` will skip `NoAuthProvider` entirely (see `useAuth.ts` line 12-13). This change has zero effect on enterprise mode.
  - The `logOut` in `defaultAuthState` is still `noop` — but `NoAuthProvider` overrides it by spreading `defaultAuthState` first, then `logOut: logoutOSS` after, so the override wins.
- **Testing Suggestions:** `cd ui-next && npx vitest run --reporter verbose`
- **Done When:** NoAuthProvider provides a real `logOut` function that calls `logoutOSS`.

---

### Sub-Task 5: Show sign-out button only when protection is enabled

- **Status:** Pending
- **Objective:** In `SidebarFooter.tsx`, show the sign-out button in OSS mode only when `STATIC_RESOURCES_PROTECTION` is `true`.
- **Related Requirements:** R1, R2
- **Dependencies and Preconditions:** Sub-Tasks 2 and 4 complete (feature flag exists, `logOut` is no longer noop).
- **In Scope for This Sub-Task:**
  1. In `ui-next/src/components/providers/sidebar/SidebarFooter.tsx`:
     - Add import:
       ```typescript
       import { FEATURES, featureFlags } from "utils";
       ```
     - Add a constant at the top (after line 20):
       ```typescript
       const isStaticResourcesProtected = featureFlags.isEnabled(
         FEATURES.STATIC_RESOURCES_PROTECTION,
       );
       ```
  2. **Collapsed sidebar** (line 72): Change the condition from:
     ```tsx
     {!open && isAuthenticated && !isMobile && (
     ```
     to:
     ```tsx
     {!open && !isMobile && (isAuthenticated || isStaticResourcesProtected) && (
     ```
     This shows the sign-out icon when either enterprise auth is active OR OSS protection is enabled.

  3. **Expanded sidebar** (line 104-290): Change the outer condition structure:
     ```tsx
     {open && (
       <Box ...>
         {isAuthenticated ? (
           // Full user info block (existing code lines 115-271, unchanged)
         ) : isStaticResourcesProtected ? (
           // OSS simplified sign-out block
           <Box sx={{ display: "flex", justifyContent: "center", mt: 4, pt: 2,
                      borderTop: `1px solid ${alpha(theme.palette.divider, 0.1)}` }}>
             <Button onClick={() => logOut?.()} startIcon={<LogoutOutlined />}
                     size="small" variant="outlined"
                     sx={{ textTransform: "none", color: theme.palette.text.secondary }}>
               Sign Out
             </Button>
           </Box>
         ) : null}
         <SidebarVersionBlock ... />
       </Box>
     )}
     ```
     Key changes:
     - `{isAuthenticated && (` becomes `{isAuthenticated ? (`
     - Add `: isStaticResourcesProtected ? (` for the OSS block
     - Add `: null` for the case where neither is true
     - The `SidebarVersionBlock` stays outside the conditional (always shown)

  4. No changes to `SidebarMenu.tsx` or `Sidebar.tsx` (they already pass through all footer props).
- **Out of Scope for This Sub-Task:** `UserInfo.tsx` (v1 component marked for removal — leave as-is).
- **Instructions:** Edit one file (`SidebarFooter.tsx`). Add the import + flag check + two conditional changes.
- **Acceptance Criteria:**
  - Protection disabled (default OSS): no sign-out button in collapsed or expanded sidebar.
  - Protection enabled: sign-out button visible in both collapsed and expanded states (simplified — no avatar, no name, no copy-token).
  - Enterprise mode: full user info block unchanged.
- **Cautionary Points (Risks & Edge Cases):**
  - The `customUserBlock` prop (line 52-67) short-circuits the entire footer rendering. Our changes to the main render path do not affect `customUserBlock`.
  - The `isMobile` guard on collapsed mode is preserved — on mobile the sidebar is always open (Drawer), so the collapsed icon is irrelevant.
  - The `showCopyAlert` Snackbar is inside the `isAuthenticated ?` branch — it should only appear when authenticated (copy token only makes sense with auth).
- **Testing Suggestions:** `cd ui-next && npx tsc --noEmit`
- **Done When:** Sign-out appears only when protection is enabled.

---

### Sub-Task 6: Test the OSS logout flow

- **Status:** Pending
- **Objective:** Add test coverage for the new OSS logout button rendering.
- **Related Requirements:** R1, R2, R3
- **Dependencies and Preconditions:** Sub-Tasks 1-5 complete.
- **In Scope for This Sub-Task:**
  1. Create `ui-next/src/components/providers/sidebar/SidebarFooter.test.tsx`:
     - Mock `featureFlags.isEnabled` to control `STATIC_RESOURCES_PROTECTION`
     - Test that the sign-out icon renders in collapsed mode when `isStaticResourcesProtected` is `true` and `isAuthenticated` is `false`
     - Test that the sign-out icon does NOT render in collapsed mode when `isStaticResourcesProtected` is `false` and `isAuthenticated` is `false`
     - Test that the simplified sign-out button renders in expanded mode when `isStaticResourcesProtected` is `true`
     - Test that no sign-out renders when both flags are `false`
     - Test that the full user info block renders when `isAuthenticated` is `true` (enterprise mode)
     - Test that clicking the sign-out button calls the `logOut` prop
     - Test that `customUserBlock` short-circuits the footer properly
  2. Use `vitest` with `@testing-library/react`. Mock `featureFlags.isEnabled` with `vi.fn()`.
  3. Import `SidebarFooter` directly and pass required props.
- **Out of Scope for This Sub-Task:** Testing the `logoutOSS` function itself (makes network calls and redirects — best left to manual/E2E). Testing enterprise auth providers.
- **Instructions:** Focused component tests that verify rendering conditions based on the protection flag and auth state.
- **Acceptance Criteria:** `cd ui-next && npx vitest run` passes all new tests.
- **Cautionary Points (Risks & Edge Cases):**
  - The `SidebarFooter` uses `useTheme()` from MUI — wrap tests in a `ThemeProvider` or use existing test setup utilities.
  - The `TokenIcon` import (`images/svg/token.svg`) may require an SVG mock in Vitest config.
  - The `SidebarFooter` references `featureFlags.isEnabled(FEATURES.PLAYGROUND)` at line 20 — mock this as well if the test environment doesn't set it.
- **Testing Suggestions:** `cd ui-next && npx vitest run --reporter verbose`
- **Done When:** Tests cover all rendering combinations of the protection flag and auth state.

---

## Final Integration & Verification

1. **Build server:**
   ```bash
   cd /workspaces/conductor && ./gradlew :conductor-server:compileJava
   ```

2. **Type-check UI:**
   ```bash
   cd ui-next && npx tsc --noEmit
   ```

3. **Run UI tests:**
   ```bash
   cd ui-next && npx vitest run --reporter verbose
   ```

4. **Format & lint:**
   ```bash
   cd ui-next && npx prettier --check src/ && npx eslint src/
   ```

5. **Spotless:**
   ```bash
   cd /workspaces/conductor && ./gradlew spotlessApply
   ```

6. **Manual verification (unprotected OSS — default):**
   - Start server: `cd server && ../gradlew bootRun`
   - Open the UI → sidebar shows **no** sign-out button at the bottom

7. **Manual verification (protected OSS):**
   - Enable protection in `server/src/main/resources/application.properties`:
     ```properties
     conductor.ui.security.static-resources-protection.enabled=true
     conductor.ui.security.static-resources-protection.users[0].username=admin
     conductor.ui.security.static-resources-protection.users[0].password=admin
     conductor.ui.security.static-resources-protection.users[0].roles=ADMIN
     ```
   - Restart server
   - Open UI → redirected to `/login.html`
   - Login with `admin`/`admin`
   - Sidebar shows simplified "Sign Out" button at the bottom
   - Click "Sign Out" → session invalidated, redirected to `/login.html?logout`

8. **Verify server injected the flag:**
   ```bash
   curl http://localhost:8080/context.js | grep STATIC_RESOURCES_PROTECTION
   ```

### Completion Checklist

- [ ] `AgentSpanUiContextController.java` — injects `STATIC_RESOURCES_PROTECTION`
- [ ] `ui-next/src/utils/flags.ts` — `STATIC_RESOURCES_PROTECTION` in FEATURES
- [ ] `ui-next/public/context.js` — default `false`
- [ ] `ui-next/src/components/features/auth/logoutOSS.ts` — OSS logout utility
- [ ] `NoAuthProvider.tsx` — wired `logoutOSS` as `logOut`
- [ ] `SidebarFooter.tsx` — sign-out gated behind `STATIC_RESOURCES_PROTECTION`
- [ ] `SidebarFooter.test.tsx` — tests cover all rendering combinations
- [ ] `cd ui-next && npx tsc --noEmit` passes
- [ ] `cd ui-next && npx vitest run` passes
- [ ] `./gradlew spotlessApply` passes
- [ ] Manual verification complete (both unprotected and protected modes)

## Settled Decisions

1. **Use server-injected flag, not cookie detection:** The `window.conductor.STATIC_RESOURCES_PROTECTION` flag is injected at runtime by the server's `AgentSpanUiContextController`. This is more reliable than checking for `JSESSIONID` cookie, which only exists after login.
2. **Use simple `fetch` for logout, not axios:** The logout is a one-off side effect, not part of the API client. Raw `fetch` keeps it dependency-free.
3. **Simplified OSS block, not identical to enterprise:** OSS mode has no avatar, no user name, no copy-token. The sign-out block is just a single "Sign Out" button. This is intentional — without user identity information, a simple button suffices.
4. **Server-side change is minimal:** The `AgentSpanUiContextController` already injects runtime flags. Adding one more `@Value` and one more append line follows the exact existing pattern. No new classes or endpoints needed.

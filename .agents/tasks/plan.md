# Plan: Display Username When Static Resources Protection Mode Is Enabled

## Objective

When the Conductor server has `conductor.ui.security.static-resources-protection.enabled=true`, the user authenticates via Spring Security form login. Currently the sidebar footer shows only a bare "Sign Out" button with no indication of which user is logged in. This plan adds the username display so the logged-in user sees their name in the sidebar footer.

## Requirements Snapshot

- **R1:** When `conductor.ui.security.static-resources-protection.enabled=true`, the server reads the authenticated user's username from `SecurityContextHolder` and injects it into the UI runtime config (`/context.js`).
- **R2:** The UI reads the injected username and displays it in the sidebar footer below the "Sign Out" button (expanded sidebar) or as a collapsed indicator (collapsed sidebar).
- **R3:** Only the sign-out button is shown when protection mode is enabled but no username is available (edge case / anonymous principal).
- **R4:** The existing enterprise (Auth0/OIDC) user block is unaffected — the protection-mode path is the `else` branch when `isAuthenticated` is `false` and `isStaticResourcesProtected` is `true`.

## Scope

- [x] **Server**: Inject the logged-in username into `window.conductor.STATIC_RESOURCES_USERNAME` via `AgentSpanUiContextController`.
- [x] **UI**: Add `STATIC_RESOURCES_USERNAME` to the `FEATURES` constant.
- [x] **UI**: Read the injected username and display it in `SidebarFooter.tsx` alongside the sign-out button when protection mode is active.
- [x] **UI**: Update `SidebarFooter.test.tsx` to cover the new username-display scenario.

## Assumptions and Constraints

- `/context.js` is served by the `AgentSpanUiContextController` which is active when `conductor.enable.ui.serving=true` (default). In protection mode, `/context.js` requests are authenticated (it is not in the `permitAll` list in `StaticResourcesSecurityConfig`), so `SecurityContextHolder` will contain the logged-in user.
- The username is a simple string from `Principal.getName()` — no roles or avatar needed.
- The UI `window.conductor` values are read lazily (at call time, not module load) by `featureFlags`, so the value injected by the server at `/context.js` runtime is available.

## Risks and Areas Requiring Care

- **SecurityContext not populated when protection mode is off**: When protection mode is `false`, requests to `/context.js` are unauthenticated (anonymous user). The injected username should only be set when protection mode is enabled.
- **Null / anonymous principal**: Spring Security may return `"anonymousUser"` as the principal name when no user is authenticated. Guard against this.
- **Username escaping**: The username is written directly into a JS string literal. Any special characters (`"`, `\`, newline) in the username could break the JavaScript. Escape the value properly.

## Core Concepts

### Server side: Injecting runtime config

The `AgentSpanUiContextController` currently injects boolean flags into `window.conductor`. We add one more injection for the username:

```java
// In AgentSpanUiContextController.contextJs(), after the STATIC_RESOURCES_PROTECTION injection:
if (staticResourcesProtectionEnabled) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated()
            && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
        String username = auth.getName();
        // Escape for JS string literal
        String escaped = username.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        js.append("window.conductor.STATIC_RESOURCES_USERNAME = \"")
          .append(escaped)
          .append("\";\n");
    }
}
```

### UI side: Consuming the injected value

`featureFlags.getValue(featureKey)` reads from `window.conductor` (among other sources). We use it like:

```typescript
const username = featureFlags.getValue(FEATURES.STATIC_RESOURCES_USERNAME);
```

## Sub-Tasks

### Sub-Task 1: Inject username server-side into `/context.js`

- **Status:** Pending
- **Objective:** Modify `AgentSpanUiContextController` to read the authenticated username from `SecurityContextHolder` and write it into `window.conductor.STATIC_RESOURCES_USERNAME`.
- **Related Requirements:** R1
- **Dependencies and Preconditions:** None — `AgentSpanUiContextController` already handles `/context.js` and injects `STATIC_RESOURCES_PROTECTION`.
- **In Scope for This Sub-Task:**
  - Import `org.springframework.security.core.Authentication` and `org.springframework.security.core.context.SecurityContextHolder`.
  - After the existing `STATIC_RESOURCES_PROTECTION` injection block, add a conditional block that sets `STATIC_RESOURCES_USERNAME` when protection mode is enabled and the principal is a real user.
  - Properly escape the username for a JS string literal (`\` → `\\`, `"` → `\"`, `\n` → `\n`).
  - Only inject the username when `staticResourcesProtectionEnabled` is `true` (avoid setting it during anonymous/unauthenticated requests).
  - Guard against `"anonymousUser"` principal string.
- **Out of Scope for This Sub-Task:**
  - Any changes to the UI.
  - Any endpoint other than `/context.js`.
- **Instructions:**
  1. Read `/workspaces/conductor/server/src/main/java/com/netflix/conductor/server/AgentSpanUiContextController.java`.
  2. Add the required imports at the top of the file.
  3. After the `STATIC_RESOURCES_PROTECTION` injection (line ~85), add the conditional block described above.
  4. Rebuild the server module to verify compilation: `./gradlew :conductor-server:compileJava`.
- **Acceptance Criteria:**
  - When protection mode is enabled and a real user is logged in, `context.js` response contains `window.conductor.STATIC_RESOURCES_USERNAME = "someuser";`.
  - When protection mode is disabled, the username is NOT injected.
  - When an anonymous principal is detected (`"anonymousUser"` or null), the username is NOT injected.
- **Cautionary Points (Risks & Edge Cases):**
  - Username must be JS-string-escaped to prevent injection or syntax errors.
  - Do NOT import or use `SecurityContextHolder` unnecessarily — only in the conditional block.
  - Verify the import compiles cleanly (Spring Security is already a dependency).
- **Testing Suggestions:**
  - `./gradlew :conductor-server:compileJava` to ensure compilation succeeds.
  - Manual verification: start the server with `conductor.ui.security.static-resources-protection.enabled=true`, log in, and fetch `/context.js` to confirm the username appears.
- **Done When:** The server module compiles and the `/context.js` response includes `STATIC_RESOURCES_USERNAME` when protection mode is enabled.

### Sub-Task 2: Add `STATIC_RESOURCES_USERNAME` feature flag to UI

- **Status:** Pending
- **Objective:** Add `STATIC_RESOURCES_USERNAME` to the `FEATURES` constant so the UI can read the injected value using `featureFlags.getValue()`.
- **Related Requirements:** R2
- **Dependencies and Preconditions:** None.
- **In Scope for This Sub-Task:**
  - Add `STATIC_RESOURCES_USERNAME: "STATIC_RESOURCES_USERNAME"` to the `FEATURES` object in `flags.ts`.
  - Add an inline comment explaining that this is driven by the server and injected via `/context.js`.
- **Out of Scope for This Sub-Task:**
  - Any changes to `featureFlags` utility — `getValue()` already works for string-typed flags.
  - Any component changes.
- **Instructions:**
  1. Read `/workspaces/conductor/ui-next/src/utils/flags.ts`.
  2. Add the new entry after `STATIC_RESOURCES_PROTECTION` (line 99) with a comment.
- **Acceptance Criteria:**
  - The new flag is present in the `FEATURES` object.
  - The existing test suite still passes.
- **Testing Suggestions:** `./gradlew :conductor-ui-next:test` (or `cd ui-next && pnpm test`) — though there is no direct test against `flags.ts`, the import should not break anything.
- **Done When:** The `FEATURES` object includes `STATIC_RESOURCES_USERNAME`.

### Sub-Task 3: Display username in SidebarFooter when protection mode is active

- **Status:** Pending
- **Objective:** Modify `SidebarFooter.tsx` to read the injected username and display it alongside the "Sign Out" button when `isStaticResourcesProtected` is true and `isAuthenticated` is false.
- **Related Requirements:** R2, R3, R4
- **Dependencies and Preconditions:** Sub-Task 2 (flag constant must exist).
- **In Scope for This Sub-Task:**
  - In the expanded-sidebar (`open=true`) branch, inside the `else` block for `isStaticResourcesProtected` (line 277–300), replace the bare "Sign Out" button with a small user info section:
    - Read the username via `const username = featureFlags.getValue(FEATURES.STATIC_RESOURCES_USERNAME)`.
    - If a username is present, display it in a Typography component (similar styling to how `conductorUser?.id` is shown for enterprise, lines 169–179), plus a "Sign Out" button below.
    - If no username is present, fall back to the current bare "Sign Out" button.
  - In the collapsed-sidebar (`open=false`) branch (lines 75–106), the sign-out icon is already shown — no change needed for the collapsed state since there's no room for a label.
- **Out of Scope for This Sub-Task:**
  - Changing the collapsed sidebar behavior.
  - Changing the enterprise-authenticated (`isAuthenticated=true`) user block.
  - Avatar support (protection mode has no avatar).
- **Instructions:**
  1. Read `/workspaces/conductor/ui-next/src/components/providers/sidebar/SidebarFooter.tsx`.
  2. Import `featureFlags` and `FEATURES` from `"utils"` (already imported on line 17).
  3. Inside the expanded sidebar block, modify the `isStaticResourcesProtected` section (lines 277–300):
     - Before rendering, get `const username = featureFlags.getValue(FEATURES.STATIC_RESOURCES_USERNAME)`.
     - If `username` is truthy, render a small user info box with the username displayed via a `Typography` component (similar style to lines 158–179 but without Avatar) and the "Sign Out" button below.
     - If `username` is falsy, keep the existing bare "Sign Out" button.
- **Acceptance Criteria:**
  - When protection mode is on and username is available, the expanded sidebar shows the username + "Sign Out" button.
  - When protection mode is on and username is NOT available, the expanded sidebar shows only the "Sign Out" button (current behavior).
  - When protection mode is off, nothing changes.
  - Enterprise mode (isAuthenticated=true) is completely unaffected.
- **Cautionary Points (Risks & Edge Cases):**
  - Use `featureFlags.getValue()` (not `isEnabled()`) since the username is a string.
  - Handle the case where the flag is not set at all (undefined/null) gracefully.
  - Ensure the existing test snapshot behavior is preserved for enterprise and unauthenticated modes.
- **Testing Suggestions:**
  - `cd ui-next && pnpm test` — ensure `SidebarFooter.test.tsx` passes.
  - The new code path can be verified manually by setting `window.conductor.STATIC_RESOURCES_USERNAME` in browser devtools and observing the sidebar.
- **Done When:** The component renders the username when `STATIC_RESOURCES_USERNAME` is set on `window.conductor`.

### Sub-Task 4: Add tests for protection-mode username display

- **Status:** Pending
- **Objective:** Extend `SidebarFooter.test.tsx` to cover the new username display in protection mode.
- **Related Requirements:** R2, R3
- **Dependencies and Preconditions:** Sub-Task 3 (component changes must be in place).
- **In Scope for This Sub-Task:**
  - In the "expanded sidebar" describe block, add a test case:
    - `"shows username when static resources protection is enabled and username is available"`
    - Mock `featureFlags.getValue` to return the username for `STATIC_RESOURCES_USERNAME`.
    - Verify the username is visible in the document.
  - Add a test case:
    - `"hides username when static resources protection is enabled but no username is available"`
    - Mock `featureFlags.getValue` to return undefined/null for `STATIC_RESOURCES_USERNAME`.
    - Verify only the sign-out button is shown, not a username.
- **Out of Scope for This Sub-Task:**
  - Editing the mocked `featureFlags` setup — it already mocks `isEnabled` and `getValue`.
- **Instructions:**
  1. Read `/workspaces/conductor/ui-next/src/components/providers/sidebar/SidebarFooter.test.tsx`.
  2. Note that `featureFlags.getValue` is already a `vi.fn()` mock (line 16).
  3. Add the two new test cases in the `"expanded sidebar (open=true)"` describe block.
- **Acceptance Criteria:**
  - New tests pass.
  - All existing tests still pass.
- **Testing Suggestions:** `cd ui-next && pnpm test` (or `npx vitest run`).
- **Done When:** The new test cases pass green.

## Final Integration & Verification

- **System-Wide Test:**
  1. `./gradlew :conductor-server:compileJava` — server compiles.
  2. `cd ui-next && pnpm test` — all UI tests pass.
  3. Start the server with `conductor.ui.security.static-resources-protection.enabled=true` and a configured user.
  4. Log in via `/login.html`, then verify the sidebar footer shows the logged-in username.
  5. Fetch `/context.js` directly and verify `window.conductor.STATIC_RESOURCES_USERNAME` contains the correct value.
- **Completion Checklist:**
  - [ ] Server injects `STATIC_RESOURCES_USERNAME` in `/context.js` only when protection mode is on.
  - [ ] UI `FEATURES` constant includes `STATIC_RESOURCES_USERNAME`.
  - [ ] SidebarFooter displays the username in protection mode when one is available.
  - [ ] SidebarFooter falls back to bare sign-out when no username is available.
  - [ ] New tests pass.
  - [ ] Existing tests pass.
  - [ ] `./gradlew spotlessApply` run on changed server files.
  - [ ] No enterprise/authenticated code path is altered.

## Open Questions

None.

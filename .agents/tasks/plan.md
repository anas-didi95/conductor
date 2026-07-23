# Plan: Restrict API Docs Side Menu to ADMIN Role Only

## Goal

Make the "API Docs" entry in the sidebar visible only to users with the `ADMIN` role. VIEWER and SUPPORT roles should not see it. If RBAC is disabled (role is `null`), the item remains visible (current fallback behavior).

---

## Step 1 — Understand the current flow

1. `sidebarCoreItems.tsx` exports `getCoreSidebarItems(open)` — a pure function returning `MenuItemType[]`. The API Docs item (`id: "swaggerItem"`) has `hidden: false`.
2. `UiSidebar.tsx` calls `getCoreSidebarItems(open)` and merges plugin items, then passes the result to `<Sidebar>`.
3. `Sidebar.tsx` filters `menuItems.filter(item => !item.hidden)` and passes `sections` to `SidebarMenu`.
4. `SidebarMenu` renders `SidebarItem` for each item; `SidebarItem` also has `if (item.hidden) return null`.
5. `usePermissions()` hook (`usePermissions.ts`) fetches `/api/rbac/current-user-role` and returns `{ role, canRead, canExecute, canWrite, isLoading }`.
   - Roles: `VIEWER`, `SUPPORT`, `ADMIN`, `null` (fallback — full access).
   - `hasPermission(role, 'WRITE')` is true only for `ADMIN` (or `null`).

**Existing precedent:** `RunWorkflowButton.tsx` already uses `usePermissions()` and conditionally renders based on `canExecute`.

---

## Step 2 — Apply permission gating in `UiSidebar.tsx`

**File:** `ui-next/src/components/providers/sidebar/UiSidebar.tsx`

### 2a — Import `usePermissions`

Add to imports:
```ts
import { usePermissions } from "hooks/usePermissions";
```

### 2b — Call the hook and derive the flag

Inside `UISidebar` component, add:
```ts
const { role } = usePermissions();
```

Determine whether to show API Docs:
- Show when `role === null` (RBAC disabled) → same as `hasPermission` fallback.
- Show when `role === 'ADMIN'`.
- Hide for `VIEWER` and `SUPPORT`.

This can be expressed as:
```ts
const showApiDocs = role === null || role === 'ADMIN';
```

Or equivalently using the existing `hasPermission`:
```ts
const showApiDocs = hasPermission(role, 'WRITE');
```

(`hasPermission` for `WRITE` returns true for `ADMIN` and `null`; false for `VIEWER` and `SUPPORT`.)

### 2c — Apply to the swagger item

In the `menuItems` useMemo, after `mergePluginSidebarItems`, find the swagger item and set its `hidden` property:

```ts
const menuItems = useMemo<MenuItemType[]>(() => {
  const coreItems = getCoreSidebarItems(open);
  const merged = mergePluginSidebarItems(coreItems, pluginSidebarItems);
  // Hide API Docs for non-ADMIN roles
  const swaggerItem = merged.find((i) => i.id === "swaggerItem");
  if (swaggerItem && !showApiDocs) {
    swaggerItem.hidden = true;
  }
  return merged;
}, [open, pluginSidebarItems, showApiDocs]);
```

---

## Step 3 — No other changes needed

- The `hidden` property is already respected at both the `Sidebar.tsx` filter and the `SidebarItem.tsx` render guard.
- No changes needed in `sidebarCoreItems.tsx` (keep it as a pure function).
- No changes needed in `Sidebar.tsx` or `SidebarMenu.tsx`.
- The route `/api-reference` itself is **not** restricted by this change — only the sidebar menu entry. If route-level protection is also desired, that would be a separate task.

---

## Step 4 — Verify

### 4a — Build check
```sh
cd ui-next
pnpm typecheck
pnpm lint
```

### 4b — Manual test scenarios

| Scenario | Expected result |
|----------|----------------|
| RBAC disabled (role `null`) | API Docs visible |
| Role `ADMIN` | API Docs visible |
| Role `SUPPORT` | API Docs hidden |
| Role `VIEWER` | API Docs hidden |
| API Docs route `/api-reference` accessed directly | Route loads (no redirect) — only menu entry is hidden |

---

## Files modified

| File | Change |
|------|--------|
| `ui-next/src/components/providers/sidebar/UiSidebar.tsx` | Import `usePermissions`, call hook, set `swaggerItem.hidden = true` for non-ADMIN roles |

# Plan: Feature Flag to Switch Monaco Editor Between CDN and Local Bundle

## Objective

Add a runtime feature flag (`window.conductor.MONACO_USE_LOCAL`) that controls whether Monaco Editor loads its workers and assets from a CDN (default) or from the local npm bundle. When set to `true`, the application bundles Monaco workers at build time via Vite `?worker` imports and configures `@monaco-editor/react` to use the local ESM package, eliminating all CDN requests.

## Requirements Snapshot

- **R1:** A runtime feature flag `MONACO_USE_LOCAL` (boolean) controls the loading strategy:
  - `false` (default): Monaco loads from CDN as it does today — zero behavioral change.
  - `true`: Monaco workers are bundled locally via Vite `?worker` imports; `self.MonacoEnvironment` routes workers to local bundles; `@monaco-editor/react`'s loader uses the local ESM `monaco-editor` package.
- **R2:** The flag is settable via `window.conductor.MONACO_USE_LOCAL` (i.e. via `/context.js` or browser DevTools), matching the project's existing runtime feature-flag pattern.
- **R3:** All existing Monaco editor features (JSON schema validation, jq/PromQL providers, diff editor, code editing) work in both modes.
- **R4:** Tests that mock `@monaco-editor/react` continue to pass in both modes.
- **R5:** The library build (`vite build --mode lib`) is unaffected — it uses `src/index.ts` and does not import the setup module.
- **R6:** CSP nonce injection for dynamically created scripts continues to work in both modes.

## Scope

- Create `src/monaco/setup.ts` — a side-effect module that reads the flag and conditionally configures local Monaco.
- Integrate the setup module into `main.tsx` (imported before React renders).
- Add `MONACO_USE_LOCAL` to `public/context.js`, `public/context.js.example`, and `src/utils/flags.ts`.
- All changes are reversible — setting the flag to `false` (or omitting it) restores CDN behavior exactly.

## Assumptions and Constraints

- `monaco-editor` ^0.55.1 and `@monaco-editor/react` ^4.7.0 are already installed as `devDependencies`/`peerDependencies`.
- `@monaco-editor/loader` v1.7.0 (bundled with `@monaco-editor/react`) supports `loader.config({ monaco })` — confirmed by inspecting its `types.d.ts`.
- Vite 7.x supports the `?worker` import suffix natively.
- `vite/client` types are already included in `tsconfig.json`'s `types` array, so `?worker` imports are typed.
- `window.conductor` is populated by `/context.js` (loaded via `<script>` in `<head>` before `main.tsx`), so the flag is available at module evaluation time.
- Tests mock `@monaco-editor/react` at the module level via `vi.mock()` in `setupTests.ts`, so the real Monaco setup module never executes in test.

## Risks and Areas Requiring Care

1. **Module evaluation order**: `loader.config({ monaco })` and `MonacoEnvironment` must be set before any `Editor` component mounts. The setup module is imported at the top of `main.tsx`, before React renders — this is safe.
2. **Worker path correctness**: Monaco v0.55.x worker paths (`monaco-editor/esm/vs/editor/editor.worker`, `monaco-editor/esm/vs/language/json/json.worker`) must be verified at implementation time.
3. **Race condition with async imports**: Using dynamic `import()` for the loader config could cause a race if an Editor mounts before the async chunk resolves. Use **static** top-level imports for `monaco-editor` and `@monaco-editor/react` and gate the config behind an `if` check on the flag.
4. **Library build compatibility**: The library build (`vite build --mode lib`) uses `src/index.ts` as entry and will NOT import `src/monaco/setup.ts`. No changes needed, but verify this.
5. **Worker chunk bloat**: The `?worker` imports add separate JS chunks at build time. Even when the flag is `false`, these chunks exist in `dist/` (unused). This is acceptable — the chunks are tiny relative to the rest of the bundle.

## Core Concepts

### Existing Monaco loading path (CDN default)

`@monaco-editor/react` uses `@monaco-editor/loader` internally. When an `Editor` mounts, the loader:
1. Creates a `<script>` tag pointing to `https://cdn.jsdelivr.net/npm/monaco-editor@0.55.1/min/vs/loader.js`
2. This downloads the AMD build of Monaco
3. Monaco then creates web workers from CDN URLs for syntax highlighting, etc.

### Local loading path (flag enabled)

Two things change:
1. **Workers**: Vite's `?worker` suffix bundles Monaco worker files as separate chunks at build time. `self.MonacoEnvironment.getWorker()` returns these bundled workers instead of CDN URLs.
2. **Loader**: `loader.config({ monaco })` tells `@monaco-editor/react` to use the locally installed ESM `monaco-editor` package instead of downloading the AMD build from CDN.

### Vite `?worker` imports

```typescript
// Vite bundles this file as a separate worker chunk
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
const worker = new EditorWorker();
```

### The loader.config API

```typescript
import { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';

// Tells the loader to use the local ESM package instead of downloading AMD from CDN
loader.config({ monaco });
```

## Sub-Tasks

### Sub-Task 1: Create the Monaco setup module with feature flag

- **Status:** Pending
- **Objective:** Create `src/monaco/setup.ts` that reads `window.conductor.MONACO_USE_LOCAL` and conditionally configures local Monaco loading.
- **Related Requirements:** R1, R2, R3, R6
- **Dependencies and Preconditions:** None.
- **In Scope for This Sub-Task:**
  - Create `src/monaco/setup.ts` with:
    - Static top-level imports for `monaco-editor`, `@monaco-editor/react`, and workers via `?worker`.
    - A guard: `if (typeof window !== 'undefined' && (window as any).conductor?.MONACO_USE_LOCAL === true)`.
    - Inside the guard:
      - Set `self.MonacoEnvironment.getWorker()` to route JSON to `JsonWorker` and everything else to `EditorWorker`.
      - Call `loader.config({ monaco })`.
    - Outside the guard: the module is a no-op (workers and monaco are imported but never used).
- **Out of Scope for This Sub-Task:**
  - Modifying any existing files.
- **Instructions:**
  1. Create directory `src/monaco/`.
  2. Create `src/monaco/setup.ts` with the following structure:
```typescript
import { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import JsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker';

const useLocal =
  typeof window !== 'undefined' &&
  (window as any).conductor?.MONACO_USE_LOCAL === true;

if (useLocal) {
  self.MonacoEnvironment = {
    getWorker(_workerId: string, label: string): Worker {
      switch (label) {
        case 'json':
          return new JsonWorker();
        default:
          return new EditorWorker();
      }
    },
  };

  loader.config({ monaco });
}
```
- **Acceptance Criteria:**
  - File exists and TypeScript type-checks (`npx tsc --noEmit` passes).
  - `?worker` imports resolve correctly (verify paths in `node_modules/monaco-editor/esm/vs/`).
- **Cautionary Points (Risks & Edge Cases):**
  - Worker path verification: run `ls node_modules/monaco-editor/esm/vs/language/json/` to confirm `json.worker.js` exists before writing the import.
  - The `(window as any).conductor` cast is intentional — `window.conductor` is declared as `any` in `flags.ts`.
- **Implementation Suggestions:**
  - Verify paths first with `ls` commands in the monaco-editor package.
  - The static imports for `monaco-editor` and `@monaco-editor/react` are safe — the loader does NOT initiate a CDN download at import time; only when `loader.init()` is called (which happens on Editor mount).
- **Testing Suggestions:**
  - `npx tsc --noEmit` — must pass.
  - `npx vite build` — must succeed.
- **Done When:** `src/monaco/setup.ts` exists, type-checks, and builds.

### Sub-Task 2: Add MONACO_USE_LOCAL to the flags and context configuration

- **Status:** Pending
- **Objective:** Register the `MONACO_USE_LOCAL` flag in the runtime config and the feature-flag constants so it can be set via `/context.js` and queried via `featureFlags`.
- **Related Requirements:** R1, R2
- **Dependencies and Preconditions:** None.
- **In Scope for This Sub-Task:**
  - Add to `src/utils/flags.ts`:
    - `MONACO_USE_LOCAL: "MONACO_USE_LOCAL"` in the `FEATURES` object.
  - Add to `public/context.js`:
    - `MONACO_USE_LOCAL: false` (explicit default for OSS).
  - Add to `public/context.js.example`:
    - `"MONACO_USE_LOCAL" : false` (documentation).
- **Out of Scope for This Sub-Task:**
  - Creating the setup module (Sub-Task 1).
  - Modifying `main.tsx`.
- **Instructions:**
  1. Read `src/utils/flags.ts`. Add the new entry near other `STATIC_RESOURCES_*` flags (around line 102) with a comment: `// Runtime flag: loads Monaco workers locally instead of from CDN when true`.
  2. Read `public/context.js`. Add `MONACO_USE_LOCAL: false` to the `// UI Configuration` section.
  3. Read `public/context.js.example`. Add `"MONACO_USE_LOCAL" : false`.
- **Acceptance Criteria:**
  - Three files modified with the new flag.
  - Flag defaults to `false`.
  - Existing tests pass.
- **Testing Suggestions:**
  - `cd ui-next && npm test` — must pass.
- **Done When:** All three files are updated, tests pass.

### Sub-Task 3: Integrate setup module into `main.tsx`

- **Status:** Pending
- **Objective:** Import `./monaco/setup` at the top of `main.tsx` so it runs before any React component renders.
- **Related Requirements:** R3, R4
- **Dependencies and Preconditions:** Sub-Task 1 (setup module exists).
- **In Scope for This Sub-Task:**
  - Add `import "./monaco/setup";` to `src/main.tsx`, placed right before the local module imports (before `import { router }...`), with a comment explaining the purpose.
- **Out of Scope for This Sub-Task:**
  - Any other changes to `main.tsx`.
- **Instructions:**
  1. Read `src/main.tsx`.
  2. Add the import after the `highlight.js` CSS import and before `import { router }...`:
```typescript
// Configure Monaco Editor workers (local or CDN based on window.conductor.MONACO_USE_LOCAL)
import "./monaco/setup";
```
- **Acceptance Criteria:**
  - The import executes before any React rendering.
  - TypeScript type-check and build pass.
- **Testing Suggestions:**
  - `npx tsc --noEmit` — passes.
  - `npx vite build` — succeeds.
  - `cd ui-next && npm test` — passes (setup module is side-effect only, never executed in test due to `vi.mock` on `@monaco-editor/react`).
- **Done When:** `main.tsx` updated, build and tests pass.

### Sub-Task 4: Verify build modes and final integration

- **Status:** Pending
- **Objective:** Confirm both app build and library build succeed, and that the feature flag works correctly in both modes.
- **Related Requirements:** R4, R5
- **Dependencies and Preconditions:** Sub-Tasks 1–3 complete.
- **In Scope for This Sub-Task:**
  - Run `npx vite build` — verify app build succeeds and worker chunks exist in `dist/`.
  - Run `npx vite build --mode lib` — verify library build succeeds and does NOT include worker chunks (since it uses `src/index.ts` entry).
  - Run `cd ui-next && npm test` — verify all unit tests pass.
  - Verify that setting `window.conductor.MONACO_USE_LOCAL = true` in browser DevTools on the dev server causes Monaco to load locally (no CDN requests).
  - Verify that with the flag `false` or unset, Monaco loads from CDN as before (no regression).
- **Out of Scope for This Sub-Task:**
  - Fixing any pre-existing test or build failures.
- **Instructions:**
  1. `npx vite build` — check for worker chunks in `dist/` (files like `*editor.worker*.js`, `*json.worker*.js`).
  2. `npx vite build --mode lib` — verify it succeeds (uses `src/index.ts`, not `main.tsx`).
  3. `npx vitest run` — all tests pass.
  4. Start dev server. Open browser, set `window.conductor.MONACO_USE_LOCAL = true` in console, navigate to a page with Monaco. Check Network tab — no requests to `cdn.jsdelivr.net` for Monaco.
  5. Reload without the flag — confirm CDN requests appear again (regression check).
- **Acceptance Criteria:**
  - App build produces separate worker chunks.
  - Library build succeeds without including workers.
  - Unit tests pass.
  - With flag `true`: Monaco works, zero CDN requests.
  - With flag `false`/unset: Monaco works, CDN requests as before.
- **Cautionary Points (Risks & Edge Cases):**
  - If the browser shows CSP errors in local mode, the CSP nonce patching in `index.html` may need adjustment for `blob:` worker URLs. Workers created via `new Worker()` with a blob URL don't need a nonce, so this should be fine.
  - If `loader.config({ monaco })` is called but the config isn't picked up by the Editor component, verify that the flag check evaluates to `true` before the Editor mounts (check console timing).
- **Testing Suggestions:**
  - `npx vite build && ls -la dist/` to inspect worker chunks.
- **Done When:** All builds, tests, and manual verifications pass.

## Final Integration & Verification

1. **Build verification:** `npx vite build && npx vite build --mode lib && npm test`
2. **Flag ON verification:** Start dev server, set `window.conductor.MONACO_USE_LOCAL = true` in console, open any editor page. Confirm no CDN requests and editor works.
3. **Flag OFF verification** (regression): Reload without the flag. Confirm CDN requests and editor works.
4. **Completion Checklist:**
   - [ ] `src/monaco/setup.ts` exists, type-checks, and builds.
   - [ ] `src/main.tsx` imports the setup module.
   - [ ] `src/utils/flags.ts` includes `MONACO_USE_LOCAL`.
   - [ ] `public/context.js` sets `MONACO_USE_LOCAL: false`.
   - [ ] `public/context.js.example` documents the flag.
   - [ ] `vite build` succeeds (app mode).
   - [ ] `vite build --mode lib` succeeds (library mode).
   - [ ] `npm test` passes.
   - [ ] With flag `true`: Monaco works, no CDN requests.
   - [ ] With flag `false`/unset: Monaco works, CDN requests as before (no regression).

## Open Questions

None.

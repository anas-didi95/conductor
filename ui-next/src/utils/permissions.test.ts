/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

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

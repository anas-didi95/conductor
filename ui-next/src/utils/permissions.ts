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

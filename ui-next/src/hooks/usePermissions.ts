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

import { useQuery } from 'react-query';
import { type UserRole, type Permission, hasPermission } from 'utils/permissions';

export interface PermissionsState {
  role: UserRole;
  canRead: boolean;
  canExecute: boolean;
  canWrite: boolean;
  isLoading: boolean;
}

export function usePermissions(): PermissionsState {
  const { data, isLoading } = useQuery<{ role: string }>(
    ['current-user-role'],
    () => fetch('/api/rbac/current-user-role').then((r) => r.json()),
    {
      staleTime: Infinity,
      retry: false,
      placeholderData: { role: 'ADMIN' },
    },
  );

  const role = (data?.role as UserRole) ?? 'ADMIN';

  return {
    role,
    canRead: hasPermission(role, 'READ'),
    canExecute: hasPermission(role, 'EXECUTE'),
    canWrite: hasPermission(role, 'WRITE'),
    isLoading,
  };
}

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

import { Navigate } from 'react-router';
import { usePermissions } from '../../../hooks/usePermissions';
import type { Permission } from 'utils/permissions';
import type { ReactNode } from 'react';

interface RequireRoutePermissionProps {
  permission: Permission;
  redirectTo: string;
  children: ReactNode;
}

export function RequireRoutePermission({ permission, redirectTo, children }: RequireRoutePermissionProps) {
  const { canRead, canExecute, canWrite } = usePermissions();
  const permissionMap = { READ: canRead, EXECUTE: canExecute, WRITE: canWrite };
  if (!permissionMap[permission]) {
    return <Navigate to={redirectTo} replace />;
  }
  return <>{children}</>;
}

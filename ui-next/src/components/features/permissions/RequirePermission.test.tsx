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

import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { RequirePermission } from './RequirePermission';

// Mock the usePermissions hook
vi.mock('../../../hooks/usePermissions', () => ({
  usePermissions: vi.fn(),
}));

import { usePermissions } from '../../../hooks/usePermissions';

const mockedUsePermissions = usePermissions as ReturnType<typeof vi.fn>;

describe('RequirePermission', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders children when permission is granted (WRITE for ADMIN)', () => {
    mockedUsePermissions.mockReturnValue({
      role: 'ADMIN',
      canRead: true,
      canExecute: true,
      canWrite: true,
      isLoading: false,
    });

    render(
      <RequirePermission permission="WRITE">
        <button>Delete</button>
      </RequirePermission>,
    );

    expect(screen.getByText('Delete')).toBeInTheDocument();
  });

  it('renders children when permission is granted (EXECUTE for SUPPORT)', () => {
    mockedUsePermissions.mockReturnValue({
      role: 'SUPPORT',
      canRead: true,
      canExecute: true,
      canWrite: false,
      isLoading: false,
    });

    render(
      <RequirePermission permission="EXECUTE">
        <button>Terminate</button>
      </RequirePermission>,
    );

    expect(screen.getByText('Terminate')).toBeInTheDocument();
  });

  it('renders fallback when permission is denied', () => {
    mockedUsePermissions.mockReturnValue({
      role: 'VIEWER',
      canRead: true,
      canExecute: false,
      canWrite: false,
      isLoading: false,
    });

    render(
      <RequirePermission permission="WRITE" fallback={<span>No access</span>}>
        <button>Delete</button>
      </RequirePermission>,
    );

    expect(screen.queryByText('Delete')).not.toBeInTheDocument();
    expect(screen.getByText('No access')).toBeInTheDocument();
  });

  it('renders nothing when permission is denied and no fallback given', () => {
    mockedUsePermissions.mockReturnValue({
      role: 'VIEWER',
      canRead: true,
      canExecute: false,
      canWrite: false,
      isLoading: false,
    });

    const { container } = render(
      <RequirePermission permission="WRITE">
        <button>Delete</button>
      </RequirePermission>,
    );

    expect(screen.queryByText('Delete')).not.toBeInTheDocument();
    expect(container.innerHTML).toBe('');
  });

  it('renders children when role is null (RBAC disabled fallback)', () => {
    mockedUsePermissions.mockReturnValue({
      role: null,
      canRead: true,
      canExecute: true,
      canWrite: true,
      isLoading: false,
    });

    render(
      <RequirePermission permission="EXECUTE">
        <button>Run</button>
      </RequirePermission>,
    );

    expect(screen.getByText('Run')).toBeInTheDocument();
  });
});

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
import { UseQueryResult } from 'react-query';

// Mock react-query's useQuery
vi.mock('react-query', () => ({
  useQuery: vi.fn(),
}));

import { useQuery } from 'react-query';
import { usePermissions } from './usePermissions';

const mockedUseQuery = useQuery as unknown as ReturnType<typeof vi.fn>;

describe('usePermissions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns VIEWER permissions when role is VIEWER', () => {
    mockedUseQuery.mockReturnValue({
      data: { role: 'VIEWER' },
      isLoading: false,
    } as Partial<UseQueryResult>);

    const result = usePermissions();

    expect(result.role).toBe('VIEWER');
    expect(result.canRead).toBe(true);
    expect(result.canExecute).toBe(false);
    expect(result.canWrite).toBe(false);
    expect(result.isLoading).toBe(false);
  });

  it('returns SUPPORT permissions when role is SUPPORT', () => {
    mockedUseQuery.mockReturnValue({
      data: { role: 'SUPPORT' },
      isLoading: false,
    } as Partial<UseQueryResult>);

    const result = usePermissions();

    expect(result.role).toBe('SUPPORT');
    expect(result.canRead).toBe(true);
    expect(result.canExecute).toBe(true);
    expect(result.canWrite).toBe(false);
  });

  it('returns ADMIN permissions when role is ADMIN', () => {
    mockedUseQuery.mockReturnValue({
      data: { role: 'ADMIN' },
      isLoading: false,
    } as Partial<UseQueryResult>);

    const result = usePermissions();

    expect(result.role).toBe('ADMIN');
    expect(result.canRead).toBe(true);
    expect(result.canExecute).toBe(true);
    expect(result.canWrite).toBe(true);
  });

  it('falls back to ADMIN when data is not yet loaded (placeholderData)', () => {
    mockedUseQuery.mockReturnValue({
      data: undefined,
      isLoading: true,
    } as Partial<UseQueryResult>);

    const result = usePermissions();

    // placeholderData returns { role: 'ADMIN' }
    expect(result.role).toBe('ADMIN');
    expect(result.canRead).toBe(true);
    expect(result.canExecute).toBe(true);
    expect(result.canWrite).toBe(true);
    expect(result.isLoading).toBe(true);
  });

  it('falls back to ADMIN when role is undefined in response', () => {
    mockedUseQuery.mockReturnValue({
      data: {},
      isLoading: false,
    } as Partial<UseQueryResult>);

    const result = usePermissions();

    expect(result.role).toBe('ADMIN');
    expect(result.canRead).toBe(true);
    expect(result.canExecute).toBe(true);
    expect(result.canWrite).toBe(true);
  });
});

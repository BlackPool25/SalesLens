import type { Page } from '@playwright/test';

/**
 * Mock the silent refresh + profile endpoints so the app
 * believes the user is authenticated on every page load.
 *
 * Call BEFORE `page.goto()` to catch the on-mount /auth/refresh call.
 */
export async function setupAuthenticated(page: Page) {
  await page.route('**/auth/refresh', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ accessToken: 'e2e-mock-token' }),
    });
  });
  await page.route('**/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 1,
        username: 'admin',
        email: 'admin@saleslens.local',
        roles: ['ADMIN'],
      }),
    });
  });
}

/**
 * Make the on-mount silent refresh fail so the app stays
 * in an unauthenticated state.
 */
export async function setupUnauthenticated(page: Page) {
  await page.route('**/auth/refresh', async (route) => {
    await route.fulfill({ status: 401 });
  });
}

/**
 * Mock the minimal data-source listing endpoint so dropdowns
 * and other components that depend on sources have data.
 */
export async function mockSources(page: Page, sources?: unknown[]) {
  const defaultSources = [
    {
      id: 'src-csv-001',
      name: 'Superstore Sales',
      sourceType: 'CSV_FILE',
      trustScore: 0.9,
      active: true,
      createdAt: '2026-06-01T10:00:00Z',
    },
    {
      id: 'src-excel-001',
      name: 'Regional Data',
      sourceType: 'EXCEL_FILE',
      trustScore: 0.85,
      active: true,
      createdAt: '2026-06-05T10:00:00Z',
    },
    {
      id: 'src-jdbc-001',
      name: 'ERP Database',
      sourceType: 'JDBC_POSTGRES',
      trustScore: 0.95,
      active: true,
      createdAt: '2026-06-10T10:00:00Z',
    },
  ];
  const data = sources ?? defaultSources;
  await page.route('**/datasources/get-all-sources**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: data,
        totalElements: data.length,
        totalPages: 1,
        size: 100,
        number: 0,
        first: true,
        last: true,
        empty: data.length === 0,
      }),
    });
  });
}

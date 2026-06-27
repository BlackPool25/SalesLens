import { test, expect } from '@playwright/test';
import { setupAuthenticated } from './fixtures';

test.describe('Conflicts Page', () => {
  // Shared mutable conflict data for resolve/suppress tests
  const openConflicts = [
    {
      id: 'conflict-001',
      entityType: 'customers',
      entityId: 'ent-cust-042',
      fieldName: 'email',
      sourceAId: 'src-csv-001',
      sourceBId: 'src-excel-001',
      sourceAName: 'Superstore Sales',
      sourceBName: 'Regional Data',
      valueA: 'john@example.com',
      valueB: 'john.doe@example.com',
      status: 'OPEN',
      resolutionStrategy: 'FLAGGED_FOR_REVIEW',
      sourceATrustScore: 0.9,
      sourceBTrustScore: 0.85,
      createdAt: '2026-06-15T12:00:00Z',
    },
    {
      id: 'conflict-002',
      entityType: 'products',
      entityId: 'ent-prod-007',
      fieldName: 'price',
      sourceAId: 'src-csv-001',
      sourceBId: 'src-jdbc-001',
      sourceAName: 'Superstore Sales',
      sourceBName: 'ERP Database',
      valueA: 29.99,
      valueB: 32.5,
      status: 'OPEN',
      resolutionStrategy: 'TRUST_HIERARCHY',
      sourceATrustScore: 0.9,
      sourceBTrustScore: 0.95,
      createdAt: '2026-06-14T08:00:00Z',
    },
  ];

  test('displays conflict cards for open conflicts', async ({ page }) => {
    await setupAuthenticated(page);

    // Mock conflicts list endpoint — only intercept GET (not PUT resolve/suppress)
    await page.route('**/api/v1/conflicts**', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: openConflicts,
            totalElements: openConflicts.length,
            totalPages: 1,
            size: 10,
            number: 0,
            first: true,
            last: true,
            empty: openConflicts.length === 0,
          }),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto('/conflicts');

    // Page heading
    await expect(page.locator('h1')).toHaveText('Data Conflicts');

    // The entity type badges have class bg-accent-subtle (not shared by <option> elements)
    const customerBadge = page.locator('span.bg-accent-subtle', { hasText: 'customers' });
    const productBadge = page.locator('span.bg-accent-subtle', { hasText: 'products' });
    await expect(customerBadge).toBeVisible();
    await expect(productBadge).toBeVisible();

    // Conflict values should appear
    await expect(page.getByText('john@example.com')).toBeVisible();
    await expect(page.getByText('john.doe@example.com')).toBeVisible();
    await expect(page.getByText('29.99')).toBeVisible();
    await expect(page.getByText('32.5')).toBeVisible();

    // Both cards should have OPEN status badge
    await expect(page.locator('span').filter({ hasText: 'OPEN' })).toHaveCount(2);
  });

  test('resolve conflict: enters value and sees RESOLVED status', async ({ page }) => {
    await setupAuthenticated(page);

    // Mutable copy so we can update status on resolve
    const mutableConflicts = JSON.parse(JSON.stringify(openConflicts));

    await page.route('**/api/v1/conflicts**', async (route) => {
      const url = new URL(route.request().url());
      const method = route.request().method();

      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: mutableConflicts,
            totalElements: mutableConflicts.length,
            totalPages: 1,
            size: 10,
            number: 0,
            first: true,
            last: true,
            empty: mutableConflicts.length === 0,
          }),
        });
      } else if (method === 'PUT' && url.pathname.includes('/resolve')) {
        mutableConflicts[0].status = 'RESOLVED';
        await route.fulfill({ status: 200, body: '{}' });
      } else {
        await route.fulfill({ status: 200, body: '{}' });
      }
    });

    await page.goto('/conflicts');

    // Wait for a conflict card to render
    await expect(page.locator('span.bg-accent-subtle', { hasText: 'customers' })).toBeVisible();

    // Click "Resolve" button on the first card
    const resolveBtn = page.getByRole('button', { name: 'Resolve' }).first();
    await resolveBtn.click();

    // The resolve input should appear
    const resolveInput = page.locator('input[placeholder="Enter resolved value..."]');
    await expect(resolveInput).toBeVisible();

    // Type a value
    await resolveInput.fill('john.doe@company.com');

    // Click the check/submit button (parent of the lucide-check SVG)
    const submitBtn = page.locator('button svg.lucide-check').locator('..');
    await submitBtn.click();

    // Wait for the status to update — React Query refetches and card shows RESOLVED
    await expect(page.locator('span').filter({ hasText: 'RESOLVED' }).first()).toBeVisible({
      timeout: 8000,
    });
  });

  test('suppress conflict: confirms and sees SUPPRESSED status', async ({ page }) => {
    await setupAuthenticated(page);

    const mutableConflicts = JSON.parse(JSON.stringify(openConflicts));

    await page.route('**/api/v1/conflicts**', async (route) => {
      const url = new URL(route.request().url());
      const method = route.request().method();

      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: mutableConflicts,
            totalElements: mutableConflicts.length,
            totalPages: 1,
            size: 10,
            number: 0,
            first: true,
            last: true,
            empty: mutableConflicts.length === 0,
          }),
        });
      } else if (method === 'PUT' && url.pathname.includes('/suppress')) {
        mutableConflicts[0].status = 'SUPPRESSED';
        await route.fulfill({ status: 200, body: '{}' });
      } else {
        await route.fulfill({ status: 200, body: '{}' });
      }
    });

    await page.goto('/conflicts');

    // Wait for a conflict card
    await expect(page.locator('span.bg-accent-subtle', { hasText: 'customers' })).toBeVisible();

    // Click "Suppress" button on the first card
    const suppressBtn = page.getByRole('button', { name: 'Suppress' }).first();
    await suppressBtn.click();

    // Confirm dialog should appear
    await expect(page.getByText('Suppress Conflict')).toBeVisible();
    await expect(
      page.getByText('Are you sure you want to suppress this conflict?')
    ).toBeVisible();

    // Click Suppress in the dialog — use .last() since 2 card buttons + 1 dialog button exist
    const confirmBtn = page.getByRole('button', { name: 'Suppress' }).last();
    await confirmBtn.click();

    // Card should update to SUPPRESSED
    await expect(page.locator('span').filter({ hasText: 'SUPPRESSED' }).first()).toBeVisible({
      timeout: 8000,
    });
  });
});

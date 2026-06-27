import { test, expect } from '@playwright/test';
import { setupAuthenticated } from './fixtures';

test.describe('Sources Page', () => {
  test('renders the sources heading when authenticated', async ({ page }) => {
    await setupAuthenticated(page);
    await page.goto('/sources');

    // The SourcesPage is a placeholder — verify it renders
    await expect(page.locator('h1')).toHaveText('Sources');
  });

  test('navigation sidebar is accessible from sources page', async ({ page }) => {
    await setupAuthenticated(page);
    await page.goto('/sources');

    // Verify the top nav is present with all expected links
    const nav = page.locator('header nav');
    await expect(nav.getByText('Sources')).toBeVisible();
    await expect(nav.getByText('Ingestion')).toBeVisible();
    await expect(nav.getByText('Quality')).toBeVisible();
    await expect(nav.getByText('Conflicts')).toBeVisible();
  });

  test('redirects to /login when not authenticated', async ({ page }) => {
    // Don't set up auth — let /auth/refresh fail
    await page.route('**/auth/refresh', async (route) => {
      await route.fulfill({ status: 401 });
    });

    await page.goto('/sources');

    // The ProtectedRoute should redirect to /login
    await expect(page).toHaveURL(/\/login/);
  });
});

import { test, expect } from '@playwright/test';
import { setupUnauthenticated } from './fixtures';

test.describe('Login Page', () => {
  test('shows the login form with email and password fields', async ({ page }) => {
    await setupUnauthenticated(page);
    await page.goto('/login');

    // Page title and subtitle
    await expect(page.locator('h1')).toHaveText('SalesLens');
    await expect(page.getByText('Sign in to your account')).toBeVisible();

    // Form fields
    await expect(page.getByPlaceholder('admin@saleslens.local')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();

    // Submit button
    await expect(page.getByRole('button', { name: 'Sign In' })).toBeVisible();
  });

  test('successful login redirects to /sources', async ({ page }) => {
    // Keep /auth/refresh failing so the login page renders (not auto-redirected)
    await setupUnauthenticated(page);

    // Mock the login POST
    await page.route('**/auth/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ accessToken: 'e2e-mock-token' }),
      });
    });

    // Mock /auth/me — called by login() after receiving token
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

    // IMPORTANT: Do NOT re-mock /auth/refresh here.
    // setupUnauthenticated already set it to 401 — login page shows.
    // After login, the AuthProvider is already mounted so /auth/refresh
    // is NOT called again on SPA navigation to /sources.

    await page.goto('/login');

    // Fill in valid credentials
    await page.getByPlaceholder('admin@saleslens.local').fill('admin@saleslens.local');
    await page.locator('input[type="password"]').fill('correct-password');

    // Submit
    await page.getByRole('button', { name: 'Sign In' }).click();

    // Should navigate to /sources
    await expect(page).toHaveURL(/\/sources/);
  });

  test('invalid login shows error message', async ({ page }) => {
    await setupUnauthenticated(page);

    // Mock login to return 401
    await page.route('**/auth/login', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Invalid credentials' }),
      });
    });

    await page.goto('/login');

    // Fill in wrong credentials
    await page.getByPlaceholder('admin@saleslens.local').fill('wrong@email.com');
    await page.locator('input[type="password"]').fill('wrong-password');

    // Submit
    await page.getByRole('button', { name: 'Sign In' }).click();

    // Verify error banner appears
    await expect(page.getByText('Invalid credentials')).toBeVisible();
  });
});

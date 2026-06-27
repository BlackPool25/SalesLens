import { test, expect } from '@playwright/test';
import { setupAuthenticated, mockSources } from './fixtures';

test.describe('Ingestion Page', () => {
  test('shows source dropdown populated with CSV and Excel sources', async ({ page }) => {
    await setupAuthenticated(page);
    await mockSources(page);
    await page.goto('/ingestion');

    // Wait for the page heading
    await expect(page.locator('h1')).toHaveText('Ingestion');

    // Source selector should be populated
    const sourceSelect = page.locator('select#source-select');
    await expect(sourceSelect).toBeVisible();

    // Should contain CSV and Excel options (not JDBC ones)
    await expect(sourceSelect.locator('option')).toContainText(['Superstore Sales (CSV_FILE)']);
    await expect(sourceSelect.locator('option')).toContainText(['Regional Data (EXCEL_FILE)']);
  });

  test('uploads a CSV file and shows success message', async ({ page }) => {
    await setupAuthenticated(page);
    await mockSources(page);

    // Mock the CSV upload endpoint
    await page.route('**/api/v1/ingest/csv', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: 'job-001', jobId: 'job-001' }),
      });
    });

    // Mock jobs endpoint (will be called after upload to refresh job list)
    await page.route('**/api/v1/jobs**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'job-001',
              sourceId: 'src-csv-001',
              sourceName: 'Superstore Sales',
              status: 'COMPLETED',
              recordsRead: 150,
              recordsTransformed: 150,
              recordsPassed: 145,
              recordsFailed: 5,
              recordsLoaded: 140,
              recordsConflicted: 5,
              createdAt: '2026-06-15T12:00:00Z',
              updatedAt: '2026-06-15T12:05:00Z',
            },
          ],
          totalElements: 1,
          totalPages: 1,
          size: 10,
          number: 0,
          first: true,
          last: true,
          empty: false,
        }),
      });
    });

    await page.goto('/ingestion');

    // Wait for sources to load and select the first CSV source
    const sourceSelect = page.locator('select#source-select');
    await sourceSelect.waitFor({ state: 'visible' });
    await sourceSelect.selectOption('src-csv-001');

    // Attach a fake CSV file to the hidden file input
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test_sales_data.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('order_id,amount,date\n1,100,2026-01-01\n2,200,2026-01-02'),
    });

    // The file should be shown with its name
    await expect(page.getByText('test_sales_data.csv')).toBeVisible();

    // Click Upload File
    await page.getByRole('button', { name: 'Upload File' }).click();

    // Wait for success message — target the success alert specifically
    const successAlert = page.locator('div.bg-semantic-success\\/10');
    await expect(successAlert).toBeVisible();
    await expect(successAlert).toContainText('job-001');
  });

  test('shows recent jobs table', async ({ page }) => {
    await setupAuthenticated(page);
    await mockSources(page);

    // Mock jobs endpoint to return a list of jobs
    await page.route('**/api/v1/jobs**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'job-completed-001',
              sourceId: 'src-csv-001',
              sourceName: 'Superstore Sales',
              status: 'COMPLETED',
              recordsRead: 500,
              recordsTransformed: 500,
              recordsPassed: 480,
              recordsFailed: 20,
              recordsLoaded: 460,
              recordsConflicted: 20,
              createdAt: '2026-06-15T10:00:00Z',
              updatedAt: '2026-06-15T10:05:00Z',
            },
            {
              id: 'job-running-002',
              sourceId: 'src-excel-001',
              sourceName: 'Regional Data',
              status: 'RUNNING',
              recordsRead: 250,
              recordsTransformed: 200,
              recordsPassed: 180,
              recordsFailed: 10,
              recordsLoaded: 0,
              recordsConflicted: 0,
              createdAt: '2026-06-15T11:00:00Z',
              updatedAt: '2026-06-15T11:02:00Z',
            },
          ],
          totalElements: 2,
          totalPages: 1,
          size: 10,
          number: 0,
          first: true,
          last: true,
          empty: false,
        }),
      });
    });

    await page.goto('/ingestion');

    // Wait for the jobs table to appear
    await expect(page.getByText('Recent Jobs')).toBeVisible();

    // Verify both jobs are rendered in the table
    // Target table cells specifically to avoid option text conflicts
    const tableCells = page.locator('table tbody td');
    await expect(tableCells.filter({ hasText: 'Superstore Sales' })).toBeVisible();
    await expect(tableCells.filter({ hasText: 'Regional Data' })).toBeVisible();

    // Verify status badges render (look for COMPLETED / RUNNING text)
    await expect(page.getByText('COMPLETED')).toBeVisible();
    await expect(page.getByText('RUNNING')).toBeVisible();

    // Verify record counts are shown (500 / 500 ...)
    await expect(page.getByText('500 / 500 / 480 / 20 / 460 / 20')).toBeVisible();
  });

  test('shows empty state when no jobs exist', async ({ page }) => {
    await setupAuthenticated(page);
    await mockSources(page);

    // Mock empty jobs response
    await page.route('**/api/v1/jobs**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [],
          totalElements: 0,
          totalPages: 0,
          size: 10,
          number: 0,
          first: true,
          last: true,
          empty: true,
        }),
      });
    });

    await page.goto('/ingestion');
    await expect(page.getByText('No ingestion jobs yet.')).toBeVisible();
  });
});

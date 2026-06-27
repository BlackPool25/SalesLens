import { test, expect } from '@playwright/test';
import { setupAuthenticated, mockSources } from './fixtures';

test.describe('Quality Dashboard', () => {
  test('prompts user to select a source when none is selected', async ({ page }) => {
    await setupAuthenticated(page);
    await mockSources(page);
    await page.goto('/quality');

    await expect(page.locator('h1')).toHaveText('Quality Overview');
    await expect(page.getByText('Please select a source to view quality metrics.')).toBeVisible();
  });

  test('displays quality scores and charts when a source is selected', async ({ page }) => {
    await setupAuthenticated(page);
    await mockSources(page);

    // Mock quality scores endpoint
    await page.route('**/api/v1/quality/scores**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              sourceId: 'src-csv-001',
              scoreCompleteness: 0.95,
              scoreValidity: 0.88,
              scoreUniqueness: 0.92,
              scoreConsistency: 0.85,
              scoreTimeliness: 0.90,
              scoreAccuracy: 0.78,
              overallScore: 0.88,
              letterGrade: 'B',
              totalRecordsScored: 15000,
              averageScore: 0.87,
              openIssues: 12,
              createdAt: '2026-06-15T12:00:00Z',
            },
            {
              sourceId: 'src-csv-001',
              scoreCompleteness: 0.93,
              scoreValidity: 0.85,
              scoreUniqueness: 0.91,
              scoreConsistency: 0.82,
              scoreTimeliness: 0.88,
              scoreAccuracy: 0.75,
              overallScore: 0.86,
              letterGrade: 'B',
              totalRecordsScored: 12000,
              averageScore: 0.85,
              openIssues: 15,
              createdAt: '2026-06-14T12:00:00Z',
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

    // Mock quality issues endpoint (called by QualityIssuesSection)
    await page.route('**/api/v1/quality/issues**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'issue-001',
              sourceFieldName: 'email',
              ruleCode: 'VALID_EMAIL',
              severity: 'HIGH',
              dimension: 'VALIDITY',
              message: 'Email format is invalid for 23 records',
              status: 'OPEN',
              createdAt: '2026-06-15T12:00:00Z',
            },
            {
              id: 'issue-002',
              sourceFieldName: 'order_amount',
              ruleCode: 'NON_NEGATIVE',
              severity: 'MEDIUM',
              dimension: 'CONSISTENCY',
              message: 'Order amount should not be negative',
              status: 'OPEN',
              createdAt: '2026-06-15T11:00:00Z',
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

    await page.goto('/quality');

    // Select the first source
    const sourceSelect = page.locator('select#source-select');
    await sourceSelect.waitFor({ state: 'visible' });
    await sourceSelect.selectOption('src-csv-001');

    // Wait for quality data to load — look for the overall score percentage
    await expect(page.getByText('88.0%')).toBeVisible();
    await expect(page.getByText('Overall Quality Score')).toBeVisible();

    // Summary stats should appear
    await expect(page.getByText('15,000')).toBeVisible(); // total records scored

    // Letter grade appears in the QualityScoreRing SVG (use the aria-label)
    const ringSvg = page.getByLabel('Quality Score: B (88%)');
    await expect(ringSvg).toBeVisible();

    // Charts should render (Recharts SVGs)
    const charts = page.locator('.recharts-responsive-container');
    await expect(charts.first()).toBeVisible();

    // Quality Issues heading should be visible
    await expect(page.getByText('Quality Issues')).toBeVisible();
  });

  test('shows quality issues table with acknowledge action', async ({ page }) => {
    await setupAuthenticated(page);
    await mockSources(page);

    // Mock quality scores (needs at least one score to show issues section)
    await page.route('**/api/v1/quality/scores**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              sourceId: 'src-csv-001',
              scoreCompleteness: 0.95,
              scoreValidity: 0.88,
              scoreUniqueness: 0.92,
              scoreConsistency: 0.85,
              scoreTimeliness: 0.90,
              scoreAccuracy: 0.78,
              overallScore: 0.88,
              letterGrade: 'B',
              totalRecordsScored: 15000,
              averageScore: 0.87,
              openIssues: 2,
              createdAt: '2026-06-15T12:00:00Z',
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

    // Mock issues with one OPEN and one ACKNOWLEDGED
    const issuesData = [
      {
        id: 'issue-open-001',
        sourceFieldName: 'email',
        ruleCode: 'VALID_EMAIL',
        severity: 'HIGH',
        dimension: 'VALIDITY',
        message: 'Email format is invalid for 23 records',
        status: 'OPEN',
        createdAt: '2026-06-15T12:00:00Z',
      },
      {
        id: 'issue-ack-002',
        sourceFieldName: 'phone',
        ruleCode: 'VALID_PHONE',
        severity: 'LOW',
        dimension: 'VALIDITY',
        message: 'Phone format varies across records',
        status: 'ACKNOWLEDGED',
        createdAt: '2026-06-14T10:00:00Z',
      },
    ];

    // Mock acknowledge endpoint
    await page.route('**/api/v1/quality/issues/**/acknowledge', async (route) => {
      // Update our data so the refetch picks up the change
      issuesData[0].status = 'ACKNOWLEDGED';
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(issuesData[0]),
      });
    });

    await page.route('**/api/v1/quality/issues**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: issuesData,
          totalElements: issuesData.length,
          totalPages: 1,
          size: 10,
          number: 0,
          first: true,
          last: true,
          empty: issuesData.length === 0,
        }),
      });
    });

    await page.goto('/quality');

    // Select source
    const sourceSelect = page.locator('select#source-select');
    await sourceSelect.waitFor({ state: 'visible' });
    await sourceSelect.selectOption('src-csv-001');

    // Wait for the issues table to render
    // Target table cells to avoid matching option or message text
    const issueCells = page.locator('table tbody td');
    await expect(issueCells.filter({ hasText: 'email' }).first()).toBeVisible();
    await expect(issueCells.filter({ hasText: 'HIGH' })).toBeVisible();
    await expect(issueCells.filter({ hasText: 'OPEN' })).toBeVisible();
    // ACKNOWLEDGED appears in both status cell and action button — get the status cell specifically
    await expect(issueCells.filter({ hasText: 'ACKNOWLEDGED' }).first()).toBeVisible();

    // Click the "Acknowledge" button for the first issue
    const acknowledgeBtn = page.getByRole('button', { name: 'Acknowledge' }).first();
    await acknowledgeBtn.click();

    // After acknowledge, the button should change to "Acknowledged"
    await expect(page.getByRole('button', { name: 'Acknowledged' }).first()).toBeVisible({ timeout: 5000 });
  });
});

import { test, expect, type Page } from '@playwright/test';

/**
 * 商品列表分页验收（TDD 红→绿）：
 * 用户要能自选每页 10 / 15 条并翻页，而不是列表只有一页。
 */
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';

async function loginAndOpen(page: Page): Promise<void> {
  await page.goto(ADMIN_URL + 'login');
  await page.waitForTimeout(1000);
  if (page.url().includes('login')) {
    await page.getByPlaceholder('用户名').fill('admin');
    await page.getByPlaceholder('密码').fill('123456');
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
      ),
      page.getByRole('button', { name: /登\s*录/ }).click(),
    ]);
  }
  await page.goto(ADMIN_URL + 'product/list');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1200);
}

test('product list supports 10/15 per page and real paging', async ({ page }) => {
  const errors: string[] = [];
  const requests: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text());
  });
  page.on('request', (r) => {
    if (r.url().includes('/api/admin/products') && r.method() === 'GET') {
      requests.push(r.url());
    }
  });

  await loginAndOpen(page);

  // 1) 分页器必须存在，并显示总条数
  const pager = page.locator('.el-pagination');
  await expect(pager).toBeVisible();
  const pagerText = (await page.locator('.sent-table-pager').innerText()).replace(/\s+/g, ' ');
  console.log('分页器:', pagerText);
  expect(pagerText).toMatch(/共\s*\d+\s*条|Total\s*\d+/);

  // 2) 默认每页 10 条
  const rows = page.locator('.el-table__body tbody tr');
  await expect(rows).toHaveCount(10);

  // 3) 切到每页 15 条
  await page.locator('.el-pagination .el-select').click();
  await page.waitForTimeout(400);
  const option15 = page
    .locator('.el-select-dropdown:visible .el-select-dropdown__item')
    .filter({ hasText: /15\s*(\/page|条\/页)/ })
    .first();
  await expect(option15).toBeVisible();
  await option15.click();
  await page.waitForTimeout(1300);
  await expect(rows).toHaveCount(15);
  console.log('切到 15 条/页后请求:', requests.at(-1));
  expect(requests.at(-1)).toContain('size=15');

  // 4) 翻到第 2 页，首行商品必须变化
  // 第一列是勾选框，商品 ID 在第二列
  const firstIdOnPage1 = await rows.first().locator('td').nth(1).innerText();
  await page.locator('.el-pagination .btn-next').click();
  await page.waitForTimeout(1300);
  const firstIdOnPage2 = await rows.first().locator('td').nth(1).innerText();
  console.log('翻页:', firstIdOnPage1, '→', firstIdOnPage2);
  expect(firstIdOnPage2).not.toBe(firstIdOnPage1);
  expect(requests.at(-1)).toContain('page=2');

  await page.screenshot({ path: 'screenshots/product-list-pagination.png' });
  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

import { test, expect, type Page } from '@playwright/test';

/**
 * 分页能力验收（TDD 红→绿）：
 * 1) 「企业审核与授信」原先 :page=1 / :size=100 写死且不监听翻页事件，分页器是摆设；
 * 2) 所有分页页要记住「页码 + 每页条数」，切到别的页面再回来仍保持，且各页面互不干扰。
 */
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';

async function login(page: Page): Promise<void> {
  await page.goto(ADMIN_URL + 'login');
  await page.waitForTimeout(900);
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
}

function track(page: Page, keyword: string): { urls: string[] } {
  const state = { urls: [] as string[] };
  page.on('request', (r) => {
    if (r.method() === 'GET' && r.url().includes(keyword)) state.urls.push(r.url());
  });
  return state;
}

test('企业审核与授信支持真翻页与自选每页条数', async ({ page }) => {
  const calls = track(page, '/api/admin/customers');
  await login(page);
  await page.goto(ADMIN_URL + 'customer/enterprise');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1200);

  const pager = page.locator('.sent-table-pager');
  await expect(pager).toBeVisible();
  const pagerText = (await pager.innerText()).replace(/\s+/g, ' ');
  console.log('企业分页器:', pagerText);
  // 50 条企业客户 / 20 条每页 = 至少 3 页
  expect(pagerText).toMatch(/共\s*\d+\s*条/);
  const rows = page.locator('.el-table__body tbody tr');
  await expect(rows).toHaveCount(20);

  // 翻到第 2 页：必须真的发请求且首行变化
  const firstIdOnPage1 = await rows.first().locator('td').first().innerText();
  await page.locator('.sent-table-pager .btn-next').click();
  await page.waitForTimeout(1200);
  const firstIdOnPage2 = await rows.first().locator('td').first().innerText();
  console.log('企业翻页:', firstIdOnPage1, '→', firstIdOnPage2);
  expect(firstIdOnPage2).not.toBe(firstIdOnPage1);
  expect(calls.urls.at(-1)).toContain('page=2');

  // 切到 15 条/页
  await page.locator('.sent-table-pager .el-select').click();
  await page.waitForTimeout(400);
  const option15 = page
    .locator('.el-select-dropdown:visible .el-select-dropdown__item')
    .filter({ hasText: /15\s*(\/page|条\/页)/ })
    .first();
  await expect(option15).toBeVisible();
  await option15.click();
  await page.waitForTimeout(1200);
  await expect(rows).toHaveCount(15);
  expect(calls.urls.at(-1)).toContain('size=15');
  console.log('切 15 条/页请求:', calls.urls.at(-1));
});

test('分页状态在页面切换后保持，且各页面互不干扰', async ({ page }) => {
  const calls = track(page, '/api/admin/products');
  await login(page);
  await page.goto(ADMIN_URL + 'product/list');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1200);

  // 商品列表：切到 15 条/页 + 第 2 页
  await page.locator('.sent-table-pager .el-select').click();
  await page.waitForTimeout(400);
  await page
    .locator('.el-select-dropdown:visible .el-select-dropdown__item')
    .filter({ hasText: /15\s*(\/page|条\/页)/ })
    .first()
    .click();
  await page.waitForTimeout(1200);
  await page.locator('.sent-table-pager .btn-next').click();
  await page.waitForTimeout(1200);
  const rows = page.locator('.el-table__body tbody tr');
  await expect(rows).toHaveCount(15);
  const firstIdOnProduct = await rows.first().locator('td').nth(1).innerText();
  expect(calls.urls.at(-1)).toContain('page=2');

  // 切到「客户列表」并翻到第 3 页（另一个页面自己的状态）
  await page.goto(ADMIN_URL + 'customer/list');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1000);
  const customerRows = page.locator('.el-table__body tbody tr');
  await expect(customerRows).toHaveCount(20);
  await page.locator('.sent-table-pager .btn-next').click();
  await page.waitForTimeout(900);
  await page.locator('.sent-table-pager .btn-next').click();
  await page.waitForTimeout(900);
  const firstIdOnCustomer = await customerRows.first().locator('td').first().innerText();

  // 回到商品列表：仍应是第 2 页 / 15 条每页
  await page.goto(ADMIN_URL + 'product/list');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1200);
  const backRows = page.locator('.el-table__body tbody tr');
  await expect(backRows).toHaveCount(15);
  expect(await backRows.first().locator('td').nth(1).innerText()).toBe(firstIdOnProduct);
  const activePage = await page.locator('.sent-table-pager .el-pager .is-active').innerText();
  const activeSize = (await page.locator('.sent-table-pager').innerText()).replace(/\s+/g, ' ');
  console.log('回到商品列表: 当前页 =', activePage, '| 分页器 =', activeSize);
  expect(activePage.trim()).toBe('2');
  expect(activeSize).toMatch(/15\s*条\/页/);
  expect(calls.urls.at(-1)).toContain('page=2');
  expect(calls.urls.at(-1)).toContain('size=15');

  // 再回客户列表：仍应是第 3 页（各页面独立）
  await page.goto(ADMIN_URL + 'customer/list');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1000);
  const customerActive = await page.locator('.sent-table-pager .el-pager .is-active').innerText();
  console.log('回到客户列表: 当前页 =', customerActive);
  expect(customerActive.trim()).toBe('3');
  expect(await page.locator('.el-table__body tbody tr').first().locator('td').first().innerText())
    .toBe(firstIdOnCustomer);
});

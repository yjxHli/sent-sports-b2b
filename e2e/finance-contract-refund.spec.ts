import { test, expect, type Page } from '@playwright/test';

/**
 * 财务三页验收（TDD 红→绿）。
 *
 * 用户缺陷：
 * 1) 「合同管理」无数据 —— contract 表从来没有任何写入方，页面永远是空表；
 * 2) 「退款审核」清一色「已退款」—— 104 条全是 REFUNDED，审核按钮全部禁用，没有可操作单据；
 * 3) 「回款记录」不能翻页 —— 前端写死 `page: 1, size: 20`，483 笔回款只能看到前 20 笔。
 *
 * 断言：合同有真实行且到期状态筛选/翻页由后端 SQL 完成；退款状态至少 3 种且存在待审核；
 * 回款记录能翻到第 2 页并真的换数据。
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

test('合同管理有真实数据，到期状态筛选与翻页都走后端', async ({ page }) => {
  const calls = track(page, '/api/admin/contracts');
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  await login(page);
  await page.goto(ADMIN_URL + 'finance/contract');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1200);

  const rows = page.locator('.el-table__body tbody tr');
  const rowCount = await rows.count();
  console.log('合同行数:', rowCount);
  expect(rowCount).toBeGreaterThan(0);

  const pagerText = (await page.locator('.sent-table-pager').innerText()).replace(/\s+/g, ' ');
  console.log('合同分页器:', pagerText);
  expect(pagerText).toMatch(/共\s*\d+\s*条/);
  // 真实合同数应远大于一页，必须有下一页
  expect(pagerText).toMatch(/共\s*(\d+)\s*条/);
  const total = Number((pagerText.match(/共\s*(\d+)\s*条/) ?? [])[1]);
  expect(total).toBeGreaterThan(20);

  // 到期状态 = 已到期：必须带 dueState 参数并由后端过滤
  await page.locator('.sent-table-filters .el-select').first().click();
  await page.waitForTimeout(400);
  await page
    .locator('.el-select-dropdown:visible .el-select-dropdown__item')
    .filter({ hasText: '已到期' })
    .first()
    .click();
  await page.getByRole('button', { name: '查询' }).click();
  await page.waitForTimeout(1200);

  expect(calls.urls.at(-1), '到期状态必须下推 SQL').toContain('dueState=EXPIRED');
  const expiredOnly = await rows.count();
  console.log('已到期行数:', expiredOnly);
  // 剩余天数列必须全是「已逾期」
  const remainingTexts = await rows.locator('td:nth-child(7)').allInnerTexts();
  expect(remainingTexts.every((t) => t.includes('已逾期') || t.includes('--'))).toBeTruthy();
  expect(expiredOnly).toBeGreaterThan(0);

  expect(errors, 'console 不应有错误').toHaveLength(0);
});

test('退款审核列表状态不再清一色「已退款」，且存在可操作单据', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  await login(page);
  await page.goto(ADMIN_URL + 'finance/refund');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1200);

  const rows = page.locator('.el-table__body tbody tr');
  const statuses = await rows.locator('td:nth-child(3)').allInnerTexts();
  const distinct = [...new Set(statuses.map((s) => s.trim()))];
  console.log('首屏退款状态种类:', distinct.join(' / '));
  expect(distinct.length, '首屏至少应有 3 种退款状态').toBeGreaterThanOrEqual(3);
  expect(distinct).not.toEqual(['已退款']);

  // 待审核行必须存在，且「通过 / 驳回」按钮可用
  const pendingIndex = statuses.findIndex((s) => s.includes('待审核'));
  expect(pendingIndex, '应存在待审核退款单').toBeGreaterThanOrEqual(0);
  const pendingRow = rows.nth(pendingIndex);
  await expect(pendingRow.getByRole('button', { name: '通过' })).toBeEnabled();
  await expect(pendingRow.getByRole('button', { name: '驳回' })).toBeEnabled();

  // 状态筛选下拉也应拿到混合结果
  await page.locator('.sent-table-filters .el-select').first().click();
  await page.waitForTimeout(400);
  await page
    .locator('.el-select-dropdown:visible .el-select-dropdown__item')
    .filter({ hasText: '待审核' })
    .first()
    .click();
  await page.getByRole('button', { name: '查询' }).click();
  await page.waitForTimeout(1200);
  const filtered = await rows.locator('td:nth-child(3)').allInnerTexts();
  expect(filtered.every((s) => s.includes('待审核'))).toBeTruthy();

  expect(errors, 'console 不应有错误').toHaveLength(0);
});

test('回款记录支持翻页，第 2 页换数据', async ({ page }) => {
  const calls = track(page, '/api/admin/payment-records');
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  await login(page);
  await page.goto(ADMIN_URL + 'finance/payment-record');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1500);

  const pager = page.locator('.record-pager');
  await expect(pager).toBeVisible();
  const pagerText = (await pager.innerText()).replace(/\s+/g, ' ');
  console.log('回款分页器:', pagerText);
  const total = Number((pagerText.match(/共\s*(\d+)\s*条/) ?? [])[1]);
  expect(total).toBeGreaterThan(20);

  const rows = page.locator('.el-table__body tbody tr').filter({ hasText: 'PR' });
  const firstOnPage1 = (await rows.first().locator('td').first().innerText()).trim();
  expect(calls.urls.at(-1)).toContain('page=1');

  await pager.locator('.btn-next').click();
  await page.waitForTimeout(1500);
  const firstOnPage2 = (await rows.first().locator('td').first().innerText()).trim();
  console.log('回款翻页:', firstOnPage1, '→', firstOnPage2);

  expect(firstOnPage2).not.toBe(firstOnPage1);
  expect(calls.urls.at(-1)).toContain('page=2');

  // 切换「仅看当前客户」要回到第 1 页，否则会停在不存在的页码上显示空表
  // Element Plus 把原生 input 隐藏了，只能点 label 包裹层
  await page.locator('.el-checkbox').filter({ hasText: '仅看当前客户' }).first().click();
  await page.waitForTimeout(1200);
  expect(calls.urls.at(-1)).toContain('page=1');

  expect(errors, 'console 不应有错误').toHaveLength(0);
});

import { test, expect, type Page } from '@playwright/test';
import { sql, sqlNumber } from './db';

/**
 * 企业审核与授信验收（TDD 红→绿）。
 *
 * 用户缺陷：
 * 1) 点「审核通过」和「审核驳回」都提示「系统异常」——前端打的是
 *    `/api/admin/enterprise/{id}/audit`，服务端从未实现该端点；
 * 2) 点「确认授信」提示成功，但列表里依然显示「未授信」——授信只写了进程内 Map，
 *    而列表读的是真库 `enterprise_profile`。
 *
 * 这里断言：审核与授信都 HTTP 200 + code=0，且界面标签随之变化（认证状态 / 授信状态）。
 */
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const ENTERPRISE = ADMIN_URL + 'customer/enterprise';

interface Call {
  status: number;
  code: number | null;
  url: string;
}

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

function watchPosts(page: Page, prefix: string): Call[] {
  const calls: Call[] = [];
  page.on('response', async (r) => {
    if (r.request().method() !== 'POST' || !r.url().includes(prefix)) return;
    let code: number | null = null;
    try {
      const body = JSON.parse(await r.text());
      code = typeof body.code === 'number' ? body.code : null;
    } catch { /* 非 JSON 响应 */ }
    calls.push({ status: r.status(), code, url: r.url() });
  });
  return calls;
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('企业审核通过与驳回都返回成功，认证状态随之变化', async ({ page }) => {
  const calls = watchPosts(page, '/api/admin/enterprise/');
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  await login(page);
  await page.goto(ENTERPRISE);
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1000);

  // 选中第一行；认证状态在第 3 列（客户ID / 企业名称 / 认证状态）
  const firstRow = page.locator('.el-table__body tbody tr').first();
  const customerId = (await firstRow.locator('td').first().innerText()).trim();
  await firstRow.getByRole('button', { name: '选择' }).click();
  await expect(page.locator('.sent-panel').nth(1)).toContainText(customerId);

  const statusCell = page.locator('.el-table__body tbody tr')
    .filter({ hasText: customerId }).first().locator('td').nth(2);

  // 1) 审核通过
  await page.getByRole('button', { name: '审核通过' }).click();
  await page.locator('.el-message-box:visible').getByRole('button', { name: /确\s*定/ }).click();
  await page.waitForTimeout(1500);
  expect(calls.length).toBeGreaterThanOrEqual(1);
  expect(calls[0].status, '审核通过 HTTP 状态').toBe(200);
  expect(calls[0].code, '审核通过业务码').toBe(0);
  await expect(statusCell).toContainText('已认证');

  // 2) 审核驳回：原因必填并落库，企业端进度页会原文展示
  await page.getByRole('button', { name: '审核驳回' }).click();
  const rejectBox = page.locator('.el-message-box:visible');
  await rejectBox.locator('textarea').fill('营业执照模糊，请重新上传清晰照片');
  await rejectBox.getByRole('button', { name: /确\s*定/ }).click();
  await page.waitForTimeout(1500);
  expect(calls.length).toBeGreaterThanOrEqual(2);
  expect(calls[1].status, '审核驳回 HTTP 状态').toBe(200);
  expect(calls[1].code, '审核驳回业务码').toBe(0);
  await expect(statusCell).toContainText('已驳回');

  expect(errors, 'console 不应有错误').toHaveLength(0);
});

test('确认授信后列表立即显示「已授信」与对应额度（≤2000 元）', async ({ page }) => {
  const calls = watchPosts(page, '/api/admin/enterprise/');
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  await login(page);
  await page.goto(ENTERPRISE);
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1000);

  const firstRow = page.locator('.el-table__body tbody tr').first();
  const customerId = Number((await firstRow.locator('td').first().innerText()).trim());
  await firstRow.getByRole('button', { name: '选择' }).click();

  const row = page.locator('.el-table__body tbody tr').filter({ hasText: String(customerId) }).first();
  const creditStateCell = row.locator('td').nth(5);
  const originalLimit = sqlNumber(
    `SELECT credit_limit_cent FROM enterprise_profile WHERE customer_id = ${customerId}`, 0);

  try {
    // 授信表单：金额 + 账期 + 生效/失效时间（新口径：最高 2000 元）
    await page.locator('.sent-panel').nth(1).getByPlaceholder('如 2000.00').fill('1500');
    const dateInputs = page.locator('.sent-panel').nth(1).locator('.el-date-editor input');
    await dateInputs.nth(0).click();
    await dateInputs.nth(0).fill('2026-11-11 00:00:00');
    await dateInputs.nth(0).press('Enter');
    await page.waitForTimeout(300);
    await dateInputs.nth(1).click();
    await dateInputs.nth(1).fill('2027-11-11 00:00:00');
    await dateInputs.nth(1).press('Enter');
    await page.waitForTimeout(300);

    await page.getByRole('button', { name: '确认授信' }).click();
    const confirmBox = page.locator('.el-message-box:visible');
    await confirmBox.locator('input').fill('确认授信');
    await confirmBox.getByRole('button', { name: '确认继续' }).click();
    await page.waitForTimeout(2000);

    const creditCall = calls.find((c) => c.url.includes('/credit'));
    expect(creditCall, '应发出授信请求').toBeTruthy();
    expect(creditCall!.status, '授信 HTTP 状态').toBe(200);
    expect(creditCall!.code, '授信业务码').toBe(0);

    // 界面必须立即从「未授信」翻成「已授信」，额度同步刷新（1500 元 = 150000 分）
    await expect(creditStateCell).toContainText('已授信');
    await expect(row).toContainText('1,500.00');
    expect(errors, 'console 不应有错误').toHaveLength(0);
  } finally {
    sql(`UPDATE enterprise_profile SET credit_limit_cent = ${originalLimit} WHERE customer_id = ${customerId};`);
  }
});

test('授信金额超过 2000 元时前端直接拦截，不发请求', async ({ page }) => {
  const calls = watchPosts(page, '/api/admin/enterprise/');
  await login(page);
  await page.goto(ENTERPRISE);
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1000);

  const firstRow = page.locator('.el-table__body tbody tr').first();
  await firstRow.getByRole('button', { name: '选择' }).click();
  await page.locator('.sent-panel').nth(1).getByPlaceholder('如 2000.00').fill('2001');
  await page.getByRole('button', { name: '确认授信' }).click();

  await expect(page.locator('.el-message--warning')).toContainText('2000');
  expect(calls.filter((c) => c.url.includes('/credit'))).toHaveLength(0);
});

test('存量企业授信全部不高于 2000 元', async ({ page }) => {
  await login(page);
  const overCap = sqlNumber('SELECT COUNT(*) FROM enterprise_profile WHERE credit_limit_cent > 200000', 1);
  expect(overCap, 'V66 迁移后不应再有超过 2000 元的企业授信').toBe(0);
});

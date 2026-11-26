/**
 * 发货工作台：按订单号搜索（2026-11-26 用户诉求，TDD 红→绿）。
 *
 * 后端 /admin/orders 早已支持 keyword，此前工作台只放了状态下拉，
 * 仓库要找某一笔待发货单只能翻页。本用例锁：搜索框存在、关键字下推到请求、
 * 结果只剩目标单、无匹配空表、清空恢复列表。纯 UI 改动，不写库。
 */
import { test, expect, type Page } from '@playwright/test';

const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

const join = (base: string, suffix: string): string =>
  `${base.replace(/\/+$/, '')}/${suffix.replace(/^\/+/, '')}`;

async function loginAdmin(page: Page): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `管理端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

async function search(page: Page, keyword: string): Promise<void> {
  const box = page.getByTestId('shipment-order-keyword');
  const waited = page.waitForResponse(
    (r) => r.url().includes('/api/admin/orders') && r.url().includes(`keyword=${keyword}`),
    { timeout: 20_000 },
  );
  await box.fill(keyword);
  await page.getByRole('button', { name: '查询' }).click();
  await waited;
}

test('发货工作台：可按订单号搜索，无匹配空表，清空恢复', async ({ page }) => {
  test.setTimeout(120_000);
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginAdmin(page);
  await page.goto(join(ADMIN_URL, '/order/shipment'));

  const rows = page.locator('.el-table__row');
  await expect(rows.first(), '发货工作台应有待发货订单').toBeVisible({ timeout: 20_000 });
  const firstNo = (await rows.first().locator('td').nth(0).innerText()).trim();
  expect(firstNo, '第 1 列应是订单号').toMatch(/^SO/);

  await expect(page.getByTestId('shipment-order-keyword'), '工作台应有订单号搜索框').toBeVisible();

  await search(page, firstNo);
  await expect(rows, `按订单号搜索应只剩 ${firstNo}`).toHaveCount(1, { timeout: 20_000 });
  await expect(rows.first()).toContainText(firstNo);

  await search(page, 'SO-NOT-EXIST-SHIPMENT-0000');
  await expect(rows, '无匹配应显示空表').toHaveCount(0, { timeout: 20_000 });

  await page.getByTestId('shipment-order-keyword').fill('');
  await page.getByRole('button', { name: '查询' }).click();
  await expect(rows.first(), '清空关键字应恢复待发货列表').toBeVisible({ timeout: 20_000 });

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

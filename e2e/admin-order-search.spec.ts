/**
 * 管理端订单列表「按订单号搜索」（TDD 红→绿，2026-11-16 用户实测）。
 *
 * 现象：管理端订单列表输入订单号点查询，一条都不出来（前端keyword 传了，后端接口/服务/Mapper 全链路都没接）。
 * 修复：Controller → Service → Mapper 补 keyword，SQL 走 order_no LIKE。
 *
 * 断言口径：
 *   1) 输入订单号查询 → 请求带 keyword，表格只剩该单；
 *   2) 不匹配的订单号 → 空表（不能把全量订单又倒出来）；
 *   3) 清空关键字再查 → 恢复多行（keyword 不再传）。
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

test('管理端订单列表可按订单号搜索，未匹配显示空表', async ({ page }) => {
  test.setTimeout(120_000);
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginAdmin(page);
  await page.goto(join(ADMIN_URL, '/order/list'));

  const rows = page.locator('.el-table__row');
  await expect(rows.first(), '订单列表应有数据').toBeVisible({ timeout: 20_000 });

  // 1) 取第一行的订单号作为关键字（默认无勾选列，第 1 列就是订单号）
  const firstNo = (await rows.first().locator('td').nth(0).innerText()).trim();
  expect(firstNo, '第 1 列应是订单号').toMatch(/^SO/);

  const keywordInput = page.getByPlaceholder('订单号');
  await expect(keywordInput, '工具栏应有订单号搜索框').toBeVisible();

  const searched = page.waitForResponse(
    (r) => r.url().includes('/api/admin/orders') && r.url().includes(`keyword=${firstNo}`),
    { timeout: 20_000 },
  );
  await keywordInput.fill(firstNo);
  await page.getByRole('button', { name: '查询' }).click();
  await searched;

  await expect(rows, '按订单号搜索应只剩 1 单').toHaveCount(1, { timeout: 20_000 });
  await expect(rows.first()).toContainText(firstNo);

  // 2) 不匹配的订单号 → 空表，而不是把全量倒出来
  const missed = page.waitForResponse(
    (r) => r.url().includes('/api/admin/orders') && r.url().includes('keyword=SO-NOT-EXIST-0000'),
    { timeout: 20_000 },
  );
  await keywordInput.fill('SO-NOT-EXIST-0000');
  await page.getByRole('button', { name: '查询' }).click();
  await missed;
  await expect(rows, '未匹配应显示空表').toHaveCount(0, { timeout: 20_000 });

  // 3) 清空关键字 → 恢复列表（关键字为空时不再带 keyword）
  const cleared = page.waitForResponse(
    (r) => r.url().includes('/api/admin/orders') && !r.url().includes('keyword='),
    { timeout: 20_000 },
  );
  await keywordInput.fill('');
  await page.getByRole('button', { name: '查询' }).click();
  await cleared;
  await expect(rows.first(), '清空搜索后应恢复订单列表').toBeVisible({ timeout: 20_000 });

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

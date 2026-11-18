/**
 * 商城端三处跳转/导航修正（TDD 红→绿）：
 *   A) 团购专区「定制专属方案」→ 跳在线客服 /chat（不再跳 /custom 订单列表）
 *   D) 顶栏「我的订单」hover 下拉 → 商品订单 /orders 与 定制订单 /custom
 *   E) 下单支付成功 → 点「查看订单」直接进该订单详情 /order/:orderNo
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

const pathOf = (page: Page): string => new URL(page.url()).pathname;
const attachNoise = (page: Page, errors: string[]) => {
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });
};

test('A: 定制专属方案跳在线客服而非订单列表', async ({ page }) => {
  const errors: string[] = [];
  attachNoise(page, errors);
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/group-buy`);
  await expect(page.locator('.group-buy').first()).toBeVisible({ timeout: 20_000 });
  await page.getByText('定制专属方案', { exact: true }).click();
  await expect.poll(() => pathOf(page), { timeout: 15_000 }).toBe('/chat');
  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

test('D: 顶栏我的订单 hover 下拉含商品订单与定制订单', async ({ page }) => {
  const errors: string[] = [];
  attachNoise(page, errors);
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
  const entry = page.locator('.topbar__orders');
  await expect(entry, '顶栏应有我的订单下拉入口').toBeVisible({ timeout: 15_000 });
  await entry.hover();
  const menu = page.locator('.topbar__orders-menu');
  await expect(menu).toBeVisible({ timeout: 10_000 });
  await expect(menu.locator('[data-nav="goods-orders"]')).toHaveText('商品订单');
  await expect(menu.locator('[data-nav="custom-orders"]')).toHaveText('定制订单');
  await menu.locator('[data-nav="goods-orders"]').click();
  await expect.poll(() => pathOf(page), { timeout: 15_000 }).toBe('/orders');
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
  const entry2 = page.locator('.topbar__orders');
  await entry2.hover();
  await entry2.locator('[data-nav="custom-orders"]').click();
  await expect.poll(() => pathOf(page), { timeout: 15_000 }).toBe('/custom');
  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

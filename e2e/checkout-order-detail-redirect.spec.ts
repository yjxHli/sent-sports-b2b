/**
 * 支付成功「查看订单」跳订单详情（TDD 红→绿）。
 *
 * 用户诉求（2026-11-16）：购买商品完成应直接跳到该商品订单详情页，
 * 而非跳到「我的订单」列表。落地为：支付成功弹窗「查看订单」→ /order/:orderNo。
 */
import { test, expect, type Page } from '@playwright/test';
import { pickSellableSku } from './pick-product';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const pathOf = (page: Page): string => new URL(page.url()).pathname;

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
  expect(body?.code).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('支付成功点「查看订单」跳到该订单详情页', async ({ page }) => {
  await loginUser(page);

  // 选一个有货 SKU 立即购买：别取「列表第一个商品」——演示库存被 E2E 买空后它可能没有可点规格
  const picked = await pickSellableSku(page.request, 2);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/product/${picked.productId}`);
  await expect(page).toHaveURL(/\/product\/\d+/, { timeout: 15_000 });
  const sku = page.locator('.spec-item').filter({ hasText: picked.spec }).first();
  await sku.click();
  await page.getByRole('button', { name: '立即购买' }).click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 15_000 });

  // 下单并支付
  await page.getByRole('button', { name: '提交订单并支付' }).click();
  const paidDialog = page.locator('.el-dialog').filter({ hasText: '支付成功' });
  await expect(paidDialog).toBeVisible({ timeout: 40_000 });
  const matched = /订单号[：:]\s*([A-Za-z0-9_-]+)/.exec(await paidDialog.innerText());
  expect(matched, '应从支付弹窗解析订单号').not.toBeNull();
  const orderNo = matched![1];

  // 关键：查看订单应进入该订单详情
  await paidDialog.getByRole('button', { name: '查看订单' }).click();
  await expect.poll(() => pathOf(page), { timeout: 15_000 }).toBe(`/order/${orderNo}`);
  await expect(page.locator('.custom-detail, .od-addr, .order-detail').first()).toBeVisible({
    timeout: 15_000,
  });
});

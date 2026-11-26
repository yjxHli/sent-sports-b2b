/**
 * 支付成功后两处落点回归（2026-11-21 用户反馈）：
 *
 *   1) 「购买商品 → 订单详情 → 点【返回我的订单】」回到了「确认订单」页，中间还挂着「去选购」。
 *      根因：详情页返回用 `router.back()`，而历史栈是「结算页 → 订单详情」，
 *      回退必然回到结算页；此时购物车行已下单清空，结算页只能渲染空态。
 *      口径：这个按钮写的是「返回我的订单」，就必须落到 `/orders`。
 *
 *   2) 「购物车结算 → 支付成功」弹窗背后是空白的确认订单页（中间一个「去选购」）。
 *      根因：下单成功后清空购物车已选行，结算页 `items` 变空 → 空态；
 *      支付成功弹窗只是盖在上面，用户看到的是「空结算页 + 去选购」。
 *      口径：支付成功后弹窗背后仍应保留本次下单的商品行，不得退化成空态。
 */
import { test, expect, type Page } from '@playwright/test';
import { pickSellableSku } from './pick-product';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const pathOf = (page: Page): string => new URL(page.url()).pathname;

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(?:\?|$)/, { timeout: 20_000 });
}

/** 支付成功弹窗 → 解析订单号 */
async function waitPaidDialog(page: Page): Promise<{ dialog: ReturnType<Page['locator']>; orderNo: string }> {
  const dialog = page.locator('.el-dialog').filter({ hasText: '支付成功' });
  await expect(dialog, '下单/支付失败：未出现「支付成功」弹窗').toBeVisible({ timeout: 40_000 });
  const matched = /订单号[：:]\s*([A-Za-z0-9_-]+)/.exec(await dialog.innerText());
  expect(matched, '应从支付弹窗解析订单号').not.toBeNull();
  return { dialog, orderNo: matched![1] };
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('「立即购买」下单后，订单详情点「返回我的订单」必须落到我的订单列表', async ({ page }) => {
  test.setTimeout(180_000);
  await loginUser(page);

  // 复刻用户路径：商品页 → 立即购买 → 确认订单 → 提交并支付 → 查看订单 → 详情
  const picked = await pickSellableSku(page.request, 1);
  await page.goto(`${USER_URL}/product/${picked.productId}`);
  const sku = page.locator('.spec-item:not(.disabled)').filter({ hasText: picked.spec }).first();
  await expect(sku, `商品 ${picked.productId} 应有可售规格`).toBeVisible({ timeout: 20_000 });
  await sku.click();
  await page.locator('.buy-btn--now').click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 20_000 });
  await expect(page.locator('.quote-line--total .sent-price')).toHaveText(/¥\d/, { timeout: 20_000 });

  await page.getByRole('button', { name: '提交订单并支付' }).click();
  const { dialog, orderNo } = await waitPaidDialog(page);

  await dialog.getByRole('button', { name: '查看订单' }).click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toBe(`/order/${orderNo}`);
  await expect(page.locator('.od-actions')).toBeVisible({ timeout: 20_000 });

  // 关键断言：按钮写的是「返回我的订单」，就得到 /orders；不能回退到确认订单页
  await page.locator('.od-bar__back').click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toBe('/orders');
  await expect(page.locator('.order-tabs'), '我的订单列表应有状态 Tab').toBeVisible({ timeout: 20_000 });
  await expect(page.getByRole('button', { name: '去选购' }), '不应回落到确认订单空态').toHaveCount(0);
});

test('购物车结算支付成功后，成功弹窗背后仍保留本次下单的商品行', async ({ page }) => {
  test.setTimeout(180_000);
  await loginUser(page);

  // 演示购物车常被其它用例塞满，先清空再只加一件，避免一次把历史商品全下单
  await page.goto(`${USER_URL}/cart`);
  await expect(page.locator('.cart-head__count')).toBeVisible({ timeout: 20_000 });
  if ((await page.locator('.cart-row').count()) > 0) {
    const selectAll = page.locator('.cart-toolbar .el-checkbox');
    const checked = await selectAll.evaluate((el) => el.classList.contains('is-checked'));
    if (!checked) await selectAll.click();
    await page.locator('.cart-toolbar .link-btn--danger').click();
    await expect(page.locator('.cart-row')).toHaveCount(0, { timeout: 20_000 });
  }

  const picked = await pickSellableSku(page.request, 1);
  await page.goto(`${USER_URL}/product/${picked.productId}`);
  const sku = page.locator('.spec-item:not(.disabled)').filter({ hasText: picked.spec }).first();
  await expect(sku, `商品 ${picked.productId} 应有可售规格`).toBeVisible({ timeout: 20_000 });
  await sku.click();
  await page.locator('.buy-btn--cart').click();
  await expect(page.locator('.el-message').filter({ hasText: '已加入购物车' })).toBeVisible({ timeout: 15_000 });

  await page.goto(`${USER_URL}/cart`);
  await expect(page.locator('.cart-row').first()).toBeVisible({ timeout: 20_000 });
  if (await page.getByRole('button', { name: /^结算/ }).isDisabled()) {
    await page.locator('.cart-row__check').first().click();
  }
  await page.getByRole('button', { name: /^结算/ }).click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 20_000 });
  await expect(page.locator('.quote-line--total .sent-price')).toHaveText(/¥\d/, { timeout: 20_000 });

  const lineBefore = (await page.locator('.co-line').first().innerText()).trim();
  expect(lineBefore, '结算页应有商品行').not.toBe('');

  await page.getByRole('button', { name: '提交订单并支付' }).click();
  const { dialog } = await waitPaidDialog(page);

  // 弹窗背后的结算页不能退化成空态：商品行还在、没有「去选购」
  await expect(page.locator('.co-line').first(), '支付成功弹窗背后应保留已下单商品行').toBeVisible();
  await expect(page.locator('.co-line').first()).toContainText(lineBefore.split('\n')[0].trim());
  await expect(page.getByRole('button', { name: '去选购' }), '不应出现空购物车的「去选购」').toHaveCount(0);

  await dialog.getByRole('button', { name: '查看订单' }).click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toMatch(/^\/order\/SO\d+/);
});

/**
 * 积分抵扣纯 UI 闭环（替换旧的 API 注入用例）：
 * 登录 → 购物车结算 → 勾选「使用积分」→ 输入积分数 → 核对试算抵扣 → 提交 → 详情页核对
 * → 支付成功后核对「积分余额刚好减少抵扣额」。
 *
 * 断言要点：
 *   1) 开关的可用积分来自后端下发，输入框上限不得超过该余额；
 *   2) 试算抵扣额来自后端 checkout-preview，且受 30% 上限与积分余额双重约束；
 *   3) 结算页展示、下单落库、订单详情页展示三处金额逐字一致；
 *   4) 支付成功即真实扣减：/api/member/points 的余额必须<b>刚好</b>减少抵扣额（多扣少扣都是 bug）。
 */
import { test, expect, type Page } from '@playwright/test';
import { pickSellableSku } from './pick-product';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const USER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

/** 取当前登录态 access token（用户端前缀 sent_user，按后缀匹配避免写死环境变量）。 */
async function accessToken(page: Page): Promise<string> {
  const raw = await page.evaluate(
    () => Object.entries(localStorage).find(([k]) => k.includes('access_token'))?.[1] ?? '',
  );
  return String(raw).replace(/^"|"$/g, '');
}

/** 经 /api/member/points 读真实积分余额（与页面同一账号、同一令牌）。 */
async function pointsBalance(page: Page): Promise<number> {
  const res = await page.request.get(`${API_URL}/member/points`, {
    headers: { Authorization: `Bearer ${await accessToken(page)}` },
  });
  const body: any = await res.json().catch(() => null);
  expect(body?.code, `查询积分余额失败：${JSON.stringify(body)}`).toBe(0);
  return Number(body.data.availablePoints);
}

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(USER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(USER_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

/** 商品详情 → 加入购物车 → 购物车页「结算」进入结算页（全程 UI 操作） */
async function gotoCartCheckout(page: Page): Promise<void> {
  // 演示购物车会被其它用例反复加购，先清空，否则一次把几十件历史商品都下单必然撞库存/下架
  await page.goto(`${USER_URL}/cart`);
  await expect(page.locator('.cart-head__count')).toBeVisible({ timeout: 20_000 });
  if (await page.locator('.cart-row').count() > 0) {
    const selectAll = page.locator('.cart-toolbar .el-checkbox');
    const checked = await selectAll.evaluate((el) => el.classList.contains('is-checked'));
    if (!checked) await selectAll.click();
    await page.locator('.cart-toolbar .link-btn--danger').click();
    await expect(page.locator('.cart-row')).toHaveCount(0, { timeout: 20_000 });
  }

  const picked = await pickSellableSku(page.request, 2);
  await page.goto(`${USER_URL}/product/${picked.productId}`);
  const sku = page.locator('.spec-item:not(.disabled)').filter({ hasText: picked.spec }).first();
  await expect(sku, `商品 ${picked.productId} 应有可售规格`).toBeVisible({ timeout: 20_000 });
  await sku.click();
  await page.getByRole('button', { name: '加入购物车' }).click();
  await expect(page.locator('.el-message').filter({ hasText: '已加入购物车' })).toBeVisible({
    timeout: 15_000,
  });

  await page.goto(`${USER_URL}/cart`);
  await expect(page.locator('.cart-row').first()).toBeVisible({ timeout: 20_000 });
  if (await page.getByRole('button', { name: /^结算/ }).isDisabled()) {
    await page.locator('.cart-row__check').first().click();
  }
  await page.getByRole('button', { name: /^结算/ }).click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 20_000 });
  await expect(page.locator('.quote-line--total .sent-price')).toHaveText(/¥\d/, { timeout: 20_000 });
}

test('购物车结算勾选积分抵扣：试算金额、提交金额与订单详情一致', async ({ page }) => {
  test.setTimeout(180_000);
  await loginUser(page);
  await gotoCartCheckout(page);

  // 未勾选时不应出现抵扣行；开关必须可用（当前账号有积分余额）
  await expect(page.getByTestId('checkout-point-deduct')).toHaveCount(0);
  const switcher = page.getByTestId('checkout-use-points');
  await expect(switcher).toBeVisible();
  await expect(switcher).toBeEnabled();
  const hint = page.getByTestId('checkout-points-hint');
  await expect(hint).toContainText('30%');
  await expect(hint).toContainText(/可用\s*\d+/);

  // 支付前余额（同账号、同令牌读后端），用于验「支付成功真的扣了、且只扣这么多」
  const balanceBefore = await pointsBalance(page);
  expect(balanceBefore, '演示账号必须有可抵扣积分').toBeGreaterThan(0);

  await switcher.click();
  const inputWrap = page.getByTestId('checkout-points-input');
  await expect(inputWrap).toBeVisible();
  const maxPoints = Number(await inputWrap.getAttribute('data-max'));
  expect(maxPoints, '输入框上限必须等于后端下发的可用积分').toBeGreaterThan(0);
  const usePoints = Math.min(50, maxPoints);

  // 用后端真实试算结果做断言基准，避免前端重复实现 30% 规则
  const previewResp = page.waitForResponse((r) => {
    if (!r.url().includes('/api/cart/checkout-preview') || r.request().method() !== 'POST') return false;
    try {
      return Number(JSON.parse(r.request().postData() ?? '{}').availablePoints) === usePoints;
    } catch {
      return false;
    }
  }, { timeout: 20_000 });
  const input = inputWrap.locator('input');
  await input.fill(String(usePoints));
  await input.press('Enter');
  const preview: any = await (await previewResp).json().catch(() => null);
  expect(preview?.code, `试算失败：${JSON.stringify(preview)}`).toBe(0);

  const goodsCent = Number(preview.data.goodsAmountCent);
  const deductCent = Number(preview.data.pointDeductCent);
  expect(deductCent, '勾选后必须真的产生积分抵扣').toBeGreaterThan(0);
  expect(deductCent, '抵扣不得超过用户提交的积分数').toBeLessThanOrEqual(usePoints);
  expect(deductCent, '抵扣不得超过商品额 30%').toBeLessThanOrEqual(Math.floor(goodsCent * 30 / 100));

  const deductText = `-¥${preview.data.pointDeductYuan}`;
  const deductRow = page.getByTestId('checkout-point-deduct');
  await expect(deductRow).toBeVisible();
  expect((await deductRow.innerText()).trim(), '结算页展示金额必须等于后端试算金额').toContain(deductText);
  await expect(page.locator('.quote-line--total .sent-price')).toHaveText(`¥${preview.data.payableYuan}`);

  // 前端在 /payments 返回后 1.2s 才「扮演渠道」回放签名参数，支付成功弹窗早于回调落地。
  // 要验余额，必须等这次异步通知真的打完（否则读到的是回调前的余额）。
  const paidCallback = page.waitForResponse(
    (r) => r.url().includes('/api/payment/mock/callback') && r.request().method() === 'POST',
    { timeout: 40_000 },
  );
  await page.getByRole('button', { name: '提交订单并支付' }).click();
  const paidDialog = page.locator('.el-dialog').filter({ hasText: '支付成功' });
  await expect(paidDialog, '下单/支付失败').toBeVisible({ timeout: 40_000 });
  const matched = /订单号[：:]\s*([A-Za-z0-9_-]+)/.exec(await paidDialog.innerText());
  expect(matched, '应从支付弹窗解析订单号').not.toBeNull();
  const orderNo = matched![1];
  await paidDialog.getByRole('button', { name: '查看订单' }).click();
  await expect(page).toHaveURL(new RegExp(`/order/${orderNo}`), { timeout: 20_000 });

  const detailRow = page.locator('.od-row').filter({ hasText: '积分抵扣' });
  await expect(detailRow).toBeVisible({ timeout: 20_000 });
  expect((await detailRow.innerText()).trim(), '详情页金额必须与结算页、后端落库一致').toContain(deductText);

  // 支付成功要真实扣减余额：差额必须刚好等于本单抵扣额（1 积分 = 1 分）
  const callbackBody: any = await (await paidCallback).json().catch(() => null);
  expect(callbackBody?.code, `支付回调失败：${JSON.stringify(callbackBody)}`).toBe(0);
  const balanceAfter = await pointsBalance(page);
  expect(balanceBefore - balanceAfter, '支付成功后积分余额必须刚好减少抵扣额').toBe(deductCent);
  expect(balanceAfter).toBeGreaterThanOrEqual(0);
});

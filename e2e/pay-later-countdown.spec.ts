/**
 * 商城「暂不支付」+ 5 分钟支付倒计时（2026-11-17 用户第 2 轮反馈 4，TDD）。
 *
 * 用户原话：「商城端支付商品的时候，可以选择暂不支付，5 分钟之内未完成支付的话就会取消订单，
 * 并且会有 5 分钟的实时倒计时提醒」。
 *
 * 断言口径：
 *   1) 结算页有「暂不支付」入口，点了只下单（不弹支付成功、不发起支付）；
 *   2) 订单详情页显示倒计时 `mm:ss`，且秒数在真实递减（不是静态文案）；
 *   3) 倒计时的基准是后端 `expireAtMillis`（下单时间 + 5 分钟），不是前端自己算的；
 *   4) 「我的订单 - 待付款」卡片同样带倒计时，不必进详情；
 *   5) 收尾按业务接口取消订单，释放预占库存（别裸改 sku_stock）。
 */
import { test, expect, type Page } from '@playwright/test';
import { pickSellableSku } from './pick-product';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088').replace(/\/+$/, '');
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;
/** 支付窗口 5 分钟，与后端 CreateOrderUseCase.PAY_TIMEOUT_MINUTES 同口径 */
const PAY_WINDOW_MS = 5 * 60_000;

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
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

async function tokenOf(page: Page): Promise<string> {
  return page.evaluate(
    () => Object.entries(localStorage).find(([k]) => k.includes('access_token'))?.[1] ?? '',
  );
}

/** 商品页 → 立即购买 → 结算页（等到取价完成，否则提交会弹「订单信息暂不可用」） */
async function gotoCheckout(page: Page): Promise<void> {
  const picked = await pickSellableSku(page.request, 1);
  await page.goto(`${USER_URL}/product/${picked.productId}`);
  const firstSku = page.locator('.spec-item:not(.disabled)').first();
  await expect(firstSku, `商品 ${picked.productId} 应有可售规格`).toBeVisible({ timeout: 20_000 });
  await firstSku.click();
  await page.locator('.buy-btn--now').click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 20_000 });
  await expect(page.locator('.quote-line--total .sent-price'), '结算页应完成取价').toHaveText(
    /¥\d/,
    { timeout: 20_000 },
  );
  // 下单必带地址：地址簿为空时先造一条（演示库通常已有默认地址）
  const chosen = page.locator('.co-addr__item--on');
  if ((await chosen.count()) === 0) {
    const token = await tokenOf(page);
    const res = await page.request.post(`${API_URL}/api/addresses`, {
      data: {
        receiver: '倒计时验收', phone: '13700137001', province: '江苏省', city: '南京市',
        district: '玄武区', detail: '龙蟠路 1 号 森特大厦', isDefault: true,
      },
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    });
    const body: any = await res.json().catch(() => null);
    expect(body?.code, `创建收货地址失败：${JSON.stringify(body)}`).toBe(0);
    await page.reload();
    await expect(page.locator('.co-addr__item--on'), '应自动选中默认地址').toHaveCount(1, {
      timeout: 20_000,
    });
  }
}

/** mm:ss → 秒 */
function secondsOf(text: string): number {
  const m = /(\d{2}):(\d{2})/.exec(text);
  expect(m, `倒计时文案格式应为 mm:ss，实际：${text}`).not.toBeNull();
  if (!m) return -1;
  return Number(m[1]) * 60 + Number(m[2]);
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('结算可「暂不支付」：详情页与待付款列表都有 5 分钟实时倒计时', async ({ page }) => {
  test.setTimeout(240_000);
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await gotoCheckout(page);

  // 1) 两个入口都在
  const payNowBtn = page.getByRole('button', { name: '提交订单并支付' });
  const payLaterBtn = page.getByRole('button', { name: '暂不支付' });
  await expect(payNowBtn, '结算页应有「提交订单并支付」').toBeVisible({ timeout: 15_000 });
  await expect(payLaterBtn, '结算页应有「暂不支付」').toBeVisible();

  let orderNo = '';
  try {
    // 2) 点「暂不支付」：只下单，不发起支付
    let payCalls = 0;
    page.on('request', (r) => {
      if (r.url().includes('/api/payments') && r.method() === 'POST') payCalls += 1;
    });
    const createResp = page.waitForResponse(
      (r) => r.url().endsWith('/api/orders') && r.request().method() === 'POST',
      { timeout: 30_000 },
    );
    await payLaterBtn.click();
    const created: any = await (await createResp).json().catch(() => null);
    expect(created?.code, `下单失败：${JSON.stringify(created)}`).toBe(0);
    orderNo = String(created?.data?.orderNo ?? '');
    expect(orderNo, '下单应返回订单号').toMatch(/^SO/);

    // 跳详情页，且没有「支付成功」弹窗
    await expect(page).toHaveURL(new RegExp(`/order/${orderNo}`), { timeout: 20_000 });
    await expect(page.locator('.el-dialog').filter({ hasText: '支付成功' })).toHaveCount(0);
    expect(payCalls, '「暂不支付」不得发起支付请求').toBe(0);

    // 3) 详情页倒计时：格式 mm:ss、不超过 5 分钟、真的在走
    const countdown = page.getByTestId('pay-countdown');
    await expect(countdown, '待付款订单必须显示 5 分钟倒计时').toBeVisible({ timeout: 20_000 });
    const firstText = (await countdown.innerText()).trim();
    const firstSeconds = secondsOf(firstText);
    expect(firstSeconds, `倒计时应在 5 分钟内：${firstText}`).toBeGreaterThan(0);
    expect(firstSeconds).toBeLessThanOrEqual(300);

    await page.waitForTimeout(3200);
    const secondText = (await countdown.innerText()).trim();
    const secondSeconds = secondsOf(secondText);
    expect(secondSeconds, `倒计时应递减：${firstText} → ${secondText}`).toBeLessThan(firstSeconds);

    // 4) 基准来自后端 expire_at：下单时间 + 5 分钟
    const token = await tokenOf(page);
    const detailRes = await page.request.get(`${API_URL}/api/orders/${orderNo}`, {
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    });
    const detail: any = await detailRes.json().catch(() => null);
    const expireAt = Number(detail?.data?.expireAtMillis ?? 0);
    const createdAt = Number(detail?.data?.createdAt ?? 0);
    expect(expireAt, '详情接口应回传支付截止时间').toBeGreaterThan(0);
    const windowMs = expireAt - createdAt;
    expect(
      Math.abs(windowMs - PAY_WINDOW_MS),
      `支付窗口应正好 5 分钟，实际 ${windowMs} ms`,
    ).toBeLessThanOrEqual(5000);

    // 5) 订单列表「待付款」卡片也带倒计时
    await page.goto(`${USER_URL}/orders?status=WAIT_PAY`);
    const card = page.locator('.order-card', { hasText: orderNo }).first();
    await expect(card, `待付款列表应有订单 ${orderNo}`).toBeVisible({ timeout: 20_000 });
    await expect(card.getByTestId('pay-countdown'), '待付款卡片应显示倒计时')
      .toContainText(/\d{2}:\d{2}/, { timeout: 15_000 });

    expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
  } finally {
    // 收尾：走业务接口取消，释放预占库存（不裸改 sku_stock）
    if (orderNo) {
      const token = await tokenOf(page);
      await page.request.post(`${API_URL}/api/orders/${orderNo}/cancel`, {
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      });
    }
  }
});

/**
 * 用户反馈（2026-11-18）：「暂不支付」的订单在列表点「去支付」应进支付界面，
 * 而不是当场提示支付成功。TDD：先断言跳转与「此刻没有支付请求」，再实现支付页。
 */
test('待付款订单从列表「去支付」跳支付页，确认后才提示支付成功', async ({ page }) => {
  test.setTimeout(240_000);

  await loginUser(page);
  await gotoCheckout(page);

  let orderNo = '';
  try {
    const createdResp = page.waitForResponse(
      (r) => r.url().endsWith('/api/orders') && r.request().method() === 'POST',
      { timeout: 30_000 },
    );
    await page.getByRole('button', { name: '暂不支付' }).click();
    const created: any = await (await createdResp).json().catch(() => null);
    expect(created?.code).toBe(0);
    orderNo = String(created?.data?.orderNo ?? '');
    expect(orderNo).toMatch(/^SO/);

    // 详情页「去支付」同样只进支付页，不在详情页直接扣款
    await expect(page).toHaveURL(new RegExp(`/order/${orderNo}`), { timeout: 20_000 });
    await page.getByRole('button', { name: '去支付' }).click();
    await expect(page).toHaveURL(new RegExp(`/pay/${orderNo}`), { timeout: 20_000 });
    await expect(page.locator('.el-dialog').filter({ hasText: '支付成功' })).toHaveCount(0);

    await page.goto(`${USER_URL}/orders?status=WAIT_PAY`);
    const card = page.locator('.order-card', { hasText: orderNo }).first();
    await expect(card).toBeVisible({ timeout: 20_000 });

    // 点「去支付」时不能已经发支付请求，也不能弹「支付成功」
    let payCalls = 0;
    page.on('request', (r) => {
      if (r.url().includes('/api/payments') && r.method() === 'POST') payCalls += 1;
    });
    await card.getByRole('button', { name: '去支付' }).click();
    await expect(page, '应跳到独立支付界面，而不是留在列表提示支付成功')
      .toHaveURL(new RegExp(`/pay/${orderNo}`), { timeout: 20_000 });
    expect(payCalls, '进入支付界面时不得已经发起支付').toBe(0);
    await expect(page.locator('.el-dialog').filter({ hasText: '支付成功' })).toHaveCount(0);
    await expect(page.getByTestId('pay-order-no')).toContainText(orderNo);
    await expect(page.getByTestId('pay-amount')).toContainText(/¥\d/);

    await page.getByTestId('pay-submit').click();
    await expect(page.getByTestId('pay-success'), '确认支付后才应显示支付成功')
      .toBeVisible({ timeout: 40_000 });
    expect(payCalls, '确认支付后必须发起一次支付请求').toBe(1);
  } finally {
    // 若中途失败订单可能仍未支付，按业务接口取消释放预占库存
    if (orderNo) {
      const token = await tokenOf(page);
      await page.request.post(`${API_URL}/api/orders/${orderNo}/cancel`, {
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      });
    }
  }
});

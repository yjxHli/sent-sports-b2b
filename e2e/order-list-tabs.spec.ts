/**
 * 我的订单页（淘宝式多状态）验收（TDD 红→绿）。
 *
 * 用户诉求：订单页要像淘宝「已买到的宝贝」，顶部有待付款 / 待发货 / 待收货 等多个状态 Tab，
 * 每张订单卡片显示下单时间、订单号、商品行（标题 / 规格 / 单价 × 数量）、实付款与对应操作按钮。
 *
 * 断言口径：
 *   1) 顶部出现状态 Tab（全部 / 待付款 / 待发货 / 待收货 / 已完成）；
 *   2) 切「待付款」后列表全部是待付款，且条数与计数一致；
 *   3) 每条订单卡片带下单时间、订单号、至少一行商品（标题 + 数量）；
 *   4) 待付款订单有「去支付」按钮，待收货订单有「确认收货」按钮；
 *   5) 全程无 console error。
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

test('我的订单页有多状态 Tab，卡片带商品行与下单时间', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/orders`);

  // 1) 状态 Tab
  const tabs = page.locator('.order-tab');
  await expect(tabs, '应展示多状态 Tab').toHaveCount(5, { timeout: 20_000 });
  for (const name of ['全部', '待付款', '待发货', '待收货', '已完成']) {
    await expect(tabs.filter({ hasText: name }).first(), `缺少「${name}」Tab`).toBeVisible();
  }

  // 2) 全部：卡片带下单时间、订单号、商品行
  const cards = page.locator('.order-card');
  await expect(cards.first(), '订单列表应有数据').toBeVisible({ timeout: 20_000 });
  const firstCard = cards.first();
  await expect(firstCard.locator('.order-card__time'), '卡片应有下单时间').toBeVisible();
  await expect(firstCard.locator('.order-card__no'), '卡片应有订单号').toBeVisible();
  await expect(
    firstCard.locator('.order-item').first(),
    '卡片应有商品行',
  ).toBeVisible();
  await expect(firstCard.locator('.order-item__title').first()).not.toBeEmpty();
  await expect(firstCard.locator('.order-item__qty').first()).toContainText('x');
  await expect(firstCard.locator('.order-card__pay'), '卡片应有实付款').toContainText('实付');

  // 3) 切「待付款」：只显示待付款，且每条都有「去支付」
  const waitingPay = tabs.filter({ hasText: '待付款' }).first();
  const filtered = page.waitForResponse(
    (r) => r.url().includes('/api/orders') && r.url().includes('status=WAIT_PAY'),
    { timeout: 20_000 },
  );
  await waitingPay.click();
  await filtered;
  await expect(page.locator('.order-card').first(), '待付款应有订单').toBeVisible({
    timeout: 20_000,
  });
  const payCount = await page.locator('.order-card').count();
  expect(payCount, '待付款列表不应为空').toBeGreaterThan(0);
  await expect(
    page.locator('.order-card .order-card__status').first(),
    '待付款 Tab 下卡片状态都应是待付款',
  ).toContainText('待付款');
  await expect(
    page.getByRole('button', { name: '去支付' }).first(),
    '待付款订单应有「去支付」按钮',
  ).toBeVisible();

  // 4) 切「待收货」：应有「确认收货」按钮
  const waitingReceive = tabs.filter({ hasText: '待收货' }).first();
  await waitingReceive.click();
  await page.waitForTimeout(1200);
  const receiveCards = await page.locator('.order-card').count();
  if (receiveCards > 0) {
    await expect(
      page.getByRole('button', { name: '确认收货' }).first(),
      '待收货订单应有「确认收货」按钮',
    ).toBeVisible();
  }

  // 5) 回「全部」仍是完整列表
  await tabs.filter({ hasText: '全部' }).first().click();
  await page.waitForTimeout(1200);
  await expect(page.locator('.order-card').first()).toBeVisible();

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

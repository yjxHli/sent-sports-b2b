/**
 * 商品页：可售库存显示真实值 + 超库存拦截（TDD 红→绿）。
 *
 * 用户诉求（2026-11-16 复述）：
 *   1) 可售库存显示真实可售数（7 件就显示 7），不再随已选数量递减——
 *      否则「可售库存 6 / 库存 7 件」两个数打架，用户以为只能买 6；
 *   2) 输入数量超过真实库存要提示库存不够且无法下单（输入 8 拦截，7 可买光）。
 *
 * 2026-11-16 18:20 文案调整：数量旁的「库存 N 件」不再重复顶部可售数，
 * 改成红色的「仅剩 N 件」（仅提示购买上限），故这里的正则取 `仅剩 N 件`。
 */
import { test, expect, type Page } from '@playwright/test';
import { pickLowStockSku, pickSellableSku } from './pick-product';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
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

/** 从「可售库存」标签读取数字 */
async function availableStock(page: Page): Promise<number> {
  const text = await page.locator('.buy__meta').getByText('可售库存').locator('..').innerText();
  return Number(/可售库存\s*(\d+)/.exec(text.replace(/\s+/g, ' '))?.[1] ?? -1);
}

async function openStockProduct(page: Page): Promise<{ qtyInput: any; totalStock: number }> {
  // 别取「列表第一个商品」：演示库存会被本套 E2E 买空，首个商品可能一个可点规格都没有（假红）。
  // 统一现场挑可售 ≥2 的规格，再按规格文案点选。
  const picked = await pickSellableSku(page.request, 2);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/product/${picked.productId}`);
  await expect(page).toHaveURL(/\/product\/\d+/, { timeout: 15_000 });
  await page.locator('.spec-item').filter({ hasText: picked.spec }).first().click();

  const qtyInput = page.locator('.buy__qty input').first();
  // 取数只用顶部「可售库存」：数量旁的「仅剩 N 件」在超库存时会被隐藏，不适合当数据源
  const totalStock = await availableStock(page);
  expect(totalStock, '需要一个有货规格').toBeGreaterThan(1);
  return { qtyInput, totalStock };
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('可售库存显示真实库存且固定，超库存输入被拦截并提示', async ({ page }) => {
  await loginUser(page);
  const { qtyInput, totalStock } = await openStockProduct(page);

  // 1) 可售库存 = 真实库存，不随已选数量递减（默认 qty=1）
  await expect
    .poll(async () => availableStock(page), { timeout: 10_000, message: '可售库存应显示真实库存值' })
    .toBe(totalStock);

  // 1b) 数量旁只写一次「仅剩 N 件」，不再重复「库存 N 件」
  await expect(page.locator('.buy__qty-hint'), '数量旁应提示「仅剩 N 件」').toHaveText(
    `仅剩 ${totalStock} 件`,
  );

  // 2) 数量调到 2，可售库存保持不变（不再递减）
  await qtyInput.fill('2');
  await qtyInput.blur();
  await expect
    .poll(async () => availableStock(page), { timeout: 10_000, message: '可售库存不应随购买量变化' })
    .toBe(totalStock);
  await expect(page.locator('.buy__qty-hint')).toHaveText(`仅剩 ${totalStock} 件`);

  // 3) 输入超过真实库存 → 提示库存不足 + 购买按钮禁用
  await qtyInput.fill(String(totalStock + 1));
  await qtyInput.blur();
  await expect(page.locator('.stock-alert'), '超库存应提示库存不足').toBeVisible({ timeout: 10_000 });
  await expect(page.getByRole('button', { name: '立即购买' })).toBeDisabled();
  await expect(page.getByRole('button', { name: '加入购物车' })).toBeDisabled();

  // 3b) 警示块里已写明可售数，「仅剩 N 件」让位隐藏，避免两处红字挤在一起
  await expect(page.locator('.buy__qty-hint')).toHaveCount(0);

  // 4) 超库存的输入值被保留（不被 el-input-number 静默钳制），用户能看到自己输入了几件
  await expect(qtyInput, '超库存输入应保留原值而非被钳制').toHaveValue(String(totalStock + 1));
});

test('定制起订量超过可用库存时无法购买并提示', async ({ page }) => {
  await loginUser(page);
  // 这条要的是「可售 < 定制起订 30」的规格：现场挑一个低库存的，
  // 而不是指望「列表第一个商品」刚好低库存（演示库存漂移后它会直接跳过，等于白跑）
  const low = await pickLowStockSku(page.request, 30);
  test.skip(!low, '演示库里没有 0<可售<30 的规格，跳过定制起订量拦截断言');
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/product/${low!.productId}`);
  await expect(page).toHaveURL(/\/product\/\d+/, { timeout: 15_000 });
  await page.locator('.spec-item').filter({ hasText: low!.spec }).first().click();

  const specs = page.locator('.spec-item:not(.disabled)');
  let lowStockFound = false;
  for (let i = 0; i < (await specs.count()); i++) {
    await specs.nth(i).click();
    // 同 openStockProduct：库存从顶部「可售库存」读，别读会被隐藏的「仅剩 N 件」
    const stock = await availableStock(page);
    if (stock > 0 && stock < 30) {
      lowStockFound = true;
      break;
    }
  }
  test.skip(!lowStockFound, '当前商品无库存<30的规格，跳过定制拦截断言');

  await page.locator('.custom-panel__check').click();
  await expect(page.locator('.stock-alert'), '超库存应提示').toBeVisible({ timeout: 10_000 });
  await expect(page.getByRole('button', { name: '立即购买' })).toBeDisabled();
  await expect(page.getByRole('button', { name: '加入购物车' })).toBeDisabled();
});

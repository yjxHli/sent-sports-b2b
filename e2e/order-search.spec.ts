/**
 * 商城端「我的订单」搜索（TDD 红→绿）。
 *
 * 用户诉求（2026-11-16）：商场端我的订单界面需要可以搜索。
 *
 * 断言口径：
 *   1) 订单 Tab 栏有搜索框（订单号 / 商品名）与搜索按钮；
 *   2) 输入订单号 → 请求带 keyword，结果只剩该单，URL 同步 keyword；
 *   3) 关键字不匹配 → 空态而不是全量订单；
 *   4) 清空搜索 → 恢复全部订单，URL 去掉 keyword；
 *   5) 搜索与状态 Tab 可叠加，不互相覆盖。
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

test('我的订单可按订单号搜索、清空恢复，搜索词写回 URL', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/orders`);
  await expect(page.locator('.order-card').first()).toBeVisible({ timeout: 20_000 });

  // 1) 搜索框与按钮
  const box = page.locator('.order-search');
  await expect(box, '订单页应有搜索区').toBeVisible({ timeout: 10_000 });
  const input = box.locator('input');
  await expect(input, '搜索框应提示可搜订单号/商品名').toHaveAttribute(
    'placeholder', /订单号|商品/,
  );
  await expect(box.getByRole('button', { name: '搜索' }), '应有搜索按钮').toBeVisible();

  // 2) 取第一张卡的订单号作为关键字
  const firstNo = (await page.locator('.order-card__no').first().innerText())
    .replace(/订单号[:：]\s*/, '').trim();
  expect(firstNo, '订单卡片应带订单号').toMatch(/^SO/);

  const searched = page.waitForResponse(
    (r) => r.url().includes('/api/orders') && r.url().includes(`keyword=${firstNo}`),
    { timeout: 20_000 },
  );
  await input.fill(firstNo);
  await box.getByRole('button', { name: '搜索' }).click();
  await searched;

  await expect(page.locator('.order-card'), '按订单号搜索应只剩 1 单').toHaveCount(1, {
    timeout: 20_000,
  });
  await expect(page.locator('.order-card__no').first()).toContainText(firstNo);
  await expect(page, '搜索词应写回 URL，便于刷新/分享').toHaveURL(/keyword=SO/);

  // 3) 不匹配关键字 → 空态，不能再把全量订单倒出来
  const missed = page.waitForResponse(
    (r) => r.url().includes('/api/orders') && r.url().includes('keyword=SO-NOT-EXIST-0000'),
    { timeout: 20_000 },
  );
  await input.fill('SO-NOT-EXIST-0000');
  await input.press('Enter');
  await missed;
  await expect(page.locator('.order-card')).toHaveCount(0, { timeout: 20_000 });
  await expect(page.locator('.el-empty'), '无结果应显示空态').toBeVisible();

  // 4) 清空 → 恢复全部
  const cleared = page.waitForResponse(
    (r) => r.url().includes('/api/orders') && !r.url().includes('keyword='),
    { timeout: 20_000 },
  );
  await input.fill('');
  await input.press('Enter');
  await cleared;
  await expect(page.locator('.order-card').first()).toBeVisible({ timeout: 20_000 });
  await expect(page, '清空后 URL 不应残留 keyword').not.toHaveURL(/keyword=/);

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

test('搜索关键字与状态 Tab 叠加：切 Tab 不清空搜索词', async ({ page }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/orders`);
  await expect(page.locator('.order-card').first()).toBeVisible({ timeout: 20_000 });

  const firstNo = (await page.locator('.order-card__no').first().innerText())
    .replace(/订单号[:：]\s*/, '').trim();
  const input = page.locator('.order-search input');

  await input.fill(firstNo);
  await input.press('Enter');
  await expect(page.locator('.order-card')).toHaveCount(1, { timeout: 20_000 });

  // 切 Tab 时 URL 与输入框都应保留 keyword，请求同时带 status + keyword
  const combined = page.waitForResponse(
    (r) => r.url().includes('/api/orders')
      && r.url().includes('status=WAIT_PAY')
      && r.url().includes(`keyword=${firstNo}`),
    { timeout: 20_000 },
  );
  await page.locator('.order-tab').filter({ hasText: '待付款' }).first().click();
  await combined;
  await expect(input).toHaveValue(firstNo);
  await expect(page).toHaveURL(/keyword=SO/);
});

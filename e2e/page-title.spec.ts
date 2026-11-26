/**
 * 浏览器标签标题（2026-11-26 用户要求）。
 *
 * 用户口径：标签上要能看出当前在哪个页面——
 * 购物车 → `森特体育 · 购物车`；搜索哑铃 → `森特体育 · 哑铃`；商品详情 → 商品名。
 * 打开多个标签页时（商城大量入口都是新开页），标题是唯一能区分它们的信息。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('标签标题随页面变化：首页 / 购物车 / 搜索 / 商品详情 / 订单', async ({ page }) => {
  test.setTimeout(120_000);
  await loginUser(page);

  // 首页
  await page.goto(`${USER_URL}/home`);
  await expect(page).toHaveTitle(/森特体育\s*·\s*首页/, { timeout: 15_000 });

  // 购物车
  await page.goto(`${USER_URL}/cart`);
  await expect(page).toHaveTitle(/森特体育\s*·\s*购物车/, { timeout: 15_000 });

  // 搜索：标题直接用关键词
  await page.goto(`${USER_URL}/products?keyword=${encodeURIComponent('哑铃')}`);
  await expect(page).toHaveTitle(/森特体育\s*·\s*哑铃/, { timeout: 15_000 });

  // 商品详情：标题覆盖成商品名
  await page.goto(`${USER_URL}/products`);
  const card = page.locator('.category-page .good-card').first();
  await expect(card).toBeVisible({ timeout: 20_000 });
  const href = await card.getAttribute('href');
  expect(href, '商品卡应带详情链接').toBeTruthy();
  await page.goto(`${USER_URL}${href}`);
  await expect(page.locator('.buy__title')).toBeVisible({ timeout: 20_000 });
  const productName = (await page.locator('.buy__title').innerText()).trim();
  expect(productName.length).toBeGreaterThan(0);
  await expect(page).toHaveTitle(new RegExp(`森特体育\\s*·\\s*${productName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`), {
    timeout: 20_000,
  });

  // 订单列表
  await page.goto(`${USER_URL}/orders`);
  await expect(page).toHaveTitle(/森特体育\s*·\s*我的订单/, { timeout: 15_000 });
});

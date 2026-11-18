/**
 * 商城首页搜索联想 + 历史搜索（2026-11-17 用户要求，TDD）。
 *
 * 口径：
 *   1) 输入关键字 → 下拉显示相似商品名（后端 /api/products/suggest 真查）；
 *   2) 点击联想 → 跳「商品结果页」并带上 categoryId + keyword；
 *   3) 搜索过的词进入历史搜索，聚焦空输入可见，「清除」可清空。
 */
import { test, expect } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';

test.beforeEach(async ({ page }) => {
  // 商城端全站需要登录，未登录会被守卫重定向到 /login
  await page.goto(`${USER_URL}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(process.env.E2E_USER_NAME ?? '13800138000');
  await page.getByPlaceholder('请输入密码').fill(process.env.E2E_USER_PASSWORD ?? '123456');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
  await page.evaluate(() => localStorage.removeItem('sent_search_history_user'));
  await page.goto(`${USER_URL}/home`);
});

test('输入联想 → 带分类跳结果页 → 历史搜索可清除', async ({ page }) => {
  test.setTimeout(90_000);

  const input = page.getByTestId('search-input');
  await input.fill('球');
  const dropdown = page.getByTestId('search-suggest');
  await expect(dropdown).toBeVisible({ timeout: 15_000 });
  const options = page.getByTestId('search-suggest-item');
  await expect(options.first()).toBeVisible({ timeout: 15_000 });
  // 可见 ≠ 文本已渲染：v-for 首帧可能先挂空节点，toHaveText 会重试到有字为止
  await expect(options.first()).toHaveText(/\S/, { timeout: 15_000 });
  const picked = (await options.first().innerText()).trim().split('\n')[0].trim();
  expect(picked.length).toBeGreaterThan(0);

  // 点击联想：跳 /products?categoryId=X&keyword=商品名
  await options.first().click();
  await expect(page).toHaveURL(/\/products\?/, { timeout: 15_000 });
  const url = new URL(page.url());
  expect(url.searchParams.get('keyword')).toBeTruthy();
  expect(url.searchParams.get('categoryId')).toBeTruthy();

  // 回到首页：空输入聚焦应显示刚才搜过的词
  await page.goto(`${USER_URL}/home`);
  await input.click();
  await expect(page.getByTestId('search-suggest')).toBeVisible();
  await expect(page.getByTestId('history-item').filter({ hasText: picked })).toBeVisible();

  // 清除历史后立即消失
  await page.getByTestId('clear-history').click();
  await expect(page.getByTestId('history-item')).toHaveCount(0);
});

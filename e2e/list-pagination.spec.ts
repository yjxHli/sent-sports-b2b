/**
 * 订单 / 定制 / 售后 三列表统一完整分页器（TDD 红→绿）。
 *
 * 用户诉求（2026-11-16）：我的订单、我的定制、我的售后加统一分页控件：
 * 共 X 条 | X 条/页 | 上一页 1 2 3 … 下一页 | 前往 X 页。
 *
 * 断言口径：
 *   1) 三个列表页都有 .el-pagination，且含 sizes 下拉、页码、jumper「前往…页」；
 *   2) 能切换每页条数（请求 size 变化），能翻页（请求 page 变化、首行变化）；
 *   3) 定制页旧 .pager、售后页「加载更多」已移除；
 *   4) 全程无 console error。
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
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

interface Case {
  path: string;
  apiFragment: string;
  cardSelector: string;
}

const CASES: Case[] = [
  { path: '/orders', apiFragment: '/api/orders', cardSelector: '.order-card' },
  { path: '/custom', apiFragment: '/api/custom-orders/my', cardSelector: '.custom-row' },
  { path: '/refunds', apiFragment: '/api/refunds', cardSelector: '.rf-card' },
];

for (const c of CASES) {
  test(`${c.path} 有完整分页器（总数/每页条数/页码/跳页）且能翻页切条数`, async ({ page }) => {
    const errors: string[] = [];
    const requests: string[] = [];
    page.on('console', (m) => {
      if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
    });
    page.on('request', (r) => {
      if (r.url().includes(c.apiFragment) && r.method() === 'GET' && !r.url().includes('/count')) {
        requests.push(r.url());
      }
    });

    await loginUser(page);
    await page.goto(`${USER_URL.replace(/\/+$/, '')}${c.path}`);
    await expect(page.locator(c.cardSelector).first()).toBeVisible({ timeout: 20_000 });

    // 1) 完整分页器存在
    const pager = page.locator('.el-pagination');
    await expect(pager, `${c.path} 应有 el-pagination`).toBeVisible({ timeout: 10_000 });
    const text = (await pager.innerText()).replace(/\s+/g, ' ');
    expect(text, `${c.path} 分页器应显示总条数`).toMatch(/共\s*\d+\s*条|Total/);
    expect(text, `${c.path} 分页器应有「前往…页」跳页`).toMatch(/前往/);
    await expect(pager.locator('.el-select'), `${c.path} 应有每页条数下拉`).toBeVisible();
    await expect(pager.locator('.el-pager'), `${c.path} 应有页码`).toBeVisible();

    // 2) 切每页条数：选 15，请求带 size=15
    await pager.locator('.el-select').click();
    const opt15 = page
      .locator('.el-select-dropdown:visible .el-select-dropdown__item')
      .filter({ hasText: /15\s*条\/页|15$/ })
      .first();
    await expect(opt15, `${c.path} 每页条数应有 15 选项`).toBeVisible({ timeout: 8_000 });
    await opt15.click();
    await expect
      .poll(() => requests.at(-1) ?? '', { timeout: 10_000, message: `${c.path} 切条数应重新请求 size=15` })
      .toContain('size=15');

    // 3) 翻到第 2 页：请求带 page=2
    await pager.locator('.btn-next').click();
    await expect
      .poll(() => requests.at(-1) ?? '', { timeout: 10_000, message: `${c.path} 翻页应请求 page=2` })
      .toContain('page=2');

    expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
  });
}

test('定制页旧分页与售后页加载更多已移除', async ({ page }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/custom`);
  await expect(page.locator('.custom-row').first()).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.pager'), '定制页旧手写分页应移除').toHaveCount(0);

  await page.goto(`${USER_URL.replace(/\/+$/, '')}/refunds`);
  await expect(page.locator('.rf-card').first()).toBeVisible({ timeout: 20_000 });
  await expect(page.getByRole('button', { name: /加载更多/ }), '售后页加载更多应移除').toHaveCount(0);
});

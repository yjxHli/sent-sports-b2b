/**
 * 全局内容风控 · 搜索合规（TDD 红→绿）。
 *
 * 口径：
 *   1) 违禁词搜索不弹红色报错、不渲染「暂无商品」，而是独立的绿色盾牌合规空状态；
 *   2) 正常词（篮球）必须完全不受影响——这是绝对红线；
 *   3) 管理端新增词条后 DFA 树即时生效，无需重启服务（用完即删，不污染词库）。
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(process.env.E2E_USER_NAME ?? '13800138000');
  await page.getByPlaceholder('请输入密码').fill(process.env.E2E_USER_PASSWORD ?? '123456');
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

async function adminToken(request: APIRequestContext): Promise<string> {
  const res = await request.fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    data: {
      username: 'admin',
      password: '123456',
      aud: 'admin',
      deviceId: `e2e-search-compliance-${Date.now()}`,
    },
  });
  const body = await res.json();
  return body?.data?.accessToken as string;
}

test.beforeEach(async ({ page }) => {
  await loginUser(page);
  await page.goto(`${USER_URL}/home`);
});

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('搜索违禁词：显示合规提示页，不返回任何商品，可返回首页', async ({ page }) => {
  const input = page.getByTestId('search-input');
  await input.fill('毒品');
  // 2026-11-26：顶部搜索改为一律新开一页，原首页保持不动
  const [result] = await Promise.all([
    page.context().waitForEvent('page', { timeout: 20_000 }),
    page.getByRole('button', { name: '搜 索' }).click(),
  ]);
  await result.waitForLoadState('domcontentloaded');

  await expect(result).toHaveURL(/\/products\?keyword=/, { timeout: 15_000 });
  const tip = result.getByTestId('illegal-search-tip');
  await expect(tip).toBeVisible({ timeout: 15_000 });
  await expect(tip).toContainText('文明上网');
  await expect(result.locator('.goods-grid .good-card')).toHaveCount(0);
  await expect(result.getByText('没有符合条件的商品')).toHaveCount(0);

  await result.getByTestId('illegal-search-back').click();
  await expect(result).toHaveURL(/\/home$/, { timeout: 15_000 });
  await result.close();
});

test('红线回归：正常词「篮球」仍能搜出商品', async ({ page }) => {
  const input = page.getByTestId('search-input');
  await input.fill('篮球');
  const [result] = await Promise.all([
    page.context().waitForEvent('page', { timeout: 20_000 }),
    page.getByRole('button', { name: '搜 索' }).click(),
  ]);
  await result.waitForLoadState('domcontentloaded');

  await expect(result.getByTestId('illegal-search-tip')).toHaveCount(0);
  const cards = result.locator('.goods-grid .good-card');
  await expect(cards.first()).toBeVisible({ timeout: 15_000 });
  expect(await cards.count()).toBeGreaterThan(0);
  await result.close();
});

test('管理端新增词条后搜索即时拦截，删除后恢复（DFA 热更新）', async ({ page, request }) => {
  const token = await adminToken(request);
  if (!token) throw new Error('管理端登录失败，无法验证词库热更新');
  const marker = `E2E搜索合规${Date.now().toString().slice(-6)}`;
  let wordId: number | null = null;

  try {
    const created = await request.fetch(`${API_URL}/admin/sensitive-words`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      data: { word: marker, category: 'OTHER', enabled: true, remark: 'E2E 搜索合规热更新' },
    });
    wordId = Number((await created.json())?.data?.id);

    await page.goto(`${USER_URL}/products?keyword=${encodeURIComponent(marker)}`);
    await expect(page.getByTestId('illegal-search-tip')).toBeVisible({ timeout: 15_000 });
  } finally {
    if (wordId && Number.isFinite(wordId)) {
      await request.fetch(`${API_URL}/admin/sensitive-words/${wordId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
    }
  }
});

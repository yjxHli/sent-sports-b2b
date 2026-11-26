/**
 * 商城端「无搜索区」版式（2026-11-25 用户诉求，TDD 红→绿）。
 *
 * 诉求：在线客服（/chat）与帮助中心各分类页（/help/category/*）不需要顶部的
 * 搜索框、品牌图标与「森特体育 / SENT SPORTS」文字；其余页面保持不变。
 *
 * 断言口径：
 *   1) 首页仍保留 .searchbar（含 .brand 与搜索输入）—— 防止误伤全站；
 *   2) /chat 不出现 .searchbar / .brand，聊天卡仍填满首屏剩余高度（不能因为少了一块就变矮）；
 *   3) 帮助中心 4 个分类页都不出现 .searchbar / .brand，正文模块照常渲染；
 *   4) 全程无 console error。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

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

test('首页保留品牌与搜索框（不被误伤）', async ({ page }) => {
  await loginUser(page);
  await page.goto(`${USER_URL}/home`);
  await expect(page.locator('.searchbar')).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.brand')).toBeVisible();
  await expect(page.getByTestId('search-input')).toBeVisible();
});

test('在线客服页不显示品牌与搜索区，聊天卡仍填满首屏', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await page.goto(`${USER_URL}/chat`);

  const card = page.locator('.chat-card');
  await expect(card).toBeVisible({ timeout: 20_000 });

  // 1) 搜索区（含品牌图标与文字、搜索框、热搜）整块不应存在
  await expect(page.locator('.searchbar')).toHaveCount(0);
  await expect(page.locator('.brand')).toHaveCount(0);
  await expect(page.getByTestId('search-input')).toHaveCount(0);

  // 2) 站点页脚（© 2026 森特体育…）在客服页也不要
  await expect(page.locator('.site-footer'), '客服页不应有站点页脚').toHaveCount(0);

  // 3) 少了两块之后聊天卡要顶上来：底部只剩主体内边距 40 + 页内边距 28 ≈ 68
  const box = await card.boundingBox();
  expect(box, '聊天卡应有可见区域').not.toBeNull();
  const viewportH = page.viewportSize()!.height;
  const gap = viewportH - (box!.y + box!.height);
  expect(gap, `聊天卡底部距视口底部 ${gap}px，超过 90px 说明没跟着长高`).toBeLessThanOrEqual(90);

  // 4) 界面整体放大：1440 视口下卡片应明显宽于原来的 880
  const viewportW = page.viewportSize()!.width;
  const minW = Math.min(1040, viewportW - 80);
  expect(box!.width, `聊天卡宽 ${box!.width}px，应≥${minW}px（原 880 太窄）`).toBeGreaterThanOrEqual(minW);

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

const CATS = [
  { key: 'shopping-guide', title: '购物指南' },
  { key: 'delivery', title: '配送方式' },
  { key: 'after-sales', title: '售后服务' },
  { key: 'about', title: '关于森特' },
];

test('消息中心页不显示品牌与搜索区', async ({ page }) => {
  await loginUser(page);
  await page.goto(`${USER_URL}/messages`);

  const msgPage = page.locator('.message-page');
  await expect(msgPage).toBeVisible({ timeout: 20_000 });
  await expect(msgPage.locator('.msg-tabs')).toBeVisible();

  await expect(page.locator('.searchbar'), '消息中心不应有搜索区').toHaveCount(0);
  await expect(page.locator('.brand'), '消息中心不应有品牌标识').toHaveCount(0);
  await expect(page.getByTestId('search-input'), '消息中心不应有搜索框').toHaveCount(0);

  // 顶栏仍在（消息页要从「首页」「在线客服」等入口跳走，不能连顶栏一起藏掉）
  await expect(page.locator('.topbar__msg')).toBeVisible();
});

test('帮助中心四个板块不显示品牌与搜索区', async ({ page }) => {
  await loginUser(page);

  for (const cat of CATS) {
    await page.goto(`${USER_URL}/help/category/${cat.key}`);

    const catPage = page.locator('.help-category');
    await expect(catPage).toBeVisible({ timeout: 20_000 });
    await expect(catPage.locator('.help-category__title')).toHaveText(cat.title);

    // 无搜索区、无品牌
    await expect(page.locator('.searchbar'), `${cat.title} 不应有搜索区`).toHaveCount(0);
    await expect(page.locator('.brand'), `${cat.title} 不应有品牌标识`).toHaveCount(0);
    await expect(page.getByTestId('search-input'), `${cat.title} 不应有搜索框`).toHaveCount(0);

    // 顶栏仍在：帮助中心是顶栏下拉的入口，不能连顶栏一起藏掉
    await expect(page.locator('.topbar__help'), `${cat.title} 顶栏帮助中心入口应保留`).toBeVisible();

    // 正文模块照常渲染
    expect(await catPage.locator('.help-module').count(), `${cat.title} 应分多个模块`).toBeGreaterThanOrEqual(2);
  }
});

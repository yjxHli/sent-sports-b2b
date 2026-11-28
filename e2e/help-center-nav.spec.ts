/**
 * 顶栏「帮助中心」下拉导航（TDD 红→绿）。
 *
 * 用户诉求（2026-11-16）：顶部导航行最右侧购物车旁添加「帮助中心」，
 * hover 弹出购物指南 / 配送方式 / 售后服务 / 关于森特 四个选项，
 * 点击每个选项进入该分类内容页，且内容按模块分块展示。
 *
 * 断言口径：
 *   1) 顶栏购物车旁有「帮助中心」入口；
 *   2) hover 弹出下拉，含 4 个分类项；
 *   3) 点每类 → 新标签页 /help/category/<key>，页面分多个模块，每模块有标题与正文；
 *   4) 配送方式也可点（本需求下 4 类全部可进）；
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
  await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

const pathOf = (page: Page): string => new URL(page.url()).pathname;

const CATS = [
  { key: 'shopping-guide', title: '购物指南' },
  { key: 'delivery', title: '配送方式' },
  { key: 'after-sales', title: '售后服务' },
  { key: 'about', title: '关于森特' },
];

test('顶栏帮助中心 hover 弹出 4 分类，点每类进分模块内容页', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  // 1) 购物车旁有「帮助中心」入口
  const help = page.locator('.topbar__help');
  await expect(help, '顶栏应有帮助中心入口').toBeVisible({ timeout: 15_000 });
  await expect(page.locator('.topbar__cart')).toBeVisible();
  await expect(help.locator('.topbar__help-trigger')).toHaveText(/帮助中心/);

  // 2) hover 弹出下拉，含 4 个分类
  await help.hover();
  const menu = page.locator('.topbar__help-menu');
  await expect(menu).toBeVisible({ timeout: 10_000 });
  for (const cat of CATS) {
    await expect(menu.locator(`.topbar__help-item[data-category="${cat.key}"]`)).toHaveText(cat.title);
  }

  // 3) 点每类 → 新标签页各自分类页，内容分模块
  for (const cat of CATS) {
    await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
    const h = page.locator('.topbar__help');
    await h.hover();
    const popup = page.waitForEvent('popup', { timeout: 15_000 });
    await h.locator(`.topbar__help-item[data-category="${cat.key}"]`).click();
    const catTab = await popup;
    await expect
      .poll(() => pathOf(catTab), { timeout: 15_000, message: `${cat.title} 应跳到 /help/category/${cat.key}` })
      .toBe(`/help/category/${cat.key}`);
    expect(page.url(), '点帮助中心分类后原首页不应导航走').toContain('/home');

    const catPage = catTab.locator('.help-category');
    await expect(catPage).toBeVisible({ timeout: 15_000 });
    await expect(catPage.locator('.help-category__title')).toHaveText(cat.title);
    const modules = catPage.locator('.help-module');
    const count = await modules.count();
    expect(count, `${cat.title} 应分多个模块展示`).toBeGreaterThanOrEqual(2);
    for (let i = 0; i < count; i++) {
      const m = modules.nth(i);
      await expect(m.locator('.help-module__title')).not.toHaveText('');
      const body = (await m.locator('.help-module__body').innerText()).trim();
      expect(body.length, `${cat.title} 模块 ${i + 1} 正文不应为空`).toBeGreaterThanOrEqual(20);
    }
    await catTab.close();
  }

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

/**
 * 商城顶栏改版 + 分类页新标签打开 + 团购专区落点（TDD 红→绿）。
 *
 * 用户诉求（2026-11-15）：
 *   1) 「在线客服」「会员中心」从绿色主导航移到右上角，和「我的账户」「我的订单」放一起；
 *   2) 截图里那一整行主导航（首页/商品/我的订单/会员中心/在线客服/我的）删除，
 *      商城主页只保留商城首页内容；
 *   3) 「查看团购专区」原来跳到 /products?keyword=训练（跑到了训练器材），
 *      应进入独立的团购专区页；
 *   4) 点首页商品分类要在**新标签页**打开分类结果页（像淘宝点「男装」那样），
 *      原首页标签页保持在首页不动。
 *
 * 断言口径：
 *   - 顶栏 .topbar 内出现「会员中心」「在线客服」，href 分别指向 /member、/chat，点击可达；
 *   - 全站不再有 .mainnav（主导航行已移除）；
 *   - 点「篮球」分类：context 新开一个 page，新页 URL 带 categoryId=3 且标题为「篮球」，
 *     原 page 仍是 /home；
 *   - 点「查看团购专区」：落地 /group-buy，不带 keyword 参数，页面有团购专区区块与商品卡片。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

/** 演示数据：篮球 = categoryId 3 */
const BASKETBALL = { name: '篮球', id: 3 };

const pathOf = (page: Page): string => new URL(page.url()).pathname;
const queryOf = (page: Page, key: string): string | null =>
  new URL(page.url()).searchParams.get(key);

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

async function backHome(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
  await expect(page.locator('.cat-nav__item').first()).toBeVisible({ timeout: 20_000 });
}

test('在线客服收纳在右上角，会员中心已挪到个人中心侧栏', async ({ page, context }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await backHome(page);

  const topbar = page.locator('.topbar');
  const chatLink = topbar.locator('.topbar__link', { hasText: '在线客服' }).first();

  // 2026-11-28：用户要求「会员中心放进个人中心侧栏，去掉顶栏入口」
  await expect(topbar.locator('.topbar__link', { hasText: '会员中心' }),
    '顶栏不应再有「会员中心」（已挪到个人中心侧栏）').toHaveCount(0);
  await expect(chatLink, '顶栏应有「在线客服」入口').toBeVisible();
  await expect(chatLink, '「在线客服」应指向 /chat').toHaveAttribute('href', /\/chat/);

  // 主导航行整行删除（首页只保留首页内容）
  await expect(page.locator('.mainnav'), '绿色主导航行应已删除').toHaveCount(0);
  await expect(page.locator('.mainnav__item'), '主导航项应已全部移除').toHaveCount(0);

  // 会员中心的入口改到个人中心侧栏，且真的能到达
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/mine`);
  const sideMember = page.getByTestId('mine-nav-member');
  await expect(sideMember, '个人中心侧栏应有「会员中心」').toBeVisible({ timeout: 20_000 });
  await sideMember.click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toBe('/member');

  await backHome(page);
  const [chatPage] = await Promise.all([
    context.waitForEvent('page', { timeout: 20_000 }),
    page.locator('.topbar__link', { hasText: '在线客服' }).first().click(),
  ]);
  await chatPage.waitForLoadState('domcontentloaded');
  expect(pathOf(chatPage), '新标签页应落在客服页').toBe('/chat');
  await chatPage.close();

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

test('点首页分类在新标签页打开独立分类页，原页停留首页', async ({ page, context }) => {
  await loginUser(page);
  await backHome(page);

  const homeUrl = page.url();

  const [popup] = await Promise.all([
    context.waitForEvent('page', { timeout: 20_000 }),
    page.locator('.cat-nav__item', { hasText: BASKETBALL.name }).first().click(),
  ]);
  await popup.waitForLoadState('domcontentloaded');

  await expect
    .poll(() => queryOf(popup, 'categoryId'), { timeout: 20_000 })
    .toBe(String(BASKETBALL.id));
  expect(pathOf(popup), '新标签页应落在分类结果页 /products').toBe('/products');
  await expect(
    popup.locator('.category-page .cat-result__title'),
    '新标签页标题应为所选分类',
  ).toHaveText(BASKETBALL.name, { timeout: 20_000 });
  await expect(popup.locator('.category-page .good-card').first()).toBeVisible({
    timeout: 20_000,
  });

  expect(page.url(), '原首页标签页不应被导航').toBe(homeUrl);

  await popup.close();
});

test('查看团购专区进入团购专区页，而不是训练关键词列表', async ({ page, context }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await backHome(page);

  // 首页底部入口保留并新开标签；团购专区页现在也保留我的森特侧边栏
  const [groupBuyPage] = await Promise.all([
    context.waitForEvent('page', { timeout: 20_000 }),
    page.locator('.side-tip__btn').first().click(),
  ]);
  await groupBuyPage.waitForLoadState('domcontentloaded');
  expect(pathOf(groupBuyPage), '新标签页应落在团购专区').toBe('/group-buy');
  expect(groupBuyPage.url(), '团购专区不应再落到训练关键词').not.toContain('keyword=');
  await expect(groupBuyPage.getByTestId('mine-sidebar'), '团购专区应有我的森特侧边栏').toBeVisible();

  await expect(groupBuyPage.locator('.group-buy'), '团购专区页应渲染专属区块').toBeVisible({
    timeout: 20_000,
  });
  await expect(groupBuyPage.locator('.group-buy .good-card').first(), '应有批量采购商品').toBeVisible({
    timeout: 20_000,
  });

  await groupBuyPage.close();
  expect(page.url(), '原首页不应被导航走').toContain('/home');

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

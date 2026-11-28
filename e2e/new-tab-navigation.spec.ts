/**
 * 商城端「新 Tab 打开」口径验收（TDD 红→绿）。
 *
 * 用户诉求（2026-11-25）：电商站不能全站新开标签页，只在这几个场景新开，其余保持同页跳转。
 *   1) 首页 / 商品列表页 → 点商品卡进详情；
 *   2) 首页左侧分类 → 分类结果页（早前已实现，本用例做回归保护）；
 *   3) 搜索商品后 → 点搜索结果卡进详情；
 *   4) 会员日「立即抢购」、领券中心「立即领取」；
 *   5) 企业采购「查看团购专区」；
 *   6) 顶栏「在线客服」。
 *   另外顶栏「购物车」也走新 Tab（用户明确要求）。
 *
 * 2026-11-26 扩大口径（用户补充）：顶部搜索（回车 / 搜索按钮 / 热搜 / 历史 / 联想）、
 * 首页大屏「立即选购」、底部四个入口瓦片，一律像点商品详情一样新开一页，原页保持不动。
 *
 * 2026-11-28 再扩大：我的森特搜索框 / 领券中心入口、收藏与足迹（含概览迷你卡）、
 * 订单列表与订单详情里的商品入口（含「再买一次」）、会员专属商品与「去逛逛」、
 * 顶栏消息与商品详情页搜索也新开；
 * 领券中心页下半区商品卡仍保持同页（见文末反向保护用例）。
 * 团购专区：企业工具侧栏入口与其他子项一样同页（且落地页保留侧边栏），
 * 企业采购页里的推广卡、团购页的商品卡 /「查看全部商品」/「联系客户经理」仍新开。
 *
 * 实现口径（断言依据）：商品卡根节点必须是真正的 `<a href target="_blank">`，
 * 才能让鼠标中键 / 右键「在新窗口打开」也生效，而不是靠 JS 拦截点击。
 *
 * 反向保护：领券中心下半区的商品卡**不在**清单内，必须仍然是同页 SPA 跳转，
 * 避免以后一刀切把全站改成都新开。
 */
import { test, expect, type BrowserContext, type Locator, type Page } from '@playwright/test';

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
  await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

const pathOf = (page: Page): string => new URL(page.url()).pathname;

/** 点一下必须新开一个标签页：返回新页，同时断言原页没有被导航走 */
async function clickAndCatchNewPage(
  context: BrowserContext,
  opener: Page,
  click: () => Promise<void>,
): Promise<Page> {
  const before = opener.url();
  const [popup] = await Promise.all([
    context.waitForEvent('page', { timeout: 20_000 }),
    click(),
  ]);
  await popup.waitForLoadState('domcontentloaded');
  expect(opener.url(), '原标签页不应被导航走').toBe(before);
  return popup;
}

/** 商品卡必须是原生锚点：中键 / 右键「在新窗口打开」才能用 */
async function expectNativeNewTabAnchor(card: Locator): Promise<void> {
  const tag = await card.evaluate((el) => el.tagName);
  expect(tag, '商品卡根节点应为 <a>，否则中键/右键打不开').toBe('A');
  await expect(card).toHaveAttribute('target', '_blank');
  await expect(card).toHaveAttribute('rel', /noopener/);
  await expect(card).toHaveAttribute('href', /\/product\/\w+/);
}

test('首页猜你喜欢商品卡 → 新标签页进详情，原页停留首页', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  const card = page.locator('.home-section .good-card').first();
  await expect(card).toBeVisible({ timeout: 20_000 });
  await expectNativeNewTabAnchor(card);

  const detail = await clickAndCatchNewPage(context, page, () => card.click());
  expect(pathOf(detail), '新页应落在商品详情').toMatch(/^\/product\/\w+$/);
  await expect(detail.getByTestId('detail-brand'), '新页应渲染商品详情').toBeVisible({
    timeout: 20_000,
  });
  await detail.close();
  expect(pathOf(page), '原首页应仍停在 /home').toBe('/home');
});

test('商品列表页商品卡 → 新标签页进详情', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/products`);

  const card = page.locator('.category-page .good-card').first();
  await expect(card).toBeVisible({ timeout: 20_000 });
  await expectNativeNewTabAnchor(card);

  const detail = await clickAndCatchNewPage(context, page, () => card.click());
  expect(pathOf(detail)).toMatch(/^\/product\/\w+$/);
  await detail.close();
  expect(pathOf(page), '原列表页应仍停在 /products').toBe('/products');
});

test('搜索（搜索按钮 / 热搜 / 历史）→ 新标签页打开结果页，标题带关键词', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  // 点「搜索」按钮
  await page.getByTestId('search-input').fill('篮球');
  const searchPage = await clickAndCatchNewPage(context, page, () =>
    page.getByRole('button', { name: '搜 索' }).click(),
  );
  expect(pathOf(searchPage), '新页应落在商品结果页').toBe('/products');
  expect(new URL(searchPage.url()).searchParams.get('keyword')).toBe('篮球');
  // 浏览器标题要能看出搜的是什么（2026-11-26 用户要求）
  await expect(searchPage).toHaveTitle(/森特体育\s*·\s*篮球/, { timeout: 20_000 });

  // 结果页商品卡仍是新开（原有口径）
  const card = searchPage.locator('.category-page .good-card').first();
  await expect(card).toBeVisible({ timeout: 20_000 });
  const detail = await clickAndCatchNewPage(context, searchPage, () => card.click());
  expect(pathOf(detail)).toMatch(/^\/product\/\w+$/);
  await detail.close();
  await searchPage.close();
  expect(pathOf(page), '原首页应仍停在 /home').toBe('/home');

  // 热搜词
  const hot = page.locator('.searchbox__hot a').first();
  await expect(hot).toBeVisible({ timeout: 20_000 });
  const hotPage = await clickAndCatchNewPage(context, page, () => hot.click());
  expect(pathOf(hotPage)).toBe('/products');
  await hotPage.close();

  // 历史搜索：清空后聚焦，点历史词
  await page.getByTestId('search-input').fill('');
  await page.getByTestId('search-input').click();
  const historyItem = page.getByTestId('history-item').first();
  await expect(historyItem, '刚搜过的词应进入历史').toBeVisible({ timeout: 15_000 });
  const historyPage = await clickAndCatchNewPage(context, page, () => historyItem.click());
  expect(pathOf(historyPage)).toBe('/products');
  await historyPage.close();
});

test('首页分类仍在新的标签页打开分类结果页（回归保护）', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  const item = page.locator('.cat-nav__item').first();
  await expect(item).toBeVisible({ timeout: 20_000 });
  const catPage = await clickAndCatchNewPage(context, page, () => item.click());
  expect(pathOf(catPage), '新页应落在分类结果页').toBe('/products');
  expect(new URL(catPage.url()).searchParams.get('categoryId')).toBeTruthy();
  await catPage.close();
  expect(pathOf(page)).toBe('/home');
});

test('顶栏在线客服 → 新标签页打开客服页', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  const chatLink = page.locator('.topbar__link', { hasText: '在线客服' }).first();
  await expect(chatLink).toBeVisible({ timeout: 20_000 });
  await expect(chatLink).toHaveAttribute('target', '_blank');
  await expect(chatLink).toHaveAttribute('href', /\/chat/);

  const chatPage = await clickAndCatchNewPage(context, page, () => chatLink.click());
  expect(pathOf(chatPage)).toBe('/chat');
  await chatPage.close();
  expect(pathOf(page)).toBe('/home');
});

test('顶栏购物车 → 新标签页打开购物车', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  const cartLink = page.locator('.topbar__cart');
  await expect(cartLink).toBeVisible({ timeout: 20_000 });
  await expect(cartLink).toHaveAttribute('target', '_blank');

  const cartPage = await clickAndCatchNewPage(context, page, () => cartLink.click());
  expect(pathOf(cartPage)).toBe('/cart');
  await cartPage.close();
});

test('商品详情页搜索 → 新标签页打开商品结果页', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/product/9101`);
  await expect(page.getByTestId('detail-brand')).toBeVisible({ timeout: 20_000 });

  const box = page.getByTestId('detail-mini-search');
  await box.locator('input').fill('篮球');
  const products = await clickAndCatchNewPage(context, page, () =>
    box.getByRole('button', { name: '搜索' }).click(),
  );
  expect(pathOf(products)).toBe('/products');
  expect(new URL(products.url()).searchParams.get('keyword')).toBe('篮球');
  await products.close();
  expect(pathOf(page)).toBe('/product/9101');
});

test('顶栏消息 → 新标签页打开消息中心', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  const msgLink = page.locator('.topbar__msg');
  await expect(msgLink).toBeVisible({ timeout: 20_000 });
  await expect(msgLink).toHaveAttribute('target', '_blank');
  await expect(msgLink).toHaveAttribute('href', /\/messages/);

  const msgPage = await clickAndCatchNewPage(context, page, () => msgLink.click());
  expect(pathOf(msgPage)).toBe('/messages');
  await msgPage.close();
  expect(pathOf(page)).toBe('/home');
});

test('会员日「立即抢购」→ 新标签页打开商品列表', async ({ page, context }) => {
  await loginUser(page);
  // 固定到周三（会员日），否则横幅不渲染；与执行当天是星期几无关
  await page.clock.install({ time: new Date('2026-11-25T10:00:00') });
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  const promoBar = page.locator('.promo-bar');
  await expect(promoBar, '周三应渲染会员日横幅').toBeVisible({ timeout: 20_000 });
  const btn = promoBar.getByRole('button', { name: '立即抢购' });

  const productsPage = await clickAndCatchNewPage(context, page, () => btn.click());
  expect(pathOf(productsPage)).toBe('/products');
  await productsPage.close();
  expect(pathOf(page), '原首页应仍停在 /home').toBe('/home');
});

test('领券中心「立即领取」→ 新标签页打开领券中心', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  const btn = page.locator('.promo-main__btn').first();
  await expect(btn).toBeVisible({ timeout: 20_000 });

  const centerPage = await clickAndCatchNewPage(context, page, () => btn.click());
  expect(pathOf(centerPage)).toBe('/coupon/center');
  await centerPage.close();
  expect(pathOf(page)).toBe('/home');
});

test('企业采购页推广卡「团购专区」→ 新标签页打开（侧栏入口改同页）', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/mine/enterprise/procurement`);

  const btn = page.locator('.ent-groupbuy').getByRole('link', { name: /查看团购/ });
  await expect(btn).toBeVisible({ timeout: 20_000 });
  await expect(btn).toHaveAttribute('target', '_blank');

  const groupBuyPage = await clickAndCatchNewPage(context, page, () => btn.click());
  expect(pathOf(groupBuyPage)).toBe('/group-buy');
  await expect(groupBuyPage.locator('.group-buy')).toBeVisible({ timeout: 20_000 });
  await expect(groupBuyPage.getByTestId('mine-sidebar'), '新开的团购专区也有侧边栏').toBeVisible();
  await groupBuyPage.close();
  expect(pathOf(page)).toBe('/mine/enterprise/procurement');
});

test('团购专区商品卡与「查看全部商品」→ 新标签页，原页不动', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/group-buy`);

  const card = page.locator('.group-buy .good-card').first();
  await expect(card, '团购专区应有商品卡').toBeVisible({ timeout: 20_000 });
  await expectNativeNewTabAnchor(card);
  const detail = await clickAndCatchNewPage(context, page, () => card.click());
  expect(pathOf(detail)).toMatch(/^\/product\/\w+$/);
  await detail.close();
  expect(pathOf(page)).toBe('/group-buy');

  const more = page.locator('.gb-block__more');
  await expect(more).toBeVisible({ timeout: 20_000 });
  await expect(more).toHaveAttribute('target', '_blank');
  const products = await clickAndCatchNewPage(context, page, () => more.click());
  expect(pathOf(products)).toBe('/products');
  await products.close();
  expect(pathOf(page)).toBe('/group-buy');
});

/**
 * 反向保护：清单之外的页面**不许**新开。
 * 领券中心下半区也是商品卡，但它不在用户列出的场景里，必须仍走同页 SPA 跳转。
 */
test('清单外场景不新开：领券中心商品卡仍同页跳转', async ({ page }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/coupon/center`);

  const card = page.locator('.good-card').first();
  await expect(card).toBeVisible({ timeout: 20_000 });
  await expect(card, '非清单内商品卡不应带 target=_blank').not.toHaveAttribute('target', '_blank');

  await card.click();
  await expect(page).toHaveURL(/\/product\/\w+/, { timeout: 20_000 });
});

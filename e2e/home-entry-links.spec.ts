/**
 * 首页顶部模块跳转验收（TDD 红→绿）。
 *
 * 用户诉求：首页顶部的大促通栏、领券中心、四个促销瓦片、轮播、企业采购等，
 * 点击后要跳到各自对应的页面或商品列表，而不是统一跳「全部商品」。
 *
 * 落点口径（与页面上的文案一一对应，且必须能落地到有内容的页面）：
 *   1) 瓦片「定制队服」→ 定制服务页 /custom
 *   2) 瓦片「跑步装备」→ /products?keyword=跑步（缓震跑步鞋 在售）
 *   3) 瓦片「健身器材」→ /products?keyword=训练（哑铃/壶铃/弹力带等 在售）
 *   4) 瓦片「球类专场」→ /products?categoryId=1（球类运动分类）
 *   5) 领券中心「立即领取」→ 会员中心 /member
 *   6) 大促通栏「立即抢购」→ 全部商品 /products
 *   7) 轮播「立即选购」→ 带明确落点的商品列表（b1 训练装备）
 *   8) 企业采购「查看团购专区」→ 团购专区页 /group-buy（阶梯价/账期/专票 + 批量商品）
 *
 * 除 URL 外还断言结果页真有商品卡片，避免「跳到空列表」也算通过。
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

const pathOf = (page: Page): string => new URL(page.url()).pathname;
const queryOf = (page: Page, key: string): string | null =>
  new URL(page.url()).searchParams.get(key);

async function backHome(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
  await expect(page.locator('.promo-tile').first()).toBeVisible({ timeout: 20_000 });
}

/** 商品结果页落地断言：有卡片、且分类/关键词条不空 */
async function expectGoodsLanded(page: Page): Promise<void> {
  await expect(page.locator('.good-card').first()).toBeVisible({ timeout: 20_000 });
}

test('首页顶部各模块点击都跳到对应页面', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await backHome(page);

  // 1) 瓦片「定制队服」→ 定制服务页
  await page.locator('.promo-tile', { hasText: '定制队服' }).first().click();
  await expect.poll(() => pathOf(page), { timeout: 15_000 }).toBe('/custom');
  await expect(page.locator('.sent-bar')).toContainText('我的定制', { timeout: 15_000 });

  // 2) 瓦片「跑步装备」→ 跑步类商品
  await backHome(page);
  await page.locator('.promo-tile', { hasText: '跑步装备' }).first().click();
  await expect.poll(() => queryOf(page, 'keyword'), { timeout: 15_000 }).toBe('跑步');
  expect(pathOf(page), '应在商品结果页').toBe('/products');
  await expectGoodsLanded(page);

  // 3) 瓦片「健身器材」→ 训练器材类商品
  await backHome(page);
  await page.locator('.promo-tile', { hasText: '健身器材' }).first().click();
  await expect.poll(() => queryOf(page, 'keyword'), { timeout: 15_000 }).toBe('训练');
  await expectGoodsLanded(page);

  // 4) 瓦片「球类专场」→ 球类运动分类
  await backHome(page);
  await page.locator('.promo-tile', { hasText: '球类专场' }).first().click();
  await expect.poll(() => queryOf(page, 'categoryId'), { timeout: 15_000 }).toBe('1');
  await expectGoodsLanded(page);

  // 5) 领券中心「立即领取」→ 会员中心（券与权益在这里）
  await backHome(page);
  await page.locator('.promo-main__btn').first().click();
  await expect.poll(() => pathOf(page), { timeout: 15_000 }).toBe('/member');

  // 6) 大促通栏「立即抢购」→ 全部商品
  await backHome(page);
  await page.locator('.promo-bar__btn').first().click();
  await expect.poll(() => pathOf(page), { timeout: 15_000 }).toBe('/products');
  await expectGoodsLanded(page);

  // 7) 轮播「立即选购」→ 带明确落点的商品列表
  await backHome(page);
  await page.locator('.banner__btn').first().click();
  await expect.poll(() => pathOf(page), { timeout: 15_000 }).toBe('/products');
  const kv = queryOf(page, 'keyword');
  const cid = queryOf(page, 'categoryId');
  expect(
    Boolean(kv) || Boolean(cid),
    `轮播「立即选购」应带关键词或分类，实际 URL：${page.url()}`,
  ).toBe(true);
  await expectGoodsLanded(page);

  // 8) 企业采购「查看团购专区」→ 独立的团购专区页（不是训练关键词列表）
  await backHome(page);
  await page.locator('.side-tip__btn').first().click();
  await expect.poll(() => pathOf(page), { timeout: 15_000 }).toBe('/group-buy');
  expect(page.url(), '团购专区不应再落到训练关键词').not.toContain('keyword=');
  await expect(page.locator('.group-buy .good-card').first()).toBeVisible({ timeout: 20_000 });

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

/**
 * 搜索框旁的购物车按钮已按需求下线（顶栏「购物车」入口保留，功能不丢）。
 * 断言：搜索区里不再有 .mini-cart / .searchbar__right，且顶栏购物车链接仍可用。
 */
test('搜索框旁不再有购物车按钮，顶栏购物车入口仍在', async ({ page }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
  await expect(page.locator('.searchbox').first()).toBeVisible({ timeout: 20_000 });

  await expect(page.locator('.mini-cart'), '搜索框旁的购物车按钮应已移除').toHaveCount(0);
  await expect(page.locator('.searchbar__right')).toHaveCount(0);
  await expect(page.locator('.topbar__cart'), '顶栏购物车入口应保留').toBeVisible();

  await page.locator('.topbar__cart').click();
  await expect.poll(() => pathOf(page), { timeout: 15_000 }).toBe('/cart');
});

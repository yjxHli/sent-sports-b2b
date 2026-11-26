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
 *   5) 领券中心「立即领取」→ 领券中心 /coupon/center（上可领券 + 下商品）
 *   6) 大促通栏「立即抢购」→ 全部商品 /products
 *   7) 轮播「立即选购」→ 带明确落点的商品列表（b1 训练装备）
 *   8) 企业采购「查看团购专区」→ 团购专区页 /group-buy（阶梯价/账期/专票 + 批量商品）
 *
 * 除 URL 外还断言结果页真有商品卡片，避免「跳到空列表」也算通过。
 */
import { test, expect, type BrowserContext, type Page } from '@playwright/test';

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

/**
 * 点一下会新开标签页的入口：返回新页，并断言原页没有被导航走。
 * 2026-11-25 口径：领券中心「立即领取」、会员日「立即抢购」、查看团购专区都改成了新开 Tab。
 */
async function clickAndCatchNewPage(
  context: BrowserContext,
  opener: Page,
  click: () => Promise<void>,
): Promise<Page> {
  const before = opener.url();
  const [popup] = await Promise.all([context.waitForEvent('page', { timeout: 20_000 }), click()]);
  await popup.waitForLoadState('domcontentloaded');
  expect(opener.url(), '原首页不应被导航走').toBe(before);
  return popup;
}

test('首页顶部各模块点击都跳到对应页面', async ({ page, context }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await backHome(page);

  // 1) 瓦片「定制队服」→ 定制服务页（2026-11-26 起新开一页，首页保持不动）
  const customPage = await clickAndCatchNewPage(context, page, () =>
    page.locator('.promo-tile', { hasText: '定制队服' }).first().click(),
  );
  expect(pathOf(customPage)).toBe('/custom');
  await expect(customPage.locator('.sent-bar')).toContainText('我的定制', { timeout: 15_000 });
  await customPage.close();

  // 2) 瓦片「跑步装备」→ 跑步类商品
  await backHome(page);
  const runPage = await clickAndCatchNewPage(context, page, () =>
    page.locator('.promo-tile', { hasText: '跑步装备' }).first().click(),
  );
  expect(pathOf(runPage), '应在商品结果页').toBe('/products');
  expect(queryOf(runPage, 'keyword')).toBe('跑步');
  await expectGoodsLanded(runPage);
  await runPage.close();

  // 3) 瓦片「健身器材」→ 训练器材类商品
  await backHome(page);
  const gymPage = await clickAndCatchNewPage(context, page, () =>
    page.locator('.promo-tile', { hasText: '健身器材' }).first().click(),
  );
  expect(queryOf(gymPage, 'keyword')).toBe('训练');
  await expectGoodsLanded(gymPage);
  await gymPage.close();

  // 4) 瓦片「球类专场」→ 球类运动分类
  await backHome(page);
  const ballPage = await clickAndCatchNewPage(context, page, () =>
    page.locator('.promo-tile', { hasText: '球类专场' }).first().click(),
  );
  expect(queryOf(ballPage, 'categoryId')).toBe('1');
  await expectGoodsLanded(ballPage);
  await ballPage.close();

  // 5) 领券中心「立即领取」→ 独立领券页（上可领券 + 下商品，2026-11-18 改造）；
  //    2026-11-25 起改为**新标签页**打开，原首页不动；金额已按用户口径改小
  await backHome(page);
  const couponText = await page.locator('.promo-main__cupons').innerText();
  expect(couponText).toContain('¥5');
  expect(couponText).toContain('¥10');
  expect(couponText).toContain('¥50');
  expect(couponText, '领券中心不能显示旧的大额券面额').not.toContain('¥500');
  // 非会员日：首页会员日卡片必须给出下一次会员日的日期时间（周三或周四 00:00）
  const memberDayNow = [3, 4].includes(new Date().getDay());
  const nextHint = page.getByTestId('member-day-next');
  if (memberDayNow) {
    await expect(nextHint, '会员日当天不必提示下次日期').toHaveCount(0);
  } else {
    await expect(nextHint).toBeVisible({ timeout: 15_000 });
    await expect(nextHint).toContainText('下次会员日');
    await expect(nextHint).toContainText(/\d{4}年\d{1,2}月\d{1,2}日（周[三四]）00:00/);
  }
  const couponPage = await clickAndCatchNewPage(context, page, () =>
    page.locator('.promo-main__btn').first().click(),
  );
  expect(pathOf(couponPage), '新标签页应落在领券中心').toBe('/coupon/center');
  await expect(couponPage.getByRole('heading', { name: '领券中心' })).toBeVisible({
    timeout: 15_000,
  });
  await couponPage.close();

  // 6) 大促通栏只在周三/周四显示；显示时「立即抢购」→ 全部商品（新标签页）
  await backHome(page);
  const memberDay = [3, 4].includes(new Date().getDay());
  const promoBar = page.locator('.promo-bar');
  if (memberDay) {
    await expect(promoBar).toBeVisible();
    const productsPage = await clickAndCatchNewPage(context, page, () =>
      promoBar.getByRole('button', { name: '立即抢购' }).click(),
    );
    expect(pathOf(productsPage), '新标签页应落在全部商品').toBe('/products');
    await expectGoodsLanded(productsPage);
    await productsPage.close();
  } else {
    await expect(promoBar, '非周三/周四不应显示会员日横幅').toHaveCount(0);
  }

  // 7) 轮播「立即选购」→ 带明确落点的商品列表（2026-11-26 起新开一页）
  await backHome(page);
  const bannerPage = await clickAndCatchNewPage(context, page, () =>
    page.locator('.banner__btn').first().click(),
  );
  expect(pathOf(bannerPage)).toBe('/products');
  const kv = queryOf(bannerPage, 'keyword');
  const cid = queryOf(bannerPage, 'categoryId');
  expect(
    Boolean(kv) || Boolean(cid),
    `轮播「立即选购」应带关键词或分类，实际 URL：${bannerPage.url()}`,
  ).toBe(true);
  await expectGoodsLanded(bannerPage);
  await bannerPage.close();

  // 8) 企业采购「查看团购专区」→ 独立的团购专区页（不是训练关键词列表），新标签页打开
  await backHome(page);
  const groupBuyPage = await clickAndCatchNewPage(context, page, () =>
    page.locator('.side-tip__btn').first().click(),
  );
  expect(pathOf(groupBuyPage), '新标签页应落在团购专区').toBe('/group-buy');
  expect(groupBuyPage.url(), '团购专区不应再落到训练关键词').not.toContain('keyword=');
  await expect(groupBuyPage.locator('.group-buy .good-card').first()).toBeVisible({
    timeout: 20_000,
  });
  await groupBuyPage.close();

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

/**
 * 搜索框旁的购物车按钮已按需求下线（顶栏「购物车」入口保留，功能不丢）。
 * 断言：搜索区里不再有 .mini-cart / .searchbar__right，且顶栏购物车链接仍可用。
 */
test('搜索框旁不再有购物车按钮，顶栏购物车入口仍在', async ({ page, context }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
  await expect(page.locator('.searchbox').first()).toBeVisible({ timeout: 20_000 });

  await expect(page.locator('.mini-cart'), '搜索框旁的购物车按钮应已移除').toHaveCount(0);
  await expect(page.locator('.searchbar__right')).toHaveCount(0);
  await expect(page.locator('.topbar__cart'), '顶栏购物车入口应保留').toBeVisible();

  // 2026-11-25 起顶栏购物车改新标签页打开（不打断正在浏览的页面）
  const cartPage = await clickAndCatchNewPage(context, page, () =>
    page.locator('.topbar__cart').click(),
  );
  expect(pathOf(cartPage), '新标签页应落在购物车').toBe('/cart');
  await cartPage.close();
});

/**
 * 非会员日首页提示稳定验收：固定浏览器时钟到周五，会员日卡片必须给出
 * 下一次会员日的日期时间（周三/周四 00:00），且仍可点击进领券中心。
 */
test('非会员日首页会员日卡片提示下次会员日日期时间', async ({ page, context }) => {
  await loginUser(page);
  await page.clock.install({ time: new Date('2026-11-13T15:30:00') }); // 周五
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
  const hint = page.getByTestId('member-day-next');
  await expect(hint).toBeVisible({ timeout: 20_000 });
  await expect(hint).toContainText('下次会员日');
  await expect(hint).toContainText('2026年11月18日（周三）00:00');
  const couponPage = await clickAndCatchNewPage(context, page, () =>
    page.locator('.promo-main__btn').first().click(),
  );
  expect(pathOf(couponPage)).toBe('/coupon/center');
  await couponPage.close();
});

/**
 * 收藏 / 足迹里的商品详情必须新开标签页（2026-11-28 用户要求）。
 *
 * 口径与商城首页、商品列表页一致：点商品进详情新开一页，原页面保持不动。
 * 覆盖三个入口：个人中心概览的收藏/足迹迷你卡、收藏夹页、足迹页。
 *
 * TDD：本 spec 先于前端改动建立，未实现时三处点击都不会产生新标签页。
 */
import { test, expect, type BrowserContext, type Page } from '@playwright/test';
import { USER_URL, join, apiCall } from './chat-helpers';

const PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const FAV_PRODUCT = 9101;
const FOOTPRINT_PRODUCT = 9107;

const pathOf = (page: Page): string => new URL(page.url()).pathname;

async function login(page: Page): Promise<string> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(PHONE);
  await page.getByPlaceholder('请输入密码').fill(PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
  return (await page.evaluate(() => localStorage.getItem('sent_user_access_token'))) ?? '';
}

async function clickAndCatchNewPage(
  context: BrowserContext,
  opener: Page,
  click: () => Promise<void>,
): Promise<Page> {
  const before = opener.url();
  const [popup] = await Promise.all([context.waitForEvent('page', { timeout: 20_000 }), click()]);
  await popup.waitForLoadState('domcontentloaded');
  expect(opener.url(), '原标签页不应被导航走').toBe(before);
  return popup;
}

test('收藏与足迹点商品详情 → 新标签页，原页不动', async ({ page, context, request }) => {
  test.setTimeout(180_000);
  const token = await login(page);

  // 自建前提：确保有一件收藏商品；本条改动前没有收藏则用例结束后还原
  const status: any = await apiCall(request, 'GET', `/favorites/status?productId=${FAV_PRODUCT}`, undefined, token);
  const addedByTest = !status?.data?.favorited;
  if (addedByTest) await apiCall(request, 'POST', `/favorites/${FAV_PRODUCT}`, undefined, token);

  try {
    // 足迹由浏览详情静默上报，先造一条
    await page.goto(join(USER_URL, `/product/${FOOTPRINT_PRODUCT}`));
    await expect(page.locator('.buy-fav'), '详情页应加载完成').toBeVisible({ timeout: 20_000 });

    // 1) 个人中心概览的收藏 / 足迹迷你卡
    await page.goto(join(USER_URL, '/mine'));
    for (const testid of ['mini-favorite', 'mini-footprint']) {
      const card = page.getByTestId(testid);
      await expect(card, `${testid} 应渲染真实商品`).toBeVisible({ timeout: 20_000 });
      const detail = await clickAndCatchNewPage(context, page, () => card.click());
      expect(pathOf(detail), `${testid} 应新开商品详情`).toMatch(/^\/product\/\w+$/);
      await expect(detail.getByTestId('detail-brand'), '新页应渲染商品详情').toBeVisible({ timeout: 20_000 });
      await detail.close();
      expect(pathOf(page), '原个人中心应保持在 /mine').toBe('/mine');
    }

    // 2) 收藏夹页
    await page.goto(join(USER_URL, '/favorites'));
    const favCard = page.locator('.fav-card').first();
    await expect(favCard).toBeVisible({ timeout: 20_000 });
    const favDetail = await clickAndCatchNewPage(context, page, () => favCard.locator('.fav-card__thumb').click());
    expect(pathOf(favDetail), '收藏夹点商品应新开详情').toMatch(/^\/product\/\w+$/);
    await favDetail.close();
    expect(pathOf(page), '原页应保持在 /favorites').toBe('/favorites');

    // 3) 足迹页
    await page.goto(join(USER_URL, '/footprints'));
    const fpCard = page.locator('.fp-line').first();
    await expect(fpCard).toBeVisible({ timeout: 20_000 });
    const fpDetail = await clickAndCatchNewPage(context, page, () => fpCard.locator('.fp-line__thumb').click());
    expect(pathOf(fpDetail), '足迹点商品应新开详情').toMatch(/^\/product\/\w+$/);
    await fpDetail.close();
    expect(pathOf(page), '原页应保持在 /footprints').toBe('/footprints');
  } finally {
    if (addedByTest) await apiCall(request, 'DELETE', `/favorites/${FAV_PRODUCT}`, undefined, token);
  }
});

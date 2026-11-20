/**
 * 商城首页「猜你喜欢」无限滚动验收（TDD 红→绿）。
 *
 * 用户诉求：下拉到商品列表底部时应继续加载下一页商品，而不是拉到底就再也拉不动。
 *
 * 断言口径：
 *   1) 首屏只请求第 1 页，卡片数 = 一页条数；
 *   2) 滚到页面底部 → 自动发出第 2 页请求，卡片数增加；
 *   3) 一直下拉到没有更多数据 → 出现「没有更多了」终态提示，且卡片总数 = 接口 total；
 *   4) 追加过程中不出现重复商品（去重正确）；
 *   5) 全程无 console error。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

/** 演示数据的商品图是虚构域名，加载失败属预期噪音，不计入 console error。 */
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

test('商城首页下拉到底自动加载更多商品', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  // 记录商品列表请求（page 参数）与接口返回的总条数
  const productReqs: string[] = [];
  let totalFromApi = 0;
  page.on('response', async (r) => {
    if (r.request().method() !== 'GET' || !/\/api\/products(\?|$)/.test(r.url())) return;
    productReqs.push(r.url());
    const body: any = await r.json().catch(() => null);
    if (body?.data?.total != null) totalFromApi = Number(body.data.total);
  });

  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  const cards = page.locator('.home-section .good-card');
  await expect(cards.first(), '首页应渲染出猜你喜欢商品').toBeVisible({ timeout: 20_000 });

  // 1) 首屏只有第 1 页
  const firstCount = await cards.count();
  expect(firstCount, '首屏应有商品').toBeGreaterThan(0);
  expect(
    productReqs.some((u) => /[?&]page=1(&|$)/.test(u)),
    `首屏应请求第 1 页，实际请求：${productReqs.join(' | ')}`,
  ).toBe(true);
  expect(totalFromApi, '商品总数必须多于首屏条数，否则无法验证无限滚动').toBeGreaterThan(firstCount);

  // 2) 滚到底 → 自动拉第 2 页并追加
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await expect
    .poll(async () => cards.count(), { timeout: 25_000, message: '滚到底部后商品数量应增加' })
    .toBeGreaterThan(firstCount);
  expect(
    productReqs.some((u) => /[?&]page=2(&|$)/.test(u)),
    `滚到底部应触发第 2 页请求，实际请求：${productReqs.join(' | ')}`,
  ).toBe(true);

  // 3) 持续下拉到终态
  const endHint = page.locator('.home-section .list-end');
  for (let i = 0; i < 15; i += 1) {
    if (await endHint.isVisible().catch(() => false)) break;
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    await page.waitForTimeout(700);
  }
  await expect(endHint, '全部加载完后应展示「没有更多了」').toBeVisible({ timeout: 20_000 });
  await expect(endHint).toContainText('没有更多');

  // 4) 最终条数 = 接口总数，且商品不重复
  await expect(cards, '最终卡片数应等于接口返回的总条数').toHaveCount(totalFromApi, { timeout: 15_000 });
  // 同名不同价的在售条目是合法的，用整卡文本（名称+价格）判重，避免误伤
  const cardTexts = await cards.allInnerTexts();
  expect(new Set(cardTexts).size, `商品出现重复：${cardTexts.length} 条 / ${new Set(cardTexts).size} 种`)
    .toBe(cardTexts.length);

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

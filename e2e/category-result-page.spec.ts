/**
 * 分类结果页验收（TDD 红→绿）。
 *
 * 用户诉求：点首页的商品分类后进入一个独立的分类页面（仿淘宝分类结果页）：
 *   顶部标题 + 「你想找」分类词条行 + 排序工具栏，下面是整页商品网格。
 *
 * 断言口径：
 *   1) 点「篮球」→ 路由切到 /products?categoryId=3（独立页面，不是首页内筛选）；
 *   2) 标题显示「篮球」，计数显示「共 13 件」；
 *   3) 出现分类词条行，当前分类高亮，可点其它分类切换；
 *   4) 商品网格为 5 列（淘宝式密度）；
 *   5) 无限滚动加载到底，卡片数 = 该分类总数；
 *   6) 排序切换后仍是完整列表；
 *   7) 全程无 console error。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

/** 演示数据：球类运动=1(15) / 足球=2(17) / 篮球=3(13) / 羽毛球=4(16) */
const BASKETBALL = { name: '篮球', id: 3, count: 13 };
const FOOTBALL = { name: '足球', id: 2, count: 17 };

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

/** 反复下拉直到出现「没有更多了」或达到上限 */
async function scrollToEnd(page: Page): Promise<void> {
  const end = page.locator('.category-page .list-end');
  for (let i = 0; i < 15; i += 1) {
    if (await end.isVisible().catch(() => false)) return;
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    await page.waitForTimeout(600);
  }
}

test('点分类进入独立分类页，词条切换与无限滚动都正常', async ({ page, context }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
  await expect(page.locator('.cat-nav__item').first()).toBeVisible({ timeout: 20_000 });

  // 1) 点「篮球」→ 在新标签页打开独立分类页（原首页不动）
  const [catPage] = await Promise.all([
    context.waitForEvent('page', { timeout: 20_000 }),
    page.locator('.cat-nav__item', { hasText: BASKETBALL.name }).first().click(),
  ]);
  catPage.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });
  await catPage.waitForLoadState('domcontentloaded');
  await expect(catPage, '点分类后应在新标签页打开独立的分类结果页').toHaveURL(
    new RegExp(`/products\\?categoryId=${BASKETBALL.id}`),
    { timeout: 20_000 },
  );

  const cards = catPage.locator('.category-page .good-card');
  await expect(catPage.locator('.category-page .cat-result__title'), '分类页标题').toHaveText(
    BASKETBALL.name,
    { timeout: 20_000 },
  );
  await expect(catPage.locator('.category-page .cat-result__count')).toContainText(
    `共 ${BASKETBALL.count} 件`,
  );

  // 2) 「你想找」词条行：4 个分类，当前分类高亮
  const chips = catPage.locator('.category-page .cat-chip');
  await expect(chips, '应展示分类词条行').toHaveCount(4);
  await expect(chips.filter({ hasText: BASKETBALL.name }).first()).toHaveClass(
    /cat-chip--active/,
  );

  // 3) 5 列网格（对齐淘宝分类页密度）
  const cols = await catPage
    .locator('.category-page .goods-grid')
    .evaluate((el) => getComputedStyle(el).gridTemplateColumns.split(' ').filter(Boolean).length);
  expect(cols, '商品网格应为 5 列（淘宝式密度）').toBe(5);

  // 4) 无限滚动到底 → 卡片数 = 分类总数
  await scrollToEnd(catPage);
  await expect(cards, `「${BASKETBALL.name}」应完整加载 ${BASKETBALL.count} 件`).toHaveCount(
    BASKETBALL.count,
    { timeout: 25_000 },
  );

  // 5) 点词条切到「足球」：URL、标题、计数、列表全部随之更新
  await chips.filter({ hasText: FOOTBALL.name }).first().click();
  await expect(catPage).toHaveURL(new RegExp(`/products\\?categoryId=${FOOTBALL.id}`), {
    timeout: 20_000,
  });
  await expect(catPage.locator('.category-page .cat-result__title')).toHaveText(FOOTBALL.name);
  await expect(catPage.locator('.category-page .cat-result__count')).toContainText(
    `共 ${FOOTBALL.count} 件`,
  );
  await scrollToEnd(catPage);
  await expect(cards, `「${FOOTBALL.name}」应完整加载 ${FOOTBALL.count} 件`).toHaveCount(
    FOOTBALL.count,
    { timeout: 25_000 },
  );

  // 6) 排序：点「销量」后仍展示完整列表
  const salesTab = catPage.locator('.category-page .cat-sort__tab', { hasText: '销量' }).first();
  await salesTab.click();
  await expect(salesTab).toHaveClass(/cat-sort__tab--active/);
  await expect(cards, '排序后列表条数不应丢失').toHaveCount(FOOTBALL.count, { timeout: 25_000 });
  // 销量降序：第一张卡片的销量不小于最后一张
  const salesTexts = await cards.locator('.good-card__sales').allInnerTexts();
  const nums = salesTexts
    .map((t) => Number(t.replace(/[^\d]/g, '')))
    .filter((n) => Number.isFinite(n) && n > 0);
  if (nums.length > 1) {
    expect(nums[0], `销量排序应降序，实际：${nums.slice(0, 5).join(',')}`).toBeGreaterThanOrEqual(
      nums[nums.length - 1],
    );
  }

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

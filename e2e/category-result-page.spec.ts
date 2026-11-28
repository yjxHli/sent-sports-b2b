/**
 * 分类结果页验收（TDD 红→绿）。
 *
 * 用户诉求：点首页的商品分类后进入一个独立的分类页面（仿淘宝分类结果页）：
 *   顶部标题 + 「你想找」分类词条行 + 排序工具栏，下面是整页商品网格。
 *
 * 断言口径：
 *   1) 点「篮球」→ 路由切到 /products?categoryId=3（独立页面，不是首页内筛选）；
 *   2) 标题显示「篮球」，计数与「该分类接口总数」一致，卡片数也一致；
 *   3) 出现分类词条行，当前分类高亮，可点其它分类切换；
 *   4) 商品网格为 6 列（设计决策变更：适配 1440 版心，对齐淘宝信息流密度）；
 *   5) 无限滚动加载到底，卡片数 = 该分类总数；
 *   6) 排序切换后仍是完整列表；
 *   7) 全程无 console error。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

/**
 * 列数断言依赖容器宽度：`--sent-container` 已改为 1440，只有视口 ≥1388 时
 * 容器才能撑满 1440 并排出 6 列（1440 = 6×216 + 5×12 的下限为 1356）。
 * 所以本文件统一在大屏视口下跑，与淘宝 6 列同口径；默认 1280 视口会自适应降为 5 列。
 */
test.use({ viewport: { width: 1600, height: 1000 } });

/**
 * 演示数据里固定存在的两个分类。**数量不写死**：V50 把已有商品重新按类型归类，
 * 分类计数会随种子/管理端增删商品变化；这里改成「以接口返回的 total 为准」，
 * 断言 UI 与服务端计数一致（这才是这条用例真正要守的契约）。
 */
const BASKETBALL = { name: '篮球', id: 3 };
const FOOTBALL = { name: '足球', id: 2 };

interface CategoryStat {
  /** 分类总数（后端分页 total） */
  total: number;
  /** 分类词条数（/api/categories 总条数） */
  categoryCount: number;
}

async function categoryStat(page: Page, categoryId: number): Promise<CategoryStat> {
  const [list, cats] = await Promise.all([
    page.request.get(`${USER_URL.replace(/\/+$/, '')}/api/products?page=1&size=1&categoryId=${categoryId}`, {
      failOnStatusCode: false,
    }),
    page.request.get(`${USER_URL.replace(/\/+$/, '')}/api/categories`, { failOnStatusCode: false }),
  ]);
  const listBody: any = await list.json().catch(() => null);
  const catBody: any = await cats.json().catch(() => null);
  expect(listBody?.code, `分类商品查询失败：${JSON.stringify(listBody)}`).toBe(0);
  expect(catBody?.code, `分类字典查询失败：${JSON.stringify(catBody)}`).toBe(0);
  return {
    total: Number(listBody?.data?.total ?? 0),
    categoryCount: (catBody?.data ?? []).length,
  };
}

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
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
  const basketStat = await categoryStat(page, BASKETBALL.id);
  const footballStat = await categoryStat(page, FOOTBALL.id);
  expect(basketStat.total, '篮球分类应有商品').toBeGreaterThan(0);
  expect(footballStat.total, '足球分类应有商品').toBeGreaterThan(0);

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
    `共 ${basketStat.total} 件`,
  );

  // 2) 「你想找」词条行：条数与分类字典一致，当前分类高亮
  const chips = catPage.locator('.category-page .cat-chip');
  await expect(chips, '分类词条数应与 /api/categories 一致').toHaveCount(basketStat.categoryCount);
  await expect(chips.filter({ hasText: BASKETBALL.name }).first()).toHaveClass(
    /cat-chip--active/,
  );

  // 3) 6 列网格（设计决策变更：适配 1440 版心，对齐淘宝信息流密度）
  const cols = await catPage
    .locator('.category-page .goods-grid')
    .evaluate((el) => getComputedStyle(el).gridTemplateColumns.split(' ').filter(Boolean).length);
  expect(cols, '商品网格应为 6 列（设计决策变更：适配 1440 版心）').toBe(6);

  // 4) 无限滚动到底 → 卡片数 = 分类总数
  await scrollToEnd(catPage);
  await expect(cards, `「${BASKETBALL.name}」应完整加载 ${basketStat.total} 件`).toHaveCount(
    basketStat.total,
    { timeout: 25_000 },
  );

  // 5) 点词条切到「足球」：URL、标题、计数、列表全部随之更新
  await chips.filter({ hasText: FOOTBALL.name }).first().click();
  await expect(catPage).toHaveURL(new RegExp(`/products\\?categoryId=${FOOTBALL.id}`), {
    timeout: 20_000,
  });
  await expect(catPage.locator('.category-page .cat-result__title')).toHaveText(FOOTBALL.name);
  await expect(catPage.locator('.category-page .cat-result__count')).toContainText(
    `共 ${footballStat.total} 件`,
  );
  await scrollToEnd(catPage);
  await expect(cards, `「${FOOTBALL.name}」应完整加载 ${footballStat.total} 件`).toHaveCount(
    footballStat.total,
    { timeout: 25_000 },
  );

  // 6) 排序：点「销量」后仍展示完整列表
  const salesTab = catPage.locator('.category-page .cat-sort__tab', { hasText: '销量' }).first();
  await salesTab.click();
  await expect(salesTab).toHaveClass(/cat-sort__tab--active/);
  await expect(cards, '排序后列表条数不应丢失').toHaveCount(footballStat.total, { timeout: 25_000 });
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

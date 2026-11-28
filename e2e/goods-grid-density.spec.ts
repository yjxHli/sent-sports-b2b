/**
 * 商品网格密度守护（TDD 红→绿）。
 *
 * 设计决策变更：版心 `--sent-container` 由 1200 → 1440，全站商品网格统一为
 * `repeat(auto-fill, minmax(216px, 1fr))`，从而在 1440 容器下排出 6 列
 * （对齐淘宝首页信息流密度），且任何窗口宽度下卡片都不窄于 216px。
 *
 * 本文件守三件事：
 *   1) 1440 容器 → 6 列（分类页/首页同一口径，不允许一页 6 一页 5）；
 *   2) 单卡宽 ≥ 216（auto-fill 下限），1440 下应落在 224~240；
 *   3) `.good-card__tags` 的三件套（包邮 / UV定制 / 已售 N）不被裁切。
 *
 * 第 3 条是这一轮真正要守的：卡片变窄时 `flex-wrap: nowrap; overflow: hidden`
 * 会把「已售」直接切掉，页面不报错、截图也未必看得出来，只有量 scrollWidth 才抓得住。
 *
 * 视口口径：列数由容器宽推导，1440 容器要求视口 ≥1388（1440 = 6×216 + 5×12）。
 * 因此 6 列用例统一在 1600 视口跑；另有一条 1280 视口用例验证"自动降列但不缩到 216 以下"。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

/** 卡片最小宽度（与 CSS 里 minmax(216px, 1fr) 的下限一致） */
const MIN_CARD = 216;

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

/** 读取网格实际排出的列数（computedStyle 的轨道列表长度） */
async function columnCount(page: Page, gridSelector: string): Promise<number> {
  return page
    .locator(gridSelector)
    .first()
    .evaluate((el) => getComputedStyle(el).gridTemplateColumns.split(' ').filter(Boolean).length);
}

/** 读取首张卡片宽度 */
async function firstCardWidth(page: Page, cardSelector: string): Promise<number> {
  const box = await page.locator(cardSelector).first().boundingBox();
  expect(box, '应至少渲染一张商品卡').not.toBeNull();
  return Math.round(box!.width);
}

/** 所有卡片的标签行一律不得裁字（scrollWidth 不得超出 clientWidth） */
async function expectTagsNotClipped(page: Page): Promise<void> {
  const clipped = await page.locator('.good-card__tags').evaluateAll((els) =>
    els
      .map((el) => {
        const node = el as HTMLElement;
        return {
          text: (node.innerText || '').replace(/\s+/g, ' ').trim(),
          scroll: node.scrollWidth,
          client: node.clientWidth,
        };
      })
      .filter((x) => x.scroll > x.client + 1),
  );
  expect(
    clipped,
    `以下卡片的「包邮 / UV定制 / 已售」被裁切（scrollWidth > clientWidth）：${JSON.stringify(clipped)}`,
  ).toEqual([]);
}

/** 版心加宽后不得出现横向滚动条 */
async function expectNoHorizontalScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  );
  expect(overflow, '页面出现横向滚动条，说明版心宽度超出了视口').toBeLessThanOrEqual(1);
}

test.describe('1440 版心 · 1600 视口', () => {
  test.use({ viewport: { width: 1600, height: 1000 } });

  test('首页猜你喜欢为 6 列，卡片不窄于 216 且标签不裁字', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', (m) => {
      if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
    });

    await loginUser(page);
    await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
    const grid = '.home-section .goods-grid';
    await expect(page.locator(`${grid} .good-card`).first()).toBeVisible({ timeout: 20_000 });

    expect(await columnCount(page, grid), '首页「猜你喜欢」应为 6 列（1440 版心）').toBe(6);

    const width = await firstCardWidth(page, `${grid} .good-card`);
    expect(width, `首屏卡片宽 ${width}px，1440 版心下应落在 224~240`).toBeGreaterThanOrEqual(224);
    expect(width, `首屏卡片宽 ${width}px，1440 版心下应落在 224~240`).toBeLessThanOrEqual(240);

    await expectTagsNotClipped(page);
    await expectNoHorizontalScroll(page);
    expect(errors, `首页出现 console error：${errors.join(' | ')}`).toEqual([]);
  });

  test('分类结果页为 6 列，卡片不窄于 216 且标签不裁字', async ({ page }) => {
    await loginUser(page);
    await page.goto(`${USER_URL.replace(/\/+$/, '')}/products?categoryId=3`);
    const grid = '.category-page .goods-grid';
    await expect(page.locator(`${grid} .good-card`).first()).toBeVisible({ timeout: 20_000 });

    expect(await columnCount(page, grid), '分类页商品网格应为 6 列（1440 版心）').toBe(6);
    expect(
      await firstCardWidth(page, `${grid} .good-card`),
      '分类页卡片宽应不低于 216px',
    ).toBeGreaterThanOrEqual(MIN_CARD);

    await expectTagsNotClipped(page);
    await expectNoHorizontalScroll(page);
  });
});

test.describe('自适应降列 · 1280 视口', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('窄屏自动降为 5 列，但卡片仍不窄于 216 且标签不裁字', async ({ page }) => {
    await loginUser(page);
    await page.goto(`${USER_URL.replace(/\/+$/, '')}/products?categoryId=3`);
    const grid = '.category-page .goods-grid';
    await expect(page.locator(`${grid} .good-card`).first()).toBeVisible({ timeout: 20_000 });

    expect(await columnCount(page, grid), '1280 视口（容器 1248）应自动降为 5 列').toBe(5);
    expect(
      await firstCardWidth(page, `${grid} .good-card`),
      '降列后卡片宽应不低于 216px（auto-fill 的下限）',
    ).toBeGreaterThanOrEqual(MIN_CARD);

    await expectTagsNotClipped(page);
    await expectNoHorizontalScroll(page);
  });
});

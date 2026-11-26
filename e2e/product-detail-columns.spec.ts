/**
 * 商城端商品详情页 · 淘宝式两栏布局（2026-11-25 用户诉求，TDD 红→绿）。
 *
 * 诉求原话：
 *   1) 顶部固定的商标与搜索框删掉，改在商品详情页左上角放商城名「森特」，中文名下方放英文；
 *   2) 商品图片占 60%、购买框占 40%，两个框都各自大一点；
 *   3) 下滑页面时购买框一直存在（吸顶）；
 *   4) 「商品详情 / 用户评价」跟商品图片放在同一列，往下滑也保持这个摆法。
 *
 * 断言口径（用户视角，不绑内部实现）：
 *   * 详情页没有整站搜索区，但左上角有「森特 / SENT SPORTS」品牌块；
 *   * 主图列 : 购买列 ≈ 60 : 40（±3 个百分点），且两栏都比旧版大（旧版 520 / 420）；
 *   * `.buy` 是 sticky，滚动 900px 后仍在视口内、买按钮仍可见；
 *   * Tab 条与详情/评价面板都落在主图列内，滚动后仍在同一列，购买列始终在它右侧。
 *
 * 商品从接口现挑（`pickSellableSku`），不写死 id。
 */
import { test, expect, type Page } from '@playwright/test';
import { pickSellableSku } from './pick-product';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const USER_NAME = process.env.E2E_USER_NAME ?? '13800138000';
const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

async function login(page: Page): Promise<void> {
  await page.goto(USER_URL, { waitUntil: 'networkidle' });
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('input').first().fill(USER_NAME);
    await page.locator('input[type="password"]').fill(USER_PASSWORD);
    await page.getByRole('button', { name: '登录' }).click();
    await expect(page.locator('input[type="password"]')).toHaveCount(0, { timeout: 15000 });
  }
}

interface Box {
  x: number;
  right: number;
  top: number;
  w: number;
  h: number;
}

interface Metrics {
  searchbar: number;
  brand: Box | null;
  brandText: string;
  left: Box | null;
  gallery: Box | null;
  thumb: Box | null;
  buy: Box | null;
  buyPosition: string;
  tabs: Box | null;
  tabsPosition: string;
  panel: Box | null;
  scrollY: number;
  maxScroll: number;
}

async function measure(page: Page): Promise<Metrics> {
  return page.evaluate(() => {
    const box = (sel: string): Box | null => {
      const el = document.querySelector(sel);
      if (!el) return null;
      const b = el.getBoundingClientRect();
      return {
        x: Math.round(b.x), right: Math.round(b.right), top: Math.round(b.top),
        w: Math.round(b.width), h: Math.round(b.height),
      };
    };
    const buyEl = document.querySelector('.buy');
    const tabsEl = document.querySelector('.detail-tabs');
    const brand = document.querySelector('[data-testid="detail-brand"]');
    return {
      searchbar: document.querySelectorAll('.searchbar').length,
      brand: box('[data-testid="detail-brand"]'),
      brandText: (brand?.textContent ?? '').replace(/\s+/g, ' ').trim(),
      left: box('.detail-left'),
      gallery: box('.gallery'),
      thumb: box('.gallery__main .thumb'),
      buy: box('.buy'),
      buyPosition: buyEl ? getComputedStyle(buyEl).position : '',
      tabs: box('.detail-tabs'),
      tabsPosition: tabsEl ? getComputedStyle(tabsEl).position : '',
      panel: box('[data-testid="detail-panel-desc"]'),
      scrollY: Math.round(window.scrollY),
      maxScroll: Math.round(document.documentElement.scrollHeight - window.innerHeight),
    };
  });
}

test.describe('商品详情页 · 淘宝式两栏', () => {
  test('品牌块在左上、主图 60% / 购买 40%、购买栏吸顶、详情与评价同列', async ({ page, request }) => {
    test.setTimeout(90_000);
    // 视口 900 高：购买栏被拉到与主图等高（≈760）后，900 下 CTA 仍在屏内；
    // 720 会把卡片底部（立即购买）顶出视口，那是视口太矮，不是布局问题。
    await page.setViewportSize({ width: 1440, height: 900 });
    const picked = await pickSellableSku(request, 1);

    await login(page);
    await page.goto(`${USER_URL}/product/${picked.productId}`, { waitUntil: 'networkidle' });
    await expect(page.locator('.detail-main')).toBeVisible({ timeout: 15_000 });

    const top = await measure(page);

    // ① 整站搜索区已从详情页移除；左上角是「森特 / SENT SPORTS」品牌块
    expect(top.searchbar, '详情页不应再有整站品牌 + 搜索区').toBe(0);
    expect(top.brand, '详情页左上角应有品牌块').not.toBeNull();
    expect(top.brandText, '品牌块中文名应是「森特」').toContain('森特');
    expect(top.brandText.toUpperCase(), '中文名下方应有英文 SENT SPORTS').toContain('SENT SPORTS');
    expect(top.brand!.top, '品牌块应贴页面顶部（在面包屑/主图之上）').toBeLessThan(top.gallery!.top);
    expect(Math.abs(top.brand!.x - top.left!.x), '品牌块应左对齐主图列').toBeLessThanOrEqual(4);

    // ② 主图列 60% / 购买列 40%，且两栏都比旧版（520 / 420）更大
    const gap = top.buy!.x - (top.left!.x + top.left!.w);
    const total = top.left!.w + gap + top.buy!.w;
    const leftRatio = top.left!.w / total;
    expect(leftRatio, `主图列占比 ${(leftRatio * 100).toFixed(1)}%（应 ≈60%）`).toBeGreaterThan(0.57);
    expect(leftRatio).toBeLessThan(0.63);
    expect(top.left!.w, `主图列 ${top.left!.w}px 偏窄（应 ≥700）`).toBeGreaterThanOrEqual(700);
    expect(top.buy!.w, `购买列 ${top.buy!.w}px 偏窄（应 ≥470）`).toBeGreaterThanOrEqual(470);
    expect(top.thumb!.w, `主图 ${top.thumb!.w}px 应比旧版 520 更大（≥600）`).toBeGreaterThanOrEqual(600);
    expect(top.buy!.x, '购买列应在主图列右侧').toBeGreaterThan(top.left!.x + top.left!.w);

    // ③ 「商品详情 / 用户评价」与主图同列：Tab 条与面板都不越出主图列
    expect(top.tabs, 'Tab 条应存在').not.toBeNull();
    expect(Math.abs(top.tabs!.x - top.left!.x), 'Tab 条应左对齐主图列').toBeLessThanOrEqual(2);
    expect(top.tabs!.w, 'Tab 条不应越出主图列').toBeLessThanOrEqual(top.left!.w + 2);
    expect(top.tabs!.top, 'Tab 条应在主图下方').toBeGreaterThan(top.gallery!.top);
    expect(top.panel, '默认应显示商品详情面板').not.toBeNull();
    expect(Math.abs(top.panel!.x - top.left!.x), '详情面板应左对齐主图列').toBeLessThanOrEqual(2);
    expect(top.panel!.w, '详情面板不应越出主图列').toBeLessThanOrEqual(top.left!.w + 2);

    // ④ 购买栏 sticky
    expect(top.buyPosition, '购买栏必须是 sticky 才能边滚边买').toBe('sticky');
    expect(top.tabsPosition, 'Tab 条也要 sticky：往下滑时分类入口一直可点').toBe('sticky');
    expect(top.maxScroll, '详情页应可滚动（否则吸顶断言无意义）').toBeGreaterThan(200);

    // 滚到「Tab 条已越过视口顶 250px」的位置，而不是滚到页面最底部：
    // 最底部时 sticky 会被容器边界顶出去（正常行为），测不出「一直吸着」；商品详情长短也不一，写死 900 会滚不到位。
    await page.evaluate(() => {
      const tabs = document.querySelector('.detail-tabs');
      const naturalTop = tabs ? tabs.getBoundingClientRect().top + window.scrollY : 0;
      const maxScroll = document.documentElement.scrollHeight - window.innerHeight;
      window.scrollTo(0, Math.min(maxScroll, naturalTop - 250));
    });
    await page.waitForTimeout(300);
    const scrolled = await measure(page);

    expect(scrolled.scrollY, '应已滚动').toBeGreaterThan(200);
    // 购买栏：滚动后仍贴在视口顶部（sticky 生效），不被内容带走
    expect(scrolled.buy!.top, `滚动后购买栏应吸在视口顶部（top=${scrolled.buy!.top}）`)
      .toBeGreaterThanOrEqual(0);
    expect(scrolled.buy!.top, `滚动后购买栏不应被滚走（top=${scrolled.buy!.top}）`).toBeLessThan(200);
    await expect(page.locator('.buy-btn--now'), '滚动后「立即购买」仍应可点').toBeVisible();
    // 滚动后购买列仍在主图列右侧（吸顶不能让它跑到左边盖住内容）
    expect(scrolled.buy!.x).toBeGreaterThan(scrolled.left!.x + scrolled.left!.w);

    // Tab 条：滚动后左边界与宽度不变（「往下滑也保持这个摆法」），
    // 且位置符合 sticky 契约（原位还没滚到视口顶时按 1:1 上移，滚过之后夹在 0）
    expect(Math.abs(scrolled.tabs!.x - scrolled.left!.x), '滚动后 Tab 条仍应左对齐主图列')
      .toBeLessThanOrEqual(2);
    expect(scrolled.tabs!.w, '滚动后 Tab 条仍不应越出主图列').toBeLessThanOrEqual(scrolled.left!.w + 2);
    const expectTabsTop = Math.max(0, top.tabs!.top - scrolled.scrollY);
    expect(
      Math.abs(scrolled.tabs!.top - expectTabsTop),
      `Tab 条没吸住：实测 top=${scrolled.tabs!.top}，sticky 应为 ${expectTabsTop}`,
    ).toBeLessThanOrEqual(2);

    // ⑤ 切到「用户评价」：面板仍在主图列内，购买栏仍在右侧吸着
    await page.getByTestId('detail-tab-review').click();
    await expect(page.getByTestId('detail-panel-review')).toBeVisible({ timeout: 15_000 });
    const review = await page.evaluate(() => {
      const r = (sel: string) => {
        const el = document.querySelector(sel);
        if (!el) return null;
        const b = el.getBoundingClientRect();
        return { x: Math.round(b.x), right: Math.round(b.right), top: Math.round(b.top), w: Math.round(b.width) };
      };
      return { panel: r('[data-testid="detail-panel-review"]'), left: r('.detail-left'), buy: r('.buy'), tabs: r('.detail-tabs') };
    });
    expect(review.panel, '用户评价面板应渲染').not.toBeNull();
    expect(Math.abs(review.panel!.x - review.left!.x), '评价面板应与主图同列').toBeLessThanOrEqual(2);
    expect(review.panel!.w, '评价面板不应越出主图列').toBeLessThanOrEqual(review.left!.w + 2);
    expect(review.buy!.x).toBeGreaterThan(review.left!.x + review.left!.w);
  });

  test('购买框放大到与主图等高、右缘对齐顶栏帮助中心；品牌可回首页，右侧有迷你搜索', async ({ page, request }) => {
    test.setTimeout(90_000);
    await page.setViewportSize({ width: 1440, height: 900 });
    const picked = await pickSellableSku(request, 1);
    const detailUrl = `${USER_URL}/product/${picked.productId}`;

    await login(page);
    await page.goto(detailUrl, { waitUntil: 'networkidle' });
    await expect(page.locator('.detail-main')).toBeVisible({ timeout: 15_000 });

    const m = await page.evaluate(() => {
      const box = (sel: string) => {
        const el = document.querySelector(sel);
        if (!el) return null;
        const b = el.getBoundingClientRect();
        return { x: Math.round(b.x), right: Math.round(b.right), w: Math.round(b.width), h: Math.round(b.height) };
      };
      return {
        detail: box('.detail'),
        thumb: box('.gallery__main .thumb'),
        buy: box('.buy'),
        help: box('.topbar__help-trigger'),
        search: box('[data-testid="detail-mini-search"]'),
      };
    });

    // ① 版心放大到顶栏同宽：右缘正好落在「帮助中心」那一列
    expect(m.detail!.right, `详情页右缘 ${m.detail!.right} 应对齐顶栏帮助中心 ${m.help!.right}`)
      .toBeGreaterThanOrEqual(m.help!.right - 8);
    expect(m.detail!.right).toBeLessThanOrEqual(m.help!.right + 8);
    expect(m.detail!.w, `版心 ${m.detail!.w}px 仍是旧的 1280 窄版心`).toBeGreaterThanOrEqual(1380);

    // ② 购买框更宽（40% 版心）且高度与主图相当（旧版 502×518，主图 684）
    expect(m.buy!.w, `购买框 ${m.buy!.w}px 偏窄`).toBeGreaterThanOrEqual(540);
    expect(m.buy!.h, `购买框高度 ${m.buy!.h}px 与主图 ${m.thumb!.h}px 差距过大`)
      .toBeGreaterThanOrEqual(m.thumb!.h * 0.9);
    expect(m.buy!.h).toBeLessThanOrEqual(m.thumb!.h * 1.1);

    // ③ 品牌行最右侧的迷你搜索：输入框 + 搜索按钮，宽度约为购买框的一半
    const search = page.getByTestId('detail-mini-search');
    await expect(search).toBeVisible();
    const input = search.locator('input');
    const button = search.getByRole('button', { name: '搜索' });
    await expect(input).toBeVisible();
    await expect(button).toBeVisible();
    expect(m.search!.w, `迷你搜索 ${m.search!.w}px 应约为购买框 ${m.buy!.w}px 的一半`)
      .toBeGreaterThanOrEqual(m.buy!.w * 0.42);
    expect(m.search!.w).toBeLessThanOrEqual(m.buy!.w * 0.58);

    // ④ 森特文字点击回商城首页
    const brand = page.getByTestId('detail-brand-home');
    await expect(brand).toContainText('森特');
    await brand.click();
    await expect(page).toHaveURL(/\/(home)(\?|$)/, { timeout: 20_000 });

    // ⑤ 迷你搜索真能搜：提交后跳商品列表并带 keyword
    await page.goto(detailUrl, { waitUntil: 'networkidle' });
    await search.locator('input').fill('排球');
    await button.click();
    await expect(page).toHaveURL(/\/products\?keyword=/, { timeout: 20_000 });
    await expect(page.locator('.searchbar')).toBeVisible();
  });
});

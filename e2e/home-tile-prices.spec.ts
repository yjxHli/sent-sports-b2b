/**
 * 首页促销瓦片「起价」真实性守护（TDD 红→绿）。
 *
 * 背景：`PROMO_TILES` 里曾经写死 `¥151 / ¥182 / ¥329 / ¥89 起`，
 * 而接口实测全站 SKU 真实零售价是 10.00 ~ 40.00 元（中位 19.9）——
 * 差了 4~10 倍，属于必须修的可信度问题。
 *
 * 本文件守两件事：
 *   1) 瓦片上的起价必须落在**接口真实价格区间**内（区间由接口算，不写死）；
 *   2) 四个瓦片的文案（定制队服/跑步装备/健身器材/球类专场）必须还在
 *      —— 它们是 `home-entry-links.spec.ts` 的点击锚点，属于红线。
 *
 * 允许「非金额」文案（如定制服务的「按件计价」），但只要有 ¥ 数字就必须真实。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

/** 四个瓦片的文案：E2E 红线，不允许改 */
const TILE_LABELS = ['定制队服', '跑步装备', '健身器材', '球类专场'];

interface PriceRange {
  min: number;
  max: number;
  skuCount: number;
}

/** 以接口返回的真实零售价计算全站价格区间（元） */
async function realPriceRange(page: Page): Promise<PriceRange> {
  const resp = await page.request.get(
    `${USER_URL.replace(/\/+$/, '')}/api/products?page=1&size=200`,
    { failOnStatusCode: false },
  );
  const body: any = await resp.json().catch(() => null);
  expect(body?.code, `商品列表查询失败：${JSON.stringify(body)}`).toBe(0);

  const prices: number[] = [];
  for (const p of body?.data?.list ?? []) {
    for (const sku of p?.skus ?? []) {
      const yuan = Number(sku?.price?.retailPriceYuan);
      if (Number.isFinite(yuan) && yuan > 0) prices.push(yuan);
    }
  }
  expect(prices.length, '接口应返回带零售价的 SKU').toBeGreaterThan(0);
  prices.sort((a, b) => a - b);
  return { min: prices[0], max: prices[prices.length - 1], skuCount: prices.length };
}

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

test('首页四个促销瓦片的起价必须落在商品真实价格区间内', async ({ page }) => {
  await loginUser(page);
  const range = await realPriceRange(page);

  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
  await expect(page.locator('.promo-tile').first()).toBeVisible({ timeout: 20_000 });

  // 红线：四个瓦片文案必须还在
  for (const label of TILE_LABELS) {
    await expect(
      page.locator('.promo-tile', { hasText: label }).first(),
      `瓦片「${label}」必须存在（E2E 点击锚点）`,
    ).toBeVisible();
  }

  const tiles = page.locator('.promo-tile');
  await expect(tiles, '首页应有 4 个促销瓦片').toHaveCount(TILE_LABELS.length);

  const offending: Array<{ tile: string; text: string; value: number }> = [];
  let pricedTiles = 0;

  for (let i = 0; i < TILE_LABELS.length; i += 1) {
    const tile = tiles.nth(i);
    const raw = (await tile.locator('.promo-tile__price').innerText().catch(() => '')).trim();
    // 「按件计价 / 询价」这类非金额文案是允许的
    const matched = raw.match(/¥\s*([\d.]+)/);
    if (!matched) continue;

    pricedTiles += 1;
    const value = Number(matched[1]);
    if (!Number.isFinite(value) || value < range.min - 0.01 || value > range.max + 0.01) {
      offending.push({ tile: TILE_LABELS[i], text: raw, value });
    }
  }

  expect(
    offending,
    `以下瓦片起价超出了真实区间 ${range.min}~${range.max} 元（共 ${range.skuCount} 个 SKU）：` +
      JSON.stringify(offending),
  ).toEqual([]);

  expect(pricedTiles, '至少 3 个商品类瓦片应展示真实起价（不能全都不显示价格）').toBeGreaterThanOrEqual(3);
});

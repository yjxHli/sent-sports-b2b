/**
 * 秒杀活动（V54）：管理端发布 → 用户端才出现 的端到端闭环。
 *
 * 用户口径（原话）：「秒杀价也是需要管理端那边发布了这个秒杀价的活动，才会出现，平时是没有的」。
 * 因此本用例的核心不是「能建活动」，而是**状态机真的能控制用户端可见性**：
 *
 *   草稿       → 用户端无横幅、按零售价卖
 *   发布(ACTIVE) → 用户端出现秒杀横幅 + 价格区按秒杀价 + 结算试算 sourceCode=FLASH_SALE
 *   下线(OFFLINE) → 横幅消失 + 价格回落零售价 + 试算回到 RETAIL
 *
 * 数据纪律：
 *   - SKU 现场从库里挑（不写死），条件同时满足「有可售库存 / 零售价≥20 元 / 底价留够 5 元空间」；
 *   - 跳过有一客一价的 SKU（一客一价优先级高于秒杀，会让试算来源不是 FLASH_SALE）；
 *   - 结束时无论成败都删掉活动（软删），避免残留活动把其它用例的价格断言打歪。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173/';
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
const USER_NAME = process.env.E2E_USER_NAME ?? '13800138000';
const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';
const STAMP = Date.now().toString().slice(-6);

const join = (base: string, suffix: string): string =>
  `${base.replace(/\/+$/, '')}/${suffix.replace(/^\/+/, '')}`;

function mysqlBin(): string {
  if (process.env.E2E_MYSQL_BIN) return process.env.E2E_MYSQL_BIN;
  return existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql';
}

function sql(statement: string): string {
  return execFileSync(mysqlBin(), [
    '-h127.0.0.1', '-P3306', '-uroot', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
    'sent_dev', '-N', '-B', '-e', statement,
  ], { encoding: 'utf8' }).trim();
}

async function api(
  request: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  data?: unknown,
  token?: string,
): Promise<any> {
  const res = await request.fetch(`${API_URL}${path}`, {
    method,
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

async function tokenOf(
  request: APIRequestContext, aud: 'admin' | 'user', username: string, password: string,
): Promise<string> {
  const login = await api(request, 'POST', '/auth/login', {
    username, password, aud, deviceId: `e2e-flash-${aud}-${Date.now()}`,
  });
  const token = login?.data?.accessToken as string;
  expect(token, `${aud} 登录失败：${JSON.stringify(login)}`).toBeTruthy();
  return token;
}

async function loginAdminUi(page: Page): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  expect((await (await resp).json().catch(() => null))?.code).toBe(0);
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

async function loginUserUi(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(USER_NAME);
  await page.getByPlaceholder('请输入密码').fill(USER_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)/, { timeout: 20_000 });
}

/** LocalDateTime 口径（后端 LocalDateTime 反序列化要求无时区后缀） */
function localDateTime(offsetMs: number): string {
  const d = new Date(Date.now() + offsetMs);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
    + `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** `{"spec":"黑色 / L"}` → `黑色 / L`（详情页规格按钮上的文字） */
function plainSpec(json: string): string {
  const m = /"spec"\s*:\s*"([^"]*)"/.exec(json ?? '');
  return (m?.[1] ?? '').replace(/\\"/g, '"');
}

const yuanInt = (cent: number): number => Math.floor(cent / 100);

test.describe('秒杀活动（V54）', () => {
  test('草稿不生效 → 管理端发布后用户端出现秒杀 → 下线即回落', async ({ page, request }) => {
    test.setTimeout(240_000);

    // ---- 0. 现场挑一个合适的 SKU ----
    const picked = sql(
      "SELECT s.id, s.spu_id, s.retail_price_cent, IFNULL(s.floor_price_cent,0), s.spec_json "
      + 'FROM sku s JOIN product p ON p.id = s.spu_id '
      + 'LEFT JOIN sku_stock st ON st.sku_id = s.id '
      + "WHERE s.status='ON' AND p.deleted=0 AND p.status='ON_SHELF' "
      + 'AND s.retail_price_cent >= 2000 '
      + 'AND IFNULL(s.floor_price_cent,0) <= s.retail_price_cent - 500 '
      + 'AND IFNULL(st.physical_qty - st.locked_qty - st.pending_qty - st.frozen_qty, 0) > 0 '
      + 'AND NOT EXISTS (SELECT 1 FROM customer_sku_price csp WHERE csp.sku_id = s.id) '
      // 企业协议价优先级高于秒杀（结算价会是 AGREEMENT），有生效协议价时本用例的取价断言就不成立
      + 'AND NOT EXISTS (SELECT 1 FROM agreement_price ap WHERE ap.customer_id = 9002 '
      + "  AND (ap.valid_from IS NULL OR ap.valid_from <= NOW(3)) "
      + '  AND (ap.valid_to IS NULL OR ap.valid_to >= NOW(3))) '
      + 'ORDER BY s.id LIMIT 1;',
    );
    test.skip(!picked, '演示库没有可用的 SKU（需零售价≥20 元、有库存、无专属价）');
    const [skuId, productId, retailText, floorText, specJson] = picked.split('\t');
    const retailCent = Number(retailText);
    const floorCent = Number(floorText);
    const flashCent = Math.max(retailCent - 500, floorCent);
    expect(flashCent, '秒杀价必须低于零售价').toBeLessThan(retailCent);
    const specText = plainSpec(specJson);
    const title = `E2E限时秒杀-${STAMP}`;

    const adminToken = await tokenOf(request, 'admin', ADMIN_NAME, ADMIN_PASSWORD);
    const userToken = await tokenOf(request, 'user', USER_NAME, USER_PASSWORD);

    const quoteOf = async (): Promise<any> => {
      const res = await api(request, 'POST', '/cart/checkout-preview', {
        availablePoints: 0,
        freightFreeThresholdCent: 0,
        freightFeeCent: 0,
        items: [{
          skuId: Number(skuId), qty: 1, customerTypeCode: 'PERSON',
          memberDiscountRate: 100, agreementDiscountRate: 100,
          allowStacking: false, ladderTiers: [], customFlag: false,
        }],
      }, userToken);
      return res?.data?.lines?.[0];
    };

    // ---- 1. 建草稿：用户端不应有任何秒杀痕迹 ----
    const created = await api(request, 'POST', '/admin/flash-sales', {
      title,
      startAt: localDateTime(-60_000),
      endAt: localDateTime(2 * 3600_000),
      remark: 'E2E 自动创建，用例结束即删除',
      items: [{ skuId: Number(skuId), flashPriceCent: flashCent }],
    }, adminToken);
    const activityId = created?.data?.activityId as string;
    expect(activityId, `秒杀活动创建失败：${JSON.stringify(created)}`).toBeTruthy();
    expect(created?.data?.status).toBe('DRAFT');

    try {
      const draftQuote = await quoteOf();
      expect(draftQuote?.sourceCode, '草稿态不应影响取价').toBe('RETAIL');

      await loginUserUi(page);
      await page.goto(join(USER_URL, `product/${productId}`));
      await expect(page.locator('.buy__title')).toBeVisible({ timeout: 20_000 });
      await expect(page.locator('[data-testid="flash-sale-banner"]')).toHaveCount(0);

      // ---- 2. 管理端发布 → 用户端出现秒杀 ----
      await loginAdminUi(page);
      await page.goto(join(ADMIN_URL, 'product/flash-sale'));
      const row = page.locator('tr', { hasText: title });
      await expect(row).toHaveCount(1, { timeout: 20_000 });
      await expect(row).toContainText('草稿');

      const published = page.waitForResponse(
        (r) => r.url().includes(`/api/admin/flash-sales/${activityId}/status`) && r.request().method() === 'POST',
        { timeout: 20_000 },
      );
      await row.getByRole('button', { name: '发布', exact: true }).click();
      expect((await (await published).json().catch(() => null))?.code, '发布失败').toBe(0);
      await expect(row).toContainText('进行中', { timeout: 20_000 });

      // 取价链路：结算试算必须用秒杀价（这是「展示价 = 结算价」的硬证据）
      const liveQuote = await quoteOf();
      expect(liveQuote?.sourceCode, '发布后取价来源应为秒杀价').toBe('FLASH_SALE');
      expect(Number(liveQuote?.unitPriceCent)).toBe(flashCent);

      // 用户端展示
      await page.goto(join(USER_URL, `product/${productId}`));
      await expect(page.locator('.buy__title')).toBeVisible({ timeout: 20_000 });
      if (specText) {
        await page.locator('.spec-item', { hasText: specText }).first().click();
      }
      const banner = page.locator('[data-testid="flash-sale-banner"]');
      await expect(banner).toBeVisible();
      await expect(banner).toContainText('限时秒杀');
      await expect(page.locator('[data-testid="flash-sale-price"]')).toContainText(String(yuanInt(flashCent)));
      const shownPrice = Number(
        (await page.locator('.buy__price .buy__num').first().innerText()).replace(/,/g, ''),
      );
      expect(shownPrice, '价格区应显示秒杀价整数部分').toBe(yuanInt(flashCent));
      await expect(page.locator('[data-testid="flash-sale-countdown"]')).toHaveText(/^\d{2}:\d{2}:\d{2}$/);

      // ---- 3. 下线 → 横幅消失、价格回落 ----
      await page.goto(join(ADMIN_URL, 'product/flash-sale'));
      const row2 = page.locator('tr', { hasText: title });
      await expect(row2).toHaveCount(1, { timeout: 20_000 });
      const offline = page.waitForResponse(
        (r) => r.url().includes(`/api/admin/flash-sales/${activityId}/status`) && r.request().method() === 'POST',
        { timeout: 20_000 },
      );
      await row2.getByRole('button', { name: '下线', exact: true }).click();
      expect((await (await offline).json().catch(() => null))?.code, '下线失败').toBe(0);
      await expect(row2).toContainText('已下线', { timeout: 20_000 });

      const offQuote = await quoteOf();
      expect(offQuote?.sourceCode, '下线后应回到零售价').toBe('RETAIL');
      expect(Number(offQuote?.unitPriceCent)).toBe(retailCent);

      await page.goto(join(USER_URL, `product/${productId}`));
      await expect(page.locator('.buy__title')).toBeVisible({ timeout: 20_000 });
      if (specText) {
        await page.locator('.spec-item', { hasText: specText }).first().click();
      }
      await expect(page.locator('[data-testid="flash-sale-banner"]')).toHaveCount(0);
      const backPrice = Number(
        (await page.locator('.buy__price .buy__num').first().innerText()).replace(/,/g, ''),
      );
      expect(backPrice, '下线后价格应回到零售价').toBe(yuanInt(retailCent));
    } finally {
      // 软删活动：即便断言中途失败也不把活跃活动留在库里
      await api(request, 'DELETE', `/admin/flash-sales/${activityId}`, undefined, adminToken);
    }
  });
});

/**
 * 商品详情富内容（V54）：管理端编辑 → 用户端渲染 的端到端闭环。
 *
 * 用户口径：商品图片、款式、详情介绍这些内容必须能在**管理端商品列表的编辑功能**里维护；
 * 用户端详情页按淘宝三列版式把它们真正渲染出来（而不是像改造前那样 4 张缩略图都是同一张主图）。
 *
 * 断言链（全部走真实读写，不 mock）：
 *   1) 管理端编辑页填 卖点副标题 / 商品参数 / 服务承诺 / 文字详情 → 保存成功；
 *   2) 服务端读回（/products/{id}）确认四个字段真的落库（不是仅前端状态变了）；
 *   3) 用户端详情页：副标题、参数宫格、服务承诺标签、详情文本、图集缩略图数量与库内一致；
 *   4) 用例结束用 SQL 把四个字段复位，避免污染其他用例（其它 spec 也会挑这个商品）。
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

/** SQL 字面量：反斜杠与单引号都要转义，否则副标题里一个引号就能把语句打断 */
function lit(value: unknown): string {
  if (value === null || value === undefined) return 'NULL';
  return `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
}

async function api(
  request: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT',
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

async function tokenOf(request: APIRequestContext, aud: 'admin' | 'user', username: string, password: string): Promise<string> {
  const login = await api(request, 'POST', '/auth/login', {
    username, password, aud, deviceId: `e2e-rich-${aud}-${Date.now()}`,
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

test.describe('商品详情富内容（V54）', () => {
  test('管理端维护的副标题/参数/服务承诺/详情，用户端详情页真实渲染', async ({ page, request }) => {
    test.setTimeout(180_000);

    const adminToken = await tokenOf(request, 'admin', ADMIN_NAME, ADMIN_PASSWORD);
    const list = await api(request, 'GET', '/products?page=1&size=20', undefined, adminToken);
    const first = list?.data?.list?.[0] ?? list?.data?.list?.find(() => true);
    test.skip(!first, '演示库没有商品，跳过');
    const productId = String(first.productId);

    const before = (await api(request, 'GET', `/products/${productId}`, undefined, adminToken))?.data;
    expect(before?.productId, '商品详情读取失败').toBeTruthy();
    const galleryCount = (before.images ?? []).length;

    const subtitle = `E2E副标题-${STAMP} · 透气速干`;
    const paramName = `E2E面料${STAMP}`;
    const paramValue = '聚酯纤维 88%';
    const desc = `E2E详情正文-${STAMP}：本商品适用于专业训练与赛事场景。`;

    // ---- 1. 管理端编辑页填写 ----
    await loginAdminUi(page);
    // 必须等详情加载完成再填：编辑页 onMounted 拉 /products/{id} 回填表单，
    // 先填后回填会被覆盖，于是「填了新副标题、存进去的还是旧值」（本用例踩过一次）。
    const detailLoaded = page.waitForResponse(
      (r) => /\/api\/products\/\d+$/.test(r.url()) && r.request().method() === 'GET',
      { timeout: 30_000 },
    );
    await page.goto(join(ADMIN_URL, `product/edit?id=${productId}`));
    await detailLoaded;
    await expect(page.locator('h2.sent-page-title')).toHaveText('编辑商品', { timeout: 20_000 });

    await page.getByPlaceholder('如：透气速干 · 训练首选（详情页标题下方一行）').fill(subtitle);

    await page.getByRole('button', { name: '新增参数' }).click();
    await page.getByPlaceholder('如：面料').last().fill(paramName);
    await page.getByPlaceholder('如：聚酯纤维').last().fill(paramValue);

    // 服务承诺：可搜索 + 可自定义的多选下拉。先填关键字，再从浮层点第一条
    // （浮层挂在 body 上，用 :visible 限定），最后断言标签真的出现在选择器里——
    // 只按 Enter 有时会被 blur 吞掉，保存后发现为空才发现就太晚了。
    const tagPanel = page.locator('.sent-panel', { hasText: '最多 6 个，可输入自定义（回车确认）' });
    await tagPanel.locator('.el-select').first().click();
    await page.keyboard.type('15天价保');
    const created = page.locator('.el-select-dropdown:visible .el-select-dropdown__item', {
      hasText: '15天价保',
    }).first();
    await created.waitFor({ state: 'visible', timeout: 10_000 });
    await created.click();
    await expect(tagPanel.locator('.el-tag', { hasText: '15天价保' })).toBeVisible();
    await page.keyboard.press('Escape');

    await page.getByPlaceholder('纯文本，不会按 HTML 渲染（避免 XSS）').fill(desc);

    const saved = page.waitForResponse(
      (r) => r.url().includes(`/api/admin/products/${productId}`) && r.request().method() === 'PUT',
      { timeout: 30_000 },
    );
    await page.getByRole('button', { name: '保存商品' }).click();
    expect((await (await saved).json().catch(() => null))?.code, '商品保存失败').toBe(0);

    try {
      // ---- 2. 服务端读回：确认真的落库 ----
      const after = (await api(request, 'GET', `/products/${productId}`, undefined, adminToken))?.data;
      expect(after.subtitle).toBe(subtitle);
      expect(after.description).toContain(`E2E详情正文-${STAMP}`);
      expect((after.params ?? []).map((p: any) => p.label)).toContain(paramName);
      expect(after.serviceTags ?? []).toContain('15天价保');

      // ---- 3. 用户端渲染 ----
      await loginUserUi(page);
      await page.goto(join(USER_URL, `product/${productId}`));
      await expect(page.locator('.buy__title')).toBeVisible({ timeout: 20_000 });

      await expect(page.locator('.buy__subtitle')).toContainText(`E2E副标题-${STAMP}`);
      await expect(page.locator('.detail-params')).toBeVisible();
      // 用本轮独有的参数名定位（参数值「聚酯纤维」每轮相同，多次失败残留会命中多张卡）
      const paramCard = page.locator('.detail-params__card', { hasText: paramName });
      await expect(paramCard).toBeVisible();
      await expect(paramCard).toContainText(paramValue);
      await expect(page.locator('.detail-service__tag', { hasText: '15天价保' })).toBeVisible();
      await expect(page.locator('.detail-desc__text')).toContainText(`E2E详情正文-${STAMP}`);

      // 图集：有多少张就渲染多少个缩略图（<2 张时左侧竖排 rail 不出现）
      const railCount = await page.locator('.gallery-rail__item').count();
      expect(railCount, `图集 ${galleryCount} 张，竖排缩略图数量不符`).toBe(galleryCount > 1 ? galleryCount : 0);
      await expect(page.locator('.spec-item').first()).toBeVisible();
    } finally {
      // ---- 4. 复位（不动图集/主图，只还原本轮改过的四个字段）----
      // 原始为 null 就写回 NULL（V54 加列未回填，库里本来就是 NULL），别留一堆空串/空数组
      sql(
        `UPDATE product SET subtitle=${lit(before.subtitle ?? null)}, `
        + `detail_html=${lit(before.description ?? null)}, `
        + `params_json=${lit(before.params ? JSON.stringify(before.params) : null)}, `
        + `service_tags_json=${lit(before.serviceTags ? JSON.stringify(before.serviceTags) : null)} `
        + `WHERE id=${productId};`,
      );
    }

    const restored = (await api(request, 'GET', `/products/${productId}`, undefined, adminToken))?.data;
    expect(restored.subtitle ?? '').toBe(before.subtitle ?? '');
  });
});

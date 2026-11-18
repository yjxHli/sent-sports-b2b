/**
 * 管理端「优惠券创建 + 发放记录」（2026-11-18 用户需求 2、3）。
 *
 * 用户口径：
 *   1) 新建一个入口能建「特定商品」的券（限定 SPU/SKU）；
 *   2) 建好的券要能在「优惠券管理 → 发放优惠券」里选到并真的发出去；
 *   3) 列表要有券号 / 创建人 / 创建时间，能删除，能「以 Excel 形式把整张表下载下来」，底部带分页；
 *   4) 最近发放记录同样能删除、能导出整张表、带分页。
 *
 * 断言口径：
 *   A) 表单建券 → 列表出现券号（CPT 前缀）/ 创建人（admin）/ 创建时间 / 适用范围「指定商品」；
 *      分页器显示总条数；导出的 CSV 行数 = 分页器总条数（导的是整张表，不是当前页）；
 *      点「去发放」跳转到优惠券管理并预选该券；发放给客户 9002 → 发放记录首行就是这张券；
 *   B) 发放记录：分页器、关键字查询、导出 CSV、批量删除（软删后行消失），用例结束用 SQL 复位；
 *   C) 删除端点：无令牌 / 错目标的令牌一律拒绝，正确令牌才把模板软删隐藏。
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { test, expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';

const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
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
  method: 'GET' | 'POST',
  path: string,
  data?: unknown,
  token?: string,
  headers?: Record<string, string>,
): Promise<any> {
  const res = await request.fetch(`${API_URL}${path}`, {
    method,
    data,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(headers ?? {}) },
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

async function adminToken(request: APIRequestContext): Promise<string> {
  const login = await api(request, 'POST', '/auth/login', {
    username: ADMIN_NAME,
    password: ADMIN_PASSWORD,
    aud: 'admin',
    deviceId: `e2e-coupon-template-${Date.now()}`,
  });
  const token = login?.data?.accessToken as string;
  expect(token, `管理端登录失败：${JSON.stringify(login)}`).toBeTruthy();
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
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `管理端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

/**
 * ElementPlus 的 data-testid 可能落在容器 div 上，也可能被 useAttrs 下沉到内层 input，
 * 统一取「真正可输入的那个元素」，避免 fill 打到 div 上直接报错。
 */
async function inputOf(root: Locator): Promise<Locator> {
  const tag = (await root.evaluate((n) => n.tagName)).toLowerCase();
  if (tag === 'input' || tag === 'textarea') return root;
  const inner = root.locator('input, textarea').first();
  return inner;
}

/** 从下拉浮层里挑第一条（浮层挂 body，必须用 :visible 限定） */
function visibleOptions(page: Page): Locator {
  return page.locator('.el-select-dropdown:visible .el-select-dropdown__item');
}

const csvRows = (text: string): string[][] =>
  text
    .replace(/^\uFEFF/, '')
    .split(/\r?\n/)
    .filter((line) => line.trim() !== '')
    .map((line) => line.replace(/^"|"$/g, '').split('","'));

/** 与后端 BatchIds.targetId 同口径：trim → 去空 → 去重 → 字典序升序 */
const targetIdOf = (ids: string[]): string =>
  [...new Set(ids.map((s) => s.trim()).filter(Boolean))].sort().join(',');

test('建「指定商品」券：券号/创建人/创建时间进列表，导出整张表，并能去发放给客户', async ({ page, request }) => {
  test.setTimeout(240_000);
  const token = await adminToken(request);
  const name = `E2E 斯伯丁专享 ${STAMP}`;
  let templateId = '0';

  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  try {
    await loginAdminUi(page);
    await page.goto(join(ADMIN_URL, '/customer/coupon-template'));
    await expect(page.getByTestId('coupon-template-create'), '应有「新建优惠券」入口')
      .toBeVisible({ timeout: 20_000 });

    // ---- 1) 表单建券：品类券 + 折扣 9 折 + 指定商品 ----
    await page.getByTestId('coupon-template-create').click();
    const dialog = page.locator('.el-dialog').filter({ hasText: '新建优惠券' });
    await expect(dialog).toBeVisible({ timeout: 10_000 });
    await (await inputOf(dialog.getByTestId('coupon-create-name'))).fill(name);

    // 默认已是「品类券 / 折扣 90」，显式再点一次保证不受默认值调整影响
    await dialog.locator('.el-radio', { hasText: '品类券' }).click();
    await dialog.locator('.el-radio', { hasText: '折扣' }).click();
    await dialog.locator('.el-radio', { hasText: '指定商品' }).click();

    await dialog.getByTestId('coupon-create-products').click();
    const firstProduct = visibleOptions(page).first();
    await expect(firstProduct, '适用商品下拉应能搜到商品').toBeVisible({ timeout: 20_000 });
    const productName = (await firstProduct.innerText()).trim();
    await firstProduct.click();
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);

    const created = page.waitForResponse(
      (r) => r.url().includes('/api/admin/coupons/templates') && r.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await dialog.getByTestId('coupon-create-submit').click();
    const body: any = await (await created).json().catch(() => null);
    expect(body?.code, `创建失败：${JSON.stringify(body)}`).toBe(0);
    templateId = String(body?.data?.templateId ?? '0');
    const templateNo = String(body?.data?.templateNo ?? '');
    expect(templateNo, '新建券必须带券号').toMatch(/^CPT/);
    console.log('新建券:', templateNo, '| 商品:', productName);

    // ---- 2) 列表：券号 / 创建人 / 创建时间 / 适用范围 / 分页器 ----
    const row = page.locator('.el-table__body tbody tr').filter({ hasText: templateNo });
    await expect(row, '新建的券应出现在列表首屏').toBeVisible({ timeout: 20_000 });
    const cells = (await row.locator('td').allInnerTexts()).map((t) => t.trim());
    console.log('列表行:', cells.join(' | '));
    expect(cells[0], '首列应是券号').toBe(templateNo);
    expect(cells[1], '第二列应是券名称').toContain(name);
    expect(cells.some((t) => t === '指定商品'), '适用范围应显示「指定商品」').toBe(true);
    // 创建人取 sys_staff.name，admin 一定有值（空值会渲染成 '-'）
    expect(cells, '必须显示创建人').not.toContain('-');
    expect(cells.some((t) => /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(t)), '必须显示创建时间').toBe(true);

    const pager = page.locator('.sent-table-pager .el-pagination');
    await expect(pager, '底部必须有分页器').toBeVisible();
    const pagerText = (await pager.innerText()).replace(/\s+/g, ' ');
    const total = Number((pagerText.match(/共\s*(\d+)\s*条/) ?? [])[1]);
    console.log('分页器:', pagerText);
    expect(total, '分页器应显示总条数').toBeGreaterThan(0);

    // ---- 3) 导出：导的是整张表（行数 = 分页器总数），不是当前页 ----
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 40_000 }),
      page.getByRole('button', { name: /导出 CSV|导出中/ }).click(),
    ]);
    expect(download.suggestedFilename()).toBe('优惠券模板.csv');
    const path = await download.path();
    expect(path, '导出必须真的落盘').toBeTruthy();
    const rows = csvRows(readFileSync(path!, 'utf8'));
    console.log('导出表头:', rows[0].join(','), '| 数据行:', rows.length - 1, '| 分页总数:', total);
    expect(rows[0], '表头应含券号与创建人').toEqual(
      expect.arrayContaining(['券号', '券名称', '创建人', '创建时间']),
    );
    expect(rows.length - 1, '导出行数必须等于整张表的总条数').toBe(total);
    expect(rows.some((r) => r.includes(templateNo)), '导出的 CSV 应包含新建的券号').toBe(true);

    // ---- 4) 去发放：跳到优惠券管理并预选这张券 ----
    await row.getByTestId('coupon-template-issue').click();
    await expect(page).toHaveURL(new RegExp(`/customer/coupon\\?templateId=${templateId}`), {
      timeout: 20_000,
    });
    const templateSelectRow = page.locator('.el-form-item').filter({ hasText: '券面额' });
    await expect(templateSelectRow, '发放页应预选刚建的券').toContainText(name, { timeout: 20_000 });

    // 指定客户 9002（手机号 13800138000）
    await page.locator('.el-radio', { hasText: '指定客户' }).click();
    const customerSelect = page.getByTestId('coupon-customers');
    await customerSelect.click();
    await (await inputOf(customerSelect)).fill('13800138000');
    const customerOption = visibleOptions(page).filter({ hasText: '13800138000' }).first();
    await expect(customerOption, '应能搜到客户 9002').toBeVisible({ timeout: 20_000 });
    await customerOption.click();
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);

    await page.getByTestId('coupon-submit').click();
    const box = page.locator('.el-message-box');
    await expect(box, '发放前应二次确认').toBeVisible({ timeout: 10_000 });
    const issued = page.waitForResponse(
      (r) => /\/api\/admin\/coupons\/issue$/.test(new URL(r.url()).pathname)
        && r.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await box.getByRole('button', { name: '确认发放' }).click();
    const issuedBody: any = await (await issued).json().catch(() => null);
    expect(issuedBody?.code, `发放失败：${JSON.stringify(issuedBody)}`).toBe(0);
    expect(Number(issuedBody?.data ?? 0), '应至少发出 1 张').toBeGreaterThan(0);

    // ---- 5) 发放记录首行就是这张新建券（按发放时间倒序） ----
    const issueRow = page.locator('.el-table__body tbody tr').first();
    await expect(issueRow).toContainText(name, { timeout: 20_000 });
    // 客户列（第 5 列）取昵称或手机号，不能是空占位
    const customerCell = (await issueRow.locator('td').nth(4).innerText()).trim();
    console.log('发放客户:', customerCell);
    expect(customerCell, '发放记录应显示客户').not.toBe('');
    expect(customerCell).not.toBe('-');

    expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
  } finally {
    if (templateId !== '0') {
      // 只清本用例造的数据，不动演示库既有券
      sql(`DELETE FROM customer_coupon WHERE template_id = ${templateId};`);
      sql(`DELETE FROM coupon_template WHERE id = ${templateId};`);
    }
  }
});

test('发放记录：分页 + 关键字查询 + 导出整张表 + 批量删除软删', async ({ page }) => {
  test.setTimeout(240_000);
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  let couponNo = '';
  try {
    await loginAdminUi(page);
    await page.goto(join(ADMIN_URL, '/customer/coupon'));
    const rows = page.locator('.el-table__body tbody tr');
    await expect(rows.first(), '发放记录应有数据').toBeVisible({ timeout: 20_000 });

    // ---- 1) 分页器 ----
    const pager = page.locator('.sent-table-pager .el-pagination');
    await expect(pager, '发放记录底部必须有分页器').toBeVisible();
    const pagerText = (await pager.innerText()).replace(/\s+/g, ' ');
    const total = Number((pagerText.match(/共\s*(\d+)\s*条/) ?? [])[1]);
    console.log('发放记录分页器:', pagerText);
    expect(total).toBeGreaterThan(0);

    // ---- 2) 关键字：按首行券号搜，结果只剩它 ----
    couponNo = (await rows.first().locator('td').first().innerText()).trim();
    expect(couponNo, '首列应是券号').toMatch(/^CPN/);
    sql(`UPDATE customer_coupon SET deleted = 0 WHERE coupon_no = '${couponNo}';`);

    await (await inputOf(page.getByTestId('coupon-issue-keyword'))).fill(couponNo);
    const searched = page.waitForResponse(
      (r) => r.url().includes('/api/admin/coupons/issues/page') && r.request().method() === 'GET',
      { timeout: 20_000 },
    );
    await page.getByRole('button', { name: '查询' }).first().click();
    expect((await searched).url()).toContain(encodeURIComponent(couponNo).replace(/%2D/g, '-'));

    await expect(rows.first()).toContainText(couponNo, { timeout: 20_000 });
    const filteredText = (await pager.innerText()).replace(/\s+/g, ' ');
    const filteredTotal = Number((filteredText.match(/共\s*(\d+)\s*条/) ?? [])[1]);
    console.log('关键字命中:', filteredTotal, '条');
    expect(filteredTotal).toBeGreaterThan(0);
    for (const text of await rows.locator('td:first-child').allInnerTexts()) {
      expect(text.trim()).toBe(couponNo);
    }

    // ---- 3) 导出：CSV 行数 = 当前筛选下的总条数 ----
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 40_000 }),
      page.getByRole('button', { name: /导出 CSV|导出中/ }).click(),
    ]);
    expect(download.suggestedFilename()).toBe('优惠券发放记录.csv');
    const path = await download.path();
    expect(path, '导出必须真的落盘').toBeTruthy();
    const csv = csvRows(readFileSync(path!, 'utf8'));
    console.log('导出表头:', csv[0].join(','), '| 数据行:', csv.length - 1);
    expect(csv[0], '表头应含券号 / 客户 / 发放时间').toEqual(
      expect.arrayContaining(['券号', '券名称', '客户', '发放时间']),
    );
    expect(csv.length - 1, '导出行数应等于整张表总条数').toBe(filteredTotal);
    expect(csv.some((r) => r.includes(couponNo))).toBe(true);

    // ---- 4) 批量删除：进删除态 → 勾选 → 输「删除」→ 行消失 ----
    await page.getByTestId('batch-delete-btn').click();
    const target = rows.filter({ hasText: couponNo }).first();
    await target.locator('.el-checkbox').click();
    const confirmBtn = page.getByTestId('batch-delete-confirm');
    await expect(confirmBtn).toBeEnabled();

    await confirmBtn.click();
    const dialog = page.locator('.el-message-box');
    await expect(dialog, '应弹出危险操作确认框').toBeVisible({ timeout: 10_000 });
    await dialog.locator('input').fill('删除');
    const done = page.waitForResponse(
      (r) => r.url().includes('/api/admin/coupons/issues/batch-delete')
        && r.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await dialog.getByRole('button', { name: '确认继续' }).click();
    const body: any = await (await done).json().catch(() => null);
    expect(body?.code, `删除失败：${JSON.stringify(body)}`).toBe(0);
    expect(Number(body?.data?.deleted ?? 0), '应删掉 1 条').toBe(1);

    await expect(page.locator('.el-table__body tbody tr').filter({ hasText: couponNo }))
      .toHaveCount(0, { timeout: 20_000 });
    expect(sql(`SELECT deleted FROM customer_coupon WHERE coupon_no = '${couponNo}';`)).toBe('1');

    expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
  } finally {
    if (couponNo) sql(`UPDATE customer_coupon SET deleted = 0 WHERE coupon_no = '${couponNo}';`);
  }
});

test('券模板/发放记录的删除端点：无令牌与错目标令牌一律拒绝，正确令牌才生效', async ({ request }) => {
  test.setTimeout(120_000);
  const token = await adminToken(request);

  const created = await api(request, 'POST', '/admin/coupons/templates', {
    name: `E2E 令牌校验 ${STAMP}`,
    couponType: 'NO_THRESHOLD',
    discountType: 'AMOUNT',
    amountCent: 500,
    thresholdCent: 0,
    discountRate: 100,
    scopeType: 'ALL',
    scopeIds: null,
    customerScope: 'ALL',
    validDays: 30,
    claimable: 0,
    totalQty: -1,
    status: 'ACTIVE',
  }, token);
  expect(created?.code, `建券失败：${JSON.stringify(created)}`).toBe(0);
  const templateId = String(created?.data?.templateId);
  const templateNo = String(created?.data?.templateNo);

  try {
    const endpoint = '/admin/coupons/templates/batch-delete';

    const noToken = await api(request, 'POST', endpoint, { ids: [templateId] }, token);
    expect(noToken?.code, '无令牌必须拒绝').not.toBe(0);

    const wrong = await api(request, 'POST', '/admin/confirm-token', {
      action: 'BATCH_DELETE', targetId: 'SOME-OTHER-ID', confirmText: '删除',
    }, token);
    const wrongToken = wrong?.data?.token as string;
    const rejected = await request.fetch(`${API_URL}${endpoint}`, {
      method: 'POST',
      data: { ids: [templateId] },
      headers: { Authorization: `Bearer ${token}`, 'X-Confirm-Token': wrongToken },
      failOnStatusCode: false,
    });
    expect(((await rejected.json().catch(() => null)) as any)?.code,
      '目标不匹配的令牌必须被拒绝').not.toBe(0);
    expect(sql(`SELECT deleted FROM coupon_template WHERE template_no = '${templateNo}';`)).toBe('0');

    const granted = await api(request, 'POST', '/admin/confirm-token', {
      action: 'BATCH_DELETE', targetId: targetIdOf([templateId]), confirmText: '删除',
    }, token);
    const ok = await request.fetch(`${API_URL}${endpoint}`, {
      method: 'POST',
      data: { ids: [templateId] },
      headers: { Authorization: `Bearer ${token}`, 'X-Confirm-Token': granted?.data?.token },
      failOnStatusCode: false,
    });
    expect(((await ok.json().catch(() => null)) as any)?.code,
      '正确令牌应删除成功').toBe(0);
    expect(sql(`SELECT deleted FROM coupon_template WHERE template_no = '${templateNo}';`)).toBe('1');

    // 软删后发放下拉不再出现
    const list = await api(request, 'GET', '/admin/coupons/templates', undefined, token);
    expect((list?.data ?? []).some((t: any) => String(t.templateNo) === templateNo),
      '软删的券不应再出现在发放下拉').toBe(false);
  } finally {
    sql(`DELETE FROM coupon_template WHERE template_no = '${templateNo}';`);
  }
});

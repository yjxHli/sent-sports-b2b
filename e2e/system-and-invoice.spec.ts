import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

/**
 * 发票搜索 + 系统三页真数据（TDD 红→绿）。
 *
 * 用户诉求（2026-11-11）：
 * 1) 发票管理加「发票 ID / 发票号 / 客户 ID」搜索；
 * 2) 员工管理、角色权限矩阵、业务参数三页无数据 —— 后端 SystemServiceImpl 是空桩，
 *    listStaff 返回空 PageResult、listRoles / listParams 返回 List.of()。
 *
 * 断言口径：
 * - 发票搜索必须命中后端真实过滤结果（不是前端截断），总数收敛到 1 条；
 * - 员工 / 角色必须有真实库行（sys_staff 6 行、sys_role 6 行）；
 * - 角色权限保存后刷新页面仍生效（证明 updateRolePerms 真落库，不是返回占位 VO）；
 * - 业务参数至少能读到种子参数，且编辑保存后回读一致。
 */
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');

/**
 * 演示库最新一批开票申请常常全是「待开票」（invoice_no 为 NULL），
 * 用例靠首屏碰运气找发票号就会假红。这里现场把一条待开票申请真正开出来，
 * 让「按发票号搜索」始终有数据可搜（不新增申请行，只把已有行开具）。
 */
async function ensureIssuedInvoice(request: APIRequestContext): Promise<void> {
  const loginRes = await request.fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    data: {
      username: 'admin', password: '123456', aud: 'admin',
      deviceId: `e2e-invoice-${Date.now()}`,
    },
    failOnStatusCode: false,
  });
  const token = (await loginRes.json().catch(() => null))?.data?.accessToken as string;
  if (!token) return;
  const headers = { Authorization: `Bearer ${token}` };
  const listed = await request.fetch(`${API_URL}/admin/invoices?page=1&size=20`, {
    headers, failOnStatusCode: false,
  });
  const list = (await listed.json().catch(() => null))?.data?.list ?? [];
  const applied = list.find((r: any) => !r.invoiceNo);
  if (!applied) return;
  await request.fetch(`${API_URL}/admin/invoices/${applied.invoiceId}/issue`, {
    method: 'POST',
    headers,
    data: { pdfUrl: '', remark: 'E2E 前置：现场开具一张发票' },
    failOnStatusCode: false,
  });
}

async function login(page: Page): Promise<void> {
  await page.goto(ADMIN_URL + 'login');
  await page.waitForTimeout(900);
  if (page.url().includes('login')) {
    await page.getByPlaceholder('用户名').fill('admin');
    await page.getByPlaceholder('密码').fill('123456');
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
      ),
      page.getByRole('button', { name: /登\s*录/ }).click(),
    ]);
  }
}

/** 取表格体第 colIndex 列（从 1 开始）的所有单元格文本 */
async function cellTexts(page: Page, colIndex: number): Promise<string[]> {
  return page.$$eval(
    `.el-table__body-wrapper tbody tr td:nth-child(${colIndex})`,
    (tds) => (tds as HTMLElement[]).map((td) => td.innerText.trim()),
  );
}

/** 分页器「共 N 条」里的 N */
async function totalOf(page: Page): Promise<number> {
  const text = (await page.locator('.sent-table-pager').first().innerText()).replace(/\s+/g, ' ');
  const m = text.match(/共\s*(\d+)\s*条/);
  return m ? Number(m[1]) : -1;
}

async function search(page: Page, placeholder: string, value: string, apiPath: string): Promise<void> {
  const input = page.getByPlaceholder(placeholder).first();
  await expect(input).toBeVisible({ timeout: 8000 });
  await input.fill(value);
  await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes(apiPath) && r.request().method() === 'GET',
      { timeout: 15000 },
    ),
    page.getByRole('button', { name: '查询' }).first().click(),
  ]);
  await page.waitForTimeout(700);
}

/* ------------------------------------------------------------------ */
/* 1. 发票管理搜索                                                      */
/* ------------------------------------------------------------------ */

test('发票管理：可按发票 ID / 发票号 / 客户 ID 搜索', async ({ page, request }) => {
  await login(page);
  await ensureIssuedInvoice(request);
  await page.goto(ADMIN_URL + 'finance/invoice');
  await page.waitForSelector('.el-table__body-wrapper tbody tr');
  await page.waitForTimeout(1200);

  const placeholder = '发票 ID / 发票号 / 客户 ID';
  const api = '/api/admin/invoices';

  const before = await totalOf(page);
  expect(before).toBeGreaterThan(0);

  // ① 发票号（默认无勾选列 → 第 2 列）
  // 待开票（APPLIED）的行还没有发票号，表格渲染成占位符「-」；
  // 挑一个真实发票号来搜，占位符本身不能作为关键字（单个「-」会命中所有 INV-x）。
  const invoiceNos = await cellTexts(page, 2);
  const invoiceNo = invoiceNos.find((t) => t && /\d/.test(t) && !/^-+$/.test(t.trim()));
  expect(invoiceNo).toBeTruthy();
  await search(page, placeholder, String(invoiceNo), api);
  const afterNo = await cellTexts(page, 2);
  console.log('发票号搜索 →', invoiceNo, '命中:', afterNo.length, '总数:', await totalOf(page));
  expect(afterNo.length).toBeGreaterThan(0);
  expect(afterNo.every((t) => t.includes(String(invoiceNo)))).toBe(true);
  expect(await totalOf(page)).toBe(1);

  // ② 发票 ID（第 1 列）：后端是「ID / 发票号 / 客户 ID」三选一模糊匹配，
  //    所以按 ID 搜索可能连带命中「客户 ID 含同一串数字」的行（例如搜 9 会带出 9002）。
  //    这里锁的是契约本身：目标发票必须命中，且每一行至少有一列含关键字。
  await page.getByPlaceholder(placeholder).first().fill('');
  await Promise.all([
    page.waitForResponse((r) => r.url().includes(api)),
    page.getByRole('button', { name: '查询' }).first().click(),
  ]);
  await page.waitForTimeout(600);
  const ids = await cellTexts(page, 1);
  const invoiceId = ids[0];
  await search(page, placeholder, invoiceId, api);
  const afterId = await cellTexts(page, 1);
  const afterIdNos = await cellTexts(page, 2);
  const afterIdCustomers = await cellTexts(page, 3);
  console.log('发票 ID 搜索 →', invoiceId, '命中:', afterId.length);
  expect(afterId.length).toBeGreaterThan(0);
  expect(afterId, `按 ID 搜索必须命中目标发票 ${invoiceId}`).toContain(invoiceId);
  afterId.forEach((idText, i) => {
    const hit = idText.includes(invoiceId)
      || (afterIdNos[i] ?? '').includes(invoiceId)
      || (afterIdCustomers[i] ?? '').includes(invoiceId);
    expect(hit, `第 ${i + 1} 行的 ID/发票号/客户 ID 都不含关键字 ${invoiceId}`).toBe(true);
  });

  // ③ 客户 ID（第 3 列）：同一客户可能有多张发票，只断言全部命中
  await page.getByPlaceholder(placeholder).first().fill('');
  await Promise.all([
    page.waitForResponse((r) => r.url().includes(api)),
    page.getByRole('button', { name: '查询' }).first().click(),
  ]);
  await page.waitForTimeout(600);
  const customerIds = await cellTexts(page, 3);
  const customerId = customerIds.find((t) => t && t !== '--');
  expect(customerId).toBeTruthy();
  await search(page, placeholder, String(customerId), api);
  const afterCustomer = await cellTexts(page, 3);
  console.log('客户 ID 搜索 →', customerId, '命中:', afterCustomer.length);
  expect(afterCustomer.length).toBeGreaterThan(0);
  expect(afterCustomer.every((t) => t.includes(String(customerId)))).toBe(true);
});

/* ------------------------------------------------------------------ */
/* 2. 员工管理有真实数据                                                */
/* ------------------------------------------------------------------ */

test('员工管理：列表应有真实员工行（不再空表）', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'system/staff');
  await page.waitForSelector('.el-table__body-wrapper tbody tr');
  await page.waitForTimeout(1500);

  const ids = await cellTexts(page, 1);
  const usernames = await cellTexts(page, 2);
  console.log('员工行数:', ids.length, '首行:', ids[0], usernames[0]);

  expect(ids.length).toBeGreaterThanOrEqual(5);
  expect(await totalOf(page)).toBeGreaterThanOrEqual(5);
  expect(usernames).toContain('ADMIN001');
  await expect(page.getByText('暂无数据')).toHaveCount(0);
});

/* ------------------------------------------------------------------ */
/* 3. 角色权限矩阵：真角色 + 权限保存落库                                */
/* ------------------------------------------------------------------ */

test('角色权限矩阵：应显示真实角色且权限可保存', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'system/role');
  await page.waitForSelector('.el-table__body-wrapper tbody tr');
  await page.waitForTimeout(1500);

  const roleCodes = await cellTexts(page, 1);
  console.log('角色数:', roleCodes.length, roleCodes.join(','));
  expect(roleCodes.length).toBeGreaterThanOrEqual(5);
  expect(roleCodes).toContain('SUPER_ADMIN');
  expect(roleCodes).toContain('CUSTOMER_SERVICE');

  // 选一个权限点明确的角色：客服角色持有 customer:review / chat:*
  await page.locator('.el-table__row', { hasText: 'CUSTOMER_SERVICE' }).first().click();
  await page.waitForTimeout(500);

  const target = page.locator('.perm-items .el-checkbox', { hasText: 'refund:audit' }).first();
  await expect(target).toBeVisible({ timeout: 6000 });
  const wasChecked = (await target.getAttribute('class'))?.includes('is-checked') ?? false;
  if (!wasChecked) await target.click();
  await page.waitForTimeout(200);

  await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api/admin/roles') && r.request().method() === 'PUT',
      { timeout: 15000 },
    ),
    page.getByRole('button', { name: '保存' }).first().click(),
  ]);
  await page.waitForTimeout(1200);

  // 刷新后重新选同一角色，权限点仍然勾选 → 证明写进了 sys_role_permission
  await page.reload();
  await page.waitForSelector('.el-table__body-wrapper tbody tr');
  await page.waitForTimeout(1500);
  await page.locator('.el-table__row', { hasText: 'CUSTOMER_SERVICE' }).first().click();
  await page.waitForTimeout(500);
  const saved = page.locator('.perm-items .el-checkbox', { hasText: 'refund:audit' }).first();
  await expect(saved).toBeVisible({ timeout: 6000 });
  await expect(saved).toHaveClass(/is-checked/);
  const checkedCount = await page.locator('.perm-items .el-checkbox.is-checked').count();
  console.log('刷新后勾选权限数:', checkedCount);
  expect(checkedCount).toBeGreaterThan(0);

  // 还原：本用例为验证落库临时勾上的权限点，跑完撤回，避免污染演示数据
  if (!wasChecked) {
    await saved.click();
    await page.waitForTimeout(200);
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/admin/roles') && r.request().method() === 'PUT',
        { timeout: 15000 },
      ),
      page.getByRole('button', { name: '保存' }).first().click(),
    ]);
    await page.waitForTimeout(800);
  }
});

/* ------------------------------------------------------------------ */
/* 4. 业务参数：种子参数可读 + 编辑保存                                  */
/* ------------------------------------------------------------------ */

test('业务参数：应有默认参数且编辑后回读一致', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'system/config');
  await page.waitForSelector('.el-table__body-wrapper tbody tr');
  await page.waitForTimeout(1200);

  const keys = await cellTexts(page, 1);
  const values = await cellTexts(page, 2);
  console.log('参数行数:', keys.length, keys.slice(0, 5).join(','));
  expect(keys.length).toBeGreaterThanOrEqual(5);
  await expect(page.getByText('暂无数据')).toHaveCount(0);

  // 编辑首行参数值 → 保存 → 刷新回读 → 再改回原值（用例可重复跑，不污染演示数据）
  const key = keys[0];
  const original = values[0];
  const marker = '业务参数回归值';

  async function editValue(value: string): Promise<void> {
    await page.locator('.el-table__row', { hasText: key }).first()
      .getByRole('button', { name: '编辑' }).click();
    await page.waitForTimeout(400);
    const valueInput = page.locator('.el-dialog__body input').nth(1);
    await expect(valueInput).toBeVisible({ timeout: 5000 });
    await valueInput.fill(value);
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/admin/params') && r.request().method() === 'PUT',
        { timeout: 15000 },
      ),
      page.getByRole('button', { name: '保存' }).click(),
    ]);
    await page.waitForTimeout(900);
    await page.reload();
    await page.waitForSelector('.el-table__body-wrapper tbody tr');
    await page.waitForTimeout(1200);
  }

  await editValue(marker);
  const readBack = await cellTexts(page, 2);
  const idx = (await cellTexts(page, 1)).indexOf(key);
  console.log('回读参数:', key, '→', readBack[idx]);
  expect(readBack[idx]).toBe(marker);

  await editValue(original);
  const restored = await cellTexts(page, 2);
  expect(restored[(await cellTexts(page, 1)).indexOf(key)]).toBe(original);
});

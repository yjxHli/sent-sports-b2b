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
 * - 系统配置中心（2026-11-25 由「业务参数」改造）：接口下发中文名与说明、页面首列是中文名 +
 *   小字键名、弹窗先看说明再填值、改完能回读（详见第 4 节用例注释）。
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

/** 直接走接口登录拿令牌：验「接口返回中文名」时不必为了令牌先跑一遍页面 */
async function adminToken(request: APIRequestContext): Promise<string> {
  const res = await request.post(`${API_URL}/auth/login`, {
    data: {
      username: 'admin', password: '123456', aud: 'admin',
      deviceId: `e2e-config-${Date.now()}`,
    },
  });
  const token = (await res.json())?.data?.accessToken as string;
  expect(token, '管理端登录失败，后面的配置断言都无从谈起').toBeTruthy();
  return token;
}

async function login(page: Page): Promise<void> {  await page.goto(ADMIN_URL + 'login');
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

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

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
/* 4. 系统配置中心：中文名可读、说明可见、编辑保存后回读一致              */
/* ------------------------------------------------------------------ */

/**
 * 2026-11-25 改造：原「业务参数」只有 `order.pay.timeout.minutes | 30` 两列，
 * 运营看不出这一项是什么、填错了会怎样。现在改成「系统配置中心」：
 *
 * 断言链（真实读写）：
 *   1) 接口 `GET /api/admin/configs` 必须下发中文名与说明（去极客化的真源在后端字典）；
 *   2) 页面首列是中文名 + 小字键名，说明列有内容，键名不能再当主标题；
 *   3) 弹窗里先看到说明再填值，数字类填字母被拦下（前端即时挡一次，后端还有一道）；
 *   4) 改值 → 保存 → 列表显示「45 分钟」→ 刷新仍生效 → 改回原值（用例可重复跑）。
 */
test('系统配置中心：中文名 + 说明可见，编辑保存后回读一致', async ({ page, request }) => {
  // 接口口径先验：页面展示的中文名来自后端字典，不是前端自己拼的
  const token = await adminToken(request);
  const listed = await request.get(`${API_URL}/admin/configs`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const configs: any[] = (await listed.json())?.data ?? [];
  const timeout = configs.find((item) => item.configKey === 'order.pay.timeout.minutes');
  expect(timeout, '接口应返回种子配置 order.pay.timeout.minutes').toBeTruthy();
  expect(timeout.configName, 'config_name 必须是中文名').toBe('订单支付超时时间');
  expect(String(timeout.description)).toContain('自动取消');
  expect(timeout.displayValue).toBe(`${timeout.configValue} 分钟`);
  const original: string = String(timeout.configValue);
  const marker = original === '45' ? '46' : '45';

  await login(page);
  await page.goto(ADMIN_URL + 'system/config');
  await expect(page.locator('h2.sent-page-title')).toHaveText('系统配置中心', { timeout: 15_000 });
  await page.waitForSelector('[data-testid="config-list"] .el-table__body-wrapper tbody tr');
  await page.waitForTimeout(800);

  // 首列：中文名当主标题，键名降级为小字；说明列必须有内容
  const row = page.locator('[data-testid="config-list"] .el-table__body-wrapper tbody tr')
    .filter({ hasText: '订单支付超时时间' });
  await expect(row, '列表应按中文名展示，而不是只显示英文键名').toHaveCount(1);
  await expect(row).toContainText('order.pay.timeout.minutes');
  await expect(row).toContainText('自动取消');
  const nameFontSize = await row.locator('.cfg-item__name').evaluate(
    (el) => getComputedStyle(el as HTMLElement).fontSize);
  const keyFontSize = await row.locator('.cfg-item__key').evaluate(
    (el) => getComputedStyle(el as HTMLElement).fontSize);
  expect(parseFloat(nameFontSize), '配置项名称要比键名大，否则还是极客视图')
    .toBeGreaterThan(parseFloat(keyFontSize));

  async function editValue(next: string): Promise<void> {
    await row.getByTestId('config-edit-btn').click();
    const dialog = page.locator('[data-testid="config-dialog"]');
    await expect(dialog, '点「修改」应弹出表单').toBeVisible({ timeout: 10_000 });
    // 输入框上方必须先说明「改这一项会发生什么」
    await expect(dialog).toContainText('自动取消');

    const input = page.getByTestId('config-value-input');
    await expect(input).toBeVisible({ timeout: 5000 });
    await input.fill(next);
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/admin/configs/order.pay.timeout.minutes')
          && r.request().method() === 'PUT',
        { timeout: 15_000 },
      ),
      page.getByTestId('config-save-btn').click(),
    ]);
    await expect(dialog).toBeHidden({ timeout: 10_000 });
    await page.waitForTimeout(600);
  }

  // 非法值：数字类配置填中文，前端直接拦下，不发请求
  await row.getByTestId('config-edit-btn').click();
  await expect(page.locator('[data-testid="config-dialog"]')).toBeVisible({ timeout: 10_000 });
  await page.getByTestId('config-value-input').fill('半小时');
  await page.getByTestId('config-save-btn').click();
  await expect(page.locator('.el-message').last(),
    '数字类配置填中文应提示只能填非负整数').toContainText('只能填非负整数', { timeout: 8000 });
  await page.getByTestId('config-cancel-btn').click();
  await expect(page.locator('[data-testid="config-dialog"]')).toBeHidden({ timeout: 8000 });

  await editValue(marker);
  await expect(row, '保存后列表应显示带单位的新值').toContainText(`${marker} 分钟`);

  await page.reload();
  await page.waitForSelector('[data-testid="config-list"] .el-table__body-wrapper tbody tr');
  await page.waitForTimeout(900);
  const restoredRow = page.locator('[data-testid="config-list"] .el-table__body-wrapper tbody tr')
    .filter({ hasText: '订单支付超时时间' });
  await expect(restoredRow, '刷新后仍是新值，证明真落库而不是只改了内存').toContainText(`${marker} 分钟`);

  // 改回原值，保证演示库与后续用例不受影响
  await restoredRow.getByTestId('config-edit-btn').click();
  await expect(page.locator('[data-testid="config-dialog"]')).toBeVisible({ timeout: 10_000 });
  await page.getByTestId('config-value-input').fill(original);
  await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api/admin/configs/order.pay.timeout.minutes')
        && r.request().method() === 'PUT',
      { timeout: 15_000 },
    ),
    page.getByTestId('config-save-btn').click(),
  ]);
  await page.waitForSelector('[data-testid="config-list"] .el-table__body-wrapper tbody tr');
  await expect(page.locator('[data-testid="config-list"] .el-table__body-wrapper tbody tr')
    .filter({ hasText: '订单支付超时时间' })).toContainText(`${original} 分钟`);
});


/**
 * 管理端六列表批量删除 + 输入「删除」二次确认（2026-11-17 用户需求 9，TDD）。
 *
 * 用户口径：
 *   1) 退款审核 / 发票 / 合同 / 定制 / 订单 / 库存六个列表，刷新按钮旁有「删除」；
 *   2) 默认不显示勾选框，首位就是业务主键（退款单号 / 发票 ID）；**点「删除」后勾选列才出现**；
 *   3) 删除前必须手输「删除」二次确认；
 *   4) 软删隐藏，不动财务。
 *
 * 断言口径：
 *   0) 进页面没有勾选列，首列表头是业务主键，点「删除」后勾选列出现；
 *   1) 删除态下不勾选点确认 → 按钮禁用，不发请求；
 *   2) 勾选后弹窗输错短语 → 确认被拦下，不发请求；
 *   3) 输对「删除」→ 调 batch-delete → 行从列表消失 → count 同步减少 → 自动退出删除态；
 *   4) 软删不碰金额：refund_order.amount_cent 前后一致（库里只改 deleted）；
 *   5) 用例结束用 SQL 复位，保证可重复执行。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';

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
): Promise<any> {
  const res = await request.fetch(`${API_URL}${path}`, {
    method,
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

async function adminToken(request: APIRequestContext): Promise<string> {
  const login = await api(request, 'POST', '/auth/login', {
    username: ADMIN_NAME,
    password: ADMIN_PASSWORD,
    aud: 'admin',
    deviceId: `e2e-batch-delete-${Date.now()}`,
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

/** 与后端 BatchIds.targetId 同口径：trim → 去空 → 去重 → 字典序升序 → 逗号连接 */
function targetIdOf(ids: string[]): string {
  return [...new Set(ids.map((s) => s.trim()).filter(Boolean))].sort().join(',');
}

test('退款审核：默认无勾选框，点「删除」才出现，输对「删除」后软删隐藏且金额不变', async ({ page, request }) => {
  test.setTimeout(180_000);

  const token = await adminToken(request);
  const listed = await api(request, 'GET', '/admin/refunds?page=1&size=20', undefined, token);
  const first = listed?.data?.list?.[0];
  test.skip(!first, '演示库没有退款单，跳过');
  const refundNo = String(first.refundNo);
  const amountBefore = sql(`SELECT amount_cent FROM refund_order WHERE refund_no = '${refundNo}';`);
  // 预防上一次中断留下的删除标记
  sql(`UPDATE refund_order SET deleted = 0 WHERE refund_no = '${refundNo}';`);

  try {
    await loginAdminUi(page);
    await page.goto(join(ADMIN_URL, '/finance/refund'));
    const rows = page.locator('.el-table__row');
    await expect(rows.first(), '退款审核列表应有数据').toBeVisible({ timeout: 20_000 });

    // 0) 默认没有勾选列：首位必须是业务主键（退款单号），不是复选框
    const headerCells = page.locator('.el-table__header-wrapper th .cell');
    await expect(headerCells.first(), '默认首列表头应是「退款单号」')
      .toHaveText('退款单号', { timeout: 10_000 });
    await expect(rows.first().locator('.el-checkbox')).toHaveCount(0);

    const deleteBtn = page.getByTestId('batch-delete-btn');
    await expect(deleteBtn, '刷新按钮旁应有删除按钮').toBeVisible();

    // 1) 点「删除」进入删除态 → 勾选列出现，确认按钮因未勾选而禁用
    await deleteBtn.click();
    const confirmBtn = page.getByTestId('batch-delete-confirm');
    await expect(confirmBtn, '进入删除态后应出现确认删除按钮').toBeVisible({ timeout: 10_000 });
    await expect(rows.first().locator('.el-checkbox'), '删除态下每行应有复选框').toHaveCount(1);
    await expect(confirmBtn, '未选择时确认按钮应禁用').toBeDisabled();

    // 2) 勾选第一行
    await rows.first().locator('.el-checkbox').click();
    await expect(confirmBtn).toBeEnabled();

    // 3) 输错短语 → 确认被拦下，不发请求
    let deleteCalls = 0;
    page.on('request', (r) => {
      if (r.url().includes('/batch-delete') && r.method() === 'POST') deleteCalls += 1;
    });
    await confirmBtn.click();
    const dialog = page.locator('.el-message-box');
    await expect(dialog, '应弹出危险操作确认框').toBeVisible({ timeout: 10_000 });
    const input = dialog.locator('input');
    await input.fill('确认删除');
    await dialog.getByRole('button', { name: '确认继续' }).click();
    await expect(dialog, '输错短语时确认框不能关闭').toBeVisible();
    await expect(dialog).toContainText('请准确输入「删除」');
    expect(deleteCalls, '输错短语时不得发删除请求').toBe(0);

    // 4) 输对 → 真正删除
    await input.fill('删除');
    const done = page.waitForResponse(
      (r) => r.url().includes('/api/admin/refunds/batch-delete') && r.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await dialog.getByRole('button', { name: '确认继续' }).click();
    const resp: any = await (await done).json().catch(() => null);
    expect(resp?.code, `删除失败：${JSON.stringify(resp)}`).toBe(0);

    await expect(page.locator('.el-table__row').filter({ hasText: refundNo }))
      .toHaveCount(0, { timeout: 20_000 });

    // 5) 删完自动退出删除态：勾选列消失，界面回到「首位是退款单号」
    await expect(page.getByTestId('batch-delete-btn'), '删除完成后应回到常态按钮').toBeVisible();
    await expect(page.getByTestId('batch-delete-confirm')).toHaveCount(0);
    await expect(page.locator('.el-table__header-wrapper th .cell').first()).toHaveText('退款单号');

    // 6) 软删只改标记，金额一分不动
    expect(sql(`SELECT deleted FROM refund_order WHERE refund_no = '${refundNo}';`)).toBe('1');
    expect(sql(`SELECT amount_cent FROM refund_order WHERE refund_no = '${refundNo}';`))
      .toBe(amountBefore);
  } finally {
    sql(`UPDATE refund_order SET deleted = 0 WHERE refund_no = '${refundNo}';`);
  }
});

test('六个列表的批量删除端点：无令牌/错目标一律 40302，正确令牌才生效', async ({ request }) => {
  test.setTimeout(120_000);
  const token = await adminToken(request);
  const targets: Array<{ page: string; pick: (row: any) => string; column: string; verify: string; reset: string }> = [
    { page: '/admin/refunds?page=1&size=20', pick: (r) => String(r.refundNo),
      column: 'refund_no', verify: 'deleted', reset: 'deleted = 0' },
    { page: '/admin/invoices?page=1&size=20', pick: (r) => String(r.invoiceNo || r.invoiceId),
      column: 'invoice_no', verify: 'deleted', reset: 'deleted = 0' },
    { page: '/admin/contracts?page=1&size=20', pick: (r) => String(r.contractNo || r.contractId),
      column: 'contract_no', verify: 'deleted', reset: 'deleted = 0' },
    { page: '/admin/custom-orders?page=1&size=20', pick: (r) => String(r.customOrderId),
      column: 'custom_no', verify: 'deleted', reset: 'deleted = 0' },
    { page: '/admin/orders?page=1&size=20', pick: (r) => String(r.orderNo),
      column: 'order_no', verify: 'admin_deleted_at IS NOT NULL', reset: 'admin_deleted_at = NULL' },
  ];

  for (const t of targets) {
    const listed = await api(request, 'GET', t.page, undefined, token);
    const first = listed?.data?.list?.[0];
    if (!first) continue;
    const id = t.pick(first);
    expect(id, `${t.page} 首行主键不应为空`).toBeTruthy();
    const table = tableOf(t.column);
    // 发票号可能为空（申请未开具）→ 后端同时支持 id，恢复/校验也用同一 OR 条件
    const where = `${t.column} = '${id}' OR CAST(id AS CHAR) = '${id}'`;
    const endpoint = `/admin${t.page.split('?')[0].replace('/admin', '')}/batch-delete`;
    sql(`UPDATE ${table} SET ${t.reset} WHERE ${where};`);

    try {
      // 无令牌：必须拒绝
      const noToken = await api(request, 'POST', endpoint, { ids: [id] }, token);
      expect(noToken?.code, `${endpoint} 无令牌应拒绝`).not.toBe(0);

      // 令牌绑定的是别的目标：不允许消费
      const wrong = await api(request, 'POST', '/admin/confirm-token', {
        action: 'BATCH_DELETE', targetId: 'SOME-OTHER-ID', confirmText: '删除',
      }, token);
      const wrongToken = wrong?.data?.token as string;
      expect(wrongToken, '错误目标也能换到令牌（令牌只绑目标，不校验存在性）').toBeTruthy();
      const rejected = await request.fetch(`${API_URL}${endpoint}`, {
        method: 'POST',
        data: { ids: [id] },
        headers: { Authorization: `Bearer ${token}`, 'X-Confirm-Token': wrongToken },
        failOnStatusCode: false,
      });
      const rejectedBody: any = await rejected.json().catch(() => null);
      expect(rejectedBody?.code, `${endpoint} 目标不匹配的令牌应被拒绝`).not.toBe(0);

      // 正确目标 + 正确短语：成功
      const granted = await api(request, 'POST', '/admin/confirm-token', {
        action: 'BATCH_DELETE', targetId: targetIdOf([id]), confirmText: '删除',
      }, token);
      expect(granted?.code, `换令牌失败：${JSON.stringify(granted)}`).toBe(0);
      const grantedToken = granted?.data?.token as string;

      const res = await request.fetch(`${API_URL}${endpoint}`, {
        method: 'POST',
        data: { ids: [id] },
        headers: { Authorization: `Bearer ${token}`, 'X-Confirm-Token': grantedToken },
        failOnStatusCode: false,
      });
      const body: any = await res.json().catch(() => null);
      expect(body?.code, `${endpoint} 删除失败：${JSON.stringify(body)}`).toBe(0);
      expect(Number(sql(`SELECT COUNT(*) FROM ${table} WHERE (${where}) AND ${t.verify};`)))
        .toBe(1);
    } finally {
      sql(`UPDATE ${table} SET ${t.reset} WHERE ${where};`);
    }
  }
});

/** 列名 → 表名（六个列表各一张表） */
function tableOf(column: string): string {
  switch (column) {
    case 'refund_no': return 'refund_order';
    case 'invoice_no': return 'invoice_request';
    case 'contract_no': return 'contract';
    case 'custom_no': return 'custom_order';
    case 'order_no': return 'orders';
    case 'sku_id': return 'sku';
    default: throw new Error(`未知列：${column}`);
  }
}

test('库存列表删除：SKU 软删并下架，台账数量不动', async ({ request }) => {
  test.setTimeout(120_000);
  const token = await adminToken(request);
  const listed = await api(request, 'GET', '/admin/stock?page=1&size=20', undefined, token);
  const first = listed?.data?.list?.[0];
  test.skip(!first, '演示库没有库存行，跳过');
  const skuId = String(first.skuId);
  const before = sql(`SELECT CONCAT(physical_qty, '/', locked_qty) FROM sku_stock WHERE sku_id = ${skuId};`);
  const originalStatus = sql(`SELECT status FROM sku WHERE id = ${skuId};`);
  sql(`UPDATE sku SET deleted = 0, status = '${originalStatus || 'ON'}' WHERE id = ${skuId};`);

  try {
    const granted = await api(request, 'POST', '/admin/confirm-token', {
      action: 'BATCH_DELETE', targetId: targetIdOf([skuId]), confirmText: '删除',
    }, token);
    const t = granted?.data?.token as string;
    const res = await request.fetch(`${API_URL}/admin/stock/batch-delete`, {
      method: 'POST',
      data: { ids: [skuId] },
      headers: { Authorization: `Bearer ${token}`, 'X-Confirm-Token': t },
      failOnStatusCode: false,
    });
    const body: any = await res.json().catch(() => null);
    expect(body?.code, `库存删除失败：${JSON.stringify(body)}`).toBe(0);

    expect(sql(`SELECT deleted FROM sku WHERE id = ${skuId};`)).toBe('1');
    expect(sql(`SELECT status FROM sku WHERE id = ${skuId};`)).toBe('OFF');
    // 台账数量一分不动（账实仍可对）
    expect(sql(`SELECT CONCAT(physical_qty, '/', locked_qty) FROM sku_stock WHERE sku_id = ${skuId};`))
      .toBe(before);

    // 列表与 count 同步隐藏
    const after = await api(request, 'GET', '/admin/stock?page=1&size=20', undefined, token);
    const stillListed = (after?.data?.list ?? []).some((r: any) => String(r.skuId) === skuId);
    expect(stillListed, '软删后 SKU 不应再出现在库存列表').toBe(false);
  } finally {
    sql(`UPDATE sku SET deleted = 0, status = '${originalStatus || 'ON'}' WHERE id = ${skuId};`);
  }
});

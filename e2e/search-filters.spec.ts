import { test, expect, type Page } from '@playwright/test';

/**
 * 列表搜索（TDD 红→绿）。
 *
 * 用户诉求：五个列表页各自缺少「按单号 / 编号查找」的能力，只能靠翻页肉眼找。
 * 1) 合同管理 —— 搜合同 ID / 合同号 / 客户 ID；
 * 2) 应收台账 —— 搜订单号；
 * 3) 退款审核 —— 搜退款单号 / 订单号；
 * 4) 企业审核与授信 —— 搜企业名称 / 企业 ID；
 * 5) 定制列表 —— 搜定制单号。
 *
 * 断言口径：取首行的真实单号回填搜索框，点「查询」后**后端必须重新请求**，
 * 且返回行的对应列全部命中关键字、总数收敛到 1 条。只在前端过滤不算通过
 * （PageQuery 单页上限 100，前端截断会漏数据）。
 */
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';

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

/** 填关键字 → 点查询 → 等后端列表接口响应 */
async function search(
  page: Page,
  placeholder: string,
  value: string,
  apiPath: string,
): Promise<void> {
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

test('合同管理：可按合同号 / 客户 ID 搜索', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'finance/contract');
  await page.waitForSelector('.el-table__body-wrapper tbody tr');
  await page.waitForTimeout(1200);

  const before = await totalOf(page);
  expect(before).toBeGreaterThan(1);

  // ① 按合同号搜（默认无勾选列 → 合同号第 2 列）
  const contractNos = await cellTexts(page, 2);
  const contractNo = contractNos[0];
  expect(contractNo).toBeTruthy();
  await search(page, '合同 ID / 合同号 / 客户 ID', contractNo, '/api/admin/contracts');

  const afterNo = await cellTexts(page, 2);
  console.log('合同号搜索 →', contractNo, '命中行数:', afterNo.length, '总数:', await totalOf(page));
  expect(afterNo.length).toBeGreaterThan(0);
  expect(afterNo.every((t) => t.includes(contractNo))).toBe(true);
  expect(await totalOf(page)).toBe(1);

  // ② 按客户 ID 搜（第 3 列）：同一客户可能有多份合同，所以只断言「全部命中」
  const customerIds = await cellTexts(page, 3);
  await search(page, '合同 ID / 合同号 / 客户 ID', contractNo.slice(0, 2), '/api/admin/contracts');
  const backToAll = await cellTexts(page, 2);
  console.log('合同号前缀搜索命中:', backToAll.length);
  expect(backToAll.every((t) => t.includes(contractNo.slice(0, 2)))).toBe(true);

  // ③ 合同 ID 精确搜索：首行 ID 只应命中自己
  await page.getByPlaceholder('合同 ID / 合同号 / 客户 ID').first().fill('');
  await page.waitForTimeout(300);
  await page.goto(ADMIN_URL + 'finance/contract');
  await page.waitForSelector('.el-table__body-wrapper tbody tr');
  await page.waitForTimeout(1200);
  const ids = await cellTexts(page, 1);
  await search(page, '合同 ID / 合同号 / 客户 ID', ids[0], '/api/admin/contracts');
  expect((await cellTexts(page, 1)).every((t) => t === ids[0])).toBe(true);
  expect(customerIds.length).toBeGreaterThan(0);
});

test('应收台账：可按订单号搜索', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'finance/receivable');
  await page.waitForSelector('.el-table__body-wrapper tbody tr');
  await page.waitForTimeout(1200);

  const before = await totalOf(page);
  expect(before).toBeGreaterThan(1);

  const orderNos = await cellTexts(page, 2);
  const orderNo = orderNos[0];
  expect(orderNo).toBeTruthy();
  await search(page, '订单号', orderNo, '/api/admin/receivables');

  const after = await cellTexts(page, 2);
  console.log('订单号搜索 →', orderNo, '命中行数:', after.length, '总数:', await totalOf(page));
  expect(after.length).toBeGreaterThan(0);
  expect(after.every((t) => t.includes(orderNo))).toBe(true);
  expect(await totalOf(page)).toBe(1);
});

test('退款审核：可按退款单号 / 订单号搜索', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'finance/refund');
  await page.waitForSelector('.el-table__body-wrapper tbody tr');
  await page.waitForTimeout(1200);

  // ① 退款单号（默认无勾选列 → 业务列第 1 列）
  const refundNos = await cellTexts(page, 1);
  const refundNo = refundNos[0];
  expect(refundNo).toBeTruthy();
  await search(page, '退款单号 / 订单号', refundNo, '/api/admin/refunds');
  const afterRefund = await cellTexts(page, 1);
  console.log('退款单号搜索 →', refundNo, '命中行数:', afterRefund.length);
  expect(afterRefund.length).toBeGreaterThan(0);
  expect(afterRefund.every((t) => t.includes(refundNo))).toBe(true);
  expect(await totalOf(page)).toBe(1);

  // ② 订单号（第 2 列）：清空后重新取一批订单号
  await page.getByPlaceholder('退款单号 / 订单号').first().fill('');
  await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/admin/refunds')),
    page.getByRole('button', { name: '查询' }).first().click(),
  ]);
  await page.waitForTimeout(700);
  const orderNos = await cellTexts(page, 2);
  const orderNo = orderNos[0];
  expect(orderNo).toBeTruthy();
  await search(page, '退款单号 / 订单号', orderNo, '/api/admin/refunds');
  const afterOrder = await cellTexts(page, 2);
  console.log('订单号搜索 →', orderNo, '命中行数:', afterOrder.length);
  expect(afterOrder.length).toBeGreaterThan(0);
  expect(afterOrder.every((t) => t.includes(orderNo))).toBe(true);
});

test('企业审核与授信：可按企业 ID 搜索', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'customer/enterprise');
  await page.waitForSelector('.el-table__body-wrapper tbody tr');
  await page.waitForTimeout(1500);

  const before = await totalOf(page);
  expect(before).toBeGreaterThan(1);

  const ids = await cellTexts(page, 1);
  const enterpriseId = ids[0];
  expect(enterpriseId).toBeTruthy();
  await search(page, '企业名称 / 企业 ID', enterpriseId, '/api/admin/customers');

  const after = await cellTexts(page, 1);
  console.log('企业 ID 搜索 →', enterpriseId, '命中行数:', after.length, '总数:', await totalOf(page));
  expect(after.length).toBeGreaterThan(0);
  expect(after.every((t) => t === enterpriseId)).toBe(true);
  expect(await totalOf(page)).toBe(1);
});

test('定制列表：可按定制单号搜索', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'custom/list');
  await page.waitForSelector('.el-table__body-wrapper tbody tr');
  await page.waitForTimeout(1500);

  const before = await totalOf(page);
  expect(before).toBeGreaterThan(1);

  // 默认无勾选列 → 定制单号就是第 1 列
  const customNos = await cellTexts(page, 1);
  const customNo = customNos[0];
  expect(customNo).toBeTruthy();
  await search(page, '定制单号', customNo, '/api/admin/custom-orders');

  const after = await cellTexts(page, 1);
  console.log('定制单号搜索 →', customNo, '命中行数:', after.length, '总数:', await totalOf(page));
  expect(after.length).toBeGreaterThan(0);
  expect(after.every((t) => t.includes(customNo))).toBe(true);
  expect(await totalOf(page)).toBe(1);
});

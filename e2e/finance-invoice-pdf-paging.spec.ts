import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { sql, sqlNumber } from './db';

/**
 * 财务三处缺陷验收（TDD 红→绿）。
 *
 * 用户缺陷：
 * 1) 「合同管理」剩余天数一列全是 `--` —— 后端把 Long 序列化成字符串，
 *    前端却只认 `typeof validTo === 'number'`；
 * 2) 「核销与冲销」右侧应收台账没有分页器 —— 写死 page:1,size:100，一列到底看不完；
 * 3) 核销输入有问题时只回泛化的「核销金额超额」，看不出到底是回款单不够还是某张应收收满；
 * 4) 「发票管理」的发票不能预览、不能下载 —— pdfUrl 指向的 /files/*.pdf 根本不存在。
 *
 * 断言：剩余天数按真实系统日期算出、应收台账能翻页且 openOnly 下推 SQL、
 * 核销超额给出带具体金额的提示、发票能预览 blob PDF 并真的下到一个 %PDF 文件。
 *
 * 数据前置（2026-11-26 收尾）：9002 的未结清应收过去靠种子里的 `ensureTiny*` 兜底，
 * 一旦被核销掉就会让「共 N 条 > 一页」不成立。现在改为**用例自己造**：
 * beforeEach 补齐到 14 条（一页 10 条），afterAll 按 id 精确删除，既不依赖种子也不留垃圾。
 */
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const CUSTOMER_ID = 9002;
/** 一页 10 条，>10 才可能翻到第 2 页；留 4 条余量给「核销过程中被关掉几张」的情况 */
const SEED_RECEIVABLE_TARGET = 14;

/** 本次用例自建的应收 id（收尾精确删除，不动演示库既有台账） */
const seededReceivableIds: number[] = [];
/** 兜底自建订单 id（只有在「没有现成订单可挂应收」时才会用到） */
const seededOrderIds: number[] = [];

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

/** 本地日历日差：到期日 vs 今天（与页面 remainingDays 同一口径） */
function diffDays(dateText: string): number | null {
  const match = dateText.trim().match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return null;
  const target = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((target.getTime() - today.getTime()) / 86_400_000);
}

/**
 * 未结清应收条数——必须与页面 `openOnly=true` 同一口径
 * （`ReceivableMapper.countReceivable`：`deleted = 0 AND status <> 'CLOSED'`）。
 */
function openReceivableCount(): number {
  return sqlNumber(
    'SELECT COUNT(*) FROM receivable '
    + `WHERE deleted = 0 AND status <> 'CLOSED' AND customer_id = ${CUSTOMER_ID}`,
  );
}

/**
 * 兜底自建一笔订单（只写订单头 + 明细，不碰库存）。
 * 只在「9002 名下已经没有可挂应收的订单」时才用得上，收尾会连订单一起删。
 */
function seedOrder(): number {
  const orderNo = `FI${Date.now().toString().slice(-7)}${seededOrderIds.length}`;
  sql(`INSERT INTO orders (order_no, customer_id, status, pay_status, goods_amount_cent, payable_cent, paid_cent)
VALUES ('${orderNo}', ${CUSTOMER_ID}, 'COMPLETED', 'UNPAID', 12000, 12000, 0);
SET @oid = LAST_INSERT_ID();
INSERT INTO order_item (order_id, sku_id, qty, unit_price_cent, amount_cent)
VALUES (@oid, (SELECT id FROM sku ORDER BY id ASC LIMIT 1), 1, 12000, 12000);`);
  const id = sqlNumber(`SELECT id FROM orders WHERE order_no = '${orderNo}'`);
  if (id > 0) {
    seededOrderIds.push(id);
  }
  return id;
}

/**
 * 把 9002 的未结清应收补齐到 `target` 条（默认 14 > 一页 10 条）。
 *
 * <p>优先复用「9002 名下还没有应收台账的订单」——这样收尾只需要删应收行，
 * 订单表一行不动，不会把「订单列表/工作台」这类并行用例的可见数据搅乱。</p>
 */
function ensureOpenReceivables(target = SEED_RECEIVABLE_TARGET): void {
  const need = target - openReceivableCount();
  if (need <= 0) {
    return;
  }
  const reusable = sql(
    'SELECT o.id FROM orders o '
    + `WHERE o.customer_id = ${CUSTOMER_ID} `
    + 'AND NOT EXISTS (SELECT 1 FROM receivable r WHERE r.order_id = o.id) '
    + `ORDER BY o.id DESC LIMIT ${need}`,
  ).split(/\r?\n/).map((line) => Number(line.trim())).filter((id) => id > 0);

  const orderIds = [...reusable];
  while (orderIds.length < need) {
    const created = seedOrder();
    if (created <= 0) {
      break;
    }
    orderIds.push(created);
  }

  orderIds.forEach((orderId, i) => {
    // 一条 SQL 里插完立刻取 id（每次 sql() 都是新连接，跨语句的 LAST_INSERT_ID() 拿不到）
    const id = sqlNumber(
      'INSERT INTO receivable (order_id, customer_id, amount_cent, received_cent, due_date, status) '
      + `VALUES (${orderId}, ${CUSTOMER_ID}, ${12000 + i * 100}, 0, `
      + `DATE_ADD(CURDATE(), INTERVAL ${7 + i} DAY), 'NORMAL'); `
      + 'SELECT LAST_INSERT_ID();',
    );
    if (id > 0) {
      seededReceivableIds.push(id);
    }
  });
  console.log(`应收台账补数据：原有 ${target - need} 条，补 ${seededReceivableIds.length} 条 → ${target} 条`);
}

/** 收尾：按 id 精确删掉本次自建的应收（连带兜底订单），演示库既有台账一行不动 */
function cleanupSeededReceivables(): void {
  if (seededReceivableIds.length) {
    sql(`DELETE FROM receivable WHERE id IN (${seededReceivableIds.join(',')})`);
    seededReceivableIds.length = 0;
  }
  if (seededOrderIds.length) {
    const ids = seededOrderIds.join(',');
    sql(`DELETE oi FROM order_item oi WHERE oi.order_id IN (${ids});
DELETE FROM orders WHERE id IN (${ids});`);
    seededOrderIds.length = 0;
  }
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

// 前置：每个用例开跑前把 9002 的未结清应收补到 14 条（够翻到第 2 页）。
// 已有足够数据时只做一次 COUNT，不写库；缺失时自建并登记 id。
test.beforeEach(() => {
  ensureOpenReceivables();
});

// 收尾：无论用例成功还是失败，都把自建的应收/订单删干净
test.afterAll(() => {
  cleanupSeededReceivables();
});

test('合同剩余天数按真实系统日期显示，不再是 --', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  await login(page);
  await page.goto(ADMIN_URL + 'finance/contract');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1000);

  const rows = page.locator('.el-table__body tbody tr');
  const rowCount = await rows.count();
  expect(rowCount).toBeGreaterThan(0);

  // 默认无勾选列 → 到期日第 6 列、剩余天数第 7 列
  const dueDates = await rows.locator('td:nth-child(6)').allInnerTexts();
  const remainings = await rows.locator('td:nth-child(7)').allInnerTexts();
  console.log('到期日首行:', dueDates[0], '| 剩余天数首行:', remainings[0]);

  // 整列不能再是占位符
  expect(remainings.every((t) => t.trim() === '--')).toBe(false);

  for (let i = 0; i < rowCount; i += 1) {
    const days = diffDays(dueDates[i]);
    const text = remainings[i].trim();
    expect(days, `到期日格式异常：${dueDates[i]}`).not.toBeNull();
    if (days! < 0) {
      expect(text, `到期日 ${dueDates[i]} 应显示已逾期`).toBe(`已逾期 ${Math.abs(days!)} 天`);
    } else if (days === 0) {
      expect(text).toBe('今日到期');
    } else {
      expect(text, `到期日 ${dueDates[i]} 应显示剩余天数`).toBe(`剩余 ${days} 天`);
    }
  }

  expect(errors, 'console 不应有错误').toHaveLength(0);
});

test('核销与冲销的应收台账支持翻页，且只查未结清', async ({ page }) => {
  const urls: string[] = [];
  page.on('request', (r) => {
    if (r.method() === 'GET' && r.url().includes('/api/admin/receivables')) urls.push(r.url());
  });
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  await login(page);
  // 带上客户 9002：未结清应收由本文件的 beforeEach 补到 14 条，保证能翻出第 2 页
  await page.goto(ADMIN_URL + 'finance/payment-record?customerId=9002');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.locator('.el-checkbox').filter({ hasText: '仅看当前客户' }).first().click();
  await page.waitForTimeout(1200);

  await page.getByRole('button', { name: '加载该客户应收' }).click();
  await page.waitForTimeout(1500);

  expect(urls.at(-1), 'openOnly 必须下推 SQL').toContain('openOnly=true');
  expect(urls.at(-1)).toContain('customerId=9002');

  const pager = page.locator('.receivable-pager');
  await expect(pager).toBeVisible();
  const pagerText = (await pager.innerText()).replace(/\s+/g, ' ');
  console.log('应收分页器:', pagerText);
  const total = Number((pagerText.match(/共\s*(\d+)\s*条/) ?? [])[1]);
  expect(total, '未结清应收应多于一页').toBeGreaterThan(10);

  await pager.locator('.btn-next').click();
  // 应收表格有自动适配行数的逻辑，点击后可能再补一次 size/page=1 请求；
  // 因此只断言「确实请求过第 2 页」，并校验那条请求仍带 openOnly，而不是赌它是最后一条。
  await expect.poll(() => urls.some((url) => url.includes('page=2')), { timeout: 10_000 })
          .toBe(true);
  expect(urls.filter((url) => url.includes('page=2')).at(-1)).toContain('openOnly=true');

  expect(errors, 'console 不应有错误').toHaveLength(0);
});

test('核销金额超出应收剩余时，直接提示超过多少与剩余多少', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  await login(page);
  await page.goto(ADMIN_URL + 'finance/payment-record?customerId=9002');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.locator('.el-checkbox').filter({ hasText: '仅看当前客户' }).first().click();
  await page.waitForTimeout(1200);
  await page.getByRole('button', { name: '加载该客户应收' }).click();
  await page.waitForTimeout(1500);

  const receivables = page.locator('.el-table').nth(1).locator('.el-table__body tbody tr');
  await expect(receivables.first()).toBeVisible();
  const remainingText = (await receivables.first().locator('td').nth(3).innerText()).trim();
  console.log('首行应收未核销金额:', remainingText);

  // 勾选首行 + 填一个明显超额的金额（999999 元）
  await receivables.first().locator('.el-checkbox').click();
  await receivables.first().locator('input.el-input__inner').fill('999999');

  await page.getByRole('button', { name: '确认核销' }).click();
  await page.waitForTimeout(900);

  const warning = page.locator('.el-message').last();
  const text = (await warning.innerText()).replace(/\s+/g, '');
  console.log('核销提示:', text);
  expect(text).toContain('超过该应收单剩余可核销金额');
  expect(text, '提示必须带上具体金额').toMatch(/\d+(\.\d{2})?/);

  // 有问题的输入不得进入确认弹窗
  await expect(page.locator('.el-message-box')).toHaveCount(0);

  expect(errors, 'console 不应有错误').toHaveLength(0);
});

test('发票可预览真实 PDF，并真的下载成 %PDF 文件', async ({ page }) => {
  const errors: string[] = [];
  const pdfResponses: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('response', (r) => {
    // 只认后端 PDF 接口：用 '/pdf' 宽松匹配会把 Vite 下发的 pdf 预览分包
    // （xxx-pdf-preview.js，text/javascript）也算进来，断言就会莫名其妙拿到非 PDF 响应
    if (/\/api\/admin\/invoices\/[^/]+\/pdf/.test(r.url())) {
      pdfResponses.push(`${r.status()} ${r.headers()['content-type']}`);
    }
  });

  await login(page);
  await page.goto(ADMIN_URL + 'finance/invoice');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1000);

  const firstRow = page.locator('.el-table__body tbody tr').first();
  // 默认无勾选列 → 发票号第 2 列（第 1 列是发票 ID）
  const invoiceNo = (await firstRow.locator('td').nth(1).innerText()).trim();
  console.log('预览发票:', invoiceNo);

  await firstRow.getByRole('button', { name: '预览 / 下载' }).click();

  const frame = page.locator('iframe.pdf-frame');
  await expect(frame).toBeVisible({ timeout: 20_000 });
  // 注意：Playwright 的 headless Chromium 不带内置 PDF 插件，iframe 里必然是一片空白，
  // 所以这里只断言拿到的是 blob: URL + 响应确实是 application/pdf；
  // 「票面真的画出来」已由真实 Chrome（channel: 'chrome'）人工核对 + 后端 PDFTextStripper 单测覆盖。
  await expect(frame).toHaveAttribute('src', /^blob:/);
  expect(pdfResponses.at(-1), 'PDF 响应必须是 application/pdf').toContain('application/pdf');
  expect(pdfResponses.at(-1), 'PDF 响应必须成功').toContain('200');

  // 预览弹窗截图留档
  await page.screenshot({
    path: `screenshots/invoice-preview-${Date.now()}.png`,
    fullPage: false,
  });

  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 30_000 }),
    page.getByRole('button', { name: '下载 PDF' }).click(),
  ]);
  console.log('下载文件名:', download.suggestedFilename());
  expect(download.suggestedFilename()).toMatch(/\.pdf$/);
  expect(download.suggestedFilename()).toContain(invoiceNo);

  const path = await download.path();
  expect(path, '下载必须落到本地临时文件').toBeTruthy();
  const head = readFileSync(path!).subarray(0, 5).toString('latin1');
  console.log('文件头:', head);
  expect(head, '下载到的必须是真正的 PDF 文件').toBe('%PDF-');
  expect(readFileSync(path!).byteLength).toBeGreaterThan(1500);

  expect(errors, 'console 不应有错误').toHaveLength(0);
});

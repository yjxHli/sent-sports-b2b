import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

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

/** 本地日历日差：到期日 vs 今天（与页面 remainingDays 同一口径） */
function diffDays(dateText: string): number | null {
  const match = dateText.trim().match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return null;
  const target = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((target.getTime() - today.getTime()) / 86_400_000);
}

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
  // 带上客户 9002：该客户历史应收最多，能翻出第 2 页
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
  await page.waitForTimeout(1500);
  expect(urls.at(-1)).toContain('page=2');
  expect(urls.at(-1)).toContain('openOnly=true');

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
    if (r.url().includes('/pdf')) pdfResponses.push(`${r.status()} ${r.headers()['content-type']}`);
  });

  await login(page);
  await page.goto(ADMIN_URL + 'finance/invoice');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(1000);

  const firstRow = page.locator('.el-table__body tbody tr').first();
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

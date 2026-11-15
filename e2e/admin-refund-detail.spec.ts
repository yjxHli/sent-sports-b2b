import { test, expect, type Page } from '@playwright/test';

/**
 * 退款审核详情（独立审核页）验收（TDD 红→绿）。
 *
 * 用户诉求：管理端退款审核列表里每一条申请都能点「详情」进入这一笔的独立审核页，
 * 页面里要有这个订单的基本信息（订单号/状态/买家/金额/商品行/物流），审核动作也在页内完成。
 *
 * 断言口径：
 *   1) 列表每行都有「详情」入口，点击后 URL 变成 /admin/finance/refund/{refundNo}；
 *   2) 详情接口 GET /api/admin/refunds/{refundNo} 返回 200 且带 order.orderNo、order.lines；
 *   3) 页面上退款单号、退款金额、订单号、买家手机号、商品明细行都真实渲染；
 *   4) 「订单详情」按钮能跳到该订单的管理端详情页；
 *   5) 全程无 console error、无 5xx。
 */
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

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

test('退款审核列表点「详情」进入独立审核页，页内含订单基本信息', async ({ page }) => {
  test.setTimeout(150_000);
  const consoleErrors: string[] = [];
  const serverErrors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) consoleErrors.push(m.text());
  });
  page.on('response', (r) => {
    if (r.status() >= 500) serverErrors.push(`${r.request().method()} ${r.url()} → ${r.status()}`);
  });

  await login(page);
  await page.goto(ADMIN_URL + 'finance/refund');
  await page.waitForSelector('.el-table__body tbody tr');

  const firstRow = page.locator('.el-table__body tbody tr').first();
  const refundNo = (await firstRow.locator('td').first().innerText()).trim();
  const orderNo = (await firstRow.locator('td').nth(1).innerText()).trim();
  console.log('列表首行:', refundNo, orderNo);
  expect(refundNo, '列表首行应有退款单号').toMatch(/^RF\w+/);
  expect(orderNo, '列表首行应有订单号').toMatch(/^SO\w+/);

  // 详情接口先立个契约：点进页面必须只打这一个详情请求并成功
  const detailResp = page.waitForResponse(
    (r) => r.url().includes(`/api/admin/refunds/${refundNo}`) && r.request().method() === 'GET',
    { timeout: 30_000 },
  );
  await firstRow.getByRole('button', { name: '详情' }).click();
  const resp = await detailResp;
  const body: any = await resp.json().catch(() => null);
  expect(resp.status(), `详情接口应 200：${JSON.stringify(body)}`).toBe(200);
  expect(body?.code, `详情接口应 code=0：${JSON.stringify(body)}`).toBe(0);
  expect(body?.data?.refundNo).toBe(refundNo);
  expect(body?.data?.order?.orderNo, '详情必须带出订单号').toBe(orderNo);
  expect(Array.isArray(body?.data?.order?.lines), '详情必须带出订单商品行').toBe(true);
  expect(body?.data?.order?.customerPhone, '详情必须带出买家手机号').toMatch(/^1\d{10}$/);

  // 独立页面：URL 带退款单号
  await expect(page).toHaveURL(new RegExp(`/finance/refund/${refundNo}$`), { timeout: 20_000 });
  await expect(page.getByRole('heading', { name: '退款审核详情' })).toBeVisible();
  await expect(page.locator('.audit-head__no')).toHaveText(refundNo);

  // 退款信息 + 订单基本信息 + 买家信息
  const pageText = () => page.locator('.sent-page-title').locator('..').innerText();
  await expect(page.locator('h3.section-title', { hasText: '退款申请' }).first()).toBeVisible();
  await expect(page.locator('h3.section-title', { hasText: '订单基本信息' }).first()).toBeVisible();
  await expect(page.locator('h4.sub-title', { hasText: '买家信息' }).first()).toBeVisible();
  const text = await pageText();
  expect(text, '页面应显示订单号').toContain(orderNo);
  expect(text, '页面应显示买家手机号').toMatch(/1\d{10}/);
  await expect(page.locator('.audit-head__stats')).toContainText(/¥\d/);

  // 凭证图片：有图渲染缩略图，无图显示空态文案，二者必须出现其一
  const shots = await page.locator('.proof-gallery__item').count();
  if (shots === 0) {
    await expect(page.getByText('用户未上传凭证图片')).toBeVisible();
  } else {
    expect(shots, '凭证图片张数应与接口一致').toBe((body?.data?.images ?? []).length);
  }

  // 商品明细：有行则表格可见，无行则空态
  const lineRows = await page.locator('.line-table .el-table__body tbody tr').count();
  if (lineRows === 0) {
    await expect(page.getByText('该订单暂无商品行')).toBeVisible();
  } else {
    expect(lineRows, '商品明细行数应与接口一致').toBe(body?.data?.order?.lines.length);
  }

  // 跳订单详情：审核员要能一键核对整单
  await page.getByRole('button', { name: '订单详情' }).click();
  await expect(page).toHaveURL(new RegExp(`/order/detail/${orderNo}$`), { timeout: 20_000 });
  await expect(page.getByRole('heading', { name: '订单详情' })).toBeVisible();

  expect(serverErrors, `不应出现 5xx：${serverErrors.join(' | ')}`).toHaveLength(0);
  expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0);
});

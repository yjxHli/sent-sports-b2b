/**
 * 订单列表商品名跳转 + 订单详情淘宝版式 + 已完成订单开票（TDD 红→绿）。
 *
 * 用户诉求（2026-11-15）：
 *   1) 订单列表里点「商品名字」要跳到对应商品详情页；
 *   2) 订单详情重新设计（交易进度条 / 商品清单 / 右侧付款详情与订单信息）；
 *   3) 只有「已完成」的订单才可以申请开票。
 *
 * 断言口径：
 *   1) 列表点商品名 → URL 变 /product/<productId>，且落到真实商品详情（不出现「商品不存在」）；
 *      —— 这是关键：旧代码用 skuId 拼 /product/，会 404 到「商品不存在或已下架」。
 *   2) 订单详情页有 .od-steps（4 步，已完成节点带勾）、.od-table 商品行、.od-col-side（付款详情/订单信息）；
 *   3) 已完成订单可点「申请开票」→ 弹窗填抬头 → 提交后展示「已提交/已开具」；
 *   4) 在途订单（待发货）没有开票入口，发票卡写明「订单完成后可申请开票」。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

const pathOf = (page: Page): string => new URL(page.url()).pathname;

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

/** 进入「已完成」订单列表（这些单一定已支付） */
async function openCompletedOrders(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/orders?status=COMPLETED`);
  await expect(page.locator('.order-card').first()).toBeVisible({ timeout: 20_000 });
}

test('订单列表点商品名跳到商品详情，而不是跳到 skuId 的死链', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await openCompletedOrders(page);

  const firstTitle = page.locator('.order-card').first().locator('.order-item__title').first();
  await expect(firstTitle, '订单卡片应展示商品名').toBeVisible({ timeout: 20_000 });
  const titleText = (await firstTitle.innerText()).trim();

  await firstTitle.click();

  await expect
    .poll(() => pathOf(page), {
      timeout: 20_000,
      message: '点商品名应跳到 /product/<productId>',
    })
    .toMatch(/^\/product\/\d+$/);

  // 真正的商品详情：面包屑 + 详情主体，且没有「商品不存在」
  await expect(page.locator('.detail-main'), '应渲染商品详情主体').toBeVisible({
    timeout: 20_000,
  });
  await expect(
    page.getByText('商品不存在或已下架'),
    '用 skuId 拼 /product/ 会 404 成「商品不存在」，说明取的不是 productId',
  ).toHaveCount(0);
  await expect(page.locator('.detail')).toContainText(titleText.slice(0, 6), {
    timeout: 20_000,
  });

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

test('订单详情按淘宝版式展示，并提供申请开票', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await openCompletedOrders(page);
  await page.locator('.order-card').first().getByRole('button', { name: '查看详情' }).click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toMatch(/^\/order\/SO\d+/);

  // 1) 版式骨架（仿淘宝：顶部四步进度 + 商品表格 + 右栏信息卡）
  await expect(page.locator('.order-detail'), '订单详情根容器').toBeVisible({ timeout: 20_000 });
  const steps = page.locator('.od-steps .od-step');
  await expect(steps, '应有「拍下→付款→发货→成交」四步进度').toHaveCount(4);
  await expect(page.locator('.od-table .od-goods').first(), '应有商品行').toBeVisible({
    timeout: 20_000,
  });
  await expect(page.locator('.od-col-side'), '右侧栏应包含付款详情/订单信息').toContainText('付款详情');
  await expect(page.locator('.od-col-side')).toContainText('订单信息');

  // 1.1) 已完成订单：四个节点全部点亮（带勾），右栏能查到订单编号
  await expect(page.locator('.od-steps .od-step--done'), '已完成订单的四步应全部点亮')
    .toHaveCount(4, { timeout: 15_000 });
  await expect(page.locator('.od-step__dot svg').first(), '已完成节点应显示勾选').toBeVisible();
  await expect(page.locator('.od-col-side'), '右栏订单信息应显示订单编号').toContainText('订单编号');

  // 2) 申请开票（已申请过的单按钮变成「查看发票」）
  const invoiceBtn = page.locator('.od-actions').getByRole('button', { name: /申请开票|查看发票/ }).first();
  await expect(invoiceBtn, '已支付订单应能申请开票').toBeVisible({ timeout: 20_000 });
  await invoiceBtn.click();

  const dialog = page.locator('.el-dialog').filter({ hasText: '申请开票' });
  await expect(dialog).toBeVisible({ timeout: 15_000 });
  // 同一订单只能开一张票：重跑用例时该单可能已提交过，此时弹窗直接展示状态而无表单。
  // 有表单才走「提交」，没有表单就只断言状态——两条路径都必须能走到「状态可见」。
  const titleInput = dialog.getByPlaceholder(/发票抬头|个人|公司/).first();
  if (await titleInput.isVisible().catch(() => false)) {
    await titleInput.fill('森特体育用品有限公司');
    const taxNo = dialog.getByPlaceholder(/纳税人识别号|税号/).first();
    if (await taxNo.isVisible().catch(() => false)) await taxNo.fill('91320100MA1TEST001');
    const email = dialog.getByPlaceholder(/邮箱/).first();
    if (await email.isVisible().catch(() => false)) await email.fill('finance@sent-sports.test');
    await dialog.getByRole('button', { name: '提交申请' }).click();
  }

  // 3) 提交后（幂等：已有的申请也直接展示状态）
  await expect(page.locator('.od-invoice'), '详情页应有发票状态区块').toBeVisible({
    timeout: 20_000,
  });
  await expect(page.locator('.od-invoice')).toContainText(/已提交|审核中|已开具/, {
    timeout: 20_000,
  });

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

/**
 * 开票口径收紧（2026-11-15 用户要求：「只有已经完成的订单才可以申请发票开具」）。
 *
 * 旧口径是「付过款就能开」，导致待发货 / 待收货的在途订单也能开票——
 * 货还没到手、可能退换，票面金额与实际成交不一致，财务无法核销。
 * 这里从用户能看到的入口反向锁住口径：在途订单连按钮都不该出现，发票卡直接写明原因。
 */
test('在途订单没有开票入口，并提示订单完成后才能开票', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/orders?status=WAIT_SHIP`);
  await expect(page.locator('.order-card').first()).toBeVisible({ timeout: 20_000 });

  // 0) 列表页同样不给入口：在途订单卡片上没有「申请开票」
  await expect(
    page.locator('.order-card').first().getByRole('button', { name: '申请开票' }),
    '待发货订单的列表卡片不应出现「申请开票」',
  ).toHaveCount(0);

  await page.locator('.order-card').first().getByRole('button', { name: '查看详情' }).click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toMatch(/^\/order\/SO\d+/);

  // 1) 没有开票入口（连「查看发票」都不该有：在途订单不可能已有发票）
  await expect(
    page.locator('.od-actions').getByRole('button', { name: /申请开票|查看发票/ }),
    '待发货订单不应出现开票按钮（只有已完成才能开票）',
  ).toHaveCount(0);

  // 2) 发票卡写明原因，而不是让用户点进弹窗再吃 409
  const invCard = page.locator('.od-invoice');
  await expect(invCard, '应有发票区块').toBeVisible({ timeout: 20_000 });
  await expect(invCard).toContainText('订单完成后可申请开票');

  // 3) 进度条只点亮到「买家付款」，「交易成功」未点亮
  const steps = page.locator('.od-steps .od-step');
  await expect(steps).toHaveCount(4);
  await expect(steps.nth(3), '在途订单的「交易成功」节点不应点亮').not.toHaveClass(/od-step--done/);

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

/**
 * 定制订单退款三档处置（2026-11-21 用户口径，红转绿后的验收用例）。
 *
 * 业务规则（唯一真源：后端 `CustomRefundPolicy` + `CustomizeRefundGatewayImpl`）：
 *   - WAIT_DRAFT / WAIT_REVIEW / REJECTED / CANCELLED → 用户可自助退；
 *   - APPROVED / ESCALATED → 可提交，但退款单打「需人工复核」，客服确认是否已备料；
 *   - IN_PRODUCTION / FINISHED / SHIPPED → 绝对拦截。
 *
 * 本文件验两条端到端主线：
 *   用例 A：含定制件的已付订单**不排产** → 详情页点「申请退款」正常进入申请页 → 提交成功（code=0），
 *           且结算页已把「审核通过前可退、投产不可退」讲在支付按钮上方；
 *   用例 B：同一路径的订单在后台**真实排产**（上传图稿 → 客服通过 → 排产，走真实状态机）→
 *           用户点「申请退款」被前端直接拦下并弹提示，页面不跳转；同时绕过前端直接打 API
 *           也必须被后端以 CUSTOM_REFUND_NOT_ALLOWED(40980) 拒单。
 *
 * 断言口径：不写死商品/金额，现场挑在架规格；排产用真实管理端端点，不用 SQL 改状态。
 */
import fs from 'node:fs';
import path from 'node:path';
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';

const PROOF_IMAGE = path.join(__dirname, 'fixtures', 'proof.png');
const CHECKOUT_TIP = '定制商品在审核通过前支持退款，一旦开始生产将不支持退款。';
const BLOCK_TEXT = '定制商品已开始生产，无法申请退款。如有质量问题请联系客服。';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;
const join = (base: string, suffix: string): string =>
  `${base.replace(/\/+$/, '')}/${suffix.replace(/^\/+/, '')}`;

async function apiGet(request: APIRequestContext, suffix: string, token?: string): Promise<any> {
  const res = await request.get(join(API_URL, suffix), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

async function apiPost(request: APIRequestContext, suffix: string, data: unknown, token?: string): Promise<any> {
  const res = await request.post(join(API_URL, suffix), {
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

/** 与后端 http 内核同一份 localStorage，供直接打接口（绕过前端）时复用登录态。 */
async function userToken(page: Page): Promise<string> {
  return page.evaluate(
    () => Object.entries(localStorage).find(([k]) => k.includes('access_token'))?.[1] ?? '',
  );
}

async function adminToken(request: APIRequestContext): Promise<string> {
  const body = await apiPost(request, '/auth/login', {
    username: ADMIN_NAME,
    password: ADMIN_PASSWORD,
    aud: 'admin',
    deviceId: `e2e-custom-refund-${Date.now()}`,
  });
  const token = body?.data?.accessToken as string;
  expect(token, `管理端登录失败：${JSON.stringify(body)}`).toBeTruthy();
  return token;
}

/** 现场挑一个可售规格（演示库存会被本套 E2E 自己买空，写死 productId 会变假红）。 */
async function pickSellableProduct(request: APIRequestContext, need = 1): Promise<string> {
  const override = process.env.E2E_PRODUCT_ID;
  if (override) return override;
  const list = await apiGet(request, '/products?page=1&size=20');
  for (const product of list?.data?.list ?? []) {
    const detail = await apiGet(request, `/products/${product.productId}`);
    const hit = (detail?.data?.skus ?? []).find(
      (s: any) => s.onShelf !== false && Number(s.stockQty) >= need,
    );
    if (hit) return String(product.productId);
  }
  throw new Error('演示库里没有可售规格，无法下单（先补演示库存或设 E2E_PRODUCT_ID）');
}

async function waitPaid(request: APIRequestContext, orderNo: string, token: string): Promise<string> {
  const deadline = Date.now() + 20_000;
  let status = '';
  while (Date.now() < deadline) {
    const body = await apiGet(request, `/orders/${orderNo}`, token);
    status = body?.data?.status ?? '';
    if (status && status !== 'WAIT_PAY') return status;
    await new Promise((r) => setTimeout(r, 400));
  }
  return status;
}

/**
 * 造一笔「含 UV 定制件」的已付订单：商品页 → 立即购买 → 勾选定制服 → 提交并支付。
 * 顺带断言结算页的退款提示（用户要求「支付按钮上方必须讲清楚」）。
 */
async function createPaidCustomOrder(page: Page, request: APIRequestContext): Promise<{ orderNo: string; skuId: number; amountCent: number }> {
  const productId = await pickSellableProduct(request);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/product/${productId}`);
  const firstSku = page.locator('.spec-item:not(.disabled)').first();
  await expect(firstSku, `商品 ${productId} 应有可售规格`).toBeVisible({ timeout: 20_000 });
  await firstSku.click();
  await page.locator('.buy-btn--now').click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 20_000 });

  // 勾选「含 UV 定制件」：勾上之后结算页必须出现退款口径提示（提示本身也是本期交付物）
  await page.locator('.co-custom').click();
  const tip = page.locator('[data-testid="checkout-custom-refund-tip"]');
  await expect(tip, '勾选定制件后，支付按钮上方应出现退款口径提示').toBeVisible({ timeout: 10_000 });
  await expect(tip).toContainText(CHECKOUT_TIP);

  await expect(page.locator('.quote-line--total .sent-price'), '结算页应完成取价').toHaveText(
    /¥\d/,
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '提交订单并支付' }).click();
  const paidDialog = page.locator('.el-dialog').filter({ hasText: '支付成功' });
  await expect(paidDialog, '下单/支付失败：未出现「支付成功」弹窗').toBeVisible({ timeout: 40_000 });
  const dialogText = await paidDialog.innerText();
  const matched = /订单号[：:]\s*([A-Za-z0-9_-]+)/.exec(dialogText);
  expect(matched, `未能从支付弹窗解析订单号：${dialogText}`).not.toBeNull();
  const orderNo = matched![1];
  await page.keyboard.press('Escape');

  const token = await userToken(page);
  const status = await waitPaid(request, orderNo, token);
  expect(status, `支付后订单应已付（WAIT_SHIP），实际 ${status}`).toBe('WAIT_SHIP');

  const detail = await apiGet(request, `/orders/${orderNo}`, token);
  const item = (detail?.data?.items ?? [])[0];
  expect(item, `订单 ${orderNo} 应有商品行`).toBeTruthy();
  const amountCent = Number(item.linePaidCent ?? Number(item.unitPriceCent ?? 0) * Number(item.qty ?? 1));
  return { orderNo, skuId: Number(item.skuId), amountCent };
}

/** 订单对应的定制单（下单时后端即时生成，正常应为 WAIT_DRAFT）。 */
async function customOrderOf(request: APIRequestContext, orderNo: string, token: string): Promise<any> {
  const body = await apiGet(request, `/custom-orders/by-order/${orderNo}`, token);
  const row = (body?.data ?? [])[0];
  expect(row, `订单 ${orderNo} 应已生成定制单：${JSON.stringify(body)}`).toBeTruthy();
  return row;
}

/** 把定制单真实推进到生产中：上传图稿 → 客服通过 → 排产（不允许绕过状态机直接改状态）。 */
async function produceCustomOrder(
  request: APIRequestContext,
  customOrderId: string,
  token: string,
  admin: string,
): Promise<void> {
  const buffer = fs.readFileSync(PROOF_IMAGE);
  const uploadRes = await request.post(join(API_URL, '/files'), {
    multipart: { file: { name: 'draft.png', mimeType: 'image/png', buffer } },
    headers: { Authorization: `Bearer ${token}` },
    failOnStatusCode: false,
  });
  const uploaded: any = await uploadRes.json().catch(() => null);
  const fileId = uploaded?.data?.fileId;
  expect(fileId, `图稿上传失败：${JSON.stringify(uploaded)}`).toBeTruthy();

  const draft = await apiPost(request, `/customize/orders/${customOrderId}/drafts`, {
    fileId,
    fileName: 'draft.png',
    fileSize: buffer.length,
  }, token);
  expect(draft?.code, `提交图稿失败：${JSON.stringify(draft)}`).toBe(0);

  const passed = await apiPost(request, `/admin/custom-orders/${customOrderId}/pass`, {}, admin);
  expect(passed?.code, `审图通过失败：${JSON.stringify(passed)}`).toBe(0);
  expect(passed?.data?.status, '审图通过后定制单应为 APPROVED').toBe('APPROVED');

  const produced = await apiPost(request, `/admin/custom-orders/${customOrderId}/production`, {}, admin);
  expect(produced?.code, `排产失败：${JSON.stringify(produced)}`).toBe(0);
  expect(produced?.data?.status, '排产后定制单应为 IN_PRODUCTION').toBe('IN_PRODUCTION');
}

test.describe('定制订单退款三档', () => {
  test('用例 A：定制单还没排产，用户点申请退款可以正常提交', async ({ page, request }) => {
    test.setTimeout(180_000);
    const consoleErrors: string[] = [];
    page.on('console', (m) => {
      if (m.type() === 'error' && !NOISE.test(m.text())) consoleErrors.push(m.text());
    });

    await loginUser(page);
    const { orderNo, skuId, amountCent } = await createPaidCustomOrder(page, request);
    const token = await userToken(page);

    const custom = await customOrderOf(request, orderNo, token);
    expect(custom.status, '刚下单的定制单应为待提交图稿').toBe('WAIT_DRAFT');

    // 详情页：WAIT_DRAFT 属于可自助退，不该出现任何拦截提示
    await page.goto(`${USER_URL.replace(/\/+$/, '')}/order/${orderNo}`);
    const refundBtn = page.locator('[data-testid="order-refund-apply"]');
    await expect(refundBtn, '待发货订单应可申请退款').toBeVisible({ timeout: 20_000 });
    await expect(page.locator('[data-testid="order-refund-blocked"]')).toHaveCount(0);
    await expect(page.locator('[data-testid="order-refund-manual"]')).toHaveCount(0);

    await refundBtn.click();
    await expect(page, '未排产的定制单应正常进入申请页').toHaveURL(
      new RegExp(`/refund/apply/${orderNo}`),
      { timeout: 20_000 },
    );

    const pageRoot = page.locator('.ra-page');
    await expect(pageRoot.locator('.ra-line').first(), '申请页应列出该订单的商品行').toBeVisible({
      timeout: 20_000,
    });
    await pageRoot.getByPlaceholder(/说明退款理由/).fill('E2E-定制未排产-可退');
    await pageRoot.locator('input[type=file]').setInputFiles(PROOF_IMAGE);
    await expect(pageRoot.locator('.el-upload-list__item')).toHaveCount(1, { timeout: 20_000 });

    const refundResp = page.waitForResponse(
      (r) => r.url().includes('/api/refunds') && r.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await pageRoot.getByRole('button', { name: '提交申请' }).click();
    const resp = await refundResp;
    const body: any = await resp.json().catch(() => null);
    expect(resp.status(), `退款接口应 200，实际 ${resp.status()}：${JSON.stringify(body)}`).toBe(200);
    expect(body?.code, `未排产的定制单退款应成功：${JSON.stringify(body)}`).toBe(0);

    const sent = resp.request().postDataJSON();
    expect(Number(sent?.lines?.[0]?.skuId)).toBe(skuId);
    expect(Number(sent?.lines?.[0]?.amountCent)).toBe(amountCent);
    await expect(page, '提交成功后应进入该退款单的进度页').toHaveURL(/\/refund\/RF[A-Za-z0-9]+/, {
      timeout: 20_000,
    });

    // 自助退这一档不该挂人工复核标记（标记只属于 APPROVED/ESCALATED）
    const admin = await adminToken(request);
    const refundNo = String(body?.data?.refundNo ?? '');
    const audit = await apiGet(request, `/admin/refunds?page=1&size=10&keyword=${refundNo}`, admin);
    const row = (audit?.data?.list ?? []).find((r: any) => r.refundNo === refundNo);
    expect(row, `管理端应能查到 ${refundNo}：${JSON.stringify(audit)}`).toBeTruthy();
    expect(row.needManualReview, 'WAIT_DRAFT 档的退款不需要人工复核').toBe(false);

    expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0);
  });

  test('用例 B：定制单已排产，前端拦截并提示，后端同样拒单', async ({ page, request }) => {
    test.setTimeout(180_000);
    const consoleErrors: string[] = [];
    page.on('console', (m) => {
      if (m.type() === 'error' && !NOISE.test(m.text())) consoleErrors.push(m.text());
    });

    await loginUser(page);
    const { orderNo, skuId, amountCent } = await createPaidCustomOrder(page, request);
    const token = await userToken(page);

    const custom = await customOrderOf(request, orderNo, token);
    await produceCustomOrder(request, String(custom.customOrderId), token, await adminToken(request));

    // 详情页：定制卡片显示「生产中」，退款按钮旁给出拦截说明
    await page.goto(`${USER_URL.replace(/\/+$/, '')}/order/${orderNo}`);
    await expect(page.locator('[data-testid="order-custom"]'), '订单详情应有定制图稿卡片').toBeVisible({
      timeout: 20_000,
    });
    const blockedTip = page.locator('[data-testid="order-refund-blocked"]');
    await expect(blockedTip, '已排产的定制单应在按钮下给出不可退说明').toBeVisible({ timeout: 20_000 });
    await expect(blockedTip).toContainText(BLOCK_TEXT);

    // 点按钮：弹警告，且**不跳转**到申请页
    await page.locator('[data-testid="order-refund-apply"]').click();
    const box = page.locator('.el-message-box');
    await expect(box, '点申请退款应弹出警告弹窗').toBeVisible({ timeout: 10_000 });
    await expect(box).toContainText(BLOCK_TEXT);
    await expect(page, '被拦下时不应进入申请页').toHaveURL(new RegExp(`/order/${orderNo}`));
    await box.getByRole('button', { name: '我知道了' }).click();
    await expect(box).toBeHidden({ timeout: 10_000 });

    // 绕过前端直接打接口：后端必须绝对拦截（专用错误码，不落半截退款单）
    const direct = await apiPost(request, '/refunds', {
      orderNo,
      lines: [{ skuId, amountCent }],
      reason: 'E2E-绕过前端',
      proof: 'E2E 直接调接口验证后端拦截',
      images: [],
    }, token);
    expect(direct?.code, `后端应拒绝已排产定制单退款：${JSON.stringify(direct)}`).toBe(40980);
    expect(String(direct?.message ?? ''), '拒单原因要说明「已开始生产」').toContain('生产');

    expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0);
  });

  /**
   * 用例 C（2026-11-21 追加）：入口补在「定制单详情页」。
   * 用户反馈：从「我的订单 → 定制订单」进来只能看图稿，想退这单还得回商品订单里找，
   * 所以详情页本身要有一个「申请退款」，口径与订单详情一致——生产前可退，排产后拦下。
   */
  test('用例 C：定制单详情页可申请退款；同一入口在排产后被拦下', async ({ page, request }) => {
    test.setTimeout(180_000);
    const consoleErrors: string[] = [];
    page.on('console', (m) => {
      if (m.type() === 'error' && !NOISE.test(m.text())) consoleErrors.push(m.text());
    });

    await loginUser(page);
    const { orderNo } = await createPaidCustomOrder(page, request);
    const token = await userToken(page);
    const custom = await customOrderOf(request, orderNo, token);
    const customNo = String(custom.customOrderId);
    const detailUrl = `${USER_URL.replace(/\/+$/, '')}/custom/${customNo}`;

    // 1) 生产前：详情页有退款入口，点了进「这一单」的申请页，能正常提交
    await page.goto(detailUrl);
    const applyBtn = page.locator('[data-testid="custom-refund-apply"]');
    await expect(applyBtn, '定制单详情页应提供「申请退款」入口').toBeVisible({ timeout: 20_000 });
    await expect(page.locator('[data-testid="custom-refund-blocked"]')).toHaveCount(0);

    await applyBtn.click();
    await expect(page, '定制单详情页的退款入口应进该订单的申请页').toHaveURL(
      new RegExp(`/refund/apply/${orderNo}`),
      { timeout: 20_000 },
    );

    const pageRoot = page.locator('.ra-page');
    await expect(pageRoot.locator('.ra-line').first()).toBeVisible({ timeout: 20_000 });
    await pageRoot.getByPlaceholder(/说明退款理由/).fill('E2E-定制详情页申请退款');
    await pageRoot.locator('input[type=file]').setInputFiles(PROOF_IMAGE);
    await expect(pageRoot.locator('.el-upload-list__item')).toHaveCount(1, { timeout: 20_000 });

    const refundResp = page.waitForResponse(
      (r) => r.url().includes('/api/refunds') && r.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await pageRoot.getByRole('button', { name: '提交申请' }).click();
    const resp = await refundResp;
    const body: any = await resp.json().catch(() => null);
    expect(resp.status(), `退款接口应 200，实际 ${resp.status()}：${JSON.stringify(body)}`).toBe(200);
    expect(body?.code, `生产前的定制单应可退款：${JSON.stringify(body)}`).toBe(0);
    await expect(page).toHaveURL(/\/refund\/RF[A-Za-z0-9]+/, { timeout: 20_000 });

    // 2) 同一张定制单真实排产后再回详情页：入口仍在，但点了只给一句解释且不跳转
    await produceCustomOrder(request, customNo, token, await adminToken(request));
    await page.goto(detailUrl);
    const blockedTip = page.locator('[data-testid="custom-refund-blocked"]');
    await expect(blockedTip, '已排产的定制单详情页应给出不可退说明').toBeVisible({ timeout: 20_000 });
    await expect(blockedTip).toContainText(BLOCK_TEXT);

    await page.locator('[data-testid="custom-refund-apply"]').click();
    const box = page.locator('.el-message-box');
    await expect(box, '排产后点申请退款应弹出警告').toBeVisible({ timeout: 10_000 });
    await expect(box).toContainText(BLOCK_TEXT);
    await expect(page, '被拦下时不应离开定制单详情页').toHaveURL(new RegExp(`/custom/${customNo}`));
    await box.getByRole('button', { name: '我知道了' }).click();

    expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0);
  });
});

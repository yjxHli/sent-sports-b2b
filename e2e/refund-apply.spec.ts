/**
 * 申请退款（缺陷回归 + 凭证图片 + 驳回站内信）：
 *
 * 一、退款 400 回归
 *   原缺陷：详情页拿不到商品行，提交 `lines: [{ skuId: 0, amountCent: 应付 }]`，
 *   后端 `RefundApplyUseCase` 按 SKU 匹配订单行找不到 → BizException(PARAM_INVALID)
 *   → HTTP 400，浏览器控制台出现 `Failed to load resource: 400`。
 *
 * 二、凭证图片贯通审核端
 *   用户拍照/选图 → `POST /api/files` 拿 fileId → 随退款单落 `refund_order.images_json`
 *   → 管理端退款审核列表能拿到 images 且图片可直接渲染（审核人员看得到实物照）。
 *
 * 三、驳回站内信
 *   客服驳回 → 用户收到一条站内信（标题含「驳回」、正文带驳回原因）→ 商城端顶栏红点 +1。
 *
 * 断言口径：
 *   1) 订单详情点「申请退款」进入 `/refund/apply/{orderNo}`（该订单专属的申请页），
 *      提交后 `POST /api/refunds` 必须 200 且 `code=0`；
 *   2) 请求体每一行都带订单里真实存在的 skuId，且行金额 > 0；images 带 1 个 fileId；
 *   3) 管理端审核列表里该退款单 images[0] 与提交的一致，且 GET /api/files/{fileId} 返回图片；
 *   4) 驳回后 `/api/messages/unread-count` 增加，消息列表里有驳回通知，首页红点可见；
 *   5) 期间不出现任何 400 响应，也不出现控制台 error。
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import path from 'node:path';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';

/** 测试素材：e2e/fixtures 下的真实 PNG（Playwright 以 CJS 运行，__dirname 可用） */
const PROOF_IMAGE = path.join(__dirname, 'fixtures', 'proof.png');

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

const join = (base: string, suffix: string): string =>
  `${base.replace(/\/+$/, '')}/${suffix.replace(/^\/+/, '')}`;

async function apiPost(request: APIRequestContext, suffix: string, data: unknown, token?: string): Promise<any> {
  const res = await request.post(join(API_URL, suffix), {
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

async function apiGet(request: APIRequestContext, suffix: string, token?: string): Promise<any> {
  const res = await request.get(join(API_URL, suffix), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

/**
 * 现场挑一个「有货」的商品：退款用例要先真下一单，写死 `PRODUCT_ID=1001` 会随运行次数变成假红
 * ——演示库存会被本套 E2E 自己买空（1001 的两个规格现在可售 0），那时连规格都点不出来。
 */
async function pickSellableProduct(
  request: APIRequestContext,
  need = 2,
): Promise<{ productId: string; spec: string }> {
  const override = process.env.E2E_PRODUCT_ID;
  if (override) return { productId: override, spec: '' };
  const list = await apiGet(request, '/products?page=1&size=20');
  for (const product of list?.data?.list ?? []) {
    const detail = await apiGet(request, `/products/${product.productId}`);
    const hit = (detail?.data?.skus ?? []).find(
      (s: any) => s.onShelf !== false && Number(s.stockQty) >= need,
    );
    if (hit) return { productId: String(product.productId), spec: String(hit.spec) };
  }
  throw new Error('演示库里没有可售规格，无法下单（先补演示库存或设 E2E_PRODUCT_ID）');
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

/** 页面里的用户令牌（与后端 http 内核同一份 localStorage）。 */
async function userToken(page: Page): Promise<string> {
  return page.evaluate(
    () => Object.entries(localStorage).find(([k]) => k.includes('access_token'))?.[1] ?? '',
  );
}

/**
 * 等支付回调落账：前端发起支付后 1.2s 自动请求 `/payment/mock/callback`，
 * 弹窗出现时订单可能仍是待付款，必须轮询到非 WAIT_PAY 再进详情页。
 */
async function waitPaid(request: APIRequestContext, orderNo: string, token: string): Promise<string> {
  const deadline = Date.now() + 20_000;
  let status = '';
  while (Date.now() < deadline) {
    const res = await request.get(`${API_URL.replace(/\/+$/, '')}/orders/${orderNo}`, {
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    });
    const body: any = await res.json().catch(() => null);
    status = body?.data?.status ?? '';
    if (status && status !== 'WAIT_PAY') return status;
    await new Promise((r) => setTimeout(r, 400));
  }
  return status;
}

test('申请退款：带凭证图片提交、审核端可见、驳回后收到站内信红点', async ({ page, request }) => {
  test.setTimeout(180_000);
  const consoleErrors: string[] = [];
  const badRequests: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) consoleErrors.push(m.text());
  });
  page.on('response', (r) => {
    if (r.status() === 400) badRequests.push(`${r.request().method()} ${r.url()}`);
  });

  await loginUser(page);

  // 1) 造一笔已支付的订单（商品页 → 立即购买 → 结算页提交并支付）
  const picked = await pickSellableProduct(request);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/product/${picked.productId}`);
  const firstSku = page.locator('.spec-item:not(.disabled)').first();
  await expect(firstSku, `商品 ${picked.productId} 应有可售规格`).toBeVisible({ timeout: 20_000 });
  await firstSku.click();
  await page.locator('.buy-btn--now').click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 20_000 });

  // 结算页取价是异步的：没拿到 quote 就点提交只会弹「订单信息暂不可用」，不会下单
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

  const statusAfterPay = await waitPaid(request, orderNo, await userToken(page));
  expect(statusAfterPay, `支付后订单应已付（WAIT_SHIP），实际 ${statusAfterPay}`).toBe('WAIT_SHIP');

  // 2) 订单详情 → 申请退款（2026-11-16 起不再弹窗，跳「这一单专属」的申请页）
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/order/${orderNo}`);
  const refundBtn = page.getByRole('button', { name: '申请退款' });
  await expect(refundBtn, '待发货订单应可申请退款').toBeVisible({ timeout: 20_000 });
  await refundBtn.click();
  await expect(page, '申请退款应进入该订单专属的申请页，而不是售后列表').toHaveURL(
    new RegExp(`/refund/apply/${orderNo}`),
    { timeout: 20_000 },
  );

  const pageRoot = page.locator('.ra-page');
  await expect(pageRoot.locator('.ra-line').first(), '申请页应列出该订单的商品行').toBeVisible({
    timeout: 20_000,
  });
  await pageRoot.getByPlaceholder(/说明退款理由/).fill('E2E-凭证-图片已上传');

  // 2.1) 上传凭证照片：先传到文件服务，提交时只带 fileId
  // el-upload(picture-card) 的 input 是隐藏的，直接塞文件即可触发 change
  await pageRoot.locator('input[type=file]').setInputFiles(PROOF_IMAGE);
  await expect(
    pageRoot.locator('.el-upload-list__item'),
    '选完照片后应出现 1 张缩略图',
  ).toHaveCount(1, { timeout: 20_000 });

  const refundResp = page.waitForResponse(
    (r) => r.url().includes('/api/refunds') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await pageRoot.getByRole('button', { name: '提交申请' }).click();
  const resp = await refundResp;
  const body: any = await resp.json().catch(() => null);

  expect(resp.status(), `退款接口应 200，实际 ${resp.status()}：${JSON.stringify(body)}`).toBe(200);
  expect(body?.code, `退款失败：${JSON.stringify(body)}`).toBe(0);
  const sent = resp.request().postDataJSON();
  expect(Array.isArray(sent?.lines) && sent.lines.length > 0, `退款必须提交商品行：${JSON.stringify(sent)}`).toBe(true);
  for (const line of sent.lines) {
    expect(Number(line.skuId), `退款行 skuId 必须是真实 SKU：${JSON.stringify(line)}`).toBeGreaterThan(0);
    expect(Number(line.amountCent), `退款行金额必须大于 0：${JSON.stringify(line)}`).toBeGreaterThan(0);
  }
  expect(
    Array.isArray(sent?.images) && sent.images.length === 1,
    `退款必须带上凭证图片 fileId：${JSON.stringify(sent?.images)}`,
  ).toBe(true);
  const proofFileId = String(sent.images[0]);
  expect(proofFileId, `fileId 应是文件服务的相对路径：${proofFileId}`).toMatch(/^\d{4}\/\d{2}\//);

  // 提交成功直接跳到该退款单的详情页（比停留在申请页再确认一次更少点击）
  await expect(page, '提交成功后应进入该退款单的进度页').toHaveURL(/\/refund\/RF[A-Za-z0-9]+/, {
    timeout: 20_000,
  });

  // 3) 审核端可见：管理端列表带 images，且图片地址真的能取到图
  const adminLogin = await apiPost(request, '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin', deviceId: `e2e-refund-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken, `管理端 API 登录失败：${JSON.stringify(adminLogin)}`).toBeTruthy();

  const refundNo = String(body?.data?.refundNo ?? '');
  expect(refundNo, `退款单号缺失：${JSON.stringify(body)}`).toBeTruthy();
  const auditList = await apiGet(request, `/admin/refunds?page=1&size=10&keyword=${refundNo}`, adminToken);
  const auditRow = (auditList?.data?.list ?? []).find((r: any) => r.refundNo === refundNo);
  expect(auditRow, `管理端退款审核列表应能查到 ${refundNo}：${JSON.stringify(auditList)}`).toBeTruthy();
  expect(auditRow.images, '审核行必须带出用户上传的凭证图片').toEqual([proofFileId]);

  const imgRes = await request.get(join(API_URL, `/files/${proofFileId}`), { failOnStatusCode: false });
  expect(imgRes.status(), `凭证图片应可直接读取（<img> 无法带 Authorization）`).toBe(200);
  expect(imgRes.headers()['content-type'] ?? '', '读图接口应返回图片类型').toContain('image/');

  // 4) 驳回 → 用户收到站内信 + 红点
  const before = await apiGet(request, '/messages/unread-count', await userToken(page));
  const unreadBefore = Number(before?.data?.unreadCount ?? 0);
  const rejected = await apiPost(request, `/admin/refunds/${refundNo}/audit`, {
    approved: false, remark: 'E2E 驳回：凭证不足以证明质量问题',
  }, adminToken);
  expect(rejected?.code, `驳回应成功：${JSON.stringify(rejected)}`).toBe(0);

  const after = await apiGet(request, '/messages/unread-count', await userToken(page));
  expect(Number(after?.data?.unreadCount ?? 0), '驳回后未读站内信应 +1（顶栏红点）').toBe(unreadBefore + 1);

  const inbox = await apiGet(request, '/messages?page=1&size=10', await userToken(page));
  const notice = (inbox?.data?.list ?? []).find(
    (m: any) => String(m.title ?? '').includes('驳回') && String(m.bizNo ?? '') === refundNo,
  );
  expect(notice, `站内信里应有驳回通知：${JSON.stringify(inbox?.data?.list?.slice(0, 3))}`).toBeTruthy();
  expect(notice.content, '驳回通知正文要带原因，用户才知道怎么改').toContain('凭证不足以证明质量问题');

  // 顶栏红点：回商城首页能看到「消息」上的数字
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
  await expect(page.locator('.topbar__msg .msg-badge'), '商城端「消息」应显示未读红点').toBeVisible({
    timeout: 20_000,
  });

  expect(badRequests, `不应出现 400 响应：${badRequests.join(' | ')}`).toHaveLength(0);
  expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0);
});

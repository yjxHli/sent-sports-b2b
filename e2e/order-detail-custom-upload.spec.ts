/**
 * 订单详情上传 UV 定制图稿（2026-11-21 用户反馈，TDD 红→绿）。
 *
 * 用户诉求：商品页/结算页勾了 UV 定制，提示「下单后需上传图稿」，但订单详情页
 * 完全没有上传入口，只能自己猜去「我的定制」里找。
 *
 * 断言口径：
 *   1) 含定制件的订单，详情页出现「UV 定制图稿」卡片，带定制单号与状态；
 *   2) 待提交图稿（WAIT_DRAFT）时给出「上传图稿」按钮；
 *   3) 上传后定制单版本 +1、状态进入 WAIT_REVIEW（待审核），页面同步刷新；
 *   4) 图稿的 fileId 是文件服务真实路径（可在管理端审图台看到图），不是占位串。
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import path from 'node:path';
import { pickSellableSku } from './pick-product';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

/** UV 定制 30 件起订 */
const CUSTOM_QTY = 30;
const DRAFT_IMAGE = path.join(__dirname, 'fixtures', 'proof.png');

const pathOf = (page: Page): string => new URL(page.url()).pathname;

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(?:\?|$)/, { timeout: 20_000 });
}

async function accessToken(page: Page): Promise<string> {
  const raw = await page.evaluate(
    () => Object.entries(localStorage).find(([k]) => k.includes('access_token'))?.[1] ?? '',
  );
  return String(raw).replace(/^"|"$/g, '');
}

async function apiGet(request: APIRequestContext, suffix: string, token: string): Promise<any> {
  const res = await request.get(`${API_URL}${suffix}`, {
    headers: { Authorization: `Bearer ${token}` },
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

test('UV 定制订单：订单详情可直接上传图稿，上传后进入待审核', async ({ page, request }) => {
  test.setTimeout(240_000);
  await loginUser(page);

  // 1) 商品页：勾选 UV 定制（故意不上传图稿，模拟「下单后再传」的真实路径）
  const picked = await pickSellableSku(page.request, CUSTOM_QTY);
  await page.goto(`${USER_URL}/product/${picked.productId}`);
  const sku = page.locator('.spec-item:not(.disabled)').filter({ hasText: picked.spec }).first();
  await expect(sku, `商品 ${picked.productId} 应有可售 ≥${CUSTOM_QTY} 的规格`).toBeVisible({ timeout: 20_000 });
  await sku.click();

  await page.locator('.custom-panel__check').click();
  await expect(page.locator('.upload-drop'), '勾选定制后商品页会出现上传区').toBeVisible({ timeout: 10_000 });
  const qtyInput = page.locator('.buy__qty input').first();
  await qtyInput.fill(String(CUSTOM_QTY));
  await qtyInput.blur();

  await page.locator('.buy-btn--now').click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 20_000 });

  // 2) 结算页勾「含 UV 定制件」→ 提交并支付
  await page.locator('.co-custom').click();
  await expect(page.locator('.quote-line', { hasText: '定制费' }), '勾选定制后应出现定制费').toBeVisible({
    timeout: 20_000,
  });
  await page.getByRole('button', { name: '提交订单并支付' }).click();

  const paidDialog = page.locator('.el-dialog').filter({ hasText: '支付成功' });
  await expect(paidDialog, '下单/支付失败：未出现「支付成功」弹窗').toBeVisible({ timeout: 40_000 });
  const orderNo = /订单号[：:]\s*([A-Za-z0-9_-]+)/.exec(await paidDialog.innerText())?.[1];
  expect(orderNo, '未能从支付弹窗解析订单号').toBeTruthy();
  await paidDialog.getByRole('button', { name: '查看订单' }).click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toBe(`/order/${orderNo}`);

  // 3) 详情页必须有定制卡片 + 上传入口（缺陷点：以前这里什么都没有）
  const card = page.getByTestId('order-custom');
  await expect(card, '订单详情应有「UV 定制图稿」卡片').toBeVisible({ timeout: 20_000 });
  await expect(card).toContainText('UV 定制图稿');
  const customOrderId = String(
    (await card.locator('.od-custom__no').first().innerText()).trim() || '',
  );
  expect(customOrderId, '定制卡片应显示定制单号').toMatch(/^CU/);

  const uploadBtn = card.getByTestId('order-custom-upload');
  await expect(uploadBtn, '待提交图稿状态应给「上传图稿」按钮').toBeVisible({ timeout: 20_000 });
  await expect(card.locator('.od-custom__status').first()).toContainText('待提交图稿');

  // 4) 上传图稿 → 状态进入待审核
  const token = await accessToken(page);
  // 走真实交互：点按钮 → 系统文件选择器 → 选图（直接 setInputFiles 不会经过按钮，draftTarget 为空）
  const chooserPromise = page.waitForEvent('filechooser');
  await uploadBtn.click();
  const chooser = await chooserPromise;
  await chooser.setFiles(DRAFT_IMAGE);
  await expect(page.locator('.el-message').filter({ hasText: '图稿已上传' })).toBeVisible({
    timeout: 30_000,
  });
  await expect(card.locator('.od-custom__status').first(), '上传后状态应变为待审核').toContainText(
    '待审核',
    { timeout: 20_000 },
  );

  // 5) 后端口径核对：版本 v1、状态 WAIT_REVIEW、fileId 是文件服务真实路径
  const list = await apiGet(request, `/custom-orders/by-order/${orderNo}`, token);
  expect(list?.code, `按订单查定制单失败：${JSON.stringify(list)}`).toBe(0);
  expect(list.data.length, '该订单应有一张定制单').toBeGreaterThan(0);
  const row = list.data[0];
  expect(row.customOrderId).toBe(customOrderId);
  expect(row.status).toBe('WAIT_REVIEW');
  expect(Number(row.version)).toBe(1);

  const detail = await apiGet(request, `/customize/orders/${customOrderId}`, token);
  const fileId = String(detail?.data?.drafts?.[0]?.fileId ?? '');
  expect(fileId, `图稿 fileId 应是文件服务真实相对路径：${fileId}`).toMatch(/^\d{4}\/\d{2}\//);
});

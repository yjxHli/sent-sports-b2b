/**
 * 收货地址闭环（2026-11-15）：结算页选择 → 后端快照落库 → 订单详情淘宝版展示。
 *
 * 用户诉求：「结算页选择 -> 后端快照落库 -> 订单详情淘宝版展示」，
 * 并且下单时必须带地址（后端空地址直接拒绝）。
 *
 * 断言口径：
 *   1) 结算页会拉地址簿并自动预选默认地址（`.co-addr__item--on`）；
 *   2) 选中的地址随 `POST /api/orders` 的 `addressId` 提交，下单成功；
 *   3) **把地址从地址簿删掉之后**，订单详情仍然显示当时的收件人 / 地址——
 *      这条最关键：证明订单存的是快照，不是每次回查地址簿；
 *   4) 详情页手机号中间四位打码（淘宝同款 138****8000）；
 *   5) 结算页内新增地址 → 保存后立刻被选中，并能删除。
 */
import { test, expect, type Page } from '@playwright/test';
import { pickSellableSku } from './pick-product';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088').replace(/\/+$/, '');
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

const pathOf = (page: Page): string => new URL(page.url()).pathname;

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
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

async function tokenOf(page: Page): Promise<string> {
  return page.evaluate(
    () => Object.entries(localStorage).find(([k]) => k.includes('access_token'))?.[1] ?? '',
  );
}

interface AddressSeed {
  receiver: string;
  phone: string;
  province: string;
  city: string;
  district: string;
  detail: string;
  isDefault: boolean;
}

async function createAddress(page: Page, seed: AddressSeed): Promise<number> {
  const token = await tokenOf(page);
  const res = await page.request.post(`${API_URL}/api/addresses`, {
    data: seed,
    headers: { Authorization: `Bearer ${token}` },
    failOnStatusCode: false,
  });
  const body: any = await res.json().catch(() => null);
  expect(body?.code, `创建收货地址失败：${JSON.stringify(body)}`).toBe(0);
  return Number(body.data.id);
}

async function deleteAddress(page: Page, id: number): Promise<void> {
  const token = await tokenOf(page);
  await page.request.delete(`${API_URL}/api/addresses/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
    failOnStatusCode: false,
  });
}

/** 商品页 → 立即购买 → 结算页（并等取价完成，否则提交会弹「订单信息暂不可用」） */
async function gotoCheckout(page: Page): Promise<void> {
  // 现场挑有货的规格：写死 1001 会随运行次数被买空（详情页一个可点规格都没有）
  const picked = await pickSellableSku(page.request, 2);
  await page.goto(`${USER_URL}/product/${picked.productId}`);
  const firstSku = page.locator('.spec-item:not(.disabled)').first();
  await expect(firstSku, `商品 ${picked.productId} 应有可售规格`).toBeVisible({ timeout: 20_000 });
  await firstSku.click();
  await page.locator('.buy-btn--now').click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 20_000 });
  await expect(page.locator('.quote-line--total .sent-price'), '结算页应完成取价').toHaveText(
    /¥\d/,
    { timeout: 20_000 },
  );
  await expect(page.locator('.co-addr'), '结算页应有收货信息区块').toBeVisible({ timeout: 20_000 });
}

/** 唯一收件人，避免与其它用例/历史数据串味 */
const STAMP = Date.now().toString().slice(-6);

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('结算页选中地址下单，详情页展示收件人、脱敏电话与完整地址', async ({ page }) => {
  test.setTimeout(180_000);
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);

  const receiver = `E2E收件人${STAMP}`;
  const phone = '13700137000';
  const addressId = await createAddress(page, {
    receiver,
    phone,
    province: '江苏省',
    city: '南京市',
    district: '玄武区',
    detail: `龙蟠路 ${STAMP} 号 森特大厦 8 楼`,
    isDefault: true,
  });

  let orderNo = '';
  try {
    await gotoCheckout(page);

    // 1) 刚建的（默认）地址被自动预选
    const chosen = page.locator('.co-addr__item--on');
    await expect(chosen, '结算页应自动选中默认地址').toHaveCount(1, { timeout: 15_000 });
    await expect(chosen).toContainText(receiver);
    await expect(chosen).toContainText('玄武区');

    // 2) 下单：请求体里必须带 addressId
    const createReq = page.waitForRequest(
      (r) => r.url().endsWith('/api/orders') && r.method() === 'POST',
      { timeout: 20_000 },
    );
    await page.getByRole('button', { name: '提交订单并支付' }).click();
    const reqBody: any = (await createReq).postDataJSON();
    expect(Number(reqBody.addressId), '下单请求必须携带 addressId').toBe(addressId);

    const paidDialog = page.locator('.el-dialog').filter({ hasText: '支付成功' });
    await expect(paidDialog, '下单/支付失败：未出现「支付成功」弹窗').toBeVisible({ timeout: 40_000 });
    const matched = /订单号[：:]\s*([A-Za-z0-9_-]+)/.exec(await paidDialog.innerText());
    expect(matched, '未能从支付弹窗解析订单号').not.toBeNull();
    orderNo = matched![1];
    await page.keyboard.press('Escape');

    // 3) 关键一步：把这条地址从地址簿删掉，详情页仍应显示它（快照已固化）
    await deleteAddress(page, addressId);
  } finally {
    if (!orderNo) await deleteAddress(page, addressId).catch(() => undefined);
  }

  await page.goto(`${USER_URL}/order/${orderNo}`);
  const addrBox = page.locator('.od-addr');
  await expect(addrBox, '订单详情应有收货地址行').toBeVisible({ timeout: 20_000 });
  await expect(addrBox, '地址已从地址簿删除，详情仍要显示快照里的收件人').toContainText(receiver);
  await expect(addrBox).toContainText('玄武区');
  await expect(addrBox).toContainText(`龙蟠路 ${STAMP} 号`);
  // 手机号中间四位必须打码
  await expect(addrBox, '电话要中间脱敏').toContainText('137****7000');
  await expect(addrBox, '不应出现完整手机号').not.toContainText('13700137000');

  // 右栏「收货信息」与交易快照同样脱敏
  await expect(page.locator('.od-col-side'), '右栏收货信息也要脱敏').toContainText('137****7000');

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

test('结算页新增地址：保存后立即选中，并能删除', async ({ page }) => {
  test.setTimeout(120_000);
  await loginUser(page);
  await gotoCheckout(page);

  const receiver = `E2E新增${STAMP}`;
  await page.getByRole('button', { name: /新增地址|新增收货地址/ }).first().click();

  const dialog = page.locator('.el-dialog').filter({ hasText: '新增收货地址' });
  await expect(dialog).toBeVisible({ timeout: 15_000 });
  await dialog.getByPlaceholder('请填写收件人姓名').fill(receiver);
  await dialog.getByPlaceholder('请填写 11 位手机号').fill('13900139000');
  await dialog.getByPlaceholder('如：江苏省').fill('浙江省');
  await dialog.getByPlaceholder('如：南京市').fill('杭州市');
  await dialog.getByPlaceholder('如：玄武区').fill('西湖区');
  await dialog.getByPlaceholder('街道、门牌号、楼层等').fill(`文三路 ${STAMP} 号 3 幢 501`);
  await dialog.getByRole('button', { name: '保存并使用' }).click();

  await expect(dialog, '保存后弹窗应关闭').toBeHidden({ timeout: 15_000 });

  // 新地址应立刻出现在列表里并被选中（用户不必再点一次）
  const row = page.locator('.co-addr__item').filter({ hasText: receiver });
  await expect(row, '新增的地址应出现在结算页').toBeVisible({ timeout: 15_000 });
  await expect(page.locator('.co-addr__item--on'), '新增后应自动选中该地址').toContainText(receiver);

  // 清理：UI 删除这条地址
  await row.getByRole('button', { name: '删除' }).click();
  const box = page.locator('.el-message-box');
  await expect(box).toBeVisible({ timeout: 15_000 });
  await box.getByRole('button', { name: '删除' }).click();
  await expect(page.locator('.co-addr__item').filter({ hasText: receiver })).toHaveCount(0, {
    timeout: 15_000,
  });
});

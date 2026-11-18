/**
 * 优惠券发放与使用（2026-11-18 用户要求，TDD 验收）：
 *   1) 新用户注册自动得 3 张券（5 无门槛 / 满20减10 / 满80减50）+ 站内信；
 *   2) 管理端「客户与会员 → 优惠券管理」可按券型发券、指定用户售后补券 + 站内信文案；
 *   3) 结算页选券 → 取价显示券抵扣 → 下单请求带 couponId → 订单落库折扣额；
 *   4) 领券中心：上可领券 + 下商品，个人客户看不到企业专属券，重复领取被拒绝。
 *
 * 金额单位一律分；面额档位禁止写死在前端，以服务端模板接口为准。
 * V43 起模板从 9 档扩到 17 档（新增四类券型），断言用「包含」而不是「等于」，避免数据漂移假红。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  USER_URL, ADMIN_URL, join, apiCall, loginApi,
  ADMIN_NAME, ADMIN_PASSWORD, CUSTOMER_PHONE, CUSTOMER_PASSWORD, CUSTOMER_ID,
} from './chat-helpers';
import { pickSellableSku } from './pick-product';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;
/** 管理端必有的 9 个金额档位（V42 存量档位，V43 只新增不改） */
const AMOUNTS = [500, 800, 1000, 1500, 2000, 3000, 5000, 8000, 10000];
/** 四类券型，V43 起管理端与领券中心都要能覆盖 */
const TYPES = ['NO_THRESHOLD', 'FULL_REDUCTION', 'CATEGORY', 'ENTERPRISE'];

async function loginUserUi(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('新用户注册自动发 3 张券，并收到站内信提醒', async ({ request }) => {
  test.setTimeout(90_000);
  const phone = `139${String(Date.now()).slice(-8)}`;
  const password = 'Coupon@123456';

  const registered = await apiCall(request, 'POST', '/auth/register', {
    phone, password, confirmPassword: password,
  });
  expect(registered?.code, `注册失败：${JSON.stringify(registered)}`).toBe(0);

  const token = await loginApi(request, phone, password, 'user');
  const mine = await apiCall(request, 'GET', '/coupons/mine', undefined, token);
  expect(mine?.code, `我的券查询失败：${JSON.stringify(mine)}`).toBe(0);
  const gifts = (mine?.data ?? []).filter((c: any) => c.source === 'REGISTER');
  expect(gifts.length, '注册礼应恰好 3 张').toBe(3);
  expect(gifts.map((c: any) => Number(c.amountCent)).sort((a: number, b: number) => a - b))
    .toEqual([500, 1000, 5000]);
  expect(gifts.map((c: any) => Number(c.thresholdCent)).sort((a: number, b: number) => a - b))
    .toEqual([0, 2000, 8000]);

  const messages = await apiCall(request, 'GET', '/messages?page=1&size=50', undefined, token);
  expect(messages?.code).toBe(0);
  const promo = (messages?.data?.list ?? []).filter(
    (m: any) => m.msgType === 'PROMO' && m.bizType === 'COUPON',
  );
  expect(promo.length, '注册发券必须有站内信').toBeGreaterThan(0);
  expect(promo[0].content).toContain('平台活动');
});

test('管理端按面额档位给指定客户售后补券，站内信走「平台补充」文案', async ({ request }) => {
  test.setTimeout(90_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');

  const templateRes = await apiCall(request, 'GET', '/admin/coupons/templates', undefined, adminToken);
  expect(templateRes?.code, `券档位查询失败：${JSON.stringify(templateRes)}`).toBe(0);
  const templates = templateRes?.data ?? [];
  expect(templates.map((t: any) => Number(t.amountCent)).sort((a: number, b: number) => a - b))
    .toEqual(expect.arrayContaining(AMOUNTS));
  // 四类券型都能发：券型字段必须齐全，否则运营只看到金额、分不清品类券/企业券
  const types = new Set(templates.map((t: any) => t.couponType));
  for (const type of TYPES) {
    expect(types.has(type), `管理端券档位应覆盖 ${type} 券型`).toBe(true);
  }

  const reason = `E2E售后补券-${Date.now()}`;
  const issued = await apiCall(request, 'POST', '/admin/coupons/issue', {
    templateId: templates.find((t: any) => Number(t.amountCent) === 800).templateId,
    customerIds: [CUSTOMER_ID],
    thresholdCent: 0,
    reason,
    scene: 'AFTER_SALE',
  }, adminToken);
  expect(issued?.code, `发券失败：${JSON.stringify(issued)}`).toBe(0);
  expect(Number(issued?.data)).toBe(1);

  const issues = await apiCall(request, 'GET', '/admin/coupons/issues?limit=50', undefined, adminToken);
  expect(issues?.code).toBe(0);
  expect((issues?.data ?? []).some((r: any) => r.reason === reason && Number(r.customerId) === CUSTOMER_ID))
    .toBe(true, '发放记录要能查到');

  const userToken = await loginApi(request, CUSTOMER_PHONE, CUSTOMER_PASSWORD, 'user');
  const mine = await apiCall(request, 'GET', '/coupons/mine', undefined, userToken);
  const coupon = (mine?.data ?? []).find((c: any) => Number(c.amountCent) === 800 && c.reason === reason);
  expect(coupon, '指定客户应收到这张 8 元券').toBeTruthy();

  const messages = await apiCall(request, 'GET', '/messages?page=1&size=50', undefined, userToken);
  const notice = (messages?.data?.list ?? []).find((m: any) => m.bizType === 'COUPON' && m.bizNo === coupon.couponNo);
  expect(notice, '补券必须单独发站内信').toBeTruthy();
  expect(notice.content).toContain('平台补充');
  expect(notice.content).toContain(reason);
});

test('结算页选券后显示券抵扣，下单请求带 couponId 且订单落库折扣额', async ({ page }) => {
  test.setTimeout(180_000);
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  // 先给演示客户发一张确定的无门槛 10 元券，避免依赖历史券数据
  const adminToken = await loginApi(page.request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const templateRes = await apiCall(page.request, 'GET', '/admin/coupons/templates', undefined, adminToken);
  const template = (templateRes?.data ?? []).find((t: any) => Number(t.amountCent) === 1000);
  expect(template, '服务端应有 10 元券档位').toBeTruthy();
  const issued = await apiCall(page.request, 'POST', '/admin/coupons/issue', {
    templateId: template.templateId, customerIds: [CUSTOMER_ID], thresholdCent: 0,
    reason: `E2E结算用券-${Date.now()}`, scene: 'ACTIVITY',
  }, adminToken);
  expect(issued?.code).toBe(0);

  await loginUserUi(page);
  const picked = await pickSellableSku(page.request, 1);
  await page.goto(join(USER_URL, `/product/${picked.productId}`));
  await expect(page.locator('.spec-item:not(.disabled)').first()).toBeVisible({ timeout: 20_000 });
  await page.locator('.spec-item:not(.disabled)').first().click();
  await page.locator('.buy-btn--now').click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 20_000 });
  await expect(page.locator('.quote-line--total .sent-price')).toHaveText(/¥\d/, { timeout: 20_000 });

  const select = page.getByTestId('checkout-coupon');
  await expect(select, '结算页应展示优惠券选择器').toBeVisible({ timeout: 20_000 });
  await select.click();
  await page.locator('.el-select-dropdown__item:visible', { hasText: '10元券' }).first().click();
  await expect(page.locator('.quote-line--discount'), '选券后应显示券抵扣行').toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.quote-line--discount')).toContainText('¥10.00');

  const createReq = page.waitForRequest(
    (r) => r.url().endsWith('/api/orders') && r.method() === 'POST',
    { timeout: 30_000 },
  );
  await page.getByRole('button', { name: '提交订单并支付' }).click();
  const body: any = (await createReq).postDataJSON();
  expect(Number(body.couponId), '下单请求必须把选中的券带给后端').toBeGreaterThan(0);

  const paid = page.locator('.el-dialog').filter({ hasText: '支付成功' });
  await expect(paid, '下单/支付失败').toBeVisible({ timeout: 40_000 });
  const orderNo = /订单号[：:]\s*([A-Za-z0-9_-]+)/.exec(await paid.innerText())?.[1] ?? '';
  expect(orderNo, '未能从支付弹窗解析订单号').toBeTruthy();

  const userToken = await loginApi(page.request, CUSTOMER_PHONE, CUSTOMER_PASSWORD, 'user');
  const detail = await apiCall(page.request, 'GET', `/orders/${orderNo}`, undefined, userToken);
  expect(detail?.code).toBe(0);
  expect(Number(detail?.data?.discountCent ?? 0), '订单头要落券抵扣额').toBe(1000);

  expect(errors, `控制台错误：${errors.join(' | ')}`).toEqual([]);
});

test('管理端优惠券管理页可按入口打开并展示券档位', async ({ page }) => {
  test.setTimeout(90_000);
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });

  await page.goto(join(ADMIN_URL, '/customer/coupon'));
  await expect(page.getByRole('heading', { name: '优惠券管理' })).toBeVisible({ timeout: 20_000 });
  // ElementPlus 的下拉项不在 <option> 里，直接展开数可见项；V43 起档位数只增不减
  await page.getByTestId('coupon-template').click();
  const items = page.locator('.el-select-dropdown__item:visible');
  await expect(items.first()).toBeVisible({ timeout: 20_000 });
  expect(await items.count(), '券档位不少于 9 档').toBeGreaterThanOrEqual(AMOUNTS.length);

  // 券名已带面额，下拉里只显示一次金额，不能是「¥5 · 5元券」
  const firstOption = items.first();
  await expect(firstOption).toContainText('5元券');
  expect(await firstOption.innerText(), '券面额不能重复展示').not.toContain('¥5 · 5元券');
  // 四类券型在下拉里可见（品类券/企业专属券靠这个标签区分）
  const dropdownText = await page.locator('.el-select-dropdown:visible').last().innerText();
  for (const label of ['无门槛券', '满减券', '品类券', '企业专属券']) {
    expect(dropdownText, `下拉应标出「${label}」`).toContain(label);
  }
});

test('结算页券列表：价格只显示一次，可用券强调、不可用券置灰且点击提示未满足使用要求', async ({ page }) => {
  test.setTimeout(180_000);

  // 给演示客户准备一张确定可用（5 元无门槛）和一张确定不可用（满 100 元减 100）的券
  const adminToken = await loginApi(page.request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const templateRes = await apiCall(page.request, 'GET', '/admin/coupons/templates', undefined, adminToken);
  const byAmount = (cent: number): any => (templateRes?.data ?? [])
    .find((t: any) => Number(t.amountCent) === cent);
  for (const [amount, threshold] of [[500, 0], [10000, 1000000]]) {
    const issued = await apiCall(page.request, 'POST', '/admin/coupons/issue', {
      templateId: byAmount(amount).templateId,
      customerIds: [CUSTOMER_ID],
      thresholdCent: threshold,
      reason: `E2E券列表样式-${amount}-${Date.now()}`,
      scene: 'ACTIVITY',
    }, adminToken);
    expect(issued?.code, `发券失败：${JSON.stringify(issued)}`).toBe(0);
  }

  await loginUserUi(page);
  const picked = await pickSellableSku(page.request, 1);
  await page.goto(join(USER_URL, `/product/${picked.productId}`));
  await expect(page.locator('.spec-item:not(.disabled)').first()).toBeVisible({ timeout: 20_000 });
  await page.locator('.spec-item:not(.disabled)').first().click();
  await page.locator('.buy-btn--now').click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 20_000 });
  await expect(page.locator('.quote-line--total .sent-price')).toHaveText(/¥\d/, { timeout: 20_000 });

  await page.getByTestId('checkout-coupon').click();
  const dropdown = page.locator('.el-select-dropdown:visible').last();
  const usable = dropdown.locator('.el-select-dropdown__item', { hasText: '5元券' }).first();
  const unusable = dropdown.locator('.el-select-dropdown__item', { hasText: '100元券' }).first();
  await expect(usable, '不可用/可用券都要出现在列表里').toBeVisible({ timeout: 15_000 });
  await expect(unusable, '不满足门槛的券也要列出来置灰').toBeVisible({ timeout: 15_000 });

  // 价格只在券名里出现一次，不能是「¥5 5元券」这种重复
  expect(await usable.innerText(), '券面额不能重复展示').not.toContain('¥5 5元券');
  await expect(usable).toHaveClass(/coupon-option--usable/);
  await expect(unusable).toHaveClass(/coupon-option--unusable/);

  await unusable.click();
  await expect(page.locator('.el-message', { hasText: '未满足使用要求' })).toBeVisible({ timeout: 10_000 });
  const selected = await page.getByTestId('checkout-coupon').innerText();
  expect(selected, '不满足门槛的券不能被真正选中').not.toContain('100元券');
});

/**
 * 领券中心（2026-11-18 用户需求 2）：
 * - 首页「领券中心」进新页面 /coupon/center，上半区可领券、下半区商品；
 * - 个人客户看不到企业专属券（B2B 大额阶梯券个人不可领）；
 * - 同一张券不能重复领：第二次领取返回业务拒绝。
 */
test('领券中心：个人不见企业专属券，可领取且不能重复领取', async ({ page, request }) => {
  test.setTimeout(180_000);
  const phone = `137${String(Date.now()).slice(-8)}`;
  const password = 'Coupon@123456';
  const registered = await apiCall(request, 'POST', '/auth/register', {
    phone, password, confirmPassword: password,
  });
  expect(registered?.code, `注册失败：${JSON.stringify(registered)}`).toBe(0);

  const token = await loginApi(request, phone, password, 'user');
  const claimable = await apiCall(request, 'GET', '/coupons/claimable', undefined, token);
  expect(claimable?.code, `可领券查询失败：${JSON.stringify(claimable)}`).toBe(0);
  const cards: any[] = claimable?.data ?? [];
  expect(cards.length, '领券中心应有可领券').toBeGreaterThan(0);
  expect(cards.every((c) => c.customerScope !== 'ENTERPRISE'),
    '个人客户不应看到企业专属券').toBe(true);
  const types = new Set(cards.map((c) => c.couponType));
  for (const type of ['NO_THRESHOLD', 'FULL_REDUCTION', 'CATEGORY']) {
    expect(types.has(type), `领券中心应覆盖 ${type} 券型`).toBe(true);
  }

  const target = cards.find((c) => c.couponType === 'NO_THRESHOLD');
  expect(target, '应有无门槛券可领').toBeTruthy();

  const first = await apiCall(request, 'POST', `/coupons/claim/${target.templateId}`, undefined, token);
  expect(first?.code, `领取失败：${JSON.stringify(first)}`).toBe(0);
  expect(first?.data?.couponNo).toBeTruthy();

  const again = await apiCall(request, 'POST', `/coupons/claim/${target.templateId}`, undefined, token);
  expect(again?.code, '重复领取必须被业务拒绝').toBe(42201);
  expect(String(again?.message ?? '')).toContain('已领取');

  const mine = await apiCall(request, 'GET', '/coupons/mine', undefined, token);
  const got = (mine?.data ?? []).find((c: any) => c.couponNo === first.data.couponNo);
  expect(got, '领取的券要进「我的优惠券」').toBeTruthy();
  expect(got.source).toBe('CLAIM');

  // UI：上半区可领券（含已领取态）、下半区商品
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(phone);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });

  await page.goto(join(USER_URL, '/coupon/center'));
  await expect(page.getByRole('heading', { name: '领券中心' })).toBeVisible({ timeout: 20_000 });
  const claimCards = page.getByTestId('claim-card');
  await expect(claimCards.first()).toBeVisible({ timeout: 20_000 });
  expect(await claimCards.count()).toBeGreaterThan(0);
  expect(await page.locator('.center-page').innerText(), '个人客户页面不应出现企业专属券')
    .not.toContain('企业专属券');

  // 已领过的那张直接是「已领取」，按钮禁用
  const claimedCard = claimCards.filter({ hasText: target.name }).first();
  await expect(claimedCard.getByTestId('claim-btn')).toHaveText('已领取');

  // 再领一张没领过的 → 提示成功，按钮变「已领取」
  const claimableBtn = page.getByTestId('claim-btn').filter({ hasText: '立即领取' }).first();
  await expect(claimableBtn, '应还有可领取的券').toBeVisible({ timeout: 20_000 });
  await claimableBtn.click();
  await expect(page.locator('.el-message', { hasText: '领取成功' })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId('claim-btn').filter({ hasText: '已领取' }))
    .toHaveCount(2, { timeout: 15_000 });

  // 下半区商品：点卡片进详情
  const goods = page.locator('.good-card').first();
  await expect(goods, '领券中心下半区要有商品').toBeVisible({ timeout: 20_000 });
  await goods.click();
  await expect(page).toHaveURL(/\/product\/\w+/, { timeout: 20_000 });
});

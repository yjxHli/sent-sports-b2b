/**
 * 定制单图稿贯通管理端（缺陷回归）：
 *
 * 用户反馈：「刚刚提交了一个定制单并附带图片，但是管理端并没有查看到」。
 * 根因链：
 *   ① 商品页选图只取了文件名，没有真正上传文件（无 fileId）；
 *   ② 下单时 `custom_order` 才创建（WAIT_DRAFT），图稿必须在**下单之后**提交；
 *   ③ 旧实现提交的是 `f_<时间戳>` 这种假 ID，管理端拿着它拼不出一条可访问的图片地址。
 *
 * 现在的口径：
 *   商品页选图 → `POST /api/files` 拿真实 fileId 暂存 → 支付成功后自动补交到刚生成的定制单
 *   → `custom_draft.file_id` 落的是文件服务相对路径 → 审图台直接渲染缩略图。
 *
 * 断言：
 *   1) 商品页选完图立刻能看到缩略图（说明文件真的传上去了）；
 *   2) 支付后「我的定制」里出现 WAIT_REVIEW 的定制单，且管理端详情 drafts[0].fileId 是真实路径；
 *   3) 该 fileId 能匿名取到图片（<img> 无法带 Authorization）；
 *   4) 审图台 UI 选中该定制单后，图稿缩略图真的加载出来了（naturalWidth > 0）。
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
const PRODUCT_ID = process.env.E2E_PRODUCT_ID ?? '1001';

/** 定制图稿：36 件起订按页面口径填 30 */
const CUSTOM_QTY = 30;
const DRAFT_IMAGE = path.join(__dirname, 'fixtures', 'proof.png');

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

async function loginUser(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
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

async function loginAdmin(page: Page): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `管理端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

test('定制图稿：用户上传后管理端审图台能看到真实图片', async ({ page, request, browser }) => {
  test.setTimeout(180_000);

  const userLogin = await apiPost(request, '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user',
    deviceId: `e2e-custom-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken, `用户端 API 登录失败：${JSON.stringify(userLogin)}`).toBeTruthy();

  const adminLogin = await apiPost(request, '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin',
    deviceId: `e2e-custom-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken, `管理端 API 登录失败：${JSON.stringify(adminLogin)}`).toBeTruthy();

  // 0) 记录下单前已有的定制单：演示数据里也有定制单（且带着历史假 fileId），
  //    不能靠「第一条非 WAIT_DRAFT」认领，必须按「新出现的单号」定位本次这笔。
  const before = await apiGet(request, '/custom-orders/my?page=1&size=20', userToken);
  const beforeIds = new Set<string>((before?.data?.list ?? []).map((c: any) => String(c.customOrderId)));

  // 1) 商品页：选规格 → 勾选 UV 定制 → 选图（必须真的上传到文件服务）
  await loginUser(page);
  await page.goto(join(USER_URL, `/product/${PRODUCT_ID}`));
  const specs = page.locator('.spec-item:not(.disabled)');
  await expect(specs.first(), '商品页应有可售规格').toBeVisible({ timeout: 20_000 });
  // 定制 30 件起订：要挑一个库存 ≥ 30 的规格（演示数据里有的规格已被大量下单占满）
  const stockHint = page.locator('.buy__qty-hint');
  let picked = false;
  for (let i = 0; i < await specs.count(); i += 1) {
    await specs.nth(i).click();
    const text = (await stockHint.innerText()).replace(/\s+/g, ' ');
    const stock = Number(/库存\s*(\d+)\s*件/.exec(text)?.[1] ?? 0);
    if (stock >= CUSTOM_QTY) {
      picked = true;
      break;
    }
  }
  expect(picked, `商品页需要一个库存 ≥ ${CUSTOM_QTY} 的可售规格才能下定制单`).toBe(true);

  await page.locator('.custom-panel__check').click();
  await expect(page.locator('.upload-drop'), '勾选定制后应出现上传区').toBeVisible({ timeout: 10_000 });
  await page.locator('input.upload-input').setInputFiles(DRAFT_IMAGE);
  await expect(
    page.locator('img.upload-file__thumb'),
    '选完图应立刻显示缩略图（说明文件已上传并拿到 fileId）',
  ).toBeVisible({ timeout: 20_000 });

  // 定制 30 件起订：数量要拉到最小起订量，否则试算/下单可能被拒
  const qtyInput = page.locator('.buy__qty input').first();
  await qtyInput.fill(String(CUSTOM_QTY));
  await qtyInput.blur();

  // 2) 立即购买 → 结算页（勾定制件）→ 提交并支付
  await page.locator('.buy-btn--now').click();
  await expect(page).toHaveURL(/\/checkout/, { timeout: 20_000 });
  await page.locator('.co-custom').click();
  await expect(page.locator('.quote-line--total .sent-price'), '结算页应完成取价').toHaveText(/¥\d/, {
    timeout: 20_000,
  });
  await page.getByRole('button', { name: '提交订单并支付' }).click();
  const paidDialog = page.locator('.el-dialog').filter({ hasText: '支付成功' });
  await expect(paidDialog, '下单/支付失败：未出现「支付成功」弹窗').toBeVisible({ timeout: 40_000 });
  const dialogText = await paidDialog.innerText();
  const orderNo = /订单号[：:]\s*([A-Za-z0-9_-]+)/.exec(dialogText)?.[1];
  expect(orderNo, `未能从支付弹窗解析订单号：${dialogText}`).toBeTruthy();
  await page.keyboard.press('Escape');

  // 3) 支付后图稿应被自动补交：轮询「我的定制」直到出现非 WAIT_DRAFT 的定制单
  let customOrderId = '';
  let customStatus = '';
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const mine = await apiGet(request, '/custom-orders/my?page=1&size=20', userToken);
    const rows: any[] = mine?.data?.list ?? [];
    const fresh = rows.find(
      (c) => !beforeIds.has(String(c.customOrderId)) && c.status && c.status !== 'WAIT_DRAFT',
    );
    if (fresh) {
      customOrderId = String(fresh.customOrderId);
      customStatus = String(fresh.status);
      break;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  expect(
    customOrderId,
    `支付后应生成「本次新增」的定制单并自动补交图稿（状态不再是 WAIT_DRAFT，实际 ${customStatus}）`,
  ).toBeTruthy();

  // 4) 管理端详情：图稿 fileId 是文件服务的真实相对路径，且能取到图片
  const detail = await apiGet(request, `/admin/custom-orders/${customOrderId}`, adminToken);
  const drafts: any[] = detail?.data?.drafts ?? [];
  expect(drafts.length, `管理端应能看到图稿版本：${JSON.stringify(detail)}`).toBeGreaterThan(0);
  const fileId = String(drafts[0].fileId ?? '');
  expect(fileId, `图稿 fileId 应是文件服务相对路径，不能再是假的 f_<时间戳>：${fileId}`).toMatch(/^\d{4}\/\d{2}\//);
  expect(detail?.data?.fileId, '详情里的「最新图稿」也应是同一个真实 fileId').toBe(fileId);

  const imgRes = await request.get(join(API_URL, `/files/${fileId}`), { failOnStatusCode: false });
  expect(imgRes.status(), '图稿文件应可匿名读取（<img> 无法带 Authorization）').toBe(200);
  expect(imgRes.headers()['content-type'] ?? '').toContain('image/');

  // 5) 审图台 UI：选中该定制单后缩略图真的加载出来
  const adminCtx = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const adminPage = await adminCtx.newPage();
  try {
    await loginAdmin(adminPage);
    await adminPage.goto(join(ADMIN_URL, '/custom/review'));
    await expect(
      adminPage.getByRole('heading', { name: '定制审核' }),
      `审图台未打开，当前 URL=${adminPage.url()}`,
    ).toBeVisible({ timeout: 20_000 });
    // Element Plus 的 el-select 是自绘控件，占位符不是 input 的 placeholder：
    // 直接点 .el-select 容器即可展开下拉，再按单号挑选项。
    await adminPage.locator('.el-select').first().click();
    const option = adminPage.locator('.el-select-dropdown__item', { hasText: customOrderId }).first();
    await expect(option, `审图台下拉里应有定制单 ${customOrderId}`).toBeVisible({ timeout: 20_000 });
    await option.click();
    await adminPage.getByRole('button', { name: '加载详情' }).click();

    const thumb = adminPage.locator('.detail-desc .draft-thumb img').first();
    await expect(thumb, '审图台应显示图稿缩略图').toBeVisible({ timeout: 20_000 });
    const loaded = await thumb.evaluate((el) => (el as HTMLImageElement).naturalWidth);
    expect(loaded, '图稿缩略图应真的解码成功（naturalWidth > 0），而不是破图').toBeGreaterThan(0);
  } finally {
    await adminCtx.close();
  }
});

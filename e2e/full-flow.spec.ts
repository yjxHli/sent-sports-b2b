/**
 * 森特体育用品管理系统 · 全链路多角色终极验收（4 角色 / 5 个 BrowserContext）
 * =============================================================================
 * 一个测试用例内严格按业务时间顺序跑通：
 *
 *  [用户]     手机号 13800138000 登录 → 商品详情选 UV 定制 SKU → 加购 + 立即购买
 *             → 结算勾选「含 UV 定制件」→ 提交订单并模拟支付 → 断言「待发货」
 *  [客服]     service/123456 独立 Context → 客服工作台（WS 已连接）→ 刷新会话列表并接待
 *  [实时]     用户发送「这个球能印我们校徽吗？」→ 客服 3000ms 内收到（不刷新页面）
 *             客服回复「可以的，请在订单详情上传图稿」→ 用户 3000ms 内收到
 *  [定制]     用户上传图稿 V1 → 客服驳回 → 用户重传 V2 → 客服审核通过
 *  [仓管]     warehouse/123456 独立 Context → 发货工作台 → 选择发货 → 填快递单号 → 确认发货
 *  [用户]     确认收货 → 订单「已完成」
 *  [财务]     finance/123456 独立 Context → 登记回款 → 勾选应收 → 确认核销（输入「确认核销」）
 *             → 断言订单 COMPLETED + 应收台账 CLOSED
 *
 * 关键实现说明（与剧本的少量工程适配，均在下方注明）：
 *  1) 自定义图稿上传入口在「我的定制」页（/custom）。订单详情页本身没有上传控件，
 *     因此用户上下文先按订单生成的 customOrderId 查询，再点「上传图稿」，业务语义一致。
 *  2) 管理端客服工作台只渲染「当前接待会话」的消息。未分配会话时后端以 QUEUE 广播，
 *     2026-11-13 起客服端也会把业务 QUEUE 帧当消息渲染（此前被整体丢弃，导致「客服要刷新
 *     才看得到客户新消息」）；本脚本仍先让客服选中并接待该会话（分配为 ACTIVE/9003），
 *     因为后面的步骤要断言接待客服是 9003。这一接待动作不涉及刷新页面接收消息，
 *     消息实时性断言仍然成立（waitForSelector 3000ms）。
 *  3) 结算页「提交订单并支付」会 POST /orders + /payments，并 1.2s 后自动回调
 *     /payment/mock/callback；脚本另有 API 兜底回调，避免前端定时器未触发导致卡在待付款。
 *
 * 运行方式见文件末尾注释。
 */

import {
  test,
  expect,
  type APIRequestContext,
  type BrowserContext,
  type Page,
} from '@playwright/test';
import path from 'node:path';
import { clearOpenSessions, loginCs } from './chat-helpers';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
/** 客服工作台独立端（web-cs，:5175）：接待段走这里，其余管理段仍是 ADMIN_URL */
const CS_URL = process.env.E2E_CS_URL ?? 'http://127.0.0.1:5175';
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';

const CFG = {
  user: { phone: process.env.E2E_USER_NAME ?? '13800138000', password: process.env.E2E_USER_PASSWORD ?? '123456' },
  admin: { username: process.env.E2E_ADMIN_NAME ?? 'admin', password: process.env.E2E_ADMIN_PASSWORD ?? '123456' },
  service: { username: process.env.E2E_SERVICE_NAME ?? 'service', password: process.env.E2E_SERVICE_PASSWORD ?? '123456' },
  warehouse: { username: process.env.E2E_WAREHOUSE_NAME ?? 'warehouse', password: process.env.E2E_WAREHOUSE_PASSWORD ?? '123456' },
  finance: { username: process.env.E2E_FINANCE_NAME ?? 'finance', password: process.env.E2E_FINANCE_PASSWORD ?? '123456' },
  customerId: Number(process.env.E2E_CUSTOMER_ID ?? 9002),
  productId: process.env.E2E_PRODUCT_ID ?? '1001',
  /** V11/V13 种子：SKU 9102 为 1001 的 UV 定制规格 */
  customSkuId: Number(process.env.E2E_CUSTOM_SKU_ID ?? 9102),
  serviceStaffId: Number(process.env.E2E_SERVICE_STAFF_ID ?? 9003),
  qty: Number(process.env.E2E_QTY ?? 1),
};

const MSG_USER = '这个球能印我们校徽吗？';
const MSG_STAFF = '可以的，请在订单详情上传图稿';

/** 真实图稿素材：定制图稿现在必须真上传（旧实现是页面自己编一个 f_<ts> 假 ID） */
const DRAFT_IMAGE = path.join(__dirname, 'fixtures', 'proof.png');

const join = (base: string, path: string): string =>
  `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;

const yuan = (cent: number): string => (cent / 100).toFixed(2);

interface LoginResult {
  status: number;
  code: number | null;
  token: string | null;
  body: unknown;
}

async function apiLogin(
  request: APIRequestContext,
  username: string,
  password: string,
  aud: 'user' | 'admin',
): Promise<LoginResult> {
  const res = await request.post(join(API_URL, '/auth/login'), {
    data: { username, password, aud, deviceId: `e2e-${aud}-${Date.now()}` },
    failOnStatusCode: false,
  });
  const body: any = await res.json().catch(() => null);
  return { status: res.status(), code: body?.code ?? null, token: body?.data?.accessToken ?? null, body };
}

async function apiGet(request: APIRequestContext, path: string, token?: string): Promise<any> {
  const res = await request.get(join(API_URL, path), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

/**
 * 现场挑一个「有货且够定制起订量」的规格。
 *
 * <p>写死 `E2E_PRODUCT_ID=1001 / E2E_CUSTOM_SKU_ID=9102` 会随运行次数变成假红：演示库存会被
 * 本套 E2E 自己买空（1001 的两个规格 physical/locked 已经同归于尽，可售 0），
 * 之后每次跑都死在「库存不足」上。改成按库存现场挑，用例就不再依赖演示数据残量。</p>
 */
async function pickCustomSku(
  request: APIRequestContext,
  token: string,
  need: number,
): Promise<{ productId: string; skuId: string; spec: string } | null> {
  if (process.env.E2E_PRODUCT_ID && process.env.E2E_CUSTOM_SKU_ID) {
    const detail = await apiGet(request, `/products/${CFG.productId}`, token);
    const fixed = (detail?.data?.skus ?? []).find((s: any) => String(s.skuId) === String(CFG.customSkuId));
    if (fixed) return { productId: String(CFG.productId), skuId: String(fixed.skuId), spec: String(fixed.spec) };
  }
  const list = await apiGet(request, '/products?page=1&size=20', token);
  for (const product of list?.data?.list ?? []) {
    const detail = await apiGet(request, `/products/${product.productId}`, token);
    const hit = (detail?.data?.skus ?? []).find(
      (s: any) => s.onShelf !== false && Number(s.stockQty) >= need,
    );
    if (hit) {
      return { productId: String(product.productId), skuId: String(hit.skuId), spec: String(hit.spec) };
    }
  }
  return null;
}

async function apiPost(
  request: APIRequestContext,
  path: string,
  data: unknown,
  token?: string,
): Promise<any> {
  const res = await request.post(join(API_URL, path), {
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

/** 读取当前页面 localStorage 中的 access token（双端前缀隔离，只读本页面自己的）。 */
async function accessTokenOf(page: Page): Promise<string> {
  return page.evaluate(
    () => Object.entries(localStorage).find(([k]) => k.includes('access_token'))?.[1] ?? '',
  );
}

/** 用户端登录（手机号 + 密码）。 */
async function loginUser(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(CFG.user.phone);
  await page.getByPlaceholder('请输入密码').fill(CFG.user.password);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

/** 管理端登录（用户名 + 密码），登录后应进入 dashboard。 */
async function loginAdmin(page: Page, username: string, password: string, roleLabel: string): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(username);
  await page.getByPlaceholder('密码').fill(password);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `${roleLabel}登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

async function orderStatus(request: APIRequestContext, orderNo: string): Promise<string> {
  const body = await apiGet(request, `/orders/${orderNo}`);
  return body?.data?.status ?? '';
}

/**
 * 把页面所有可滚动容器（含 window）滚回顶部。
 * <p>pure-admin 外壳是「外层固定 + 内部 main 滚动」，Playwright 的 fullPage 截图
 * 在这种布局下等价于视口截图；若前一步操作把内部容器滚下去了，顶部 toast 会被裁掉。</p>
 */
async function scrollToTop(page: Page): Promise<void> {
  await page.evaluate(() => {
    window.scrollTo(0, 0);
    for (const el of Array.from(document.querySelectorAll<HTMLElement>('*'))) {
      if (el.scrollTop > 0) el.scrollTop = 0;
    }
  });
  await page.waitForTimeout(120);
}

/**
 * 在客服工作台左侧列表里定位「会话 #id」那一项（新版工作台是 .session-item 列表，不再用表格）。
 */
async function findSessionItem(page: Page, sessionId: number, timeout = 20_000) {
  const item = page.locator('.session-item', { hasText: `会话 #${sessionId}` }).first();
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await item.isVisible().catch(() => false)) return item;
    await page.getByRole('button', { name: '刷新列表' }).click().catch(() => {});
    await page.waitForTimeout(400);
  }
  throw new Error(`客服工作台未出现会话 ${sessionId}（等待 ${timeout}ms）`);
}

// ---------------------------------------------------------------------------
// 主用例
// ---------------------------------------------------------------------------
test('全链路多角色终极验收：用户下单 → 客服实时会话 → 定制审图 → 仓管发货 → 财务核销', async ({ browser, request }) => {
  // 8 分钟不够：本机整条链路（下单→客服→定制→发货→核销）实测约 8~9 分钟，
  // 2026-11-13 定位到是最后一步「核销确认框」被总超时截断，非业务失败，故放宽到 12 分钟。
  test.setTimeout(720_000);

  const stamp = Date.now();

  const userCtx: BrowserContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const serviceCtx: BrowserContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const warehouseCtx: BrowserContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const financeCtx: BrowserContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });

  const userPage = await userCtx.newPage();
  const servicePage = await serviceCtx.newPage();
  const warehousePage = await warehouseCtx.newPage();
  const financePage = await financeCtx.newPage();

  let userToken = '';
  let adminToken = '';
  let serviceToken = '';
  let financeToken = '';
  let orderNo = '';
  let txnNo = '';
  let amountCent = 0;
  // 渠道受理回执（P0 起回调必须验签）：后端发起支付时下发，兜底回调原样回放
  let notifyParams: Record<string, string> | null = null;
  let notifySign = '';
  let sessionId = 0;
  let customNo = '';
  const beforeCustomIds = new Set<string>();
  /** 本次实际选中的规格（现场按可售库存挑，不再写死 9102；发货工作台断言要用它） */
  let pickedSkuId = String(CFG.customSkuId);

  try {
    // =====================================================================
    // 0 · 前置自检：四角色 API 登录（失败时直接给出清晰原因）
    // =====================================================================
    await test.step('前置自检：五个角色均可登录', async () => {
      const [u, a, s, w, f] = await Promise.all([
        apiLogin(request, CFG.user.phone, CFG.user.password, 'user'),
        apiLogin(request, CFG.admin.username, CFG.admin.password, 'admin'),
        apiLogin(request, CFG.service.username, CFG.service.password, 'admin'),
        apiLogin(request, CFG.warehouse.username, CFG.warehouse.password, 'admin'),
        apiLogin(request, CFG.finance.username, CFG.finance.password, 'admin'),
      ]);
      expect(u.code, `用户端登录：${JSON.stringify(u.body)}`).toBe(0);
      expect(a.code, `超管登录：${JSON.stringify(a.body)}`).toBe(0);
      expect(s.code, `客服登录（V13 种子）：${JSON.stringify(s.body)}`).toBe(0);
      expect(w.code, `仓管登录（V14 种子）：${JSON.stringify(w.body)}`).toBe(0);
      expect(f.code, `财务登录（V14 种子）：${JSON.stringify(f.body)}`).toBe(0);
      adminToken = String(a.token ?? '');
      expect(adminToken, '超管令牌用于清理历史未结束会话').toBeTruthy();
    });

    // =====================================================================
    // 1 · 用户 Context：登录 → 下单（含 UV 定制件）→ 支付 → 待发货
    // =====================================================================
    await test.step('用户：手机号登录', async () => {
      await loginUser(userPage);
      userToken = await accessTokenOf(userPage);
      expect(userToken.split('.').length, '登录后应持有 3 段 JWT').toBe(3);
    });

    await test.step('用户：记录下单前的定制单集合（用于识别本次新建定制单）', async () => {
      const body = await apiGet(request, '/custom-orders/my?page=1&size=100', userToken);
      expect(body?.code, `我的定制单读取失败：${JSON.stringify(body)}`).toBe(0);
      for (const row of body?.data?.list ?? []) beforeCustomIds.add(String(row.customOrderId));
    });

    await test.step('用户：商品详情选择 UV 定制规格并加购', async () => {
      // 定制 30 件起订：先找可售 ≥30 的规格，避免演示库存被买空后假红
      const picked = await pickCustomSku(request, userToken, 30);
      expect(picked, '演示库里应有可售 ≥30 的规格（定制 30 件起订）').toBeTruthy();
      pickedSkuId = picked!.skuId;
      await userPage.goto(join(USER_URL, `/product/${picked!.productId}`));
      await userPage.locator('.spec-item').filter({ hasText: picked!.spec }).first().click();
      await userPage.getByRole('button', { name: '加入购物车' }).click();
      await expect(userPage.getByText('已加入购物车'), '加入购物车应成功').toBeVisible({ timeout: 15_000 });
    });

    await test.step('用户：立即购买 → 结算勾选「含 UV 定制件」→ 提交订单并模拟支付', async () => {
      await userPage.getByRole('button', { name: '立即购买' }).click();
      await expect(userPage).toHaveURL(/\/checkout/, { timeout: 15_000 });

      const uvCheckbox = userPage.locator('.el-checkbox').filter({ hasText: /含 UV 定制件/ }).first();
      await expect(uvCheckbox, '结算页应有「含 UV 定制件」选项').toBeVisible();
      await uvCheckbox.click();
      await expect(uvCheckbox).toHaveClass(/is-checked/);

      const payResp = userPage.waitForResponse(
        (r) => r.url().includes('/api/payments') && r.request().method() === 'POST',
        { timeout: 40_000 },
      );
      await userPage.getByRole('button', { name: '提交订单并支付' }).click();

      const paidDialog = userPage.locator('.el-dialog').filter({ hasText: '支付成功' });
      await expect(paidDialog, '下单/支付失败：未出现「支付成功」弹窗').toBeVisible({ timeout: 40_000 });
      const text = await paidDialog.innerText();
      const matched = /订单号[：:]\s*([A-Za-z0-9_-]+)/.exec(text);
      expect(matched, `未能从支付弹窗解析订单号：${text}`).not.toBeNull();
      orderNo = matched![1];

      const payBody: any = await (await payResp).json().catch(() => null);
      txnNo = payBody?.data?.txnNo ?? '';
      amountCent = Number(payBody?.data?.amountCent ?? 0);
      notifyParams = payBody?.data?.notifyParams ?? null;
      notifySign = payBody?.data?.sign ?? '';
      expect(txnNo, '发起支付必须返回 txnNo').toBeTruthy();
    });

    await test.step('用户：断言订单流转为「待发货」', async () => {
      const deadline = Date.now() + 15_000;
      let status = '';
      while (Date.now() < deadline) {
        status = await orderStatus(request, orderNo);
        if (status === 'WAIT_SHIP') break;
        await new Promise((r) => setTimeout(r, 400));
      }
      // 兜底：前端 1.2s 自动回调未触发时，按契约手动补一次（后端条件更新天然幂等）
      if (status !== 'WAIT_SHIP' && txnNo) {
        const cb = await apiPost(request, '/payment/mock/callback', {
          ...(notifyParams ?? { txnNo, orderNo, amountCent, channel: 'BALANCE' }),
          sign: notifySign,
        });
        expect(cb?.code, `兜底支付回调失败：${JSON.stringify(cb)}`).toBe(0);
        status = await orderStatus(request, orderNo);
      }
      expect(status, '支付后订单应为 WAIT_SHIP').toBe('WAIT_SHIP');

      await userPage.goto(join(USER_URL, `/order/${orderNo}`));
      await expect(userPage.getByText('待发货').first()).toBeVisible({ timeout: 15_000 });
    });

    // =====================================================================
    // 2 · 用户打开客服会话（建立 WAITING 会话 + 连接 WS）
    // =====================================================================
    await test.step('用户：进入客服页并发出首条消息（首条消息才建立会话）', async () => {
      // 老口径下进页面就会 POST 建会话；现在只查 current。先清掉历史未结束会话，
      // 保证 current 拿到的就是本次咨询，而不是上一次 E2E 留下的 ACTIVE/WAITING。
      await clearOpenSessions(request, adminToken);

      // 2026-11-25 起顶栏「在线客服」是新标签页打开：本用例后续步骤都要在这张聊天页上继续，
      // 因此直接按 URL 进入（新开 Tab 的行为由 new-tab-navigation.spec.ts 覆盖）。
      await userPage.goto(join(USER_URL, '/chat'));
      // 进入页面只查不建：这里必须真的发一句话，客服工作台才看得到该会话
      const box = userPage.getByPlaceholder('请输入想咨询的问题');
      await expect(box).toBeEnabled({ timeout: 20_000 });
      await box.fill(MSG_USER);
      await box.press('Enter');

      // 会话由首条消息触发创建，不能再等 POST /chat/sessions；改从 current 轮询读取。
      const deadline = Date.now() + 20_000;
      while (Date.now() < deadline) {
        const body = await apiGet(request, `/chat/sessions/current?customerId=${CFG.customerId}`, userToken);
        sessionId = Number(body?.data?.sessionId ?? 0);
        if (sessionId > 0) break;
        await new Promise((r) => setTimeout(r, 300));
      }
      expect(sessionId, '发出首条客户消息后，current 应能查到本次会话').toBeGreaterThan(0);
      // 「在线」= WS 已连接（useChatSession.start 查询当前会话后 connect）
      await expect(userPage.locator('.chat-state--on'), '用户端 WS 应处于在线状态').toBeVisible({ timeout: 20_000 });
    });

    // =====================================================================
    // 3 · 客服 Context：登录 → 客服工作台 → 接待该会话
    // =====================================================================
    await test.step('客服：独立 Context 登录并进入客服工作台（WS 已连接）', async () => {
      // 客服台已是独立工程（web-cs :5175）：登录后直落 /workbench，没有 /dashboard 中间站
      await loginCs(servicePage, CFG.service.username, CFG.service.password);
      serviceToken = await accessTokenOf(servicePage);
      await servicePage.goto(join(CS_URL, '/workbench'));
      await expect(servicePage.getByRole('heading', { name: '客服工作台' })).toBeVisible();
      await expect(
        servicePage.locator('.el-tag').filter({ hasText: 'WebSocket 已连接' }),
        '客服工作台 WS 必须已连接（否则无法实时收消息）',
      ).toBeVisible({ timeout: 20_000 });
    });

    await test.step('客服：打开该会话 Tab 并接待（分配为 ACTIVE）', async () => {
      await servicePage.getByRole('button', { name: '刷新列表' }).click();
      const item = await findSessionItem(servicePage, sessionId);
      // 点左侧会话行即在工作台右侧打开一个 Tab（多 Tab 上限 3）
      await item.click();
      await expect(
        servicePage.locator('.message-box'),
        '打开会话 Tab 后应出现消息区',
      ).toBeVisible({ timeout: 15_000 });

      // 复用场景兜底：同一客户会话未结束时后端会复用原会话（CreateSessionUseCase.findOpen），
      // 可能已经是我方接待（ACTIVE/9003），此时再点「接待」会被状态机拒绝（仅 WAITING 可分配），
      // 因此按当前状态决定是否执行分配。
      const sessionState = async () => {
        const body = await apiGet(request, '/admin/chat/sessions?page=1&size=50', serviceToken);
        const target = (body?.data?.list ?? []).find((x: any) => Number(x.sessionId) === sessionId);
        return `${target?.status ?? ''}|${target?.staffId ?? ''}`;
      };
      if ((await sessionState()) !== `ACTIVE|${CFG.serviceStaffId}`) {
        // Tab 内的「接待」按钮正式把会话分配给 9003（WAITING → ACTIVE）
        await servicePage.getByRole('button', { name: '接待' }).first().click();
      }

      await expect.poll(sessionState, {
        timeout: 20_000,
        message: '会话应被分配为 ACTIVE 且接待客服为 9003',
      }).toBe(`ACTIVE|${CFG.serviceStaffId}`);
    });

    // =====================================================================
    // 4 · WebSocket 实时互动（3000ms 内、不刷新）
    // =====================================================================
    await test.step('实时：用户发消息，客服 3s 内收到（不刷新）', async () => {
      // 断言「新增了一条」而不是「存在该文案」：同一客户会话未结束时会被后端复用，
      // 历史里可能已有上一轮的同一条文案，用存在性判断会假绿，测不出实时推送。
      const bubble = servicePage.locator('.message-box .bubble').filter({ hasText: MSG_USER });
      const before = await bubble.count();

      // 客服接待后后端推送 ASSIGNED，用户端排队提示消失、输入解锁（WAITING 时输入框是禁用的）
      await expect(userPage.locator('.queue-banner'), '客服接待后排队提示应消失').toBeHidden({ timeout: 15_000 });
      await expect(userPage.getByPlaceholder('请输入想咨询的问题')).toBeEnabled({ timeout: 15_000 });
      await userPage.getByPlaceholder('请输入想咨询的问题').fill(MSG_USER);
      await userPage.getByPlaceholder('请输入想咨询的问题').press('Enter');

      await expect
        .poll(() => bubble.count(), {
          timeout: 3_000,
          message: '客服端应在 3s 内实时收到用户消息（无刷新，气泡数量应 +1）',
        })
        .toBeGreaterThan(before);
      await expect(bubble.first(), '客服端消息区应显示用户原话').toContainText(MSG_USER);
      // 把消息区滚进视口，截图佐证「实时收到消息」。
      await servicePage.locator('.message-box').scrollIntoViewIfNeeded();
      await servicePage.screenshot({ path: 'screenshots/chat-success.png' });
    });

    await test.step('实时：客服回复，用户 3s 内收到（不刷新）', async () => {
      const bubble = userPage.locator('.chat-msg--other .chat-bubble').filter({ hasText: MSG_STAFF });
      const before = await bubble.count();

      await servicePage.getByPlaceholder('输入消息后回车发送').fill(MSG_STAFF);
      await servicePage.getByPlaceholder('输入消息后回车发送').press('Enter');

      await expect
        .poll(() => bubble.count(), {
          timeout: 3_000,
          message: '用户端应在 3s 内实时收到客服回复（无刷新，气泡数量应 +1）',
        })
        .toBeGreaterThan(before);
      await expect(bubble.first(), '用户端消息区应显示客服回复原文').toContainText(MSG_STAFF);
    });

    // =====================================================================
    // 5 · 定制图稿：V1 → 驳回 → V2 → 通过
    // =====================================================================
    await test.step('后端：下单已生成 UV 定制单（WAIT_DRAFT）', async () => {
      await expect.poll(async () => {
        const body = await apiGet(request, '/custom-orders/my?page=1&size=100', userToken);
        const fresh = (body?.data?.list ?? []).find(
          (r: any) => !beforeCustomIds.has(String(r.customOrderId)) && r.status === 'WAIT_DRAFT',
        );
        customNo = fresh?.customOrderId ?? '';
        return customNo;
      }, { timeout: 25_000, message: 'UV 定制订单应生成 custom_order(WAIT_DRAFT)' }).toBeTruthy();
    });

    /**
     * 图稿上传倒计时（2026-11-23 用户口径）：支付后 72 小时未上传 → 系统自动取消订单并全额退款。
     * 详情页的倒计时是用户唯一能提前感知这条规则的入口，所以放在「刚支付完」这一刻验：
     * 服务端给的是「支付时间 + 72 小时」，页面必须把它读出来并显示成「还剩 N 天」。
     */
    await test.step('定制：详情页显示图稿上传倒计时（支付 + 72 小时）', async () => {
      const body = await apiGet(request, `/customize/orders/${customNo}`, userToken);
      const remainingHours = (Number(body?.data?.draftDeadlineMillis ?? 0) - Date.now()) / 3_600_000;
      expect(remainingHours, '刚支付完，截止时刻应还剩约 72 小时').toBeGreaterThan(70);
      expect(remainingHours, '倒计时不能超过 72 小时上限').toBeLessThanOrEqual(72);

      await userPage.goto(join(USER_URL, `/custom/${customNo}`));
      const countdown = userPage.locator('[data-testid="draft-countdown"]');
      await expect(countdown, '待上传图稿的定制单详情页必须有倒计时').toBeVisible({ timeout: 20_000 });
      await expect(countdown).toContainText('距上传设计图稿截止');
      await expect(countdown).toContainText('天');
    });

    /**
     * 用户端：按定制单号查询 → 选一张真实图片上传。
     * 旧实现点一下就自动编一个 f_<ts> 假 fileId，管理端审图台根本拿不到图；
     * 现在按钮会拉起系统文件选择器，必须用 filechooser 把真实文件送进去。
     */
    const uploadDraft = async (): Promise<void> => {
      await userPage.goto(join(USER_URL, '/custom'));
      await userPage.getByPlaceholder('输入定制单号直接查询').fill(customNo);
      await userPage.getByRole('button', { name: '查询', exact: true }).click();
      const btn = userPage.getByRole('button', { name: '上传图稿' });
      await expect(btn, '定制单详情应展示「上传图稿」').toBeVisible({ timeout: 15_000 });
      const [chooser] = await Promise.all([
        userPage.waitForEvent('filechooser', { timeout: 15_000 }),
        btn.click(),
      ]);
      await chooser.setFiles(DRAFT_IMAGE);
    };

    const openReview = async (): Promise<void> => {
      await servicePage.goto(join(ADMIN_URL, `/custom/review?id=${customNo}`));
      await expect(servicePage.getByRole('heading', { name: '定制审核' })).toBeVisible();
      await expect(servicePage.getByText('锁定版本', { exact: true })).toBeVisible({ timeout: 15_000 });
    };

    await test.step('定制：用户上传图稿 V1', async () => {
      await uploadDraft();
      await expect.poll(async () => {
        const body = await apiGet(request, `/customize/orders/${customNo}`, userToken);
        return `${body?.data?.status ?? ''}|${body?.data?.version ?? 0}`;
      }, { timeout: 25_000 }).toBe('WAIT_REVIEW|1');
    });

    await test.step('定制：客服驳回 V1（填写原因）', async () => {
      // 关键：客服台（5175）与管理端（5174）是两个不同 origin，localStorage 不互通。
      // cs-token_* 存在 5175 源下，进 5174 读不到 → 不补这一步会直接掉到管理端登录页。
      // 同一账号(service)再登一次管理端即可，两套令牌互不覆盖（前缀不同：cs-token_* / sent_admin_*）。
      await loginAdmin(servicePage, CFG.service.username, CFG.service.password, '客服');
      await openReview();
      const reject = servicePage.getByRole('button', { name: '驳回重做' });
      await expect(reject, '待审核图稿应可驳回').toBeEnabled({ timeout: 15_000 });
      await servicePage.getByPlaceholder('给客户/生产端的说明').fill('图稿分辨率不足，请重传 UV 稿');
      await reject.click();
      await expect.poll(async () => {
        const body = await apiGet(request, `/admin/custom-orders/${customNo}`, serviceToken);
        return `${body?.data?.status ?? ''}|${body?.data?.reviewLogCount ?? 0}`;
      }, { timeout: 25_000 }).toBe('REJECTED|1');
    });

    await test.step('定制：用户重新上传 V2', async () => {
      await uploadDraft();
      await expect.poll(async () => {
        const body = await apiGet(request, `/customize/orders/${customNo}`, userToken);
        return `${body?.data?.status ?? ''}|${body?.data?.version ?? 0}`;
      }, { timeout: 25_000 }).toBe('WAIT_REVIEW|2');
    });

    await test.step('定制：客服审核通过 V2', async () => {
      await openReview();
      const pass = servicePage.getByRole('button', { name: '审核通过' });
      await expect(pass, '重传后应回到 WAIT_REVIEW 可审核').toBeEnabled({ timeout: 15_000 });
      await pass.click();
      await expect.poll(async () => {
        const body = await apiGet(request, `/admin/custom-orders/${customNo}`, serviceToken);
        return `${body?.data?.status ?? ''}|${body?.data?.lockedVersion ?? 0}`;
      }, { timeout: 25_000 }).toBe('APPROVED|2');
    });

    // 定制件发货门禁：必须是 FINISHED（已完工）。审核通过只是「可生产」，
    // 还差「转生产 → 标记完工」两步，否则仓管发货会被 40901 状态冲突拒绝。
    await test.step('定制：转生产 → 标记完工（发货前置门禁）', async () => {
      await openReview();
      const toProduce = servicePage.getByRole('button', { name: '转生产' });
      await expect(toProduce, '审核通过后应可转生产').toBeEnabled({ timeout: 15_000 });
      await toProduce.click();
      await expect.poll(async () => {
        const body = await apiGet(request, `/admin/custom-orders/${customNo}`, serviceToken);
        return body?.data?.status ?? '';
      }, { timeout: 25_000 }).toBe('IN_PRODUCTION');

      const finish = servicePage.getByRole('button', { name: '标记完工' });
      await expect(finish, '转生产后应可标记完工').toBeEnabled({ timeout: 15_000 });
      await finish.click();
      await expect.poll(async () => {
        const body = await apiGet(request, `/admin/custom-orders/${customNo}`, serviceToken);
        return body?.data?.status ?? '';
      }, { timeout: 25_000 }).toBe('FINISHED');
    });

    // =====================================================================
    // 6 · 仓管 Context：找到订单 → 发货
    // =====================================================================
    await test.step('仓管：独立 Context 登录并发货', async () => {
      await loginAdmin(warehousePage, CFG.warehouse.username, CFG.warehouse.password, '仓管');
      await warehousePage.goto(join(ADMIN_URL, '/order/shipment'));
      await expect(warehousePage.getByRole('heading', { name: '发货工作台' })).toBeVisible();

      const row = warehousePage.locator('.el-table__row').filter({ hasText: orderNo }).first();
      await expect(row, `待发货列表应包含订单 ${orderNo}`).toBeVisible({ timeout: 25_000 });
      await row.getByRole('button', { name: '选择发货' }).click();

      await warehousePage.getByPlaceholder('如：顺丰速运').fill('E2E 顺丰速运');
      await warehousePage.getByPlaceholder('快递单号').fill(`E2E-TRACK-${stamp}`);

      // 发货行现在由「订单详情」自动带出真实 SKU / 可发数量 / 定制状态，
      // 且该单的定制件已在上一步标记完工，默认即为勾选可发；不再手填 skuId=1 的占位数字框。
      const line = warehousePage.locator('.ship-line').first();
      await expect(line, '选中订单后应自动带出真实商品行').toBeVisible({ timeout: 20_000 });
      await expect(line).toContainText(pickedSkuId);
      await expect(line.locator('.el-checkbox'), '已完工定制行应默认勾选')
        .toHaveClass(/is-checked/, { timeout: 15_000 });

      // 证据：发货行由订单详情自动带出（SKU / 可发数量 / 定制状态），不再是手填 skuId=1
      await warehousePage.screenshot({ path: 'screenshots/shipment-lines.png' });

      await warehousePage.getByRole('button', { name: '确认发货' }).click();
      await expect(warehousePage.getByText('发货成功', { exact: false })).toBeVisible({ timeout: 25_000 });
      // 管理端是「外壳固定 + 内部容器滚动」布局（fullPage 截图等于视口），
      // 上一步点「确认发货」把内部容器滚下去了，顶部 toast 会被裁掉；截图前统一回到顶部。
      await scrollToTop(warehousePage);
      await warehousePage.screenshot({ path: 'screenshots/shipment-success.png' });
    });

    // =====================================================================
    // 7 · 用户确认收货 → 订单已完成（触发应收台账落账）
    // =====================================================================
    await test.step('用户：确认收货断言「已完成」', async () => {
      await userPage.goto(join(USER_URL, '/orders'));
      const card = userPage.locator('.order-card').filter({ hasText: orderNo }).first();
      await expect(card, '我的订单列表应包含该订单').toBeVisible({ timeout: 20_000 });
      const confirmBtn = card.getByRole('button', { name: '确认收货' });
      await expect(confirmBtn, '发货后订单应处于待收货，显示「确认收货」').toBeVisible({ timeout: 20_000 });
      await confirmBtn.click();
      await expect(card.getByText('已完成')).toBeVisible({ timeout: 20_000 });
      expect(await orderStatus(request, orderNo), '确认收货后订单应为 COMPLETED').toBe('COMPLETED');
    });

    // =====================================================================
    // 8 · 财务 Context：登记回款 → 核销该订单应收
    // =====================================================================
    await test.step('财务：独立 Context 登录并读取该订单应收', async () => {
      await loginAdmin(financePage, CFG.finance.username, CFG.finance.password, '财务');
      financeToken = await accessTokenOf(financePage);

      let receivable: any = null;
      await expect.poll(async () => {
        const body = await apiGet(
          request,
          `/admin/receivables?page=1&size=100&customerId=${CFG.customerId}`,
          financeToken,
        );
        receivable = (body?.data?.list ?? []).find((r: any) => r.orderNo === orderNo) ?? null;
        return receivable ? 'FOUND' : '';
      }, { timeout: 25_000, message: '确认收货后应为该订单生成应收台账' }).toBe('FOUND');

      expect(receivable.status, '新生成应收应为 NORMAL').toBe('NORMAL');
      expect(Number(receivable.amountCent), '应收金额应大于 0').toBeGreaterThan(0);

      await financePage.goto(
        join(ADMIN_URL, `/finance/payment-record?customerId=${CFG.customerId}&receivableId=${receivable.receivableId}`),
      );
      await expect(financePage.getByRole('heading', { name: '回款登记与核销' })).toBeVisible();
    });

    await test.step('财务：登记一笔回款（金额 = 应收未核销）', async () => {
      const body = await apiGet(
        request,
        `/admin/receivables?page=1&size=100&customerId=${CFG.customerId}`,
        financeToken,
      );
      const target = (body?.data?.list ?? []).find((r: any) => r.orderNo === orderNo);
      const outstanding = Number(target?.amountCent ?? 0) - Number(target?.writtenOffCent ?? 0);
      expect(outstanding, '应收未核销金额应大于 0').toBeGreaterThan(0);

      await financePage.getByPlaceholder('如 1200.00').fill(yuan(outstanding));
      await financePage.getByPlaceholder('必填').fill(`E2E-BANK-${stamp}`);
      await financePage.getByRole('button', { name: '登记回款' }).click();
      await expect(financePage.getByText(/回款登记成功/)).toBeVisible({ timeout: 25_000 });
    });

    await test.step('财务：勾选应收并确认核销（输入「确认核销」）', async () => {
      const row = financePage.locator('.el-table__row').filter({ hasText: orderNo }).first();
      await expect(row, '核销表应加载出该订单应收').toBeVisible({ timeout: 25_000 });

      // 列序：选择框 / 应收 ID / 订单号 / 未核销 / 状态 / 本次核销(元)。
      // 用 placeholder 定位「本次核销(元)」输入框，不要按 td 下标 —— nth(4) 落到「状态」单元格，
      // 里面没有 input，fill 会一直空等到用例超时（日志表现就是 locator.fill: Test ended）。
      const checkbox = row.locator('.el-checkbox').first();
      const amountInput = row.locator('input[placeholder="0.00"]');

      // 未核销金额先在勾选之前取好，避免中途多一次 API 往返把勾选状态拖进重算窗口
      const body = await apiGet(
        request,
        `/admin/receivables?page=1&size=100&customerId=${CFG.customerId}`,
        financeToken,
      );
      const target = (body?.data?.list ?? []).find((r: any) => r.orderNo === orderNo);
      const outstanding = Number(target?.amountCent ?? 0) - Number(target?.writtenOffCent ?? 0);

      // 表格会按可用高度自适应重算条数并重新拉数据，重载会清空勾选：
      // 勾选 + 填金额都放进轮询，提交前确认这一行确实还处于勾选态，否则点「确认核销」
      // 只会弹「请先勾选需要核销的应收单」，等不到危险操作确认框。
      await expect.poll(async () => {
        if (!(await checkbox.evaluate((el) => el.classList.contains('is-checked')))) {
          await checkbox.click({ timeout: 5_000 }).catch(() => undefined);
        }
        await amountInput.fill(yuan(outstanding)).catch(() => undefined);
        return checkbox.evaluate((el) => el.classList.contains('is-checked'));
      }, { timeout: 20_000, message: '应收行应保持勾选' }).toBe(true);

      await financePage.getByRole('button', { name: '确认核销' }).click();

      const box = financePage.locator('.el-message-box');
      await expect(box, '核销应弹出危险操作确认框').toBeVisible({ timeout: 15_000 });
      await box.locator('input').fill('确认核销');
      await box.getByRole('button', { name: '确认继续' }).click();
      await expect(financePage.getByText(/核销完成/)).toBeVisible({ timeout: 25_000 });
      // 等确认框彻底关闭（含淡出动画），否则截图里会残留半透明的「危险操作确认」蒙层。
      await expect(box).toBeHidden({ timeout: 5_000 });
      await financePage.screenshot({ path: 'screenshots/finance-success.png' });
    });

    await test.step('财务：断言应收台账结清 + 订单已完成', async () => {
      await expect.poll(async () => {
        const body = await apiGet(
          request,
          `/admin/receivables?page=1&size=100&customerId=${CFG.customerId}`,
          financeToken,
        );
        const target = (body?.data?.list ?? []).find((r: any) => r.orderNo === orderNo);
        if (!target) return '';
        const outstanding = Number(target.amountCent ?? 0) - Number(target.writtenOffCent ?? 0);
        return `${target.status}|${outstanding}`;
      }, { timeout: 25_000, message: '核销后应收应为 CLOSED 且未核销金额为 0' }).toBe('CLOSED|0');

      expect(await orderStatus(request, orderNo), '终态订单应为 COMPLETED').toBe('COMPLETED');
    });
  } finally {
    await Promise.allSettled([userCtx.close(), serviceCtx.close(), warehouseCtx.close(), financeCtx.close()]);
  }
});

/**
 * 本地运行方式
 * -----------------------------------------------------------------------------
 * 1) 依赖服务（本机 MySQL 8.0.43 / sent_dev，root/123456）：
 *      java -jar server/target/sent-server.jar            # 后端 :8088（dev profile）
 *      pnpm --dir web-user dev                            # 商城 :5173
 *      pnpm --dir web-admin dev                           # 后台 :5174
 *      pnpm --dir web-cs dev                              # 客服台 :5175（接待段跑这里）
 *    （或设置 E2E_AUTOSTART=1 让 Playwright 自动拉起四端）
 *
 * 2) 执行本脚本（复用系统 Chrome，无需下载 Chromium）：
 *      cd e2e && npx playwright test full-flow.spec.ts --headed
 *    只跑本用例并输出步骤日志：
 *      cd e2e && npx playwright test full-flow.spec.ts --reporter=list
 *    若所在环境对「批量删除」有限流（Playwright 默认会清空 test-results/），
 *    改用独立输出目录即可绕开：
 *      cd e2e && npx playwright test full-flow.spec.ts --output "runs/$(date +%H%M%S)"
 *
 * 3) 关键环境变量（均可选）：
 *      E2E_USER_NAME=13800138000  E2E_USER_PASSWORD=123456
 *      E2E_SERVICE_NAME=service   E2E_WAREHOUSE_NAME=warehouse  E2E_FINANCE_NAME=finance
 *      E2E_PRODUCT_ID=1001  E2E_CUSTOM_SKU_ID=9102  E2E_CUSTOMER_ID=9002
 *      E2E_USER_URL / E2E_ADMIN_URL / E2E_CS_URL / E2E_API_URL / E2E_CHANNEL=msedge
 *
 * 4) 前置条件（Flyway 到 V19）：
 *      - V15：客户 13800138000 存在且可登录
 *      - V16：客服角色具备 chat:serve / chat:assign
 *      - V17 + V18：sys_sequence 业务单号序列（否则订单号会与演示数据撞号，
 *        表现为「下单 200 但支付 400 参数校验失败」）
 *      - V19：custom:produce（定制「转生产 / 标记完工」，是定制商品发货的前置门禁）
 *
 * 5) 幂等性：脚本可反复运行。同一客户会话未结束会被后端复用（CreateSessionUseCase.findOpen），
 *    因此接待步骤按当前状态决定是否分配；实时消息用「气泡数量 +1」断言，
 *    不会被上一轮留下的同文案历史消息误判为通过。
 *
 * 6) 截图产物：
 *      e2e/screenshots/chat-success.png
 *      e2e/screenshots/shipment-success.png
 *      e2e/screenshots/finance-success.png
 */

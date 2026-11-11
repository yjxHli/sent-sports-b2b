/**
 * 森特体育用品管理系统 · 核心主链路 E2E（验收阶段任务 2）
 * =============================================================================
 * 真实浏览器操作，覆盖：
 *  00 前置自检：登录 JWT / 商品目录 / 受保护端点鉴权
 *  01 核心链路：用户端登录 → 浏览商品 → 立即购买 → 结算页提交订单并点击支付 → 支付网关回调
 *     → 管理端「新开独立 Context」登录仓管 → 发货工作台找到该订单并点击发货
 *     → 回到用户端订单列表点击「确认收货」→ 断言订单状态变为「已完成」。
 *  02 定制图稿链路（Phase 10）：UV 定制件下单 → 上传图稿 V1 → 客服账号驳回 → 重传 V2
 *     → 客服审核通过 → 断言 custom_draft / custom_review_log 真库落账。
 *
 * 设计要点
 *  - 双端 token 物理隔离（VITE_TOKEN_STORAGE_PREFIX = sent_user / sent_admin），
 *    因此使用两个独立 BrowserContext，不共享 storageState。
 *  - 管理端 base 为 /admin/，路由为 h5 history，URL 形如 /admin/order/shipment。
 *  - 前端结算页只调用 POST /api/payments 发起支付，不会主动触发渠道回调；
 *    因此脚本用 APIRequestContext 按 P4 契约补一步 POST /api/payment/mock/callback，
 *    使订单真正流转到 WAIT_SHIP，管理端发货工作台才查得到该单。
 *  - 所有账号 / SKU / 地址均可用环境变量覆盖，便于在本地、CI、Testcontainers 环境复用。
 *
 * 环境变量
 *  E2E_USER_NAME / E2E_USER_PASSWORD      用户端手机号账号（默认 13800138000 / 123456，V11 + V15 种子）
 *  E2E_ADMIN_NAME / E2E_ADMIN_PASSWORD    管理端仓管账号（默认 admin / 123456，V11 种子）
 *  E2E_PRODUCT_ID                         商品 ID（默认 1001）
 *  E2E_SKU_ID / E2E_QTY                   发货行 SKU 与数量（默认 9101 / 1）
 *  E2E_USER_URL / E2E_ADMIN_URL / E2E_API_URL
 */

import { test, expect, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';

const CFG = {
  user: {
    username: process.env.E2E_USER_NAME ?? '13800138000',
    password: process.env.E2E_USER_PASSWORD ?? '123456',
  },
  admin: {
    username: process.env.E2E_ADMIN_NAME ?? 'admin',
    password: process.env.E2E_ADMIN_PASSWORD ?? '123456',
  },
  service: {
    username: process.env.E2E_SERVICE_NAME ?? 'service',
    password: process.env.E2E_SERVICE_PASSWORD ?? '123456',
  },
  productId: process.env.E2E_PRODUCT_ID ?? '1001',
  skuId: Number(process.env.E2E_SKU_ID ?? 9101),
  qty: Number(process.env.E2E_QTY ?? 1),
  /** UV 定制件 SKU（V11 种子 9102，V13 开放该 SPU 的定制标记）。 */
  customSkuId: Number(process.env.E2E_CUSTOM_SKU_ID ?? 9102),
};

const join = (base: string, path: string): string =>
  `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;

/** 向后端登录接口取证，用于自检与失败诊断（不参与浏览器登录态） */
async function apiLogin(
  request: APIRequestContext,
  username: string,
  password: string,
  aud: 'user' | 'admin',
): Promise<{ status: number; code: number | null; token: string | null; body: unknown }> {
  const response = await request.post(join(API_URL, '/auth/login'), {
    data: { username, password, aud, deviceId: `e2e-${aud}` },
    failOnStatusCode: false,
  });
  let body: any = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  return {
    status: response.status(),
    code: body?.code ?? null,
    token: body?.data?.accessToken ?? null,
    body,
  };
}

/** 读取页面 localStorage 里的 access token（双端前缀隔离，只取当前页面自己的）。 */
async function accessTokenOf(page: Page): Promise<string> {
  return page.evaluate(
    () => Object.entries(localStorage).find(([k]) => k.includes('access_token'))?.[1] ?? '',
  );
}

/** 带 Bearer 令牌读取后端 DTO，失败时返回业务体供断言诊断。 */
async function apiGet(request: APIRequestContext, path: string, token: string): Promise<any> {
  const res = await request.get(join(API_URL, path), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

/**
 * 直连本机 MySQL 做「表级」落账断言（API 计数之外再核一次原始行）。
 * 本机无 mysql CLI 时返回 null，由调用方降级为只断言 API 计数，不阻断 E2E。
 */
function dbScalar(sql: string): string | null {
  try {
    return execFileSync(
      process.env.E2E_MYSQL_BIN ?? 'mysql',
      ['-h', process.env.E2E_DB_HOST ?? '127.0.0.1', '-P', process.env.E2E_DB_PORT ?? '3306',
        '-u', process.env.E2E_DB_USER ?? 'root', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
        '-N', '-B', '-e', sql, process.env.E2E_DB_NAME ?? 'sent_dev'],
      { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] },
    ).trim();
  } catch (error) {
    console.warn(`[db-check] 跳过表级 SQL 断言：${(error as Error).message}`);
    return null;
  }
}

// 用例彼此独立：即使环境自检失败，主链路仍会真实打开浏览器执行，
// 以便在报告里同时暴露「API 层缺口」与「浏览器层表现」。

// ---------------------------------------------------------------------------
// 00 · 前置自检：一次跑完把所有「环境级阻断」列在报告里，避免主链路超时后才看到模糊错误
// ---------------------------------------------------------------------------
test('00 前置自检：后端可达 / 登录返回 JWT / 目录有商品', async ({ request }) => {
  const userLogin = await apiLogin(request, CFG.user.username, CFG.user.password, 'user');
  const adminLogin = await apiLogin(request, CFG.admin.username, CFG.admin.password, 'admin');

  expect
    .soft(userLogin.status, '用户端登录接口 HTTP 状态（非 200 通常表示服务未启动或参数校验失败）')
    .toBe(200);
  expect
    .soft(userLogin.code, `用户端登录业务码：${JSON.stringify(userLogin.body)}`)
    .toBe(0);
  expect
    .soft(
      userLogin.token?.split('.').length ?? 0,
      '登录返回的 accessToken 必须是 3 段 JWT（LoginUseCase 需经 TokenService.issueAccess 签发）',
    )
    .toBe(3);

  expect.soft(adminLogin.status, '管理端登录接口 HTTP 状态').toBe(200);
  expect
    .soft(
      adminLogin.token?.split('.').length ?? 0,
      '管理端 accessToken 同样必须是 JWT',
    )
    .toBe(3);

  // 目录接口：商品详情/列表为 P4 查询桩时返回空列表或 40401
  const catalog = await request.get(join(API_URL, '/products'), {
    params: { page: 1, size: 5 },
    headers: userLogin.token ? { Authorization: `Bearer ${userLogin.token}` } : {},
    failOnStatusCode: false,
  });
  let catalogBody: any = null;
  try {
    catalogBody = await catalog.json();
  } catch {
    catalogBody = null;
  }
  const catalogRows: unknown[] =
    catalogBody?.data?.list ?? catalogBody?.data?.records ?? catalogBody?.data ?? [];
  expect
    .soft(
      Array.isArray(catalogRows) ? catalogRows.length : -1,
      '商品目录必须返回真实 SPU（ProductQueryServiceImpl 需查 product 表，不能是空桩）',
    )
    .toBeGreaterThan(0);

  // 带令牌访问受保护端点：非 JWT 令牌会被 AuthFilter 的 TokenService.verify 拒绝（40101）
  if (userLogin.token) {
    const orders = await request.get(join(API_URL, '/orders'), {
      params: { page: 1, size: 1 },
      headers: { Authorization: `Bearer ${userLogin.token}` },
      failOnStatusCode: false,
    });
    let orderBody: any = null;
    try {
      orderBody = await orders.json();
    } catch {
      orderBody = null;
    }
    expect
      .soft(
        orderBody?.code ?? orders.status(),
        '[验收缺口] 登录令牌必须能通过 AuthFilter 校验（40101 = 非 JWT 被拒绝）',
      )
      .not.toBe(40101);
  }
});

// ---------------------------------------------------------------------------
// 01 · 核心链路：双 Context 真实浏览器操作
// ---------------------------------------------------------------------------
test('01 核心链路：用户登录下单支付 → 仓管发货 → 用户确认收货', async ({ browser, request }) => {
  const userCtx: BrowserContext = await browser.newContext({
    viewport: { width: 390, height: 844 },
  });
  const adminCtx: BrowserContext = await browser.newContext({
    viewport: { width: 1440, height: 900 },
  });
  const userPage: Page = await userCtx.newPage();
  const adminPage: Page = await adminCtx.newPage();
  let orderNo = '';

  try {
    // ------------------------------------------------------------------
    // 1) 用户端登录
    // ------------------------------------------------------------------
    await test.step('用户端：手机号密码登录', async () => {
      await userPage.goto(join(USER_URL, '/login'));
      await userPage.getByPlaceholder('请输入11位手机号').fill(CFG.user.username);
      await userPage.getByPlaceholder('请输入密码').fill(CFG.user.password);
      const loginResponse = userPage.waitForResponse(
        (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
        { timeout: 15_000 },
      );
      await userPage.getByRole('button', { name: '登录' }).click();
      const response = await loginResponse;
      const body: any = await response.json().catch(() => null);
      expect(
        body?.code,
        `[验收缺口] 用户端登录失败（HTTP ${response.status()}）：${JSON.stringify(body)}；` +
          'V11 种子账号或 AccountSeeder 未生效，需检查 app.seed-accounts.enabled',
      ).toBe(0);
      await expect(userPage).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 15_000 });
    });

    // ------------------------------------------------------------------
    // 1b) 登录鉴权闭环证据：本地令牌是 JWT → /api/me 用 sub 查到客户 → 个人中心渲染
    // ------------------------------------------------------------------
    await test.step('登录鉴权闭环：JWT sub → /api/me → 个人中心（截图）', async () => {
      const token = await userPage.evaluate(() =>
        Object.entries(localStorage).find(([k]) => k.includes('access_token'))?.[1] ?? null);
      expect(token, '登录后本地应持有 access token').toBeTruthy();
      expect(token?.split('.').length, 'access token 必须是 3 段 JWT').toBe(3);
      await userPage.goto(join(USER_URL, '/mine'));
      await expect(
        userPage.getByText('验收客户'),
        '个人中心昵称来自 GET /api/me，证明 AuthFilter 已把 JWT sub 正确注入供 Controller 使用',
      ).toBeVisible({ timeout: 15_000 });
      await userPage.screenshot({ path: 'screenshots/01-user-auth-me.png', fullPage: true });
    });

    // ------------------------------------------------------------------
    // 2) 浏览商品 → 立即购买 → 结算 → 点击支付
    // ------------------------------------------------------------------
    await test.step('用户端：选规格并立即购买', async () => {
      await userPage.goto(join(USER_URL, `/product/${CFG.productId}`));
      const buyNow = userPage.getByRole('button', { name: '立即购买' });
      await expect(
        buyNow,
        '[验收缺口] 商品详情接口为桩/NOT_FOUND 时页面显示「商品不存在」，无法进入结算',
      ).toBeVisible();
      await buyNow.click();
      await expect(userPage).toHaveURL(/\/checkout/);
      await expect(userPage.getByRole('button', { name: '提交订单并支付' })).toBeVisible();
    });

    let txnNo = '';
    let amountCent = 0;
    await test.step('用户端：提交订单并点击支付，抓取订单号与支付流水号', async () => {
      // checkout 依次 POST /api/orders、POST /api/payments；抓取支付响应里的 txnNo 供回调使用
      const payResponse = userPage.waitForResponse(
        (r) => r.url().includes('/api/payments') && r.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await userPage.getByRole('button', { name: '提交订单并支付' }).click();

      const paidDialog = userPage.locator('.el-dialog').filter({ hasText: '支付成功' });
      await expect(
        paidDialog,
        '下单 / 支付任一环节失败时不会出现「支付成功」弹窗（可查看失败截图与网络日志）',
      ).toBeVisible({ timeout: 30_000 });

      const dialogText = await paidDialog.innerText();
      const matched = /订单号[：:]\s*([A-Za-z0-9_-]+)/.exec(dialogText);
      expect(matched, `未能从弹窗解析订单号，实际内容：${dialogText}`).not.toBeNull();
      orderNo = matched![1];

      const pay = await payResponse;
      const payBody: any = await pay.json().catch(() => null);
      txnNo = payBody?.data?.txnNo ?? '';
      amountCent = Number(payBody?.data?.amountCent ?? 0);
      expect(txnNo, '发起支付必须返回 txnNo，否则无法模拟渠道回调').toBeTruthy();
    });

    // ------------------------------------------------------------------
    // 3) 支付渠道异步回调：前端发起支付后 1.2s 自动请求 /payment/mock/callback，
    //    将订单从 WAIT_PAY → WAIT_SHIP（管理端发货工作台的前置状态）
    // ------------------------------------------------------------------
    await test.step('支付渠道异步回调：订单流转为 WAIT_SHIP', async () => {
      const readStatus = async (): Promise<string> => {
        const res = await request.get(join(API_URL, `/orders/${orderNo}`), {
          failOnStatusCode: false,
        });
        const body: any = await res.json().catch(() => null);
        return body?.data?.status ?? '';
      };

      const deadline = Date.now() + 10_000;
      let status = '';
      while (Date.now() < deadline) {
        status = await readStatus();
        if (status === 'WAIT_SHIP') break;
        await new Promise((resolve) => setTimeout(resolve, 400));
      }

      // 兜底：未启用前端自动回调的部署，按 P4 契约手动触发一次（后端条件更新天然幂等）
      if (status !== 'WAIT_SHIP') {
        const callback = await request.post(join(API_URL, '/payment/mock/callback'), {
          data: {
            txnNo,
            orderNo,
            amountCent,
            channel: 'BALANCE',
            payChannelTradeNo: `E2E-${Date.now()}`,
            paidAtMillis: Date.now(),
          },
          failOnStatusCode: false,
        });
        const body: any = await callback.json().catch(() => null);
        expect(callback.status(), `兜底支付回调失败：${JSON.stringify(body)}`).toBe(200);
        expect(body?.code, `兜底支付回调业务失败：${JSON.stringify(body)}`).toBe(0);
        status = await readStatus();
      }
      expect(status, '订单必须被异步回调推进到 WAIT_SHIP').toBe('WAIT_SHIP');
    });

    // ------------------------------------------------------------------
    // 4) 管理端：新 Context 独立登录仓管
    // ------------------------------------------------------------------
    await test.step('管理端：独立 Context 登录仓管账号', async () => {
      await adminPage.goto(join(ADMIN_URL, '/login'));
      await adminPage.getByPlaceholder('用户名').fill(CFG.admin.username);
      await adminPage.getByPlaceholder('密码').fill(CFG.admin.password);
      const loginResponse = adminPage.waitForResponse(
        (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
        { timeout: 15_000 },
      );
      await adminPage.getByRole('button', { name: '登录' }).click();
      const response = await loginResponse;
      const body: any = await response.json().catch(() => null);
      expect(
        body?.code,
        `[验收缺口] 管理端登录失败（HTTP ${response.status()}）：${JSON.stringify(body)}`,
      ).toBe(0);
      await expect(adminPage).toHaveURL(/\/admin\/dashboard/, { timeout: 15_000 });
      // 管理端鉴权闭环证据：admin token 通过 /admin/me 拿到 '*' 权限后进入工作台
      await adminPage.screenshot({ path: 'screenshots/02-admin-auth.png', fullPage: true });
    });

    // ------------------------------------------------------------------
    // 5) 发货工作台：定位订单 → 选择发货 → 填发货行 → 确认发货
    // ------------------------------------------------------------------
    await test.step('管理端：发货工作台找到订单并点击发货', async () => {
      await adminPage.goto(join(ADMIN_URL, '/order/shipment'));
      // 菜单项 / 面包屑 / 页面标题都含此文案，必须用 heading 精确定位，避免 strict mode 冲突
      await expect(adminPage.getByRole('heading', { name: '发货工作台' })).toBeVisible();

      const row = adminPage
        .locator('.el-table__row')
        .filter({ hasText: orderNo })
        .first();
      await expect(
        row,
        '[验收缺口] 待发货列表查不到该订单：确认支付回调已把订单推进到 WAIT_SHIP，且 /admin/orders 列表为真实查询',
      ).toBeVisible({ timeout: 20_000 });
      await row.getByRole('button', { name: '选择发货' }).click();

      await adminPage.getByPlaceholder('如：顺丰速运').fill('E2E 顺丰速运');
      await adminPage.getByPlaceholder('快递单号').fill(`E2E-TRACK-${Date.now()}`);

      // 发货行现在由「订单详情」自动带出真实 SKU / 可发数量 / 定制状态并默认勾选，
      // 不再手填 skuId=1 的占位数字框（第一个 input 已是隐藏的 checkbox）。
      const shipLine = adminPage.locator('.ship-line').first();
      await expect(shipLine, '选中订单后应自动带出商品行').toBeVisible({ timeout: 20_000 });
      await expect(shipLine).toContainText(String(CFG.skuId));
      await expect(shipLine.locator('.el-checkbox'), '可发行应默认勾选')
        .toHaveClass(/is-checked/, { timeout: 15_000 });

      await adminPage.getByRole('button', { name: '确认发货' }).click();
      await expect(adminPage.getByText('发货成功', { exact: false })).toBeVisible({
        timeout: 20_000,
      });
    });

    // ------------------------------------------------------------------
    // 6) 回到用户端：确认收货，断言状态为「已完成」
    // ------------------------------------------------------------------
    await test.step('用户端：确认收货并断言订单已完成', async () => {
      await userPage.goto(join(USER_URL, '/orders'));
      const card = userPage.locator('.order-card').filter({ hasText: orderNo }).first();
      await expect(card, '我的订单列表应能看到刚下的订单').toBeVisible({ timeout: 20_000 });
      await expect(
        card.getByRole('button', { name: '确认收货' }),
        '[验收缺口] 订单未流转到 WAIT_RECEIVE（发货状态未回写）时不显示确认收货按钮',
      ).toBeVisible({ timeout: 20_000 });
      await card.getByRole('button', { name: '确认收货' }).click();
      await expect(card.getByText('已完成')).toBeVisible({ timeout: 20_000 });
    });
  } finally {
    // 双端独立 context 必须都关闭，避免 trace/video 句柄泄漏
    await userCtx.close();
    await adminCtx.close();
  }
});

// ---------------------------------------------------------------------------
// 02 · 定制图稿链路（Phase 10）：UV 定制订单 → 图稿 V1 → 客服驳回 → 重传 V2 → 审核通过
//     断言 custom_draft / custom_review_log 真库落账
// ---------------------------------------------------------------------------
test('02 定制图稿链路：UV 定制下单 → 驳回 → 重传 → 审图通过（真库落账）', async ({ browser, request }) => {
  const userCtx: BrowserContext = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const serviceCtx: BrowserContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const userPage: Page = await userCtx.newPage();
  const servicePage: Page = await serviceCtx.newPage();
  let customNo = '';
  let userToken = '';
  let serviceToken = '';

  /** 用户端：把指定定制单号查询出来，再点「上传图稿」 */
  async function uploadDraft(page: Page): Promise<void> {
    await page.goto(join(USER_URL, '/custom'));
    await page.getByPlaceholder('输入定制单号直接查询').fill(customNo);
    await page.getByRole('button', { name: '查询' }).click();
    const upload = page.getByRole('button', { name: '上传图稿' });
    await expect(upload, '定制单详情卡片应展示上传按钮').toBeVisible({ timeout: 15_000 });
    await upload.click();
  }

  /** 管理端：带 ?id= 直达审核台，onMounted 会自动加载详情 */
  async function openReview(page: Page): Promise<void> {
    await page.goto(join(ADMIN_URL, `/custom/review?id=${customNo}`));
    await expect(page.getByRole('heading', { name: '定制审核' })).toBeVisible();
    // 「锁定版本」只在 detail 加载后出现，用它等到详情真正就绪，避免误判隐藏的 el-option
    await expect(page.getByText('锁定版本', { exact: true })).toBeVisible({ timeout: 15_000 });
  }

  try {
    // ------------------------------------------------------------------
    // 1) 用户端登录 + 记录「下单前」已有定制单，避免历史数据干扰识别
    // ------------------------------------------------------------------
    await test.step('用户端：登录并记录已有定制单集合', async () => {
      await userPage.goto(join(USER_URL, '/login'));
      await userPage.getByPlaceholder('请输入11位手机号').fill(CFG.user.username);
      await userPage.getByPlaceholder('请输入密码').fill(CFG.user.password);
      await userPage.getByRole('button', { name: '登录' }).click();
      await expect(userPage).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 15_000 });
      userToken = await accessTokenOf(userPage);
      expect(userToken?.split('.').length, '登录后必须持有 3 段 JWT').toBe(3);
    });

    const before = await apiGet(request, '/custom-orders/my?page=1&size=100', userToken);
    const beforeIds = new Set<string>(
      (before?.data?.list ?? []).map((r: any) => String(r.customOrderId)),
    );
    expect(before?.code, `我的定制单列表读取失败：${JSON.stringify(before)}`).toBe(0);

    // ------------------------------------------------------------------
    // 2) 选 UV 定制规格 → 立即购买 → 勾选「含 UV 定制件」→ 提交订单并支付
    // ------------------------------------------------------------------
    await test.step('用户端：创建一条包含 UV 定制件的订单', async () => {
      await userPage.goto(join(USER_URL, `/product/${CFG.productId}`));
      // 商品详情的规格按钮文案是「SKU-9102」（旧版的 .sku-item 容器已不存在）
      const uvSku = userPage
        .getByRole('button', { name: new RegExp(`SKU-${CFG.customSkuId}`) })
        .first();
      await expect(uvSku, `[验收缺口] 商品详情未列出 UV 定制 SKU ${CFG.customSkuId}`).toBeVisible();
      await uvSku.click();
      await userPage.getByRole('button', { name: '立即购买' }).click();
      await expect(userPage).toHaveURL(/\/checkout/);

      // Element Plus 把原生 input 隐藏了，必须点可见的 label 容器（.el-checkbox）
      const uvCheckbox = userPage.locator('.el-checkbox')
        .filter({ hasText: /含 UV 定制件/ })
        .first();
      await expect(uvCheckbox, '[验收缺口] 结算页缺少 UV 定制选项').toBeVisible();
      await uvCheckbox.click();
      await expect(uvCheckbox, 'UV 定制选项应处于勾选态').toHaveClass(/is-checked/);

      const payResponse = userPage.waitForResponse(
        (r) => r.url().includes('/api/payments') && r.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await userPage.getByRole('button', { name: '提交订单并支付' }).click();
      const paidDialog = userPage.locator('.el-dialog').filter({ hasText: '支付成功' });
      await expect(paidDialog, 'UV 定制订单下单 / 支付失败').toBeVisible({ timeout: 30_000 });
      await payResponse;
    });

    // ------------------------------------------------------------------
    // 3) 下单时后端即时生成 custom_order（WAIT_DRAFT），从真实列表接口取单号
    // ------------------------------------------------------------------
    await test.step('后端：下单即生成 UV 定制单（custom_order）', async () => {
      await expect.poll(async () => {
        const body = await apiGet(request, '/custom-orders/my?page=1&size=100', userToken);
        const rows: any[] = body?.data?.list ?? [];
        const fresh = rows.find((r) => !beforeIds.has(String(r.customOrderId))
          && r.status === 'WAIT_DRAFT');
        customNo = fresh?.customOrderId ?? '';
        return customNo;
      }, { timeout: 20_000, message: 'customFlag=1 的订单行应在下单时生成 custom_order' }).toBeTruthy();
    });

    // ------------------------------------------------------------------
    // 4) 用户端上传图稿 V1（真实 custom_draft 落账）
    // ------------------------------------------------------------------
    await test.step('用户端：上传图稿 V1，断言 WAIT_REVIEW + version 1', async () => {
      await uploadDraft(userPage);
      await expect.poll(async () => {
        const body = await apiGet(request, `/customize/orders/${customNo}`, userToken);
        return `${body?.data?.status ?? ''}|${body?.data?.version ?? 0}`;
      }, { timeout: 20_000 }).toBe('WAIT_REVIEW|1');
    });

    // ------------------------------------------------------------------
    // 5) 客服账号（service / 123456，仅持 custom:review）独立 Context 登录
    // ------------------------------------------------------------------
    await test.step('管理端：客服账号独立 Context 登录', async () => {
      await servicePage.goto(join(ADMIN_URL, '/login'));
      await servicePage.getByPlaceholder('用户名').fill(CFG.service.username);
      await servicePage.getByPlaceholder('密码').fill(CFG.service.password);
      const loginResponse = servicePage.waitForResponse(
        (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
        { timeout: 15_000 },
      );
      await servicePage.getByRole('button', { name: '登录' }).click();
      const response = await loginResponse;
      const body: any = await response.json().catch(() => null);
      expect(body?.code, `[验收缺口] 客服账号登录失败：${JSON.stringify(body)}；V13 种子或 AccountSeeder 未生效`).toBe(0);
      serviceToken = await accessTokenOf(servicePage);
      expect(serviceToken?.split('.').length).toBe(3);
      const me = await apiGet(request, '/admin/me', serviceToken);
      expect(me?.data?.permissions, '客服角色必须持有 custom:review').toContain('custom:review');
    });

    // ------------------------------------------------------------------
    // 6) 客服驳回 V1（真实 custom_review_log 落账）
    // ------------------------------------------------------------------
    await test.step('管理端：客服驳回图稿 V1', async () => {
      await openReview(servicePage);
      const rejectBtn = servicePage.getByRole('button', { name: '驳回重做' });
      await expect(rejectBtn, '[验收缺口] 待审核详情未加载或按钮被禁用').toBeEnabled();
      await servicePage.getByPlaceholder('给客户/生产端的说明').fill('图稿分辨率不足，请重传 UV 稿');
      await rejectBtn.click();
      await expect.poll(async () => {
        const body = await apiGet(request, `/admin/custom-orders/${customNo}`, serviceToken);
        return `${body?.data?.status ?? ''}|${body?.data?.reviewLogCount ?? 0}`;
      }, { timeout: 20_000 }).toBe('REJECTED|1');
    });

    // ------------------------------------------------------------------
    // 7) 用户重传 V2
    // ------------------------------------------------------------------
    await test.step('用户端：重新上传图稿 V2', async () => {
      await uploadDraft(userPage);
      await expect.poll(async () => {
        const body = await apiGet(request, `/customize/orders/${customNo}`, userToken);
        return `${body?.data?.status ?? ''}|${body?.data?.version ?? 0}`;
      }, { timeout: 20_000 }).toBe('WAIT_REVIEW|2');
    });

    // ------------------------------------------------------------------
    // 8) 客服审核通过 V2
    // ------------------------------------------------------------------
    await test.step('管理端：客服审核通过图稿 V2', async () => {
      await openReview(servicePage);
      const passBtn = servicePage.getByRole('button', { name: '审核通过' });
      await expect(passBtn, '[验收缺口] 重传后未回到 WAIT_REVIEW').toBeEnabled();
      await passBtn.click();
      await expect.poll(async () => {
        const body = await apiGet(request, `/admin/custom-orders/${customNo}`, serviceToken);
        return `${body?.data?.status ?? ''}|${body?.data?.lockedVersion ?? 0}`;
      }, { timeout: 20_000 }).toBe('APPROVED|2');
      await servicePage.screenshot({ path: 'screenshots/03-custom-approved.png', fullPage: true });
    });

    // ------------------------------------------------------------------
    // 9) 真库断言：custom_draft 2 行、custom_review_log 2 行（API 计数 + 原始表）
    // ------------------------------------------------------------------
    await test.step('真库落账断言：custom_draft / custom_review_log', async () => {
      const body = await apiGet(request, `/admin/custom-orders/${customNo}`, serviceToken);
      expect(body?.code).toBe(0);
      expect(body?.data?.status).toBe('APPROVED');
      expect(body?.data?.rejectCount, '一次驳回必须回写真库').toBe(1);
      expect(body?.data?.draftCount, 'V1 + V2 必须真实落 custom_draft').toBe(2);
      expect(body?.data?.reviewLogCount, 'REJECT + PASS 必须真实落 custom_review_log').toBe(2);
      expect(body?.data?.drafts?.map((d: any) => d.version)).toEqual([1, 2]);
      expect(body?.data?.reviews?.map((r: any) => r.result)).toEqual(['REJECT', 'PASS']);
      expect(body?.data?.reviews?.[0]?.reasonCode).toBe('DRAFT_UNCLEAR');

      // 表级复核：直接查 MySQL 原始行（本机无 mysql CLI 时自动降级跳过）
      const sub = `(SELECT id FROM custom_order WHERE custom_no = '${customNo}')`;
      const draftRows = dbScalar(`SELECT COUNT(*) FROM custom_draft WHERE custom_order_id = ${sub}`);
      const reviewRows = dbScalar(`SELECT COUNT(*) FROM custom_review_log WHERE custom_order_id = ${sub}`);
      if (draftRows !== null) expect(Number(draftRows), 'custom_draft 原始行数').toBe(2);
      if (reviewRows !== null) expect(Number(reviewRows), 'custom_review_log 原始行数').toBe(2);
    });
  } finally {
    await userCtx.close();
    await serviceCtx.close();
  }
});

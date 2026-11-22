/**
 * 客服相关 E2E 公共助手（2026-11-18 重构）。
 *
 * 业务口径变更：客户进入聊天页只查不建会话，**发出首条消息才算咨询客服**。
 * 工作台的 ACTIVE / WAITING 列表也只展示「已收到客户消息」的会话，
 * 因此所有需要「客服能看到会话」的用例，建完会话后必须先补一条客户消息。
 */
import { expect, type APIRequestContext, type Page } from '@playwright/test';

export const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
export const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
/**
 * 客服工作台独立端（web-cs，:5175）。
 *
 * 2026-11-20 起管理端不再承载工作台：接待类用例必须跑在这个 base 上。
 * 三处与 admin 不同，改用例时别沿用旧写法：
 * 1) base 是 `/`（不是 `/admin/`），登录页 `/login`、工作台 `/workbench`；
 * 2) Token 前缀 `cs-token_*`，与管理端 `sent_admin_*` 物理隔离，**不能复用管理端 storageState**；
 * 3) 登录成功即跳 `/workbench`，没有 `/dashboard` 中间站。
 */
export const CS_URL = process.env.E2E_CS_URL ?? 'http://127.0.0.1:5175';
export const CS_LOGIN_PATH = '/login';
export const CS_WORKBENCH_PATH = '/workbench';
export const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';

export const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
export const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
export const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);
export const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
/** 客服经理（V37 种子账号，工号 CHATMG001，登录名 chat_mgr） */
export const CHAT_MANAGER_NAME = process.env.E2E_CHAT_MANAGER_NAME ?? 'chat_mgr';
export const CHAT_MANAGER_PASSWORD = process.env.E2E_CHAT_MANAGER_PASSWORD ?? '123456';

export function join(base: string, path: string): string {
  return `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;
}

export async function apiCall(
  request: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  data?: unknown,
  token?: string,
): Promise<any> {
  const res = await request.fetch(join(API_URL, path), {
    method,
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

export async function loginApi(
  request: APIRequestContext,
  username: string,
  password: string,
  aud: 'admin' | 'user',
  deviceId = `e2e-helper-${Date.now()}`,
): Promise<string> {
  const body = await apiCall(request, 'POST', '/auth/login', { username, password, aud, deviceId });
  expect(body?.code, `登录失败（${username}）：${JSON.stringify(body)}`).toBe(0);
  return String(body?.data?.accessToken ?? '');
}

/**
 * 客服端 UI 登录（web-cs，:5175）。
 *
 * 占位符用「用户名 / 密码」子串匹配，与登录页实际 placeholder
 * （「请输入工号 / 用户名」「请输入密码」）对齐；登录成功后 Vue Router 会直接
 * replace 到 `/workbench`，因此这里等的是 /workbench 而不是管理端的 /dashboard。
 */
export async function loginCs(
  page: Page,
  username: string = ADMIN_NAME,
  password: string = ADMIN_PASSWORD,
): Promise<void> {
  await page.goto(join(CS_URL, CS_LOGIN_PATH));
  await page.getByPlaceholder('用户名').fill(username);
  await page.getByPlaceholder('密码').fill(password);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `客服端登录失败（${username}）：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/workbench/, { timeout: 20_000 });
  // 2026-11-21 起客服登录默认离线：点「在线」才开始接单（真实客服同样操作）
  await goOnline(page);
}

/**
 * 把顶栏坐席状态切到「在线」。
 *
 * <p>客服端登录后默认离线（用户口径：登录不自动接单，离线看不到排队），
 * 因此任何需要「客户进线自动分配 / 自动弹 Tab」的用例，登录后都必须先切在线。
 * 已经在线的用例重复调用无副作用（按钮已是 active，点击被 store 判重忽略）。</p>
 */
export async function goOnline(page: Page): Promise<void> {
  const online = page.locator('[data-testid="presence-online"]');
  await online.click();
  await expect(online).toHaveClass(/is-active/, { timeout: 15_000 });
}

/** 直接打开工作台（已登录时用它，避免每次重走登录页） */
export async function gotoWorkbench(page: Page): Promise<void> {
  await page.goto(join(CS_URL, CS_WORKBENCH_PATH));
}

/** 置部分客服离线，保证新会话进入排队；pass [] 表示不做改动 */export async function setStaffPresence(
  request: APIRequestContext,
  adminToken: string,
  staffIds: number[],
  presence: 'ONLINE' | 'OFFLINE' | 'BUSY',
): Promise<void> {
  for (const staffId of staffIds) {
    await apiCall(request, 'POST', '/admin/chat/presence', { staffId, presence }, adminToken);
  }
}

/**
 * 清掉所有未结束会话。
 * 必须同时查排队栏（不分页）与会话分页：DemoDataGenerator 会把 2026-06 的两条历史
 * CLOSED 会话重置为 WAITING，它们 id 最小、落在最后一页，只清第一页捞不到。
 */
export async function clearOpenSessions(request: APIRequestContext, adminToken: string): Promise<void> {
  // 只清「看得见」的会话，**不要在这里改坐席状态**：清场助手被 chat-audit / 全链路等多个 spec 复用，
  // 顺手切 ONLINE 会把 WAITING 演示会话补位成 ACTIVE、占掉并发名额，反而让后面新建的会话排不上队
  // （2026-11-21 踩过：chat-concurrency / full-flow 因并发位被僵尸会话占满而红）。
  // 需要连 WAITING 一起清的用例（如 chat-auto-open*，它断言开场 Tab 数为 0）自己在用例内补一步
  // 「先切在线 → 再关 → 再离场」。
  const queue = await apiCall(request, 'GET', '/admin/chat/sessions/open?staffId=9001', undefined, adminToken);
  const listed = await apiCall(request, 'GET', '/admin/chat/sessions?page=1&size=100', undefined, adminToken);
  const rows: any[] = [
    ...(queue?.data?.waiting ?? []),
    ...(queue?.data?.active ?? []),
    ...(listed?.data?.list ?? []),
  ];
  for (const row of rows) {
    if (row.status !== 'CLOSED') {
      await apiCall(request, 'POST', `/admin/chat/sessions/${row.sessionId}/close`,
        { reason: 'DONE' }, adminToken);
    }
  }
}

/**
 * 建立「有效咨询」：建会话后补一条客户消息。
 * 不补这条消息，工作台的 selectVisibleActiveByStaff / selectVisibleWaitingFifo 会把会话过滤掉，
 * 用例里表现为「会话明明建了，客服端却不弹 Tab」。
 */
export async function startConsultation(
  request: APIRequestContext,
  sessionId: number,
  userToken: string,
  content = '你好，我要咨询商品',
): Promise<void> {
  const body = await apiCall(request, 'POST', `/chat/sessions/${sessionId}/messages`, {
    senderType: 'CUSTOMER',
    content,
    clientMsgId: `e2e-first-${sessionId}-${Date.now()}`,
  }, userToken);
  expect(body?.code, `补首条客户消息失败：${JSON.stringify(body)}`).toBe(0);
}

/** 从当前 `/chat/sessions/current` 读客户未结束会话，没有则 null（只查不建） */
export async function currentOpenSession(
  request: APIRequestContext,
  customerId: number,
  userToken: string,
): Promise<number | null> {
  const body = await apiCall(request, 'GET', `/chat/sessions/current?customerId=${customerId}`,
    undefined, userToken);
  const id = Number(body?.data?.sessionId ?? 0);
  return id > 0 ? id : null;
}

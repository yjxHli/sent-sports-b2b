/**
 * 客服相关 E2E 公共助手（2026-11-18 重构）。
 *
 * 业务口径变更：客户进入聊天页只查不建会话，**发出首条消息才算咨询客服**。
 * 工作台的 ACTIVE / WAITING 列表也只展示「已收到客户消息」的会话，
 * 因此所有需要「客服能看到会话」的用例，建完会话后必须先补一条客户消息。
 */
import { expect, type APIRequestContext } from '@playwright/test';

export const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
export const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
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

/** 置部分客服离线，保证新会话进入排队；pass [] 表示不做改动 */
export async function setStaffPresence(
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

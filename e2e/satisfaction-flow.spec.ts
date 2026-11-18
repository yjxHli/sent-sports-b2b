/**
 * 客服满意度评价（2026-11-17 用户要求，TDD）：
 * 客服主动发卡 → 客户打分（满意/一般/不满意）→ 同一会话只留一条评价
 * → 管理端「今日满意率」实时更新；会话结束时还会自动补发一张卡。
 *
 * 满意率口径：满意数 ÷ 有效评价数 × 100%（一般/不满意都算有效）。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { startConsultation } from './chat-helpers';

const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);
const STAFF_IDS = [9001, 9003, 9004, 9005, 9006, 9007];
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';

function sql(statement: string): string {
  const bin = process.env.E2E_MYSQL_BIN ?? (existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql');
  return execFileSync(bin, ['-h127.0.0.1', '-P3306', '-uroot',
    `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`, 'sent_dev', '-N', '-B', '-e', statement],
    { encoding: 'utf8' }).trim();
}

const join = (base: string, suffix: string): string =>
  `${base.replace(/\/+$/, '')}/${suffix.replace(/^\/+/, '')}`;

async function api(request: APIRequestContext, method: 'GET' | 'POST', path: string,
                   data?: unknown, token?: string): Promise<any> {
  const res = await request.fetch(join(API_URL, path), {
    method, data, headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

async function loginAdmin(page: Page): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

test('客服发评价卡 → 客户打分 → 今日满意率实时显示，重复打分幂等', async ({ browser, request }) => {
  test.setTimeout(180_000);

  const adminLogin = await api(request, 'POST', '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin',
    deviceId: `e2e-sat-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken, `管理端登录失败：${JSON.stringify(adminLogin)}`).toBeTruthy();
  const userLogin = await api(request, 'POST', '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user',
    deviceId: `e2e-sat-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken, `用户端登录失败：${JSON.stringify(userLogin)}`).toBeTruthy();

  // 清场：只留 9001 在线并关掉遗留会话，避免其它会话干扰统计与自动弹 Tab
  for (const staffId of STAFF_IDS) {
    await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
  }
  const queue = await api(request, 'GET', '/admin/chat/sessions/open?staffId=9001', undefined, adminToken);
  const listed = await api(request, 'GET', '/admin/chat/sessions?page=1&size=100', undefined, adminToken);
  const openRows: any[] = [...(queue?.data?.waiting ?? []), ...(queue?.data?.active ?? []),
    ...(listed?.data?.list ?? [])];
  for (const row of openRows) {
    if (row.status !== 'CLOSED') {
      await api(request, 'POST', `/admin/chat/sessions/${row.sessionId}/close`, { reason: 'DONE' }, adminToken);
    }
  }
  await api(request, 'POST', '/admin/chat/presence', { staffId: 9001, presence: 'ONLINE' }, adminToken);

  const created = await api(request, 'POST', '/chat/sessions',
    { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  // 业务口径：首条客户消息才算咨询，工作台只展示已有客户消息的会话
  await startConsultation(request, sessionId, userToken);
  expect(sessionId, `建会话失败：${JSON.stringify(created)}`).toBeGreaterThan(0);
  expect(Number(created?.data?.staffId)).toBe(9001);

  try {
    // 1) 客服主动发卡（幂等键 SATISFY-{sessionId}）
    const sent = await api(request, 'POST', `/admin/chat/sessions/${sessionId}/satisfaction`, {}, adminToken);
    expect(sent?.code, `发卡失败：${JSON.stringify(sent)}`).toBe(0);
    expect(sent?.data?.messageId).toBeTruthy();

    const history = await api(request, 'GET', `/chat/sessions/${sessionId}/messages`, undefined, userToken);
    const cards = (history?.data ?? []).filter((m: any) => m.msgType === 'SATISFY');
    expect(cards.length).toBe(1);
    expect(JSON.parse(cards[0].content).status).toBe('PENDING');
    // 发卡后全局唯一：重复点不产生第二张
    await api(request, 'POST', `/admin/chat/sessions/${sessionId}/satisfaction`, {}, adminToken);
    const again = await api(request, 'GET', `/chat/sessions/${sessionId}/messages`, undefined, userToken);
    expect((again?.data ?? []).filter((m: any) => m.msgType === 'SATISFY').length).toBe(1);

    // 2) 客户打「满意」
    const rated = await api(request, 'POST', `/chat/sessions/${sessionId}/satisfaction`,
      { score: 'SATISFIED' }, userToken);
    expect(rated?.code, `打分失败：${JSON.stringify(rated)}`).toBe(0);
    expect(rated?.data?.score).toBe('SATISFIED');

    const afterRate = await api(request, 'GET', `/chat/sessions/${sessionId}/messages`, undefined, userToken);
    const updated = (afterRate?.data ?? []).find((m: any) => m.msgType === 'SATISFY');
    expect(JSON.parse(updated.content).status).toBe('RATED');
    expect(JSON.parse(updated.content).score).toBe('SATISFIED');

    // 3) 同一会话只留一条有效评价：再次提交「不满意」也必须回原档位
    const repeated = await api(request, 'POST', `/chat/sessions/${sessionId}/satisfaction`,
      { score: 'UNSATISFIED' }, userToken);
    expect(repeated?.data?.score).toBe('SATISFIED');
    expect(sql(`SELECT COUNT(*) FROM chat_satisfaction WHERE session_id = ${sessionId};`)).toBe('1');

    // 4) 今日满意率：服务端实时统计，前端 chip 展示
    const stats = await api(request, 'GET', '/admin/chat/stats/today?staffId=9001', undefined, adminToken);
    expect(Number(stats?.data?.ratedCount ?? 0)).toBeGreaterThanOrEqual(1);
    expect(Number(stats?.data?.satisfiedCount ?? 0)).toBeGreaterThanOrEqual(1);
    expect(Number(stats?.data?.ratePercent ?? -1)).toBeGreaterThanOrEqual(0);

    const page = await browser.newPage();
    try {
      await loginAdmin(page);
      await page.goto(join(ADMIN_URL, '/chat/workbench'));
      const chip = page.getByTestId('today-satisfaction-rate');
      await expect(chip).toBeVisible({ timeout: 20_000 });
      await expect(chip).toContainText('今日满意率');
      await expect(chip).not.toContainText('--');

      // 聊天气泡时间分隔：首条消息必显当前时间（5 分钟阈值由 shared 纯函数单测覆盖）
      await expect(page.getByTestId('chat-time-divider').first()).toBeVisible({ timeout: 15_000 });
    } finally {
      await page.close();
    }
  } finally {
    for (const staffId of STAFF_IDS) {
      await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
    }
    sql(`DELETE FROM chat_satisfaction WHERE session_id = ${sessionId};`);
  }
});

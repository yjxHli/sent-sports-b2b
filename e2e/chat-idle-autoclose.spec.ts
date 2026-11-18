/**
 * 客服会话 10 分钟无消息自动结束（TDD 红→绿，2026-11-16 用户要求）。
 *
 * 规则：客户超过 10 分钟没发消息 → 会话自动结束（close_reason=TIMEOUT），坐席名额释放。
 * 实现：ChatTimeoutJob 每分钟扫 last_msg_at 超过 10 分钟的 ACTIVE 会话，落库走
 *       CloseSessionUseCase.timeoutClose（关会话 + 负载 -1 + 队列补位）。
 *
 * 为什么不用「真等 10 分钟」：把 last_msg_at 回拨 15 分钟，等价于这条会话已经安静了 15 分钟，
 * 定时任务下一轮（≤60s）就该把它收掉。这样 E2E 一分钟内能验证整条链路。
 *
 * 断言口径：
 *   1) 前置：客户进线拿到 ACTIVE 会话，9001 工作台「进行中」能看到；
 *   2) 回拨 last_msg_at 15 分钟后，一轮任务内会话变 CLOSED 且 close_reason=TIMEOUT；
 *   3) 工作台列表随之清空（名额释放，会话不再挂在进行中）。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type APIRequestContext } from '@playwright/test';
import { startConsultation } from './chat-helpers';

const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const STAFF_IDS = [9001, 9003, 9004, 9005, 9006, 9007];
/** 定时任务每分钟一轮，留足两轮余量。 */
const POLL_TIMEOUT_MS = 150_000;

const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';

function resolveMysqlBin(): string {
  const fromEnv = process.env.E2E_MYSQL_BIN;
  if (fromEnv) return fromEnv;
  return existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql';
}

/** 只读/改一行的 SQL 执行（-N -B 便于按行解析结果）。 */
function querySql(sql: string): string {
  const password = process.env.E2E_DB_PASSWORD ?? '123456';
  return execFileSync(
    resolveMysqlBin(),
    ['-h127.0.0.1', '-P3306', '-uroot', `-p${password}`, 'sent_dev', '-N', '-B', '-e', sql],
    { encoding: 'utf8' },
  ).trim();
}

function execSql(sql: string): void {
  const password = process.env.E2E_DB_PASSWORD ?? '123456';
  execFileSync(
    resolveMysqlBin(),
    ['-h127.0.0.1', '-P3306', '-uroot', `-p${password}`, 'sent_dev', '-e', sql],
    { stdio: 'ignore' },
  );
}

const join = (base: string, suffix: string): string =>
  `${base.replace(/\/+$/, '')}/${suffix.replace(/^\/+/, '')}`;

async function api(
  request: APIRequestContext,
  method: 'GET' | 'POST',
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

test('客户 10 分钟无消息：会话自动结束（TIMEOUT）并从工作台进行中移除', async ({ request }) => {
  test.setTimeout(240_000);

  const adminLogin = await api(request, 'POST', '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin',
    deviceId: `e2e-idle-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken, `管理端登录失败：${JSON.stringify(adminLogin)}`).toBeTruthy();
  const userLogin = await api(request, 'POST', '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user',
    deviceId: `e2e-idle-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken, `用户端登录失败：${JSON.stringify(userLogin)}`).toBeTruthy();

  // 清场：全员离线 → 结束该客户遗留的未结束会话 → 9001 上线（单坐席只认 9001）
  for (const staffId of STAFF_IDS) {
    await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
  }
  // 清「所有」未结束会话：排队栏（/sessions/open）不受分页影响，能捞到 id 最小、落在列表最后一页的
  // 两条 2026-06 演示会话——服务端启动时 DemoDataGenerator 会把它们重置成 WAITING「待接入」
  const queue = await api(request, 'GET', '/admin/chat/sessions/open?staffId=9001', undefined, adminToken);
  const listed = await api(request, 'GET', '/admin/chat/sessions?page=1&size=100', undefined, adminToken);
  const openRows: any[] = [...(queue?.data?.waiting ?? []), ...(queue?.data?.active ?? []), ...(listed?.data?.list ?? [])];
  for (const row of openRows) {
    if (row.status !== 'CLOSED') {
      await api(request, 'POST', `/admin/chat/sessions/${row.sessionId}/close`, { reason: 'DONE' }, adminToken);
    }
  }
  await api(request, 'POST', '/admin/chat/presence', { staffId: 9001, presence: 'ONLINE' }, adminToken);

  // 1) 客户进线：应为 ACTIVE 且挂在 9001 名下（先确认前置成立，再谈超时）
  const created = await api(request, 'POST', '/chat/sessions', { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  // 业务口径：首条客户消息才算咨询，工作台只展示已有客户消息的会话
  await startConsultation(request, sessionId, userToken);
  expect(sessionId, `建会话失败：${JSON.stringify(created)}`).toBeGreaterThan(0);
  expect(created?.data?.status, '9001 在线时客户进线应是进行中').toBe('ACTIVE');
  expect(Number(created?.data?.staffId)).toBe(9001);

  try {
    // 坐席负载在内存仓储里（没有落 MySQL），只能通过 workload 接口读
    const workloadBefore = await api(request, 'GET', '/admin/chat/workload', undefined, adminToken);
    const activeBeforeClose = Number(workloadBefore?.data?.find(
      (w: any) => Number(w.staffId) === 9001)?.activeSessions ?? 0);
    const open = await api(request, 'GET', '/admin/chat/sessions/open?staffId=9001', undefined, adminToken);
    const visible = (open?.data?.active ?? []).some((row: any) => Number(row.sessionId) === sessionId);
    expect(visible, '前置：刚进线的会话应出现在 9001 的「进行中」').toBe(true);

    // 2) 回拨最后消息时间 → 等价于已安静 15 分钟
    execSql(`UPDATE chat_session SET last_msg_at = DATE_SUB(NOW(3), INTERVAL 15 MINUTE) WHERE id = ${sessionId};`);

    // 3) 等一轮定时任务把它收掉
    const deadline = Date.now() + POLL_TIMEOUT_MS;
    let state = '';
    while (Date.now() < deadline) {
      state = querySql(`SELECT CONCAT(status, '|', IFNULL(close_reason, '')) FROM chat_session WHERE id = ${sessionId};`);
      if (state.startsWith('CLOSED|')) break;
      await new Promise((resolve) => setTimeout(resolve, 5_000));
    }
    expect(state, `会话未在 ${POLL_TIMEOUT_MS / 1000}s 内被自动结束（当前 ${state}）`).toMatch(/^CLOSED\|/);
    expect(state, '自动结束原因必须是 TIMEOUT').toBe('CLOSED|TIMEOUT');

    // 4) 名额释放：工作台「进行中」不再有它
    const after = await api(request, 'GET', '/admin/chat/sessions/open?staffId=9001', undefined, adminToken);
    const stillThere = (after?.data?.active ?? []).some((row: any) => Number(row.sessionId) === sessionId);
    expect(stillThere, '自动结束后会话不该还留在工作台进行中').toBe(false);

    // 5) 坐席并发计数必须 -1：直接 UPDATE 状态会漏掉这一步，几轮后 9001 被判满、新客户永远排队
    const workloadAfter = await api(request, 'GET', '/admin/chat/workload', undefined, adminToken);
    const activeAfterClose = Number(workloadAfter?.data?.find(
      (w: any) => Number(w.staffId) === 9001)?.activeSessions ?? 0);
    expect(activeBeforeClose, '前置：进线后 9001 应占用 1 个名额').toBeGreaterThan(0);
    expect(activeAfterClose, '超时结束必须释放坐席名额').toBeLessThan(activeBeforeClose);
  } finally {
    for (const staffId of STAFF_IDS) {
      await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
    }
  }
});

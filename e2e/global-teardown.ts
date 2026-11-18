/**
 * 全量 E2E 收尾清理（2026-11-16）。
 *
 * 背景：用例会真的建客服会话、把客服置为 ONLINE，跑完不复位就会污染演示环境——
 * 最典型的一次是 full-flow 用 9003 接待后留下「ACTIVE + staff_id=9003」的会话，
 * 商城测试账号下次进客服页会**复用它**，而 9001 工作台按 staff_id 过滤看不到这条会话，
 * 表现成「客户发了你好，客服界面进行中/排队都是 0，刷新也没有」。
 *
 * 这里做两件事（都走真实接口，等价于客服下班收摊）：
 *   1) 全员置 OFFLINE —— PresenceUseCase 会把名下 ACTIVE 会话释放回 WAITING；
 *   2) 结束所有未结束会话 —— 下次进线重新分配，避免复用上一轮的会话。
 *
 * 注意：用**绝对地址**，不要给 request context 设 baseURL 再传 '/api/...'——
 * URL 解析会把 '/api' 前缀吃掉（'http://h/api' + '/admin/x' → 'http://h/admin/x'），
 * 登录直接 404、teardown 静默失效（踩过）。
 */
import { request } from '@playwright/test';

const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
const STAFF_IDS = [9001, 9003, 9004, 9005, 9006, 9007];

export default async function globalTeardown(): Promise<void> {
  const ctx = await request.newContext();
  const auth = (token: string) => ({ Authorization: `Bearer ${token}` });
  try {
    const login = await ctx.post(`${API_URL}/auth/login`, {
      data: {
        username: ADMIN_NAME,
        password: ADMIN_PASSWORD,
        aud: 'admin',
        deviceId: `e2e-teardown-${Date.now()}`,
      },
      failOnStatusCode: false,
    });
    const body: any = await login.json().catch(() => null);
    const token = body?.data?.accessToken as string | undefined;
    // 后端没起来（只跑静态检查/看报告）就跳过，别让 teardown 把整轮结果标成失败
    if (!token) return;

    for (const staffId of STAFF_IDS) {
      await ctx
        .post(`${API_URL}/admin/chat/presence`, {
          data: { staffId, presence: 'OFFLINE' },
          headers: auth(token),
          failOnStatusCode: false,
        })
        .catch(() => undefined);
    }

    // 两条来源都扫：排队栏（/sessions/open）不受分页影响，能捞到 id 最小、落在列表最后一页的
    // 2026-06 演示会话（服务端启动时 DemoDataGenerator 会把它们重置成 WAITING「待接入」）
    const queueResp = await ctx
      .get(`${API_URL}/admin/chat/sessions/open?staffId=${STAFF_IDS[0]}`, {
        headers: auth(token),
        failOnStatusCode: false,
      })
      .catch(() => undefined);
    const queueBody: any = await queueResp?.json().catch(() => null);
    const listed = await ctx
      .get(`${API_URL}/admin/chat/sessions?page=1&size=100`, {
        headers: auth(token),
        failOnStatusCode: false,
      })
      .catch(() => undefined);
    const listBody: any = await listed?.json().catch(() => null);
    const openRows = [
      ...(queueBody?.data?.waiting ?? []),
      ...(queueBody?.data?.active ?? []),
      ...(listBody?.data?.list ?? []),
    ];
    for (const row of openRows) {
      if (row.status === 'CLOSED') continue;
      await ctx
        .post(`${API_URL}/admin/chat/sessions/${row.sessionId}/close`, {
          data: { reason: 'DONE' },
          headers: auth(token),
          failOnStatusCode: false,
        })
        .catch(() => undefined);
    }
  } finally {
    await ctx.dispose();
  }
}

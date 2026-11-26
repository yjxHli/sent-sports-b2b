/**
 * 客服审查（2026-11-18 用户要求）：客服经理查看每个客服与用户的沟通情况，
 * 列表含用户 ID / 客服 ID / 聊天时间 / 问题概述 / 解决情况，详情含聊天记录 + 客服填写信息。
 * 权限必须精确隔离：只有超管与客服经理持 chat:audit，持 '*' 的业务角色不能越权。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  ADMIN_URL, join, apiCall, loginApi, clearOpenSessions,
  setStaffPresence, startConsultation, ADMIN_NAME, ADMIN_PASSWORD,
  CHAT_MANAGER_NAME, CHAT_MANAGER_PASSWORD, CUSTOMER_ID, closeSessionWithTicket,
} from './chat-helpers';

const NOTE = {
  summary: '咨询定制足球印字起订量',
  category: 'INQUIRY',
  info: 'CLEAR',
  result: 'DONE',
  solution: '已告知 30 件起订、7 天交期',
};

async function loginUi(page: Page, username: string, password: string): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(username);
  await page.getByPlaceholder('密码').fill(password);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('客服经理审查：列表字段完整、详情可看聊天记录与小结；业务角色越权被拒', async ({ browser, request }) => {
  test.setTimeout(180_000);

  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const userToken = await loginApi(request, '13800138000', '123456', 'user');
  await setStaffPresence(request, adminToken, [9001, 9003, 9004, 9005, 9006, 9007], 'OFFLINE');
  await clearOpenSessions(request, adminToken);
  await setStaffPresence(request, adminToken, [9001], 'ONLINE');

  const created = await apiCall(request, 'POST', '/chat/sessions',
    { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  expect(sessionId, `建会话失败：${JSON.stringify(created)}`).toBeGreaterThan(0);
  await startConsultation(request, sessionId, userToken, NOTE.summary);
  // 9001 在线时建会话已自动分配：只有仍在排队才需要人工接待
  const detail = await apiCall(request, 'GET', `/admin/chat/sessions/${sessionId}`, undefined, adminToken);
  if (detail?.data?.status === 'WAITING') {
    const accepted = await apiCall(request, 'POST', `/admin/chat/sessions/${sessionId}/accept`,
      { staffId: 9001 }, adminToken);
    expect(accepted?.code, `接待失败：${JSON.stringify(accepted)}`).toBe(0);
  }

  const saved = await apiCall(request, 'PUT', `/admin/chat/sessions/${sessionId}/note`,
    { staffId: 9001, ...NOTE }, adminToken);
  expect(saved?.code, `保存小结失败：${JSON.stringify(saved)}`).toBe(0);

  // 系统消息源：评价卡在打分后会写成 SATISFY + JSON 内容，审查回放必须翻译成人话
  const sentCard = await apiCall(request, 'POST',
    `/admin/chat/sessions/${sessionId}/satisfaction`, {}, adminToken);
  expect(sentCard?.code, `发评价卡失败：${JSON.stringify(sentCard)}`).toBe(0);
  const rated = await apiCall(request, 'POST', `/chat/sessions/${sessionId}/satisfaction`,
    { score: 'SATISFIED' }, userToken);
  expect(rated?.code, `打分失败：${JSON.stringify(rated)}`).toBe(0);

  const closed = await closeSessionWithTicket(request, Number(sessionId), adminToken);
  expect(closed?.code).toBe(0);

  // 权限隔离：销售（角色带 '*'）不能访问 chat:audit
  const salesToken = await loginApi(request, 'sales', '123456', 'admin');
  const forbidden = await apiCall(request, 'GET', '/admin/chat/audit/sessions?page=1&size=10',
    undefined, salesToken);
  expect(forbidden?.code, 'exact=true 不能吃 "*" 通配').toBe(40301);

  const managerToken = await loginApi(request, CHAT_MANAGER_NAME, CHAT_MANAGER_PASSWORD, 'admin');
  const audit = await apiCall(request, 'GET',
    `/admin/chat/audit/sessions?page=1&size=20&keyword=${CUSTOMER_ID}`, undefined, managerToken);
  expect(audit?.code, `客服经理审查列表失败：${JSON.stringify(audit)}`).toBe(0);
  const row = (audit?.data?.list ?? []).find((r: any) => Number(r.sessionId) === sessionId);
  expect(row, '审查列表应包含刚结束的会话').toBeTruthy();
  expect(Number(row.customerId)).toBe(CUSTOMER_ID);
  expect(Number(row.staffId)).toBe(9001);
  expect(row.summary).toBe(NOTE.summary);
  expect(row.result).toBe('DONE');
  expect(row.noteStatus).toBe('已提交');

  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await ctx.newPage();
  try {
    await loginUi(page, CHAT_MANAGER_NAME, CHAT_MANAGER_PASSWORD);
    await page.goto(join(ADMIN_URL, '/chat/audit'));
    await expect(page.getByRole('heading', { name: '客服审查' })).toBeVisible();
    await page.getByPlaceholder('用户 ID / 会话号 / 问题概述').fill(String(CUSTOMER_ID));
    await page.getByRole('button', { name: '查询' }).click();
    const targetRow = page.locator('tbody tr', { hasText: String(sessionId) }).first();
    await expect(targetRow).toBeVisible({ timeout: 20_000 });
    await expect(targetRow.getByTestId('audit-customer-id')).toHaveText(String(CUSTOMER_ID));
    await targetRow.getByTestId('audit-detail-btn').click();
    await expect(page.getByTestId('audit-transcript')).toContainText(NOTE.summary, { timeout: 15_000 });
    // 第三轮需求：底部的 SATISFY JSON 报文不能裸露，统一显示「会话已结束」
    await expect(page.getByTestId('audit-transcript')).toContainText('会话已结束', { timeout: 15_000 });
    await expect(page.getByTestId('audit-transcript')).not.toContainText('"status"');
    await expect(page.getByTestId('audit-note')).toContainText(NOTE.summary);
    await expect(page.getByTestId('audit-note')).toContainText(NOTE.solution);
  } finally {
    await setStaffPresence(request, adminToken, [9001, 9003, 9004, 9005, 9006, 9007], 'OFFLINE');
    await ctx.close();
  }
});

test('历史服务记录服务端硬上限 10，前端传 999 也不放行', async ({ request }) => {
  test.setTimeout(180_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const userToken = await loginApi(request, '13800138000', '123456', 'user');
  await clearOpenSessions(request, adminToken);
  for (let i = 0; i < 12; i += 1) {
    const created = await apiCall(request, 'POST', '/chat/sessions',
      { customerId: CUSTOMER_ID, vip: false }, userToken);
    const id = Number(created?.data?.sessionId);
    expect(id).toBeGreaterThan(0);
    await closeSessionWithTicket(request, Number(id), adminToken);
  }
  const history = await apiCall(request, 'GET',
    `/admin/chat/customers/${CUSTOMER_ID}/sessions?limit=999`, undefined, adminToken);
  expect(history?.code).toBe(0);
  expect((history?.data ?? []).length).toBeLessThanOrEqual(10);
});

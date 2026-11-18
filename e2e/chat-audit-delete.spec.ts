/**
 * 客服审查删除（2026-11-18 用户要求）：对齐发票管理——
 * 点「删除」进入勾选态 → 输入「删除」二次确认 → 软删会话审查记录，历史聊天不受影响。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';
import {
  ADMIN_URL, join, apiCall, loginApi, clearOpenSessions, setStaffPresence, startConsultation,
  ADMIN_NAME, ADMIN_PASSWORD, CHAT_MANAGER_NAME, CHAT_MANAGER_PASSWORD, CUSTOMER_ID,
  CUSTOMER_PHONE, CUSTOMER_PASSWORD,
} from './chat-helpers';

const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';
const NOTE = {
  summary: '审查删除用小结合', category: 'INQUIRY', info: 'CLEAR',
  result: 'DONE', solution: '已按口径回复',
};

function sql(statement: string): string {
  const bin = process.env.E2E_MYSQL_BIN ?? (existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql');
  return execFileSync(bin, [
    '-h127.0.0.1', '-P3306', '-uroot', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
    'sent_dev', '-N', '-B', '-e', statement,
  ], { encoding: 'utf8' }).trim();
}

async function loginManagerUi(page: Page): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(CHAT_MANAGER_NAME);
  await page.getByPlaceholder('密码').fill(CHAT_MANAGER_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

test('客服审查可批量软删：确认令牌后从列表消失，库里只打标记', async ({ browser, request }) => {
  test.setTimeout(240_000);

  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const managerToken = await loginApi(request, CHAT_MANAGER_NAME, CHAT_MANAGER_PASSWORD, 'admin');
  const userToken = await loginApi(request, CUSTOMER_PHONE, CUSTOMER_PASSWORD, 'user');
  await setStaffPresence(request, adminToken, [9001, 9003, 9004, 9005, 9006, 9007], 'OFFLINE');
  await clearOpenSessions(request, adminToken);
  await setStaffPresence(request, adminToken, [9001], 'ONLINE');

  const created = await apiCall(request, 'POST', '/chat/sessions',
    { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  expect(sessionId).toBeGreaterThan(0);
  await startConsultation(request, sessionId, userToken, NOTE.summary);
  const detail = await apiCall(request, 'GET', `/admin/chat/sessions/${sessionId}`, undefined, adminToken);
  if (detail?.data?.status === 'WAITING') {
    await apiCall(request, 'POST', `/admin/chat/sessions/${sessionId}/accept`,
      { staffId: 9001 }, adminToken);
  }
  await apiCall(request, 'PUT', `/admin/chat/sessions/${sessionId}/note`,
    { staffId: 9001, ...NOTE }, adminToken);
  await apiCall(request, 'POST', `/admin/chat/sessions/${sessionId}/close`,
    { reason: 'DONE' }, adminToken);

  sql(`UPDATE chat_session SET audit_deleted = 0 WHERE id = ${sessionId};`);
  try {
    const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
    const page = await ctx.newPage();
    try {
      await loginManagerUi(page);
      await page.goto(join(ADMIN_URL, '/chat/audit'));
      await expect(page.getByRole('heading', { name: '客服审查' })).toBeVisible();
      await page.getByPlaceholder('用户 ID / 会话号 / 问题概述').fill(String(sessionId));
      await page.getByRole('button', { name: '查询' }).click();
      const row = page.locator('tbody tr', { hasText: String(sessionId) }).first();
      await expect(row).toBeVisible({ timeout: 20_000 });

      await page.getByTestId('batch-delete-btn').click();
      await expect(page.getByTestId('batch-delete-confirm')).toBeDisabled();
      await row.locator('.el-checkbox').click();
      await page.getByTestId('batch-delete-confirm').click();
      const dialog = page.locator('.el-message-box');
      await expect(dialog).toBeVisible({ timeout: 10_000 });
      await dialog.locator('input').fill('删除');
      const done = page.waitForResponse(
        (r) => r.url().includes('/api/admin/chat/audit/sessions/batch-delete')
          && r.request().method() === 'POST',
        { timeout: 20_000 },
      );
      await dialog.getByRole('button', { name: '确认继续' }).click();
      const resp: any = await (await done).json().catch(() => null);
      expect(resp?.code, `审查删除失败：${JSON.stringify(resp)}`).toBe(0);

      await expect(page.locator('tbody tr', { hasText: String(sessionId) }))
        .toHaveCount(0, { timeout: 20_000 });
      const flag = sql(`SELECT audit_deleted FROM chat_session WHERE id = ${sessionId};`);
      expect(flag).toBe('1');

      // 软删只影响审查列表，聊天记录与小结仍在
      const transcript = await apiCall(request, 'GET',
        `/admin/chat/sessions/${sessionId}/transcript`, undefined, managerToken);
      expect(transcript?.code).toBe(0);
      expect(transcript?.data?.messages?.length).toBeGreaterThan(0);
    } finally {
      await ctx.close();
    }
  } finally {
    sql(`UPDATE chat_session SET audit_deleted = 0 WHERE id = ${sessionId};`);
    await setStaffPresence(request, adminToken, [9001, 9003, 9004, 9005, 9006, 9007], 'OFFLINE');
  }
});

/**
 * 会话生命周期（2026-11-18 用户要求）：
 *  1) 进入聊天页不建会话，首条消息才建；
 *  2) 客服结束后客户立刻看到「本次会话已结束」，客服 Tab 置灰保留，需主动关闭；
 *  3) 客服在右侧填写本次情况，必填校验生效；客户可主动重新咨询。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  ADMIN_URL, USER_URL, join, apiCall, loginApi, clearOpenSessions,
  setStaffPresence, startConsultation, currentOpenSession,
  ADMIN_NAME, ADMIN_PASSWORD, CUSTOMER_ID, CUSTOMER_PHONE, CUSTOMER_PASSWORD,
} from './chat-helpers';

async function loginUserUi(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

async function loginAdminUi(page: Page): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

test('首条消息才建会话；结束后双端感知；Tab 手动关闭；小结必填；客户可重新咨询', async ({ browser, request }) => {
  test.setTimeout(240_000);

  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const userToken = await loginApi(request, CUSTOMER_PHONE, CUSTOMER_PASSWORD, 'user');
  await setStaffPresence(request, adminToken, [9001, 9003, 9004, 9005, 9006, 9007], 'OFFLINE');
  await clearOpenSessions(request, adminToken);
  await setStaffPresence(request, adminToken, [9001], 'ONLINE');

  // 0) 先制造一条「历史服务」：结束后的会话才能在右侧回放，并校验大弹窗的气泡样式
  const prior = await apiCall(request, 'POST', '/chat/sessions',
    { customerId: CUSTOMER_ID, vip: false }, userToken);
  const priorId = Number(prior?.data?.sessionId);
  expect(priorId).toBeGreaterThan(0);
  await startConsultation(request, priorId, userToken, '历史咨询消息');
  const priorDetail = await apiCall(request, 'GET', `/admin/chat/sessions/${priorId}`, undefined, adminToken);
  if (priorDetail?.data?.status === 'WAITING') {
    await apiCall(request, 'POST', `/admin/chat/sessions/${priorId}/accept`, { staffId: 9001 }, adminToken);
  }
  await apiCall(request, 'PUT', `/admin/chat/sessions/${priorId}/note`, {
    staffId: 9001, summary: '历史服务小结', category: 'INQUIRY', info: 'CLEAR',
    result: 'DONE', solution: '已按历史口径回复',
  }, adminToken);
  await apiCall(request, 'POST', `/admin/chat/sessions/${priorId}/close`, { reason: 'DONE' }, adminToken);

  // 1) 只查不建：进入聊天页前没有任何未结束会话
  expect(await currentOpenSession(request, CUSTOMER_ID, userToken)).toBeNull();

  const created = await apiCall(request, 'POST', '/chat/sessions',
    { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  expect(sessionId).toBeGreaterThan(0);
  await startConsultation(request, sessionId, userToken, '首条咨询消息');
  // 9001 在线时建会话已自动分配：只有仍在排队才需要人工接待
  const detail = await apiCall(request, 'GET', `/admin/chat/sessions/${sessionId}`, undefined, adminToken);
  if (detail?.data?.status === 'WAITING') {
    const accepted = await apiCall(request, 'POST', `/admin/chat/sessions/${sessionId}/accept`,
      { staffId: 9001 }, adminToken);
    expect(accepted?.code, `接待失败：${JSON.stringify(accepted)}`).toBe(0);
  }

  const userCtx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const adminCtx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const userPage = await userCtx.newPage();
  const adminPage = await adminCtx.newPage();
  try {
    await loginUserUi(userPage);
    await userPage.goto(join(USER_URL, '/chat'));
    await expect(userPage.locator('.chat-bubble', { hasText: '首条咨询消息' })).toBeVisible({ timeout: 20_000 });
    await expect(userPage.getByPlaceholder('请输入想咨询的问题')).toBeEnabled({ timeout: 15_000 });

    await loginAdminUi(adminPage);
    await adminPage.goto(join(ADMIN_URL, '/chat/workbench'));
    const tab = adminPage.locator('.el-tabs__item', { hasText: `#${sessionId}` }).first();
    await expect(tab).toBeVisible({ timeout: 25_000 });
    await expect(adminPage.locator('.chat-head__name')).toContainText('进行中');

    // 2) 客服结束：客户看到结束提示，客服 Tab 置灰但仍在
    await adminPage.getByRole('button', { name: '结束会话' }).first().click();
    await adminPage.locator('.el-message-box').getByRole('button', { name: '确认结束' }).click();
    await expect(userPage.getByTestId('chat-closed-banner'), '客户应看到本次会话已结束')
      .toContainText('本次会话已结束', { timeout: 20_000 });
    await expect(userPage.getByPlaceholder('本次会话已结束，请点击重新咨询')).toBeDisabled();
    await expect(tab, '结束后 Tab 不应自动消失').toContainText('已结束', { timeout: 20_000 });

    // 3) 小结必填：空的问题概述 / 解决办法被拒
    const invalid = await apiCall(request, 'PUT', `/admin/chat/sessions/${sessionId}/note`, {
      staffId: 9001, summary: '', category: 'INQUIRY', info: 'CLEAR', result: 'DONE', solution: '',
    }, adminToken);
    expect(invalid?.code).toBe(40001);

    await adminPage.getByTestId('note-summary').fill('客户咨询定制球衣');
    await adminPage.getByTestId('note-solution').fill('已报价并发送起订说明');
    await adminPage.getByTestId('note-save').click();
    await expect(adminPage.locator('.el-message', { hasText: '本次情况已保存' })).toBeVisible({ timeout: 10_000 });

    // 3b) 历史服务详情：大弹窗 + 与客服聊天同款气泡回放
    const historyRow = adminPage.locator('[data-testid="history-row"]',
      { hasText: `会话 #${priorId}` }).first();
    await expect(historyRow, '右侧历史服务应包含上一条已结束会话').toBeVisible({ timeout: 20_000 });
    await historyRow.click();
    const historyDialog = adminPage.locator('.history-replay-dialog');
    await expect(historyDialog).toBeVisible({ timeout: 15_000 });
    const dialogWidth = await historyDialog.locator('.el-dialog__body')
      .evaluate((el: HTMLElement) => el.getBoundingClientRect().width);
    expect(dialogWidth, '历史服务详情应使用宽屏大弹窗').toBeGreaterThan(900);
    await expect(historyDialog.getByTestId('history-transcript'))
      .toContainText('历史咨询消息', { timeout: 15_000 });
    await expect(historyDialog.locator('.bubble').first(), '消息应以聊天气泡呈现').toBeVisible();
    await expect(historyDialog.getByTestId('history-note')).toContainText('历史服务小结');
    await historyDialog.locator('.el-dialog__headerbtn').click();
    await expect(historyDialog).toHaveCount(0);

    // 4) 客服主动关闭 Tab 才消失
    await tab.locator('.is-icon-close').click();
    await expect(tab).toHaveCount(0, { timeout: 10_000 });

    // 5) 客户主动重新咨询 → 生成新会话，结束提示消失
    await userPage.getByTestId('chat-reopen').click();
    await expect(userPage.getByTestId('chat-closed-banner')).toHaveCount(0, { timeout: 20_000 });
    const reopened = await currentOpenSession(request, CUSTOMER_ID, userToken);
    expect(reopened, '重新咨询应建立新会话').toBeGreaterThan(0);
    expect(reopened).not.toBe(sessionId);
  } finally {
    await setStaffPresence(request, adminToken, [9001, 9003, 9004, 9005, 9006, 9007], 'OFFLINE');
    await userCtx.close();
    await adminCtx.close();
  }
});

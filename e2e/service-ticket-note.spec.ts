/**
 * 服务工单记录（V61，2026-11-25）：客服在 web-cs 填完整工单 → 主管在 web-admin 审查页并排看到工单。
 * 覆盖：必填 40005、客户基础信息只读带入、草稿自动保存、扩展字段展示。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  ADMIN_URL, CS_URL, join, apiCall, loginApi, loginCs, goOnline, clearOpenSessions,
  setStaffPresence, startConsultation, closeSessionWithTicket, ADMIN_NAME, ADMIN_PASSWORD,
  CHAT_MANAGER_NAME, CHAT_MANAGER_PASSWORD, CUSTOMER_ID,
} from './chat-helpers';

const TICKET = {
  summary: '客户反馈定制足球印字交期',
  category: 'INQUIRY',
  info: 'CLEAR',
  result: 'DONE',
  solution: '已核实订单 S001，确认 7 天交期并同步客户',
  subCategory: '印字Logo',
  customerDemand: '本周内到货',
  verifiedInfo: '订单 S001 / SKU F001',
  needFollowup: true,
  followupTime: '2026-11-26 10:30:00',
  /** 2026-11-26：回访联系方式由客服手填，不再默认带客户登录手机号 */
  followupContact: '13900002222',
  attachmentsJson: '["ticket-proof"]',
};

async function loginManager(page: Page): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(CHAT_MANAGER_NAME);
  await page.getByPlaceholder('密码').fill(CHAT_MANAGER_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

async function newConsultation(request: any, adminToken: string, userToken: string): Promise<number> {
  const created = await apiCall(request, 'POST', '/chat/sessions',
    { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  expect(sessionId, `建会话失败：${JSON.stringify(created)}`).toBeGreaterThan(0);
  await startConsultation(request, sessionId, userToken, TICKET.summary);
  const detail = await apiCall(request, 'GET', `/admin/chat/sessions/${sessionId}`, undefined, adminToken);
  if (detail?.data?.status === 'WAITING') {
    await apiCall(request, 'POST', `/admin/chat/sessions/${sessionId}/accept`,
      { staffId: 9001 }, adminToken);
  }
  return sessionId;
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('客服填写完整服务工单，主管审查页并排展示全部字段', async ({ browser, request }) => {
  test.setTimeout(240_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const userToken = await loginApi(request, '13800138000', '123456', 'user');
  await setStaffPresence(request, adminToken, [9001, 9003, 9004, 9005, 9006, 9007], 'OFFLINE');
  await clearOpenSessions(request, adminToken);
  await setStaffPresence(request, adminToken, [9001], 'ONLINE');
  const sessionId = await newConsultation(request, adminToken, userToken);

  const csCtx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const csPage = await csCtx.newPage();
  try {
    await loginCs(csPage);
    await goOnline(csPage);
    await expect(csPage.locator(`.session-item[data-session-id="${sessionId}"]`))
      .toBeVisible({ timeout: 20_000 });
    await csPage.locator(`.session-item[data-session-id="${sessionId}"]`).click();

    // 右侧直接内联完整工单：分类/明确信息状态/回访时间/附件全在右栏，右栏自己滚动
    const notePanel = csPage.getByTestId('session-note-panel');
    // 回访时间与联系方式改为「勾了需回访才出现」（V64 联动），所以不在这批常显字段里
    for (const id of ['note-inline-summary', 'note-inline-info', 'note-inline-sub-category',
      'note-inline-customer-demand', 'note-inline-verified-info', 'note-inline-solution',
      'note-inline-followup', 'note-attachment-upload']) {
      await expect(notePanel.getByTestId(id), `${id} 应内联在右栏`).toBeVisible();
    }
    // 未勾「需回访」时，回访时间与联系方式都不该占地方（否则客服会以为必须填）
    await expect(notePanel.getByTestId('note-inline-followup-time'), '未勾需回访时不显示回访时间')
      .toHaveCount(0);
    await expect(notePanel.getByTestId('note-inline-followup-phone'), '未勾需回访时不显示联系方式')
      .toHaveCount(0);
    await expect(notePanel.getByTestId('note-inline-save')).toBeVisible();
    await expect(notePanel.getByTestId('note-inline-submit')).toBeVisible();

    // 抽屉彻底取消：没有「放大查看」入口，也不存在第二个工单表单
    await expect(notePanel.getByTestId('open-note-drawer'), '不再有放大查看入口').toHaveCount(0);
    await expect(csPage.getByTestId('note-drawer'), '抽屉已移除').toHaveCount(0);

    // 五个必填项名称旁都有红色星号
    const required = notePanel.locator('.required');
    await expect(required).toHaveCount(5);
    await expect(required.first()).toHaveCSS('color', 'rgb(217, 96, 79)');

    // 右栏加宽适度：聊天区仍够用，但不能宽到把工单挤出屏
    const chatWidth = await csPage.locator('.chat-panel')
      .evaluate((el) => el.getBoundingClientRect().width);
    expect(chatWidth, `聊天区宽度 ${chatWidth}`).toBeGreaterThan(800);
    expect(chatWidth, `聊天区宽度 ${chatWidth}`).toBeLessThan(920);

    // 并发接待：切客户/切 Tab 前填一半，切回来内容还在（草稿自动保存）
    await notePanel.getByTestId('note-inline-category').click();
    await csPage.getByRole('option', { name: '商品咨询' }).click();
    await notePanel.getByTestId('note-inline-summary').fill(TICKET.summary);
    await notePanel.getByTestId('note-inline-solution').fill(TICKET.solution);
    await notePanel.getByTestId('note-inline-sub-category').fill(TICKET.subCategory);
    await notePanel.getByTestId('note-inline-customer-demand').fill(TICKET.customerDemand);
    await notePanel.getByTestId('note-inline-verified-info').fill(TICKET.verifiedInfo);
    await notePanel.getByTestId('note-inline-followup').click();
    // 勾上「需回访」后：回访时间与联系方式一起出现，但联系方式是空输入框，
    // 必须由客服手填（用户口径：不用客户登录号码，可能是同事/座机/微信）
    await expect(notePanel.getByTestId('note-inline-followup-time')).toBeVisible({ timeout: 10_000 });
    await expect(notePanel.getByTestId('note-inline-followup-phone')).toBeVisible();
    const contactInput = notePanel.getByTestId('note-inline-followup-phone').locator('input');
    await expect(contactInput, '回访联系方式不能默认带出客户手机号').toHaveValue('');
    await contactInput.fill(TICKET.followupContact);
    // 回访时间内联：直接输入 + 回车提交（和用户手填路径一致）
    const followupInput = notePanel.getByTestId('note-inline-followup-time').locator('input');
    await followupInput.fill(TICKET.followupTime);
    await followupInput.press('Enter');
    await csPage.getByTestId('record-tab-history').click();
    await expect(notePanel).toBeHidden();
    await csPage.getByTestId('record-tab-note').click();
    await expect(notePanel.getByTestId('note-inline-solution')).toHaveValue(TICKET.solution);
    await expect(notePanel.getByTestId('note-inline-sub-category')).toHaveValue(TICKET.subCategory);
    await expect(notePanel.getByTestId('note-inline-followup-phone').locator('input'))
      .toHaveValue(TICKET.followupContact);

    // 2026-11-26：先「暂时保存」落草稿（不进审查），再「提交工单」（才进审查）
    await notePanel.getByTestId('note-inline-save').click();
    await expect(csPage.locator('.el-message', { hasText: '草稿已保存' }).first())
      .toBeVisible({ timeout: 10_000 });
    await expect(notePanel.getByTestId('note-submit-status')).toHaveText('草稿未提交');

    const draft = await apiCall(request, 'GET', `/admin/chat/sessions/${sessionId}/note`, undefined, adminToken);
    expect(draft?.data?.submitStatus, '暂时保存必须是草稿').toBe('DRAFT');

    await notePanel.getByTestId('note-inline-submit').click();
    await expect(csPage.locator('.el-message', { hasText: '工单已提交' }).first())
      .toBeVisible({ timeout: 10_000 });
    await expect(notePanel.getByTestId('note-submit-status')).toHaveText('已提交');

    const stored = await apiCall(request, 'GET', `/admin/chat/sessions/${sessionId}/note`, undefined, adminToken);
    expect(stored?.code, `读取工单失败：${JSON.stringify(stored)}`).toBe(0);
    expect(stored?.data?.submitStatus).toBe('SUBMITTED');
    expect(stored?.data?.subCategory).toBe(TICKET.subCategory);
    expect(stored?.data?.customerDemand).toBe(TICKET.customerDemand);
    expect(stored?.data?.verifiedInfo).toBe(TICKET.verifiedInfo);
    expect(stored?.data?.needFollowup).toBe(true);
    expect(stored?.data?.followupContact, '回访联系方式按手填内容落库').toBe(TICKET.followupContact);
    expect(Number(stored?.data?.followupTimeMillis ?? 0), '回访时间应落库').toBeGreaterThan(0);
    expect(String(stored?.data?.customerPhone ?? '')).toContain('13800138000');
    expect(Number(stored?.data?.consultedAtMillis)).toBeGreaterThan(0);

    await closeSessionWithTicket(request, sessionId, adminToken);
  } finally {
    await csCtx.close();
  }

  // 主管审查：聊天记录与完整工单并排展示
  const auditCtx = await browser.newContext({ viewport: { width: 1680, height: 1050 } });
  const auditPage = await auditCtx.newPage();
  try {
    await loginManager(auditPage);
    await auditPage.goto(join(ADMIN_URL, '/chat/audit'));
    await auditPage.getByPlaceholder('用户 ID / 会话号 / 问题概述').fill(String(sessionId));
    const targetRow = auditPage.locator('tbody tr', { hasText: String(sessionId) }).first();
    await expect(targetRow).toBeVisible({ timeout: 20_000 });
    await targetRow.getByTestId('audit-detail-btn').click();
    const note = auditPage.getByTestId('audit-note');
    await expect(auditPage.getByTestId('audit-transcript')).toBeVisible();
    await expect(note).toContainText(TICKET.summary);
    await expect(note).toContainText(TICKET.solution);
    await expect(note).toContainText(TICKET.subCategory);
    await expect(note).toContainText(TICKET.customerDemand);
    await expect(note).toContainText(TICKET.verifiedInfo);
    await expect(note).toContainText('客户昵称');
    await expect(note).toContainText('联系方式');
    await expect(note).toContainText('咨询时间');
    await expect(note).toContainText('回访联系方式');
    await expect(note, '审查页要展示客服手填的回访方式').toContainText(TICKET.followupContact);
  } finally {
    await auditCtx.close();
  }
});

/**
 * 2026-11-26 用户口径：
 *   ① 结束会话不再强制工单（交流完就可以结束，工单可以事后补）；
 *   ② 但只有「提交」的工单才进管理端客服审查，草稿/未填不出现；
 *   ③ 提交后发现问题可以改了再提交，覆盖同一会话号的记录。
 */
test('未提交工单也能结束会话，提交后才进客服审查且可重复提交覆盖', async ({ request }) => {
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const userToken = await loginApi(request, '13800138000', '123456', 'user');
  await setStaffPresence(request, adminToken, [9001, 9003, 9004, 9005, 9006, 9007], 'OFFLINE');
  await clearOpenSessions(request, adminToken);
  await setStaffPresence(request, adminToken, [9001], 'ONLINE');
  const sessionId = await newConsultation(request, adminToken, userToken);

  // 提交时必填仍校验（暂时保存不校验）
  const blank = await apiCall(request, 'PUT', `/admin/chat/sessions/${sessionId}/note`, {
    staffId: 9001, summary: '咨询', category: 'INQUIRY', info: 'CLEAR', result: 'DONE',
    solution: '  ', submit: true,
  }, adminToken);
  expect(blank?.code, '提交时空解决办法必须返回 40005').toBe(40005);

  const draft = await apiCall(request, 'PUT', `/admin/chat/sessions/${sessionId}/note`, {
    staffId: 9001, summary: '咨询', category: 'INQUIRY', info: 'CLEAR', result: 'DONE',
    solution: '  ', submit: false,
  }, adminToken);
  expect(draft?.code, '暂时保存允许缺必填').toBe(0);
  expect(draft?.data?.submitStatus).toBe('DRAFT');

  // 没提交工单也能结束会话
  const closed = await apiCall(request, 'POST', `/admin/chat/sessions/${sessionId}/close`,
    { reason: 'DONE' }, adminToken);
  expect(closed?.code, '没有提交工单也能结束会话').toBe(0);

  // 草稿不进审查
  const before = await apiCall(request, 'GET',
    `/admin/chat/audit/sessions?page=1&size=100&keyword=${sessionId}`, undefined, adminToken);
  const beforeIds = (before?.data?.list ?? []).map((r: any) => Number(r.sessionId));
  expect(beforeIds, '草稿工单不该出现在客服审查').not.toContain(sessionId);

  // 提交后出现
  const submitted = await apiCall(request, 'PUT', `/admin/chat/sessions/${sessionId}/note`, {
    staffId: 9001, summary: '第一次提交', category: 'INQUIRY', info: 'CLEAR', result: 'DONE',
    solution: '已按标准流程处理完成', submit: true,
  }, adminToken);
  expect(submitted?.code).toBe(0);
  const afterSubmit = await apiCall(request, 'GET',
    `/admin/chat/audit/sessions?page=1&size=100&keyword=${sessionId}`, undefined, adminToken);
  const rows = (afterSubmit?.data?.list ?? []).filter((r: any) => Number(r.sessionId) === sessionId);
  expect(rows.length, '提交后应出现在客服审查').toBe(1);
  expect(rows[0].summary).toBe('第一次提交');

  // 再次提交（会话未关闭也允许）：覆盖同一会话号的记录，不新增行
  const resubmitted = await apiCall(request, 'PUT', `/admin/chat/sessions/${sessionId}/note`, {
    staffId: 9001, summary: '第二次修正', category: 'INQUIRY', info: 'CLEAR', result: 'DONE',
    solution: '修正后的解决办法', submit: true,
  }, adminToken);
  expect(resubmitted?.code).toBe(0);
  const afterResubmit = await apiCall(request, 'GET',
    `/admin/chat/audit/sessions?page=1&size=100&keyword=${sessionId}`, undefined, adminToken);
  const finalRows = (afterResubmit?.data?.list ?? []).filter((r: any) => Number(r.sessionId) === sessionId);
  expect(finalRows.length, '同一会话只能有一条审查记录').toBe(1);
  expect(finalRows[0].summary, '重新提交应覆盖原内容').toBe('第二次修正');
});

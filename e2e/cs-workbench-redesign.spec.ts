/**
 * 客服工作台界面重构（2026-11-25 用户诉求，TDD 红→绿）。
 *
 * 诉求：
 *   1) 左侧删掉「排队中」列表（在线即自动接待），只剩「进行中」；
 *   2) 中间顶部 Tab 条删除，切换会话走左侧列表，列表项显示**进线客户的头像与姓名**；
 *   3) 会话结束后聊天区显示「本次会话已结束」，左侧保留一条「已结束」；
 *   4) 输入框左侧三个工具：图片 / 表情 / 请评价；
 *   5) 右侧「本次情况 / 历史服务 / 顾客订单」改成平级 Tab，选中项变色。
 *
 * 断言口径以用户视角为准：看得到什么、点了什么会变什么，不绑内部实现。
 */
import { test, expect } from '@playwright/test';
import {
  ADMIN_NAME, ADMIN_PASSWORD, CS_URL,
  apiCall, clearOpenSessions, fillMinimalTicket, join, loginApi, loginCs,
  setStaffPresence, startConsultation,
} from './chat-helpers';

/** 演示环境唯一的坐席（登录账号 admin 的工号） */
const STAFF_ID = 9001;
const CUSTOMER_ID = 9002;

test('工作台：无排队列表、客户姓名头像、三工具、服务记录三 Tab', async ({ browser, request }) => {
  test.setTimeout(180_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin', `e2e-redesign-a-${Date.now()}`);
  const userToken = await loginApi(request, '13800138000', '123456', 'user', `e2e-redesign-u-${Date.now()}`);

  await setStaffPresence(request, adminToken, [STAFF_ID], 'OFFLINE');
  await clearOpenSessions(request, adminToken);
  await setStaffPresence(request, adminToken, [STAFF_ID], 'ONLINE');

  // 客户进线（在线坐席未满 3 个 → 后端自动分配成 ACTIVE，不需要人工「接待」）
  const created = await apiCall(request, 'POST', '/chat/sessions', { customerId: CUSTOMER_ID, vip: false }, userToken);
  expect(created?.code, `建会话失败：${JSON.stringify(created)}`).toBe(0);
  const sessionId = Number(created.data.sessionId);
  expect(Number(created.data.staffId), '在线坐席应被自动接待').toBe(STAFF_ID);
  await startConsultation(request, sessionId, userToken);

  const open = await apiCall(request, 'GET', `/admin/chat/sessions/open?staffId=${STAFF_ID}`, undefined, adminToken);
  const row = (open?.data?.active ?? []).find((r: any) => Number(r.sessionId) === sessionId);
  expect(row, '会话应在进行中列表里').toBeTruthy();
  const expectName = String(row.customerName ?? '').trim();
  expect(expectName.length, '接口应带出进线客户昵称').toBeGreaterThan(0);

  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await ctx.newPage();
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !/Failed to load resource|net::ERR/i.test(m.text())) errors.push(m.text());
  });

  try {
    await loginCs(page, ADMIN_NAME, ADMIN_PASSWORD);
    await page.goto(join(CS_URL, '/workbench'));
    await expect(page.locator('.el-tag', { hasText: 'WebSocket 已连接' })).toBeVisible({ timeout: 20_000 });

    // 1) 左侧没有排队列表；进行中列表显示进线客户的姓名（不是「客户 9002」）
    await expect(page.locator('[data-testid="queue-panel"]'), '排队列表已删除').toHaveCount(0);
    const activePanel = page.locator('[data-testid="active-panel"]');
    // 演示库里可能残留历史会话，按会话号精确定位本次这条，别用 .first()
    const ourRow = activePanel.locator('.session-item', { hasText: `会话 #${sessionId}` }).first();
    await expect(ourRow, '左侧进行中应出现本次会话').toBeVisible({ timeout: 20_000 });
    await expect(ourRow.locator('[data-testid="session-customer-name"]'), '列表项应显示进线客户姓名')
      .toHaveText(expectName);
    // 头像兜底：无头像文件时显示姓名首字
    await expect(ourRow.locator('.s-avatar')).toBeVisible();
    await ourRow.click();

    // 2) 中间没有顶部 Tab 条；聊天区直接显示该会话，头部是客户姓名
    await expect(page.locator('.el-tabs'), '顶部会话 Tab 条已删除').toHaveCount(0);
    const panel = page.locator(`.chat-panel[data-session-id="${sessionId}"]`);
    await expect(panel).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('[data-testid="chat-customer-name"]')).toContainText(expectName);
    await expect(page.locator('.chat-head__sub')).toContainText(`会话 #${sessionId}`);

    // 3) 输入区三工具：图片 → 表情 → 请评价（DOM 顺序即用户看到的顺序）
    const tools = page.locator('.send-row__tools .tool-btn');
    await expect(tools).toHaveCount(3);
    await expect(tools.nth(0)).toHaveAttribute('data-testid', 'send-image');
    await expect(tools.nth(1)).toHaveAttribute('data-testid', 'emoji-toggle');
    await expect(tools.nth(2)).toHaveAttribute('data-testid', 'send-satisfaction');
    await expect(tools.nth(0)).toContainText('图片');
    await expect(tools.nth(1)).toContainText('表情');
    await expect(tools.nth(2)).toContainText('请评价');

    // 4) 表情面板：点开 → 点一个 → 插进输入框
    const input = page.getByPlaceholder('输入消息后回车发送');
    await page.getByTestId('emoji-toggle').click();
    const emojiPanel = page.getByTestId('emoji-panel');
    await expect(emojiPanel, '点「表情」应弹出常用表情面板').toBeVisible();
    const firstEmoji = (await emojiPanel.locator('[data-testid="emoji-item"]').first().innerText()).trim();
    await emojiPanel.locator('[data-testid="emoji-item"]').first().click();
    await expect(input, '点表情应插入输入框').toHaveValue(firstEmoji);

    // 5) 右侧服务记录三个平级 Tab：默认本次情况，切换后各自显示且选中项变色
    const notePanel = page.getByTestId('session-note-panel');
    const historyPanel = page.getByTestId('history-panel');
    const ordersPanel = page.getByTestId('orders-panel');
    const noteTab = page.getByTestId('record-tab-note');
    const historyTab = page.getByTestId('record-tab-history');
    const ordersTab = page.getByTestId('record-tab-orders');

    await expect(noteTab).toHaveClass(/record-tab--active/);
    await expect(notePanel).toBeVisible();
    await expect(historyPanel).toBeHidden();
    await expect(ordersPanel).toBeHidden();

    await historyTab.click();
    await expect(historyTab).toHaveClass(/record-tab--active/);
    await expect(noteTab).not.toHaveClass(/record-tab--active/);
    await expect(historyPanel).toBeVisible();
    await expect(notePanel).toBeHidden();

    await ordersTab.click();
    await expect(ordersTab).toHaveClass(/record-tab--active/);
    await expect(ordersPanel).toBeVisible();
    await expect(historyPanel).toBeHidden();

    // 6) 结束会话：先切回「本次情况」补最小工单过 V61 门禁，再看结束横幅与左侧「已结束」
    await page.getByTestId('record-tab-note').click();
    await fillMinimalTicket(page);
    await page.getByTestId('close-session').click();
    await page.locator('.el-message-box').getByRole('button', { name: '确认结束' }).click();
    await expect(page.getByTestId('chat-ended-banner'), '聊天区应显示本次会话已结束').toBeVisible({ timeout: 20_000 });
    const closedPanel = page.getByTestId('closed-panel');
    await expect(closedPanel, '左侧应保留已结束会话').toContainText(`会话 #${sessionId}`);
    await expect(page.locator('[data-testid="send-btn"]'), '结束后发送按钮应禁用').toBeDisabled();

    // 填完本次情况后手动关闭该行，聊天区回到空态
    await closedPanel.getByTestId('close-ended').first().click();
    await expect(page.locator('.chat-panel')).toHaveCount(0);
    await expect(page.locator('.chat-empty')).toBeVisible();

    expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
  } finally {
    await clearOpenSessions(request, adminToken).catch(() => undefined);
    await setStaffPresence(request, adminToken, [STAFF_ID], 'OFFLINE').catch(() => undefined);
    await ctx.close();
  }
});

/**
 * 智能客服闭环：机器人辅助接待 + 无缝转人工（2026-11-24，TDD 红→绿）。
 *
 * 口径（用户原话）：
 *   1) 客户进线先看到「智能助手小森」的欢迎语；
 *   2) 发「发货」→ 机器人按知识库秒回物流内容，不打扰坐席；
 *   3) 发「转人工」→ 机器人回「正在为您转接人工客服，请稍候...」；
 *   4) 人工客服 9003 接入 → 聊天区正中出「人工客服 9003 为您服务」的灰色系统提示。
 *
 * 渲染口径（2026-11-24 二次改造）：机器人（senderType=BOT）说的话必须长成「正常会话气泡 + 机器人头像」，
 * 只有 SYSTEM 旁白（已接入 / 已结束）才是居中灰字。以前两者共用 .chat-system，客户分不清谁在回答。
 *
 * 与既有用例的边界：只走商城端 UI 建会话（带 sessionType=BOT）；API 直接建的会话仍是 HUMAN，
 * 人工客服分配链路（chat-realtime / chat-order-panel 等）行为不变。
 */
import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import {
  API_URL, ADMIN_NAME, ADMIN_PASSWORD, CUSTOMER_ID, CUSTOMER_PASSWORD, CUSTOMER_PHONE, USER_URL,
  apiCall, clearOpenSessions, currentOpenSession, join, loginApi, setStaffPresence,
  closeSessionWithTicket,
} from './chat-helpers';

const STAFF_IDS = [9001, 9003, 9004, 9005, 9006, 9007];

async function loginUser(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('机器人迎宾 → 知识库应答 → 转人工 → 人工接入全链路', async ({ browser, request }) => {
  test.setTimeout(180_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const userToken = await loginApi(request, CUSTOMER_PHONE, CUSTOMER_PASSWORD, 'user');

  // 清场：全员离线 + 关掉该客户所有未结束会话，保证本次是「全新进线」
  await setStaffPresence(request, adminToken, STAFF_IDS, 'OFFLINE');
  await clearOpenSessions(request, adminToken);

  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !/Failed to load resource|net::ERR/i.test(m.text())) errors.push(m.text());
  });

  let sessionId = 0;
  try {
    await loginUser(page);
    await page.goto(join(USER_URL, '/chat'));
    await expect(page.locator('.chat-input')).toBeVisible({ timeout: 20_000 });

    // ---------- 1) 机器人迎宾（同款气泡：头像 + 名字，不是灰字旁白） ----------
    await expect(page.getByTestId('bot-welcome'), '进线应先看到智能助手小森的欢迎语')
      .toContainText('智能助手小森', { timeout: 20_000 });
    await expect(page.getByTestId('bot-welcome')).toContainText('请问有什么可以帮您');
    await expect(page.getByTestId('bot-welcome').locator('.avatar--bot'),
      '机器人迎宾要用机器人头像，和人工客服的「客服」头区分开').toBeVisible();

    // ---------- 2) 知识库秒回 ----------
    const box = page.getByPlaceholder('请输入想咨询的问题');
    await expect(box).toBeEnabled({ timeout: 15_000 });
    await box.fill('什么时候发货');
    await box.press('Enter');

    const botReply = page.getByTestId('chat-bot-msg').filter({ hasText: '顺丰' });
    await expect(botReply, '发「发货」应命中知识库并秒回物流话术').toBeVisible({ timeout: 20_000 });
    await expect(botReply).toContainText('48小时内发货');
    // 机器人回复必须是正常会话气泡（带机器人头像），不能再退回居中灰字
    await expect(botReply.locator('.avatar--bot'),
      '机器人回复要按正常会话格式发送，头像是机器人').toBeVisible();
    await expect(page.locator('.chat-system', { hasText: '顺丰' }),
      '机器人回复不能渲染成 SYSTEM 居中提示').toHaveCount(0);

    // 机器人答完仍在机器人模式：转人工入口还在
    await expect(page.getByTestId('chat-transfer-human')).toBeVisible();

    // ---------- 3) 转人工 ----------
    await page.getByTestId('chat-transfer-human').click();
    const transferTip = page.getByTestId('chat-bot-msg').filter({ hasText: '正在为您转接人工客服' });
    await expect(transferTip, '转人工应给出明确的转接提示').toBeVisible({ timeout: 20_000 });
    await expect(transferTip.locator('.avatar--bot'),
      '转接提示是机器人说的话，同样按机器人气泡渲染').toBeVisible();

    // 后端状态机：session_type 已从 BOT 切到 HUMAN，会话进排队
    sessionId = (await currentOpenSession(request, CUSTOMER_ID, userToken)) ?? 0;
    expect(sessionId, '转人工后应存在进行中的会话').toBeGreaterThan(0);
    const current = await apiCall(request, 'GET',
      `/chat/sessions/current?customerId=${CUSTOMER_ID}`, undefined, userToken);
    expect(current?.data?.sessionType, '转人工后接待模式必须是 HUMAN').toBe('HUMAN');
    expect(current?.data?.status, '转人工后应回到排队中').toBe('WAITING');

    // 转人工后快捷入口收起（已经不是机器人接待了）
    await expect(page.getByTestId('chat-transfer-human')).toHaveCount(0);

    // ---------- 4) 人工客服 9003 接入 ----------
    // 9003 切在线会触发坐席补位：排队中的会话直接被派给他（ACTIVE），此时再调 accept 会撞
    // STATE_CONFLICT（40901）。所以先看真实状态，只有还停在 WAITING 才手动接入。
    await setStaffPresence(request, adminToken, [9003], 'ONLINE');
    const adminState = async (): Promise<string> => {
      const listed = await apiCall(request, 'GET', '/admin/chat/sessions?page=1&size=50', undefined, adminToken);
      const row = (listed?.data?.list ?? []).find((x: any) => Number(x.sessionId) === sessionId);
      return `${row?.status ?? ''}|${row?.staffId ?? ''}`;
    };
    let state = '';
    for (let i = 0; i < 20; i += 1) {
      state = await adminState();
      if (state.startsWith('ACTIVE')) break;
      await page.waitForTimeout(500);
    }
    if (!state.startsWith('ACTIVE')) {
      const accepted = await apiCall(request, 'POST', `/admin/chat/sessions/${sessionId}/accept`,
        { staffId: 9003 }, adminToken);
      expect(accepted?.code, `人工接入失败：${JSON.stringify(accepted)}`).toBe(0);
    }
    // 无论走自动补位还是手动接入，最终都必须落在「9003 接待中」
    for (let i = 0; i < 20; i += 1) {
      state = await adminState();
      if (state === 'ACTIVE|9003') break;
      await page.waitForTimeout(300);
    }
    expect(state, '会话应被 9003 接待').toBe('ACTIVE|9003');

    const assignedTip = page.locator('.chat-system', { hasText: '人工客服 9003 为您服务' });
    await expect(assignedTip, '人工接入后应出现「人工客服 9003 为您服务」系统气泡')
      .toBeVisible({ timeout: 20_000 });

    // 系统提示是居中灰底样式，不能渲染成左右气泡
    await expect(page.locator('.chat-msg .chat-system')).toHaveCount(0,
      { timeout: 5_000 });

    expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
  } finally {
    // 中途失败也要把会话关掉：sessionId 只在第 3 步之后才拿到，
    // 前面任何一步抛错都补查一次，别把 WAITING 会话留给后面的用例。
    if (sessionId <= 0) {
      sessionId = (await currentOpenSession(request, CUSTOMER_ID, userToken).catch(() => null)) ?? 0;
    }
    if (sessionId > 0) {
      await closeSessionWithTicket(request, Number(sessionId), adminToken);
    }
    await setStaffPresence(request, adminToken, [9003], 'OFFLINE');
    await ctx.close();
  }
});

/**
 * 客服发券 + 回访联动（V64，2026-11-25 用户诉求）：
 *   1) 右侧服务记录新增「优惠券发放」tab；
 *   2) 客服单张最高 20 元（服务端硬拦截，不是前端藏起来）；
 *   3) 发放后直接进客户账户并发站内信；
 *   4) 工单里只有勾了「需回访」才出现回访时间与客户手机号。
 *
 * 会写库（customer_coupon / user_message），用例结束按券号清掉，保证可重复执行。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type APIRequestContext } from '@playwright/test';
import {
  ADMIN_NAME, ADMIN_PASSWORD, CS_URL, CUSTOMER_ID, apiCall, clearOpenSessions,
  closeSessionWithTicket, join, loginApi, loginCs, setStaffPresence, startConsultation,
} from './chat-helpers';

const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';
/** 满80减50券：面额 50 元，用来验证客服发券的 20 元上限 */
const OVER_CAP_TEMPLATE_ID = 22;

function mysqlBin(): string {
  if (process.env.E2E_MYSQL_BIN) return process.env.E2E_MYSQL_BIN;
  return existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql';
}

function sql(statement: string): string {
  return execFileSync(mysqlBin(), [
    '-h127.0.0.1', '-P3306', '-uroot', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
    'sent_dev', '-N', '-B', '-e', statement,
  ], { encoding: 'utf8' }).trim();
}

/** 按券号清掉本次发放的券与对应站内信（失败不改变结论） */
function cleanupCoupons(couponNos: string[]): void {
  try {
    // 先用券号精确清；再用「本次用例专用原因前缀」兜底清一次——
    // 用例中途失败时拿不到券号，只靠券号会留下脏记录，下一轮记录列表里就会多出别人的券
    const byReason = sql(
      `SELECT coupon_no FROM customer_coupon WHERE customer_id = ${CUSTOMER_ID} `
      + "AND reason LIKE 'E2E 安抚补偿%';");
    const all = [...couponNos, ...byReason.split('\n').map((s) => s.trim()).filter(Boolean)];
    if (!all.length) return;
    const list = all.map((no) => `'${no}'`).join(',');
    sql(`DELETE FROM customer_coupon WHERE coupon_no IN (${list});`);
    sql(`DELETE FROM user_message WHERE biz_no IN (${list});`);
  } catch {
    // 无本机 mysql 时跳过清场
  }
}

async function recordsOf(request: APIRequestContext, token: string): Promise<any[]> {
  const body = await apiCall(request, 'GET', `/admin/chat/customers/${CUSTOMER_ID}/coupons?limit=10`,
    undefined, token);
  expect(body?.code, `读发券记录失败：${JSON.stringify(body)}`).toBe(0);
  return body?.data ?? [];
}

test('客服发券：20 元上限硬拦截、发放即到账、记录可查', async ({ browser, request }) => {
  test.setTimeout(300_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const userToken = await loginApi(request, '13800138000', '123456', 'user');
  const issuedNos: string[] = [];
  let sessionId = 0;

  try {
    /* ---------------- 接口层：可选档位与越权拦截 ---------------- */
    const options = await apiCall(request, 'GET', '/admin/chat/coupons/options', undefined, adminToken);
    expect(options?.code, `读可发券档位失败：${JSON.stringify(options)}`).toBe(0);
    const templates: any[] = options?.data ?? [];
    // 2026-11-26 用户口径：客服工作台只有 5 档，且全部无门槛
    expect(templates.length, '客服工作台固定 5 档补偿券').toBe(5);
    expect(templates.map((t) => Number(t.amountCent)).sort((a, b) => a - b))
      .toEqual([500, 800, 1000, 1500, 2000]);
    for (const t of templates) {
      expect(Number(t.thresholdCent), `「${t.name}」必须无门槛`).toBe(0);
    }

    // 运营券不能从客服通道发：这一步证明档位不是靠前端隐藏
    const overCap = await apiCall(request, 'POST', `/admin/chat/customers/${CUSTOMER_ID}/coupons`,
      { templateId: OVER_CAP_TEMPLATE_ID, reason: 'E2E 越界测试' }, adminToken);
    expect(overCap?.code, `运营券不该能从客服通道发出：${JSON.stringify(overCap)}`).not.toBe(0);
    expect(String(overCap?.message ?? '')).toContain('客服补偿券');

    // 用后端给的第一个档位做 UI 操作，不写死券名（演示库券名会随运营改）
    const target = templates[0];

    /* ---------------- 客服端：发券 + 记录 + 回访联动 ---------------- */
    await setStaffPresence(request, adminToken, [9001, 9003, 9004, 9005, 9006, 9007], 'OFFLINE');
    await clearOpenSessions(request, adminToken);
    await setStaffPresence(request, adminToken, [9001], 'ONLINE');
    const created = await apiCall(request, 'POST', '/chat/sessions',
      { customerId: CUSTOMER_ID, vip: false }, userToken);
    sessionId = Number(created?.data?.sessionId);
    expect(sessionId).toBeGreaterThan(0);
    await startConsultation(request, sessionId, userToken, '我这边体验不太好');

    const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
    const page = await ctx.newPage();
    try {
      await loginCs(page);
      await page.goto(join(CS_URL, '/workbench'));
      await expect(page.locator(`.session-item[data-session-id="${sessionId}"]`))
        .toBeVisible({ timeout: 20_000 });
      await page.locator(`.session-item[data-session-id="${sessionId}"]`).click();
      await expect(page.locator('.chat-panel')).toBeVisible({ timeout: 20_000 });

      // 1) 第 4 个 tab 就叫「优惠券发放」
      const couponTab = page.getByTestId('record-tab-coupon');
      await expect(couponTab).toContainText('优惠券发放');
      await couponTab.click();
      const panel = page.getByTestId('coupon-panel');
      await expect(panel).toBeVisible();

      // 2) 下拉只有 5 档无门槛券，全部 ≤20 元
      await page.getByTestId('gift-template').click();
      const optionTexts = await page.locator('.el-select-dropdown__item:visible').allInnerTexts();
      expect(optionTexts.length, '客服工作台固定 5 档').toBe(5);
      for (const text of optionTexts) {
        const amount = Number((text.match(/¥([\d.]+)/) ?? [])[1] ?? '0');
        expect(amount, `「${text}」超过 20 元不该出现在客服下拉里`).toBeLessThanOrEqual(20);
      }
      await page.locator('.el-select-dropdown__item:visible', { hasText: target.name }).first().click();

      const reason = `E2E 安抚补偿 ${Date.now()}`;
      await page.getByTestId('gift-reason').fill(reason);
      await page.getByTestId('gift-submit').click();
      await expect(page.locator('.el-message', { hasText: '已发放' }).first())
        .toBeVisible({ timeout: 20_000 });

      // 3) 发放记录立刻出现，带来源「客服补偿」与原因
      const row = page.getByTestId('gift-row').first();
      await expect(row).toBeVisible({ timeout: 15_000 });
      await expect(row).toContainText(target.name);
      await expect(row).toContainText(reason);
      await expect(row).toContainText('客服补偿');

      // 4) 落库口径：source=AFTER_SALE、reason 原样、券已到该客户账户
      const stored = await recordsOf(request, adminToken);
      const top = stored[0];
      expect(top, '应能读到刚发的券').toBeTruthy();
      expect(top.name).toBe(target.name);
      expect(top.source).toBe('AFTER_SALE');
      expect(top.reason).toBe(reason);
      expect(String(top.couponNo)).toMatch(/^CPN/);
      issuedNos.push(String(top.couponNo));

      // 5) 站内信：客户收到一条 PROMO 提醒（不依赖 UI，直接查库最稳）
      const inboxCount = Number(sql(
        `SELECT COUNT(*) FROM user_message WHERE biz_no = '${top.couponNo}';`));
      expect(inboxCount, '发放必须同时给客户发站内信').toBeGreaterThan(0);

      // 6) 回访联动：未勾需回访时时间与联系方式都不出现，勾上后一起出现；
      //    联系方式是空输入框，由客服手填（2026-11-26 不再默认带客户手机号）
      const notePanel = page.getByTestId('session-note-panel');
      await page.getByTestId('record-tab-note').click();
      await expect(notePanel.getByTestId('note-inline-followup-time')).toHaveCount(0);
      await notePanel.getByTestId('note-inline-followup').click();
      await expect(notePanel.getByTestId('note-inline-followup-time')).toBeVisible({ timeout: 10_000 });
      const phone = notePanel.getByTestId('note-inline-followup-phone');
      await expect(phone).toBeVisible();
      await expect(phone.locator('input'), '回访联系方式需客服手填').toHaveValue('');
      // 取消勾选：时间选择器收起，避免带着上一次的时间被误保存
      await notePanel.getByTestId('note-inline-followup').click();
      await expect(notePanel.getByTestId('note-inline-followup-time')).toHaveCount(0);
    } finally {
      await ctx.close();
    }
  } finally {
    cleanupCoupons(issuedNos);
    // 会话必须收尾：留着 ACTIVE 会话会长期占用 9001 的并发名额（上限 3），
    // 后面的 full-flow 会因为这个「僵尸会话」拿不到接待位而红（2026-11-25 全量跑踩过）
    if (sessionId > 0) {
      await closeSessionWithTicket(request, sessionId, adminToken).catch(() => undefined);
    }
    await clearOpenSessions(request, adminToken).catch(() => undefined);
    await setStaffPresence(request, adminToken, [9001], 'OFFLINE').catch(() => undefined);
  }
});

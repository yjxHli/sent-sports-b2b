/**
 * 商城端客服咨询界面（对齐「淘小蜜」参考图，TDD 红→绿）。
 *
 * 口径：
 *   1) 输入区四个入口顺序固定：订单 → 上传图片 → 拍照（占位）→ 表情；
 *   2) 订单入口 / 「发订单给客服」都打开同一个双 Tab 弹窗，点遮罩可关闭；
 *   3) 顶部「点击查看历史咨询记录」与右上角「服务进度」各自打开弹窗；
 *   4) 不改变既有搜索框/聊天输入框 DOM 与 data-testid。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(process.env.E2E_USER_NAME ?? '13800138000');
  await page.getByPlaceholder('请输入密码').fill(process.env.E2E_USER_PASSWORD ?? '123456');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test.beforeEach(async ({ page }) => {
  await loginUser(page);
  await page.goto(`${USER_URL}/chat`);
  await expect(page.locator('.chat-input')).toBeVisible({ timeout: 20_000 });
});

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('输入区四个入口顺序：订单 / 上传图片 / 拍照 / 表情', async ({ page }) => {
  const tools = page.locator('.chat-input__tools .tool');
  await expect(tools).toHaveCount(4);
  const ids = await tools.evaluateAll((els) => els.map((el) => el.getAttribute('data-testid')));
  expect(ids).toEqual(['chat-tool-order', 'chat-tool-image', 'chat-tool-camera', 'chat-tool-emoji']);

  // 拍照暂不实现，但必须给出明确提示，不能静默无响应
  await page.getByTestId('chat-tool-camera').click();
  await expect(page.getByText('拍照功能即将上线')).toBeVisible({ timeout: 10_000 });

  // 表情：点开后能选一个塞进输入框
  await page.getByTestId('chat-tool-emoji').click();
  const panel = page.getByTestId('chat-emoji-panel');
  await expect(panel).toBeVisible();
  await panel.locator('button').first().click();
  await expect(page.locator('.chat-input__field input').first()).not.toHaveValue('');
});

test('订单弹窗：双 Tab、可搜索、点遮罩关闭；发订单给客服入口打开同一个弹窗', async ({ page }) => {
  await page.getByTestId('chat-tool-order').click();
  const dialog = page.getByTestId('chat-order-dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId('order-tab-order')).toHaveClass(/is-active/);
  await expect(page.getByTestId('order-tab-other')).toBeVisible();
  await expect(dialog.locator('[data-testid="order-dialog-row"]').first()).toBeVisible({ timeout: 15_000 });

  // 切到非订单问题：展示 FAQ 列表
  await page.getByTestId('order-tab-other').click();
  await expect(page.getByTestId('faq-item').first()).toBeVisible();
  await expect(page.getByTestId('faq-item').filter({ hasText: '发票' })).toBeVisible();

  // 点弹窗以外任意位置关闭
  await dialog.click({ position: { x: 4, y: 4 } });
  await expect(dialog).toHaveCount(0);

  // 「发订单给客服」横幅打开同一个弹窗
  await page.getByTestId('chat-send-order-banner').click();
  await expect(page.getByTestId('chat-order-dialog')).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId('order-tab-order')).toHaveClass(/is-active/);
});

test('顶部历史咨询记录：打开弹窗并展示历史会话', async ({ page }) => {
  await page.getByTestId('chat-history-link').click();
  const dialog = page.getByTestId('chat-history-dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await expect(dialog).toContainText('历史咨询记录');
  await expect(dialog.locator('[data-testid="history-session-row"]').first()).toBeVisible({ timeout: 15_000 });
});

test('右上角服务进度：展示当前服务与历史服务进度', async ({ page }) => {
  await page.getByTestId('chat-service-progress').click();
  const dialog = page.getByTestId('chat-progress-dialog');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await expect(dialog).toContainText('服务进度');
  await expect(dialog).toContainText('历史服务进度');
});

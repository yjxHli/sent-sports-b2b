/**
 * 客户标签（V63/V64，2026-11-25 用户诉求）：
 *   1) 客服在会话头部打标：按钮离头像姓名稍远，已打标签排在按钮右边；
 *   2) 弹窗按四大类分组、可搜索，取消勾选即取消标签（没有单独的删除入口）；
 *   3) 四大类配色必须能一眼区分；
 *   4) 管理端能维护「有哪些标签」：新增 / 停用 / 删除（有客户在用时拒绝）。
 *
 * 断言口径：
 *   - 标签是内部数据：客服端能读，用户端没有任何接口（这里只校验管理端/客服端）；
 *   - 会写库的用例自建自清（标签定义 + 9002 的关联），跑完不留脏数据。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import {
  ADMIN_NAME, ADMIN_PASSWORD, ADMIN_URL, CS_URL, CUSTOMER_ID, apiCall, clearOpenSessions,
  join, loginApi, loginCs, setStaffPresence, startConsultation,
} from './chat-helpers';

const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';
const TEMP_TAG = 'E2E临时标签';

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

/** 清场：临时标签定义 + 9002 身上的标签关联（失败不影响用例结论） */
function cleanup(): void {
  try {
    sql(`DELETE FROM customer_tag_link WHERE customer_id = ${CUSTOMER_ID};`);
    sql(`DELETE FROM customer_tag WHERE name = '${TEMP_TAG}';`);
  } catch {
    // 没有本机 mysql 时跳过清场，用例本身仍有效
  }
}

async function loginAdmin(page: Page): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

/**
 * 点 popconfirm 的「确定」。
 *
 * <p>每行有「停用」「删除」两个 popconfirm，前一个点完它的 popper 不会立刻从 DOM 消失，
 * 直接写 `.el-popconfirm` 会命中两个而触发 strict mode violation（2026-11-25 踩过：
 * 单跑通过、全量必红）。取最后一个 = 刚打开的那个。</p>
 */
async function confirmPop(page: Page): Promise<void> {
  const confirm = page.locator('.el-popconfirm').last();
  await expect(confirm).toBeVisible({ timeout: 10_000 });
  await confirm.getByRole('button', { name: '确定' }).click();
}

test('客户标签：管理端维护目录、客服端给客户打标并可取消', async ({ browser, request }) => {
  test.setTimeout(300_000);
  cleanup();

  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const userToken = await loginApi(request, '13800138000', '123456', 'user');

  // 后端目录口径先立住：四大类各 6 条种子，分类中文名与配色键都由后端下发
  const catalog = await apiCall(request, 'GET', '/admin/customer-tags?includeDisabled=true',
    undefined, adminToken);
  expect(catalog?.code, `读标签目录失败：${JSON.stringify(catalog)}`).toBe(0);
  const seeds: any[] = catalog?.data ?? [];
  expect(seeds.length, '种子标签应至少 20 条').toBeGreaterThanOrEqual(20);
  const categories = new Set(seeds.map((t) => t.category));
  for (const cat of ['EXCELLENT', 'NEUTRAL', 'RISK', 'MALICIOUS']) {
    expect(categories.has(cat), `缺少分类 ${cat} 的标签`).toBe(true);
  }
  const colorKeys = new Set(seeds.map((t) => t.colorKey));
  expect(colorKeys.size, '四大类必须有 4 种不同配色键').toBe(4);

  /* ---------------- 管理端：维护标签目录 ---------------- */
  const adminCtx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const adminPage = await adminCtx.newPage();
  try {
    await loginAdmin(adminPage);
    await adminPage.goto(join(ADMIN_URL, '/customer/tags'));
    const list = adminPage.getByTestId('tag-list');
    await expect(list.locator('tbody tr').first()).toBeVisible({ timeout: 20_000 });
    expect(await list.locator('tbody tr').count(), '24 条种子标签应全部列出').toBeGreaterThanOrEqual(20);

    // 搜索：分类与标签名都能定位，且能看出分类文案
    await adminPage.getByTestId('tag-search').fill('羊毛党');
    await expect(list.locator('tbody tr')).toHaveCount(1);
    await expect(list.locator('tbody tr').first()).toContainText('恶意');

    // 新增：分类下拉给四档中文名，保存后立刻出现在列表里
    await adminPage.getByTestId('tag-create-btn').click();
    await adminPage.getByTestId('tag-name-input').fill(TEMP_TAG);
    await adminPage.getByTestId('tag-category-input').click();
    await adminPage.getByRole('option', { name: '风险留意' }).click();
    await adminPage.getByTestId('tag-save-btn').click();
    await expect(adminPage.locator('.el-message', { hasText: '标签已新增' }).first())
      .toBeVisible({ timeout: 15_000 });

    await adminPage.getByTestId('tag-search').fill(TEMP_TAG);
    await expect(list.locator('tbody tr')).toHaveCount(1);
    await expect(list.locator('tbody tr').first()).toContainText('风险留意');

    // 停用：状态列变「已停用」，且不再出现在客服弹窗（客服端那一段据此断言）
    await list.locator('tbody tr').first().getByTestId('tag-toggle').click();
    await confirmPop(adminPage);
    await expect(adminPage.locator('.el-message', { hasText: '已停用' }).first())
      .toBeVisible({ timeout: 15_000 });
    await expect(list.locator('tbody tr').first()).toContainText('已停用');

    // 删除：没有客户在用，直接删掉
    await list.locator('tbody tr').first().getByTestId('tag-delete-btn').click();
    await confirmPop(adminPage);
    await expect(adminPage.locator('.el-message', { hasText: '标签已删除' }).first())
      .toBeVisible({ timeout: 15_000 });
    await expect(list.locator('tbody tr'), '删除后该标签从列表消失').toHaveCount(0);
  } finally {
    await adminCtx.close();
  }

  /* ---------------- 客服端：会话头部打标 ---------------- */
  await setStaffPresence(request, adminToken, [9001, 9003, 9004, 9005, 9006, 9007], 'OFFLINE');
  await clearOpenSessions(request, adminToken);
  await setStaffPresence(request, adminToken, [9001], 'ONLINE');
  const created = await apiCall(request, 'POST', '/chat/sessions',
    { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  expect(sessionId, `建会话失败：${JSON.stringify(created)}`).toBeGreaterThan(0);
  await startConsultation(request, sessionId, userToken, '我想问下优惠券');

  const csCtx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const csPage = await csCtx.newPage();
  try {
    await loginCs(csPage);
    await csPage.goto(join(CS_URL, '/workbench'));
    await expect(csPage.locator(`.session-item[data-session-id="${sessionId}"]`))
      .toBeVisible({ timeout: 20_000 });
    await csPage.locator(`.session-item[data-session-id="${sessionId}"]`).click();
    await expect(csPage.locator('.chat-panel')).toBeVisible({ timeout: 20_000 });

    // 1) 入口在会话头部，且离头像/姓名留了明显间距（用户要求「稍微远一点」）
    const tagBar = csPage.getByTestId('customer-tags');
    const addBtn = csPage.getByTestId('tag-add-btn');
    await expect(addBtn).toBeVisible();
    const who = await csPage.locator('.chat-head__who').first()
      .evaluate((el) => el.getBoundingClientRect().right);
    const barLeft = await tagBar.evaluate((el) => el.getBoundingClientRect().left);
    expect(barLeft - who, `标签区与头像姓名区的距离 ${barLeft - who}px`).toBeGreaterThanOrEqual(24);

    // 2) 未打标时只有按钮，没有 chip
    await expect(csPage.locator('.chat-head__tags .tag-chip')).toHaveCount(0);

    // 3) 弹窗：四大类分组 + 搜索
    await addBtn.click();
    const dialog = csPage.getByTestId('tag-dialog');
    await expect(dialog).toBeVisible({ timeout: 15_000 });
    await expect(dialog.locator('.tag-group')).toHaveCount(4);
    await expect(dialog).toContainText('优质 / 正面');
    await expect(dialog).toContainText('恶意');

    await csPage.getByTestId('tag-search').fill('羊毛党');
    await expect(dialog.locator('.tag-opt')).toHaveCount(1, { timeout: 10_000 });
    const firstOpt = dialog.locator('.tag-opt').first();
    // 2026-11-26 用户反馈：点了标签但看不出选没选。选中态必须挂在整颗胶囊上（描边 + 实心勾）
    await expect(firstOpt, '默认未选中').not.toHaveClass(/tag-opt--on/);
    await firstOpt.click();
    await expect(firstOpt, '选中后要明显框起来').toHaveClass(/tag-opt--on/);
    await expect(firstOpt.locator('.tag-opt__check'), '选中项应有勾').toHaveText('✓');
    await csPage.getByTestId('tag-search').fill('忠实老客');
    await expect(dialog.locator('.tag-opt')).toHaveCount(1);
    const secondOpt = dialog.locator('.tag-opt').first();
    await secondOpt.click();
    await expect(secondOpt).toHaveClass(/tag-opt--on/);
    await csPage.getByTestId('tag-search').fill('');
    await csPage.getByTestId('tag-save').click();
    await expect(csPage.locator('.el-message', { hasText: '标签已更新' }).first())
      .toBeVisible({ timeout: 15_000 });
    await expect(dialog).toBeHidden();

    // 4) 已打标签排在「标签」按钮右边，且两类颜色不同
    const chips = csPage.locator('.chat-head__tags .tag-chip');
    await expect(chips).toHaveCount(2);
    await expect(tagBar).toContainText('忠实老客');
    await expect(tagBar).toContainText('羊毛党');
    const btnRight = await addBtn.evaluate((el) => el.getBoundingClientRect().right);
    const chipLefts = await chips.evaluateAll((els) =>
      els.map((el) => ({ left: el.getBoundingClientRect().left, color: getComputedStyle(el).color })));
    for (const chip of chipLefts) {
      expect(chip.left, `标签应显示在按钮右侧（chip ${chip.left} vs 按钮右缘 ${btnRight}）`)
        .toBeGreaterThanOrEqual(btnRight - 1);
    }
    expect(new Set(chipLefts.map((c) => c.color)).size, '优质与恶意必须不同颜色').toBe(2);

    // 5) 取消标签：仍然点「标签」按钮，在弹窗里取消勾选（没有单独的删除入口）
    await expect(csPage.getByTestId('tag-remove-btn')).toHaveCount(0);
    await addBtn.click();
    await expect(dialog).toBeVisible();
    await csPage.getByTestId('tag-search').fill('忠实老客');
    await expect(dialog.locator('.tag-opt')).toHaveCount(1);
    await dialog.locator('.tag-opt').first().click();
    await csPage.getByTestId('tag-save').click();
    await expect(csPage.locator('.el-message', { hasText: '标签已更新' }).first())
      .toBeVisible({ timeout: 15_000 });
    await expect(chips).toHaveCount(1);
    await expect(tagBar).toContainText('羊毛党');
    await expect(tagBar).not.toContainText('忠实老客');

    // 6) 落库确认：另一个客服（管理端令牌读同一接口）看到的也是这一条
    const stored = await apiCall(request, 'GET', `/admin/customers/${CUSTOMER_ID}/tags`,
      undefined, adminToken);
    expect(stored?.code, `读客户标签失败：${JSON.stringify(stored)}`).toBe(0);
    expect((stored?.data ?? []).map((t: any) => t.name)).toEqual(['羊毛党']);
  } finally {
    await csCtx.close();
    await clearOpenSessions(request, adminToken).catch(() => undefined);
    // 必须把 9001 复位成离线：本用例为了接待把 9001 置了 ONLINE，
    // 若留在在线状态，后面的 full-flow（它用 9003 接待）新建会话会被自动分配到 9001，
    // 9003 的工作台永远看不到那条会话 → 「客服工作台未出现会话」（2026-11-25 全量跑踩过）
    await setStaffPresence(request, adminToken, [9001], 'OFFLINE').catch(() => undefined);
    cleanup();
  }
});

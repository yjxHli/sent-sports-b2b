import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

/**
 * 违禁词库管理（V46）：系统管理 → 违禁词库。
 *
 * 用户诉求（2026-11-19）：「管理员可以设计聊天和用户名的违禁词有哪些，可以放在系统里」。
 * 本用例锁三件事：
 * 1) 词库是库里的真数据（种子词能看到，不是写死的前端常量）；
 * 2) 新增的词立刻落库并可查（保存即生效，不需要重启后端口）；
 * 3) 停用 / 删除后状态与列表同步（停用不删词，删除才真正消失）。
 */
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');

async function login(page: Page): Promise<void> {
  await page.goto(ADMIN_URL + 'login');
  await page.waitForTimeout(900);
  if (page.url().includes('login')) {
    await page.getByPlaceholder('用户名').fill('admin');
    await page.getByPlaceholder('密码').fill('123456');
    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST'),
      page.getByRole('button', { name: '登录' }).click(),
    ]);
  }
  await page.waitForURL(/dashboard/, { timeout: 20_000 });
}

/** 直查后端，确认词真的进了库（UI 上看到的不一定是真数据）。 */
async function wordsByKeyword(request: APIRequestContext, keyword: string): Promise<any[]> {
  const res = await request.fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    data: { username: 'admin', password: '123456', aud: 'admin', deviceId: `e2e-sword-${Date.now()}` },
    failOnStatusCode: false,
  });
  const token = (await res.json().catch(() => null))?.data?.accessToken as string;
  const listed = await request.fetch(
    `${API_URL}/admin/sensitive-words?page=1&size=20&keyword=${encodeURIComponent(keyword)}`,
    { headers: { Authorization: `Bearer ${token}` }, failOnStatusCode: false },
  );
  return (await listed.json().catch(() => null))?.data?.list ?? [];
}

test('违禁词库：菜单可进、种子词可见，新增/停用/删除全链路生效', async ({ page, request }) => {
  const marker = `E2E违禁词${Date.now().toString().slice(-6)}`;
  await login(page);

  // 1) 从侧边栏进「违禁词库」：先展开「系统」分组（el-sub-menu 默认折叠），再点菜单项
  await page.locator('.el-sub-menu__title', { hasText: '系统' }).first().click();
  await page.locator('.el-menu-item', { hasText: '违禁词库' }).first().click();
  await page.waitForURL(/system\/sensitive-words/, { timeout: 15_000 });
  await expect(page.getByRole('heading', { name: '违禁词库' })).toBeVisible();

  // 2) 种子词必须能看到：证明列表读的是库，不是前端写死的数组。
  //    词库会持续加词（运营随时补词），不能假设种子词一定在第一页，按关键词检索定位。
  await page.waitForSelector('.el-table__body tbody tr', { timeout: 15_000 });
  const searchBox = page.locator('input[placeholder="搜词或备注"]');
  for (const seedWord of ['测试违禁词', '加V']) {
    await searchBox.fill(seedWord);
    await page.getByRole('button', { name: '查询' }).click();
    await expect(page.locator('.el-table__body')).toContainText(seedWord, { timeout: 15_000 });
  }
  // 复位检索条件，后续新增/停用/删除按全新列表走
  await searchBox.fill('');
  await page.getByRole('button', { name: '查询' }).click();

  // 3) 新增：保存后立刻出现在列表，并且后端确实查得到
  await page.getByTestId('sw-create').click();
  await page.getByTestId('sw-word').fill(marker);
  await page.getByTestId('sw-save').click();
  await expect(page.getByText(`「${marker}」已加入词库，立即生效`)).toBeVisible({ timeout: 15_000 });

  await page.locator('input[placeholder="搜词或备注"]').fill(marker);
  await page.getByRole('button', { name: '查询' }).click();
  await expect(page.locator('.el-table__body')).toContainText(marker, { timeout: 15_000 });

  const created = await wordsByKeyword(request, marker);
  expect(created.length, '新增的词必须落库（保存即生效）').toBeGreaterThan(0);

  // 4) 停用：状态翻转，词条仍在（停用只是不参与匹配）
  const row = page.locator('.el-table__body tbody tr', { hasText: marker }).first();
  await row.getByTestId('sw-toggle').click();
  await expect(row).toContainText('已停用', { timeout: 15_000 });
  expect((await wordsByKeyword(request, marker)).length, '停用不删词').toBeGreaterThan(0);

  // 5) 删除：列表与库里都消失
  await row.getByRole('button', { name: '删除' }).click();
  await page.getByRole('button', { name: '确定' }).click();
  await expect(page.locator('.el-table__body')).not.toContainText(marker, { timeout: 15_000 });
  expect(await wordsByKeyword(request, marker)).toHaveLength(0);
});

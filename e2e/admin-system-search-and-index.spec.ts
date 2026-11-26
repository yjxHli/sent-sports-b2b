/**
 * 系统页增强（2026-11-26 用户需求，TDD 先红后绿）：
 *   1) 员工管理支持按员工号 / 姓名 / 手机号搜索，并支持按状态筛选；
 *   2) 系统配置中心保留序号；违禁词库取消序号，改为按分类 + 首字拼音排序（2026-11-26 调整）。
 *
 * 员工搜索用例自建员工、临时置为 DISABLED，验完删除并物理清理；不依赖演示库既有状态。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { apiCall, loginApi, ADMIN_NAME, ADMIN_PASSWORD } from './chat-helpers';

const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';

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

async function loginAdmin(page: Page): Promise<void> {
  await page.goto(ADMIN_URL + 'login');
  await page.waitForTimeout(700);
  if (page.url().includes('login')) {
    await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
    await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST'),
      page.getByRole('button', { name: /登\s*录/ }).click(),
    ]);
  }
  await page.waitForURL(/dashboard/, { timeout: 20_000 });
}

async function headerTexts(page: Page): Promise<string[]> {
  return page.$$eval('.el-table__header-wrapper th .cell', (cells) =>
    (cells as HTMLElement[]).map((cell) => cell.innerText.trim()));
}

async function bodyCellTexts(page: Page, colIndex: number): Promise<string[]> {
  return page.$$eval(
    `.el-table__body-wrapper tbody tr td:nth-child(${colIndex}) .cell`,
    (cells) => (cells as HTMLElement[]).map((cell) => cell.innerText.trim()),
  );
}

async function waitStaffList(page: Page, action: () => Promise<void>): Promise<void> {
  await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api/admin/staff') && r.request().method() === 'GET',
      { timeout: 15_000 },
    ),
    action(),
  ]);
  await page.waitForTimeout(400);
}

test.describe.configure({ mode: 'serial' });

test('员工管理：员工号 / 姓名 / 手机号与状态筛选都走服务端', async ({ page, request }) => {
  const token = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const suffix = Date.now().toString().slice(-8);
  const staffNo = `E2EEMP${suffix}`;
  const name = `搜索员工${suffix}`;
  const phone = `139${suffix}`;
  let staffId = 0;

  try {
    const created = await apiCall(request, 'POST', '/admin/staff', {
      username: staffNo,
      name,
      phone,
      roleCode: 'SALES',
    }, token);
    expect(created?.code, `创建临时员工失败：${JSON.stringify(created)}`).toBe(0);
    staffId = Number(created?.data?.staffId);
    expect(staffId, '创建员工应返回员工 ID').toBeGreaterThan(0);
    sql(`UPDATE sys_staff SET status = 'DISABLED' WHERE id = ${staffId};`);

    await loginAdmin(page);
    await page.goto(ADMIN_URL + 'system/staff');
    await expect(page.getByRole('heading', { name: '员工管理' })).toBeVisible({ timeout: 15_000 });
    const keyword = page.getByTestId('staff-keyword');
    const statusFilter = page.getByTestId('staff-status-filter');
    await expect(keyword, '员工管理必须有关键词搜索框').toBeVisible({ timeout: 10_000 });
    await expect(statusFilter, '员工管理必须有状态筛选下拉').toBeVisible({ timeout: 10_000 });

    const body = page.locator('.el-table__body-wrapper tbody');
    for (const term of [staffNo, name, phone]) {
      await waitStaffList(page, async () => {
        await keyword.fill(term);
        await page.getByRole('button', { name: '查询' }).first().click();
      });
      const usernames = await bodyCellTexts(page, 2);
      const names = await bodyCellTexts(page, 3);
      const phones = await bodyCellTexts(page, 4);
      expect(usernames.length, `关键词「${term}」应命中临时员工`).toBe(1);
      expect(usernames[0]).toContain(staffNo);
      expect(names[0]).toContain(name);
      expect(phones[0]).toContain(phone);
    }

    // 状态筛选：清空关键词后只看「已停用」，临时员工必须在结果里且不能混入启用中的行
    await keyword.fill('');
    await waitStaffList(page, async () => {
      await statusFilter.locator('.el-select').click();
      await page.getByRole('option', { name: '已停用' }).click();
      await page.getByRole('button', { name: '查询' }).first().click();
    });
    await expect(body).toContainText(staffNo, { timeout: 10_000 });
    const statuses = await bodyCellTexts(page, 6);
    expect(statuses.length).toBeGreaterThan(0);
    expect(statuses.every((text) => text.includes('已停用')), `状态列应全为已停用：${statuses.join(',')}`).toBe(true);

    // 反向验证：切到「启用中」后临时员工不应出现
    await waitStaffList(page, async () => {
      await statusFilter.locator('.el-select').click();
      await page.getByRole('option', { name: '启用中' }).click();
      await page.getByRole('button', { name: '查询' }).first().click();
    });
    await expect(body).not.toContainText(staffNo, { timeout: 10_000 });
  } finally {
    if (staffId) {
      try {
        await apiCall(request, 'DELETE', `/admin/staff/${staffId}`, undefined, token);
      } catch {
        // 兜底物理清理仍会执行
      }
      try {
        sql(`DELETE FROM sys_staff WHERE id = ${staffId};`);
      } catch {
        // 没有本机 mysql 时不阻塞用例结论
      }
    }
  }
});

test('系统配置中心：表格第一列是序号（违禁词库已按要求取消）', async ({ page }) => {
  await loginAdmin(page);

  await page.goto(ADMIN_URL + 'system/config');
  await expect(page.getByRole('heading', { name: '系统配置中心' })).toBeVisible({ timeout: 15_000 });
  await page.waitForSelector('.el-table__body-wrapper tbody tr', { timeout: 15_000 });
  expect((await headerTexts(page))[0], '系统配置中心第一列应为序号').toBe('序号');
  const configIndex = (await bodyCellTexts(page, 1))[0] ?? '';
  expect(/^\d+$/.test(configIndex), `序号应是数字，实际为「${configIndex}」`).toBe(true);

  await page.goto(ADMIN_URL + 'system/sensitive-words');
  await expect(page.getByRole('heading', { name: '违禁词库' })).toBeVisible({ timeout: 15_000 });
  await page.waitForSelector('.el-table__body-wrapper tbody tr', { timeout: 15_000 });
  const wordHeaders = (await headerTexts(page)).map((h) => h.trim());
  expect(wordHeaders[0], '违禁词库第一列应是违禁词').toBe('违禁词');
  expect(wordHeaders, '违禁词库不应再有序号列').not.toContain('序号');
});

test('违禁词库：按分类再按词条首字拼音顺序排列', async ({ page }) => {
  await loginAdmin(page);
  await page.goto(ADMIN_URL + 'system/sensitive-words');
  await page.waitForSelector('.el-table__body-wrapper tbody tr', { timeout: 15_000 });

  // 列：违禁词 / 分类 / 状态 / 备注 / 更新时间（序号已取消）
  const rows = await page.locator('.el-table__body-wrapper tbody tr').evaluateAll((trs) =>
    trs.map((tr) => Array.from(tr.querySelectorAll('td .cell'))
      .map((cell) => (cell.textContent ?? '').trim())));
  const categoryOrder = ['辱骂脏话', '广告导流', '违法违禁', '其他'];
  const collator = new Intl.Collator('zh-CN');

  expect(rows.length).toBeGreaterThan(1);
  for (let i = 1; i < rows.length; i += 1) {
    const prev = rows[i - 1];
    const curr = rows[i];
    const prevCat = categoryOrder.indexOf(prev[1]);
    const currCat = categoryOrder.indexOf(curr[1]);
    expect(prevCat, `未知分类：${prev[1]}`).toBeGreaterThanOrEqual(0);
    expect(currCat, `未知分类：${curr[1]}`).toBeGreaterThanOrEqual(0);
    expect(prevCat, `分类顺序错误：${prev[1]} → ${curr[1]}`).toBeLessThanOrEqual(currCat);
    if (prevCat === currCat) {
      expect(collator.compare(prev[0].charAt(0), curr[0].charAt(0)),
        `首字拼音顺序错误：${prev[0]} → ${curr[0]}`).toBeLessThanOrEqual(0);
    }
  }
});

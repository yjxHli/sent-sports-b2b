import { defineConfig, devices } from '@playwright/test';

/**
 * 森特体育用品管理系统 · Playwright 配置
 *
 * 默认假定三端服务已经启动（推荐手动启动，见下方命令）；设置 E2E_AUTOSTART=1 时
 * 由 Playwright 的 webServer 自行拉起后端 + 两个 Vite dev server。
 *
 *   # 1) 后端（本机 MySQL 8.0.43 / sent_dev，root/123456）
 *   java -jar server/target/sent-server.jar --spring.datasource.password=123456   # 默认 :8088
 *   # 2) 用户端 / 管理端
 *   pnpm --dir web-user dev
 *   pnpm --dir web-admin dev
 *   # 3) 执行
 *   cd e2e && npx playwright test
 *
 * 双端 token 物理隔离（sent_user / sent_admin），因此用两个独立 BrowserContext，
 * 绝不共用 storageState。
 */

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';
const AUTO_START = process.env.E2E_AUTOSTART === '1';

export default defineConfig({
  testDir: '.',
  testMatch: '**/*.spec.ts',
  timeout: 120_000,
  expect: { timeout: 12_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['json', { outputFile: 'playwright-report/results.json' }],
  ],
  use: {
    baseURL: USER_URL,
    // 默认复用系统 Chrome/Edge，避免 CI/离线环境下载 Chromium；
    // 设置 E2E_CHANNEL=msedge 可切到 Edge，设置为空则使用 Playwright 自带 Chromium。
    channel: (process.env.E2E_CHANNEL ?? 'chrome') as 'chrome' | 'msedge' | undefined,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: AUTO_START
    ? [
        {
          command:
            'java -jar server/target/sent-server.jar --spring.datasource.password=123456',
          cwd: '..',
          url: 'http://127.0.0.1:8088/v3/api-docs',
          reuseExistingServer: true,
          timeout: 180_000,
        },
        {
          command: 'pnpm --dir web-user dev',
          cwd: '..',
          url: `${USER_URL}/`,
          reuseExistingServer: true,
          timeout: 120_000,
        },
        {
          command: 'pnpm --dir web-admin dev',
          cwd: '..',
          url: `${ADMIN_URL}`,
          reuseExistingServer: true,
          timeout: 120_000,
        },
      ]
    : undefined,
});

// 供 spec 复用的常量（避免三处硬编码）
export const E2E_URLS = { USER_URL, ADMIN_URL, API_URL };

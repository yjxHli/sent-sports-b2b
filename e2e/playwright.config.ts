import { defineConfig, devices } from '@playwright/test';

/**
 * 森特体育用品管理系统 · Playwright 配置
 *
 * 默认假定三端服务已经启动（推荐手动启动，见下方命令）；设置 E2E_AUTOSTART=1 时
 * 由 Playwright 的 webServer 自行拉起后端 + 两个 Vite dev server。
 *
 *   # 1) 后端（本机 MySQL 8.0.43 / sent_dev，root/123456）
 *   java -jar server/target/sent-server.jar --spring.datasource.password=123456   # 默认 :8088
 *   # 2) 用户端 / 管理端 / 客服台
 *   pnpm --dir web-user dev
 *   pnpm --dir web-admin dev
 *   pnpm --dir web-cs dev      # 客服工作台（:5175）——接待类用例跑在这里
 *   # 3) 执行（`npx playwright test` 在本机会静默卡死，统一走 cli.js / npm script）
 *   cd e2e
 *   npm run test:fast      # 本地快验：只跑 parallel 桶，4 worker（~5 分钟）
 *   npm run test:session   # 定向：客服/坐席桶（1 worker）
 *   npm run test:cart      # 定向：购物车/结算桶（1 worker）
 *   npm run test:full      # 全量门禁：单进程 + workers=1（最稳，~17 分钟）
 *   npm run test:buckets   # 全量门禁的另一条路：三桶顺序跑（不并发）
 *
 * 三端 token 物理隔离（sent_user / sent_admin / cs-token），因此各用独立 BrowserContext，
 * 绝不共用 storageState；客服台用例的 base 是 CS_URL，只有「商城」用例才用 USER_URL。
 */

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
/**
 * 客服工作台独立端（web-cs）。2026-11-20 从管理端剥离后，
 * 原先跑在 /admin/chat/workbench 的 14 个接待类 spec 全部迁移到本 base。
 */
const CS_URL = process.env.E2E_CS_URL ?? 'http://127.0.0.1:5175';
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';
const AUTO_START = process.env.E2E_AUTOSTART === '1';

/**
 * 并行度与「分桶」（2026-11-25 引入，2026-11-26 定为**不并发**）。
 *
 * 一个演示库（`sent_dev`）撑着整套用例，能不能并行取决于**写的是不是同一份数据**：
 *
 * | 桶 | 内容 | 默认并行度 | 为什么 |
 * |---|---|---|---|
 * | `parallel` | 列表/详情/报表/管理端查询/独立写 | 4 worker | 文件之间互不相干 |
 * | `shared-session` | 客服会话、坐席、站内信、话术(机器人应答)、库存定时任务 | 1 worker | 同一时刻只允许一条活跃会话 / 一个在线坐席 |
 * | `shared-cart` | 购物车/结算/支付/评价/定制单/秒杀改价/客户资料 | 1 worker | 共用演示客户 9002 的购物车、同一批 SKU 与价格 |
 *
 * **默认全量不再并发**（多个 Playwright 进程同时跑会互删产物、抢库存、抢会话，得不偿失）：
 *   npm run test:full   # 单进程、全量、workers=1 —— 发布门禁口径（~17 分钟，最稳）
 *   npm run test:fast   # 只跑 parallel 桶，4 worker —— 本地改动后的快验（~5 分钟）
 *   npm run test:session / test:cart   # 定向跑某个写库桶（1 worker）
 *   npm run test:buckets # 三桶顺序跑（分桶编排，排查用；不并发）
 *
 * `npx playwright test` 在本机会静默卡死，统一走 cli.js / npm script。
 *
 * 并行单位是**用例文件**（`fullyParallel: false`）：同一 spec 内部永远按书写顺序跑，
 * 再叠加各 spec 顶部的 `test.describe.configure({ mode: 'serial' })`（一失败后续跳过），
 * 不会出现「同一文件的两个用例争同一个购物车/券/发票」。
 *
 * `globalTeardown` 会把坐席置 OFFLINE 并关掉所有未结束会话；如果将来又要多进程并发，
 * 只能由**客服桶**负责收尾，其它桶用 `E2E_SKIP_TEARDOWN=1` 关掉。
 */
const WORKERS = Number(process.env.E2E_WORKERS ?? 4);

/**
 * 产物目录必须可分桶（2026-11-25）。
 * Playwright 每次启动会**清空** `outputDir`，多进程并发时共用 `test-results/` 会互相删
 * trace/video，跑出来一堆 `ENOENT ... playwright-artifacts-*` 假故障。
 * `run-e2e.mjs` 会给每个桶设 `E2E_OUTPUT_DIR` / `E2E_REPORT_DIR`。
 */
const OUTPUT_DIR = process.env.E2E_OUTPUT_DIR ?? 'test-results';
const REPORT_DIR = process.env.E2E_REPORT_DIR ?? 'playwright-report';

/** 客服会话 / 坐席 / 站内信 / 定时任务：全局单例，只能单 worker */
const SESSION_SPECS = [
  'chat-*.spec.ts',
  'cs-*.spec.ts',
  'satisfaction-*.spec.ts',
  'smart-bot.spec.ts',
  'customer-tags.spec.ts', // 会切坐席 ONLINE/OFFLINE
  'customer-operations.spec.ts',
  'service-ticket-note.spec.ts', // 工单依附会话
  'full-flow.spec.ts', // 下单 + 客服接待全链路
  'messages-inbox.spec.ts',
  'message-badge-realtime.spec.ts', // 与上者共用演示账号收件箱
  'invoice-notifications.spec.ts', // 开票站内信：也会写客户 9002 的收件箱
  'order-timeout-autocancel.spec.ts', // 定时任务释放库存
  'faq-config.spec.ts', // 话术要经「机器人应答」验证，会占用客服会话
];

/** 购物车 / 结算 / 支付 / 评价写：共用 9002 的购物车与同一批 SKU，彼此必须串行 */
const CART_SPECS = [
  'checkout-address.spec.ts',
  'checkout-pay-return.spec.ts',
  'checkout-order-detail-redirect.spec.ts',
  'point-deduct-ui-flow.spec.ts',
  'pay-later-countdown.spec.ts',
  'order-detail-actions.spec.ts',
  'order-list-tabs.spec.ts', // 依赖「待付款」真实单据，必须排在下单类之后
  'product-review.spec.ts', // 写评价，会打歪「无评价商品」这类只读断言
  'admin-review-manage.spec.ts',
  'profile-edit.spec.ts', // 改演示客户 9002 的昵称，会和客服端改客户资料互相覆盖
  // 定制单：都要走「下单 → 图稿上传/审核 → 退款」，共用同一批定制 SKU 与退款单，必须串行
  'custom-draft-image.spec.ts',
  'custom-refund-policy.spec.ts',
  'custom-order-detail.spec.ts',
  'e2e-core-flow.spec.ts',
  // 秒杀会真实改价，和价格/秒杀断言互相打歪（历史坑：残留活跃秒杀把「10~40 元」断言打红）
  'flash-sale.spec.ts',
  'price-repricing.spec.ts',
];

export default defineConfig({
  testDir: '.',
  testMatch: '**/*.spec.ts',
  timeout: 120_000,
  expect: { timeout: 12_000 },
  // 并行单位 = 文件；文件内部保持书写顺序（配合各 spec 的 serial 声明）
  fullyParallel: false,
  workers: WORKERS,
  retries: 0,
  outputDir: OUTPUT_DIR,
  // 收尾复位客服在线状态并结束残留会话（多进程并发时只让客服桶执行，见 run-e2e.mjs）
  globalTeardown: process.env.E2E_SKIP_TEARDOWN === '1' ? undefined : './global-teardown.ts',
  projects: [
    {
      name: 'parallel',
      testIgnore: [...SESSION_SPECS, ...CART_SPECS],
    },
    {
      // 单 worker：`--project=shared-session --workers=1`
      name: 'shared-session',
      testMatch: SESSION_SPECS,
    },
    {
      // 单 worker：`--project=shared-cart --workers=1`
      name: 'shared-cart',
      testMatch: CART_SPECS,
    },
  ],
  reporter: [
    ['list'],
    ['html', { outputFolder: REPORT_DIR, open: 'never' }],
    ['json', { outputFile: `${REPORT_DIR}/results.json` }],
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
        {
          // 客服工作台独立端：接待类用例（chat-*/satisfaction-*）依赖它
          command: 'pnpm --dir web-cs dev',
          cwd: '..',
          url: `${CS_URL}/`,
          reuseExistingServer: true,
          timeout: 120_000,
        },
      ]
    : undefined,
});

// 供 spec 复用的常量（避免多处硬编码）
export const E2E_URLS = { USER_URL, ADMIN_URL, CS_URL, API_URL };

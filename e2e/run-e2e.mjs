#!/usr/bin/env node
/**
 * E2E 分桶执行器（2026-11-25 引入，2026-11-26 改为**默认顺序执行**）。
 *
 * 整套用例跑在同一个演示库上，桶与桶之间会抢库存/会话/产物目录，所以默认**一个桶跑完再跑下一个**：
 *   parallel       4 worker   只读/独立写（列表、详情、报表、管理端查询）
 *   shared-session 1 worker   客服会话 / 坐席 / 站内信 / 话术(机器人) / 库存定时任务
 *   shared-cart    1 worker   购物车 / 结算 / 支付 / 评价 / 定制单 / 秒杀改价 / 客户资料
 *
 * 用法：
 *   node run-e2e.mjs                        # 三桶顺序（默认）
 *   node run-e2e.mjs --only parallel,shared-cart
 *   node run-e2e.mjs --parallel             # 实验：三桶并发（会抢数据，仅排查用）
 *
 * 顺序模式下每个桶自己跑一次 `globalTeardown`（清会话/复位坐席），阶段之间天然干净。
 * 若用 `--parallel`，只有 shared-session 桶负责收尾，其它桶带 `E2E_SKIP_TEARDOWN=1`。
 */
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import process from 'node:process';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CLI = path.join(HERE, 'node_modules', '@playwright', 'test', 'cli.js');

const CONCURRENT = process.argv.includes('--parallel');
const onlyArg = process.argv.find((a) => a.startsWith('--only'));
const only = onlyArg
  ? (onlyArg.includes('=')
      ? onlyArg.split('=')[1]
      : process.argv[process.argv.indexOf(onlyArg) + 1] ?? ''
    )
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
  : null;

const BUCKETS = [
  { name: 'parallel', args: ['--project=parallel'] },
  { name: 'shared-session', args: ['--project=shared-session', '--workers=1'] },
  { name: 'shared-cart', args: ['--project=shared-cart', '--workers=1'] },
];

const targets = only ? BUCKETS.filter((b) => only.includes(b.name)) : BUCKETS;
if (targets.length === 0) {
  console.error(`没有匹配的桶，可选：${BUCKETS.map((b) => b.name).join(' / ')}`);
  process.exit(1);
}

function runBucket(bucket) {
  return new Promise((resolve) => {
    const started = Date.now();
    const env = { ...process.env };
    // 并发模式下收尾只能由一个桶负责；顺序模式每个桶都自己收尾，阶段之间更干净
    if (CONCURRENT && bucket.name !== 'shared-session') env.E2E_SKIP_TEARDOWN = '1';
    else delete env.E2E_SKIP_TEARDOWN;
    // 每个桶独立产物目录：Playwright 启动会清空 outputDir，共用会互相删 trace/video
    env.E2E_OUTPUT_DIR = path.join('test-results', bucket.name);
    env.E2E_REPORT_DIR = path.join('playwright-report', bucket.name);

    const child = spawn(process.execPath, [CLI, 'test', ...bucket.args, '--reporter=list'], {
      cwd: HERE,
      env,
      stdio: 'inherit',
    });
    child.on('close', (code) => {
      resolve({
        name: bucket.name,
        ok: code === 0,
        seconds: Math.round((Date.now() - started) / 10) / 100,
      });
    });
  });
}

async function main() {
  const started = Date.now();
  console.log(
    `▶ E2E ${targets.length} 桶${CONCURRENT ? '并发（实验）' : '顺序'}执行：` +
      `${targets.map((b) => b.name).join(' → ')}\n`,
  );

  const results = [];
  if (CONCURRENT) {
    results.push(...(await Promise.all(targets.map(runBucket))));
  } else {
    for (const bucket of targets) results.push(await runBucket(bucket));
  }

  const total = Math.round((Date.now() - started) / 10) / 100;
  console.log('\n──────── E2E 分桶结果 ────────');
  for (const r of results) {
    console.log(`${r.ok ? '✔' : '✘'} ${r.name.padEnd(15)} ${String(r.seconds).padStart(7)}s`);
  }
  console.log(`合计 ${total}s（${CONCURRENT ? '并发' : '顺序'}）`);

  if (results.some((r) => !r.ok)) {
    console.log('\n失败桶：' + results.filter((r) => !r.ok).map((r) => r.name).join(', '));
    process.exitCode = 1;
  }
}

await main();

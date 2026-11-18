/**
 * 聊天气泡时间分隔规则（2026-11-17 用户要求，TDD 红→绿）。
 * 规则：相邻两条消息间隔达到 5 分钟就重新显示当前时间；首条必显。
 * 用 Node 内置 test runner 跑纯函数，无需引入额外测试框架：
 *   node --experimental-strip-types --test packages/shared/test/chat-time.test.ts
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { shouldShowTimeDivider, fmtChatTime } from '../src/format.ts';

const T0 = new Date(2026, 10, 17, 2, 0, 0).getTime();

test('首条消息必须显示时间', () => {
  assert.equal(shouldShowTimeDivider(null, T0), true);
  assert.equal(shouldShowTimeDivider(0, T0), true);
});

test('间隔不足 5 分钟不显示时间', () => {
  assert.equal(shouldShowTimeDivider(T0, T0 + 4 * 60_000 + 59_000), false);
});

test('间隔达到 5 分钟（含）显示时间', () => {
  assert.equal(shouldShowTimeDivider(T0, T0 + 5 * 60_000), true);
  assert.equal(shouldShowTimeDivider(T0, T0 + 6 * 60_000), true);
});

test('时钟回拨/脏数据不显示时间', () => {
  assert.equal(shouldShowTimeDivider(T0, T0 - 1000), false);
});

test('时间文案：今天只显 HH:mm，昨天带「昨天」，更早带日期', () => {
  // 按本地日期构造，避免 CI 时区与 UTC 断言错位
  const now = new Date(2026, 10, 17, 12, 0, 0).getTime();
  assert.equal(fmtChatTime(now, now), '12:00');
  assert.equal(fmtChatTime(new Date(2026, 10, 16, 12, 0).getTime(), now), '昨天 12:00');
  assert.equal(fmtChatTime(new Date(2026, 10, 14, 12, 0).getTime(), now), '11月14日 12:00');
  assert.equal(fmtChatTime(new Date(2025, 9, 13, 12, 0).getTime(), now), '2025年10月13日 12:00');
});

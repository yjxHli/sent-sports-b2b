/**
 * 会员日规则纯函数（TDD 红→绿）：
 * 会员日=每周三/周四；下次会员日=从当前时刻向后第一个周三或周四的 00:00；
 * 倒计时格式 xx天xx小时xx分钟。前后端共用同一口径，前端负责展示、后端负责拦截。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { isMemberDay, nextMemberDayAt, formatCountdown } from '../src/format.ts';

// 本地时区构造，避免 UTC 换算导致星期偏移
const at = (y: number, m: number, d: number, h = 10, min = 0) => new Date(y, m - 1, d, h, min);

test('周三、周四是会员日，其余不是', () => {
  assert.equal(isMemberDay(at(2026, 11, 18)), true, '周三'); // 2026-11-18 周三
  assert.equal(isMemberDay(at(2026, 11, 19)), true, '周四'); // 2026-11-19 周四
  assert.equal(isMemberDay(at(2026, 11, 20)), false, '周五');
  assert.equal(isMemberDay(at(2026, 11, 21)), false, '周六');
  assert.equal(isMemberDay(at(2026, 11, 22)), false, '周日');
  assert.equal(isMemberDay(at(2026, 11, 23)), false, '周一');
  assert.equal(isMemberDay(at(2026, 11, 24)), false, '周二');
});

test('周五的下次会员日是本周三 00:00', () => {
  const next = nextMemberDayAt(at(2026, 11, 20, 15, 30)); // 周五
  assert.equal(next.getFullYear(), 2026);
  assert.equal(next.getMonth() + 1, 11);
  assert.equal(next.getDate(), 25); // 下周三
  assert.equal(next.getHours(), 0);
  assert.equal(next.getMinutes(), 0);
});

test('周二的下次会员日是明天周三 00:00', () => {
  const next = nextMemberDayAt(at(2026, 11, 24, 9, 0)); // 周二
  assert.equal(next.getDate(), 25);
  assert.equal(next.getDay(), 3);
});

test('周三当天的下次边界是次日周四 00:00（同一轮会员日的第二天）', () => {
  const next = nextMemberDayAt(at(2026, 11, 18, 10, 0)); // 周三上午
  assert.equal(next.getDate(), 19);
  assert.equal(next.getDay(), 4);
  assert.equal(next.getHours(), 0);
});

test('周四当天的下次边界是下周三 00:00（下一轮会员日）', () => {
  const next = nextMemberDayAt(at(2026, 11, 19, 10, 0)); // 周四上午
  assert.equal(next.getDate(), 25);
  assert.equal(next.getDay(), 3);
});

test('倒计时格式化为 xx天xx小时xx分钟', () => {
  const day = 86_400_000;
  const hour = 3_600_000;
  const minute = 60_000;
  assert.equal(formatCountdown(2 * day + 3 * hour + 4 * minute), '2天3小时4分钟');
  assert.equal(formatCountdown(0), '0天0小时0分钟');
  assert.equal(formatCountdown(59_000), '0天0小时0分钟', '不足 1 分钟按 0');
  assert.equal(formatCountdown(25 * hour + 61 * minute), '1天2小时1分钟', '进位正确');
});

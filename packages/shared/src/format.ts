/**
 * 金额与时间格式化（P6-02）。
 * 铁律：后端金额一律 BIGINT 分，页面禁止 `{{ price / 100 }}`，统一走 fmtCent。
 * 本模块为纯函数，不依赖 Vue / axios / 浏览器环境，两端共用且可做单测。
 */

/**
 * 分 -> 元（保留两位小数，千分位）。
 * @example fmtCent(12900) === "129.00"
 */
export function fmtCent(cent: number | string | null | undefined): string {
  if (cent === null || cent === undefined || cent === '') return '0.00';
  const c = typeof cent === 'string' ? Number(cent) : cent;
  if (!Number.isFinite(c)) return '0.00';
  const negative = c < 0;
  const abs = Math.abs(c);
  const yuan = Math.floor(abs / 100);
  const frac = (abs % 100).toString().padStart(2, '0');
  return (negative ? '-' : '') + yuan.toLocaleString('zh-CN') + '.' + frac;
}

/** 元（用户输入的字符串）-> 分，非法输入返回 null，避免把 NaN 写进后端 */
export function yuanToCent(yuan: string | number | null | undefined): number | null {
  if (yuan === null || yuan === undefined || yuan === '') return null;
  const value = typeof yuan === 'string' ? Number(yuan.replace(/,/g, '').trim()) : yuan;
  if (!Number.isFinite(value)) return null;
  return Math.round(value * 100);
}

/** 带 ¥ 前缀的金额展示（列表/详情页用） */
export function fmtMoney(cent: number | string | null | undefined): string {
  return `¥${fmtCent(cent)}`;
}

/** 数量展示：库存四口径均为整数，避免出现 1.5 件 */
export function fmtQty(qty: number | string | null | undefined): string {
  const n = typeof qty === 'string' ? Number(qty) : qty;
  if (n === null || n === undefined || !Number.isFinite(n)) return '0';
  return Math.trunc(n).toLocaleString('zh-CN');
}

/** 时间戳（毫秒 / 秒）/ ISO 字符串 -> YYYY-MM-DD HH:mm */
export function fmtDateTime(value: number | string | null | undefined): string {
  const date = toDate(value);
  if (!date) return '-';
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 时间戳 -> YYYY-MM-DD */
export function fmtDate(value: number | string | null | undefined): string {
  const date = toDate(value);
  if (!date) return '-';
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function toDate(value: number | string | null | undefined): Date | null {
  if (value === null || value === undefined || value === '') return null;
  let date: Date;
  if (typeof value === 'number') {
    // 后端 created_at 为 DATETIME(3) 毫秒；JWT exp 等秒级时间戳需放大
    date = new Date(value < 1e11 ? value * 1000 : value);
  } else if (/^\d+$/.test(value)) {
    const n = Number(value);
    date = new Date(n < 1e11 ? n * 1000 : n);
  } else {
    date = new Date(value.replace(' ', 'T'));
  }
  return Number.isNaN(date.getTime()) ? null : date;
}

/** 手机号脱敏（字段脱敏由后端决定，前端仅兜底展示） */
export function maskPhone(phone: string | null | undefined): string {
  if (!phone) return '-';
  return phone.length < 7 ? phone : `${phone.slice(0, 3)}****${phone.slice(-4)}`;
}

/**
 * 聊天消息时间分隔阈值：相邻两条消息相隔 5 分钟就重新显示一次当前时间（微信口径）。
 * 两端共用同一份规则，避免客服端与客户看到一个显、一个不显。
 */
export const CHAT_TIME_GAP_MS = 5 * 60_000;

/**
 * 当前消息上方是否要插入时间提示。
 *
 * @param prevAt 上一条消息时间（毫秒 / 秒 / 时间字符串），首条传 null
 * @param curAt  当前消息时间；无效时间一律不显示，避免 1970-01-01 出现在气泡上方
 */
export function shouldShowTimeDivider(
  prevAt: number | string | null | undefined,
  curAt: number | string | null | undefined,
  gapMs: number = CHAT_TIME_GAP_MS,
): boolean {
  const cur = toDate(curAt);
  if (!cur) return false;
  const prev = toDate(prevAt);
  if (!prev) return true;
  return cur.getTime() - prev.getTime() >= gapMs;
}

/** 同一天用 HH:mm（对齐微信），跨天带「昨天 / MM月DD日」，跨年带年份。 */
export function fmtChatTime(value: number | string | null | undefined, nowMs: number = Date.now()): string {
  const date = toDate(value);
  if (!date) return '';
  const now = new Date(nowMs);
  const pad = (n: number) => n.toString().padStart(2, '0');
  const hm = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  if (date.getFullYear() !== now.getFullYear()) {
    return `${date.getFullYear()}年${pad(date.getMonth() + 1)}月${pad(date.getDate())}日 ${hm}`;
  }
  const sameDay = (d: Date) => d.getFullYear() === date.getFullYear()
    && d.getMonth() === date.getMonth() && d.getDate() === date.getDate();
  if (sameDay(now)) return hm;
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
  if (sameDay(yesterday)) return `昨天 ${hm}`;
  return `${pad(date.getMonth() + 1)}月${pad(date.getDate())}日 ${hm}`;
}

/** 逾期天数：负数按 0 处理，用于应收台账标红 */
export function overdueDays(dueAt: number | string | null | undefined, now = Date.now()): number {
  const date = toDate(dueAt);
  if (!date) return 0;
  return Math.max(0, Math.floor((now - date.getTime()) / 86_400_000));
}

// ===========================================================================
// 会员日规则（每周三/周四）：前后端共用同一口径
// ===========================================================================

/** 会员日是周三(3)、周四(4)，其余日期不可从领券中心领券 */
export function isMemberDay(date: Date | number = new Date()): boolean {
  const d = date instanceof Date ? date : new Date(date);
  const day = d.getDay();
  return day === 3 || day === 4;
}

/**
 * 从给定时刻向后（严格大于）找第一个会员日的 00:00。
 * 今天是周三/周四时指向下一次（下周三），与后端 MemberDayPolicy 一致。
 */
export function nextMemberDayAt(now: Date | number = new Date()): Date {
  const d = (now instanceof Date ? now : new Date(now));
  const next = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1, 0, 0, 0, 0);
  while (!isMemberDay(next)) next.setDate(next.getDate() + 1);
  return next;
}

/** 毫秒差 -> `xx天xx小时xx分钟`（不足 1 分钟按 0，向下取整） */
export function formatCountdown(ms: number): string {
  const totalMin = Math.max(0, Math.floor(ms / 60_000));
  const days = Math.floor(totalMin / 1440);
  const hours = Math.floor((totalMin % 1440) / 60);
  const minutes = totalMin % 60;
  return `${days}天${hours}小时${minutes}分钟`;
}

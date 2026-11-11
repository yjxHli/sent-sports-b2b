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

/** 姓名脱敏：保留姓氏 */
export function maskName(name: string | null | undefined): string {
  if (!name) return '-';
  return name.length <= 1 ? name : `${name.slice(0, 1)}${'*'.repeat(name.length - 1)}`;
}

/** 逾期天数：负数按 0 处理，用于应收台账标红 */
export function overdueDays(dueAt: number | string | null | undefined, now = Date.now()): number {
  const date = toDate(dueAt);
  if (!date) return 0;
  return Math.max(0, Math.floor((now - date.getTime()) / 86_400_000));
}

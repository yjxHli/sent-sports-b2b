/**
 * 共享层（packages/shared）：两端共用的契约、枚举、格式化与请求基础。
 * 前后端枚举必须由同一份定义产出，禁止手工复制，避免「前端以为能退、后端判不可退」。
 *
 * 子模块：
 * - `@sent/shared`       → 统一响应 / 错误码 / 枚举 / 格式化 / 类型（本文件聚合）
 * - `@sent/shared/http`  → 双令牌 HTTP 内核（依赖 axios，按需单独引入）
 */

export * from './enums';
export * from './format';
export * from './types';

/** 统一响应体（与后端 com.sent.sports.common.web.R 对应） */
export interface ApiResult<T = unknown> {
  code: number;
  message: string;
  data: T;
  traceId: string;
}

/** 错误码（与后端 ErrorCode.java 保持一致） */
export enum ErrorCode {
  SUCCESS = 0,
  PARAM_INVALID = 40001,
  IDEMPOTENCY_KEY_MISSING = 40002,
  UNAUTHENTICATED = 40101,
  TOKEN_EXPIRED = 40102,
  REFRESH_TOKEN_INVALID = 40103,
  FORBIDDEN = 40301,
  CONFIRM_TOKEN_INVALID = 40302,
  NOT_FOUND = 40401,
  STATE_CONFLICT = 40901,
  DUPLICATE_PROCESSING = 40902,
  STOCK_NOT_ENOUGH = 40910,
  BELOW_FLOOR_PRICE = 40920,
  WRITE_OFF_OVERFLOW = 40930,
  REFUND_OVERFLOW = 40940,
  CREDIT_NOT_ENOUGH = 40950,
  BIZ_REJECT = 42201,
  /** 手机号未注册：登录页据此弹提示并跳注册页回填手机号 */
  PHONE_NOT_REGISTERED = 42202,
  /** 验证码错误/过期/已消费 */
  SMS_CODE_INVALID = 42203,
  TOO_FREQUENT = 42901,
  SYSTEM_ERROR = 50001,
}

/**
 * 2026-11-28：删除三个零引用导出（TOKEN_KEYS / REFRESH_CODES / LOGOUT_CODES）。
 * HTTP 内核 http.ts 自带局部 KEYS 与刷新/登出判定，这三份常量从未被任何端使用，
 * 留着会让人误以为改这里就能改令牌行为——真正的真源只有 http.ts。
 */

/** 当前环境 API 基地址（Vite 在构建时替换 import.meta.env） */
export const API_BASE = import.meta.env?.VITE_API_BASE ?? '/api';

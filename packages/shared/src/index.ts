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
  TOO_FREQUENT = 42901,
  SYSTEM_ERROR = 50001,
}

/** 需要触发刷新流程的错误码 */
export const REFRESH_CODES = [ErrorCode.TOKEN_EXPIRED];
/** 需要直接登出的错误码 */
export const LOGOUT_CODES = [ErrorCode.REFRESH_TOKEN_INVALID, ErrorCode.UNAUTHENTICATED];

/** 当前环境 API 基地址（Vite 在构建时替换 import.meta.env） */
export const API_BASE = import.meta.env?.VITE_API_BASE ?? '/api';

/** 双令牌默认存储键（各端必须再叠加自己的前缀，见 VITE_TOKEN_STORAGE_PREFIX） */
export const TOKEN_KEYS = {
  access: 'sent_access_token',
  refresh: 'sent_refresh_token',
  expiresIn: 'sent_expires_in',
} as const;

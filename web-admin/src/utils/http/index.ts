/**
 * 管理端 HTTP 适配层（P6-06）。
 *
 * 拦截器全部实现在 `@sent/shared/http` 的 createSentHttp（两端共用一份，避免重复维护）：
 * X-New-Token 防旧盖新、40102 单飞刷新重放、40103 清令牌跳登录、白名单、_retry 防循环。
 * 本文件只注入管理端的 baseURL / TokenStorage / 登出动作，并保持对外 API 不变。
 */
import {
  createSentHttp,
  unwrap as sharedUnwrap,
  type HttpError,
  type SentRequestConfig,
} from '@sent/shared/http';
import { API_BASE } from '@sent/shared';
import * as auth from '@/utils/auth';

const sent = createSentHttp({
  baseURL: import.meta.env.VITE_API_BASE || API_BASE || '/api',
  storage: {
    getAccessToken: auth.getAccessToken,
    getRefreshToken: auth.getRefreshToken,
    isAccessTokenExpired: auth.isAccessTokenExpired,
    applyNewAccessToken: auth.setAccessTokenIfNewer,
    setTokens: auth.setTokens,
    clearTokens: auth.clearTokens,
  },
  onLogout: auth.logoutToLogin,
});

export const http = sent;
export const request = <T = unknown>(config: SentRequestConfig) => sent.request<T>(config);
export const refreshSingleFlight = (): Promise<string> => sent.refreshSingleFlight();
export const setHttpErrorHandler = (handler: (error: HttpError) => void): void =>
  sent.setErrorHandler(handler);
export const unwrap = sharedUnwrap;

export { HttpError } from '@sent/shared/http';
export type { SentRequestConfig, HttpError as HttpErrorType } from '@sent/shared/http';

/**
 * 商城端 HTTP 适配层（P6-04）。
 * 拦截逻辑与管理端共用 `@sent/shared/http`，只注入商城端的 baseURL / 令牌存储 / 登出动作。
 */
import {
  createSentHttp,
  unwrap as sharedUnwrap,
  type HttpError,
  type SentRequestConfig,
} from '@sent/shared/http';
import { API_BASE } from '@sent/shared';
import * as auth from './auth';

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
export type { SentRequestConfig } from '@sent/shared/http';

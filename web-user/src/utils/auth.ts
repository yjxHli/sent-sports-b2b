/**
 * 商城端令牌适配层（P6-07 / D1 双端隔离）。
 * 与 web-admin 共用 shared 的 createTokenStorage，仅注入不同前缀与 aud。
 */
import { createTokenStorage, decodeJwtPayload, expOf, type TokenVO } from '@sent/shared/http';

export type { TokenVO };
export { decodeJwtPayload, expOf };

const historyMode = (import.meta.env.VITE_ROUTER_HISTORY || 'h5')
  .replace(/["']/g, '').trim() === 'hash' ? 'hash' : 'h5';

const handle = createTokenStorage({
  prefix: import.meta.env.VITE_TOKEN_STORAGE_PREFIX || 'sent_user',
  aud: import.meta.env.VITE_AUD || 'user',
  publicPath: import.meta.env.VITE_PUBLIC_PATH || '/',
  historyMode,
});

export const AUD = handle.aud;
export const LOGIN_PATH = handle.loginPath;

export const getAccessToken = handle.getAccessToken;
export const getRefreshToken = handle.getRefreshToken;
export const getExpiresAt = handle.getExpiresAt;
export const getDeviceId = handle.getDeviceId;
export const setTokens = handle.setTokens;
export const setAccessTokenIfNewer = handle.setAccessTokenIfNewer;
export const clearTokens = handle.clearTokens;
export const isAccessTokenExpired = handle.isAccessTokenExpired;
export const onTokenChanged = handle.onTokenChanged;
export const redirectToLogin = handle.redirectToLogin;
export const logoutToLogin = handle.logoutToLogin;

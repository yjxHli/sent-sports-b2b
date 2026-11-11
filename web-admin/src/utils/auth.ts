/**
 * 管理端令牌适配层（P6-07）。
 *
 * 具体实现已下沉到 `@sent/shared/http` 的 createTokenStorage，与 web-user 共用同一份逻辑；
 * 本文件只负责注入管理端差异（storage 前缀 / aud / 部署路径 / 路由模式），
 * 并保持原有具名导出不变，使 useAuthStore 与 http 层无需改动。
 */
import { createTokenStorage, decodeJwtPayload, expOf, type TokenVO } from '@sent/shared/http';

export type { TokenVO };
export { decodeJwtPayload, expOf };

const historyMode = (import.meta.env.VITE_ROUTER_HISTORY || 'h5')
  .replace(/["']/g, '').trim() === 'hash' ? 'hash' : 'h5';

const handle = createTokenStorage({
  prefix: import.meta.env.VITE_TOKEN_STORAGE_PREFIX || 'sent_admin',
  aud: import.meta.env.VITE_AUD || 'admin',
  publicPath: import.meta.env.VITE_PUBLIC_PATH || '/admin/',
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

/**
 * 客服端令牌适配层（从 web-admin/src/utils/auth.ts 剥离）。
 *
 * 具体实现仍复用 `@sent/shared/http` 的 createTokenStorage（三端共用一份）；
 * 本文件只注入客服端差异：storage 前缀 `cs-token`、部署路径、路由模式。
 *
 * 为什么前缀必须是 cs-token：
 * 同域下 web-admin(sent_admin_*) / web-user(sent_user_*) / web-cs 共用一份 localStorage，
 * 前缀撞了就会出现「开客服台把管理端顶下线」。单测见 test/csAccess.test.ts。
 */
import { createTokenStorage, decodeJwtPayload, expOf, type TokenVO } from '@sent/shared/http';
import { csStorageKeys } from '@/utils/csAccess';

export type { TokenVO };
export { decodeJwtPayload, expOf };

const historyMode = (import.meta.env.VITE_ROUTER_HISTORY || 'h5')
  .replace(/["']/g, '').trim() === 'hash' ? 'hash' : 'h5';

export const TOKEN_PREFIX = import.meta.env.VITE_TOKEN_STORAGE_PREFIX || 'cs-token';

const handle = createTokenStorage({
  prefix: TOKEN_PREFIX,
  aud: import.meta.env.VITE_AUD || 'admin',
  publicPath: import.meta.env.VITE_PUBLIC_PATH || '/',
  historyMode,
});

export const AUD = handle.aud;
export const LOGIN_PATH = handle.loginPath;
/** 键名对外暴露一份，供调试面板 / E2E 断言「没有串用管理端键」 */
export const STORAGE_KEYS = csStorageKeys(TOKEN_PREFIX);

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

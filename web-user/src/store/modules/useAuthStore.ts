/**
 * 商城端登录态 Store（对齐 web-admin P6-06 / P6-07）。
 *
 * 职责边界与 admin 一致：
 * - Token 唯一真源是 utils/auth.ts；http 层直接读写它，不 import store。
 * - 两者通过 onTokenChanged 订阅联动，computed 自动重算。
 * - 商城端无需角色权限矩阵；本 store 仅维护登录态与 userId（由 JWT sub 解析）。
 */
import { computed, onScopeDispose, ref } from 'vue';
import { defineStore } from 'pinia';
import { ErrorCode } from '@sent/shared';
import { decodeJwtPayload } from '@sent/shared/http';
import {
  AUD,
  clearTokens,
  getAccessToken,
  getDeviceId,
  getRefreshToken,
  isAccessTokenExpired,
  logoutToLogin,
  onTokenChanged,
  setTokens,
  type TokenVO,
} from '@/utils/auth';
import { refreshSingleFlight } from '@/utils/http';
import { authApi } from '@/api/auth';

export const useAuthStore = defineStore('sent-user-auth', () => {
  const revision = ref(0);
  const touch = (): void => { revision.value += 1; };
  const off = onTokenChanged(touch);
  onScopeDispose(off);

  const accessToken = computed(() => { void revision.value; return getAccessToken(); });
  const refreshToken = computed(() => { void revision.value; return getRefreshToken(); });
  const isLoggedIn = computed(() => Boolean(accessToken.value && refreshToken.value));

  /** 当前用户主体：JWT 的 sub 即 userId；商城端以此作为 customerId 透传给业务接口 */
  const userId = computed(() => {
    void revision.value;
    const sub = decodeJwtPayload(getAccessToken())?.sub;
    return sub ? Number(sub) : null;
  });

  const aud = AUD;
  const deviceId = getDeviceId();

  function setToken(vo: TokenVO): void {
    setTokens(vo);
    touch();
  }

  async function login(username: string, password: string): Promise<TokenVO> {
    const res = await authApi.login({ username, password, aud, deviceId });
    if (res.code !== ErrorCode.SUCCESS) throw new Error(res.message || '登录失败');
    setToken(res.data);
    return res.data;
  }

  async function refresh(): Promise<string> {
    const token = await refreshSingleFlight();
    touch();
    return token;
  }

  async function logout(): Promise<void> {
    const current = getRefreshToken();
    if (current) {
      try { await authApi.logout(current); } catch { /* 网络失败也必须完成本地登出 */ }
    }
    logoutToLogin();
    touch();
  }

  function isExpired(leewaySeconds = 0): boolean {
    void revision.value;
    return isAccessTokenExpired(leewaySeconds);
  }

  return {
    accessToken, refreshToken, isLoggedIn, userId, aud, deviceId,
    setToken, clearTokens, login, refresh, logout, logoutToLogin, isExpired,
  };
});

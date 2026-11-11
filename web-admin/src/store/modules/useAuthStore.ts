/**
 * 管理端登录态 Store（与 utils/http/index.ts 配套的 P6-06 / P6-07）。
 *
 * 职责边界：
 * - 本 store 只做「页面可用的响应式视图 + 业务动作（登录/刷新/登出）」。
 * - Token 的唯一真源是 utils/auth.ts；http 层直接读写它，不 import store。
 * - 两者通过 onTokenChanged 订阅联动：http 静默续期写入新 Access 后，
 *   store 的 computed 会自动重算，页面无需手动同步，也不会出现双写不一致。
 */
import { computed, onScopeDispose, ref } from 'vue';
import { defineStore } from 'pinia';
import { ErrorCode } from '@sent/shared';
import {
  AUD,
  clearTokens,
  expOf,
  getAccessToken,
  getDeviceId,
  getExpiresAt,
  getRefreshToken,
  isAccessTokenExpired,
  logoutToLogin,
  onTokenChanged,
  setAccessTokenIfNewer,
  setTokens,
  type TokenVO,
} from '@/utils/auth';
import { refreshSingleFlight } from '@/utils/http';
import { authApi } from '@/api/auth';

export const useAuthStore = defineStore('sent-admin-auth', () => {
  /**
   * localStorage 不是响应式数据源，用 revision 作为「读令牌」的响应式依赖；
   * 所有令牌写入（含 http 层的 X-New-Token 静默替换）都会触发订阅把它 +1。
   */
  const revision = ref(0);
  const touch = (): void => {
    revision.value += 1;
  };

  const off = onTokenChanged(touch);
  onScopeDispose(off);

  const accessToken = computed(() => {
    void revision.value;
    return getAccessToken();
  });
  const refreshToken = computed(() => {
    void revision.value;
    return getRefreshToken();
  });
  /** 绝对过期时间戳（毫秒） */
  const expiresAt = computed(() => {
    void revision.value;
    return getExpiresAt();
  });
  const isLoggedIn = computed(() => Boolean(accessToken.value && refreshToken.value));

  /** 登录端与设备标识：后端按 aud|userId|deviceId 隔离一次性刷新令牌 */
  const aud = AUD;
  const deviceId = getDeviceId();

  /** 登录成功 / 刷新成功后整体替换双令牌 */
  function setToken(vo: TokenVO): void {
    setTokens(vo);
    touch();
  }

  /**
   * 暴露给页面与测试的「防旧盖新」入口，与 http 层 X-New-Token 用的是同一套判定。
   * @returns 是否发生替换（exp 未变大时返回 false）
   */
  function applyNewAccessToken(token: string, expireInSeconds?: number): boolean {
    const changed = setAccessTokenIfNewer(token, expireInSeconds);
    if (changed) touch();
    return changed;
  }

  /** 仅清本地令牌，不跳转（用于路由守卫、40103 的内部处理） */
  function clearToken(): void {
    clearTokens();
    touch();
  }

  /** 登录：公开接口，不注入 Authorization、不参与 token 分流 */
  async function login(username: string, password: string): Promise<TokenVO> {
    const res = await authApi.login({ username, password, aud, deviceId });
    if (res.code !== ErrorCode.SUCCESS) {
      throw new Error(res.message || '登录失败');
    }
    setToken(res.data);
    return res.data;
  }

  /** 主动刷新：复用 http 层的单飞锁，避免与 40102 触发的刷新互相打架 */
  async function refresh(): Promise<string> {
    const token = await refreshSingleFlight();
    touch();
    return token;
  }

  /** 主动登出：先通知后端吊销刷新令牌，无论成败都清本地并跳登录 */
  async function logout(): Promise<void> {
    const current = getRefreshToken();
    if (current) {
      try {
        await authApi.logout(current);
      } catch {
        /* 网络失败也必须完成本地登出 */
      }
    }
    logoutToLogin();
    touch();
  }

  function isExpired(leewaySeconds = 0): boolean {
    void revision.value;
    return isAccessTokenExpired(leewaySeconds);
  }

  return {
    // state
    accessToken,
    refreshToken,
    expiresAt,
    isLoggedIn,
    aud,
    deviceId,
    // actions
    setToken,
    applyNewAccessToken,
    clearToken,
    login,
    refresh,
    logout,
    logoutToLogin,
    isExpired,
    expOf,
  };
});

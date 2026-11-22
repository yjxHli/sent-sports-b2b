/**
 * 客服端登录态 Store（对应 utils/auth.ts + utils/http）。
 *
 * 职责边界与管理端一致：
 * - 本 store 只做「页面可用的响应式视图 + 业务动作」，Token 的唯一真源是 utils/auth.ts。
 * - http 层直接读写 utils/auth.ts，不 import store；两者通过 onTokenChanged 联动，
 *   因此 X-New-Token 静默续期后 computed 会自动重算，页面无需手动同步。
 *
 * 客服端额外增加的只有一件事：`signIn()` 把「登录 → 拉权限 → 判 chat:serve」
 * 串成一个原子动作。任一步失败都不留下半登录状态（不满足就清 Token）。
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
import { usePermissionStore } from '@/store/modules/permission';

/** 非客服账号：调用方据此展示红字拦截，而不是弹普通报错 toast */
export class NotAgentError extends Error {
  constructor(message = '权限不足，您不是客服人员') {
    super(message);
    this.name = 'NotAgentError';
  }
}

export const useAuthStore = defineStore('cs-auth', () => {
  /**
   * localStorage 不是响应式数据源，用 revision 当「读令牌」的依赖；
   * 任何令牌写入（含 http 层 X-New-Token 静默替换）都会触发订阅把它 +1。
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
  const expiresAt = computed(() => {
    void revision.value;
    return getExpiresAt();
  });
  const isLoggedIn = computed(() => Boolean(accessToken.value && refreshToken.value));

  /** 登录端固定 admin（客服是员工账号），设备标识用于隔离一次性刷新令牌 */
  const aud = AUD;
  const deviceId = getDeviceId();

  function setToken(vo: TokenVO): void {
    setTokens(vo);
    touch();
  }

  function applyNewAccessToken(token: string, expireInSeconds?: number): boolean {
    const changed = setAccessTokenIfNewer(token, expireInSeconds);
    if (changed) touch();
    return changed;
  }

  /** 仅清本地令牌，不跳转（守卫 / 40103 内部处理用） */
  function clearToken(): void {
    clearTokens();
    touch();
  }

  /** 丢令牌并清空身份缓存：权限不足、账号被停用等场景必须成对调用，避免残留 me */
  function resetSession(): void {
    clearTokens();
    touch();
    usePermissionStore().reset();
  }

  /**
   * 客服登录：登录 → 拉 /admin/me → 校验 chat:serve。
   *
   * @throws NotAgentError 账号合法但不是客服（已清 Token）
   * @throws Error         账号密码错误 / 网络异常（不清 Token，允许重试）
   */
  async function signIn(username: string, password: string): Promise<TokenVO> {
    const res = await authApi.login({ username, password, aud, deviceId });
    if (res.code !== ErrorCode.SUCCESS) {
      throw new Error(res.message || '登录失败');
    }
    setToken(res.data);

    const permission = usePermissionStore();
    try {
      await permission.load(true);
    } catch (cause) {
      // 拿不到权限就无法判定身份：视为登录失败，不能留着一个「能进但不知道是谁」的会话
      resetSession();
      throw cause instanceof Error ? cause : new Error('无法获取当前账号权限');
    }

    if (!permission.isAgent) {
      // 核心拦截：清 Token + 清身份，由页面弹红字
      resetSession();
      throw new NotAgentError();
    }
    return res.data;
  }

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
    usePermissionStore().reset();
    logoutToLogin();
    touch();
  }

  function isExpired(leewaySeconds = 0): boolean {
    void revision.value;
    return isAccessTokenExpired(leewaySeconds);
  }

  return {
    accessToken,
    refreshToken,
    expiresAt,
    isLoggedIn,
    aud,
    deviceId,
    setToken,
    applyNewAccessToken,
    clearToken,
    resetSession,
    signIn,
    refresh,
    logout,
    logoutToLogin,
    isExpired,
    expOf,
  };
});

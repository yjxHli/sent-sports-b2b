/**
 * 两端共用的双令牌 HTTP 内核（P6-04，plan §7.11 / §9.4.4 D1–D5）。
 *
 * 为什么放在 shared：plan 明确要求「两端共用一份，不要在 web-admin / web-user 各写一份」，
 * 否则修一次 bug 要改两处。两端差异（baseURL、storage 前缀、aud、登录路径）全部参数化注入。
 *
 * 已实现：
 * - D1 双端隔离：由 createTokenStorage({ prefix, aud, publicPath }) 生成互不相干的存储。
 * - D2 滑动续期：响应头 X-New-Token 静默替换；只接受 exp 更大的新 Token（D4 防旧盖新）。
 * - D3 错误码分流：40102 单飞刷新 + 重放一次；40103 / 40101 清令牌跳登录（带回跳）。
 * - D5 白名单 + _retry：登录/刷新本身不注入令牌、不触发刷新，杜绝死循环。
 *
 * 两个容易踩的坑（已处理）：
 * 1) 后端 AuthFilter 用 HTTP 401 + body.code 写错误，axios 走 rejected 分支，
 *    因此 fulfilled 与 rejected 都必须解析同一套业务码。
 * 2) 刷新请求走独立裸 axios 实例，不经过业务拦截器，避免 refresh → 40102 → refresh 自我递归。
 */
import axios, {
  AxiosHeaders,
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { ErrorCode, type ApiResult } from './index';
import type { TokenVO } from './types';

export type { TokenVO } from './types';

// ===========================================================================
// 一、Token 存储（唯一真源，纯浏览器 Storage，不依赖 Vue / Pinia）
// ===========================================================================

/** createSentHttp 所需的最小存储契约 */
export interface TokenStorage {
  getAccessToken(): string;
  getRefreshToken(): string;
  isAccessTokenExpired(leewaySeconds?: number): boolean;
  /** 内部必须比较 exp，返回是否真的替换 */
  applyNewAccessToken(next: string, hintExpireInSeconds?: number): boolean;
  setTokens(vo: TokenVO): void;
  clearTokens(): void;
}

export interface TokenStorageOptions {
  /** 双端必须用不同前缀，如 sent_admin / sent_user */
  prefix: string;
  /** 登录端标识，随 /auth/login 提交 */
  aud: string;
  /** 部署基路径，如 /admin/ 或 / */
  publicPath: string;
  historyMode?: 'h5' | 'hash';
  /** 覆盖自动推导的登录页地址 */
  loginPath?: string;
}

export interface TokenStorageHandle extends TokenStorage {
  readonly aud: string;
  readonly loginPath: string;
  getExpiresAt(): number;
  getDeviceId(): string;
  expOf(token: string | null | undefined): number;
  decodeJwtPayload(token: string): Record<string, unknown> | null;
  setTokens(vo: TokenVO): void;
  setAccessTokenIfNewer(next: string, hintExpireInSeconds?: number): boolean;
  clearTokens(): void;
  isAccessTokenExpired(leewaySeconds?: number): boolean;
  onTokenChanged(listener: () => void): () => void;
  redirectToLogin(): void;
  logoutToLogin(): void;
}

/** 解出 JWT payload；只解码不验签，验签永远在后端 */
export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    const segment = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = segment.padEnd(Math.ceil(segment.length / 4) * 4, '=');
    const bytes = Uint8Array.from(atob(padded), (ch) => ch.charCodeAt(0));
    const payload: unknown = JSON.parse(new TextDecoder().decode(bytes));
    return payload !== null && typeof payload === 'object'
      ? (payload as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

/** 读取 JWT 的 exp（秒），失败返回 0 */
export function expOf(token: string | null | undefined): number {
  const exp = decodeJwtPayload(token ?? '')?.exp;
  return typeof exp === 'number' && Number.isFinite(exp) && exp > 0 ? exp : 0;
}

/**
 * 生成一套隔离的令牌存储。同一浏览器同时登录管理端与商城端时互不覆盖。
 * 所有写操作都会广播 onTokenChanged，供 Pinia store 保持响应式。
 */
export function createTokenStorage(options: TokenStorageOptions): TokenStorageHandle {
  const prefix = options.prefix.trim() || 'sent';
  const base = options.publicPath.replace(/\/+$/, '');
  const loginPath = options.loginPath
    ?? (options.historyMode === 'hash' ? `${base}/#/login` : `${base}/login`);

  const KEYS = {
    access: `${prefix}_access_token`,
    refresh: `${prefix}_refresh_token`,
    expires: `${prefix}_expires_at`,
    device: `${prefix}_device_id`,
  } as const;

  // SSR / 隐私模式下 localStorage 不可用，降级为内存态
  const memory = new Map<string, string>();
  const store = (): Storage | null => {
    try {
      return typeof window === 'undefined' ? null : window.localStorage;
    } catch {
      return null;
    }
  };
  const read = (key: string): string => {
    const s = store();
    if (!s) return memory.get(key) ?? '';
    try {
      return s.getItem(key) ?? '';
    } catch {
      return memory.get(key) ?? '';
    }
  };
  const write = (key: string, value: string): void => {
    memory.set(key, value);
    const s = store();
    if (!s) return;
    try {
      s.setItem(key, value);
    } catch {
      /* 配额满：内存态仍可用 */
    }
  };
  const remove = (key: string): void => {
    memory.delete(key);
    const s = store();
    if (!s) return;
    try {
      s.removeItem(key);
    } catch {
      /* ignore */
    }
  };

  const listeners = new Set<() => void>();
  const emit = (): void => {
    listeners.forEach((fn) => {
      try {
        fn();
      } catch {
        /* 订阅方异常不得影响令牌写入 */
      }
    });
  };

  const getAccessToken = (): string => read(KEYS.access);
  const getRefreshToken = (): string => read(KEYS.refresh);
  const getExpiresAt = (): number => {
    const value = Number(read(KEYS.expires));
    return Number.isFinite(value) && value > 0 ? value : 0;
  };

  const setTokens = (vo: TokenVO): void => {
    if (!vo?.accessToken) return;
    write(KEYS.access, vo.accessToken);
    if (vo.refreshToken) write(KEYS.refresh, vo.refreshToken);
    const expSec = expOf(vo.accessToken)
      || Math.floor(Date.now() / 1000) + Math.max(0, Math.floor(vo.expiresIn || 0));
    write(KEYS.expires, String(expSec * 1000));
    emit();
  };

  /** D4：只有新 Token 的 exp 严格大于旧 Token 的 exp 才替换，防并发旧盖新 */
  const setAccessTokenIfNewer = (next: string, hintExpireInSeconds?: number): boolean => {
    if (!next) return false;
    const hintSec = typeof hintExpireInSeconds === 'number'
      && Number.isFinite(hintExpireInSeconds) && hintExpireInSeconds > 0
      ? Math.floor(Date.now() / 1000) + Math.floor(hintExpireInSeconds)
      : 0;
    const nextExp = expOf(next) || hintSec;
    if (nextExp <= 0) return false; // 无法确认 exp，一律不覆盖
    const oldExp = expOf(getAccessToken()) || Math.floor(getExpiresAt() / 1000);
    if (nextExp <= oldExp) return false;
    write(KEYS.access, next);
    write(KEYS.expires, String(nextExp * 1000));
    emit();
    return true;
  };

  const clearTokens = (): void => {
    remove(KEYS.access);
    remove(KEYS.refresh);
    remove(KEYS.expires);
    emit();
  };

  const isAccessTokenExpired = (leewaySeconds = 0): boolean => {
    const token = getAccessToken();
    if (!token) return true;
    const expSec = expOf(token) || Math.floor(getExpiresAt() / 1000);
    if (expSec <= 0) return true;
    return Date.now() >= expSec * 1000 - leewaySeconds * 1000;
  };

  const getDeviceId = (): string => {
    let id = read(KEYS.device);
    if (!id) {
      id = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
      write(KEYS.device, id);
    }
    return id;
  };

  let redirecting = false;
  const redirectToLogin = (): void => {
    if (typeof window === 'undefined' || redirecting) return;
    const path = window.location.pathname;
    if (path === loginPath || path.endsWith('/login')) return;
    redirecting = true;
    // 回跳地址必须是 vue-router 内部路径（不含部署基路径）。
    // window.location.pathname 带 /admin，登录页再 router.replace 会二次拼 base → /admin/admin/xxx 404。
    const relative = base && path.startsWith(`${base}/`) ? path.slice(base.length) : path;
    const back = relative + window.location.search + window.location.hash;
    const target = options.historyMode === 'hash'
      ? loginPath
      : `${loginPath}?redirect=${encodeURIComponent(back)}`;
    window.location.replace(target);
  };

  const logoutToLogin = (): void => {
    clearTokens();
    redirectToLogin();
  };

  return {
    aud: options.aud,
    loginPath,
    getAccessToken,
    getRefreshToken,
    getExpiresAt,
    getDeviceId,
    expOf,
    decodeJwtPayload,
    setTokens,
    setAccessTokenIfNewer,
    applyNewAccessToken: setAccessTokenIfNewer,
    clearTokens,
    isAccessTokenExpired,
    onTokenChanged: (listener: () => void) => {
      listeners.add(listener);
      return () => { listeners.delete(listener); };
    },
    redirectToLogin,
    logoutToLogin,
  };
}

// ===========================================================================
// 二、HTTP 客户端
// ===========================================================================

/** 统一抛出的错误对象，业务层可读取 code / traceId 做埋点 */
export class HttpError extends Error {
  constructor(
    public readonly code: number,
    message: string,
    public readonly traceId?: string,
  ) {
    super(message);
    this.name = 'HttpError';
  }
}

/** 扩展 axios 配置：内部流转标记，不发给后端 */
export interface SentRequestConfig extends AxiosRequestConfig {
  /** 已因 40102 重放过一次，防止无限循环 */
  _retry?: boolean;
  /** 跳过 Authorization 注入 */
  skipAuth?: boolean;
  /** 跳过整套 token 分流 */
  skipAuthFlow?: boolean;
}

type InterceptedConfig = InternalAxiosRequestConfig & SentRequestConfig;
type ResultResponse = AxiosResponse<ApiResult<unknown>>;

export interface SentHttpOptions {
  baseURL: string;
  storage: TokenStorage;
  /** 40103 / 刷新失败时执行（通常为 logoutToLogin） */
  onLogout: () => void;
  /** 基础设施类错误（40301 / 42901 / 50001）提示钩子 */
  onError?: (error: HttpError) => void;
  refreshPath?: string;
  whiteList?: string[];
  timeout?: number;
}

export interface SentHttp {
  request<T>(config: SentRequestConfig): Promise<ApiResult<T>>;
  get<T>(url: string, params?: object, config?: SentRequestConfig): Promise<ApiResult<T>>;
  post<T>(url: string, data?: unknown, config?: SentRequestConfig): Promise<ApiResult<T>>;
  put<T>(url: string, data?: unknown, config?: SentRequestConfig): Promise<ApiResult<T>>;
  patch<T>(url: string, data?: unknown, config?: SentRequestConfig): Promise<ApiResult<T>>;
  delete<T>(url: string, config?: SentRequestConfig): Promise<ApiResult<T>>;
  /**
   * 二进制下载（PDF / 图片 / 导出文件）：走同一套拦截器，因此同样享受
   * X-New-Token 续期、40102 单飞刷新重放与 40103 跳登录。
   * 后端出错时回的是统一 R 结构（application/json），会被识别成 HttpError 抛出，
   * 不会让用户下到一个「内容是错误 JSON 的 .pdf」。
   */
  getBlob(url: string, params?: object, config?: SentRequestConfig): Promise<Blob>;
  /** 取回 Blob 后立刻触发浏览器另存为 */
  download(url: string, fileName: string, params?: object, config?: SentRequestConfig): Promise<void>;
  refreshSingleFlight(): Promise<string>;
  setErrorHandler(handler: (error: HttpError) => void): void;
}

const DEFAULT_WHITE_LIST = [
  '/auth/login', '/auth/refresh', '/auth/logout', '/auth/sms-code', '/ping', '/health',
];
const INFRA_ERROR_CODES = new Set<number>([
  ErrorCode.FORBIDDEN, ErrorCode.SYSTEM_ERROR, ErrorCode.TOO_FREQUENT,
]);

function isApiResult(value: unknown): value is ApiResult<unknown> {
  return typeof value === 'object'
    && value !== null
    && typeof (value as { code?: unknown }).code === 'number';
}

function isLogoutCode(code: number): boolean {
  return code === ErrorCode.REFRESH_TOKEN_INVALID || code === ErrorCode.UNAUTHENTICATED;
}

/** axios 的 AxiosHeaders 与普通对象两种形态都兼容 */
function headerOf(response: AxiosResponse, name: string): string | undefined {
  const headers = response.headers as
    | (Record<string, unknown> & { get?: (key: string) => unknown })
    | undefined;
  if (!headers) return undefined;
  const fromGetter = typeof headers.get === 'function' ? headers.get(name) : undefined;
  const raw = fromGetter ?? headers[name] ?? headers[name.toLowerCase()];
  return typeof raw === 'string' && raw.trim() ? raw.trim() : undefined;
}

class PureHttp implements SentHttp {
  private readonly instance: AxiosInstance;
  private readonly rawHttp: AxiosInstance;
  private readonly opts: Required<Pick<SentHttpOptions, 'baseURL' | 'storage' | 'onLogout' | 'refreshPath' | 'whiteList'>>;
  private refreshing: Promise<string> | null = null;
  private errorHandler: (error: HttpError) => void;

  constructor(options: SentHttpOptions) {
    this.opts = {
      baseURL: options.baseURL.replace(/\/+$/, '') || '/api',
      storage: options.storage,
      onLogout: options.onLogout,
      refreshPath: options.refreshPath ?? '/auth/refresh',
      whiteList: options.whiteList ?? DEFAULT_WHITE_LIST,
    };
    this.errorHandler = options.onError ?? (() => { /* 默认静默 */ });

    this.instance = axios.create({
      baseURL: this.opts.baseURL,
      timeout: options.timeout ?? 20_000,
      headers: { 'Content-Type': 'application/json;charset=UTF-8' },
    });
    // 刷新专用裸实例：无任何拦截器，杜绝自我递归
    this.rawHttp = axios.create({
      baseURL: this.opts.baseURL,
      timeout: 15_000,
      headers: { 'Content-Type': 'application/json;charset=UTF-8' },
    });
    this.setupInterceptors();
  }

  private isWhiteListed(url: string): boolean {
    const path = url.split('?')[0].split('#')[0];
    const relative = path.startsWith(this.opts.baseURL)
      ? path.slice(this.opts.baseURL.length)
      : path;
    return this.opts.whiteList.some((item) => relative === item || relative.startsWith(`${item}/`));
  }

  private setupInterceptors(): void {
    this.instance.interceptors.request.use(
      async (config) => {
        const cfg = config as InterceptedConfig;
        const url = cfg.url ?? '';
        if (!cfg.skipAuth && !this.isWhiteListed(url)) {
          let token = this.opts.storage.getAccessToken();
          // 预判续期：本地已知过期先换新，与响应侧 40102 共用同一把单飞锁
          if (token && !cfg._retry
            && this.opts.storage.getRefreshToken()
            && this.opts.storage.isAccessTokenExpired(0)) {
            token = await this.refreshSingleFlight();
          }
          if (token) cfg.headers.set('Authorization', `Bearer ${token}`);
        }
        return cfg;
      },
      (cause: unknown) => Promise.reject(cause),
    );

    this.instance.interceptors.response.use(
      (response: ResultResponse) => this.onFulfilled(response),
      (cause: AxiosError<ApiResult<unknown>>) => this.onRejected(cause),
    );
  }

  private captureNewToken(response: AxiosResponse): void {
    const next = headerOf(response, 'X-New-Token');
    if (!next) return;
    const hintRaw = headerOf(response, 'X-Token-Expire-In');
    const hint = hintRaw === undefined ? undefined : Number(hintRaw);
    this.opts.storage.applyNewAccessToken(next, Number.isFinite(hint) ? (hint as number) : undefined);
  }

  private toHttpError(body?: ApiResult<unknown>, cause?: unknown): HttpError {
    if (isApiResult(body)) return new HttpError(body.code, body.message || '请求失败', body.traceId);
    if (cause instanceof HttpError) return cause;
    const message = (cause as { message?: string } | undefined)?.message;
    return new HttpError(ErrorCode.SYSTEM_ERROR, message || '网络异常，请稍后重试');
  }

  private notify(body: ApiResult<unknown>): void {
    if (INFRA_ERROR_CODES.has(body.code)) {
      this.errorHandler(new HttpError(body.code, body.message || '请求失败', body.traceId));
    }
  }

  private async onFulfilled(response: ResultResponse): Promise<ResultResponse> {
    this.captureNewToken(response);
    const body = response.data;
    if (!isApiResult(body)) return response; // 文件下载等非统一响应体
    if (body.code === ErrorCode.TOKEN_EXPIRED) return this.replayOnTokenExpired(response);
    if (isLogoutCode(body.code)) {
      this.opts.onLogout();
      return Promise.reject(this.toHttpError(body));
    }
    this.notify(body);
    return response;
  }

  private async onRejected(cause: AxiosError<ApiResult<unknown>>): Promise<ResultResponse> {
    const response = cause.response as ResultResponse | undefined;
    if (response) {
      // 后端用 HTTP 401 + body.code 写 40102/40103，rejected 分支必须同样分流
      this.captureNewToken(response);
      const body = response.data;
      if (isApiResult(body)) {
        if (body.code === ErrorCode.TOKEN_EXPIRED) return this.replayOnTokenExpired(response);
        if (isLogoutCode(body.code)) {
          this.opts.onLogout();
          return Promise.reject(this.toHttpError(body));
        }
        this.notify(body);
      }
    }
    return Promise.reject(this.toHttpError(undefined, cause));
  }

  private async replayOnTokenExpired(response: ResultResponse): Promise<ResultResponse> {
    const cfg = response.config as InterceptedConfig;
    if (cfg._retry || cfg.skipAuthFlow || this.isWhiteListed(cfg.url ?? '')) {
      this.opts.onLogout();
      return Promise.reject(new HttpError(ErrorCode.TOKEN_EXPIRED, '令牌已过期，请重新登录'));
    }
    const token = await this.refreshSingleFlight(); // 失败内部已清令牌 + 跳登录
    // 原请求头（Idempotency-Key / X-Confirm-Token 等）必须原样重放，只换 Authorization
    const headers = AxiosHeaders.from(cfg.headers);
    headers.set('Authorization', `Bearer ${token}`);
    const replayed = await this.request<unknown>({ ...cfg, _retry: true, headers });
    return { ...response, data: replayed };
  }

  /** 并发只发一次刷新，其余请求挂起在同一个 Promise 上（等待队列） */
  refreshSingleFlight(): Promise<string> {
    if (this.refreshing) return this.refreshing;
    const refreshToken = this.opts.storage.getRefreshToken();
    if (!refreshToken) {
      this.opts.onLogout();
      return Promise.reject(new HttpError(ErrorCode.REFRESH_TOKEN_INVALID, '刷新令牌缺失'));
    }
    this.refreshing = this.rawHttp
      .post<ApiResult<TokenVO>>(this.opts.refreshPath, { refreshToken })
      .then((res) => {
        const body = res.data;
        if (!isApiResult(body) || body.code !== ErrorCode.SUCCESS) {
          throw new HttpError(
            isApiResult(body) ? body.code : ErrorCode.REFRESH_TOKEN_INVALID,
            isApiResult(body) ? body.message : '刷新令牌已失效',
          );
        }
        this.opts.storage.setTokens(body.data);
        return body.data.accessToken;
      })
      .catch((cause: unknown) => {
        this.opts.onLogout();
        throw this.toHttpError(undefined, cause);
      })
      .finally(() => { this.refreshing = null; });
    return this.refreshing;
  }

  request<T = unknown>(config: SentRequestConfig): Promise<ApiResult<T>> {
    return this.instance
      .request<ApiResult<T>, AxiosResponse<ApiResult<T>>>(config)
      .then((res) => res.data);
  }

  get<T = unknown>(url: string, params?: object, config: SentRequestConfig = {}): Promise<ApiResult<T>> {
    return this.request<T>({ ...config, method: 'GET', url, params });
  }

  post<T = unknown>(url: string, data?: unknown, config: SentRequestConfig = {}): Promise<ApiResult<T>> {
    return this.request<T>({ ...config, method: 'POST', url, data });
  }

  put<T = unknown>(url: string, data?: unknown, config: SentRequestConfig = {}): Promise<ApiResult<T>> {
    return this.request<T>({ ...config, method: 'PUT', url, data });
  }

  patch<T = unknown>(url: string, data?: unknown, config: SentRequestConfig = {}): Promise<ApiResult<T>> {
    return this.request<T>({ ...config, method: 'PATCH', url, data });
  }

  delete<T = unknown>(url: string, config: SentRequestConfig = {}): Promise<ApiResult<T>> {
    return this.request<T>({ ...config, method: 'DELETE', url });
  }

  async getBlob(url: string, params?: object, config: SentRequestConfig = {}): Promise<Blob> {
    const cfg = { ...config, method: 'GET', url, params } as InterceptedConfig;
    const response = await this.instance.request<Blob, AxiosResponse<Blob>>({
      ...cfg,
      responseType: 'blob',
      // 让 4xx/5xx 也走 fulfilled 分支：rejected 分支会丢掉响应体，
      // 而「查无此票」「无权限」这类错误的真实原因就写在响应体里。
      validateStatus: () => true,
    });
    const failure = await this.readBlobFailure(response.data);
    if (failure) {
      if (failure.code === ErrorCode.TOKEN_EXPIRED && !cfg._retry && !cfg.skipAuthFlow) {
        await this.refreshSingleFlight();
        return this.getBlob(url, params, { ...config, _retry: true });
      }
      if (isLogoutCode(failure.code)) {
        this.opts.onLogout();
      }
      throw this.toHttpError(failure);
    }
    if (response.status >= 400) {
      throw new HttpError(ErrorCode.SYSTEM_ERROR, `文件获取失败（HTTP ${response.status}）`);
    }
    return response.data;
  }

  async download(url: string, fileName: string, params?: object,
                 config: SentRequestConfig = {}): Promise<void> {
    const blob = await this.getBlob(url, params, config);
    const href = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = href;
    anchor.download = fileName;
    anchor.style.display = 'none';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    // 立刻 revoke 会让部分浏览器拿到空文件，交回事件循环再释放
    setTimeout(() => URL.revokeObjectURL(href), 0);
  }

  /** responseType=blob 时错误体也是 Blob，必须读回来才能拿到 code/message */
  private async readBlobFailure(data: unknown): Promise<ApiResult<unknown> | null> {
    if (!(data instanceof Blob)) return null;
    if (!data.type.includes('json') && data.size > 64 * 1024) return null;
    try {
      const body: unknown = JSON.parse(await data.text());
      return isApiResult(body) && body.code !== ErrorCode.SUCCESS ? body : null;
    } catch {
      return null;
    }
  }

  setErrorHandler(handler: (error: HttpError) => void): void {
    this.errorHandler = handler;
  }
}

export function createSentHttp(options: SentHttpOptions): SentHttp {
  return new PureHttp(options);
}

/** 只想拿 data、不想每处判 code 时使用；非 0 业务码统一抛 HttpError */
export async function unwrap<T = unknown>(task: Promise<ApiResult<T>>): Promise<T> {
  const body = await task;
  if (body.code !== ErrorCode.SUCCESS) {
    throw new HttpError(body.code, body.message || '请求失败', body.traceId);
  }
  return body.data;
}

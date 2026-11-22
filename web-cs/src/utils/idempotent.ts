/**
 * 幂等键工具（P6-13，plan §7.12 / ADR-12）。
 *
 * 口径：进入页面/打开表单时生成一次并复用；提交成功后清空（下次操作换新房）；
 * 提交失败（业务校验不通过）保留原键，用户改完参数重试仍是同一笔，避免重复副作用。
 * 后端 IdempotentInterceptor 要求 Idempotency-Key 长度 8~64，缺失或非法返回 40002。
 */
import { onScopeDispose } from 'vue';

const MIN_LEN = 8;
const MAX_LEN = 64;

export interface IdempotencyScope {
  readonly scene: string;
  /** 当前键（未生成时 null） */
  current(): string | null;
  /** 已存在则复用，否则生成新键 */
  getOrCreate(): string;
  /** 强制换新键（如切换单据） */
  reset(): string;
  /** 提交成功后清空 */
  clear(): void;
  /** 直接给 axios 用的请求头；无键时返回空对象 */
  headers(): Record<string, string>;
}

/** 生成符合后端长度约束的键：场景前缀 + 随机串 */
export function newIdempotencyKey(scene = 'sent'): string {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
  const raw = `${scene.replace(/[^a-zA-Z0-9]/g, '').slice(0, 16)}_${random.replace(/[^a-zA-Z0-9]/g, '')}`;
  return raw.slice(0, MAX_LEN).padEnd(MIN_LEN, '0');
}

export function createIdempotencyScope(scene: string): IdempotencyScope {
  let key: string | null = null;
  return {
    scene,
    current: () => key,
    getOrCreate: () => {
      if (!key) key = newIdempotencyKey(scene);
      return key;
    },
    reset: () => {
      key = newIdempotencyKey(scene);
      return key;
    },
    clear: () => { key = null; },
    headers: (): Record<string, string> => {
      const current = key;
      return current ? { 'Idempotency-Key': current } : {};
    },
  };
}

/**
 * Vue 组件内使用：组件卸载时自动清空，避免键泄漏到下一个页面。
 * 只能在 setup / effect scope 内调用。
 */
export function useIdempotencyKey(scene: string): IdempotencyScope {
  const scope = createIdempotencyScope(scene);
  onScopeDispose(() => scope.clear());
  return scope;
}

/** 把幂等键并入已有请求配置，写接口统一这样用 */
export function withIdempotency(
  scope: IdempotencyScope,
  config: Record<string, unknown> = {},
): Record<string, unknown> {
  const own = scope.getOrCreate();
  const headers = (config.headers as Record<string, string> | undefined) ?? {};
  return { ...config, headers: { ...headers, 'Idempotency-Key': own } };
}

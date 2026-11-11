/**
 * 幂等键工具（对齐 web-admin P6-13 / plan §7.12）。
 * 进入页面生成一次并复用；提交成功清空（换新房），失败保留原键重试。
 * 后端 IdempotentInterceptor 要求 Idempotency-Key 长度 8~64。
 */
import { onScopeDispose } from 'vue';

const MIN_LEN = 8;
const MAX_LEN = 64;

export interface IdempotencyScope {
  readonly scene: string;
  current(): string | null;
  getOrCreate(): string;
  reset(): string;
  clear(): void;
  headers(): Record<string, string>;
}

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

export function useIdempotencyKey(scene: string): IdempotencyScope {
  const scope = createIdempotencyScope(scene);
  onScopeDispose(() => scope.clear());
  return scope;
}

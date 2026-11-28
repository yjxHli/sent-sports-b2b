/**
 * 兼容层（2026-11-28 P2）：框架无关的幂等工具已上提到 `@sent/shared/idempotent`。
 * Vue 组件卸载自动清键属于应用层生命周期，留在本地薄封装，避免 shared 依赖 vue。
 */
import { onScopeDispose } from 'vue';
import { createIdempotencyScope, type IdempotencyScope } from '@sent/shared/idempotent';

export { newIdempotencyKey, createIdempotencyScope, withIdempotency } from '@sent/shared/idempotent';
export type { IdempotencyScope } from '@sent/shared/idempotent';

export function useIdempotencyKey(scene: string): IdempotencyScope {
  const scope = createIdempotencyScope(scene);
  onScopeDispose(() => scope.clear());
  return scope;
}

import { ref, watch, type Ref } from 'vue';
import { useRoute } from 'vue-router';

/**
 * 列表页分页状态记忆（按路由隔离，存 sessionStorage）。
 *
 * 列表页组件在路由切换时会被销毁，原先各自 `ref(1) / ref(20)` 必然回到第一页。
 * 此处把「当前页码 + 每页条数」按 `route.path` 存进 sessionStorage：
 * - 同一标签页内切到别的页面再回来，自动恢复上次选择；
 * - 不同页面 key 不同，互不干扰；
 * - 关闭标签页即清空，不会让下次打开莫名其妙停在第 5 页；
 * - 查询条件变化（页面里 page.value = 1）会同步写回缓存，符合预期。
 *
 * 用 sessionStorage 而非 localStorage，是为了区分「会话内的页面往返」与「下一次全新访问」。
 */
const STORAGE_PREFIX = 'sent:pagination:';
const MIN_SIZE = 1;
const MAX_SIZE = 200;

interface PaginationState {
  page: number;
  size: number;
}

export interface PaginationOption {
  /** 无缓存时使用的每页条数，默认 20（商品列表等可传 10）。 */
  defaultSize?: number;
  /**
   * 自定义记忆键（默认按 `route.path`）。
   * 同一路由下有多张表时必须各自指定，否则两张表共用一个键、互相覆盖页码。
   */
  key?: string;
  /** false = 不读也不写 sessionStorage（同页的次要表格用，避免污染主表的记忆） */
  remember?: boolean;
}

function readState(key: string): PaginationState | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.sessionStorage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<PaginationState>;
    const page = Number(parsed?.page);
    const size = Number(parsed?.size);
    if (!Number.isInteger(page) || page < 1) return null;
    if (!Number.isInteger(size) || size < MIN_SIZE || size > MAX_SIZE) return null;
    return { page, size };
  } catch {
    return null; // 隐私模式 / 手工改坏的值，一律回退默认值
  }
}

export function usePagination(options: PaginationOption = {}): {
  page: Ref<number>;
  size: Ref<number>;
} {
  const route = useRoute();
  const key = `${STORAGE_PREFIX}${options.key || route.path}`;
  const remember = options.remember !== false;
  const defaultSize = options.defaultSize ?? 20;
  const saved = remember ? readState(key) : null;

  const page = ref(saved?.page ?? 1);
  const size = ref(saved?.size ?? defaultSize);

  watch(
    [page, size],
    ([p, s]) => {
      if (!remember || typeof window === 'undefined') return;
      try {
        window.sessionStorage.setItem(key, JSON.stringify({ page: p, size: s }));
      } catch {
        // 存储不可用（隐私模式 / 配额满）时静默降级为不记忆，不影响分页本身
      }
    },
    { immediate: true },
  );

  return { page, size };
}

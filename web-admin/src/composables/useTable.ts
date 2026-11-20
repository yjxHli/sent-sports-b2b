/**
 * 列表页门面（P3-F1）：把「拉列表 + loading + 分页 + 翻页 + 整表导出」收在一处。
 *
 * 重构前每个列表页都要自己写一遍这四段：
 * 1. `load()`：置 loading、`unwrap(api.list({page,size,...}))`、写 rows/total、catch 里清空 + 提示；
 * 2. `onPage`：改页码再 `load()`；`onSize`：改条数 + 回第 1 页 + `load()`；`onSearch`：回第 1 页 + `load()`；
 * 3. `exportAll()`：`for (p=1; p<=50; p++)` 按 100 一页循环拉全，`list.length < 100` 或
 *    `all.length >= total` 收尾 —— 全仓 13 份逐字相同的拷贝，改一处漏十二处；
 * 4. `total` 忘了 `Number()`（后端 Long 序列化成字符串，ElPagination 只收数字）。
 *
 * 本模块只做「编排」，不碰 HTTP 细节：`fetch` 由页面传入并自行 `unwrap()`，
 * 因此筛选条件的形状仍由各页决定，门面只负责按 page/size 把它们送进去。
 *
 * 两种模式：
 * - `server`（默认）：后端真分页，`fetch({page,size,...条件})` 返回 `{list,total}`；
 * - `client`：结果集很小（系统参数、销售排行），一次拉全后在前端切片，
 *   此时 `fetch` 可以忽略分页参数、直接返回数组，分页只是展示层切片。
 *
 * 约定：页面按需解构，且**命名与模板保持一致**（`rows`/`total`/`page`/`size`/
 * `loading`/`load`/`onPage`/`onSize`/`onSearch`），模板就不用跟着改；
 * 页面原有命名靠解构重命名保留（`{ rows: writeOffRows, load: loadWriteOffs }`）。
 */
import { onMounted, ref, type Ref } from 'vue';
import { ElMessage } from 'element-plus';
import { usePagination } from './usePagination';

/** 后端统一分页返回体（`list` + `total`，total 可能是字符串化的 Long） */
export interface PageData<T> {
  list?: T[] | null;
  total?: number | string | null;
}

/** 翻页参数：门面只写 page/size，其余键由页面的 query 提供 */
interface PagingParams {
  page: number;
  size: number;
}

export interface TableOptions<T, Q extends Record<string, unknown>> {
  /** 默认 `server`（后端真分页）；`client` 表示一次拉全、前端切片 */
  mode?: 'server' | 'client';
  /** 取数函数（内部自行 unwrap）：server 返回分页体，client 可直接返回数组 */
  fetch: (params: PagingParams & Q) => Promise<PageData<T> | T[] | null | undefined>;
  /** 当前筛选条件；用 getter 而非对象快照，保证翻页时读到最新值 */
  query?: () => Q;
  /** 无缓存时的每页条数，默认 20（商品/回款等页传 10） */
  defaultSize?: number;
  /** 分页记忆键（默认按 route.path）；同一页面多张表必须各给一个，否则页码互相覆盖 */
  paginationKey?: string;
  /** false = 该表不记忆页码（同页次要表格用） */
  rememberPagination?: boolean;
  /** 加载失败提示；传空串表示静默（页面自己决定空表语义） */
  errorMessage?: string;
  /** 结果落地前的加工：补派生列 / 纠偏 total / client 模式下的关键字过滤 */
  transform?: (ctx: { list: T[]; total: number }) => { list: T[]; total: number };
  /** 是否在 onMounted 自动加载，默认 true */
  immediate?: boolean;
  /** 列表落地后的回调：用于「刷新后保持选中行」这类页面级副作用 */
  onLoaded?: (rows: T[], total: number) => void;
  /** 请求结束后的回调（含失败）：用于量高度、解除骨架屏等 */
  onSettled?: () => void;
}

export interface UseTableReturn<T> {
  /** 当前页数据（client 模式下是切片结果） */
  rows: Ref<T[]>;
  total: Ref<number>;
  loading: Ref<boolean>;
  page: Ref<number>;
  size: Ref<number>;
  /** 按当前 page/size/筛选条件重新加载 */
  load: () => Promise<void>;
  onPage: (value: number) => void;
  onSize: (value: number) => void;
  /** 查询：回到第 1 页再加载 */
  onSearch: () => void;
  /** 整表导出：按当前筛选循环拉完所有分页（单页 100，最多 50 页） */
  fetchAll: () => Promise<T[]>;
}

/** 整表导出上限：50 页 × 100 行，防止误点把百万行拉进浏览器 */
const EXPORT_PAGE_SIZE = 100;
const EXPORT_MAX_PAGES = 50;

export function useTable<T, Q extends Record<string, unknown> = Record<string, any>>(
  options: TableOptions<T, Q>,
): UseTableReturn<T> {
  const clientMode = options.mode === 'client';
  const rows = ref([]) as Ref<T[]>;
  const total = ref(0);
  const loading = ref(false);
  const { page, size } = usePagination({
    defaultSize: options.defaultSize,
    key: options.paginationKey,
    remember: options.rememberPagination,
  });
  /** client 模式下的全量结果（翻页只切片，不重新请求） */
  const source = ref([]) as Ref<T[]>;

  /** 归一：分页体取 list，数组原样收，null 当空表 */
  function normalize(payload: PageData<T> | T[] | null | undefined): { list: T[]; total: number } {
    if (Array.isArray(payload)) return { list: payload, total: payload.length };
    const list = (payload?.list ?? []) as T[];
    return { list, total: Number(payload?.total ?? 0) };
  }

  function params(targetPage: number, targetSize: number): PagingParams & Q {
    return { ...(options.query?.() ?? ({} as Q)), page: targetPage, size: targetSize };
  }

  /** client 模式：按 page/size 从全量结果里切出当前页 */
  function slice(): void {
    const maxPage = Math.max(1, Math.ceil(source.value.length / size.value));
    if (page.value > maxPage) page.value = maxPage;
    const start = (page.value - 1) * size.value;
    rows.value = source.value.slice(start, start + size.value);
    options.onLoaded?.(rows.value, total.value);
  }

  async function load(): Promise<void> {
    loading.value = true;
    try {
      const normalized = normalize(await options.fetch(params(page.value, size.value)));
      const next = options.transform ? options.transform(normalized) : normalized;
      total.value = next.total;
      if (clientMode) {
        source.value = next.list;
        slice();
        return;
      }
      rows.value = next.list;
      options.onLoaded?.(next.list, next.total);
    } catch (error) {
      rows.value = [];
      total.value = 0;
      if (options.errorMessage !== '') {
        ElMessage.error((error as Error)?.message || '列表加载失败');
      }
    } finally {
      loading.value = false;
      options.onSettled?.();
    }
  }

  function onPage(value: number): void {
    page.value = value;
    if (clientMode) {
      slice();
      return;
    }
    void load();
  }

  function onSize(value: number): void {
    size.value = value;
    page.value = 1;
    if (clientMode) {
      slice();
      return;
    }
    void load();
  }

  function onSearch(): void {
    // 两种模式都要重新请求：server 模式换页码，client 模式的关键字过滤发生在拉全之后
    page.value = 1;
    void load();
  }

  async function fetchAll(): Promise<T[]> {
    if (clientMode) {
      return normalize(await options.fetch(params(1, EXPORT_PAGE_SIZE))).list;
    }
    const all: T[] = [];
    for (let p = 1; p <= EXPORT_MAX_PAGES; p += 1) {
      const data = normalize(await options.fetch(params(p, EXPORT_PAGE_SIZE)));
      all.push(...data.list);
      if (data.list.length < EXPORT_PAGE_SIZE || all.length >= data.total) break;
    }
    return all;
  }

  if (options.immediate !== false) {
    onMounted(() => { void load(); });
  }

  return { rows, total, loading, page, size, load, onPage, onSize, onSearch, fetchAll };
}

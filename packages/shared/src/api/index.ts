/**
 * 管理端 / 客服端共用 API（2026-11-28 P2 批次）。
 *
 * <p>这两端此前各存了一份**逐字节相同**的 auth / order / file API，改一处漏一处。
 * 但两端不能共用一个全局 `http` 实例：令牌存储前缀（`sent_admin` / `cs-token_`）、
 * `aud`、登出跳转路径都不同——共用会让客服端拿着管理端令牌请求，直接 401。</p>
 *
 * <p><b>解法：工厂注入而非全局单例。</b>调用方把自己的 HTTP 实例传进来：
 * ```ts
 * const apis = createSharedAdminApi(http);   // http 来自本端的 @/utils/http
 * export const { authApi, orderApi, fileApi } = apis;
 * ```
 * 这样 baseURL / TokenStorage / 刷新策略仍由各端适配层决定，共享的只有「接口契约与路径」。
 */
import type { SentHttp, SentRequestConfig } from '../http';
import type {
  AdminOrderDetailVO,
  AdminOrderVO,
  MeVO,
  PageQuery,
  PageResult,
  RefundVO,
  TokenVO,
} from '../types';

/** 仅使用共享 API 的最小 HTTP 门面：便于测试注入假实例。 */
export type SharedApiHttp = Pick<SentHttp, 'get' | 'post'>;

export interface LoginPayload {
  username: string;
  password: string;
  aud?: string;
  deviceId?: string;
}

export interface FileVO {
  fileId: string;
  url: string;
  fileName: string;
  size: number;
}

export interface ShipLineCmd {
  skuId: number;
  qty: number;
}

export interface ShipCmd {
  carrier?: string;
  trackingNo?: string;
  lines: ShipLineCmd[];
}

export interface AgentOrderItemCmd {
  skuId: number;
  qty: number;
  customerTypeCode?: string;
}

export interface AgentCreateCmd {
  customerId: number;
  remark: string;
  items: AgentOrderItemCmd[];
}

export interface ShipOrderResult {
  orderNo: string;
  status: string;
  shippedLines?: ShipLineCmd[];
}

/** 客服侧栏「顾客历史订单」行（金额后端已同时给分与元） */
export interface AdminCustomerOrderVO {
  orderNo: string;
  status: string;
  payableCent?: number | string | null;
  payableYuan?: string | null;
  createdAt?: number | string | null;
}

/** 注入本端 HTTP 实例，生成这一端的 API 对象。 */
export function createSharedAdminApi(http: SharedApiHttp) {
  const authApi = {
    login: (payload: LoginPayload) => http.post<TokenVO>(
      '/auth/login', payload, { skipAuth: true, skipAuthFlow: true },
    ),
    refresh: (refreshToken: string) => http.post<TokenVO>(
      '/auth/refresh', { refreshToken }, { skipAuth: true, skipAuthFlow: true },
    ),
    logout: (refreshToken: string) => http.post<void>(
      '/auth/logout', { refreshToken }, { skipAuthFlow: true },
    ),
    /** 当前员工资料 + permissions[] + dataScope */
    me: () => http.get<MeVO>('/admin/me'),
  };

  const orderApi = {
    list: (query: PageQuery & { status?: string; keyword?: string }) =>
      http.get<PageResult<AdminOrderVO>>('/admin/orders', query),
    /** 详情按业务单号查询；后端返回四维度状态 + 金额（分）+ 订单行。 */
    detail: (orderNo: string) => http.get<AdminOrderDetailVO>(`/admin/orders/${orderNo}`),
    agentCreate: (cmd: AgentCreateCmd) => http.post<AdminOrderVO>('/admin/orders', cmd),
    split: (id: number, lines: ShipLineCmd[]) =>
      http.post<AdminOrderVO>(`/admin/orders/${id}/split`, { lines }),
    /** config 用于注入 Idempotency-Key；发货是写操作，必须防重复提交 */
    ship: (id: string, cmd: ShipCmd, config?: SentRequestConfig) =>
      http.post<ShipOrderResult>(`/admin/orders/${id}/ship`, cmd, config),
    /**
     * 关闭订单（危险操作）：调用方先用 utils/confirm.ts 的 openConfirm 换取一次性令牌，
     * 再把 grant.headers 作为 config 传入；本方法不自行持有令牌。
     */
    close: (orderNo: string, reason: string, config?: SentRequestConfig) =>
      http.post<AdminOrderVO>(`/admin/orders/${orderNo}/close`, { reason }, config),
    /** 订单列表批量软删（只写管理端删除时间） */
    batchDelete: (ids: string[], config?: SentRequestConfig) =>
      http.post<{ deleted: number }>('/admin/orders/batch-delete', { ids }, config),
    /** 用户端退款单查询 */
    refunds: (query: PageQuery) => http.get<PageResult<RefundVO>>('/refunds', query),
    /**
     * 某位顾客的最近订单（客服工作台右侧侧栏）。
     * 后端 limit 归一：缺省 10、上限 20。
     */
    customerOrders: (customerId: number, limit = 10) =>
      http.get<AdminCustomerOrderVO[]>(`/admin/customers/${customerId}/orders`, { limit }),
  };

  const fileApi = {
    upload: (file: File) => {
      const form = new FormData();
      form.append('file', file, file.name);
      return http.post<FileVO>('/files', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 60_000,
      });
    },
    url: (fileId?: string | null): string => (fileId ? `/api/files/${fileId}` : ''),
  };

  return { authApi, orderApi, fileApi };
}

export type SharedAdminApis = ReturnType<typeof createSharedAdminApi>;

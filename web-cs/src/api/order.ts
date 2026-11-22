/**
 * 订单与发货 API（P6-16，对应 AdminOrderController）。
 * 关单为危险操作，必须携带确认令牌；发货逐行选量，支持多次发货。
 */
import { http, type SentRequestConfig } from '@/utils/http';
import type { PageQuery, PageResult, AdminOrderVO, AdminOrderDetailVO, RefundVO } from '@sent/shared';

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

export const orderApi = {
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
   * 关闭订单（危险操作）：orderNo 必须是业务单号，reason 必填并会写入操作日志。
   * 调用方先用 utils/confirm.ts 的 openConfirm 换取一次性令牌，
   * 再把 grant.headers（含 X-Confirm-Token）作为 config 传入；本方法不自行持有令牌。
   */
  close: (orderNo: string, reason: string, config?: SentRequestConfig) =>
    http.post<AdminOrderVO>(`/admin/orders/${orderNo}/close`, { reason }, config),
  /** 订单列表批量软删（只写管理端删除时间，不动金额/支付/客户侧可见性） */
  batchDelete: (ids: string[], config?: SentRequestConfig) =>
    http.post<{ deleted: number }>('/admin/orders/batch-delete', { ids }, config),

  /** 用户端退款单查询（管理端退款审核端点待后端补齐） */
  refunds: (query: PageQuery) => http.get<PageResult<RefundVO>>('/refunds', query),

  /**
   * 某位顾客的最近订单（客服工作台右侧「顾客历史订单」侧栏）。
   * 后端 limit 归一：缺省 10、上限 20，不会因为传大数字把侧栏撑爆。
   */
  customerOrders: (customerId: number, limit = 10) =>
    http.get<AdminCustomerOrderVO[]>(`/admin/customers/${customerId}/orders`, { limit }),
};

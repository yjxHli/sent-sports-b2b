/**
 * 库存 API（P6-15，对应 AdminStockController）。
 * 盘点审批是危险操作：需先经 utils/confirm.ts 取 X-Confirm-Token。
 */
import { http, type SentRequestConfig } from '@/utils/http';
import type { PageQuery, PageResult, StockVO, StockFlowVO } from '@sent/shared';

export interface InboundCmd {
  skuId: number;
  qty: number;
  reason: string;
  remark?: string;
}

export interface LossCmd {
  skuId: number;
  qty: number;
  reason: string;
  remark?: string;
}

export interface InboundVO {
  skuId: number;
  afterPhysicalQty: number;
  afterAvailableQty: number;
}

export interface CheckVO {
  checkNo: string;
  skuId: number;
  bookQty: number;
  status: string;
}

export interface CheckCountVO {
  checkNo: string;
  bookQty: number;
  actualQty: number;
  diffQty: number;
  status: string;
}

export interface CheckApproveVO {
  checkNo: string;
  status: string;
  afterPhysicalQty: number;
}

export const stockApi = {
  list: (query: PageQuery & { skuId?: number }) =>
    http.get<PageResult<StockVO>>('/admin/stock', query),
  flows: (query: PageQuery & { skuId?: number; bizType?: string }) =>
    http.get<PageResult<StockFlowVO>>('/admin/stock/flows', query),
  inbound: (cmd: InboundCmd, config?: SentRequestConfig) =>
    http.post<InboundVO>('/admin/stock/inbound', cmd, config),
  loss: (cmd: LossCmd, config?: SentRequestConfig) =>
    http.post<InboundVO>('/admin/stock/loss', cmd, config),
  createCheck: (skuId: number) => http.post<CheckVO>('/admin/stock/checks', { skuId }),
  count: (id: string, actualQty: number) =>
    http.put<CheckCountVO>(`/admin/stock/checks/${id}/count`, { actualQty }),
  /** confirmToken 同时进 body（后端 DTO）与请求头（P5 确认令牌拦截器） */
  approve: (id: string, confirmToken: string) => http.post<CheckApproveVO>(
    `/admin/stock/checks/${id}/approve`,
    { confirmToken },
    { headers: { 'X-Confirm-Token': confirmToken } },
  ),
};

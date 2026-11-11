/**
 * 售后 API（用户端，对齐 RefundController P4-22）。
 * 申请退款需逐行金额（分）与凭证；我的售后为只读列表/详情/申诉。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';

export interface RefundVO {
  refundNo: string;
  orderNo: string;
  status: string | null;
  amountCent?: number | null;
  amountYuan?: string | null;
}

export interface RefundDetailVO {
  refundNo: string;
  orderNo: string;
  status: string | null;
  amountCent?: number | null;
  amountYuan?: string | null;
  type?: string | null;
  reason?: string | null;
  applicantType?: string | null;
  appealCount?: number | null;
  createdAtMillis?: number | null;
  refundedAtMillis?: number | null;
}

export const refundApi = {
  apply: (cmd: {
    orderNo: string;
    lines: { skuId: number; amountCent: number }[];
    reason: string;
    proof: string;
  }) => http.post<RefundVO>('/refunds', cmd),
  list: (query: PageQuery & { status?: string }) =>
    http.get<PageResult<RefundVO>>('/refunds', query),
  detail: (refundNo: string) =>
    http.get<RefundDetailVO>(`/refunds/${refundNo}`),
  appeal: (refundNo: string) =>
    http.post<RefundVO>(`/refunds/${refundNo}/appeal`),
};

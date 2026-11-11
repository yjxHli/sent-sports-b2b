/**
 * 订单 API（用户端，对齐 OrderController P4-16）。
 * 下单需幂等键；取价参数（会员/协议折扣率、阶梯价）后端为桩，UI 传 0 默认值。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult } from '@sent/shared';
import type { SentRequestConfig } from '@sent/shared/http';

export interface OrderItemCmd {
  skuId: number;
  qty: number;
  customerTypeCode?: string | null;
  memberDiscountRate?: number;
  agreementDiscountRate?: number;
  allowStacking?: boolean;
  ladderTiers?: { minQty: number; rateOff: number }[];
  /** UV 定制件：后端下单时据此生成 custom_order，客户随后上传图稿走审图。 */
  customFlag?: boolean;
}

export interface OrderVO {
  orderNo: string;
  status: string | null;
  payStatus: string | null;
  shipStatus?: string | null;
  refundStatus?: string | null;
  payableCent?: number | null;
  payableYuan?: string | null;
  goodsAmountCent?: number | null;
  goodsAmountYuan?: string | null;
  freightCent?: number | null;
  freightYuan?: string | null;
  pointDeductCent?: number | null;
  pointDeductYuan?: string | null;
}

export interface OrderListItem {
  orderNo: string;
  status: string | null;
  payStatus: string | null;
  refundStatus?: string | null;
  payableCent?: number | null;
  payableYuan?: string | null;
}

export const orderApi = {
  create: (cmd: {
    customerId: number;
    idemKey: string;
    availablePoints: number;
    freightFreeThresholdCent: number;
    freightFeeCent: number;
    items: OrderItemCmd[];
  }, config?: SentRequestConfig) => http.post<OrderVO>('/orders', cmd, config),
  list: (query: PageQuery) =>
    http.get<PageResult<OrderListItem>>('/orders', query),
  detail: (orderNo: string) =>
    http.get<OrderVO>(`/orders/${orderNo}`),
  cancel: (orderNo: string) =>
    http.post<void>(`/orders/${orderNo}/cancel`),
  confirm: (orderNo: string, lines: { skuId: number; qty: number }[]) =>
    http.post<OrderVO>(`/orders/${orderNo}/confirm`, { lines }),
};

/**
 * 购物车 API（用户端，对齐 CartController P4-13）。
 * 结算试算（checkoutPreview）委托取价引擎，返回逐行单价与整单应付。
 */
import { http } from '@/utils/http';
import type { SentRequestConfig } from '@sent/shared/http';

export interface CartItemVO {
  itemId: number;
  skuId: number;
  skuName: string;
  qty: number;
  selected: boolean;
}

export interface PreviewItemCmd {
  skuId: number;
  qty: number;
  customerTypeCode?: string | null;
  memberDiscountRate?: number;
  agreementDiscountRate?: number;
  allowStacking?: boolean;
  ladderTiers?: { minQty: number; rateOff: number }[];
}

export interface QuoteLineVO {
  skuId: number;
  unitPriceCent: number;
  unitPriceYuan: string;
  sourceCode: string;
  belowCost: boolean;
  lineTotalCent: number;
  lineTotalYuan: string;
}

export interface QuoteVO {
  lines: QuoteLineVO[];
  goodsAmountCent: number;
  goodsAmountYuan: string;
  freightCent: number;
  freightYuan: string;
  pointDeductCent: number;
  pointDeductYuan: string;
  payableCent: number;
  payableYuan: string;
}

export const cartApi = {
  list: () => http.get<CartItemVO[]>('/cart'),
  add: (skuId: number, qty: number) =>
    http.post<CartItemVO>('/cart/items', { skuId, qty }),
  update: (id: number, qty: number, selected: boolean) =>
    http.put<CartItemVO>(`/cart/items/${id}`, { qty, selected }),
  remove: (id: number) => http.delete<void>(`/cart/items/${id}`),
  checkoutPreview: (cmd: {
    availablePoints: number;
    freightFreeThresholdCent: number;
    freightFeeCent: number;
    items: PreviewItemCmd[];
  }) => http.post<QuoteVO>('/cart/checkout-preview', cmd),
  /** 下单幂等键经 config.headers 注入 */
  _config: (config: SentRequestConfig = {}) => config,
};

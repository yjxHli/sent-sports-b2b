/**
 * 购物车 API（用户端，对齐 CartController P4-13）。
 * 结算试算（checkoutPreview）委托取价引擎，返回逐行单价与整单应付。
 */
import { http } from '@/utils/http';

export interface CartItemVO {
  itemId: number;
  skuId: number;
  skuName: string;
  qty: number;
  selected: boolean;
  /**
   * 商品主图（2026-11-28 补）：个人中心购物车卡要和收藏 / 足迹统一成
   * 「商品图片 + 商品名称 + 商品价格」，没图时前端退回占位图。
   */
  mainImage?: string | null;
  /** 加购时的价格快照（分）；后端 Long 序列化成字符串，用前先 Number() */
  unitPriceCent?: number | string | null;
  /** 加购时的价格快照（元，两位小数字符串） */
  unitPriceYuan?: string | null;
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
  /** 定制费（10 元/件，V33），普通订单为 0 */
  customFeeCent?: number;
  customFeeYuan?: string;
  freightCent: number;
  freightYuan: string;
  pointDeductCent: number;
  pointDeductYuan: string;
  /** 优惠券抵扣（分 / 元）；没有券时为 0 */
  couponDiscountCent: number;
  couponDiscountYuan: string;
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
    /** 优惠券主键；不选传 undefined，后端按门槛实时判定，无效券静默按 0 抵扣 */
    couponId?: number;
    items: PreviewItemCmd[];
  }) => http.post<QuoteVO>('/cart/checkout-preview', cmd),
};

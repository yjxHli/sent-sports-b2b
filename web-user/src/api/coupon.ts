/** 用户端优惠券 API。金额单位分；时间毫秒（JacksonConfig 把 Long 序列化成字符串，前端需 Number()）。 */
import { http } from '@/utils/http';

export interface CouponVO {
  couponId: number;
  customerId: number;
  couponNo: string;
  name: string;
  amountCent: number;
  thresholdCent: number;
  /** UNUSED / USED / EXPIRED */
  status: string;
  /** REGISTER / ACTIVITY / AFTER_SALE / CLAIM */
  source: string;
  reason?: string | null;
  expireAtMillis: number;
  usedAtMillis: number;
  orderNo?: string | null;
  /** NO_THRESHOLD / FULL_REDUCTION / CATEGORY / ENTERPRISE */
  couponType?: string;
  /** AMOUNT 金额券 / PERCENT 折扣券 */
  discountType?: string;
  /** 折扣券支付比例：90 = 9 折；金额券固定 100 */
  discountRate?: number;
  /** ALL / SPU / SKU */
  scopeType?: string;
  scopeIds?: string | null;
  /** ALL / ENTERPRISE */
  customerScope?: string;
}

/** 领券中心卡片：remainQty 为 -1 表示不限量，0 表示已领完 */
export interface ClaimableVO {
  templateId: number;
  name: string;
  amountCent: number;
  thresholdCent: number;
  couponType: string;
  discountType: string;
  discountRate: number;
  scopeType: string;
  scopeIds?: string | null;
  customerScope: string;
  validDays: number;
  remainQty: number;
  claimed: boolean;
}

export const couponApi = {
  mine: () => http.get<CouponVO[]>('/coupons/mine'),
  /**
   * 可用券。传 skuIds 才能判品类券范围（购物车里没有对应商品时不算可用）；
   * 数组用逗号拼接，Spring 直接绑成 List<Long>。
   */
  usable: (goodsCent: number, skuIds?: Array<number | string>) =>
    http.get<CouponVO[]>('/coupons/usable', {
      goodsCent,
      skuIds: skuIds && skuIds.length ? skuIds.join(',') : undefined,
    }),
  claimable: () => http.get<ClaimableVO[]>('/coupons/claimable'),
  claim: (templateId: number) => http.post<{ couponNo: string }>(`/coupons/claim/${templateId}`),
};

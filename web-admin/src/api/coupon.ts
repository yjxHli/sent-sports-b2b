/**
 * 管理端优惠券 API（客户与会员 → 优惠券创建 / 优惠券管理）。
 * 对应 AdminCouponController；金额单位分，Long 被 JacksonConfig 序列化成字符串，前端统一 Number()。
 */
import { http } from '@/utils/http';
import type { SentRequestConfig } from '@sent/shared/http';
import type { PageQuery, PageResult } from '@sent/shared';

/** NO_THRESHOLD 无门槛 / FULL_REDUCTION 满减 / CATEGORY 品类 / ENTERPRISE 企业专属 */
export type CouponType = 'NO_THRESHOLD' | 'FULL_REDUCTION' | 'CATEGORY' | 'ENTERPRISE';
export type CouponDiscountType = 'AMOUNT' | 'PERCENT';

export interface CouponTemplateVO {
  templateId: number;
  name: string;
  amountCent: number;
  thresholdCent: number;
  validDays: number;
  couponType: CouponType;
  /** AMOUNT 金额券 / PERCENT 折扣券 */
  discountType: CouponDiscountType;
  /** 折扣券支付比例：90 = 9 折 */
  discountRate: number;
  /** ALL / SPU / SKU */
  scopeType: string;
  scopeIds?: string | null;
  /** ALL / ENTERPRISE */
  customerScope: string;
}

/** 券模板列表行（券号 / 创建人 / 创建时间） */
export interface CouponTemplatePageVO extends CouponTemplateVO {
  templateNo: string;
  claimable: number;
  totalQty: number;
  claimedQty: number;
  status: string;
  createdBy: number;
  creatorName: string;
  createdAtMillis: number;
}

export interface CouponIssueVO {
  couponId: number;
  customerId: number;
  couponNo: string;
  name: string;
  amountCent: number;
  thresholdCent: number;
  source: string;
  reason?: string | null;
  createdAtMillis: number;
  couponType: CouponType;
  discountType: CouponDiscountType;
  discountRate: number;
}

/** 发放记录列表行：带客户昵称与券状态 */
export interface CouponIssuePageVO extends CouponIssueVO {
  customerName: string;
  status: string;
  expireAtMillis: number;
}

export interface CreateTemplateCmd {
  name: string;
  couponType: CouponType;
  discountType: CouponDiscountType;
  amountCent: number;
  thresholdCent: number;
  discountRate: number;
  scopeType: 'ALL' | 'SPU' | 'SKU';
  scopeIds?: string | null;
  customerScope: 'ALL' | 'ENTERPRISE';
  validDays: number;
  claimable: number;
  totalQty: number;
  status: string;
}

export const couponApi = {
  /** 发放下拉：全部可用券档位（含自建券） */
  templates: () => http.get<CouponTemplateVO[]>('/admin/coupons/templates'),
  /** 券模板分页列表：券号 / 创建人 / 创建时间，软删的不出现 */
  templatesPage: (query: PageQuery & { couponType?: string; keyword?: string }) =>
    http.get<PageResult<CouponTemplatePageVO>>('/admin/coupons/templates/page', query),
  createTemplate: (cmd: CreateTemplateCmd) =>
    http.post<{ templateId: number; templateNo: string }>('/admin/coupons/templates', cmd),
  batchDeleteTemplates: (ids: string[], config?: SentRequestConfig) =>
    http.post<{ deleted: number }>('/admin/coupons/templates/batch-delete', { ids }, config),

  issues: (limit = 50) => http.get<CouponIssueVO[]>('/admin/coupons/issues', { limit }),
  issuesPage: (query: PageQuery & { source?: string; keyword?: string }) =>
    http.get<PageResult<CouponIssuePageVO>>('/admin/coupons/issues/page', query),
  batchDeleteIssues: (ids: string[], config?: SentRequestConfig) =>
    http.post<{ deleted: number }>('/admin/coupons/issues/batch-delete', { ids }, config),

  /**
   * customerIds 为空 = 全员发放（企业专属券只发企业客户）；thresholdCent 不传 = 沿用模板门槛，
   * 传 0 = 无门槛。scene：ACTIVITY 平台活动 / AFTER_SALE 售后补偿（站内信文案不同）。
   */
  issue: (cmd: {
    templateId: number;
    customerIds?: number[];
    thresholdCent?: number | null;
    reason?: string;
    scene?: 'ACTIVITY' | 'AFTER_SALE';
  }, config?: SentRequestConfig) => http.post<number>('/admin/coupons/issue', cmd, config),
};

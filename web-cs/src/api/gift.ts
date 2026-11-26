/**
 * 客服发券 API（V64，对应 CsCouponGiftController）。
 *
 * 与运营发券（`/admin/coupons`）完全分开：客服只能发面额 ≤ 20 元的券给当前会话这一个客户，
 * 上限由服务端判定，前端拿到的 options 已经是过滤后的档位。
 */
import { http } from '@/utils/http';

export interface GiftTemplateVO {
  templateId: number;
  name: string;
  /** 面额（分） */
  amountCent: number;
  thresholdCent: number;
  validDays: number;
  couponType: string;
  discountType: string;
  discountRate: number;
  scopeType: string;
  scopeIds?: string | null;
  customerScope: string;
}

export interface GiftRecordVO {
  couponId: number;
  couponNo: string;
  name: string;
  amountCent: number;
  thresholdCent: number;
  status: string;
  /** ACTIVITY 平台活动 / AFTER_SALE 客服补偿 / CLAIM 领券中心 */
  source: string;
  reason?: string | null;
  expireAtMillis: number;
  createdAtMillis: number;
}

export interface GiftResultVO {
  issued: number;
  name: string;
  amountCent: number;
}

export const couponGiftApi = {
  /** 可发券档位（面额 ≤ 20 元） */
  options: () => http.get<GiftTemplateVO[]>('/admin/chat/coupons/options'),
  /** 该客户的发券记录 */
  records: (customerId: number, limit = 10) =>
    http.get<GiftRecordVO[]>(`/admin/chat/customers/${customerId}/coupons`, { limit }),
  /** 发券 + 站内信 */
  gift: (customerId: number, templateId: number, reason: string) =>
    http.post<GiftResultVO>(`/admin/chat/customers/${customerId}/coupons`, { templateId, reason }),
};

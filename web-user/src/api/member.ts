/**
 * 会员 API（用户端，对齐 MemberController P4-28）。
 * 读面：等级进度 / 积分 / 额度应收 / 专属商品（金额分 + 元）。写面（授级/积分调整）属管理侧，本端只读。
 */
import { http } from '@/utils/http';

export interface LevelVO {
  customerId: number;
  level: string;
  growthValue: number;
  currentThreshold: number;
  nextThreshold: number;
  progress: number;
  validToMillis: number;
}

export interface PointVO {
  customerId: number;
  availablePoints: number;
}

export interface CreditVO {
  customerId: number;
  creditLimitCent: number;
  creditLimitYuan: string;
  receivableCent: number;
  receivableYuan: string;
}

export interface ExclusiveProductVO {
  productId: string;
  productName: string;
  priceCent: number;
  priceYuan: string;
}

export const memberApi = {
  level: () => http.get<LevelVO>('/member/level'),
  points: () => http.get<PointVO>('/member/points'),
  credit: () => http.get<CreditVO>('/member/credit'),
  exclusiveProducts: () => http.get<ExclusiveProductVO[]>('/member/exclusive-products'),
};

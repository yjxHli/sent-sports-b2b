/**
 * 客户与会员 API（P6-18，对应 AdminCustomerController / MemberController）。
 * 客户列表后端带数据范围（ALL/TEAM/SELF），前端不做数据过滤。
 */
import { http } from '@/utils/http';
import type { PageQuery, PageResult, CustomerVO, MemberLevelVO, PointVO } from '@sent/shared';

/**
 * 客户等级码唯一真源：与真库 `customer.level_code` 值域及后端
 * `CustomerOperationServiceImpl.LEVEL_CODES` 白名单严格一致。
 * 此前三个页面各自写死 NORMAL/SILVER/GOLD/PLATINUM/VIP，后端 MemberLevel
 * 却是 NORMAL/VIP1/VIP2，选中即抛 50001 系统异常。
 */
export const CUSTOMER_LEVELS = [
  { value: 'NORMAL', label: '普通会员' },
  { value: 'LV1', label: '一级会员' },
  { value: 'LV2', label: '二级会员' },
  { value: 'LV3', label: '三级会员' },
] as const;

export interface TransferVO {
  customerId: number;
  fromStaffId: number;
  toStaffId: number;
}

export interface GrantLevelVO {
  customerId: number;
  level: string;
  validToMillis: number;
}

export interface AdjustPointVO {
  customerId: number;
  beforePoints: number;
  afterPoints: number;
  delta: number;
}

export interface MemberCreditVO {
  customerId: number;
  creditLimitCent: number;
  creditLimitYuan?: string;
  /** 已占用授信（分） */
  creditUsedCent?: number;
  /** 可用额度（分）= 总额度 − 已用，由后端算好，前端不二次计算 */
  availableCent: number;
  /** 未结应收（分） */
  receivableCent?: number;
}

export interface ExclusiveProductVO {
  productId: string;
  productName: string;
  priceCent: number;
  priceYuan?: string;
}

export const customerApi = {
  list: (query: PageQuery & { keyword?: string; level?: string; type?: string }) =>
    http.get<PageResult<CustomerVO>>('/admin/customers', query),
  detail: (id: number) => http.get<CustomerVO>(`/admin/customers/${id}`),
  transfer: (id: number, toStaffId: number, reason: string) =>
    http.post<TransferVO>(`/admin/customers/${id}/transfer`, { toStaffId, reason }),
  grantLevel: (id: number, level: string, validToMillis: number, reason: string) =>
    http.post<GrantLevelVO>(`/admin/customers/${id}/level`, { level, validToMillis, reason }),
  adjustPoints: (id: number, delta: number, reason: string, bizType?: string, bizNo?: string) =>
    http.post<AdjustPointVO>(`/admin/customers/${id}/points`, { delta, reason, bizType, bizNo }),

  /**
   * 企业认证审核：通过置 enterprise_profile.status=ACTIVE，驳回置 REJECTED。
   * 此前该端点在服务端不存在，两个按钮都会落到「系统异常」。
   */
  auditEnterprise: (id: number, approved: boolean, remark?: string) =>
    http.post<{ customerId: number; status: string; approved: boolean }>(
      `/admin/enterprise/${id}/audit`, { approved, remark }),

  // ---- 会员域（用户端公开接口，管理端会员页复用时显式传 customerId） ----
  memberLevel: (customerId?: number) =>
    http.get<MemberLevelVO>('/member/level', customerId ? { customerId } : undefined),
  memberPoints: (customerId?: number) =>
    http.get<PointVO>('/member/points', customerId ? { customerId } : undefined),
  memberCredit: (customerId?: number) =>
    http.get<MemberCreditVO>('/member/credit', customerId ? { customerId } : undefined),
  exclusiveProducts: (customerId?: number) =>
    http.get<ExclusiveProductVO[]>('/member/exclusive-products',
      customerId ? { customerId } : undefined),
};

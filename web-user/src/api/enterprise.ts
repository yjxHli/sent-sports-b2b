/**
 * 用户端企业工具 API（个人中心 → 企业工具）。
 *
 * 一个接口带出企业档案、授信与合同：`GET /api/me/enterprise`。
 * 个人客户没有企业档案时返回 `status: 'NONE'`（不是 404），
 * 前端据此展示「资质不够 → 去申请企业认证」。
 */
import { http } from '@/utils/http';

export interface EnterpriseContractVO {
  contractNo?: string | null;
  amountCent?: number | null;
  amountYuan?: string | null;
  /** ACTIVE / EXPIRED / TERMINATED */
  status?: string | null;
  signDate?: string | null;
  expireDate?: string | null;
  discountRate?: number | null;
}

export interface EnterpriseOverviewVO {
  /** NONE / PENDING / ACTIVE / REJECTED */
  status?: string | null;
  companyName?: string | null;
  creditCode?: string | null;
  creditLimitCent?: number | null;
  creditUsedCent?: number | null;
  creditAvailableCent?: number | null;
  /** 账期天数（先采后付） */
  paymentTermDays?: number | null;
  creditExpireAtMillis?: number | null;
  auditRemark?: string | null;
  auditedAtMillis?: number | null;
  submittedAtMillis?: number | null;
  contracts?: EnterpriseContractVO[] | null;
}

export const enterpriseApi = {
  overview: () => http.get<EnterpriseOverviewVO>('/me/enterprise'),
};

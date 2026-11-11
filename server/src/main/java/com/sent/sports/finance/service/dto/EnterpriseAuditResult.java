package com.sent.sports.finance.service.dto;

/**
 * 企业认证审核结果（授信前置动作）。
 *
 * @param customerId 企业客户号
 * @param status     审核后的档案状态（ACTIVE / REJECTED）
 * @param approved   本次是「通过」还是「驳回」
 */
public record EnterpriseAuditResult(long customerId, String status, boolean approved) {
}

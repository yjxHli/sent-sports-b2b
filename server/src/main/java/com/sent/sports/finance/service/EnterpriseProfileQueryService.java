package com.sent.sports.finance.service;

/**
 * 企业档案只读端口：供用户端「审核进度」查询使用。
 *
 * <p>跨模块只经 service 接口（ArchUnit 规则 4），auth 模块不直接碰 finance mapper。
 * 返回的是企业提交过的原始资料 + 审核意见，前端据此展示「审核中 / 已通过 / 已驳回」。</p>
 */
public interface EnterpriseProfileQueryService {

    /** 按注册手机号找企业档案；不存在返回 null。 */
    AuditProgress findByPhone(String phone);

    /**
     * @param auditRemark 驳回原因（通过时为 null）
     * @param auditedAtMillis 审核时间，未审核为 null
     * @param submittedAtMillis 资料提交时间
     */
    record AuditProgress(long customerId, String phone, String companyName, String creditCode,
                         String licenseFileId, String status, String auditRemark,
                         Long auditedAtMillis, Long submittedAtMillis) {
    }
}

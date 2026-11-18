package com.sent.sports.finance.service;

/**
 * 企业档案注册端口（跨模块只暴露 service 接口，符合 ArchUnit 规则 4）。
 * 企业注册落 PENDING，管理端审核后才改为 ACTIVE/REJECTED。
 */
public interface EnterpriseProfileRegistrationService {

    /**
     * 新建待审核企业档案。
     *
     * @param licenseFileId 营业执照文件 fileId（二进制不进 JSON 列）
     */
    void registerPending(long customerId, String companyName, String creditCode, String licenseFileId);
}

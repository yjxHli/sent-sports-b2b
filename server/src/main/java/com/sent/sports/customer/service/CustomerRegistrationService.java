package com.sent.sports.customer.service;

import com.sent.sports.customer.service.dto.RegisteredCustomer;

/** 客户注册端口（跨模块只暴露 service 接口 + DTO，符合 ArchUnit 规则 4）。 */
public interface CustomerRegistrationService {

    /** 写入 customer 表并返回新客户身份；手机号重复由 uk_customer_phone 兜底。 */
    RegisteredCustomer register(String phone, String rawPassword, String nickname);

    /** 企业注册：客户类型写 ENTERPRISE，企业档案由 finance 模块单独落 PENDING。 */
    RegisteredCustomer registerEnterprise(String phone, String rawPassword, String nickname);
}

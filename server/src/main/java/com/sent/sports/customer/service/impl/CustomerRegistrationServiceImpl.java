package com.sent.sports.customer.service.impl;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.common.util.SensitiveWordFilter;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customer.service.CustomerRegistrationService;
import com.sent.sports.customer.service.dto.RegisteredCustomer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 客户注册落库实现：默认个人客户 / LV1 / 头像为空（前端用昵称首字兜底）。 */
@Service
public class CustomerRegistrationServiceImpl implements CustomerRegistrationService {

    private final CustomerMapper customerMapper;
    private final IdGen idGen;
    private final SensitiveWordFilter sensitiveWordFilter;

    public CustomerRegistrationServiceImpl(CustomerMapper customerMapper, IdGen idGen,
                                           SensitiveWordFilter sensitiveWordFilter) {
        this.customerMapper = customerMapper;
        this.idGen = idGen;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    @Override
    @UseCase
    public RegisteredCustomer register(String phone, String rawPassword, String nickname) {
        return create(phone, rawPassword, nickname, "PERSON");
    }

    @Override
    @UseCase
    public RegisteredCustomer registerEnterprise(String phone, String rawPassword, String nickname) {
        return create(phone, rawPassword, nickname, "ENTERPRISE");
    }

    private RegisteredCustomer create(String phone, String rawPassword, String nickname, String customerType) {
        // 注册昵称风控（2026-11-19）：个人注册与企业注册都走这里，是唯一的落库口。
        // 当前昵称由手机号尾号自动生成、必然合规，但校验放在落库口才能覆盖日后「注册可填昵称」的改动。
        if (sensitiveWordFilter.contains(nickname)) {
            throw new BizException(ErrorCode.SENSITIVE_NICKNAME);
        }
        CustomerPO po = new CustomerPO();
        po.setCustomerNo(idGen.next("CUS"));
        po.setPhone(phone);
        po.setPasswordHash(rawPassword);
        po.setNickname(nickname);
        po.setType(customerType);
        po.setLevelCode("LV1");
        po.setGrowthValue(0L);
        po.setPointsBalance(0L);
        po.setStatus("ACTIVE");
        po.setTokenVersion(0L);
        po.setDeleted(0);
        try {
            customerMapper.insert(po);
        } catch (DuplicateKeyException e) {
            // 防重只用 DB 唯一索引，不引分布式锁
            throw new BizException(ErrorCode.BIZ_REJECT, "该手机号已注册");
        }
        return new RegisteredCustomer(po.getId(), phone, nickname, po.getCustomerNo());
    }
}

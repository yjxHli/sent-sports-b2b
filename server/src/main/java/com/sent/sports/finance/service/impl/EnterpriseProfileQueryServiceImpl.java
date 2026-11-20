package com.sent.sports.finance.service.impl;

import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.service.EnterpriseProfileQueryService;
import org.springframework.stereotype.Service;

/** 企业档案只读实现：SQL 收口在 mapper，service 只做端口转发。 */
@Service
public class EnterpriseProfileQueryServiceImpl implements EnterpriseProfileQueryService {

    private final EnterpriseProfileMapper enterpriseProfileMapper;

    public EnterpriseProfileQueryServiceImpl(EnterpriseProfileMapper enterpriseProfileMapper) {
        this.enterpriseProfileMapper = enterpriseProfileMapper;
    }

    @Override
    public AuditProgress findByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return enterpriseProfileMapper.selectAuditProgressByPhone(phone.trim());
    }
}

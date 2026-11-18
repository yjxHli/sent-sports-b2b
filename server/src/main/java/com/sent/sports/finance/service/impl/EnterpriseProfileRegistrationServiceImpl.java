package com.sent.sports.finance.service.impl;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.service.EnterpriseProfileRegistrationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 企业档案注册实现：只写 PENDING 档案，审核仍走 {@code EnterpriseAuditUseCase}。 */
@Service
public class EnterpriseProfileRegistrationServiceImpl implements EnterpriseProfileRegistrationService {

    private final EnterpriseProfileMapper enterpriseProfileMapper;

    public EnterpriseProfileRegistrationServiceImpl(EnterpriseProfileMapper enterpriseProfileMapper) {
        this.enterpriseProfileMapper = enterpriseProfileMapper;
    }

    @Override
    @UseCase
    public void registerPending(long customerId, String companyName, String creditCode, String licenseFileId) {
        try {
            enterpriseProfileMapper.insertPending(customerId, companyName, creditCode, licenseFileId,
                    companyName, creditCode);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.BIZ_REJECT, "该企业档案已存在");
        }
    }
}

package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.service.BatchDeleteExecutor;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 企业审核列表：只打 enterprise_profile.deleted 标记，不退还授信、不改审核状态。 */
@Service
public class EnterpriseAuditBatchDeleteUseCase {

    private final EnterpriseProfileMapper enterpriseProfileMapper;

    public EnterpriseAuditBatchDeleteUseCase(EnterpriseProfileMapper enterpriseProfileMapper) {
        this.enterpriseProfileMapper = enterpriseProfileMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return BatchDeleteExecutor.numeric(ids, enterpriseProfileMapper::batchMarkDeleted);
    }
}

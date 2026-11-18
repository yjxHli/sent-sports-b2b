package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.finance.mapper.ContractMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 管理端合同列表批量删除：只打 {@code contract.deleted}，合同金额与到期日不改动。 */
@Service
public class ContractBatchDeleteUseCase {

    private final ContractMapper contractMapper;

    public ContractBatchDeleteUseCase(ContractMapper contractMapper) {
        this.contractMapper = contractMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return contractMapper.batchMarkDeleted(BatchIds.normalize(ids));
    }
}

package com.sent.sports.system.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.service.BatchDeleteExecutor;
import com.sent.sports.system.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 操作日志：只打 sys_operation_log.deleted 标记，before/after JSON 原文保留。 */
@Service
public class OperationLogBatchDeleteUseCase {

    private final OperationLogMapper operationLogMapper;

    public OperationLogBatchDeleteUseCase(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return BatchDeleteExecutor.numeric(ids, operationLogMapper::batchMarkDeleted);
    }
}

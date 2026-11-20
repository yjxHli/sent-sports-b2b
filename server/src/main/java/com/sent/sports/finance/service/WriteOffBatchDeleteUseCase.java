package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.service.BatchDeleteExecutor;
import com.sent.sports.finance.mapper.WriteOffMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 核销与冲销明细：只打软删标记，不改回款已核销金额。 */
@Service
public class WriteOffBatchDeleteUseCase {

    private final WriteOffMapper writeOffMapper;

    public WriteOffBatchDeleteUseCase(WriteOffMapper writeOffMapper) {
        this.writeOffMapper = writeOffMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return BatchDeleteExecutor.numeric(ids, writeOffMapper::batchMarkDeleted);
    }
}

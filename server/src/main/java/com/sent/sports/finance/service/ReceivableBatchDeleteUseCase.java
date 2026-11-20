package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.service.BatchDeleteExecutor;
import com.sent.sports.finance.mapper.ReceivableMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 应收台账：只打软删标记，应收金额与已核销金额一律不动。 */
@Service
public class ReceivableBatchDeleteUseCase {

    private final ReceivableMapper receivableMapper;

    public ReceivableBatchDeleteUseCase(ReceivableMapper receivableMapper) {
        this.receivableMapper = receivableMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return BatchDeleteExecutor.numeric(ids, receivableMapper::batchMarkDeleted);
    }
}

package com.sent.sports.customize.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 管理端定制列表批量删除：只打 {@code custom_order.deleted}，图稿与审图日志保留可追溯。 */
@Service
public class CustomBatchDeleteUseCase {

    private final CustomOrderMapper customOrderMapper;

    public CustomBatchDeleteUseCase(CustomOrderMapper customOrderMapper) {
        this.customOrderMapper = customOrderMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return customOrderMapper.batchMarkDeleted(BatchIds.normalize(ids));
    }
}

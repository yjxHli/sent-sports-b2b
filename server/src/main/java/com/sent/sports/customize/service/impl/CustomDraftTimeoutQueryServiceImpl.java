package com.sent.sports.customize.service.impl;

import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.service.CustomDraftTimeoutQueryService;
import com.sent.sports.customize.service.dto.CustomDraftWaiting;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 图稿上传超时查询实现：只做 PO → 中立 DTO 的搬运，判定逻辑留给领域策略与任务层。 */
@Service
public class CustomDraftTimeoutQueryServiceImpl implements CustomDraftTimeoutQueryService {

    private final CustomOrderMapper customOrderMapper;

    public CustomDraftTimeoutQueryServiceImpl(CustomOrderMapper customOrderMapper) {
        this.customOrderMapper = customOrderMapper;
    }

    @Override
    public List<CustomDraftWaiting> findWaitingDrafts(LocalDateTime paidFrom, int limit) {
        return customOrderMapper.selectDraftWaiting(paidFrom, limit).stream()
                .map(row -> new CustomDraftWaiting(
                        row.getCustomNo(),
                        row.getOrderNo(),
                        row.getCustomerId() == null ? 0L : row.getCustomerId(),
                        row.getPaidAt(),
                        row.getRemindCount() == null ? 0 : row.getRemindCount(),
                        row.getLastRemindAt()))
                .toList();
    }
}

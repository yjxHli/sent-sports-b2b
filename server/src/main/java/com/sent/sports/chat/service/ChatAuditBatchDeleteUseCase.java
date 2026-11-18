package com.sent.sports.chat.service;

import com.sent.sports.chat.mapper.ChatAuditMapper;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 客服审查批量删除用例：与发票管理同口径——只打 {@code audit_deleted} 软删标记，
 * 不改会话状态、聊天记录或客服小结；确认令牌在控制器层校验。
 */
@Service
public class ChatAuditBatchDeleteUseCase {

    private final ChatAuditMapper auditMapper;

    public ChatAuditBatchDeleteUseCase(ChatAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        List<Long> normalized = ids == null ? List.of() : ids.stream()
                .map(id -> id == null ? "" : id.trim())
                .filter(id -> !id.isEmpty())
                .distinct()
                .map(id -> {
                    try {
                        return Long.valueOf(id);
                    } catch (NumberFormatException e) {
                        throw new BizException(ErrorCode.PARAM_INVALID, "会话号不合法");
                    }
                })
                .sorted(Comparator.naturalOrder())
                .toList();
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先选择要删除的会话");
        }
        return auditMapper.batchMarkAuditDeleted(normalized);
    }
}

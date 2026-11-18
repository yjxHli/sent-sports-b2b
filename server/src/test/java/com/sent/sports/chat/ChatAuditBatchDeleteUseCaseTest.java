package com.sent.sports.chat;

import com.sent.sports.chat.mapper.ChatAuditMapper;
import com.sent.sports.chat.service.ChatAuditBatchDeleteUseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客服审查批量删除用例（TDD 红）：对齐发票管理——只打软删标记，
 * 确认令牌由控制器校验，这里只负责归一化 ID 并落库。
 */
class ChatAuditBatchDeleteUseCaseTest extends BaseUseCaseTest {

    @Mock
    private ChatAuditMapper auditMapper;

    @InjectMocks
    private ChatAuditBatchDeleteUseCase useCase;

    @Test
    void marksSessionsDeletedWithNormalizedNumericIds() {
        when(auditMapper.batchMarkAuditDeleted(List.of(101L, 102L))).thenReturn(2);

        int deleted = useCase.delete(List.of(" 102 ", "101", "102", ""));

        assertThat(deleted).isEqualTo(2);
        verify(auditMapper).batchMarkAuditDeleted(List.of(101L, 102L));
    }

    @Test
    void rejectsNonNumericIdBeforeTouchingDatabase() {
        assertThatThrownBy(() -> useCase.delete(List.of("101", "abc")))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PARAM_INVALID);
        verify(auditMapper, never()).batchMarkAuditDeleted(org.mockito.ArgumentMatchers.anyList());
    }
}

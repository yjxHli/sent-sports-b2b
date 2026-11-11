package com.sent.sports.finance;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.service.EnterpriseAuditUseCase;
import com.sent.sports.finance.service.dto.EnterpriseAuditResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企业认证审核用例（TDD 红）：此前前端 {@code POST /admin/enterprise/{id}/audit}
 * 打在没有实现的端点上，审核通过与驳回都直接变成「系统异常」。
 * <p>本用例把审核语义钉死在 enterprise_profile.status：
 * 通过 → {@code ACTIVE}，驳回 → {@code REJECTED}；档案不存在 → 40401，客户号非法 → 40001。</p>
 */
class EnterpriseAuditUseCaseTest extends BaseUseCaseTest {

    private static final long CUSTOMER_ID = 91154L;

    @Mock
    private EnterpriseProfileMapper enterpriseProfileMapper;

    @InjectMocks
    private EnterpriseAuditUseCase useCase;

    @Test
    @DisplayName("审核通过：把企业档案状态置为 ACTIVE")
    void auditApprovedMarksActive() {
        when(enterpriseProfileMapper.updateStatus(CUSTOMER_ID, EnterpriseAuditUseCase.STATUS_ACTIVE)).thenReturn(1);

        EnterpriseAuditResult r = useCase.audit(CUSTOMER_ID, true, "资料齐全");

        verify(enterpriseProfileMapper).updateStatus(CUSTOMER_ID, EnterpriseAuditUseCase.STATUS_ACTIVE);
        assertEquals(CUSTOMER_ID, r.customerId());
        assertEquals(EnterpriseAuditUseCase.STATUS_ACTIVE, r.status());
        assertTrue(r.approved());
    }

    @Test
    @DisplayName("审核驳回：把企业档案状态置为 REJECTED")
    void auditRejectedMarksRejected() {
        when(enterpriseProfileMapper.updateStatus(CUSTOMER_ID, EnterpriseAuditUseCase.STATUS_REJECTED)).thenReturn(1);

        EnterpriseAuditResult r = useCase.audit(CUSTOMER_ID, false, "资料不完整");

        verify(enterpriseProfileMapper).updateStatus(CUSTOMER_ID, EnterpriseAuditUseCase.STATUS_REJECTED);
        assertEquals(EnterpriseAuditUseCase.STATUS_REJECTED, r.status());
        assertEquals(false, r.approved());
    }

    @Test
    @DisplayName("企业档案不存在：抛 40401，不再降级成系统异常")
    void auditMissingProfileThrowsNotFound() {
        when(enterpriseProfileMapper.updateStatus(anyLong(), anyString())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> useCase.audit(CUSTOMER_ID, true, "资料齐全"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("客户号非法：抛 40001 且不触碰数据库")
    void auditInvalidCustomerIdRejected() {
        BizException ex = assertThrows(BizException.class,
                () -> useCase.audit(0L, true, "资料齐全"));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(enterpriseProfileMapper, never()).updateStatus(anyLong(), anyString());
    }
}

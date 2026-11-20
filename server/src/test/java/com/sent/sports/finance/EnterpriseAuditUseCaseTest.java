package com.sent.sports.finance;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.service.EnterpriseAuditUseCase;
import com.sent.sports.finance.service.dto.EnterpriseAuditResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企业认证审核用例（TDD 红 → 绿）。
 * <p>2026-11-19 新增：驳回原因必须落库并在用户端审核进度页可见；
 * 驳回不允许空原因，否则企业永远不知道问题在哪里。</p>
 */
class EnterpriseAuditUseCaseTest extends BaseUseCaseTest {

    private static final long CUSTOMER_ID = 91154L;
    private static final long NOW = 1_793_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Mock
    private EnterpriseProfileMapper enterpriseProfileMapper;

    private EnterpriseAuditUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new EnterpriseAuditUseCase(enterpriseProfileMapper, CLOCK);
    }

    @Test
    @DisplayName("审核通过：状态置为 ACTIVE，审核意见一并落库")
    void auditApprovedMarksActive() {
        when(enterpriseProfileMapper.updateAudit(
                CUSTOMER_ID, EnterpriseAuditUseCase.STATUS_ACTIVE, "资料齐全", NOW)).thenReturn(1);

        EnterpriseAuditResult r = useCase.audit(CUSTOMER_ID, true, "资料齐全");

        verify(enterpriseProfileMapper).updateAudit(
                CUSTOMER_ID, EnterpriseAuditUseCase.STATUS_ACTIVE, "资料齐全", NOW);
        assertEquals(CUSTOMER_ID, r.customerId());
        assertEquals(EnterpriseAuditUseCase.STATUS_ACTIVE, r.status());
        assertTrue(r.approved());
    }

    @Test
    @DisplayName("审核驳回：状态置为 REJECTED，具体原因落库供用户查看")
    void auditRejectedPersistsReason() {
        when(enterpriseProfileMapper.updateAudit(
                CUSTOMER_ID, EnterpriseAuditUseCase.STATUS_REJECTED, "营业执照模糊，请重新上传", NOW)).thenReturn(1);

        EnterpriseAuditResult r = useCase.audit(CUSTOMER_ID, false, "营业执照模糊，请重新上传");

        verify(enterpriseProfileMapper).updateAudit(
                CUSTOMER_ID, EnterpriseAuditUseCase.STATUS_REJECTED, "营业执照模糊，请重新上传", NOW);
        assertEquals(EnterpriseAuditUseCase.STATUS_REJECTED, r.status());
        assertEquals(false, r.approved());
    }

    @Test
    @DisplayName("驳回原因不能为空：抛 40001 且不触碰数据库")
    void auditRejectedWithoutReasonRejected() {
        BizException ex = assertThrows(BizException.class,
                () -> useCase.audit(CUSTOMER_ID, false, "   "));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(enterpriseProfileMapper, never()).updateAudit(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("企业档案不存在：抛 40401，不再降级成系统异常")
    void auditMissingProfileThrowsNotFound() {
        when(enterpriseProfileMapper.updateAudit(anyLong(), anyString(), anyString(), anyLong())).thenReturn(0);

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
        verify(enterpriseProfileMapper, never()).updateAudit(anyLong(), anyString(), anyString(), anyLong());
    }
}

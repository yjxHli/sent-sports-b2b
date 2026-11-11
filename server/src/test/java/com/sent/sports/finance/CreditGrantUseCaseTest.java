package com.sent.sports.finance;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.domain.CreditLine;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.repository.CreditLineRepository;
import com.sent.sports.finance.service.CreditGrantUseCase;
import com.sent.sports.finance.service.dto.CreditGrantCmd;
import com.sent.sports.finance.service.dto.CreditGrantResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企业授信用例测试（P3-42/43，TDD 红）：spec §6.8 / 验收——
 * 额度与账期、有效期（validFrom/validTo）、超额下单拦截依据（仅管理 CreditLine）。
 */
class CreditGrantUseCaseTest extends BaseUseCaseTest {

    @Mock
    private CreditLineRepository creditLineRepository;
    @Mock
    private EnterpriseProfileMapper enterpriseProfileMapper;
    @InjectMocks
    private CreditGrantUseCase useCase;
    @Captor
    private ArgumentCaptor<CreditLine> creditCaptor;

    @Test
    void createsNewCreditLineWhenAbsent() {
        when(creditLineRepository.findByCustomerId(10L)).thenReturn(Optional.empty());

        CreditGrantResult r = useCase.grant(new CreditGrantCmd(10L, 50000L, 60L, 100L, 200L, 7L, "首授信"));

        verify(creditLineRepository).save(creditCaptor.capture());
        CreditLine saved = creditCaptor.getValue();
        assertEquals(10L, saved.getCustomerId());
        assertEquals(50000L, saved.getTotalCent());
        assertEquals(0L, saved.getUsedCent());
        assertEquals(60L, saved.getAccountPeriodDays());
        assertEquals(100L, saved.getValidFromMillis());
        assertEquals(200L, saved.getValidToMillis());
        assertEquals(CreditLine.STATUS_ACTIVE, saved.getStatus());

        assertEquals(10L, r.customerId());
        assertEquals(50000L, r.totalCent());
        assertEquals(50000L, r.availableCent());
        assertEquals(200L, r.validToMillis());
        assertEquals(CreditLine.STATUS_ACTIVE, r.status());
    }

    @Test
    void updatesExistingCreditLineKeepingUsedCent() {
        CreditLine existing = new CreditLine(10L, 30000L, 10000L, 30L, 1L, 2L, CreditLine.STATUS_ACTIVE);
        when(creditLineRepository.findByCustomerId(10L)).thenReturn(Optional.of(existing));

        CreditGrantResult r = useCase.grant(new CreditGrantCmd(10L, 50000L, 60L, 300L, 400L, 7L, "调额"));

        verify(creditLineRepository).save(creditCaptor.capture());
        CreditLine saved = creditCaptor.getValue();
        assertEquals(50000L, saved.getTotalCent());
        assertEquals(10000L, saved.getUsedCent());
        assertEquals(60L, saved.getAccountPeriodDays());
        assertEquals(300L, saved.getValidFromMillis());
        assertEquals(400L, saved.getValidToMillis());
        assertEquals(CreditLine.STATUS_ACTIVE, saved.getStatus());

        assertEquals(50000L, r.totalCent());
        assertEquals(40000L, r.availableCent());
    }

    @Test
    void customerMustBePositive() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.grant(new CreditGrantCmd(0L, 50000L, 60L, 100L, 200L, 7L, "x")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(creditLineRepository, never()).save(any());
    }

    @Test
    void totalMustBePositive() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.grant(new CreditGrantCmd(10L, 0L, 60L, 100L, 200L, 7L, "x")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(creditLineRepository, never()).save(any());
    }

    @Test
    void validToMustBeAfterValidFrom() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.grant(new CreditGrantCmd(10L, 50000L, 60L, 200L, 100L, 7L, "x")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(creditLineRepository, never()).save(any());
    }

    /**
     * 授信必须同时落到 enterprise_profile：客户列表的「授信额度」来自于此，
     * 只写内存仓储时界面永远停在「未授信」。
     */
    @Test
    @DisplayName("授信写库：已有企业档案走 UPDATE，额度/账期/到期时间全部落库")
    void grantPersistsCreditToEnterpriseProfile() {
        when(creditLineRepository.findByCustomerId(10L)).thenReturn(Optional.empty());
        when(enterpriseProfileMapper.updateCredit(10L, 50000L, 60L, 200L)).thenReturn(1);

        useCase.grant(new CreditGrantCmd(10L, 50000L, 60L, 100L, 200L, 7L, "首授信"));

        verify(enterpriseProfileMapper).updateCredit(10L, 50000L, 60L, 200L);
        verify(enterpriseProfileMapper, never()).insertCredit(anyLong(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("授信写库：无企业档案时补建档案（企业名称用「企业客户+ID」兜底）")
    void grantInsertsProfileWhenAbsent() {
        when(creditLineRepository.findByCustomerId(10L)).thenReturn(Optional.empty());
        when(enterpriseProfileMapper.updateCredit(10L, 50000L, 60L, 200L)).thenReturn(0);

        useCase.grant(new CreditGrantCmd(10L, 50000L, 60L, 100L, 200L, 7L, "首授信"));

        verify(enterpriseProfileMapper).insertCredit(10L, "企业客户10", 50000L, 60L, 200L);
    }
}

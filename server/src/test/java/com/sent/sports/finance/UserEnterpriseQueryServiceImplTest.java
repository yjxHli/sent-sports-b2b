package com.sent.sports.finance;

import com.sent.sports.finance.dto.UserEnterpriseDtos;
import com.sent.sports.finance.mapper.ContractMapper;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.mapper.po.MyContractRow;
import com.sent.sports.finance.mapper.po.MyEnterpriseRow;
import com.sent.sports.finance.service.impl.UserEnterpriseQueryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户端「企业工具」查询（TDD 红→绿）。
 *
 * <p>个人客户点进企业工具是正常路径，不能回 404；因此没有企业档案时统一返回
 * {@code status = NONE}，由前端渲染「资质不够 + 去申请企业认证」。</p>
 *
 * <p>同时锁住可用额度的下限：历史数据里已用额度可能大于总额（授信上限下调过），
 * 透出负数会让用户以为倒欠平台。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserEnterpriseQueryServiceImplTest {

    private static final long CUSTOMER = 9002L;

    @Mock
    private EnterpriseProfileMapper enterpriseProfileMapper;
    @Mock
    private ContractMapper contractMapper;

    @InjectMocks
    private UserEnterpriseQueryServiceImpl service;

    @Test
    @DisplayName("个人客户没有企业档案：status=NONE，额度全 0，不查合同")
    void personalCustomerGetsNoneStatus() {
        when(enterpriseProfileMapper.selectMyProfile(CUSTOMER)).thenReturn(null);

        UserEnterpriseDtos.OverviewVO vo = service.overview(CUSTOMER);

        assertEquals("NONE", vo.status());
        assertEquals(0L, vo.creditLimitCent());
        assertEquals(0L, vo.creditAvailableCent());
        assertNull(vo.companyName());
        assertTrue(vo.contracts().isEmpty());
        verify(contractMapper, never()).selectMyContracts(anyLong());
    }

    @Test
    @DisplayName("已认证企业：带出授信额度、可用额度、账期与合同列表")
    void activeEnterpriseMapsCreditAndContracts() {
        MyEnterpriseRow profile = new MyEnterpriseRow();
        profile.setCustomerId(CUSTOMER);
        profile.setCompanyName("森特体育用品有限公司");
        profile.setCreditCode("91320000MA1XXXXX0A");
        profile.setStatus("ACTIVE");
        profile.setCreditLimitCent(200_000L);
        profile.setCreditUsedCent(50_000L);
        profile.setPaymentTermDays(30);
        profile.setCreditExpireAt(LocalDateTime.of(2027, 6, 30, 23, 59, 59));
        profile.setSubmittedAt(LocalDateTime.of(2026, 11, 1, 9, 0, 0));
        when(enterpriseProfileMapper.selectMyProfile(CUSTOMER)).thenReturn(profile);

        MyContractRow contract = new MyContractRow();
        contract.setContractNo("CT-2026-001");
        contract.setAmountCent(500_000L);
        contract.setStatus("ACTIVE");
        contract.setSignDate(LocalDate.of(2026, 1, 1));
        contract.setExpireDate(LocalDate.of(2027, 1, 1));
        contract.setDiscountRate(95);
        when(contractMapper.selectMyContracts(CUSTOMER)).thenReturn(List.of(contract));

        UserEnterpriseDtos.OverviewVO vo = service.overview(CUSTOMER);

        assertEquals("ACTIVE", vo.status());
        assertEquals("森特体育用品有限公司", vo.companyName());
        assertEquals(200_000L, vo.creditLimitCent());
        assertEquals(50_000L, vo.creditUsedCent());
        assertEquals(150_000L, vo.creditAvailableCent());
        assertEquals(30, vo.paymentTermDays());
        assertTrue(vo.creditExpireAtMillis() > 0L);
        assertEquals(1, vo.contracts().size());
        UserEnterpriseDtos.ContractItemVO item = vo.contracts().get(0);
        assertEquals("CT-2026-001", item.contractNo());
        assertEquals("5000.00", item.amountYuan());
        assertEquals("2026-01-01", item.signDate());
        assertEquals("2027-01-01", item.expireDate());
        assertEquals(95, item.discountRate());
    }

    @Test
    @DisplayName("已用额度超过总额（历史数据）：可用额度兜底为 0，不返回负数")
    void availableNeverNegative() {
        MyEnterpriseRow profile = new MyEnterpriseRow();
        profile.setCustomerId(CUSTOMER);
        profile.setStatus("ACTIVE");
        profile.setCreditLimitCent(100_000L);
        profile.setCreditUsedCent(180_000L);
        when(enterpriseProfileMapper.selectMyProfile(CUSTOMER)).thenReturn(profile);
        when(contractMapper.selectMyContracts(CUSTOMER)).thenReturn(List.of());

        UserEnterpriseDtos.OverviewVO vo = service.overview(CUSTOMER);

        assertEquals(0L, vo.creditAvailableCent());
    }

    @Test
    @DisplayName("审核驳回：带出驳回原因，供前端提示重新提交")
    void rejectedProfileKeepsAuditRemark() {
        MyEnterpriseRow profile = new MyEnterpriseRow();
        profile.setCustomerId(CUSTOMER);
        profile.setStatus("REJECTED");
        profile.setAuditRemark("营业执照模糊，请重新上传");
        when(enterpriseProfileMapper.selectMyProfile(CUSTOMER)).thenReturn(profile);
        when(contractMapper.selectMyContracts(CUSTOMER)).thenReturn(List.of());

        UserEnterpriseDtos.OverviewVO vo = service.overview(CUSTOMER);

        assertEquals("REJECTED", vo.status());
        assertEquals("营业执照模糊，请重新上传", vo.auditRemark());
    }
}

package com.sent.sports.coupon;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CouponTemplatePO;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.coupon.service.CouponClaimUseCase;
import com.sent.sports.customer.service.CustomerQueryService;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 领券中心：企业专属券个人不可领、重复领取被拒绝、库存扣减失败即领完。 */
class CouponClaimUseCaseTest extends BaseUnitTest {

    @Mock
    private CouponTemplateMapper templateMapper;
    @Mock
    private CouponMapper couponMapper;
    @Mock
    private CustomerQueryService customerQueryService;

    @InjectMocks
    private CouponClaimUseCase useCase;

    @Test
    void personalCannotClaimEnterpriseCoupon() {
        when(templateMapper.selectById(25L)).thenReturn(template(25L, "企业专享满1000减100",
                10000L, 100000L, "ENTERPRISE"));
        when(customerQueryService.typeOf(9002L)).thenReturn("PERSON");

        BizException ex = assertThrows(BizException.class, () -> useCase.claim(9002L, 25L));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("企业"), "错误文案要点明企业专属");
        verify(couponMapper, never()).insert(any());
    }

    @Test
    void enterpriseCustomerClaimsEnterpriseCouponWithSnapshot() {
        when(templateMapper.selectById(25L)).thenReturn(template(25L, "企业专享满1000减100",
                10000L, 100000L, "ENTERPRISE"));
        when(customerQueryService.typeOf(91001L)).thenReturn("ENTERPRISE");
        when(templateMapper.increaseClaimed(25L)).thenReturn(1);

        useCase.claim(91001L, 25L);

        ArgumentCaptor<CustomerCouponPO> captor = ArgumentCaptor.forClass(CustomerCouponPO.class);
        verify(couponMapper).insert(captor.capture());
        CustomerCouponPO po = captor.getValue();
        assertEquals(91001L, po.getCustomerId());
        assertEquals("ENTERPRISE", po.getCouponType());
        assertEquals("ENTERPRISE", po.getCustomerScope());
        assertEquals(10000L, po.getAmountCent());
        assertEquals("CLAIM-25", po.getClaimKey(), "领取凭证按模板唯一，防重复领取");
    }

    @Test
    void soldOutTemplateRejected() {
        when(templateMapper.selectById(20L)).thenReturn(template(20L, "5元无门槛券", 500L, 0L, "ALL"));
        when(templateMapper.increaseClaimed(20L)).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> useCase.claim(9002L, 20L));

        assertEquals(ErrorCode.BIZ_REJECT, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("领完"));
        verify(couponMapper, never()).insert(any());
    }

    private static CouponTemplatePO template(long id, String name, long amountCent,
                                             long thresholdCent, String customerScope) {
        CouponTemplatePO po = new CouponTemplatePO();
        po.setId(id);
        po.setName(name);
        po.setAmountCent(amountCent);
        po.setThresholdCent(thresholdCent);
        po.setCouponType("ENTERPRISE".equals(customerScope) ? "ENTERPRISE" : "NO_THRESHOLD");
        po.setDiscountType("AMOUNT");
        po.setDiscountRate(100);
        po.setScopeType("ALL");
        po.setCustomerScope(customerScope);
        po.setClaimable(1);
        po.setValidDays(30);
        po.setScene("ADMIN");
        po.setStatus("ACTIVE");
        return po;
    }
}

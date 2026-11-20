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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Clock;

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

    private CouponClaimUseCase useCase;

    @BeforeEach
    void fixedClockAtMemberDay() {
        // 用真实 Clock.fixed 而非 Mockito mock：LocalDateTime.now(clock) 会同时调用
        // instant() 与 getZone()，mock 只 stub 前者会 NPE；真实时钟也避免严格桩检查误报。
        useCase = new CouponClaimUseCase(templateMapper, couponMapper, customerQueryService,
                fixedAt(LocalDate.of(2026, 11, 18), 10, 0)); // 2026-11-18 是周三（会员日）
    }

    private static Clock fixedAt(LocalDate date, int hour, int minute) {
        return Clock.fixed(date.atTime(hour, minute).toInstant(ZoneOffset.ofHours(8)),
                ZoneOffset.ofHours(8));
    }

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
    void nonMemberDayRejectsClaimBeforeConsumingStock() {
        // 周五 15:30：非会员日，必须在扣库存之前拒绝
        useCase = new CouponClaimUseCase(templateMapper, couponMapper, customerQueryService,
                fixedAt(LocalDate.of(2026, 11, 20), 15, 30));
        when(templateMapper.selectById(20L)).thenReturn(template(20L, "5元无门槛券", 500L, 0L, "ALL"));

        BizException ex = assertThrows(BizException.class, () -> useCase.claim(9002L, 20L));

        assertEquals(ErrorCode.BIZ_REJECT, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("会员日"), "提示应明确告知仅会员日可领");
        verify(templateMapper, never()).increaseClaimed(20L); // 非会员日不得扣减库存
        verify(couponMapper, never()).insert(any());
    }

    @Test
    void memberDayValidDaysDependOnCouponType() {
        when(templateMapper.selectById(20L)).thenReturn(template(20L, "5元无门槛券", 500L, 0L, "ALL"));
        when(templateMapper.increaseClaimed(20L)).thenReturn(1);
        when(templateMapper.selectById(21L)).thenReturn(fullReduction(21L, "满80减50券"));
        when(templateMapper.increaseClaimed(21L)).thenReturn(1);

        useCase.claim(9002L, 20L);
        useCase.claim(9002L, 21L);
        ArgumentCaptor<CustomerCouponPO> captor = ArgumentCaptor.forClass(CustomerCouponPO.class);
        verify(couponMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        java.util.List<CustomerCouponPO> pos = captor.getAllValues();

        assertEquals(LocalDate.of(2026, 11, 21), pos.get(0).getExpireAt().toLocalDate(),
                "¥5 无门槛券会员日领取后 3 天有效");
        assertEquals(LocalDate.of(2026, 12, 3), pos.get(1).getExpireAt().toLocalDate(),
                "满减券会员日领取后 15 天有效");
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

    private static CouponTemplatePO fullReduction(long id, String name) {
        CouponTemplatePO po = template(id, name, 5000L, 8000L, "ALL");
        po.setCouponType("FULL_REDUCTION");
        return po;
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

package com.sent.sports.coupon;

import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.coupon.service.CouponUseCase;
import com.sent.sports.coupon.service.dto.CouponLine;
import com.sent.sports.customer.service.CustomerQueryService;
import com.sent.sports.product.service.ProductQueryService;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 券抵扣口径（2026-11-18 四类券）：
 * 无门槛/满减按整单金额；品类券只对命中 SPU/SKU 的行金额生效；
 * 企业专属券个人客户不可用。
 */
class CouponUseCaseTest extends BaseUnitTest {

    @Mock
    private CouponMapper couponMapper;
    @Mock
    private CustomerQueryService customerQueryService;
    @Mock
    private ProductQueryService productQueryService;

    @InjectMocks
    private CouponUseCase useCase;

    @Test
    void categoryCouponOnlyDiscountsMatchingSpuLines() {
        when(couponMapper.selectByIdAndCustomer(23L, 9002L)).thenReturn(po(coupon(
                23L, 0L, 0L, "斯伯丁篮球专享9折", Coupon.TYPE_CATEGORY,
                Coupon.DISCOUNT_PERCENT, 90, Coupon.SCOPE_SPU, "9101", Coupon.CUSTOMER_ALL)));
        when(productQueryService.spuIdsOfSkus(List.of(9201L, 9102L)))
                .thenReturn(Map.of(9201L, 9101L, 9102L, 1001L));

        // 9201 是斯伯丁（3990 分），9102 是其它商品（1573 分）：只按 3990 算 9 折，减 399 分
        long discount = useCase.discountFor(23L, 9002L, List.of(
                new CouponLine(9201L, 3990L), new CouponLine(9102L, 1573L)));

        assertEquals(399L, discount, "品类券只对命中 SPU 的行金额打折");
    }

    @Test
    void categoryCouponSkipsNonMatchingOrder() {
        when(couponMapper.selectByIdAndCustomer(24L, 9002L)).thenReturn(po(coupon(
                24L, 0L, 0L, "斯伯丁篮球专享9折", Coupon.TYPE_CATEGORY,
                Coupon.DISCOUNT_PERCENT, 90, Coupon.SCOPE_SKU, "9201,9202", Coupon.CUSTOMER_ALL)));

        assertEquals(0L, useCase.discountFor(24L, 9002L, List.of(new CouponLine(9102L, 1573L))),
                "订单里没有命中 SKU 时品类券不抵扣");
    }

    @Test
    void enterpriseCouponCannotBeUsedByPersonalCustomer() {
        when(couponMapper.selectByIdAndCustomer(25L, 9002L)).thenReturn(po(coupon(
                25L, 10000L, 100000L, "企业专享满1000减100", Coupon.TYPE_ENTERPRISE,
                Coupon.DISCOUNT_AMOUNT, 100, Coupon.SCOPE_ALL, null, Coupon.CUSTOMER_ENTERPRISE)));
        when(customerQueryService.typeOf(9002L)).thenReturn("PERSON");

        assertEquals(0L, useCase.discountFor(25L, 9002L, List.of(new CouponLine(1L, 120000L))),
                "个人客户即使被误发企业券也不能使用");
    }

    @Test
    void enterpriseCouponAppliesForEnterpriseCustomer() {
        when(couponMapper.selectByIdAndCustomer(25L, 91001L)).thenReturn(po(coupon(
                25L, 10000L, 100000L, "企业专享满1000减100", Coupon.TYPE_ENTERPRISE,
                Coupon.DISCOUNT_AMOUNT, 100, Coupon.SCOPE_ALL, null, Coupon.CUSTOMER_ENTERPRISE)));
        when(customerQueryService.typeOf(91001L)).thenReturn("ENTERPRISE");

        assertEquals(10000L, useCase.discountFor(25L, 91001L, List.of(new CouponLine(1L, 120000L))));
    }

    @Test
    void thresholdIsCheckedBeforeDiscount() {
        when(couponMapper.selectByIdAndCustomer(21L, 9002L)).thenReturn(po(coupon(
                21L, 1000L, 2000L, "满20减10券", Coupon.TYPE_FULL_REDUCTION,
                Coupon.DISCOUNT_AMOUNT, 100, Coupon.SCOPE_ALL, null, Coupon.CUSTOMER_ALL)));

        assertEquals(0L, useCase.discountFor(21L, 9002L, List.of(new CouponLine(1L, 1999L))));
        assertEquals(1000L, useCase.discountFor(21L, 9002L, List.of(new CouponLine(1L, 2000L))));
    }

    private static Coupon coupon(long id, long amountCent, long thresholdCent, String name,
                                 String couponType, String discountType, int discountRate,
                                 String scopeType, String scopeIds, String customerScope) {
        return new Coupon(id, 9002L, "CPN-TEST", name, amountCent, thresholdCent,
                couponType, discountType, discountRate, scopeType, scopeIds, customerScope,
                Coupon.UNUSED, "ACTIVITY", null, LocalDateTime.now().plusDays(30), null, null);
    }

    private static CustomerCouponPO po(Coupon c) {
        CustomerCouponPO po = new CustomerCouponPO();
        po.setId(c.id());
        po.setCustomerId(c.customerId());
        po.setCouponNo(c.couponNo());
        po.setName(c.name());
        po.setAmountCent(c.amountCent());
        po.setThresholdCent(c.thresholdCent());
        po.setCouponType(c.couponType());
        po.setDiscountType(c.discountType());
        po.setDiscountRate(c.discountRate());
        po.setScopeType(c.scopeType());
        po.setScopeIds(c.scopeIds());
        po.setCustomerScope(c.customerScope());
        po.setStatus(c.status());
        po.setSource(c.source());
        po.setExpireAt(c.expireAt());
        return po;
    }
}

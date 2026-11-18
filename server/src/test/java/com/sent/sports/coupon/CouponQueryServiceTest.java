package com.sent.sports.coupon;

import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.mapper.CouponMapper;
import com.sent.sports.coupon.mapper.CouponTemplateMapper;
import com.sent.sports.coupon.mapper.po.CustomerCouponPO;
import com.sent.sports.coupon.service.impl.CouponQueryServiceImpl;
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
 * 结算页「可用券」判定（2026-11-18 四类券）：
 * 品类券只对命中 SPU/SKU 的订单行生效，购物车里没有对应商品时不能出现在可用券里，
 * 否则用户选了券、订单却抵扣 0，等于骗一次点击。
 */
class CouponQueryServiceTest extends BaseUnitTest {

    @Mock
    private CouponMapper couponMapper;
    @Mock
    private CouponTemplateMapper templateMapper;
    @Mock
    private CustomerQueryService customerQueryService;
    @Mock
    private ProductQueryService productQueryService;

    @InjectMocks
    private CouponQueryServiceImpl service;

    @Test
    void categoryCouponHiddenWhenCartHasNoMatchingSku() {
        when(couponMapper.selectByCustomer(9002L))
                .thenReturn(List.of(po(23L, "斯伯丁篮球专享9折", Coupon.TYPE_CATEGORY,
                        Coupon.DISCOUNT_PERCENT, 90, Coupon.SCOPE_SPU, "9101")));
        when(productQueryService.spuIdsOfSkus(List.of(9102L))).thenReturn(Map.of(9102L, 1001L));

        assertEquals(0, service.listUsable(9002L, 1573L, List.of(9102L)).size(),
                "购物车里没有斯伯丁商品时，品类券不该出现在可用券里");
    }

    @Test
    void categoryCouponListedWhenCartMatches() {
        when(couponMapper.selectByCustomer(9002L))
                .thenReturn(List.of(po(23L, "斯伯丁篮球专享9折", Coupon.TYPE_CATEGORY,
                        Coupon.DISCOUNT_PERCENT, 90, Coupon.SCOPE_SPU, "9101")));
        when(productQueryService.spuIdsOfSkus(List.of(9201L))).thenReturn(Map.of(9201L, 9101L));

        assertEquals(1, service.listUsable(9002L, 3990L, List.of(9201L)).size(),
                "命中 SPU 时品类券可用");
    }

    @Test
    void withoutSkuIdsKeepsLegacyBehaviour() {
        when(couponMapper.selectByCustomer(9002L))
                .thenReturn(List.of(po(23L, "斯伯丁篮球专享9折", Coupon.TYPE_CATEGORY,
                        Coupon.DISCOUNT_PERCENT, 90, Coupon.SCOPE_SPU, "9101")));

        // 老接口（只有金额）无法判定范围，保持原有行为，避免外部调用方拿到空列表
        assertEquals(1, service.listUsable(9002L, 3990L).size());
    }

    @Test
    void enterpriseCouponHiddenForPersonalCustomer() {
        when(couponMapper.selectByCustomer(9002L))
                .thenReturn(List.of(po(25L, "企业专享满1000减100", Coupon.TYPE_ENTERPRISE,
                        Coupon.DISCOUNT_AMOUNT, 100, Coupon.SCOPE_ALL, null)));
        when(customerQueryService.typeOf(9002L)).thenReturn("PERSON");

        assertEquals(0, service.listUsable(9002L, 120000L, List.of(1L)).size(),
                "个人客户不该在企业券上看到可用");
    }

    private static CustomerCouponPO po(long id, String name, String couponType, String discountType,
                                       int discountRate, String scopeType, String scopeIds) {
        CustomerCouponPO po = new CustomerCouponPO();
        po.setId(id);
        po.setCustomerId(9002L);
        po.setCouponNo("CPN-TEST-" + id);
        po.setName(name);
        po.setAmountCent(discountType.equals("AMOUNT") ? 10000L : 0L);
        po.setThresholdCent(couponType.equals(Coupon.TYPE_ENTERPRISE) ? 100000L : 0L);
        po.setCouponType(couponType);
        po.setDiscountType(discountType);
        po.setDiscountRate(discountRate);
        po.setScopeType(scopeType);
        po.setScopeIds(scopeIds);
        po.setCustomerScope(couponType.equals(Coupon.TYPE_ENTERPRISE)
                ? Coupon.CUSTOMER_ENTERPRISE : Coupon.CUSTOMER_ALL);
        po.setStatus(Coupon.UNUSED);
        po.setSource("ACTIVITY");
        po.setExpireAt(LocalDateTime.now().plusDays(30));
        return po;
    }
}

package com.sent.sports.coupon;

import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 优惠券领域规则：门槛、封顶、过期与核销状态，全部是纯内存计算。 */
class CouponDomainTest extends BaseUnitTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 12, 0);

    @Test
    void usableRequiresUnusedNotExpiredAndThresholdMet() {
        Coupon coupon = coupon(1000, 2000, "UNUSED", NOW.plusDays(1));

        assertTrue(coupon.usableFor(2000, NOW), "刚好达到门槛可用");
        assertFalse(coupon.usableFor(1999, NOW), "差 1 分也不能用");
        Coupon expired = coupon(1000, 2000, "UNUSED", NOW.minusHours(1));
        assertFalse(expired.usableFor(5000, NOW), "已过期的券不可用");
    }

    @Test
    void usedCouponCannotBeUsedAgain() {
        Coupon coupon = coupon(1000, 0, "UNUSED", NOW.plusDays(1));
        coupon.markUsed("SO20261118001", NOW);

        assertEquals("USED", coupon.status());
        assertEquals("SO20261118001", coupon.orderNo());
        assertFalse(coupon.usableFor(10000, NOW), "已核销的券不能重复使用");
    }

    @Test
    void discountIsCappedByFaceValueAndNeverExceedsEligibleAmount() {
        Coupon coupon = coupon(1000, 2000, "UNUSED", NOW.plusDays(1));

        assertEquals(1000, coupon.discountFor(5000), "满减券最多抵面额");
        assertEquals(800, coupon.discountFor(800), "订单不足面额时按订单金额封顶");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("品类折扣券按 9 折计算，抵扣不超过命中行金额")
    void percentCouponDiscountsByRate() {
        Coupon coupon = percentCoupon(90, "SPU", "9101");

        assertEquals(399, coupon.discountFor(3990), "9 折 = 减 10%，3990 分应减 399 分");
        assertEquals(100, coupon.discountFor(1000));
        assertEquals(0, coupon.discountFor(0));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("品类券范围：SPU 命中该商品下所有 SKU，SKU 券只命中指定规格")
    void scopeMatchingForSpuAndSku() {
        Coupon spuCoupon = percentCoupon(90, "SPU", "9101");
        assertTrue(spuCoupon.matchesSku(9201L, 9101L), "SPU 券命中该商品下的 SKU");
        assertFalse(spuCoupon.matchesSku(9102L, 1001L), "其它商品的 SKU 不命中");

        Coupon skuCoupon = percentCoupon(90, "SKU", "9201,9202");
        assertTrue(skuCoupon.matchesSku(9202L, 9101L));
        assertFalse(skuCoupon.matchesSku(9203L, 9101L), "未列入 scope_ids 的规格不命中");
    }

    private static Coupon percentCoupon(int rate, String scopeType, String scopeIds) {
        return new Coupon(23L, 9002L, "CPN009", "斯伯丁篮球专享9折", 0L, 0L,
                Coupon.TYPE_CATEGORY, Coupon.DISCOUNT_PERCENT, rate, scopeType, scopeIds,
                Coupon.CUSTOMER_ALL, Coupon.UNUSED, "ACTIVITY", null, NOW.plusDays(30), null, null);
    }

    private static Coupon coupon(long amountCent, long thresholdCent, String status, LocalDateTime expireAt) {
        return new Coupon(1L, 9002L, "CPN001", "10元券", amountCent, thresholdCent,
                status, "REGISTER", null, expireAt, null, null);
    }
}

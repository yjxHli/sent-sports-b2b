package com.sent.sports.coupon;

import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 券适用性唯一真源（R9）：可用券列表与下单抵扣都必须由 {@link Coupon#applicability} 出结论。
 *
 * <p>历史缺陷：列表按「整单金额 + 范围命中」判、下单按「命中行金额」判，
 * 于是出现「列表说这张券能用，结算却抵 0 元」。下面把两侧口径钉在同一组断言上。</p>
 */
class CouponApplicabilityTest extends BaseUnitTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 12, 0);

    @Test
    @DisplayName("企业专属券：个人客户不可用，企业客户按整单判定")
    void enterpriseCouponExcludesPersonCustomers() {
        Coupon coupon = coupon(Coupon.TYPE_ENTERPRISE, Coupon.DISCOUNT_AMOUNT, 100, 10000L, 100000L,
                Coupon.SCOPE_ALL, null, Coupon.CUSTOMER_ENTERPRISE);

        assertFalse(coupon.applicability("PERSON", 200000L, List.of(), NOW).usable());
        Coupon.Eligibility ok = coupon.applicability("ENTERPRISE", 200000L, List.of(), NOW);
        assertTrue(ok.usable());
        assertEquals(10000L, ok.discountCent());
    }

    @Test
    @DisplayName("已过期 / 已核销 / 未达门槛一律不可用")
    void expiredUsedAndBelowThresholdAreUnusable() {
        Coupon expired = new Coupon(1L, 9002L, "CPN001", "10元券", 1000L, 0L,
                Coupon.UNUSED, "REGISTER", null, NOW.minusMinutes(1), null, null);
        assertFalse(expired.applicability("PERSON", 5000L, List.of(), NOW).usable());

        Coupon used = coupon(Coupon.TYPE_NO_THRESHOLD, Coupon.DISCOUNT_AMOUNT, 100, 1000L, 0L,
                Coupon.SCOPE_ALL, null, Coupon.CUSTOMER_ALL);
        used.markUsed("SO-1", NOW);
        assertFalse(used.applicability("PERSON", 5000L, List.of(), NOW).usable());

        Coupon threshold = coupon(Coupon.TYPE_FULL_REDUCTION, Coupon.DISCOUNT_AMOUNT, 100, 1000L, 20000L,
                Coupon.SCOPE_ALL, null, Coupon.CUSTOMER_ALL);
        assertFalse(threshold.applicability("PERSON", 19999L, List.of(), NOW).usable(),
                "差 1 分也不能用");
        assertTrue(threshold.applicability("PERSON", 20000L, List.of(), NOW).usable());
    }

    @Test
    @DisplayName("品类折扣券：只对命中 SPU 的行金额打折")
    void categoryPercentCouponDiscountsMatchingLinesOnly() {
        Coupon coupon = coupon(Coupon.TYPE_CATEGORY, Coupon.DISCOUNT_PERCENT, 90, 0L, 0L,
                Coupon.SCOPE_SPU, "9101", Coupon.CUSTOMER_ALL);

        Coupon.Eligibility result = coupon.applicability("PERSON", 5563L, List.of(
                new Coupon.Line(9201L, 9101L, 3990L),
                new Coupon.Line(9102L, 1001L, 1573L)), NOW);

        assertTrue(result.usable());
        assertEquals(3990L, result.eligibleCent(), "可抵扣基数只算命中行");
        assertEquals(399L, result.discountCent(), "9 折 = 减 10%");
    }

    @Test
    @DisplayName("品类券：购物车里没有命中商品则不可用（列表与下单同结论）")
    void categoryCouponWithoutMatchingLineIsUnusable() {
        Coupon coupon = coupon(Coupon.TYPE_CATEGORY, Coupon.DISCOUNT_PERCENT, 90, 0L, 0L,
                Coupon.SCOPE_SKU, "9201,9202", Coupon.CUSTOMER_ALL);

        assertFalse(coupon.applicability("PERSON", 5000L,
                List.of(new Coupon.Line(9102L, 1001L, 5000L)), NOW).usable());
        assertTrue(coupon.applicability("PERSON", 5000L, List.of(), NOW).usable(),
                "未传行（老调用方）不做范围判断，保持历史行为");
    }

    @Test
    @DisplayName("门槛按整单商品额判、抵扣按命中行金额算：列表说可用，结算就不会抵 0")
    void thresholdUsesOrderTotalWhileDiscountUsesEligibleLines() {
        Coupon coupon = coupon(Coupon.TYPE_CATEGORY, Coupon.DISCOUNT_AMOUNT, 100, 1000L, 2000L,
                Coupon.SCOPE_SPU, "9101", Coupon.CUSTOMER_ALL);

        // 命中行只有 1500，但整单 2500 已达 2000 门槛：列表与下单都会说「可用」
        Coupon.Eligibility result = coupon.applicability("PERSON", 2500L, List.of(
                new Coupon.Line(9201L, 9101L, 1500L),
                new Coupon.Line(9102L, 1001L, 1000L)), NOW);

        assertTrue(result.usable(), "门槛按整单判定，避免「列表可用、结算抵 0」");
        assertEquals(1500L, result.eligibleCent());
        assertEquals(1000L, result.discountCent(), "面额 1000 未超过命中行金额，全额抵");
    }

    private static Coupon coupon(String couponType, String discountType, int discountRate,
                                 long amountCent, long thresholdCent, String scopeType,
                                 String scopeIds, String customerScope) {
        return new Coupon(1L, 9002L, "CPN001", "测试券", amountCent, thresholdCent,
                couponType, discountType, discountRate, scopeType, scopeIds, customerScope,
                Coupon.UNUSED, "ACTIVITY", null, NOW.plusDays(10), null, null);
    }
}

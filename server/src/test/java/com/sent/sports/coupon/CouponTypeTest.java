package com.sent.sports.coupon;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.coupon.domain.Coupon;
import com.sent.sports.coupon.domain.CouponType;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 券型策略枚举（R7）：范围/门槛/客户域/领券有效期四类差异必须由券型自己回答。
 *
 * <p>这些断言就是「新增券型要改哪些地方」的清单——只要这里不用改，
 * 就说明 {@code CouponTemplateAdminUseCase} / {@code MemberDayPolicy} 里没有残留分支。</p>
 */
class CouponTypeTest extends BaseUnitTest {

    @Test
    @DisplayName("编码解析：大小写与空白容错，未知编码返回 null / 抛 40001")
    void parsesCodeTolerantly() {
        assertEquals(CouponType.CATEGORY, CouponType.from(" category "));
        assertEquals(CouponType.ENTERPRISE, CouponType.from("ENTERPRISE"));
        assertNull(CouponType.from("UNKNOWN"));
        assertNull(CouponType.from(null));

        BizException ex = assertThrows(BizException.class, () -> CouponType.of("UNKNOWN"));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("券型不合法"));
    }

    @Test
    @DisplayName("无门槛券：门槛恒为 0，小额 3 天 / 其余 7 天")
    void noThresholdNormalizesAndShrinksValidity() {
        assertEquals(0L, CouponType.NO_THRESHOLD.normalizeThreshold(5000L));
        assertEquals(3, CouponType.NO_THRESHOLD.claimValidDays(500L, 30), "¥5 无门槛 3 天");
        assertEquals(7, CouponType.NO_THRESHOLD.claimValidDays(1000L, 30), "其余无门槛 7 天");
        assertFalse(CouponType.NO_THRESHOLD.requireThreshold());
        assertFalse(CouponType.NO_THRESHOLD.requireScope());
        assertEquals(Coupon.CUSTOMER_ALL, CouponType.NO_THRESHOLD.customerScope());
    }

    @Test
    @DisplayName("满减券：门槛必填，会员日领券 15 天")
    void fullReductionNeedsThreshold() {
        assertTrue(CouponType.FULL_REDUCTION.requireThreshold());
        assertEquals(15, CouponType.FULL_REDUCTION.claimValidDays(1000L, 30));
        assertEquals(2000L, CouponType.FULL_REDUCTION.normalizeThreshold(2000L), "门槛原样保留");
    }

    @Test
    @DisplayName("品类券：必须选范围，有效期沿用模板")
    void categoryRequiresScope() {
        assertTrue(CouponType.CATEGORY.requireScope());
        assertFalse(CouponType.CATEGORY.requireThreshold());
        assertEquals(30, CouponType.CATEGORY.claimValidDays(0L, 30));
    }

    @Test
    @DisplayName("企业券：门槛必填 + 客户域恒为企业，有效期沿用模板")
    void enterpriseAlwaysScopedToEnterprise() {
        assertTrue(CouponType.ENTERPRISE.requireThreshold());
        assertEquals(Coupon.CUSTOMER_ENTERPRISE, CouponType.ENTERPRISE.customerScope());
        assertEquals(60, CouponType.ENTERPRISE.claimValidDays(10000L, 60));
    }

    @Test
    @DisplayName("validateTemplate：品类券不选商品 / 满减券无门槛都要被拒")
    void validateTemplateCoversScopeAndThreshold() {
        BizException noScope = assertThrows(BizException.class,
                () -> CouponType.CATEGORY.validateTemplate(Coupon.SCOPE_ALL, null, 0L));
        assertEquals(ErrorCode.PARAM_INVALID, noScope.getErrorCode());
        assertTrue(noScope.resolvedMessage().contains("商品"));

        BizException noThreshold = assertThrows(BizException.class,
                () -> CouponType.FULL_REDUCTION.validateTemplate(Coupon.SCOPE_ALL, null, 0L));
        assertEquals(ErrorCode.PARAM_INVALID, noThreshold.getErrorCode());
        assertTrue(noThreshold.resolvedMessage().contains("门槛"));

        // 无门槛券不设门槛是正常配置，不能抛
        CouponType.NO_THRESHOLD.validateTemplate(Coupon.SCOPE_ALL, null, 0L);
        CouponType.CATEGORY.validateTemplate(Coupon.SCOPE_SPU, "9101", 0L);
    }
}

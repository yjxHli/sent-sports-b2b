package com.sent.sports.customize.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P2-27 定制计费测试（TDD 红）：spec §6.5.2。
 * 服务费 = 单价（面数基础价 × 数量阶梯折扣）× 数量；无颜色系数。
 */
class CustomFeeCalculatorTest {

    private static final Map<FaceSide, Long> BASE = Map.of(
            FaceSide.SINGLE, 500L,
            FaceSide.DOUBLE, 800L,
            FaceSide.FULL, 1200L);

    private static final List<CustomFeeConfig.QtyTier> TIERS = List.of(
            new CustomFeeConfig.QtyTier(1, 9, 100),
            new CustomFeeConfig.QtyTier(10, 49, 90),
            new CustomFeeConfig.QtyTier(50, 199, 80),
            new CustomFeeConfig.QtyTier(200, -1, 70));

    private CustomFeeConfig config() {
        return new CustomFeeConfig(BASE, TIERS);
    }

    @Test
    void faceSideRaisesUnitPrice() {
        CustomFeeResult s = CustomFeeCalculator.calc(config(), FaceSide.SINGLE, 1);
        CustomFeeResult d = CustomFeeCalculator.calc(config(), FaceSide.DOUBLE, 1);
        CustomFeeResult f = CustomFeeCalculator.calc(config(), FaceSide.FULL, 1);
        assertEquals(500L, s.getUnitPriceCent());
        assertEquals(800L, d.getUnitPriceCent());
        assertEquals(1200L, f.getUnitPriceCent());
    }

    @Test
    void qtyLadderBoundaries() {
        // 1-9 → 100
        assertEquals(800L, CustomFeeCalculator.calc(config(), FaceSide.DOUBLE, 9).getUnitPriceCent());
        // 10-49 → 90  -> 800*90/100 = 720
        assertEquals(720L, CustomFeeCalculator.calc(config(), FaceSide.DOUBLE, 10).getUnitPriceCent());
        assertEquals(720L, CustomFeeCalculator.calc(config(), FaceSide.DOUBLE, 49).getUnitPriceCent());
        // 50-199 → 80 -> 640
        assertEquals(640L, CustomFeeCalculator.calc(config(), FaceSide.DOUBLE, 50).getUnitPriceCent());
        assertEquals(640L, CustomFeeCalculator.calc(config(), FaceSide.DOUBLE, 199).getUnitPriceCent());
        // 200+ → 70 -> 560
        assertEquals(560L, CustomFeeCalculator.calc(config(), FaceSide.DOUBLE, 200).getUnitPriceCent());
    }

    @Test
    void totalFeeIsUnitTimesQty() {
        CustomFeeResult r = CustomFeeCalculator.calc(config(), FaceSide.FULL, 10);
        assertEquals(1080L, r.getUnitPriceCent()); // 1200*90/100
        assertEquals(10, r.getQty());
        assertEquals(10800L, r.getTotalFeeCent());
    }

    @Test
    void noColorDimension() {
        // 计费只依赖面数与数量，无任何颜色系数；同一输入结果恒定
        CustomFeeResult a = CustomFeeCalculator.calc(config(), FaceSide.DOUBLE, 30);
        CustomFeeResult b = CustomFeeCalculator.calc(config(), FaceSide.DOUBLE, 30);
        assertEquals(a.getTotalFeeCent(), b.getTotalFeeCent());
    }

    // 以下为占位，确保 BizException/ErrorCode 被引用（避免未使用导入告警无关紧要）
    @SuppressWarnings("unused")
    private void unused() {
        BizException e = new BizException(ErrorCode.BIZ_REJECT);
        assertEquals(ErrorCode.BIZ_REJECT, e.getErrorCode());
    }
}

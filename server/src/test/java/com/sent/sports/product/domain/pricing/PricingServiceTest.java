package com.sent.sports.product.domain.pricing;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-03 取价引擎测试（TDD 红）：覆盖验收 A3 / A23。
 * - 一客一价永远终结计算（即便阶梯/协议/会员命中也不再参与）
 * - 协议价压阶梯（企业协议命中，阶梯失效）
 * - 阶梯压会员（VIP 大单走阶梯而非会员价）
 * - 折上折需显式开关（关：仅协议价；开：协议价 × 阶梯折扣率）
 * - 低于底价抛 BELOW_FLOOR_PRICE(40920)
 * - 低于成本价仅置 belowCost 标记、不阻塞下单
 * - 阶梯区间边界（qty=9/10/49/50）
 */
class PricingServiceTest {

    private final PricingService service = new PricingService();

    private PriceContext.ContextBuilder base() {
        return PriceContext.builder()
                .retailPriceCent(10000L)   // 100.00 元
                .floorPriceCent(1000L)     // 10.00 元
                .costPriceCent(800L)       // 8.00 元
                .customerType(CustomerType.PERSON)
                .qty(10);
    }

    /** 一客一价终结：即使阶梯命中，仍按一客一价成交。 */
    @Test
    void customerSkuPriceTerminates() {
        PriceContext ctx = base()
                .customerSkuPriceCent(8000L)                 // 一客一价 80 元
                .memberDiscountRate(95)
                .ladderTiers(List.of(new LadderTier(10, 49, 90)))
                .build();
        PriceResult r = service.calc(ctx);
        assertEquals(8000L, r.getUnitPriceCent());
        assertEquals(PriceSource.CUSTOMER_SKU, r.getSource());
        assertFalse(r.isBelowCost());
    }

    /** 协议价压阶梯：企业协议 5 折，阶梯 4.5 折命中，仍按协议 5 折成交。 */
    @Test
    void agreementBeatsLadder() {
        PriceContext ctx = base()
                .customerType(CustomerType.ENTERPRISE)
                .agreement(new AgreementInfo(50, true))
                .ladderTiers(List.of(new LadderTier(10, 49, 45)))
                .build();
        PriceResult r = service.calc(ctx);
        assertEquals(5000L, r.getUnitPriceCent());
        assertEquals(PriceSource.AGREEMENT, r.getSource());
    }

    /** 阶梯压会员：VIP 95 折，买 50 个命中阶梯 92 折，按阶梯 92 折成交。 */
    @Test
    void ladderBeatsMember() {
        PriceContext ctx = base()
                .qty(50)
                .memberDiscountRate(95)
                .ladderTiers(List.of(new LadderTier(10, 49, 95), new LadderTier(50, -1, 92)))
                .build();
        PriceResult r = service.calc(ctx);
        assertEquals(9200L, r.getUnitPriceCent());
        assertEquals(PriceSource.LADDER, r.getSource());
    }

    /** 折上折关闭：仅协议价，不参与阶梯。 */
    @Test
    void stackingOffAgreementOnly() {
        PriceContext ctx = base()
                .customerType(CustomerType.ENTERPRISE)
                .allowStacking(false)
                .agreement(new AgreementInfo(50, true))
                .ladderTiers(List.of(new LadderTier(10, 49, 45)))
                .build();
        PriceResult r = service.calc(ctx);
        assertEquals(5000L, r.getUnitPriceCent());
        assertEquals(PriceSource.AGREEMENT, r.getSource());
    }

    /** 折上折开启：协议价(5折) × 阶梯折扣率(4.5折) = 2.25 折。 */
    @Test
    void stackingOnCombines() {
        PriceContext ctx = base()
                .customerType(CustomerType.ENTERPRISE)
                .allowStacking(true)
                .agreement(new AgreementInfo(50, true))
                .ladderTiers(List.of(new LadderTier(10, 49, 45)))
                .build();
        PriceResult r = service.calc(ctx);
        // 10000 * 50/100 * 45/100 = 2250
        assertEquals(2250L, r.getUnitPriceCent());
        assertEquals(PriceSource.STACKED, r.getSource());
    }

    /** 低于底价：会员 8 折(80元) < 底价 90 元 → 抛 BELOW_FLOOR_PRICE。 */
    @Test
    void belowFloorThrows() {
        PriceContext ctx = base()
                .floorPriceCent(9000L)
                .costPriceCent(7000L)
                .memberDiscountRate(80)
                .build();
        BizException ex = assertThrows(BizException.class, () -> service.calc(ctx));
        assertEquals(ErrorCode.BELOW_FLOOR_PRICE, ex.getErrorCode());
    }

    /** 低于成本价：会员 98 折(98元) < 成本 99 元 → 仅标记 belowCost，不抛异常。 */
    @Test
    void belowCostMarksNotThrows() {
        PriceContext ctx = base()
                .floorPriceCent(9000L)
                .costPriceCent(9900L)
                .memberDiscountRate(98)
                .build();
        PriceResult r = service.calc(ctx);
        assertEquals(9800L, r.getUnitPriceCent());
        assertEquals(PriceSource.MEMBER, r.getSource());
        assertTrue(r.isBelowCost());
    }

    /** 阶梯区间边界：qty=9 不命中→零售；10/49 命中第一档；50 命中第二档。 */
    @Test
    void ladderBoundary() {
        List<LadderTier> tiers = List.of(new LadderTier(10, 49, 95), new LadderTier(50, -1, 90));
        // qty=9：无阶梯，会员无折扣→零售 10000
        PriceResult r9 = service.calc(base().qty(9).memberDiscountRate(100).ladderTiers(tiers).build());
        assertEquals(10000L, r9.getUnitPriceCent());
        assertEquals(PriceSource.RETAIL, r9.getSource());

        PriceResult r10 = service.calc(base().qty(10).memberDiscountRate(100).ladderTiers(tiers).build());
        assertEquals(9500L, r10.getUnitPriceCent());
        assertEquals(PriceSource.LADDER, r10.getSource());

        PriceResult r49 = service.calc(base().qty(49).memberDiscountRate(100).ladderTiers(tiers).build());
        assertEquals(9500L, r49.getUnitPriceCent());

        PriceResult r50 = service.calc(base().qty(50).memberDiscountRate(100).ladderTiers(tiers).build());
        assertEquals(9000L, r50.getUnitPriceCent());
        assertEquals(PriceSource.LADDER, r50.getSource());
    }
}

package com.sent.sports.product.domain.pricing;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秒杀价（V54）取价规则：**公开活动价压阶梯/会员，但不压合同价**。
 *
 * <p>优先级设计意图：一客一价与协议价是谈判/合同结果，属于"承诺价"，
 * 不能被一个公开活动价悄悄覆盖；秒杀只对走零售/阶梯/会员的散客生效。</p>
 */
class FlashSalePricingTest {

    private final PricingService service = new PricingService();

    private PriceContext.ContextBuilder base() {
        return PriceContext.builder()
                .retailPriceCent(10000L)   // 100.00 元
                .floorPriceCent(1000L)     // 10.00 元
                .costPriceCent(800L)       // 8.00 元
                .customerType(CustomerType.PERSON)
                .qty(1);
    }

    @Test
    @DisplayName("秒杀价优先于阶梯与会员：25 元秒杀 vs 阶梯 9 折 vs 会员 95 折 → 25 元")
    void flashBeatsLadderAndMember() {
        PriceContext ctx = base()
                .qty(10)
                .memberDiscountRate(95)
                .ladderTiers(List.of(new LadderTier(10, 49, 90)))
                .flashSale(2500L, "FLASH#12")
                .build();

        PriceResult r = service.calc(ctx);

        assertEquals(2500L, r.getUnitPriceCent());
        assertEquals(PriceSource.FLASH_SALE, r.getSource());
        assertEquals("FLASH#12", r.getRuleId());
    }

    @Test
    @DisplayName("企业协议有效时秒杀不生效：协议 5 折(50 元) 覆盖 25 元秒杀 → 按协议 50 元")
    void flashSkippedWhenAgreementValid() {
        PriceContext ctx = base()
                .customerType(CustomerType.ENTERPRISE)
                .agreement(new AgreementInfo(50, true))
                .flashSale(2500L, "FLASH#12")
                .build();

        PriceResult r = service.calc(ctx);

        assertEquals(5000L, r.getUnitPriceCent());
        assertEquals(PriceSource.AGREEMENT, r.getSource());
    }

    @Test
    @DisplayName("一客一价存在时秒杀不生效：40 元一客一价覆盖 25 元秒杀 → 按 40 元")
    void flashSkippedWhenCustomerSkuPrice() {
        PriceContext ctx = base()
                .customerSkuPriceCent(4000L)
                .flashSale(2500L, "FLASH#12")
                .build();

        PriceResult r = service.calc(ctx);

        assertEquals(4000L, r.getUnitPriceCent());
        assertEquals(PriceSource.CUSTOMER_SKU, r.getSource());
    }

    @Test
    @DisplayName("无活动时行为不变：秒杀字段为空 → 依旧走会员价")
    void withoutFlashFallsBackToMember() {
        PriceContext ctx = base()
                .memberDiscountRate(95)
                .build();

        PriceResult r = service.calc(ctx);

        assertEquals(9500L, r.getUnitPriceCent());
        assertEquals(PriceSource.MEMBER, r.getSource());
    }

    @Test
    @DisplayName("秒杀价低于底价仍然抛 BELOW_FLOOR_PRICE（所以发布活动必须先校验）")
    void flashBelowFloorThrows() {
        PriceContext ctx = base()
                .floorPriceCent(3000L)
                .flashSale(2500L, "FLASH#12")
                .build();

        BizException ex = assertThrows(BizException.class, () -> service.calc(ctx));
        assertEquals(ErrorCode.BELOW_FLOOR_PRICE, ex.getErrorCode());
    }

    @Test
    @DisplayName("秒杀价低于成本但不低于底价：仅标记 belowCost，不阻塞下单")
    void flashBelowCostMarksOnly() {
        PriceContext ctx = base()
                .floorPriceCent(500L)
                .costPriceCent(3000L)
                .flashSale(2500L, "FLASH#12")
                .build();

        PriceResult r = service.calc(ctx);

        assertEquals(2500L, r.getUnitPriceCent());
        assertEquals(PriceSource.FLASH_SALE, r.getSource());
        assertTrue(r.isBelowCost(), "低于成本要留痕，供管理端举证");
        assertFalse(r.isBelowCost() && r.getUnitPriceCent() >= 3000L);
    }
}

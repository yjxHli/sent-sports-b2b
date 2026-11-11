package com.sent.sports.product.domain.pricing;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 取价引擎编排（plan §7.1）：严格优先级、命中即返回（不比较、不取低）。
 * <pre>
 * 1) 折上折开关开启 且 企业协议有效 且 命中阶梯 → 协议价 × 阶梯折扣率（STACKED）
 * 2) 否则按 order 升序尝试：一客一价 → 协议价 → 阶梯 → 会员 → 零售
 * 3) 任何结果都过防倒挂双重校验：低于底价抛 BELOW_FLOOR_PRICE(40920)；低于成本价仅置 belowCost 标记
 * </pre>
 */
public class PricingService {

    private final List<PriceStrategy> strategies;

    public PricingService() {
        this(List.of(
                new CustomerSkuPriceStrategy(),
                new AgreementPriceStrategy(),
                new LadderPriceStrategy(),
                new MemberPriceStrategy(),
                new RetailPriceStrategy()));
    }

    public PricingService(List<PriceStrategy> strategies) {
        this.strategies = new ArrayList<>(strategies);
        this.strategies.sort(Comparator.comparingInt(PriceStrategy::order));
    }

    public PriceResult calc(PriceContext ctx) {
        // 折上折：仅当显式开启、企业协议有效、且命中阶梯时生效
        if (ctx.isAllowStacking() && ctx.agreementValid()) {
            LadderTier tier = ctx.matchLadder();
            if (tier != null) {
                long unit = ctx.getRetailPriceCent()
                        * ctx.getAgreement().discountRate()
                        * tier.rate()
                        / 10000L;
                return finalize(unit, PriceSource.STACKED, "AGREEMENT×LADDER#" + tier, ctx);
            }
        }

        for (PriceStrategy s : strategies) {
            if (s.match(ctx)) {
                PriceResult pr = s.price(ctx);
                return finalize(pr.getUnitPriceCent(), pr.getSource(), pr.getRuleId(), ctx);
            }
        }
        // 理论上不会到达（Retail 始终命中）
        return finalize(ctx.getRetailPriceCent(), PriceSource.RETAIL, "RETAIL", ctx);
    }

    /** 防倒挂双重校验：底价拦截、成本价告警。 */
    private PriceResult finalize(long unitPriceCent, PriceSource source, String ruleId, PriceContext ctx) {
        if (unitPriceCent < ctx.getFloorPriceCent()) {
            throw new BizException(ErrorCode.BELOW_FLOOR_PRICE);
        }
        boolean belowCost = unitPriceCent < ctx.getCostPriceCent();
        return new PriceResult(unitPriceCent, source, ruleId, belowCost);
    }
}

package com.sent.sports.product.domain.pricing;

/** 阶梯价：按购买数量命中区间，折扣率优先于会员等级。 */
public class LadderPriceStrategy implements PriceStrategy {

    @Override
    public int order() {
        return 3;
    }

    @Override
    public boolean match(PriceContext ctx) {
        return ctx.matchLadder() != null;
    }

    @Override
    public PriceResult price(PriceContext ctx) {
        LadderTier tier = ctx.matchLadder();
        long unit = ctx.getRetailPriceCent() * tier.rate() / 100;
        return new PriceResult(unit, PriceSource.LADDER, "LADDER#" + tier, false);
    }
}

package com.sent.sports.product.domain.pricing;

/** 零售价：始终命中，作为兜底来源。 */
public class RetailPriceStrategy implements PriceStrategy {

    @Override
    public int order() {
        return 5;
    }

    @Override
    public boolean match(PriceContext ctx) {
        return true;
    }

    @Override
    public PriceResult price(PriceContext ctx) {
        return new PriceResult(ctx.getRetailPriceCent(), PriceSource.RETAIL, "RETAIL", false);
    }
}

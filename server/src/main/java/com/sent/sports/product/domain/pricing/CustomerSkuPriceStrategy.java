package com.sent.sports.product.domain.pricing;

/** 一客一价：谈判结果，命中即终结全部计算（优先级最高）。 */
public class CustomerSkuPriceStrategy implements PriceStrategy {

    @Override
    public int order() {
        return 1;
    }

    @Override
    public boolean match(PriceContext ctx) {
        return ctx.getCustomerSkuPriceCent() != null;
    }

    @Override
    public PriceResult price(PriceContext ctx) {
        return new PriceResult(ctx.getCustomerSkuPriceCent(), PriceSource.CUSTOMER_SKU, "CUSTOMER_SKU", false);
    }
}

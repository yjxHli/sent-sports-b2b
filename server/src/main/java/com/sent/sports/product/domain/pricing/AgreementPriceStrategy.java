package com.sent.sports.product.domain.pricing;

/** 企业协议价：命中后终结计算（吸收阶梯，不叠加）。 */
public class AgreementPriceStrategy implements PriceStrategy {

    @Override
    public int order() {
        return 2;
    }

    @Override
    public boolean match(PriceContext ctx) {
        return ctx.agreementValid();
    }

    @Override
    public PriceResult price(PriceContext ctx) {
        long unit = ctx.getRetailPriceCent() * ctx.getAgreement().discountRate() / 100;
        return new PriceResult(unit, PriceSource.AGREEMENT, "AGREEMENT@" + ctx.getAgreement().discountRate(), false);
    }
}

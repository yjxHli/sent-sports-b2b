package com.sent.sports.product.domain.pricing;

/** 会员等级折扣：等级折扣率 >0 且 <100 时生效，晚于阶梯。 */
public class MemberPriceStrategy implements PriceStrategy {

    @Override
    public int order() {
        return 4;
    }

    @Override
    public boolean match(PriceContext ctx) {
        int rate = ctx.getMemberDiscountRate();
        return rate > 0 && rate < 100;
    }

    @Override
    public PriceResult price(PriceContext ctx) {
        long unit = ctx.getRetailPriceCent() * ctx.getMemberDiscountRate() / 100;
        return new PriceResult(unit, PriceSource.MEMBER, "MEMBER@" + ctx.getMemberDiscountRate(), false);
    }
}

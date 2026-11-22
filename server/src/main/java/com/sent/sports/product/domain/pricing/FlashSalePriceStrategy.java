package com.sent.sports.product.domain.pricing;

/**
 * 秒杀活动价（V54）：公开限时价，order=0 最先尝试。
 *
 * <p><b>为什么不覆盖合同价</b>：一客一价与协议价是谈判/合同结果，属于对客户的承诺；
 * 一个公开活动价不应把它们悄悄抬高或压低。故 {@code match()} 显式排除这两种情况，
 * 效果是：合同价 &gt; 秒杀价 &gt; 阶梯价 &gt; 会员价 &gt; 零售价。</p>
 *
 * <p>本策略不做底价校验，交给 {@link PricingService#calc} 的 finalize 统一处理——
 * 秒杀价若低于底价会抛 {@code BELOW_FLOOR_PRICE}，因此管理端发布活动前必须校验，
 * 不能指望运行时兜底。</p>
 */
public class FlashSalePriceStrategy implements PriceStrategy {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public boolean match(PriceContext ctx) {
        return ctx.getFlashSalePriceCent() != null
                && ctx.getCustomerSkuPriceCent() == null
                && !ctx.agreementValid();
    }

    @Override
    public PriceResult price(PriceContext ctx) {
        Long flashPrice = ctx.getFlashSalePriceCent();
        long unit = flashPrice == null ? ctx.getRetailPriceCent() : flashPrice;
        String ruleId = ctx.getFlashSaleRuleId() == null ? "FLASH_SALE" : ctx.getFlashSaleRuleId();
        return new PriceResult(unit, PriceSource.FLASH_SALE, ruleId, false);
    }
}

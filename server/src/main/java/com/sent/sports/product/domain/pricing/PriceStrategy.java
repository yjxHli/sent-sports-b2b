package com.sent.sports.product.domain.pricing;

/**
 * 取价策略（策略模式）：每个价格类型实现一种取价逻辑。
 * PricingService 按 {@link #order()} 升序依次尝试，命中即返回（严格优先级、不取低）。
 */
public interface PriceStrategy {

    /** 优先级，越小越先尝试。 */
    int order();

    /** 该策略在当前上下文是否适用。 */
    boolean match(PriceContext ctx);

    /** 计算成交单价（调用方保证 match()==true）。不做底价/成本校验，由服务统一 finalize。 */
    PriceResult price(PriceContext ctx);
}
